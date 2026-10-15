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


import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qbits.objectstorage.api.Capability;
import com.kingsrook.qbits.objectstorage.api.CapabilityStatus;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.cluster.ClusterCaches;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Capability rows (per connection and per bucket): reads them (cached), falls
 ** back to the provider defaults, and records probe results. UNKNOWN is treated
 ** as unsupported for writes by the UI.
 *******************************************************************************/
public final class CapabilityStore
{
   private static final QLogger LOG = QLogger.getLogger(CapabilityStore.class);

   /** Capabilities that are properties of the connection (the rest are per bucket). */
   public static final Set<Capability> CONNECTION_SCOPE = EnumSet.of(Capability.LIST_V2, Capability.PRESIGNED_GET, Capability.PRESIGNED_PUT,
      Capability.PRESIGNED_POST, Capability.USAGE_ADMIN_API, Capability.QUOTA_ADMIN_API);

   /** Cluster cache name of the capability statuses (a probe result recorded on one node clears that key on every node). */
   public static final String CACHE_NAME = "qbit-object-storage.capabilities";

   private static final Map<String, CapabilityStatus> CACHE  = ClusterCaches.shared(CACHE_NAME, new ConcurrentHashMap<>(),
      CapabilityStore::clearCache, CapabilityStore::clearKey);
   private static final Set<String>                   LOADED = ConcurrentHashMap.newKeySet();



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private CapabilityStore()
   {
   }



   /*******************************************************************************
    ** Drop everything cached on this node.
    *******************************************************************************/
   public static void clearCache()
   {
      CACHE.clear();
      LOADED.clear();
   }



   /*******************************************************************************
    ** Drop one cached status on this node; the next read reloads the missing
    ** statuses from the capability rows (cached ones are kept).
    *******************************************************************************/
   static void clearKey(String key)
   {
      CACHE.remove(key);
      LOADED.clear();
   }



   /*******************************************************************************
    ** Whether a status is cached on this node (tests).
    *******************************************************************************/
   static boolean isCached(StorageBucketConfig bucket, Capability capability)
   {
      return (CACHE.containsKey(scopeKey(bucket, capability)));
   }



   /*******************************************************************************
    ** The status of a capability for a bucket alias.
    *******************************************************************************/
   public static CapabilityStatus status(String bucketAlias, Capability capability)
   {
      StorageBucketConfig bucket = ObjectStorageRuntime.bucketConfig(bucketAlias);
      loadIfNeeded(bucket);
      String key = scopeKey(bucket, capability);
      CapabilityStatus found = CACHE.get(key);
      if(found != null)
      {
         return (found);
      }
      return (ProviderDefaults.defaultStatus(ObjectStorageRuntime.getConfig().findConnection(bucket.getConnection()).getProviderType(), capability));
   }



   /*******************************************************************************
    ** Whether the status allows using the feature (known good).
    *******************************************************************************/
   public static boolean usable(CapabilityStatus status)
   {
      return (status == CapabilityStatus.SUPPORTED || status == CapabilityStatus.PARTIAL);
   }



   /*******************************************************************************
    ** Whether the status rules a feature out (known bad), as opposed to untested.
    *******************************************************************************/
   public static boolean ruledOut(CapabilityStatus status)
   {
      return (status == CapabilityStatus.UNSUPPORTED || status == CapabilityStatus.NOT_PERMITTED);
   }



   /*******************************************************************************
    ** Record one probe result (cache and, when the tables are there, the row).
    ** The row is written first, then the key is cleared on every node (they
    ** reload it from the row), then cached here.
    *******************************************************************************/
   public static void record(StorageBucketConfig bucket, Capability capability, CapabilityStatus status, String detail)
   {
      String key = scopeKey(bucket, capability);
      persist(bucket, capability, status, detail);
      ClusterCaches.invalidateKey(CACHE_NAME, key);
      CACHE.put(key, status);
   }



   /*******************************************************************************
    ** Write one probe result to its capability row; never throws.
    *******************************************************************************/
   private static void persist(StorageBucketConfig bucket, Capability capability, CapabilityStatus status, String detail)
   {
      if(!Db.available(ObjectStorageTables.BUCKET_CAPABILITY))
      {
         return;
      }
      try
      {
         boolean connectionScope = CONNECTION_SCOPE.contains(capability);
         String  table           = connectionScope ? ObjectStorageTables.CONNECTION_CAPABILITY : ObjectStorageTables.BUCKET_CAPABILITY;
         String  idField         = connectionScope ? "connectionId" : "bucketId";
         Integer id              = connectionScope ? ConfigSync.connectionId(bucket.getConnection()) : ConfigSync.bucketId(bucket.getAlias());
         if(id == null)
         {
            return;
         }
         Db.asSystem(() ->
         {
            QRecord row = Db.first(table, idField, id, "capability", capability.name());
            QRecord values = new QRecord().withValue(idField, id).withValue("capability", capability.name()).withValue("status", status.name())
               .withValue("detail", detail).withValue("source", "PROBE").withValue("probedAt", ObjectStorageRuntime.clock().instant());
            if(row == null)
            {
               Db.insert(table, values);
            }
            else
            {
               Db.update(table, List.of(values.withValue("id", row.getValue("id"))));
            }
            return (null);
         });
      }
      catch(Exception e)
      {
         LOG.warn("Could not record a storage capability", e);
      }
   }



   /*******************************************************************************
    ** The cache key: connection-scoped capabilities share one key per connection.
    *******************************************************************************/
   private static String scopeKey(StorageBucketConfig bucket, Capability capability)
   {
      return (CONNECTION_SCOPE.contains(capability) ? "c:" + bucket.getConnection() + ":" + capability : "b:" + bucket.getAlias() + ":" + capability);
   }



   /*******************************************************************************
    ** Load the persisted rows for a bucket (and its connection) once.
    *******************************************************************************/
   private static void loadIfNeeded(StorageBucketConfig bucket)
   {
      if(!LOADED.add(bucket.getAlias()) || !Db.available(ObjectStorageTables.BUCKET_CAPABILITY))
      {
         return;
      }
      try
      {
         Integer bucketId     = ConfigSync.bucketId(bucket.getAlias());
         Integer connectionId = ConfigSync.connectionId(bucket.getConnection());
         Db.asSystem(() ->
         {
            if(bucketId != null)
            {
               for(QRecord row : Db.query(ObjectStorageTables.BUCKET_CAPABILITY, "bucketId", bucketId))
               {
                  CACHE.putIfAbsent("b:" + bucket.getAlias() + ":" + row.getValueString("capability"), CapabilityStatus.valueOf(row.getValueString("status")));
               }
            }
            if(connectionId != null)
            {
               for(QRecord row : Db.query(ObjectStorageTables.CONNECTION_CAPABILITY, "connectionId", connectionId))
               {
                  CACHE.putIfAbsent("c:" + bucket.getConnection() + ":" + row.getValueString("capability"), CapabilityStatus.valueOf(row.getValueString("status")));
               }
            }
            return (null);
         });
      }
      catch(Exception e)
      {
         LOG.warn("Could not load storage capabilities", e);
      }
   }
}
