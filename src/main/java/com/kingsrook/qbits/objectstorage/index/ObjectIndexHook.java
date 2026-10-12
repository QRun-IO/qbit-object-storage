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
import java.util.List;
import com.kingsrook.qbits.objectstorage.api.StoredObject;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.IndexHook;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Write-through: every write made through the service updates the index
 ** immediately (the object row and the rolled-up folder and bucket totals). A
 ** failure here never fails the write; the next scan repairs the index.
 *******************************************************************************/
public class ObjectIndexHook implements IndexHook
{
   /*******************************************************************************
    ** An object was created or changed.
    *******************************************************************************/
   @Override
   public void objectWritten(StorageBucketConfig bucket, StoredObject object)
   {
      if(!applies(bucket))
      {
         return;
      }
      try
      {
         Db.asSystem(() ->
         {
            Integer bucketId = ObjectIndex.requireBucketId(bucket.getAlias());
            QRecord existing = Db.first(ObjectStorageTables.OBJECT, "bucketId", bucketId, "objectKey", object.getKey());
            Instant now      = ObjectStorageRuntime.clock().instant();
            if(existing == null)
            {
               Integer folderId = FolderIndex.empty(bucketId).ensureFor(object.getKey());
               QRecord row = ObjectIndex.newRow(bucketId, object.getKey(), folderId, object.getSize(), ObjectIndex.cleanEtag(object.getEtag()), object.getLastModified(), object.getStorageClass(),
                  currentGeneration(bucketId), now);
               QRecord inserted = Db.insert(ObjectStorageTables.OBJECT, row);
               Db.update(ObjectStorageTables.OBJECT, List.of(ObjectIndex.headersUpdate(bucket, inserted.getValue("id"), object)));
               FolderIndex.applyDelta(bucketId, object.getKey(), 1, object.getSize() == null ? 0 : object.getSize(), object.getLastModified());
            }
            else
            {
               boolean wasLive = !Boolean.TRUE.equals(existing.getValueBoolean("deleted"));
               long    before  = wasLive && existing.getValueLong("size") != null ? existing.getValueLong("size") : 0;
               Db.update(ObjectStorageTables.OBJECT, List.of(ObjectIndex.headersUpdate(bucket, existing.getValue("id"), object)));
               FolderIndex.applyDelta(bucketId, object.getKey(), wasLive ? 0 : 1, (object.getSize() == null ? 0 : object.getSize()) - before, object.getLastModified());
            }
            return (null);
         });
      }
      catch(QException | RuntimeException e)
      {
         throw (new IllegalStateException("Index write-through failed: " + e.getMessage(), e));
      }
   }



   /*******************************************************************************
    ** An object was removed from the live listing.
    *******************************************************************************/
   @Override
   public void objectRemoved(StorageBucketConfig bucket, String key, boolean soft)
   {
      if(!applies(bucket))
      {
         return;
      }
      try
      {
         Db.asSystem(() ->
         {
            Integer bucketId = ObjectIndex.requireBucketId(bucket.getAlias());
            QRecord existing = Db.first(ObjectStorageTables.OBJECT, "bucketId", bucketId, "objectKey", key);
            if(existing == null)
            {
               return (null);
            }
            boolean wasLive = !Boolean.TRUE.equals(existing.getValueBoolean("deleted"));
            long    size    = existing.getValueLong("size") == null ? 0 : existing.getValueLong("size");
            if(soft)
            {
               Db.update(ObjectStorageTables.OBJECT, List.of(new QRecord().withValue("id", existing.getValue("id")).withValue("deleted", true)
                  .withValue("indexedAt", ObjectStorageRuntime.clock().instant())));
            }
            else
            {
               Db.delete(ObjectStorageTables.OBJECT, List.of(existing.getValueInteger("id")));
            }
            if(wasLive)
            {
               FolderIndex.applyDelta(bucketId, key, -1, -size, null);
            }
            return (null);
         });
      }
      catch(QException | RuntimeException e)
      {
         throw (new IllegalStateException("Index write-through failed: " + e.getMessage(), e));
      }
   }



   /*******************************************************************************
    ** Whether the index covers this bucket and the tables are in the context.
    *******************************************************************************/
   private static boolean applies(StorageBucketConfig bucket)
   {
      return (Boolean.TRUE.equals(bucket.getIndexed()) && Db.available(ObjectStorageTables.OBJECT) && ConfigSync.bucketId(bucket.getAlias()) != null);
   }



   /*******************************************************************************
    ** The bucket's current scan generation (new rows join it, so the next scan
    ** does not treat them as unseen).
    *******************************************************************************/
   private static Long currentGeneration(Integer bucketId) throws QException
   {
      QRecord bucket = Db.first(ObjectStorageTables.BUCKET, "id", bucketId);
      return (bucket == null || bucket.getValueLong("scanGeneration") == null ? 0L : bucket.getValueLong("scanGeneration"));
   }
}
