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

package com.kingsrook.qbits.objectstorage;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaderPolicy;
import com.kingsrook.qbits.objectstorage.api.PublicUrlResolver;
import com.kingsrook.qbits.objectstorage.api.StorageAlertSink;
import com.kingsrook.qbits.objectstorage.api.StorageReferenceProvider;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qbits.objectstorage.internal.SecretRefs;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/********************************************************************************
 ** Host-facing configuration for the Object Storage QBit. Code is the source of
 ** ** truth: connections and buckets are declared here and upserted into the
 ** ** object_storage_connection and object_storage_bucket tables.
 ** **
 ** ** Tables are named object_storage_<name> (lowercase snake_case, backend name equal
 ** ** to the table name); permissions are object_storage.<action>.
 *******************************************************************************/
public class ObjectStorageQBitConfig implements QBitConfig
{
   public static final String TABLE_NAME_PREFIX    = "object_storage_";
   public static final String PERMISSION_BASE_NAME = "object_storage";

   private String backendName;
   private List<StorageConnectionConfig> connections = new ArrayList<>();
   private List<StorageBucketConfig> buckets = new ArrayList<>();
   private ObjectHeaderPolicy headerPolicy;
   private PublicUrlResolver publicUrlResolver;
   private List<StorageReferenceProvider> referenceProviders = new ArrayList<>();
   private StorageAlertSink alertSink;
   private Boolean adminApp = false;
   private String appName = "objectStorageAdmin";
   private String appLabel = "Storage";
   private String parentAppName;
   private List<String> allowedSecretRefPatterns = new ArrayList<>(SecretRefs.DEFAULT_ALLOWED_PATTERNS);
   private Integer bulkCap = 10_000;
   private Boolean bucketScoping = true;
   private Boolean openWhenAnonymous = false;
   private Integer maxPresignsPerUserPerHour = 100;
   private Double scanRemovalSafetyShare = 0.5;
   private Long indexMaxKeys = 5_000_000L;
   private String schedulerName;
   private Integer nightlyIntervalSeconds = 86400;
   private Integer headBacklogPerRun = 2000;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public ObjectStorageQBitConfig()
   {
   }



   /********************************************************************************
    ** Getter for backendName.
    *******************************************************************************/
   public String getBackendName()
   {
      return (this.backendName);
   }



   /********************************************************************************
    ** Setter for backendName.
    *******************************************************************************/
   public void setBackendName(String backendName)
   {
      this.backendName = backendName;
   }



   /********************************************************************************
    ** Fluent setter for backendName.
    *******************************************************************************/
   public ObjectStorageQBitConfig withBackendName(String backendName)
   {
      this.backendName = backendName;
      return (this);
   }



   /********************************************************************************
    ** Getter for connections.
    *******************************************************************************/
   public List<StorageConnectionConfig> getConnections()
   {
      return (this.connections);
   }



   /********************************************************************************
    ** Setter for connections.
    *******************************************************************************/
   public void setConnections(List<StorageConnectionConfig> connections)
   {
      this.connections = connections;
   }



   /********************************************************************************
    ** Fluent setter for connections.
    *******************************************************************************/
   public ObjectStorageQBitConfig withConnections(List<StorageConnectionConfig> connections)
   {
      this.connections = connections;
      return (this);
   }



   /********************************************************************************
    ** Getter for buckets.
    *******************************************************************************/
   public List<StorageBucketConfig> getBuckets()
   {
      return (this.buckets);
   }



   /********************************************************************************
    ** Setter for buckets.
    *******************************************************************************/
   public void setBuckets(List<StorageBucketConfig> buckets)
   {
      this.buckets = buckets;
   }



   /********************************************************************************
    ** Fluent setter for buckets.
    *******************************************************************************/
   public ObjectStorageQBitConfig withBuckets(List<StorageBucketConfig> buckets)
   {
      this.buckets = buckets;
      return (this);
   }



   /********************************************************************************
    ** Getter for headerPolicy.
    *******************************************************************************/
   public ObjectHeaderPolicy getHeaderPolicy()
   {
      return (this.headerPolicy);
   }



   /********************************************************************************
    ** Setter for headerPolicy.
    *******************************************************************************/
   public void setHeaderPolicy(ObjectHeaderPolicy headerPolicy)
   {
      this.headerPolicy = headerPolicy;
   }



