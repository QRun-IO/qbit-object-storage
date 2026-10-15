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


import java.util.List;
import java.util.Optional;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.cluster.ClusterCaches;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Post insert, update and delete of the editable config rows that the runtime
 ** caches: a bucket row (presign limits, ConfigSync) or a header rule
 ** (DefaultObjectHeaderPolicy). Clears that cache on every node, so an admin's
 ** edit takes effect everywhere at once; the short TTLs remain the safety net.
 *******************************************************************************/
public class ConfigRowChangeCustomizer implements TableCustomizerInterface
{

   /*******************************************************************************
    ** {@inheritDoc}
    *******************************************************************************/
   @Override
   public List<QRecord> postInsert(InsertInput insertInput, List<QRecord> records) throws QException
   {
      invalidate(insertInput.getTableName());
      return (records);
   }



   /*******************************************************************************
    ** {@inheritDoc}
    *******************************************************************************/
   @Override
   public List<QRecord> postUpdate(UpdateInput updateInput, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      invalidate(updateInput.getTableName());
      return (records);
   }



   /*******************************************************************************
    ** {@inheritDoc}
    *******************************************************************************/
   @Override
   public List<QRecord> postDelete(DeleteInput deleteInput, List<QRecord> records) throws QException
   {
      invalidate(deleteInput.getTableName());
      return (records);
   }



   /*******************************************************************************
    ** Clear the cache that holds the table's rows, on every node.
    *******************************************************************************/
   static void invalidate(String tableName)
   {
      if(ObjectStorageTables.BUCKET.equals(tableName))
      {
         ClusterCaches.invalidate(ConfigSync.ROW_LIMITS_CACHE_NAME);
      }
      else if(ObjectStorageTables.HEADER_RULE.equals(tableName))
      {
         ClusterCaches.invalidate(DefaultObjectHeaderPolicy.CACHE_NAME);
      }
   }
}
