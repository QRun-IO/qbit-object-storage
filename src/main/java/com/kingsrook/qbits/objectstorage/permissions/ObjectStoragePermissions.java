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


/*******************************************************************************
 ** The permission names. object_storage.admin implies the others.
 *******************************************************************************/
public final class ObjectStoragePermissions
{
   /** Read all storage tables; preview, download, presign GET. */
   public static final String VIEW   = "object_storage.view";
   /** Upload, copy, move/rename, change headers and metadata, apply header policy, presign PUT, restore. */
   public static final String WRITE  = "object_storage.write";
   /** Delete objects (soft where possible). */
   public static final String DELETE = "object_storage.delete";
   /** Edit connections (operational fields), header rules, protection rules; write probes; permanent deletes; runs over the bulk cap. */
   public static final String ADMIN  = "object_storage.admin";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ObjectStoragePermissions()
   {
   }
}
