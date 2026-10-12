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
 ** A presigned POST policy request (browser form upload with a size range).
 *******************************************************************************/
public class PresignPost
{
   private String key;
   private Duration ttl;
   private String contentType;
   private Long minBytes = 0L;
   private Long maxBytes;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public PresignPost()
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
   public PresignPost withKey(String key)
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
   public PresignPost withTtl(Duration ttl)
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
   public PresignPost withContentType(String contentType)
   {
      this.contentType = contentType;
      return (this);
   }



   /********************************************************************************
    ** Getter for minBytes.
    *******************************************************************************/
   public Long getMinBytes()
   {
      return (this.minBytes);
   }



   /********************************************************************************
    ** Setter for minBytes.
    *******************************************************************************/
   public void setMinBytes(Long minBytes)
   {
      this.minBytes = minBytes;
   }



   /********************************************************************************
    ** Fluent setter for minBytes.
    *******************************************************************************/
   public PresignPost withMinBytes(Long minBytes)
   {
      this.minBytes = minBytes;
      return (this);
   }



   /********************************************************************************
    ** Getter for maxBytes.
    *******************************************************************************/
   public Long getMaxBytes()
   {
      return (this.maxBytes);
   }



   /********************************************************************************
    ** Setter for maxBytes.
    *******************************************************************************/
   public void setMaxBytes(Long maxBytes)
   {
      this.maxBytes = maxBytes;
   }



   /********************************************************************************
    ** Fluent setter for maxBytes.
    *******************************************************************************/
   public PresignPost withMaxBytes(Long maxBytes)
   {
      this.maxBytes = maxBytes;
      return (this);
   }

}
