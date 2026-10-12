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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.objectstorage.api.ListRequest;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaders;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.api.ObjectSummary;
import com.kingsrook.qbits.objectstorage.api.Page;
import com.kingsrook.qbits.objectstorage.api.StorageBucket;
import com.kingsrook.qbits.objectstorage.api.StoredObject;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The object index: a real QQQ table (object_storage_object) kept in step with
 ** a bucket, because S3 listing is a forward-only cursor with no server-side
 ** sort, filter, count or page N.
 **
 ** - Full scan: streams ListObjectsV2 pages, upserts rows in batches, marks each
 **   row with the scan generation, and at the end removes rows not seen, with a
 **   safety stop when more than a configurable share would go (usually a wrong
 **   prefix or credentials). The continuation token is saved on the bucket row
 **   so a large scan resumes after a restart.
 ** - HEAD backlog: objects whose headers are unknown get HEADed under the rate
 **   limit, oldest first.
 ** - Write-through: see ObjectIndexHook. An index failure never fails a write.
 ** - Drift: writes made outside QQQ appear at the next scan.
 **
 ** Deleted objects of a versioned or trash-enabled bucket are kept as rows with
 ** deleted=true (so Restore can find them) until the trash retention passes.
 *******************************************************************************/
public final class ObjectIndex
{
   private static final QLogger LOG = QLogger.getLogger(ObjectIndex.class);

   public static final int PAGE_SIZE = 1000;

   private static final Set<String> RUNNING = java.util.concurrent.ConcurrentHashMap.newKeySet();

   /** Test seam: simulate a crash after this many pages of a scan (so resume can be tested). */
   static volatile Integer failAfterPages = null;



   /*******************************************************************************
    ** The outcome of a scan.
    *******************************************************************************/
   public record ScanResult(String status, long seen, long added, long updated, long removed, long markedDeleted, int pages, String note)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ObjectIndex()
   {
   }



   /*******************************************************************************
    ** Whether a scan of the bucket is running in this app instance.
    *******************************************************************************/
   public static boolean isScanning(String alias)
   {
      return (RUNNING.contains(alias));
   }



   /*******************************************************************************
    ** Scan a bucket (at most one running scan per bucket). Never throws for a
    ** provider error: the result carries the status and the bucket row says so.
    *******************************************************************************/
   public static ScanResult scan(String alias) throws QException
   {
      StorageBucketConfig config = ObjectStorageRuntime.bucketConfig(alias);
      if(!Boolean.TRUE.equals(config.getIndexed()))
      {
         return (new ScanResult("NOT_INDEXED", 0, 0, 0, 0, 0, 0, "Bucket " + alias + " is not indexed"));
      }
      if(!RUNNING.add(alias))
      {
         return (new ScanResult("ALREADY_RUNNING", 0, 0, 0, 0, 0, 0, "A scan of " + alias + " is already running"));
      }
      try
      {
         return (Db.asSystem(() -> doScan(config)));
      }
      catch(QException | RuntimeException e)
      {
         markBucket(config, "FAILED", null, null);
         ObjectStorageRuntime.alertSink().raise("storage.scan.stale", "bucket:" + alias, "warning", "The scan of " + alias + " failed: " + e.getMessage(), Map.of("bucket", alias));
         throw (e);
      }
      finally
      {
         RUNNING.remove(alias);
      }
   }



