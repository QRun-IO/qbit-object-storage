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


import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.objectstorage.api.MatchMode;
import com.kingsrook.qbits.objectstorage.api.StorageReference;
import com.kingsrook.qbits.objectstorage.api.StorageReferenceProvider;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** The reference scan: runs after a bucket scan, rebuilds
 ** object_storage_object_reference from the registered StorageReferenceProviders,
 ** and sets object_storage_object.referenceCount.
 **
 ** - Orphaned = a live object in a reference-tracked bucket with referenceCount 0,
 **   outside the exempt prefixes.
 ** - Missing = a reference row with no matching object (no key match, or a prefix
 **   with zero objects).
 ** - Referenced objects are skipped by Delete and Move.
 **
 ** BACKUP buckets are never scanned: backup objects are not app media, so they
 ** are never orphaned.
 *******************************************************************************/
public final class ReferenceScan
{
   /*******************************************************************************
    ** The outcome of a reference scan.
    *******************************************************************************/
   public record Result(long references, long missing, long orphaned, long orphanedBytes)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ReferenceScan()
   {
   }



   /*******************************************************************************
    ** Whether the bucket takes part in reference scans.
    *******************************************************************************/
   public static boolean applies(StorageBucketConfig bucket)
   {
      return (Boolean.TRUE.equals(bucket.getReferenceTracked()) && bucket.getRole() != BucketRole.BACKUP && Boolean.TRUE.equals(bucket.getIndexed()));
   }



   /*******************************************************************************
    ** Run the scan for a bucket alias.
    *******************************************************************************/
   public static Result run(String alias) throws QException
   {
      StorageBucketConfig config = ObjectStorageRuntime.bucketConfig(alias);
      if(!applies(config))
      {
         return (new Result(0, 0, 0, 0));
      }
      Integer bucketId = ObjectIndex.requireBucketId(alias);
      return (Db.asSystem(() -> doRun(config, bucketId)));
   }



