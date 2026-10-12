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


/*******************************************************************************
 ** The storage API for a system caller (see ObjectStorage.asSystem): calls are
 ** tagged with the calling qbit and run, skip the user's object_storage.*
 ** permissions, and may use the system-only permanent delete.
 *******************************************************************************/
public final class SystemStorage
{
   private final StorageCaller caller;



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   SystemStorage(StorageCaller caller)
   {
      this.caller = caller;
   }



   /*******************************************************************************
    ** The bucket behind an alias, called as this system caller.
    *******************************************************************************/
   public StorageBucket bucket(String alias)
   {
      ConfigSync.ensureSynced();
      return (new StorageBucket(alias, caller));
   }
}