   /*******************************************************************************
    ** The scan itself (runs as the system user).
    *******************************************************************************/
   private static ScanResult doScan(StorageBucketConfig config) throws QException
   {
      String   alias    = config.getAlias();
      Integer  bucketId = requireBucketId(alias);
      QRecord  bucket   = Db.first(ObjectStorageTables.BUCKET, "id", bucketId);
      boolean  resume   = ("RUNNING".equals(bucket.getValueString("lastScanStatus")) || "FAILED".equals(bucket.getValueString("lastScanStatus"))) && StringUtils.hasContent(bucket.getValueString("scanCursor"));
      long     gen      = bucket.getValueLong("scanGeneration") == null ? 1 : bucket.getValueLong("scanGeneration") + (resume ? 0 : 1);
      String   token    = resume ? bucket.getValueString("scanCursor") : null;
      long     existing = Db.count(ObjectStorageTables.OBJECT, new QQueryFilter(Db.eq("bucketId", bucketId), Db.eq("deleted", false)));

      Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", bucketId).withValue("lastScanStatus", "RUNNING").withValue("scanGeneration", gen)
         .withValue("scanCursor", token)));

      StorageBucket     storage   = ObjectStorage.asSystem("object-storage", "scan-" + alias).bucket(alias);
      FolderIndex       folders   = FolderIndex.load(bucketId);
      long              seen      = 0;
      long              added     = 0;
      long              updated   = 0;
      int               pages     = 0;
      boolean           capped    = false;
      String            fatal     = null;
      Instant           now       = ObjectStorageRuntime.clock().instant();

      do
      {
         Page<ObjectSummary> page;
         try
         {
            page = storage.list(ListRequest.prefix("").pageSize(PAGE_SIZE).after(token));
         }
         catch(RuntimeException e)
         {
            fatal = e.getMessage();
            break;
         }
         pages++;
         long[] counts = upsertPage(bucketId, gen, page.getItems(), folders, now);
         added += counts[0];
         updated += counts[1];
         seen += page.getItems().size();
         token = page.getNextToken();
         Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", bucketId).withValue("scanCursor", token)));
         if(failAfterPages != null && pages >= failAfterPages && token != null)
         {
            failAfterPages = null;
            throw (new IllegalStateException("Simulated crash during a scan"));
         }
         if(seen > ObjectStorageRuntime.getConfig().getIndexMaxKeys())
         {
            capped = true;
            break;
         }
      }
      while(token != null);

      if(fatal != null)
      {
         markBucket(config, "FAILED", gen, null);
         ObjectStorageRuntime.alertSink().raise("storage.scan.stale", "bucket:" + alias, "warning", "The scan of " + alias + " failed: " + fatal, Map.of("bucket", alias));
         return (new ScanResult("FAILED", seen, added, updated, 0, 0, pages, fatal));
      }
      if(capped)
      {
         markBucket(config, "CAP_REACHED", gen, null);
         return (new ScanResult("CAP_REACHED", seen, added, updated, 0, 0, pages, "More than " + ObjectStorageRuntime.getConfig().getIndexMaxKeys() + " keys: the index stopped at the cap"));
      }

      // the listing completed: handle rows that were not seen
      List<Serializable> unseenIds = new ArrayList<>();
      List<QRecord>      toMark    = new ArrayList<>();
      boolean            keepDeleted = isSoftDeleteBucket(config);
      QQueryFilter unseenFilter = new QQueryFilter(Db.eq("bucketId", bucketId), Db.eq("deleted", false), new QFilterCriteria("scanGeneration", QCriteriaOperator.LESS_THAN, gen));
      Db.forEachPage(ObjectStorageTables.OBJECT, unseenFilter, 5000, rows -> rows.forEach(row -> unseenIds.add(row.getValueInteger("id"))));
      double share = ObjectStorageRuntime.getConfig().getScanRemovalSafetyShare();
      if(existing > 20 && unseenIds.size() > existing * share)
      {
         String note = "The scan would remove " + unseenIds.size() + " of " + existing + " indexed objects (more than " + Math.round(share * 100)
            + "%), which usually means a wrong prefix or credentials. Nothing was removed.";
         markBucket(config, "STOPPED_SAFETY", gen, null);
         ObjectStorageRuntime.alertSink().raise("storage.scan.stale", "bucket:" + alias, "warning", note, Map.of("bucket", alias));
         recomputeStats(config);
         return (new ScanResult("STOPPED_SAFETY", seen, added, updated, 0, 0, pages, note));
      }
      long removed = 0;
      long marked  = 0;
      if(keepDeleted)
      {
         for(Serializable id : unseenIds)
         {
            toMark.add(new QRecord().withValue("id", id).withValue("deleted", true).withValue("indexedAt", now));
         }
         for(int i = 0; i < toMark.size(); i += PAGE_SIZE)
         {
            Db.update(ObjectStorageTables.OBJECT, toMark.subList(i, Math.min(toMark.size(), i + PAGE_SIZE)));
         }
         marked = toMark.size();
      }
      else
      {
         for(int i = 0; i < unseenIds.size(); i += PAGE_SIZE)
         {
            Db.delete(ObjectStorageTables.OBJECT, unseenIds.subList(i, Math.min(unseenIds.size(), i + PAGE_SIZE)));
         }
         removed = unseenIds.size();
      }
      if(keepDeleted)
      {
         removed += purgeExpiredDeletedRows(config, bucketId, now);
      }

      markBucket(config, "OK", gen, now);
      recomputeStats(config);
      ObjectStorageRuntime.alertSink().resolve("storage.scan.stale", "bucket:" + alias);
      return (new ScanResult("OK", seen, added, updated, removed, marked, pages, null));
   }



   /*******************************************************************************
    ** Upsert one page of listed objects; returns {added, updated}.
    *******************************************************************************/
   private static long[] upsertPage(Integer bucketId, long gen, List<ObjectSummary> items, FolderIndex folders, Instant now) throws QException
   {
      if(items.isEmpty())
      {
         return (new long[] { 0, 0 });
      }
      List<Serializable> keys = new ArrayList<>();
      items.forEach(i -> keys.add(i.getKey()));
      Map<String, QRecord> existing = new HashMap<>();
      for(QRecord row : Db.query(ObjectStorageTables.OBJECT, new QQueryFilter(Db.eq("bucketId", bucketId), new QFilterCriteria("objectKey", QCriteriaOperator.IN, keys))))
      {
         existing.put(row.getValueString("objectKey"), row);
      }
      List<QRecord> inserts = new ArrayList<>();
      List<QRecord> updates = new ArrayList<>();
      for(ObjectSummary s : items)
      {
         Integer folderId = folders.ensureFor(s.getKey());
         QRecord row      = existing.get(s.getKey());
         String  etag     = cleanEtag(s.getEtag());
         if(row == null)
         {
            inserts.add(newRow(bucketId, s.getKey(), folderId, s.getSize(), etag, s.getLastModified(), s.getStorageClass(), gen, now));
         }
         else
         {
            boolean changed = !java.util.Objects.equals(row.getValueLong("size"), s.getSize()) || !java.util.Objects.equals(row.getValueString("etag"), etag)
               || !java.util.Objects.equals(row.getValueInstant("lastModified"), s.getLastModified());
            QRecord update = new QRecord().withValue("id", row.getValue("id")).withValue("folderId", folderId).withValue("size", s.getSize()).withValue("etag", etag)
               .withValue("lastModified", s.getLastModified()).withValue("storageClass", s.getStorageClass()).withValue("scanGeneration", gen).withValue("indexedAt", now)
               .withValue("deleted", false);
            if(changed)
            {
               update.withValue("headersKnown", false);
            }
            updates.add(update);
         }
      }
      if(!inserts.isEmpty())
      {
         Db.insert(ObjectStorageTables.OBJECT, inserts);
      }
      if(!updates.isEmpty())
      {
         Db.update(ObjectStorageTables.OBJECT, updates);
      }
      return (new long[] { inserts.size(), updates.size() });
   }



   /*******************************************************************************
    ** A new object row.
    *******************************************************************************/
   static QRecord newRow(Integer bucketId, String key, Integer folderId, Long size, String etag, Instant lastModified, String storageClass, Long gen, Instant now)
   {
      String name = key.substring(key.lastIndexOf('/') + 1);
      int    dot  = name.lastIndexOf('.');
      return (new QRecord().withValue("bucketId", bucketId).withValue("folderId", folderId).withValue("objectKey", key).withValue("name", name)
         .withValue("extension", dot > 0 ? name.substring(dot + 1).toLowerCase() : null).withValue("size", size).withValue("etag", etag).withValue("lastModified", lastModified)
         .withValue("storageClass", storageClass).withValue("referenceCount", 0).withValue("headersKnown", false).withValue("deleted", false)
         .withValue("scanGeneration", gen).withValue("indexedAt", now));
   }



   /*******************************************************************************
    ** The ETag without its quotes.
    *******************************************************************************/
   static String cleanEtag(String etag)
   {
      return (etag == null ? null : etag.replace("\"", ""));
   }



   /*******************************************************************************
    ** Whether deleting from the bucket is reversible (versioning or trash), in
    ** which case unseen rows are kept as deleted rows so Restore can find them.
    *******************************************************************************/
   static boolean isSoftDeleteBucket(StorageBucketConfig config)
   {
      if(Boolean.TRUE.equals(config.getTrashEnabled()))
      {
         return (true);
      }
      try
      {
         return (ObjectStorage.asSystem("object-storage", "index").bucket(config.getAlias()).isVersioned());
      }
      catch(RuntimeException e)
      {
         return (false);
      }
   }



   /*******************************************************************************
    ** Remove deleted rows older than the trash retention.
    *******************************************************************************/
   private static long purgeExpiredDeletedRows(StorageBucketConfig config, Integer bucketId, Instant now) throws QException
   {
      Instant cutoff = now.minus(java.time.Duration.ofDays(config.getTrashRetentionDays()));
      List<Serializable> ids = new ArrayList<>();
      Db.forEachPage(ObjectStorageTables.OBJECT, new QQueryFilter(Db.eq("bucketId", bucketId), Db.eq("deleted", true), new QFilterCriteria("indexedAt", QCriteriaOperator.LESS_THAN, cutoff)), 5000,
         rows -> rows.forEach(r -> ids.add(r.getValueInteger("id"))));
      for(int i = 0; i < ids.size(); i += PAGE_SIZE)
      {
         Db.delete(ObjectStorageTables.OBJECT, ids.subList(i, Math.min(ids.size(), i + PAGE_SIZE)));
      }
      return (ids.size());
   }



   /*******************************************************************************
    ** Set the scan status on the bucket row.
    *******************************************************************************/
   private static void markBucket(StorageBucketConfig config, String status, Long gen, Instant completedAt)
   {
      try
      {
         Integer id = ConfigSync.bucketId(config.getAlias());
         if(id != null)
         {
            QRecord update = new QRecord().withValue("id", id).withValue("lastScanStatus", status);
            if(completedAt != null)
            {
               update.withValue("lastScanDate", completedAt).withValue("scanCursor", null);
            }
            Db.asSystem(() -> Db.update(ObjectStorageTables.BUCKET, List.of(update)));
         }
      }
      catch(Exception e)
      {
         LOG.warn("Could not update the scan status", e, logPair("bucket", config.getAlias()));
      }
   }



   /*******************************************************************************
    ** Recompute the folder rollups and the bucket totals and header coverage from
    ** the object rows.
    *******************************************************************************/
   public static void recomputeStats(StorageBucketConfig config) throws QException
   {
      Integer bucketId = requireBucketId(config.getAlias());
      Db.asSystem(() ->
      {
         FolderIndex.Rollup rollup = new FolderIndex.Rollup();
         Db.forEachPage(ObjectStorageTables.OBJECT, new QQueryFilter(Db.eq("bucketId", bucketId), Db.eq("deleted", false)), 5000, rows ->
         {
            for(QRecord row : rows)
            {
               rollup.add(row.getValueInteger("folderId"), row.getValueLong("size"), row.getValueInstant("lastModified"));
            }
         });
         FolderIndex.writeRollups(bucketId, rollup);
         long known = Db.count(ObjectStorageTables.OBJECT, new QQueryFilter(Db.eq("bucketId", bucketId), Db.eq("deleted", false), Db.eq("headersKnown", true)));
         long match = Db.count(ObjectStorageTables.OBJECT, new QQueryFilter(Db.eq("bucketId", bucketId), Db.eq("deleted", false), Db.eq("matchesHeaderPolicy", true)));
         Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", bucketId).withValue("objectCount", rollup.totalCount).withValue("totalBytes", rollup.totalBytes)
            .withValue("headersKnownCount", known).withValue("headersMatchingPolicyCount", match)));
         return (null);
      });
   }



   /*******************************************************************************
    ** HEAD the objects whose headers are unknown (oldest first), up to max, and
    ** record their headers and whether they match the header policy. An object
    ** that no longer exists is marked deleted.
    *******************************************************************************/
   public static int headBacklog(String alias, int max) throws QException
   {
      StorageBucketConfig config = ObjectStorageRuntime.bucketConfig(alias);
      if(!Boolean.TRUE.equals(config.getIndexed()))
      {
         return (0);
      }
      Integer bucketId = requireBucketId(alias);
      return (Db.asSystem(() ->
      {
         List<QRecord> todo = new ArrayList<>();
         QQueryFilter filter = new QQueryFilter(Db.eq("bucketId", bucketId), Db.eq("deleted", false), Db.eq("headersKnown", false));
         filter.setLimit(max);
         filter.withOrderBy(new com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy("lastModified"));
         todo.addAll(Db.query(ObjectStorageTables.OBJECT, filter));
         StorageBucket storage = ObjectStorage.asSystem("object-storage", "head-" + alias).bucket(alias);
         List<QRecord> updates = new ArrayList<>();
         for(QRecord row : todo)
         {
            QRecord update = refreshRow(config, storage, row);
            if(update != null)
            {
               updates.add(update);
            }
         }
         for(int i = 0; i < updates.size(); i += PAGE_SIZE)
         {
            Db.update(ObjectStorageTables.OBJECT, updates.subList(i, Math.min(updates.size(), i + PAGE_SIZE)));
         }
         if(!updates.isEmpty())
         {
            recomputeStats(config);
         }
         return (updates.size());
      }));
   }



   /*******************************************************************************
    ** HEAD one object row and build the update (null when the HEAD failed).
    *******************************************************************************/
   static QRecord refreshRow(StorageBucketConfig config, StorageBucket storage, QRecord row)
   {
      String key = row.getValueString("objectKey");
      try
      {
         java.util.Optional<StoredObject> head = storage.head(key);
         if(head.isEmpty())
         {
            return (new QRecord().withValue("id", row.getValue("id")).withValue("deleted", true).withValue("indexedAt", ObjectStorageRuntime.clock().instant()));
         }
         return (headersUpdate(config, row.getValue("id"), head.get()));
      }
      catch(RuntimeException e)
      {
         LOG.warn("Could not HEAD an object to refresh the index", e, logPair("key", key));
         return (null);
      }
   }



   /*******************************************************************************
    ** The row update for a fully known object (HEAD result or write-through).
    *******************************************************************************/
   static QRecord headersUpdate(StorageBucketConfig config, Serializable id, StoredObject o)
   {
      ObjectHeaders headers = o.getHeaders() == null ? new ObjectHeaders() : o.getHeaders();
      QRecord update = new QRecord().withValue("id", id).withValue("size", o.getSize()).withValue("etag", cleanEtag(o.getEtag())).withValue("lastModified", o.getLastModified())
         .withValue("storageClass", o.getStorageClass()).withValue("contentType", headers.getContentType()).withValue("cacheControl", headers.getCacheControl())
         .withValue("contentDisposition", headers.getContentDisposition())
         .withValue("userMetadata", headers.getUserMetadata() == null || headers.getUserMetadata().isEmpty() ? null : JsonUtils.toJson(new LinkedHashMap<>(headers.getUserMetadata())))
         .withValue("versionId", o.getVersionId()).withValue("headersKnown", true).withValue("deleted", false).withValue("matchesHeaderPolicy", matchesPolicy(config, o.getKey(), headers))
         .withValue("indexedAt", ObjectStorageRuntime.clock().instant());
      return (update);
   }



   /*******************************************************************************
    ** Whether the observed headers equal what the policy says (no opinion counts as
    ** a match; BACKUP buckets are outside the policy).
    *******************************************************************************/
   public static boolean matchesPolicy(StorageBucketConfig config, String key, ObjectHeaders observed)
   {
      if(config.getRole() == BucketRole.BACKUP)
      {
         return (true);
      }
      ObjectHeaders wanted = ObjectStorageRuntime.headerPolicy().headersFor(config, key, observed.getContentType());
      if(wanted == null || (wanted.getCacheControl() == null && wanted.getContentDisposition() == null))
      {
         return (true);
      }
      return (wanted.getCacheControl() == null || java.util.Objects.equals(wanted.getCacheControl().trim(), observed.getCacheControl() == null ? null : observed.getCacheControl().trim()))
         && (wanted.getContentDisposition() == null || java.util.Objects.equals(wanted.getContentDisposition().trim(), observed.getContentDisposition() == null ? null : observed.getContentDisposition().trim()));
   }



   /*******************************************************************************
    ** The bucket's row id, failing clearly when the tables are not in the context.
    *******************************************************************************/
   static Integer requireBucketId(String alias)
   {
      Integer id = ConfigSync.bucketId(alias);
      if(id == null)
      {
         throw (new IllegalStateException("The bucket " + alias + " has no row: the storage tables are not available in this context"));
      }
      return (id);
   }



   /*******************************************************************************
    ** The distinct aliases of buckets that are indexed.
    *******************************************************************************/
   public static List<String> indexedAliases()
   {
      List<String> aliases = new ArrayList<>();
      for(StorageBucketConfig bucket : ObjectStorageRuntime.getConfig().getBuckets())
      {
         if(Boolean.TRUE.equals(bucket.getIndexed()))
         {
            aliases.add(bucket.getAlias());
         }
      }
      return (aliases);
   }



   /*******************************************************************************
    ** Keys of a set, for tests.
    *******************************************************************************/
   static Set<String> keysOf(List<QRecord> rows)
   {
      Set<String> keys = new HashSet<>();
      rows.forEach(r -> keys.add(r.getValueString("objectKey")));
      return (keys);
   }
}
