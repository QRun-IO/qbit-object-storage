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

package com.kingsrook.qbits.objectstorage.index;


import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.api.ListRequest;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.api.ObjectSummary;
import com.kingsrook.qbits.objectstorage.api.Page;
import com.kingsrook.qbits.objectstorage.api.StorageBucket;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The scheduled work: the nightly scan, HEAD backlog, reference scan, usage
 ** snapshot, trash purge, and the freshness and quota alerts. Each step is
 ** isolated: one failing bucket never stops the others.
 *******************************************************************************/
public final class StorageJobs
{
   private static final QLogger LOG = QLogger.getLogger(StorageJobs.class);



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private StorageJobs()
   {
   }



   /*******************************************************************************
    ** Run everything for every indexed bucket; returns one line per step.
    *******************************************************************************/
   public static List<String> nightly() throws QException
   {
      List<String> log = new ArrayList<>();
      for(String alias : ObjectIndex.indexedAliases())
      {
         try
         {
            ObjectIndex.ScanResult scan = ObjectIndex.scan(alias);
            log.add(alias + " scan: " + scan.status() + " (" + scan.seen() + " seen, " + scan.added() + " new, " + scan.removed() + " removed)");
            int headed = ObjectIndex.headBacklog(alias, ObjectStorageRuntime.getConfig().getHeadBacklogPerRun());
            log.add(alias + " HEAD backlog: " + headed + " objects");
            StorageBucketConfig config = ObjectStorageRuntime.bucketConfig(alias);
            if(ReferenceScan.applies(config))
            {
               ReferenceScan.Result r = ReferenceScan.run(alias);
               log.add(alias + " references: " + r.references() + ", missing " + r.missing() + ", orphaned " + r.orphaned());
            }
            usageSnapshot(alias);
         }
         catch(QException | RuntimeException e)
         {
            LOG.warn("A nightly storage step failed", e, logPair("bucket", alias));
            log.add(alias + " FAILED: " + e.getMessage());
         }
      }
      for(StorageBucketConfig bucket : ObjectStorageRuntime.getConfig().getBuckets())
      {
         if(Boolean.TRUE.equals(bucket.getTrashEnabled()))
         {
            try
            {
               log.add(bucket.getAlias() + " trash purge: " + purgeTrash(bucket.getAlias()) + " objects");
            }
            catch(RuntimeException e)
            {
               log.add(bucket.getAlias() + " trash purge FAILED: " + e.getMessage());
            }
         }
      }
      checkFreshness();
      return (log);
   }



   /*******************************************************************************
    ** Record today's usage for a bucket (one row per bucket per day) and raise or
    ** resolve the quota alert.
    *******************************************************************************/
   public static void usageSnapshot(String alias) throws QException
   {
      Integer bucketId = ObjectIndex.requireBucketId(alias);
      Db.asSystem(() ->
      {
         QRecord bucket = Db.first(ObjectStorageTables.BUCKET, "id", bucketId);
         Map<String, Map<String, Long>> byPrefix = new LinkedHashMap<>();
         Db.forEachPage(ObjectStorageTables.FOLDER, new QQueryFilter(Db.eq("bucketId", bucketId)), 5000, rows ->
         {
            for(QRecord folder : rows)
            {
               if(folder.getValueInteger("parentFolderId") == null)
               {
                  byPrefix.put(folder.getValueString("path"), Map.of("objects", folder.getValueLong("objectCount") == null ? 0L : folder.getValueLong("objectCount"),
                     "bytes", folder.getValueLong("totalBytes") == null ? 0L : folder.getValueLong("totalBytes")));
               }
            }
         });
         LocalDate today = LocalDate.ofInstant(ObjectStorageRuntime.clock().instant(), ZoneOffset.UTC);
         QRecord values = new QRecord().withValue("bucketId", bucketId).withValue("snapshotDate", today).withValue("objectCount", bucket.getValueLong("objectCount"))
            .withValue("totalBytes", bucket.getValueLong("totalBytes")).withValue("byPrefix", JsonUtils.toJson(byPrefix));
         QRecord existing = Db.first(ObjectStorageTables.USAGE_DAILY, "bucketId", bucketId, "snapshotDate", today);
         if(existing == null)
         {
            Db.insert(ObjectStorageTables.USAGE_DAILY, values);
         }
         else
         {
            Db.update(ObjectStorageTables.USAGE_DAILY, List.of(values.withValue("id", existing.getValue("id"))));
         }
         Long quota = bucket.getValueLong("quotaBytes");
         long used  = bucket.getValueLong("totalBytes") == null ? 0 : bucket.getValueLong("totalBytes");
         if(quota != null && quota > 0 && used >= quota * 0.8)
         {
            boolean critical = used >= quota * 0.95;
            ObjectStorageRuntime.alertSink().raise("storage.quota.near", "bucket:" + alias, critical ? "error" : "warning",
               alias + " uses " + Math.round(used * 100.0 / quota) + "% of its quota", Map.of("bucket", alias, "bytes", used, "quota", quota));
         }
         else
         {
            ObjectStorageRuntime.alertSink().resolve("storage.quota.near", "bucket:" + alias);
         }
         return (null);
      });
   }



   /*******************************************************************************
    ** Permanently delete trash objects older than the retention (the nightly purge
    ** for stores whose lifecycle rules cannot expire the trash). Returns the count.
    *******************************************************************************/
   public static int purgeTrash(String alias)
   {
      StorageBucketConfig config     = ObjectStorageRuntime.bucketConfig(alias);
      String              trashAlias = config.getTrashBucket() == null ? alias : config.getTrashBucket();
      StorageBucket       trash      = ObjectStorage.asSystem("object-storage", "trash-purge").bucket(trashAlias);
      Instant             cutoff     = ObjectStorageRuntime.clock().instant().minus(Duration.ofDays(config.getTrashRetentionDays()));
      List<String>        expired    = new ArrayList<>();
      String              token      = null;
      do
      {
         Page<ObjectSummary> page = trash.list(ListRequest.prefix(config.getTrashPrefix()).pageSize(1000).after(token));
         for(ObjectSummary o : page.getItems())
         {
            if(o.getLastModified() != null && o.getLastModified().isBefore(cutoff))
            {
               expired.add(o.getKey());
            }
         }
         token = page.getNextToken();
      }
      while(token != null);
      return (expired.isEmpty() ? 0 : trash.purgeKeys(expired));
   }



   /*******************************************************************************
    ** Raise storage.scan.stale for indexed buckets not scanned in 48 hours.
    *******************************************************************************/
   public static void checkFreshness() throws QException
   {
      Instant cutoff = ObjectStorageRuntime.clock().instant().minus(Duration.ofHours(48));
      for(String alias : ObjectIndex.indexedAliases())
      {
         Integer id = com.kingsrook.qbits.objectstorage.internal.ConfigSync.bucketId(alias);
         if(id == null)
         {
            continue;
         }
         QRecord bucket = Db.asSystem(() -> Db.first(ObjectStorageTables.BUCKET, "id", id));
         Instant last   = bucket == null ? null : bucket.getValueInstant("lastScanDate");
         if(last == null || last.isBefore(cutoff))
         {
            ObjectStorageRuntime.alertSink().raise("storage.scan.stale", "bucket:" + alias, "warning", "The index of " + alias + " has not completed a scan in 48 hours", Map.of("bucket", alias));
         }
      }
   }
}
