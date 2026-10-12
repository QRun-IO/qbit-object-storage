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


import com.kingsrook.qbits.objectstorage.api.StorageCaller;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Writes object_storage_operation: the storage audit log. Every write from
 ** every caller (UI action, app code, scheduled job) lands here. Presigned URLs
 ** are logged without the signature (key, method, expiry and issuer only),
 ** because the URL itself is a bearer token. A logging failure never fails the
 ** operation it describes.
 *******************************************************************************/
public final class OperationLog
{
   private static final QLogger LOG = QLogger.getLogger(OperationLog.class);



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private OperationLog()
   {
   }



   /*******************************************************************************
    ** One log entry under construction.
    *******************************************************************************/
   public static class Entry
   {
      private final StorageCaller caller;
      private final String        action;
      private final String        bucketAlias;
      private String              key;
      private String              destinationKey;
      private Long                bytes;
      private String              result = "OK";
      private String              errorMessage;
      private String              requestId;
      private String              detail;
      private String              processRunId;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      public Entry(StorageCaller caller, String action, String bucketAlias)
      {
         this.caller = caller;
         this.action = action;
         this.bucketAlias = bucketAlias;
         this.processRunId = caller.getRunId();
      }



      /*******************************************************************************
       ** Set the key.
       *******************************************************************************/
      public Entry key(String key)
      {
         this.key = key;
         return (this);
      }



      /*******************************************************************************
       ** Set the destination key.
       *******************************************************************************/
      public Entry destination(String destinationKey)
      {
         this.destinationKey = destinationKey;
         return (this);
      }



      /*******************************************************************************
       ** Set the byte count.
       *******************************************************************************/
      public Entry bytes(Long bytes)
      {
         this.bytes = bytes;
         return (this);
      }



      /*******************************************************************************
       ** Set the provider request id.
       *******************************************************************************/
      public Entry requestId(String requestId)
      {
         this.requestId = requestId;
         return (this);
      }



      /*******************************************************************************
       ** Set the detail text.
       *******************************************************************************/
      public Entry detail(String detail)
      {
         this.detail = detail;
         return (this);
      }



      /*******************************************************************************
       ** Set the process run id (UI actions).
       *******************************************************************************/
      public Entry processRunId(String processRunId)
      {
         this.processRunId = processRunId;
         return (this);
      }



      /*******************************************************************************
       ** Mark failed.
       *******************************************************************************/
      public Entry failed(String errorMessage)
      {
         this.result = "FAILED";
         this.errorMessage = errorMessage;
         return (this);
      }



      /*******************************************************************************
       ** Mark skipped.
       *******************************************************************************/
      public Entry skipped(String reason)
      {
         this.result = "SKIPPED";
         this.errorMessage = reason;
         return (this);
      }



      /*******************************************************************************
       ** Write it.
       *******************************************************************************/
      public void write()
      {
         OperationLog.write(this);
      }
   }



   /*******************************************************************************
    ** Insert the row (as the system user), never throwing.
    *******************************************************************************/
   private static void write(Entry e)
   {
      if(!Db.available(ObjectStorageTables.OPERATION))
      {
         LOG.info("Storage operation (no operation table in context)", logPair("action", e.action), logPair("bucket", e.bucketAlias), logPair("key", e.key), logPair("result", e.result));
         return;
      }
      try
      {
         Integer bucketId = ConfigSync.bucketId(e.bucketAlias);
         Db.asSystem(() -> Db.insert(ObjectStorageTables.OPERATION, new QRecord()
            .withValue("occurredAt", ObjectStorageRuntime.clock().instant())
            .withValue("actor", e.caller.describe())
            .withValue("action", e.action)
            .withValue("bucketId", bucketId)
            .withValue("bucketAlias", e.bucketAlias)
            .withValue("objectKey", e.key)
            .withValue("destinationKey", e.destinationKey)
            .withValue("bytes", e.bytes)
            .withValue("result", e.result)
            .withValue("errorMessage", e.errorMessage)
            .withValue("providerRequestId", e.requestId)
            .withValue("processRunId", e.processRunId)
            .withValue("detail", e.detail)));
      }
      catch(Exception ex)
      {
         LOG.warn("Could not write a storage operation log row", ex, logPair("action", e.action), logPair("key", e.key));
      }
   }
}
