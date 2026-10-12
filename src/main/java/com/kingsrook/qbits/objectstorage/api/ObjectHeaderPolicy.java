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


import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;


/*******************************************************************************
 ** Decides the headers for a new or rewritten object. The default
 ** implementation reads the object_storage_header_rule table; an app can plug
 ** in its own (for example one driven by per-media-type settings it keeps).
 *******************************************************************************/
public interface ObjectHeaderPolicy
{
   /*******************************************************************************
    ** The headers (Cache-Control, Content-Disposition) the object should have, or
    ** empty headers when the policy has no opinion.
    *******************************************************************************/
   ObjectHeaders headersFor(StorageBucketConfig bucket, String key, String contentType);
}
