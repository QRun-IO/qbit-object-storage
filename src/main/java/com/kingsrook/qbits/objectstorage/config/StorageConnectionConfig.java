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

package com.kingsrook.qbits.objectstorage.config;


/********************************************************************************
 ** Configuration of one S3-compatible connection. Secrets are references
 ** ** (for example ${env.S3_SECRET_KEY}), never values.
 *******************************************************************************/
public class StorageConnectionConfig
{
   private String name;
   private ProviderType providerType = ProviderType.OTHER;
   private String endpoint;
   private String presignEndpoint;
   private String region;
   private AddressingStyle addressingStyle;
   private String signatureVersion = "V4";
   private ChecksumMode checksumMode;
   private PayloadSigning payloadSigning = PayloadSigning.SIGNED;
   private Boolean tlsVerify = true;
   private String caBundleRef;
   private CredentialsMode credentialsMode = CredentialsMode.STATIC_REFS;
   private String accessKeyRef;
   private String secretKeyRef;
   private String sessionTokenRef;
   private String presignAccessKeyRef;
   private String presignSecretKeyRef;
   private AdminApiType adminApiType = AdminApiType.NONE;
   private Integer maxOpsPerSecond = 20;
   private Integer maxConcurrency = 4;
   private Integer connectTimeoutMs = 5000;
   private Integer readTimeoutMs = 60000;
   private Integer multipartPartSizeMb = 16;
   private Long multipartCopyThresholdBytes = 5_368_709_120L;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public StorageConnectionConfig()
   {
   }



   /********************************************************************************
    ** Getter for name.
    *******************************************************************************/
   public String getName()
   {
      return (this.name);
   }



   /********************************************************************************
    ** Setter for name.
    *******************************************************************************/
   public void setName(String name)
   {
      this.name = name;
   }



   /********************************************************************************
    ** Fluent setter for name.
    *******************************************************************************/
   public StorageConnectionConfig withName(String name)
   {
      this.name = name;
      return (this);
   }



   /********************************************************************************
    ** Getter for providerType.
    *******************************************************************************/
   public ProviderType getProviderType()
   {
      return (this.providerType);
   }



   /********************************************************************************
    ** Setter for providerType.
    *******************************************************************************/
   public void setProviderType(ProviderType providerType)
   {
      this.providerType = providerType;
   }



   /********************************************************************************
    ** Fluent setter for providerType.
    *******************************************************************************/
   public StorageConnectionConfig withProviderType(ProviderType providerType)
   {
      this.providerType = providerType;
      return (this);
   }



   /********************************************************************************
    ** Getter for endpoint.
    *******************************************************************************/
   public String getEndpoint()
   {
      return (this.endpoint);
   }



   /********************************************************************************
    ** Setter for endpoint.
    *******************************************************************************/
   public void setEndpoint(String endpoint)
   {
      this.endpoint = endpoint;
   }



   /********************************************************************************
    ** Fluent setter for endpoint.
    *******************************************************************************/
   public StorageConnectionConfig withEndpoint(String endpoint)
   {
      this.endpoint = endpoint;
      return (this);
   }



   /********************************************************************************
    ** Getter for presignEndpoint.
    *******************************************************************************/
   public String getPresignEndpoint()
   {
      return (this.presignEndpoint);
   }



   /********************************************************************************
    ** Setter for presignEndpoint.
    *******************************************************************************/
   public void setPresignEndpoint(String presignEndpoint)
   {
      this.presignEndpoint = presignEndpoint;
   }



   /********************************************************************************
    ** Fluent setter for presignEndpoint.
    *******************************************************************************/
   public StorageConnectionConfig withPresignEndpoint(String presignEndpoint)
   {
      this.presignEndpoint = presignEndpoint;
      return (this);
   }



   /********************************************************************************
    ** Getter for region.
    *******************************************************************************/
   public String getRegion()
   {
      return (this.region);
   }



   /********************************************************************************
    ** Setter for region.
    *******************************************************************************/
   public void setRegion(String region)
   {
      this.region = region;
   }



   /********************************************************************************
    ** Fluent setter for region.
    *******************************************************************************/
   public StorageConnectionConfig withRegion(String region)
   {
      this.region = region;
      return (this);
   }



   /********************************************************************************
    ** Getter for addressingStyle.
    *******************************************************************************/
   public AddressingStyle getAddressingStyle()
   {
      return (this.addressingStyle);
   }



   /********************************************************************************
    ** Setter for addressingStyle.
    *******************************************************************************/
   public void setAddressingStyle(AddressingStyle addressingStyle)
   {
      this.addressingStyle = addressingStyle;
   }



   /********************************************************************************
    ** Fluent setter for addressingStyle.
    *******************************************************************************/
   public StorageConnectionConfig withAddressingStyle(AddressingStyle addressingStyle)
   {
      this.addressingStyle = addressingStyle;
      return (this);
   }