   /********************************************************************************
    ** Fluent setter for headerPolicy.
    *******************************************************************************/
   public ObjectStorageQBitConfig withHeaderPolicy(ObjectHeaderPolicy headerPolicy)
   {
      this.headerPolicy = headerPolicy;
      return (this);
   }



   /********************************************************************************
    ** Getter for publicUrlResolver.
    *******************************************************************************/
   public PublicUrlResolver getPublicUrlResolver()
   {
      return (this.publicUrlResolver);
   }



   /********************************************************************************
    ** Setter for publicUrlResolver.
    *******************************************************************************/
   public void setPublicUrlResolver(PublicUrlResolver publicUrlResolver)
   {
      this.publicUrlResolver = publicUrlResolver;
   }



   /********************************************************************************
    ** Fluent setter for publicUrlResolver.
    *******************************************************************************/
   public ObjectStorageQBitConfig withPublicUrlResolver(PublicUrlResolver publicUrlResolver)
   {
      this.publicUrlResolver = publicUrlResolver;
      return (this);
   }



   /********************************************************************************
    ** Getter for referenceProviders.
    *******************************************************************************/
   public List<StorageReferenceProvider> getReferenceProviders()
   {
      return (this.referenceProviders);
   }



   /********************************************************************************
    ** Setter for referenceProviders.
    *******************************************************************************/
   public void setReferenceProviders(List<StorageReferenceProvider> referenceProviders)
   {
      this.referenceProviders = referenceProviders;
   }



   /********************************************************************************
    ** Fluent setter for referenceProviders.
    *******************************************************************************/
   public ObjectStorageQBitConfig withReferenceProviders(List<StorageReferenceProvider> referenceProviders)
   {
      this.referenceProviders = referenceProviders;
      return (this);
   }



   /********************************************************************************
    ** Getter for alertSink.
    *******************************************************************************/
   public StorageAlertSink getAlertSink()
   {
      return (this.alertSink);
   }



   /********************************************************************************
    ** Setter for alertSink.
    *******************************************************************************/
   public void setAlertSink(StorageAlertSink alertSink)
   {
      this.alertSink = alertSink;
   }



   /********************************************************************************
    ** Fluent setter for alertSink.
    *******************************************************************************/
   public ObjectStorageQBitConfig withAlertSink(StorageAlertSink alertSink)
   {
      this.alertSink = alertSink;
      return (this);
   }



   /********************************************************************************
    ** Getter for adminApp.
    *******************************************************************************/
   public Boolean getAdminApp()
   {
      return (this.adminApp);
   }



   /********************************************************************************
    ** Setter for adminApp.
    *******************************************************************************/
   public void setAdminApp(Boolean adminApp)
   {
      this.adminApp = adminApp;
   }



   /********************************************************************************
    ** Fluent setter for adminApp.
    *******************************************************************************/
   public ObjectStorageQBitConfig withAdminApp(Boolean adminApp)
   {
      this.adminApp = adminApp;
      return (this);
   }



   /********************************************************************************
    ** Getter for appName.
    *******************************************************************************/
   public String getAppName()
   {
      return (this.appName);
   }



   /********************************************************************************
    ** Setter for appName.
    *******************************************************************************/
   public void setAppName(String appName)
   {
      this.appName = appName;
   }



   /********************************************************************************
    ** Fluent setter for appName.
    *******************************************************************************/
   public ObjectStorageQBitConfig withAppName(String appName)
   {
      this.appName = appName;
      return (this);
   }



   /********************************************************************************
    ** Getter for appLabel.
    *******************************************************************************/
   public String getAppLabel()
   {
      return (this.appLabel);
   }



   /********************************************************************************
    ** Setter for appLabel.
    *******************************************************************************/
   public void setAppLabel(String appLabel)
   {
      this.appLabel = appLabel;
   }



   /********************************************************************************
    ** Fluent setter for appLabel.
    *******************************************************************************/
   public ObjectStorageQBitConfig withAppLabel(String appLabel)
   {
      this.appLabel = appLabel;
      return (this);
   }



   /********************************************************************************
    ** Getter for parentAppName.
    *******************************************************************************/
   public String getParentAppName()
   {
      return (this.parentAppName);
   }