   /*******************************************************************************
    ** The scan itself.
    *******************************************************************************/
   private static Result doRun(StorageBucketConfig config, Integer bucketId) throws QException
   {
      String alias = config.getAlias();
      Instant now  = ObjectStorageRuntime.clock().instant();

      List<StorageReference> references = new ArrayList<>();
      for(StorageReferenceProvider provider : ObjectStorageRuntime.getConfig().getReferenceProviders())
      {
         provider.references(alias).forEach(references::add);
      }

      // resolve every reference against the index
      Map<String, Integer> idByKey = new HashMap<>();
      List<String>         keyRefs = new ArrayList<>();
      for(StorageReference ref : references)
      {
         if(ref.getMatchMode() == MatchMode.KEY)
         {
            keyRefs.add(ref.getKeyOrPrefix());
         }
      }
      for(int i = 0; i < keyRefs.size(); i += 500)
      {
         List<Serializable> slice = new ArrayList<>(keyRefs.subList(i, Math.min(keyRefs.size(), i + 500)));
         for(QRecord row : Db.query(ObjectStorageTables.OBJECT, new QQueryFilter(Db.eq("bucketId", bucketId), Db.eq("deleted", false), new QFilterCriteria("objectKey", QCriteriaOperator.IN, slice))))
         {
            idByKey.put(row.getValueString("objectKey"), row.getValueInteger("id"));
         }
      }

      Map<Integer, Integer> counts   = new HashMap<>();
      List<QRecord>         rows     = new ArrayList<>();
      long                  missing  = 0;
      Map<String, List<Integer>> prefixCache = new HashMap<>();
      for(StorageReference ref : references)
      {
         List<Integer> matched = new ArrayList<>();
         if(ref.getMatchMode() == MatchMode.KEY)
         {
            Integer id = idByKey.get(ref.getKeyOrPrefix());
            if(id != null)
            {
               matched.add(id);
            }
         }
         else
         {
            matched = prefixCache.computeIfAbsent(ref.getKeyOrPrefix(), p -> idsUnder(bucketId, p));
         }
         for(Integer id : matched)
         {
            counts.merge(id, 1, Integer::sum);
         }
         boolean isMissing = matched.isEmpty();
         if(isMissing)
         {
            missing++;
         }
         rows.add(new QRecord().withValue("bucketId", bucketId).withValue("objectId", matched.isEmpty() ? null : matched.get(0)).withValue("keyOrPrefix", ref.getKeyOrPrefix())
            .withValue("matchMode", ref.getMatchMode().name()).withValue("sourceTable", ref.getTableName()).withValue("sourceRecordId", ref.getRecordId())
            .withValue("sourceField", ref.getFieldName()).withValue("label", ref.getLabel()).withValue("missing", isMissing).withValue("scannedAt", now));
      }

      // rebuild the reference rows
      List<Serializable> oldIds = new ArrayList<>();
      Db.forEachPage(ObjectStorageTables.OBJECT_REFERENCE, new QQueryFilter(Db.eq("bucketId", bucketId)), 5000, old -> old.forEach(r -> oldIds.add(r.getValueInteger("id"))));
      for(int i = 0; i < oldIds.size(); i += 1000)
      {
         Db.delete(ObjectStorageTables.OBJECT_REFERENCE, oldIds.subList(i, Math.min(oldIds.size(), i + 1000)));
      }
      for(int i = 0; i < rows.size(); i += 1000)
      {
         Db.insert(ObjectStorageTables.OBJECT_REFERENCE, rows.subList(i, Math.min(rows.size(), i + 1000)));
      }

      // set referenceCount where it changed
      Set<Integer>  seenWithCount = new HashSet<>();
      List<QRecord> updates       = new ArrayList<>();
      Db.forEachPage(ObjectStorageTables.OBJECT, new QQueryFilter(Db.eq("bucketId", bucketId), new QFilterCriteria("referenceCount", QCriteriaOperator.GREATER_THAN, 0)), 5000, objs ->
      {
         for(QRecord obj : objs)
         {
            Integer id = obj.getValueInteger("id");
            seenWithCount.add(id);
            int wanted = counts.getOrDefault(id, 0);
            if(obj.getValueInteger("referenceCount") != wanted)
            {
               updates.add(new QRecord().withValue("id", id).withValue("referenceCount", wanted));
            }
         }
      });
      for(Map.Entry<Integer, Integer> entry : counts.entrySet())
      {
         if(!seenWithCount.contains(entry.getKey()))
         {
            updates.add(new QRecord().withValue("id", entry.getKey()).withValue("referenceCount", entry.getValue()));
         }
      }
      for(int i = 0; i < updates.size(); i += 1000)
      {
         Db.update(ObjectStorageTables.OBJECT, updates.subList(i, Math.min(updates.size(), i + 1000)));
      }

      // orphans
      long[] orphaned = new long[2];
      Db.forEachPage(ObjectStorageTables.OBJECT, new QQueryFilter(Db.eq("bucketId", bucketId), Db.eq("deleted", false), Db.eq("referenceCount", 0)), 5000, objs ->
      {
         for(QRecord obj : objs)
         {
            if(!exempt(config, obj.getValueString("objectKey")))
            {
               orphaned[0]++;
               orphaned[1] += obj.getValueLong("size") == null ? 0 : obj.getValueLong("size");
            }
         }
      });
      Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", bucketId).withValue("orphanedCount", orphaned[0]).withValue("orphanedBytes", orphaned[1])
         .withValue("missingCount", missing).withValue("lastReferenceScanDate", now)));
      raiseAlerts(config, bucketId, missing, orphaned[0]);
      return (new Result(references.size(), missing, orphaned[0], orphaned[1]));
   }



   /*******************************************************************************
    ** The ids of live objects under a prefix.
    *******************************************************************************/
   private static List<Integer> idsUnder(Integer bucketId, String prefix)
   {
      List<Integer> ids = new ArrayList<>();
      try
      {
         Db.forEachPage(ObjectStorageTables.OBJECT, new QQueryFilter(Db.eq("bucketId", bucketId), Db.eq("deleted", false), new QFilterCriteria("objectKey", QCriteriaOperator.STARTS_WITH, prefix)), 5000,
            rows -> rows.forEach(r -> ids.add(r.getValueInteger("id"))));
      }
      catch(QException e)
      {
         throw (new IllegalStateException(e));
      }
      return (ids);
   }



   /*******************************************************************************
    ** Whether a key is under an exempt prefix (never reported as orphaned).
    *******************************************************************************/
   static boolean exempt(StorageBucketConfig config, String key)
   {
      for(String prefix : config.getExemptPrefixes())
      {
         if(key != null && key.startsWith(prefix))
         {
            return (true);
         }
      }
      return (false);
   }



   /*******************************************************************************
    ** Raise or resolve the missing and orphaned alerts (a no-op without the Alerts
    ** qbit).
    *******************************************************************************/
   private static void raiseAlerts(StorageBucketConfig config, Integer bucketId, long missing, long orphaned) throws QException
   {
      String alias = config.getAlias();
      if(missing > 0)
      {
         ObjectStorageRuntime.alertSink().raise("storage.objects.missing", "bucket:" + alias, "error", missing + " referenced objects are missing from " + alias, Map.of("bucket", alias, "count", missing));
      }
      else
      {
         ObjectStorageRuntime.alertSink().resolve("storage.objects.missing", "bucket:" + alias);
      }
      QRecord bucket    = Db.first(ObjectStorageTables.BUCKET, "id", bucketId);
      Integer threshold = bucket == null ? null : bucket.getValueInteger("orphanCountAlertThreshold");
      if(threshold != null && orphaned > threshold)
      {
         ObjectStorageRuntime.alertSink().raise("storage.objects.orphaned", "bucket:" + alias, "warning", orphaned + " orphaned objects in " + alias, Map.of("bucket", alias, "count", orphaned));
      }
      else
      {
         ObjectStorageRuntime.alertSink().resolve("storage.objects.orphaned", "bucket:" + alias);
      }
   }
}