   /********************************************************************************
    ** Getter for signatureVersion.
    *******************************************************************************/
   public String getSignatureVersion()
   {
      return (this.signatureVersion);
   }



   /********************************************************************************
    ** Setter for signatureVersion.
    *******************************************************************************/
   public void setSignatureVersion(String signatureVersion)
   {
      this.signatureVersion = signatureVersion;
   }



   /********************************************************************************
    ** Fluent setter for signatureVersion.
    *******************************************************************************/
   public StorageConnectionConfig withSignatureVersion(String signatureVersion)
   {
      this.signatureVersion = signatureVersion;
      return (this);
   }



   /********************************************************************************
    ** Getter for checksumMode.
    *******************************************************************************/
   public ChecksumMode getChecksumMode()
   {
      return (this.checksumMode);
   }



   /********************************************************************************
    ** Setter for checksumMode.
    *******************************************************************************/
   public void setChecksumMode(ChecksumMode checksumMode)
   {
      this.checksumMode = checksumMode;
   }



   /********************************************************************************
    ** Fluent setter for checksumMode.
    *******************************************************************************/
   public StorageConnectionConfig withChecksumMode(ChecksumMode checksumMode)
   {
      this.checksumMode = checksumMode;
      return (this);
   }



   /********************************************************************************
    ** Getter for payloadSigning.
    *******************************************************************************/
   public PayloadSigning getPayloadSigning()
   {
      return (this.payloadSigning);
   }



   /********************************************************************************
    ** Setter for payloadSigning.
    *******************************************************************************/
   public void setPayloadSigning(PayloadSigning payloadSigning)
   {
      this.payloadSigning = payloadSigning;
   }



   /********************************************************************************
    ** Fluent setter for payloadSigning.
    *******************************************************************************/
   public StorageConnectionConfig withPayloadSigning(PayloadSigning payloadSigning)
   {
      this.payloadSigning = payloadSigning;
      return (this);
   }



   /********************************************************************************
    ** Getter for tlsVerify.
    *******************************************************************************/
   public Boolean getTlsVerify()
   {
      return (this.tlsVerify);
   }



   /********************************************************************************
    ** Setter for tlsVerify.
    *******************************************************************************/
   public void setTlsVerify(Boolean tlsVerify)
   {
      this.tlsVerify = tlsVerify;
   }



   /********************************************************************************
    ** Fluent setter for tlsVerify.
    *******************************************************************************/
   public StorageConnectionConfig withTlsVerify(Boolean tlsVerify)
   {
      this.tlsVerify = tlsVerify;
      return (this);
   }



   /********************************************************************************
    ** Getter for caBundleRef.
    *******************************************************************************/
   public String getCaBundleRef()
   {
      return (this.caBundleRef);
   }



   /********************************************************************************
    ** Setter for caBundleRef.
    *******************************************************************************/
   public void setCaBundleRef(String caBundleRef)
   {
      this.caBundleRef = caBundleRef;
   }



   /********************************************************************************
    ** Fluent setter for caBundleRef.
    *******************************************************************************/
   public StorageConnectionConfig withCaBundleRef(String caBundleRef)
   {
      this.caBundleRef = caBundleRef;
      return (this);
   }



   /********************************************************************************
    ** Getter for credentialsMode.
    *******************************************************************************/
   public CredentialsMode getCredentialsMode()
   {
      return (this.credentialsMode);
   }



   /********************************************************************************
    ** Setter for credentialsMode.
    *******************************************************************************/
   public void setCredentialsMode(CredentialsMode credentialsMode)
   {
      this.credentialsMode = credentialsMode;
   }



   /********************************************************************************
    ** Fluent setter for credentialsMode.
    *******************************************************************************/
   public StorageConnectionConfig withCredentialsMode(CredentialsMode credentialsMode)
   {
      this.credentialsMode = credentialsMode;
      return (this);
   }



   /********************************************************************************
    ** Getter for accessKeyRef.
    *******************************************************************************/
   public String getAccessKeyRef()
   {
      return (this.accessKeyRef);
   }



   /********************************************************************************
    ** Setter for accessKeyRef.
    *******************************************************************************/
   public void setAccessKeyRef(String accessKeyRef)
   {
      this.accessKeyRef = accessKeyRef;
   }



   /********************************************************************************
    ** Fluent setter for accessKeyRef.
    *******************************************************************************/
   public StorageConnectionConfig withAccessKeyRef(String accessKeyRef)
   {
      this.accessKeyRef = accessKeyRef;
      return (this);
   }



   /********************************************************************************
    ** Getter for secretKeyRef.
    *******************************************************************************/
   public String getSecretKeyRef()
   {
      return (this.secretKeyRef);
   }