   /********************************************************************************
    ** Setter for parentAppName.
    *******************************************************************************/
   public void setParentAppName(String parentAppName)
   {
      this.parentAppName = parentAppName;
   }



   /********************************************************************************
    ** Fluent setter for parentAppName.
    *******************************************************************************/
   public ObjectStorageQBitConfig withParentAppName(String parentAppName)
   {
      this.parentAppName = parentAppName;
      return (this);
   }



   /********************************************************************************
    ** Getter for allowedSecretRefPatterns.
    *******************************************************************************/
   public List<String> getAllowedSecretRefPatterns()
   {
      return (this.allowedSecretRefPatterns);
   }



   /********************************************************************************
    ** Setter for allowedSecretRefPatterns.
    *******************************************************************************/
   public void setAllowedSecretRefPatterns(List<String> allowedSecretRefPatterns)
   {
      this.allowedSecretRefPatterns = allowedSecretRefPatterns;
   }



   /********************************************************************************
    ** Fluent setter for allowedSecretRefPatterns.
    *******************************************************************************/
   public ObjectStorageQBitConfig withAllowedSecretRefPatterns(List<String> allowedSecretRefPatterns)
   {
      this.allowedSecretRefPatterns = allowedSecretRefPatterns;
      return (this);
   }



   /********************************************************************************
    ** Getter for bulkCap.
    *******************************************************************************/
   public Integer getBulkCap()
   {
      return (this.bulkCap);
   }



   /********************************************************************************
    ** Setter for bulkCap.
    *******************************************************************************/
   public void setBulkCap(Integer bulkCap)
   {
      this.bulkCap = bulkCap;
   }



   /********************************************************************************
    ** Fluent setter for bulkCap.
    *******************************************************************************/
   public ObjectStorageQBitConfig withBulkCap(Integer bulkCap)
   {
      this.bulkCap = bulkCap;
      return (this);
   }



   /********************************************************************************
    ** Getter for bucketScoping.
    *******************************************************************************/
   public Boolean getBucketScoping()
   {
      return (this.bucketScoping);
   }



   /********************************************************************************
    ** Setter for bucketScoping.
    *******************************************************************************/
   public void setBucketScoping(Boolean bucketScoping)
   {
      this.bucketScoping = bucketScoping;
   }



   /********************************************************************************
    ** Fluent setter for bucketScoping.
    *******************************************************************************/
   public ObjectStorageQBitConfig withBucketScoping(Boolean bucketScoping)
   {
      this.bucketScoping = bucketScoping;
      return (this);
   }



   /********************************************************************************
    ** Getter for openWhenAnonymous.
    *******************************************************************************/
   public Boolean getOpenWhenAnonymous()
   {
      return (this.openWhenAnonymous);
   }



   /********************************************************************************
    ** Setter for openWhenAnonymous.
    *******************************************************************************/
   public void setOpenWhenAnonymous(Boolean openWhenAnonymous)
   {
      this.openWhenAnonymous = openWhenAnonymous;
   }



   /********************************************************************************
    ** Fluent setter for openWhenAnonymous.
    *******************************************************************************/
   public ObjectStorageQBitConfig withOpenWhenAnonymous(Boolean openWhenAnonymous)
   {
      this.openWhenAnonymous = openWhenAnonymous;
      return (this);
   }



   /********************************************************************************
    ** Getter for maxPresignsPerUserPerHour.
    *******************************************************************************/
   public Integer getMaxPresignsPerUserPerHour()
   {
      return (this.maxPresignsPerUserPerHour);
   }



   /********************************************************************************
    ** Setter for maxPresignsPerUserPerHour.
    *******************************************************************************/
   public void setMaxPresignsPerUserPerHour(Integer maxPresignsPerUserPerHour)
   {
      this.maxPresignsPerUserPerHour = maxPresignsPerUserPerHour;
   }



   /********************************************************************************
    ** Fluent setter for maxPresignsPerUserPerHour.
    *******************************************************************************/
   public ObjectStorageQBitConfig withMaxPresignsPerUserPerHour(Integer maxPresignsPerUserPerHour)
   {
      this.maxPresignsPerUserPerHour = maxPresignsPerUserPerHour;
      return (this);
   }



