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
 ** Configuration of one bucket alias: where it lives and what it is for.
 *******************************************************************************/
public class StorageBucketConfig
{
   private String alias;
   private String connection;
   private String bucketName;
   private BucketRole role = BucketRole.PRIVATE;
   private String publicBaseUrl;
   private Boolean indexed = true;
   private ManagedBy managedBy = ManagedBy.IAC;
   private Boolean trashEnabled = false;
   private String trashBucket;
   private String trashPrefix = ".trash/";
   private Integer trashRetentionDays = 30;
   private Integer maxPresignGetSeconds = 3600;
   private Integer maxPresignPutSeconds = 900;
   private Boolean referenceTracked = false;
   private java.util.List<String> exemptPrefixes = new java.util.ArrayList<>();
   private Long maxUploadBytes = 104_857_600L;
   private Boolean protectedBucket = false;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public StorageBucketConfig()
   {
   }



   /********************************************************************************
    ** Getter for alias.
    *******************************************************************************/
   public String getAlias()
   {
      return (this.alias);
   }



   /********************************************************************************
    ** Setter for alias.
    *******************************************************************************/
   public void setAlias(String alias)
   {
      this.alias = alias;
   }



   /********************************************************************************
    ** Fluent setter for alias.
    *******************************************************************************/
   public StorageBucketConfig withAlias(String alias)
   {
      this.alias = alias;
      return (this);
   }



   /********************************************************************************
    ** Getter for connection.
    *******************************************************************************/
   public String getConnection()
   {
      return (this.connection);
   }



   /********************************************************************************
    ** Setter for connection.
    *******************************************************************************/
   public void setConnection(String connection)
   {
      this.connection = connection;
   }



   /********************************************************************************
    ** Fluent setter for connection.
    *******************************************************************************/
   public StorageBucketConfig withConnection(String connection)
   {
      this.connection = connection;
      return (this);
   }



   /********************************************************************************
    ** Getter for bucketName.
    *******************************************************************************/
   public String getBucketName()
   {
      return (this.bucketName);
   }



   /********************************************************************************
    ** Setter for bucketName.
    *******************************************************************************/
   public void setBucketName(String bucketName)
   {
      this.bucketName = bucketName;
   }



   /********************************************************************************
    ** Fluent setter for bucketName.
    *******************************************************************************/
   public StorageBucketConfig withBucketName(String bucketName)
   {
      this.bucketName = bucketName;
      return (this);
   }



   /********************************************************************************
    ** Getter for role.
    *******************************************************************************/
   public BucketRole getRole()
   {
      return (this.role);
   }



   /********************************************************************************
    ** Setter for role.
    *******************************************************************************/
   public void setRole(BucketRole role)
   {
      this.role = role;
   }



   /********************************************************************************
    ** Fluent setter for role.
    *******************************************************************************/
   public StorageBucketConfig withRole(BucketRole role)
   {
      this.role = role;
      return (this);
   }



   /********************************************************************************
    ** Getter for publicBaseUrl.
    *******************************************************************************/
   public String getPublicBaseUrl()
   {
      return (this.publicBaseUrl);
   }



   /********************************************************************************
    ** Setter for publicBaseUrl.
    *******************************************************************************/
   public void setPublicBaseUrl(String publicBaseUrl)
   {
      this.publicBaseUrl = publicBaseUrl;
   }



   /********************************************************************************
    ** Fluent setter for publicBaseUrl.
    *******************************************************************************/
   public StorageBucketConfig withPublicBaseUrl(String publicBaseUrl)
   {
      this.publicBaseUrl = publicBaseUrl;
      return (this);
   }



   /********************************************************************************
    ** Getter for indexed.
    *******************************************************************************/
   public Boolean getIndexed()
   {
      return (this.indexed);
   }



   /********************************************************************************
    ** Setter for indexed.
    *******************************************************************************/
   public void setIndexed(Boolean indexed)
   {
      this.indexed = indexed;
   }



