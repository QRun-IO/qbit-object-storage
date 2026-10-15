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


import java.net.URI;
import java.util.function.Function;
import com.kingsrook.qbits.objectstorage.config.AddressingStyle;
import com.kingsrook.qbits.objectstorage.config.CredentialsMode;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.backend.module.filesystem.s3.model.metadata.S3BackendMetaData;
import com.kingsrook.qqq.backend.module.filesystem.s3.model.metadata.S3ChecksumMode;
import com.kingsrook.qqq.backend.module.filesystem.s3.utils.S3ClientFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.DelegatingS3Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Request;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;


/*******************************************************************************
 ** One connection's live state: the settings, one cached SDK v2 S3Client and
 ** S3Presigner, and the rate limiter.
 **
 ** Core owns S3 client construction. This runtime owns its uncached client and
 ** closes it on reset/reinstall; the presigner remains local.
 *******************************************************************************/
public class ConnectionRuntime
{
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
    ** Display hash of connection settings; runtime reset/reinstall rebuilds clients.
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
      S3BackendMetaData backend = backendMetaData();
      String token = config.getCredentialsMode() == CredentialsMode.STATIC_REFS ? SecretRefs.resolve(config.getSessionTokenRef()) : null;
      AwsCredentialsProvider sessionCredentials = null;
      if(token != null && backend.getAccessKey() != null && backend.getSecretKey() != null)
      {
         sessionCredentials = StaticCredentialsProvider.create(AwsSessionCredentials.create(backend.getAccessKey(), backend.getSecretKey(), token));
      }

      S3Client coreClient = S3ClientFactory.buildClient(backend);
      return (sessionCredentials == null ? coreClient : new SessionCredentialsClient(coreClient, sessionCredentials));
   }



   /*******************************************************************************
    ** Resolve references before passing the connection settings to core. No bucket
    ** is mapped: one connection serves multiple buckets. Presigning stays local.
    *******************************************************************************/
   S3BackendMetaData backendMetaData()
   {
      S3BackendMetaData backend = new S3BackendMetaData()
         .withEndpoint(SecretRefs.resolveSetting(config.getEndpoint()))
         .withRegion(effectiveRegion())
         .withPathStyleAccess(pathStyle())
         .withChecksumMode(S3ChecksumMode.valueOf(config.effectiveChecksumMode().name()))
         .withTlsVerify(config.getTlsVerify())
         .withCaCertificatesPem(SecretRefs.resolve(config.getCaBundleRef()))
         .withConnectTimeoutMillis(config.getConnectTimeoutMs())
         .withSocketTimeoutMillis(config.getReadTimeoutMs());
      if(config.getCredentialsMode() == CredentialsMode.STATIC_REFS)
      {
         backend.withAccessKey(SecretRefs.resolve(config.getAccessKeyRef()))
            .withSecretKey(SecretRefs.resolve(config.getSecretKeyRef()));
      }
      return (backend);
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
    ** STAND-IN(core=QRun-IO/qqq#1165, removeBy=4.1.0-RC.9)
    ** RC.8 S3BackendMetaData and S3ClientFactory still lack a session token or
    ** credentials-provider hook (verified against the released JAR).
    ** Override request credentials only; core still owns the client and transport.
    *******************************************************************************/
   private static class SessionCredentialsClient extends DelegatingS3Client
   {
      private final AwsCredentialsProvider credentialsProvider;



      /*******************************************************************************
       ** Wrap a core-built client with the connection's session credentials.
       *******************************************************************************/
      private SessionCredentialsClient(S3Client delegate, AwsCredentialsProvider credentialsProvider)
      {
         super(delegate);
         this.credentialsProvider = credentialsProvider;
      }



      /*******************************************************************************
       ** Preserve request overrides and the concrete request type while adding the
       ** session token to signing, including streaming and paginated operations.
       *******************************************************************************/
      @Override
      @SuppressWarnings("unchecked") // toBuilder().build() retains the concrete request type T.
      protected <T extends S3Request, ReturnT> ReturnT invokeOperation(T request, Function<T, ReturnT> operation)
      {
         AwsRequestOverrideConfiguration.Builder overrides = request.overrideConfiguration()
            .map(AwsRequestOverrideConfiguration::toBuilder).orElseGet(AwsRequestOverrideConfiguration::builder);
         T signedRequest = (T) request.toBuilder()
            .overrideConfiguration(overrides.credentialsProvider(credentialsProvider).build())
            .build();
         return (operation.apply(signedRequest));
      }
   }
}
