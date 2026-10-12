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

package com.kingsrook.qbits.objectstorage.permissions;


import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.actions.permissions.CustomPermissionChecker;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;


/*******************************************************************************
 ** One checker for every qbit table, process, app and widget:
 ** - table reads, the app and widgets need object_storage.view;
 ** - writes to the editable config tables (connection operational fields, header
 **   rules, protection rules, bucket thresholds) need object_storage.admin;
 **   observed and history tables are never written through the UI or API (they
 **   change only through actions and the service);
 ** - each process needs the permission registered for it (default admin).
 *******************************************************************************/
public class ObjectStoragePermissionChecker implements CustomPermissionChecker
{
   private static final List<String> ADMIN_EDITABLE = List.of(ObjectStorageTables.CONNECTION, ObjectStorageTables.BUCKET,
      ObjectStorageTables.HEADER_RULE, ObjectStorageTables.PROTECTION_RULE);



   /*******************************************************************************
    ** Check, throwing when denied.
    *******************************************************************************/
   @Override
   public void checkPermissionsThrowing(AbstractActionInput actionInput, MetaDataWithPermissionRules metaData) throws QPermissionDeniedException
   {
      String needed = required(actionInput, metaData);
      if(needed == null)
      {
         throw (new QPermissionDeniedException("This storage table changes only through storage actions."));
      }
      if(!has(needed))
      {
         throw (new QPermissionDeniedException("This needs the " + needed + " permission."));
      }
   }



   /*******************************************************************************
    ** The permission an action needs, or null when nobody may do it directly.
    *******************************************************************************/
   static String required(AbstractActionInput actionInput, MetaDataWithPermissionRules metaData)
   {
      boolean write = actionInput instanceof InsertInput || actionInput instanceof UpdateInput || actionInput instanceof DeleteInput;
      if(metaData instanceof QTableMetaData table)
      {
         if(!write)
         {
            return (ObjectStoragePermissions.VIEW);
         }
         return (ADMIN_EDITABLE.contains(table.getName()) ? ObjectStoragePermissions.ADMIN : null);
      }
      if(metaData instanceof QProcessMetaData process)
      {
         Map<String, String> byProcess = ObjectStorageRuntime.processPermissions();
         return (byProcess.getOrDefault(process.getName(), ObjectStoragePermissions.ADMIN));
      }
      return (ObjectStoragePermissions.VIEW);
   }



   /*******************************************************************************
    ** Whether the current session holds a permission (admin implies the others),
    ** or the instance is fully anonymous and the config allows that (local dev).
    *******************************************************************************/
   public static boolean has(String permission)
   {
      QInstance instance = QContext.getQInstance();
      QSession  session  = QContext.getQSession();
      if(session != null && (session.hasPermission(permission) || session.hasPermission(ObjectStoragePermissions.ADMIN)))
      {
         return (true);
      }
      return (ObjectStorageRuntime.isInstalled() && Boolean.TRUE.equals(ObjectStorageRuntime.getConfig().getOpenWhenAnonymous())
         && instance != null && instance.getAuthentication() != null && QAuthenticationType.FULLY_ANONYMOUS.equals(instance.getAuthentication().getType()));
   }
}