   /********************************************************************************
    ** Getter for scanRemovalSafetyShare.
    *******************************************************************************/
   public Double getScanRemovalSafetyShare()
   {
      return (this.scanRemovalSafetyShare);
   }



   /********************************************************************************
    ** Setter for scanRemovalSafetyShare.
    *******************************************************************************/
   public void setScanRemovalSafetyShare(Double scanRemovalSafetyShare)
   {
      this.scanRemovalSafetyShare = scanRemovalSafetyShare;
   }



   /********************************************************************************
    ** Fluent setter for scanRemovalSafetyShare.
    *******************************************************************************/
   public ObjectStorageQBitConfig withScanRemovalSafetyShare(Double scanRemovalSafetyShare)
   {
      this.scanRemovalSafetyShare = scanRemovalSafetyShare;
      return (this);
   }



   /********************************************************************************
    ** Getter for indexMaxKeys.
    *******************************************************************************/
   public Long getIndexMaxKeys()
   {
      return (this.indexMaxKeys);
   }



   /********************************************************************************
    ** Setter for indexMaxKeys.
    *******************************************************************************/
   public void setIndexMaxKeys(Long indexMaxKeys)
   {
      this.indexMaxKeys = indexMaxKeys;
   }



   /********************************************************************************
    ** Fluent setter for indexMaxKeys.
    *******************************************************************************/
   public ObjectStorageQBitConfig withIndexMaxKeys(Long indexMaxKeys)
   {
      this.indexMaxKeys = indexMaxKeys;
      return (this);
   }



   /********************************************************************************
    ** Getter for schedulerName.
    *******************************************************************************/
   public String getSchedulerName()
   {
      return (this.schedulerName);
   }



   /********************************************************************************
    ** Setter for schedulerName.
    *******************************************************************************/
   public void setSchedulerName(String schedulerName)
   {
      this.schedulerName = schedulerName;
   }



   /********************************************************************************
    ** Fluent setter for schedulerName.
    *******************************************************************************/
   public ObjectStorageQBitConfig withSchedulerName(String schedulerName)
   {
      this.schedulerName = schedulerName;
      return (this);
   }



   /********************************************************************************
    ** Getter for nightlyIntervalSeconds.
    *******************************************************************************/
   public Integer getNightlyIntervalSeconds()
   {
      return (this.nightlyIntervalSeconds);
   }



   /********************************************************************************
    ** Setter for nightlyIntervalSeconds.
    *******************************************************************************/
   public void setNightlyIntervalSeconds(Integer nightlyIntervalSeconds)
   {
      this.nightlyIntervalSeconds = nightlyIntervalSeconds;
   }



   /********************************************************************************
    ** Fluent setter for nightlyIntervalSeconds.
    *******************************************************************************/
   public ObjectStorageQBitConfig withNightlyIntervalSeconds(Integer nightlyIntervalSeconds)
   {
      this.nightlyIntervalSeconds = nightlyIntervalSeconds;
      return (this);
   }



   /********************************************************************************
    ** Getter for headBacklogPerRun.
    *******************************************************************************/
   public Integer getHeadBacklogPerRun()
   {
      return (this.headBacklogPerRun);
   }



   /********************************************************************************
    ** Setter for headBacklogPerRun.
    *******************************************************************************/
   public void setHeadBacklogPerRun(Integer headBacklogPerRun)
   {
      this.headBacklogPerRun = headBacklogPerRun;
   }



   /********************************************************************************
    ** Fluent setter for headBacklogPerRun.
    *******************************************************************************/
   public ObjectStorageQBitConfig withHeadBacklogPerRun(Integer headBacklogPerRun)
   {
      this.headBacklogPerRun = headBacklogPerRun;
      return (this);
   }



   /*******************************************************************************
    ** Add a connection.
    *******************************************************************************/
   public ObjectStorageQBitConfig withConnection(StorageConnectionConfig connection)
   {
      this.connections.add(connection);
      return (this);
   }



   /*******************************************************************************
    ** Add a bucket.
    *******************************************************************************/
   public ObjectStorageQBitConfig withBucket(StorageBucketConfig bucket)
   {
      this.buckets.add(bucket);
      return (this);
   }



   /*******************************************************************************
    ** Add a reference provider.
    *******************************************************************************/
   public ObjectStorageQBitConfig withReferenceProvider(StorageReferenceProvider provider)
   {
      this.referenceProviders.add(provider);
      return (this);
   }



