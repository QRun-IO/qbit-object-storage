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

package com.kingsrook.qbits.objectstorage.processes;


import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.api.StorageBucket;
import com.kingsrook.qbits.objectstorage.api.StorageCaller;
import com.kingsrook.qbits.objectstorage.api.StoredObject;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.internal.ProtectionRules;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.permissions.ObjectStoragePermissionChecker;
import com.kingsrook.qbits.objectstorage.permissions.ObjectStoragePermissions;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Shared helpers for the record and table actions: resolving a row's bucket,
 ** the skip rules every destructive action applies (referenced, protected,
 ** object lock), permission re-checks, and per-run caches.
 *******************************************************************************/
public final class ActionSupport
{
   /*******************************************************************************
    ** Why an action leaves an object alone.
    *******************************************************************************/
   public enum Skip
   {
      NONE,
      REFERENCED,
      PROTECTED,
      LOCKED,
      BACKUP_BUCKET,
      NO_BUCKET_ACCESS
   }



   /*******************************************************************************
    ** A classification: the reason, and a human detail for the review line.
    *******************************************************************************/
   public record Classification(Skip skip, String detail)
   {
      /*******************************************************************************
       ** Nothing in the way.
       *******************************************************************************/
      public static Classification none()
      {
         return (new Classification(Skip.NONE, null));
      }
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ActionSupport()
   {
   }



   /*******************************************************************************
    ** The alias of the bucket an object, folder or reference row belongs to.
    *******************************************************************************/
   public static String aliasOf(QRecord row)
   {
      Integer bucketId = row.getValueInteger("bucketId");
      if(bucketId == null && row.getValueString("alias") != null)
      {
         return (row.getValueString("alias"));
      }
      String alias = ConfigSync.aliasOf(bucketId);
      if(alias == null && bucketId != null)
      {
         try
         {
            QRecord bucket = Db.asSystem(() -> Db.first(ObjectStorageTables.BUCKET, "id", bucketId));
            alias = bucket == null ? null : bucket.getValueString("alias");
         }
         catch(QException e)
         {
            alias = null;
         }
      }
      return (alias);
   }



   /*******************************************************************************
    ** The configured bucket (null when the row's bucket is not in this config).
    *******************************************************************************/
   public static StorageBucketConfig config(String alias)
   {
      return (alias == null ? null : ConfigSync.withBucketOverrides(ObjectStorageRuntime.getConfig().findBucket(alias)));
   }



   /*******************************************************************************
    ** Whether the current user holds a permission (admin implies the rest).
    *******************************************************************************/
   public static boolean has(String permission)
   {
      return (ObjectStoragePermissionChecker.has(permission));
   }



   /*******************************************************************************
    ** Whether the current user is an admin.
    *******************************************************************************/
   public static boolean isAdmin()
   {
      return (has(ObjectStoragePermissions.ADMIN));
   }



   /*******************************************************************************
    ** Whether the current user can see a bucket row (the per-bucket record lock is
    ** applied by the query). The load step uses this before each provider call.
    *******************************************************************************/
   public static boolean canSeeBucket(Integer bucketId, Map<Integer, Boolean> cache)
   {
      if(bucketId == null)
      {
         return (false);
      }
      return (cache.computeIfAbsent(bucketId, id ->
      {
         try
         {
            return (!new QueryAction().execute(new QueryInput(ObjectStorageTables.BUCKET).withFilter(new com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter(Db.eq("id", id)))).getRecords().isEmpty());
         }
         catch(QException e)
         {
            return (false);
         }
      }));
   }



   /*******************************************************************************
    ** The service handle for a bucket alias, as the current user.
    *******************************************************************************/
   public static StorageBucket storage(String alias)
   {
      return (ObjectStorage.bucket(alias));
   }



   /*******************************************************************************
    ** Classify an object for a destructive action: BACKUP buckets are outside the
    ** actions (backups manage their own objects); a referenced object is skipped
    ** when asked; protection rules apply; an object under lock is skipped.
    *******************************************************************************/
   public static Classification classify(QRecord row, ProtectionRules.Op op, boolean checkReferences, boolean checkLock)
   {
      String              alias  = aliasOf(row);
      StorageBucketConfig config = config(alias);
      if(config == null)
      {
         return (new Classification(Skip.NO_BUCKET_ACCESS, "its bucket is not in the configuration"));
      }
      if(config.getRole() == com.kingsrook.qbits.objectstorage.config.BucketRole.BACKUP)
      {
         return (new Classification(Skip.BACKUP_BUCKET, "backup buckets are managed by the backups qbit"));
      }
      if(checkReferences && row.getValueInteger("referenceCount") != null && row.getValueInteger("referenceCount") > 0)
      {
         return (new Classification(Skip.REFERENCED, "referenced by " + referencedBy(row)));
      }
      ProtectionRules.Result protection = ProtectionRules.check(alias, row.getValueString("objectKey"), op, StorageCaller.user());
      if(protection.blocked())
      {
         return (new Classification(Skip.PROTECTED, protection.reason()));
      }
      if(checkLock)
      {
         try
         {
            StoredObject head = storage(alias).head(row.getValueString("objectKey")).orElse(null);
            if(head != null && head.getLockRetainUntil() != null && head.getLockRetainUntil().isAfter(Instant.now()))
            {
               return (new Classification(Skip.LOCKED, "object lock until " + head.getLockRetainUntil()));
            }
         }
         catch(RuntimeException e)
         {
            return (Classification.none());
         }
      }
      return (Classification.none());
   }



   /*******************************************************************************
    ** The first few records that reference an object, for the review line.
    *******************************************************************************/
   public static String referencedBy(QRecord row)
   {
      try
      {
         List<QRecord> refs = Db.asSystem(() -> Db.query(ObjectStorageTables.OBJECT_REFERENCE, "objectId", row.getValue("id")));
         List<String>  labels = new ArrayList<>();
         for(QRecord ref : refs)
         {
            labels.add(StringUtils.hasContent(ref.getValueString("label")) ? ref.getValueString("label") : ref.getValueString("sourceTable") + " #" + ref.getValueString("sourceRecordId"));
            if(labels.size() == 3)
            {
               break;
            }
         }
         int count = row.getValueInteger("referenceCount") == null ? refs.size() : row.getValueInteger("referenceCount");
         return (count + " record" + (count == 1 ? "" : "s") + (labels.isEmpty() ? "" : " (" + String.join(", ", labels) + (count > labels.size() ? ", ..." : "") + ")"));
      }
      catch(QException e)
      {
         return ("other records");
      }
   }



   /*******************************************************************************
    ** The object rows of a run, loaded by id when only a partial record came in.
    *******************************************************************************/
   public static Map<String, Serializable> noValues()
   {
      return (new HashMap<>());
   }



   /*******************************************************************************
    ** Bytes as KB, MB, GB.
    *******************************************************************************/
   public static String bytes(long value)
   {
      if(value < 1024)
      {
         return (value + " B");
      }
      String[] units = { "KB", "MB", "GB", "TB" };
      double   v     = value;
      int      u     = -1;
      while(v >= 1024 && u < units.length - 1)
      {
         v /= 1024;
         u++;
      }
      return (String.format("%.1f %s", v, units[u]));
   }
}