   /********************************************************************************
    ** Fluent setter for indexed.
    *******************************************************************************/
   public StorageBucketConfig withIndexed(Boolean indexed)
   {
      this.indexed = indexed;
      return (this);
   }



   /********************************************************************************
    ** Getter for managedBy.
    *******************************************************************************/
   public ManagedBy getManagedBy()
   {
      return (this.managedBy);
   }



   /********************************************************************************
    ** Setter for managedBy.
    *******************************************************************************/
   public void setManagedBy(ManagedBy managedBy)
   {
      this.managedBy = managedBy;
   }



   /********************************************************************************
    ** Fluent setter for managedBy.
    *******************************************************************************/
   public StorageBucketConfig withManagedBy(ManagedBy managedBy)
   {
      this.managedBy = managedBy;
      return (this);
   }



   /********************************************************************************
    ** Getter for trashEnabled.
    *******************************************************************************/
   public Boolean getTrashEnabled()
   {
      return (this.trashEnabled);
   }



   /********************************************************************************
    ** Setter for trashEnabled.
    *******************************************************************************/
   public void setTrashEnabled(Boolean trashEnabled)
   {
      this.trashEnabled = trashEnabled;
   }



   /********************************************************************************
    ** Fluent setter for trashEnabled.
    *******************************************************************************/
   public StorageBucketConfig withTrashEnabled(Boolean trashEnabled)
   {
      this.trashEnabled = trashEnabled;
      return (this);
   }



   /********************************************************************************
    ** Getter for trashBucket.
    *******************************************************************************/
   public String getTrashBucket()
   {
      return (this.trashBucket);
   }



   /********************************************************************************
    ** Setter for trashBucket.
    *******************************************************************************/
   public void setTrashBucket(String trashBucket)
   {
      this.trashBucket = trashBucket;
   }



   /********************************************************************************
    ** Fluent setter for trashBucket.
    *******************************************************************************/
   public StorageBucketConfig withTrashBucket(String trashBucket)
   {
      this.trashBucket = trashBucket;
      return (this);
   }



   /********************************************************************************
    ** Getter for trashPrefix.
    *******************************************************************************/
   public String getTrashPrefix()
   {
      return (this.trashPrefix);
   }



   /********************************************************************************
    ** Setter for trashPrefix.
    *******************************************************************************/
   public void setTrashPrefix(String trashPrefix)
   {
      this.trashPrefix = trashPrefix;
   }



   /********************************************************************************
    ** Fluent setter for trashPrefix.
    *******************************************************************************/
   public StorageBucketConfig withTrashPrefix(String trashPrefix)
   {
      this.trashPrefix = trashPrefix;
      return (this);
   }



   /********************************************************************************
    ** Getter for trashRetentionDays.
    *******************************************************************************/
   public Integer getTrashRetentionDays()
   {
      return (this.trashRetentionDays);
   }



   /********************************************************************************
    ** Setter for trashRetentionDays.
    *******************************************************************************/
   public void setTrashRetentionDays(Integer trashRetentionDays)
   {
      this.trashRetentionDays = trashRetentionDays;
   }



   /********************************************************************************
    ** Fluent setter for trashRetentionDays.
    *******************************************************************************/
   public StorageBucketConfig withTrashRetentionDays(Integer trashRetentionDays)
   {
      this.trashRetentionDays = trashRetentionDays;
      return (this);
   }



   /********************************************************************************
    ** Getter for maxPresignGetSeconds.
    *******************************************************************************/
   public Integer getMaxPresignGetSeconds()
   {
      return (this.maxPresignGetSeconds);
   }



   /********************************************************************************
    ** Setter for maxPresignGetSeconds.
    *******************************************************************************/
   public void setMaxPresignGetSeconds(Integer maxPresignGetSeconds)
   {
      this.maxPresignGetSeconds = maxPresignGetSeconds;
   }