   /********************************************************************************
    ** Setter for secretKeyRef.
    *******************************************************************************/
   public void setSecretKeyRef(String secretKeyRef)
   {
      this.secretKeyRef = secretKeyRef;
   }



   /********************************************************************************
    ** Fluent setter for secretKeyRef.
    *******************************************************************************/
   public StorageConnectionConfig withSecretKeyRef(String secretKeyRef)
   {
      this.secretKeyRef = secretKeyRef;
      return (this);
   }



   /********************************************************************************
    ** Getter for sessionTokenRef.
    *******************************************************************************/
   public String getSessionTokenRef()
   {
      return (this.sessionTokenRef);
   }



   /********************************************************************************
    ** Setter for sessionTokenRef.
    *******************************************************************************/
   public void setSessionTokenRef(String sessionTokenRef)
   {
      this.sessionTokenRef = sessionTokenRef;
   }



   /********************************************************************************
    ** Fluent setter for sessionTokenRef.
    *******************************************************************************/
   public StorageConnectionConfig withSessionTokenRef(String sessionTokenRef)
   {
      this.sessionTokenRef = sessionTokenRef;
      return (this);
   }



   /********************************************************************************
    ** Getter for presignAccessKeyRef.
    *******************************************************************************/
   public String getPresignAccessKeyRef()
   {
      return (this.presignAccessKeyRef);
   }



   /********************************************************************************
    ** Setter for presignAccessKeyRef.
    *******************************************************************************/
   public void setPresignAccessKeyRef(String presignAccessKeyRef)
   {
      this.presignAccessKeyRef = presignAccessKeyRef;
   }



   /********************************************************************************
    ** Fluent setter for presignAccessKeyRef.
    *******************************************************************************/
   public StorageConnectionConfig withPresignAccessKeyRef(String presignAccessKeyRef)
   {
      this.presignAccessKeyRef = presignAccessKeyRef;
      return (this);
   }



   /********************************************************************************
    ** Getter for presignSecretKeyRef.
    *******************************************************************************/
   public String getPresignSecretKeyRef()
   {
      return (this.presignSecretKeyRef);
   }



   /********************************************************************************
    ** Setter for presignSecretKeyRef.
    *******************************************************************************/
   public void setPresignSecretKeyRef(String presignSecretKeyRef)
   {
      this.presignSecretKeyRef = presignSecretKeyRef;
   }



   /********************************************************************************
    ** Fluent setter for presignSecretKeyRef.
    *******************************************************************************/
   public StorageConnectionConfig withPresignSecretKeyRef(String presignSecretKeyRef)
   {
      this.presignSecretKeyRef = presignSecretKeyRef;
      return (this);
   }



   /********************************************************************************
    ** Getter for adminApiType.
    *******************************************************************************/
   public AdminApiType getAdminApiType()
   {
      return (this.adminApiType);
   }



   /********************************************************************************
    ** Setter for adminApiType.
    *******************************************************************************/
   public void setAdminApiType(AdminApiType adminApiType)
   {
      this.adminApiType = adminApiType;
   }



   /********************************************************************************
    ** Fluent setter for adminApiType.
    *******************************************************************************/
   public StorageConnectionConfig withAdminApiType(AdminApiType adminApiType)
   {
      this.adminApiType = adminApiType;
      return (this);
   }



   /********************************************************************************
    ** Getter for maxOpsPerSecond.
    *******************************************************************************/
   public Integer getMaxOpsPerSecond()
   {
      return (this.maxOpsPerSecond);
   }



   /********************************************************************************
    ** Setter for maxOpsPerSecond.
    *******************************************************************************/
   public void setMaxOpsPerSecond(Integer maxOpsPerSecond)
   {
      this.maxOpsPerSecond = maxOpsPerSecond;
   }



   /********************************************************************************
    ** Fluent setter for maxOpsPerSecond.
    *******************************************************************************/
   public StorageConnectionConfig withMaxOpsPerSecond(Integer maxOpsPerSecond)
   {
      this.maxOpsPerSecond = maxOpsPerSecond;
      return (this);
   }



   /********************************************************************************
    ** Getter for maxConcurrency.
    *******************************************************************************/
   public Integer getMaxConcurrency()
   {
      return (this.maxConcurrency);
   }



   /********************************************************************************
    ** Setter for maxConcurrency.
    *******************************************************************************/
   public void setMaxConcurrency(Integer maxConcurrency)
   {
      this.maxConcurrency = maxConcurrency;
   }



   /********************************************************************************
    ** Fluent setter for maxConcurrency.
    *******************************************************************************/
   public StorageConnectionConfig withMaxConcurrency(Integer maxConcurrency)
   {
      this.maxConcurrency = maxConcurrency;
      return (this);
   }



