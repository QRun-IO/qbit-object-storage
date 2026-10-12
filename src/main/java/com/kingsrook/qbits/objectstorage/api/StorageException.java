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


/*******************************************************************************
 ** The only exception the storage API throws. No raw SDK exception leaks out.
 ** Carries a stable reason and the provider's request ID when there is one.
 *******************************************************************************/
public class StorageException extends RuntimeException
{
   /*******************************************************************************
    ** Stable failure reasons.
    *******************************************************************************/
   public enum Reason
   {
      NOT_FOUND,
      ACCESS_DENIED,
      UNSUPPORTED,
      PRECONDITION_FAILED,
      THROTTLED,
      PROVIDER_ERROR
   }

   private final Reason  reason;
   private final String  requestId;
   private final Integer httpStatus;



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   public StorageException(Reason reason, String message, String requestId, Integer httpStatus, Throwable cause)
   {
      super(message, cause);
      this.reason = reason;
      this.requestId = requestId;
      this.httpStatus = httpStatus;
   }



   /*******************************************************************************
    ** Constructor without a provider response.
    *******************************************************************************/
   public StorageException(Reason reason, String message)
   {
      this(reason, message, null, null, null);
   }



   /*******************************************************************************
    ** Getter for reason.
    *******************************************************************************/
   public Reason getReason()
   {
      return (reason);
   }



   /*******************************************************************************
    ** Getter for requestId.
    *******************************************************************************/
   public String getRequestId()
   {
      return (requestId);
   }



   /*******************************************************************************
    ** Getter for httpStatus.
    *******************************************************************************/
   public Integer getHttpStatus()
   {
      return (httpStatus);
   }
}