   /********************************************************************************
    ** Fluent setter for maxPresignGetSeconds.
    *******************************************************************************/
   public StorageBucketConfig withMaxPresignGetSeconds(Integer maxPresignGetSeconds)
   {
      this.maxPresignGetSeconds = maxPresignGetSeconds;
      return (this);
   }



   /********************************************************************************
    ** Getter for maxPresignPutSeconds.
    *******************************************************************************/
   public Integer getMaxPresignPutSeconds()
   {
      return (this.maxPresignPutSeconds);
   }



   /********************************************************************************
    ** Setter for maxPresignPutSeconds.
    *******************************************************************************/
   public void setMaxPresignPutSeconds(Integer maxPresignPutSeconds)
   {
      this.maxPresignPutSeconds = maxPresignPutSeconds;
   }



   /********************************************************************************
    ** Fluent setter for maxPresignPutSeconds.
    *******************************************************************************/
   public StorageBucketConfig withMaxPresignPutSeconds(Integer maxPresignPutSeconds)
   {
      this.maxPresignPutSeconds = maxPresignPutSeconds;
      return (this);
   }



   /********************************************************************************
    ** Getter for referenceTracked.
    *******************************************************************************/
   public Boolean getReferenceTracked()
   {
      return (this.referenceTracked);
   }



   /********************************************************************************
    ** Setter for referenceTracked.
    *******************************************************************************/
   public void setReferenceTracked(Boolean referenceTracked)
   {
      this.referenceTracked = referenceTracked;
   }



   /********************************************************************************
    ** Fluent setter for referenceTracked.
    *******************************************************************************/
   public StorageBucketConfig withReferenceTracked(Boolean referenceTracked)
   {
      this.referenceTracked = referenceTracked;
      return (this);
   }



   /********************************************************************************
    ** Getter for exemptPrefixes.
    *******************************************************************************/
   public java.util.List<String> getExemptPrefixes()
   {
      return (this.exemptPrefixes);
   }



   /********************************************************************************
    ** Setter for exemptPrefixes.
    *******************************************************************************/
   public void setExemptPrefixes(java.util.List<String> exemptPrefixes)
   {
      this.exemptPrefixes = exemptPrefixes;
   }



   /********************************************************************************
    ** Fluent setter for exemptPrefixes.
    *******************************************************************************/
   public StorageBucketConfig withExemptPrefixes(java.util.List<String> exemptPrefixes)
   {
      this.exemptPrefixes = exemptPrefixes;
      return (this);
   }



   /********************************************************************************
    ** Getter for maxUploadBytes.
    *******************************************************************************/
   public Long getMaxUploadBytes()
   {
      return (this.maxUploadBytes);
   }



   /********************************************************************************
    ** Setter for maxUploadBytes.
    *******************************************************************************/
   public void setMaxUploadBytes(Long maxUploadBytes)
   {
      this.maxUploadBytes = maxUploadBytes;
   }



   /********************************************************************************
    ** Fluent setter for maxUploadBytes.
    *******************************************************************************/
   public StorageBucketConfig withMaxUploadBytes(Long maxUploadBytes)
   {
      this.maxUploadBytes = maxUploadBytes;
      return (this);
   }



   /********************************************************************************
    ** Getter for protectedBucket.
    *******************************************************************************/
   public Boolean getProtectedBucket()
   {
      return (this.protectedBucket);
   }



   /********************************************************************************
    ** Setter for protectedBucket.
    *******************************************************************************/
   public void setProtectedBucket(Boolean protectedBucket)
   {
      this.protectedBucket = protectedBucket;
   }



   /********************************************************************************
    ** Fluent setter for protectedBucket.
    *******************************************************************************/
   public StorageBucketConfig withProtectedBucket(Boolean protectedBucket)
   {
      this.protectedBucket = protectedBucket;
      return (this);
   }



   /*******************************************************************************
    ** Constructor with the alias.
    *******************************************************************************/
   public StorageBucketConfig(String alias)
   {
      this.alias = alias;
   }

}