   /********************************************************************************
    ** Getter for connectTimeoutMs.
    *******************************************************************************/
   public Integer getConnectTimeoutMs()
   {
      return (this.connectTimeoutMs);
   }



   /********************************************************************************
    ** Setter for connectTimeoutMs.
    *******************************************************************************/
   public void setConnectTimeoutMs(Integer connectTimeoutMs)
   {
      this.connectTimeoutMs = connectTimeoutMs;
   }



   /********************************************************************************
    ** Fluent setter for connectTimeoutMs.
    *******************************************************************************/
   public StorageConnectionConfig withConnectTimeoutMs(Integer connectTimeoutMs)
   {
      this.connectTimeoutMs = connectTimeoutMs;
      return (this);
   }



   /********************************************************************************
    ** Getter for readTimeoutMs.
    *******************************************************************************/
   public Integer getReadTimeoutMs()
   {
      return (this.readTimeoutMs);
   }



   /********************************************************************************
    ** Setter for readTimeoutMs.
    *******************************************************************************/
   public void setReadTimeoutMs(Integer readTimeoutMs)
   {
      this.readTimeoutMs = readTimeoutMs;
   }



   /********************************************************************************
    ** Fluent setter for readTimeoutMs.
    *******************************************************************************/
   public StorageConnectionConfig withReadTimeoutMs(Integer readTimeoutMs)
   {
      this.readTimeoutMs = readTimeoutMs;
      return (this);
   }



   /********************************************************************************
    ** Getter for multipartPartSizeMb.
    *******************************************************************************/
   public Integer getMultipartPartSizeMb()
   {
      return (this.multipartPartSizeMb);
   }



   /********************************************************************************
    ** Setter for multipartPartSizeMb.
    *******************************************************************************/
   public void setMultipartPartSizeMb(Integer multipartPartSizeMb)
   {
      this.multipartPartSizeMb = multipartPartSizeMb;
   }



   /********************************************************************************
    ** Fluent setter for multipartPartSizeMb.
    *******************************************************************************/
   public StorageConnectionConfig withMultipartPartSizeMb(Integer multipartPartSizeMb)
   {
      this.multipartPartSizeMb = multipartPartSizeMb;
      return (this);
   }



   /********************************************************************************
    ** Getter for multipartCopyThresholdBytes.
    *******************************************************************************/
   public Long getMultipartCopyThresholdBytes()
   {
      return (this.multipartCopyThresholdBytes);
   }



   /********************************************************************************
    ** Setter for multipartCopyThresholdBytes.
    *******************************************************************************/
   public void setMultipartCopyThresholdBytes(Long multipartCopyThresholdBytes)
   {
      this.multipartCopyThresholdBytes = multipartCopyThresholdBytes;
   }



   /********************************************************************************
    ** Fluent setter for multipartCopyThresholdBytes.
    *******************************************************************************/
   public StorageConnectionConfig withMultipartCopyThresholdBytes(Long multipartCopyThresholdBytes)
   {
      this.multipartCopyThresholdBytes = multipartCopyThresholdBytes;
      return (this);
   }



   /*******************************************************************************
    ** Constructor with the connection name.
    *******************************************************************************/
   public StorageConnectionConfig(String name)
   {
      this.name = name;
   }



   /*******************************************************************************
    ** The addressing style, defaulting by provider (path for self-hosted stores).
    *******************************************************************************/
   public AddressingStyle effectiveAddressingStyle()
   {
      if(addressingStyle != null)
      {
         return (addressingStyle);
      }
      return (switch(providerType)
      {
         case AWS_S3, CLOUDFLARE_R2, BACKBLAZE_B2, WASABI, DO_SPACES -> AddressingStyle.VIRTUAL_HOST;
         default -> AddressingStyle.PATH;
      });
   }



   /*******************************************************************************
    ** The checksum mode, defaulting to WHEN_REQUIRED for every provider except AWS.
    *******************************************************************************/
   public ChecksumMode effectiveChecksumMode()
   {
      if(checksumMode != null)
      {
         return (checksumMode);
      }
      return (providerType == ProviderType.AWS_S3 ? ChecksumMode.WHEN_SUPPORTED : ChecksumMode.WHEN_REQUIRED);
   }



   /*******************************************************************************
    ** Whether a separate presign key is configured.
    *******************************************************************************/
   public boolean hasPresignKey()
   {
      return (presignAccessKeyRef != null && presignSecretKeyRef != null);
   }



   /*******************************************************************************
    ** Redacted: only the name and non-secret settings.
    *******************************************************************************/
   @Override
   public String toString()
   {
      return ("StorageConnectionConfig{name=" + name + ", providerType=" + providerType + ", endpoint=" + endpoint + ", credentials=[redacted]}");
   }

}
