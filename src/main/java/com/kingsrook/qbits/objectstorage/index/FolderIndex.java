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


import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** The folder tree of an indexed bucket: one object_storage_folder row per
 ** prefix, with parentFolderId giving the tree and object count, bytes and
 ** newest change rolled up (a folder counts everything beneath it).
 *******************************************************************************/
public final class FolderIndex
{
   private final Integer              bucketId;
   private final Map<String, Integer> idByPath = new HashMap<>();



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   private FolderIndex(Integer bucketId)
   {
      this.bucketId = bucketId;
   }



   /*******************************************************************************
    ** An index with nothing cached: each folder is looked up on demand (for
    ** one-off write-through, where loading the whole tree would be wasteful).
    *******************************************************************************/
   public static FolderIndex empty(Integer bucketId)
   {
      return (new FolderIndex(bucketId));
   }



   /*******************************************************************************
    ** Load every existing folder of the bucket (cheap next to the object rows).
    *******************************************************************************/
   public static FolderIndex load(Integer bucketId) throws QException
   {
      FolderIndex index = new FolderIndex(bucketId);
      Db.forEachPage(ObjectStorageTables.FOLDER, new QQueryFilter(Db.eq("bucketId", bucketId)), 5000, rows ->
      {
         for(QRecord row : rows)
         {
            index.idByPath.put(row.getValueString("path"), row.getValueInteger("id"));
         }
      });
      return (index);
   }



   /*******************************************************************************
    ** The folder id of a key's parent prefix, creating the chain of folders as
    ** needed. Null for a key at the root.
    *******************************************************************************/
   public Integer ensureFor(String key) throws QException
   {
      Integer parentId = null;
      int     slash    = key.indexOf('/');
      while(slash >= 0)
      {
         String path = key.substring(0, slash + 1);
         Integer id  = idByPath.get(path);
         if(id == null)
         {
            QRecord existing = Db.first(ObjectStorageTables.FOLDER, "bucketId", bucketId, "path", path);
            if(existing != null)
            {
               id = existing.getValueInteger("id");
            }
            else
            {
               String trimmed = path.substring(0, path.length() - 1);
               id = Db.insert(ObjectStorageTables.FOLDER, new QRecord().withValue("bucketId", bucketId).withValue("parentFolderId", parentId).withValue("path", path)
                  .withValue("name", trimmed.substring(trimmed.lastIndexOf('/') + 1)).withValue("objectCount", 0L).withValue("totalBytes", 0L)).getValueInteger("id");
            }
            idByPath.put(path, id);
         }
         parentId = id;
         slash = key.indexOf('/', slash + 1);
      }
      return (parentId);
   }



   /*******************************************************************************
    ** The id of a folder path, or null.
    *******************************************************************************/
   public Integer idOf(String path)
   {
      return (idByPath.get(path));
   }



   /*******************************************************************************
    ** Folder counts and sizes, direct (per folder) and the grand total.
    *******************************************************************************/
   public static class Rollup
   {
      final Map<Integer, long[]>    direct   = new HashMap<>();
      final Map<Integer, Instant>   newest   = new HashMap<>();
      long                          totalCount = 0;
      long                          totalBytes = 0;



      /*******************************************************************************
       ** Add one live object.
       *******************************************************************************/
      public void add(Integer folderId, Long size, Instant lastModified)
      {
         totalCount++;
         totalBytes += size == null ? 0 : size;
         if(folderId != null)
         {
            long[] c = direct.computeIfAbsent(folderId, k -> new long[2]);
            c[0]++;
            c[1] += size == null ? 0 : size;
            if(lastModified != null && (newest.get(folderId) == null || lastModified.isAfter(newest.get(folderId))))
            {
               newest.put(folderId, lastModified);
            }
         }
      }
   }



   /*******************************************************************************
    ** Write every folder's rolled-up totals: a folder counts everything beneath it.
    *******************************************************************************/
   public static void writeRollups(Integer bucketId, Rollup rollup) throws QException
   {
      Map<Integer, Integer> parentOf = new HashMap<>();
      List<Integer>         ids      = new ArrayList<>();
      Db.forEachPage(ObjectStorageTables.FOLDER, new QQueryFilter(Db.eq("bucketId", bucketId)), 5000, rows ->
      {
         for(QRecord row : rows)
         {
            parentOf.put(row.getValueInteger("id"), row.getValueInteger("parentFolderId"));
            ids.add(row.getValueInteger("id"));
         }
      });
      Map<Integer, long[]>  total  = new HashMap<>();
      Map<Integer, Instant> newest = new HashMap<>();
      for(Map.Entry<Integer, long[]> entry : rollup.direct.entrySet())
      {
         Instant when = rollup.newest.get(entry.getKey());
         for(Integer id = entry.getKey(); id != null; id = parentOf.get(id))
         {
            long[] t = total.computeIfAbsent(id, k -> new long[2]);
            t[0] += entry.getValue()[0];
            t[1] += entry.getValue()[1];
            if(when != null && (newest.get(id) == null || when.isAfter(newest.get(id))))
            {
               newest.put(id, when);
            }
         }
      }
      List<QRecord> updates = new ArrayList<>();
      for(Integer id : ids)
      {
         long[] t = total.getOrDefault(id, new long[2]);
         updates.add(new QRecord().withValue("id", id).withValue("objectCount", t[0]).withValue("totalBytes", t[1]).withValue("newestChange", newest.get(id)));
      }
      for(int i = 0; i < updates.size(); i += 1000)
      {
         Db.update(ObjectStorageTables.FOLDER, updates.subList(i, Math.min(updates.size(), i + 1000)));
      }
   }



   /*******************************************************************************
    ** Apply a delta to every ancestor folder of a key (write-through), and to the
    ** bucket totals.
    *******************************************************************************/
   public static void applyDelta(Integer bucketId, String key, long countDelta, long bytesDelta, Instant newest) throws QException
   {
      int slash = key.indexOf('/');
      while(slash >= 0)
      {
         QRecord folder = Db.first(ObjectStorageTables.FOLDER, "bucketId", bucketId, "path", key.substring(0, slash + 1));
         if(folder != null)
         {
            QRecord update = new QRecord().withValue("id", folder.getValue("id"))
               .withValue("objectCount", Math.max(0, (folder.getValueLong("objectCount") == null ? 0 : folder.getValueLong("objectCount")) + countDelta))
               .withValue("totalBytes", Math.max(0, (folder.getValueLong("totalBytes") == null ? 0 : folder.getValueLong("totalBytes")) + bytesDelta));
            if(newest != null && (folder.getValueInstant("newestChange") == null || newest.isAfter(folder.getValueInstant("newestChange"))))
            {
               update.withValue("newestChange", newest);
            }
            Db.update(ObjectStorageTables.FOLDER, List.of(update));
         }
         slash = key.indexOf('/', slash + 1);
      }
      QRecord bucket = Db.first(ObjectStorageTables.BUCKET, "id", bucketId);
      if(bucket != null)
      {
         Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", bucketId)
            .withValue("objectCount", Math.max(0, (bucket.getValueLong("objectCount") == null ? 0 : bucket.getValueLong("objectCount")) + countDelta))
            .withValue("totalBytes", Math.max(0, (bucket.getValueLong("totalBytes") == null ? 0 : bucket.getValueLong("totalBytes")) + bytesDelta))));
      }
   }
}
