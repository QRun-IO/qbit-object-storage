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


import java.util.Map;


/*******************************************************************************
 ** Where storage conditions are raised. The default is a no-op; when the Alerts
 ** qbit is installed an adapter forwards to its producer SPI. Alerts carry a
 ** stable dedupe key and counts, never secrets or presigned URLs.
 *******************************************************************************/
public interface StorageAlertSink
{
   /*******************************************************************************
    ** Raise (or refresh) an alert.
    *******************************************************************************/
   void raise(String alertType, String dedupeKey, String severity, String message, Map<String, Object> details);



   /*******************************************************************************
    ** Resolve an alert.
    *******************************************************************************/
   void resolve(String alertType, String dedupeKey);



   /*******************************************************************************
    ** A sink that does nothing.
    *******************************************************************************/
   static StorageAlertSink noop()
   {
      return (new StorageAlertSink()
      {
         /*******************************************************************************
          ** Does nothing.
          *******************************************************************************/
         @Override
         public void raise(String alertType, String dedupeKey, String severity, String message, Map<String, Object> details)
         {
         }



         /*******************************************************************************
          ** Does nothing.
          *******************************************************************************/
         @Override
         public void resolve(String alertType, String dedupeKey)
         {
         }
      });
   }
}
