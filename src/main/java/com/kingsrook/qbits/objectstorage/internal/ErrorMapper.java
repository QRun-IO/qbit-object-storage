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


import com.kingsrook.qbits.objectstorage.api.StorageException;
import com.kingsrook.qbits.objectstorage.api.StorageException.Reason;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;


/*******************************************************************************
 ** Maps SDK exceptions to the stable StorageException reasons.
 *******************************************************************************/
public final class ErrorMapper
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ErrorMapper()
   {
   }



   /*******************************************************************************
    ** The error code the provider returned, or null.
    *******************************************************************************/
   public static String errorCode(Throwable t)
   {
      if(t instanceof AwsServiceException ase && ase.awsErrorDetails() != null)
      {
         return (ase.awsErrorDetails().errorCode());
      }
      return (null);
   }



   /*******************************************************************************
    ** Map any throwable.
    *******************************************************************************/
   public static StorageException map(Throwable t, String what)
   {
      if(t instanceof StorageException se)
      {
         return (se);
      }
      if(t instanceof AwsServiceException ase)
      {
         int    status    = ase.statusCode();
         String code      = errorCode(ase);
         String requestId = ase.requestId();
         Reason reason    = Reason.PROVIDER_ERROR;
         if(status == 404 || "NoSuchKey".equals(code) || "NoSuchBucket".equals(code) || "NotFound".equals(code))
         {
            reason = Reason.NOT_FOUND;
         }
         else if(status == 403 || status == 401 || "AccessDenied".equals(code) || "InvalidAccessKeyId".equals(code) || "SignatureDoesNotMatch".equals(code))
         {
            reason = Reason.ACCESS_DENIED;
         }
         else if(status == 412 || "PreconditionFailed".equals(code))
         {
            reason = Reason.PRECONDITION_FAILED;
         }
         else if(status == 503 || "SlowDown".equals(code) || "Throttling".equals(code) || status == 429)
         {
            reason = Reason.THROTTLED;
         }
         else if(status == 501 || "NotImplemented".equals(code) || "MethodNotAllowed".equals(code) || status == 405)
         {
            reason = Reason.UNSUPPORTED;
         }
         return (new StorageException(reason, what + " failed: " + (code == null ? "HTTP " + status : code), requestId, status, ase));
      }
      if(t instanceof SdkClientException)
      {
         return (new StorageException(Reason.PROVIDER_ERROR, what + " failed: " + t.getMessage(), null, null, t));
      }
      return (new StorageException(Reason.PROVIDER_ERROR, what + " failed: " + t, null, null, t));
   }
}
