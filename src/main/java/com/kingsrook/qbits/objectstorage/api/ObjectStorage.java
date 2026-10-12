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

package com.kingsrook.qbits.objectstorage.api;


import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;


/*******************************************************************************
 ** Entry point of the storage API. Application code and other qbits call
 ** ObjectStorage.bucket("alias") instead of building their own S3 client.
 **
 ** <pre>
 **   StorageBucket files = ObjectStorage.bucket("files");
 **   files.put(PutRequest.of("media/42/hero.jpg", bytes).withContentType("image/jpeg"));
 **   URL link = files.presignGet(PresignGet.of("media/42/hero.jpg"));
 **
 **   // a calling qbit with its own permission check (for example backups):
 **   ObjectStorage.asSystem("backups", runId).bucket("backups").put(...);
 ** </pre>
 *******************************************************************************/
public final class ObjectStorage
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ObjectStorage()
   {
   }



   /*******************************************************************************
    ** The bucket behind an alias from config, called as the current session user.
    *******************************************************************************/
   public static StorageBucket bucket(String alias)
   {
      ConfigSync.ensureSynced();
      return (new StorageBucket(alias, StorageCaller.user()));
   }



   /*******************************************************************************
    ** A facade whose calls run as a system caller: tagged with the calling qbit
    ** and its run, not subject to the user's object_storage.* permissions (the
    ** calling qbit must have checked its own permission first), and logged as
    ** "system: backups run 1234 (started by user X)".
    *******************************************************************************/
   public static SystemStorage asSystem(String callerName, String runId)
   {
      return (new SystemStorage(StorageCaller.system(callerName, runId)));
   }



   /*******************************************************************************
    ** Register a change listener (the CDN qbit plans purges with one).
    *******************************************************************************/
   public static void addChangeListener(StorageChangeListener listener)
   {
      ObjectStorageRuntime.addChangeListener(listener);
   }



   /*******************************************************************************
    ** Replace the public URL resolver (the CDN qbit returns its hostnames).
    *******************************************************************************/
   public static void setPublicUrlResolver(PublicUrlResolver resolver)
   {
      ObjectStorageRuntime.setPublicUrlResolver(resolver);
   }
}
