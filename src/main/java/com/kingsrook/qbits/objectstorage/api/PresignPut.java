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


import java.time.Duration;


/********************************************************************************
 ** A presigned upload link request; the content type and length are signed.
 *******************************************************************************/
public class PresignPut
{
   private String key;
   private Duration ttl;
   private String contentType;
   private Long contentLength;
   private String cacheControl;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public PresignPut()
   {
   }



   /********************************************************************************
    ** Getter for key.
    *******************************************************************************/
   public String getKey()
   {
      return (this.key);
   }



   /********************************************************************************
    ** Setter for key.
    *******************************************************************************/
   public void setKey(String key)
   {
      this.key = key;
   }



   /********************************************************************************
    ** Fluent setter for key.
    *******************************************************************************/
   public PresignPut withKey(String key)
   {
      this.key = key;
      return (this);
   }



   /********************************************************************************
    ** Getter for ttl.
    *******************************************************************************/
   public Duration getTtl()
   {
      return (this.ttl);
   }



   /********************************************************************************
    ** Setter for ttl.
    *******************************************************************************/
   public void setTtl(Duration ttl)
   {
      this.ttl = ttl;
   }



   /********************************************************************************
    ** Fluent setter for ttl.
    *******************************************************************************/
   public PresignPut withTtl(Duration ttl)
   {
      this.ttl = ttl;
      return (this);
   }



   /********************************************************************************
    ** Getter for contentType.
    *******************************************************************************/
   public String getContentType()
   {
      return (this.contentType);
   }



   /********************************************************************************
    ** Setter for contentType.
    *******************************************************************************/
   public void setContentType(String contentType)
   {
      this.contentType = contentType;
   }



   /********************************************************************************
    ** Fluent setter for contentType.
    *******************************************************************************/
   public PresignPut withContentType(String contentType)
   {
      this.contentType = contentType;
      return (this);
   }



   /********************************************************************************
    ** Getter for contentLength.
    *******************************************************************************/
   public Long getContentLength()
   {
      return (this.contentLength);
   }



   /********************************************************************************
    ** Setter for contentLength.
    *******************************************************************************/
   public void setContentLength(Long contentLength)
   {
      this.contentLength = contentLength;
   }



   /********************************************************************************
    ** Fluent setter for contentLength.
    *******************************************************************************/
   public PresignPut withContentLength(Long contentLength)
   {
      this.contentLength = contentLength;
      return (this);
   }



   /********************************************************************************
    ** Getter for cacheControl.
    *******************************************************************************/
   public String getCacheControl()
   {
      return (this.cacheControl);
   }



   /********************************************************************************
    ** Setter for cacheControl.
    *******************************************************************************/
   public void setCacheControl(String cacheControl)
   {
      this.cacheControl = cacheControl;
   }



   /********************************************************************************
    ** Fluent setter for cacheControl.
    *******************************************************************************/
   public PresignPut withCacheControl(String cacheControl)
   {
      this.cacheControl = cacheControl;
      return (this);
   }



   /*******************************************************************************
    ** A presigned PUT for a key.
    *******************************************************************************/
   public static PresignPut of(String key)
   {
      return (new PresignPut().withKey(key));
   }



   /*******************************************************************************
    ** Fluent setter for the content type (short form).
    *******************************************************************************/
   public PresignPut contentType(String contentType)
   {
      this.contentType = contentType;
      return (this);
   }



   /*******************************************************************************
    ** Short-form fluent setter for contentLength.
    *******************************************************************************/
   public PresignPut contentLength(Long contentLength)
   {
      this.contentLength = contentLength;
      return (this);
   }



   /*******************************************************************************
    ** Short-form fluent setter for ttl.
    *******************************************************************************/
   public PresignPut ttl(Duration ttl)
   {
      this.ttl = ttl;
      return (this);
   }

}