   /*******************************************************************************
    ** The configured connection of a name, or null.
    *******************************************************************************/
   public StorageConnectionConfig findConnection(String name)
   {
      for(StorageConnectionConfig connection : connections)
      {
         if(connection.getName() != null && connection.getName().equals(name))
         {
            return (connection);
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** The configured bucket of an alias, or null.
    *******************************************************************************/
   public StorageBucketConfig findBucket(String alias)
   {
      for(StorageBucketConfig bucket : buckets)
      {
         if(bucket.getAlias() != null && bucket.getAlias().equals(alias))
         {
            return (bucket);
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** Validate: names, references to connections, the BACKUP and trash rules, and
    ** the secret-reference allow-list.
    *******************************************************************************/
   @Override
   public void validate(QInstance qInstance, List<String> errors)
   {
      assertCondition(StringUtils.hasContent(backendName), "backendName is required for the Object Storage QBit", errors);

      Set<String> connectionNames = new HashSet<>();
      for(StorageConnectionConfig connection : connections)
      {
         if(!assertCondition(StringUtils.hasContent(connection.getName()), "Every storage connection needs a name", errors))
         {
            continue;
         }
         assertCondition(connectionNames.add(connection.getName()), "Duplicate storage connection name: " + connection.getName(), errors);
         for(String ref : new String[] { connection.getAccessKeyRef(), connection.getSecretKeyRef(), connection.getSessionTokenRef(),
            connection.getPresignAccessKeyRef(), connection.getPresignSecretKeyRef(), connection.getCaBundleRef() })
         {
            assertCondition(SecretRefs.isAllowed(ref, allowedSecretRefPatterns),
               "Connection " + connection.getName() + ": a secret reference is not an allowed reference (allowed: " + allowedSecretRefPatterns + ")", errors);
         }
         if(Boolean.FALSE.equals(connection.getTlsVerify()) && StringUtils.hasContent(connection.getEndpoint()))
         {
            assertCondition(connection.getEndpoint().startsWith("http://") || connection.getEndpoint().startsWith("${"),
               "Connection " + connection.getName() + ": tlsVerify=false is allowed only for http:// development endpoints", errors);
         }
      }

      Set<String> aliases = new HashSet<>();
      for(StorageBucketConfig bucket : buckets)
      {
         if(!assertCondition(StringUtils.hasContent(bucket.getAlias()), "Every storage bucket needs an alias", errors))
         {
            continue;
         }
         assertCondition(aliases.add(bucket.getAlias()), "Duplicate storage bucket alias: " + bucket.getAlias(), errors);
         assertCondition(connectionNames.contains(bucket.getConnection()), "Bucket " + bucket.getAlias() + " names an unknown connection: " + bucket.getConnection(), errors);
         assertCondition(StringUtils.hasContent(bucket.getBucketName()), "Bucket " + bucket.getAlias() + " needs a bucketName", errors);
         if(bucket.getRole() == BucketRole.BACKUP)
         {
            assertCondition(!StringUtils.hasContent(bucket.getPublicBaseUrl()), "Bucket " + bucket.getAlias() + " has role BACKUP, which is always private: it cannot have a public base URL", errors);
         }
         if(Boolean.TRUE.equals(bucket.getTrashEnabled()))
         {
            StorageBucketConfig trashTarget = StringUtils.hasContent(bucket.getTrashBucket()) ? findBucket(bucket.getTrashBucket()) : bucket;
            if(trashTarget == null)
            {
               errors.add("Bucket " + bucket.getAlias() + " names an unknown trash bucket: " + bucket.getTrashBucket());
            }
            else
            {
               assertCondition(trashTarget.getRole() != BucketRole.PUBLIC && !StringUtils.hasContent(trashTarget.getPublicBaseUrl()),
                  "Bucket " + bucket.getAlias() + ": the trash location must be a private bucket (a trash prefix in a public bucket would leave deleted files readable)", errors);
            }
         }
      }
   }



   /*******************************************************************************
    ** Tables without their own backend use the configured backend.
    *******************************************************************************/
   @Override
   public String getDefaultBackendNameForTables()
   {
      return (backendName);
   }

}
