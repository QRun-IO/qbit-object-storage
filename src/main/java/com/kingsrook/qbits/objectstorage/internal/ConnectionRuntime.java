/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2025.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.objectstorage.internal;


import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collection;
import com.kingsrook.qbits.objectstorage.config.AddressingStyle;
import com.kingsrook.qbits.objectstorage.config.ChecksumMode;
import com.kingsrook.qbits.objectstorage.config.CredentialsMode;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.utils.AttributeMap;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** One connection's live state: the settings, one cached SDK v2 S3Client and
 ** S3Presigner, and the rate limiter.
 **
 ** STAND-IN: this is the small internal client factory used until QQQ core's
 ** S3ClientFactory (qqq #1095, RC.5) is released; replace it then. Nothing
 ** outside this package depends on how the client is built.
 *******************************************************************************/
public class ConnectionRuntime
{
   private static final QLogger LOG = QLogger.getLogger(ConnectionRuntime.class);

   private final StorageConnectionConfig config;
   private final RateLimiter             rateLimiter;
   private final String                  settingsHash;

   private S3Client      client;
   private S3Presigner   presigner;
   private String        presignAccessKeyId;
   private String        presignSecretKey;
   private String        presignRegion;
   private String        presignHost;



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   public ConnectionRuntime(StorageConnectionConfig config)
   {
      this.config = config;
      this.rateLimiter = new RateLimiter(config.getMaxOpsPerSecond(), config.getMaxConcurrency());
      this.settingsHash = Integer.toHexString(String.valueOf(config.getEndpoint() + "|" + config.getPresignEndpoint() + "|" + config.getRegion() + "|" + config.effectiveAddressingStyle()
         + "|" + config.effectiveChecksumMode() + "|" + config.getTlsVerify() + "|" + config.getCredentialsMode()).hashCode());
   }



   /*******************************************************************************
    ** Getter for config.
    *******************************************************************************/
   public StorageConnectionConfig getConfig()
   {
      return (config);
   }



   /*******************************************************************************
    ** Getter for rateLimiter.
    *******************************************************************************/
   public RateLimiter getRateLimiter()
   {
      return (rateLimiter);
   }



   /*******************************************************************************
    ** Hash of the settings that shape the client (clients rebuild when it changes).
    *******************************************************************************/
   public String getSettingsHash()
   {
      return (settingsHash);
   }



   /*******************************************************************************
    ** The (lazily built, cached) S3 client.
    *******************************************************************************/
   public synchronized S3Client client()
   {
      if(client == null)
      {
         client = buildClient();
      }
      return (client);
   }



   /*******************************************************************************
    ** The (lazily built, cached) presigner. Uses the presign endpoint and the
    ** separate least-privilege presign key when configured.
    *******************************************************************************/
   public synchronized S3Presigner presigner()
   {
      if(presigner == null)
      {
         presigner = buildPresigner();
      }
      return (presigner);
   }



   /*******************************************************************************
    ** Credentials used for presigned POST (the presign key when there is one).
    *******************************************************************************/
   public synchronized String[] presignCredentials()
   {
      presigner();
      return (new String[] { presignAccessKeyId, presignSecretKey, presignRegion, presignHost });
   }



   /*******************************************************************************
    ** Close the client and presigner.
    *******************************************************************************/
   public synchronized void close()
   {
      if(client != null)
      {
         client.close();
         client = null;
      }
      if(presigner != null)
      {
         presigner.close();
         presigner = null;
      }
   }



   /*******************************************************************************
    ** The region to sign for.
    *******************************************************************************/
   private String effectiveRegion()
   {
      String region = SecretRefs.resolveSetting(config.getRegion());
      if(StringUtils.hasContent(region))
      {
         return (region);
      }
      return (StringUtils.hasContent(config.getEndpoint()) ? "us-east-1" : null);
   }



   /*******************************************************************************
    ** Whether path-style addressing applies.
    *******************************************************************************/
   private boolean pathStyle()
   {
      return (config.effectiveAddressingStyle() == AddressingStyle.PATH);
   }



   /*******************************************************************************
    ** Build the S3 client from the settings.
    *******************************************************************************/
   private S3Client buildClient()
   {
      S3ClientBuilder builder = S3Client.builder()
         .credentialsProvider(credentials(config.getAccessKeyRef(), config.getSecretKeyRef()))
         .httpClient(httpClient())
         .forcePathStyle(pathStyle())
         .overrideConfiguration(o -> o.retryStrategy(RetryMode.ADAPTIVE_V2));

      String endpoint = SecretRefs.resolveSetting(config.getEndpoint());
      if(StringUtils.hasContent(endpoint))
      {
         builder.endpointOverride(URI.create(endpoint));
      }
      String region = effectiveRegion();
      if(region != null)
      {
         builder.region(Region.of(region));
      }

      if(config.effectiveChecksumMode() == ChecksumMode.WHEN_REQUIRED)
      {
         builder.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED);
         builder.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
      }
      else
      {
         builder.requestChecksumCalculation(RequestChecksumCalculation.WHEN_SUPPORTED);
         builder.responseChecksumValidation(ResponseChecksumValidation.WHEN_SUPPORTED);
      }

      LOG.debug("Building S3 client", logPair("connection", config.getName()), logPair("endpoint", endpoint), logPair("checksumMode", config.effectiveChecksumMode()));
      return (builder.build());
   }



   /*******************************************************************************
    ** Build the presigner.
    *******************************************************************************/
   private S3Presigner buildPresigner()
   {
      boolean separateKey = config.hasPresignKey();
      String  accessRef   = separateKey ? config.getPresignAccessKeyRef() : config.getAccessKeyRef();
      String  secretRef   = separateKey ? config.getPresignSecretKeyRef() : config.getSecretKeyRef();
      String  endpoint    = SecretRefs.resolveSetting(StringUtils.hasContent(config.getPresignEndpoint()) ? config.getPresignEndpoint() : config.getEndpoint());
      String  region      = effectiveRegion();

      S3Presigner.Builder builder = S3Presigner.builder()
         .credentialsProvider(credentials(accessRef, secretRef))
         .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle()).build());
      if(StringUtils.hasContent(endpoint))
      {
         builder.endpointOverride(URI.create(endpoint));
      }
      if(region != null)
      {
         builder.region(Region.of(region));
      }

      presignAccessKeyId = SecretRefs.resolve(accessRef);
      presignSecretKey = SecretRefs.resolve(secretRef);
      presignRegion = region == null ? "us-east-1" : region;
      presignHost = endpoint;
      return (builder.build());
   }



   /*******************************************************************************
    ** Static keys from the references, or the SDK default chain.
    *******************************************************************************/
   private AwsCredentialsProvider credentials(String accessRef, String secretRef)
   {
      if(config.getCredentialsMode() == CredentialsMode.STATIC_REFS)
      {
         String accessKey = SecretRefs.resolve(accessRef);
         String secretKey = SecretRefs.resolve(secretRef);
         if(accessKey != null && secretKey != null)
         {
            String token = SecretRefs.resolve(config.getSessionTokenRef());
            return (StaticCredentialsProvider.create(token == null ? AwsBasicCredentials.create(accessKey, secretKey)
               : AwsSessionCredentials.create(accessKey, secretKey, token)));
         }
      }
      return (DefaultCredentialsProvider.builder().build());
   }



   /*******************************************************************************
    ** Apache HTTP client with the connection's timeouts and TLS settings.
    *******************************************************************************/
   private SdkHttpClient httpClient()
   {
      ApacheHttpClient.Builder builder = ApacheHttpClient.builder()
         .connectionTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
         .socketTimeout(Duration.ofMillis(config.getReadTimeoutMs()));

      if(Boolean.FALSE.equals(config.getTlsVerify()))
      {
         LOG.warn("TLS verification is disabled for a storage connection; use only for development", logPair("connection", config.getName()));
         return (builder.buildWithDefaults(AttributeMap.builder().put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, Boolean.TRUE).build()));
      }

      String caPem = SecretRefs.resolve(config.getCaBundleRef());
      if(StringUtils.hasContent(caPem))
      {
         TrustManager[] trustManagers = trustManagers(caPem);
         builder.tlsTrustManagersProvider(() -> trustManagers);
      }
      return (builder.build());
   }



   /*******************************************************************************
    ** Trust the given PEM bundle in addition to the JVM default trust store.
    *******************************************************************************/
   static TrustManager[] trustManagers(String pem)
   {
      try
      {
         CertificateFactory                certificateFactory = CertificateFactory.getInstance("X.509");
         Collection<? extends Certificate> certificates       = certificateFactory.generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
         if(certificates.isEmpty())
         {
            throw (new IllegalArgumentException("The CA bundle contains no certificates"));
         }
         KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
         keyStore.load(null, null);
         int index = 0;
         for(Certificate certificate : certificates)
         {
            keyStore.setCertificateEntry("object-storage-ca-" + index++, certificate);
         }
         X509TrustManager custom   = x509(keyStore);
         X509TrustManager fallback = x509(null);
         return (new TrustManager[] { new Composite(custom, fallback) });
      }
      catch(IllegalArgumentException e)
      {
         throw (e);
      }
      catch(Exception e)
      {
         throw (new IllegalArgumentException("Could not load the CA bundle for a storage connection", e));
      }
   }



   /*******************************************************************************
    ** The X509 trust manager of a key store (null: the JVM default).
    *******************************************************************************/
   private static X509TrustManager x509(KeyStore keyStore) throws Exception
   {
      TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      factory.init(keyStore);
      for(TrustManager trustManager : factory.getTrustManagers())
      {
         if(trustManager instanceof X509TrustManager x)
         {
            return (x);
         }
      }
      throw (new IllegalStateException("No X509TrustManager available"));
   }



   /*******************************************************************************
    ** Trusts a chain if either the private-CA or the default trust manager does.
    *******************************************************************************/
   private record Composite(X509TrustManager custom, X509TrustManager fallback) implements X509TrustManager
   {
      /*******************************************************************************
       ** Client certs are not used.
       *******************************************************************************/
      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException
      {
         try
         {
            custom.checkClientTrusted(chain, authType);
         }
         catch(CertificateException e)
         {
            fallback.checkClientTrusted(chain, authType);
         }
      }



      /*******************************************************************************
       ** Server certs.
       *******************************************************************************/
      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
      {
         try
         {
            custom.checkServerTrusted(chain, authType);
         }
         catch(CertificateException e)
         {
            fallback.checkServerTrusted(chain, authType);
         }
      }



      /*******************************************************************************
       ** Accepted issuers of both.
       *******************************************************************************/
      @Override
      public X509Certificate[] getAcceptedIssuers()
      {
         X509Certificate[] a = custom.getAcceptedIssuers();
         X509Certificate[] b = fallback.getAcceptedIssuers();
         X509Certificate[] all = new X509Certificate[a.length + b.length];
         System.arraycopy(a, 0, all, 0, a.length);
         System.arraycopy(b, 0, all, a.length, b.length);
         return (all);
      }
   }
}
