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


import com.kingsrook.qbits.objectstorage.api.StoredObject;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;


/*******************************************************************************
 ** Keeps the object index in step with writes made through the service
 ** (write-through). An index failure never fails the write; the next scan
 ** repairs it.
 *******************************************************************************/
public interface IndexHook
{
   /*******************************************************************************
    ** An object was created or changed (the HEAD result after the write).
    *******************************************************************************/
   void objectWritten(StorageBucketConfig bucket, StoredObject object);



   /*******************************************************************************
    ** An object was removed from the live listing (deleted or moved away).
    *******************************************************************************/
   void objectRemoved(StorageBucketConfig bucket, String key);
}
