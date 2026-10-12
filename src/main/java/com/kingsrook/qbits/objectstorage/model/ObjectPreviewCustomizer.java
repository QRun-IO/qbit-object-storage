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

package com.kingsrook.qbits.objectstorage.model;


import java.util.List;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qqq.backend.core.actions.customizers.AbstractPostQueryCustomizer;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Fills the virtual previewUrl field of object records with a short-lived
 ** (5 minute) presigned GET, so the FILE_PREVIEW adornment can show a thumbnail
 ** and a lightbox without the browser ever holding a credential. Only records the
 ** user could query get one (record locks applied first), and BACKUP buckets get
 ** none. At most 200 records per query.
 *******************************************************************************/
public class ObjectPreviewCustomizer extends AbstractPostQueryCustomizer
{
   private static final QLogger LOG = QLogger.getLogger(ObjectPreviewCustomizer.class);

   private static final int MAX_PER_QUERY = 200;



   /*******************************************************************************
    ** Add the preview URLs.
    *******************************************************************************/
   @Override
   public List<QRecord> apply(List<QRecord> records)
   {
      if(!ObjectStorageRuntime.isInstalled())
      {
         return (records);
      }
      int done = 0;
      for(QRecord record : records)
      {
         if(done >= MAX_PER_QUERY)
         {
            break;
         }
         if(Boolean.TRUE.equals(record.getValueBoolean("deleted")) || record.getValueString("objectKey") == null)
         {
            continue;
         }
         try
         {
            String alias = ConfigSync.aliasOf(record.getValueInteger("bucketId"));
            if(alias == null)
            {
               continue;
            }
            StorageBucketConfig bucket = ObjectStorageRuntime.getConfig().findBucket(alias);
            if(bucket == null || bucket.getRole() == BucketRole.BACKUP)
            {
               continue;
            }
            record.setValue("previewUrl", ObjectStorage.bucket(alias).previewUrl(record.getValueString("objectKey")).toString());
            done++;
         }
         catch(RuntimeException e)
         {
            LOG.debug("Could not make a preview URL", e);
         }
      }
      return (records);
   }
}
