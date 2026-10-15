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
import com.kingsrook.qbits.objectstorage.config.AddressingStyle;
import com.kingsrook.qbits.objectstorage.config.CredentialsMode;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.backend.module.filesystem.s3.model.metadata.S3BackendMetaData;
import com.kingsrook.qqq.backend.module.filesystem.s3.model.metadata.S3ChecksumMode;
import com.kingsrook.qqq.backend.module.filesystem.s3.utils.S3ClientFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
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
      return (S3ClientFactory.buildClient(backendMetaData()));
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
         withStaticCredentials(backend, config.getAccessKeyRef(), config.getSecretKeyRef());
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
    ** Static keys (with the session token, when set) from the references, or the
    ** SDK default chain; core builds the provider.
    *******************************************************************************/
   private AwsCredentialsProvider credentials(String accessRef, String secretRef)
   {
      S3BackendMetaData credentials = new S3BackendMetaData();
      if(config.getCredentialsMode() == CredentialsMode.STATIC_REFS)
      {
         withStaticCredentials(credentials, accessRef, secretRef);
      }
      return (S3ClientFactory.buildCredentialsProvider(credentials));
   }



   /*******************************************************************************
    ** Resolve static keys onto core meta data. The session token applies only
    ** with both keys, as before (core rejects a token without keys).
    *******************************************************************************/
   private void withStaticCredentials(S3BackendMetaData backend, String accessRef, String secretRef)
   {
      String accessKey = SecretRefs.resolve(accessRef);
      String secretKey = SecretRefs.resolve(secretRef);
      backend.withAccessKey(accessKey).withSecretKey(secretKey);
      if(accessKey != null && secretKey != null)
      {
         backend.withSessionToken(SecretRefs.resolve(config.getSessionTokenRef()));
      }
   }
}
