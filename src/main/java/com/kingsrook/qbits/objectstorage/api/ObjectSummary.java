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


import java.time.Instant;


/********************************************************************************
 ** One object as reported by a listing (no headers).
 *******************************************************************************/
public class ObjectSummary
{
   private String key;
   private Long size;
   private String etag;
   private Instant lastModified;
   private String storageClass;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public ObjectSummary()
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
   public ObjectSummary withKey(String key)
   {
      this.key = key;
      return (this);
   }



   /********************************************************************************
    ** Getter for size.
    *******************************************************************************/
   public Long getSize()
   {
      return (this.size);
   }



   /********************************************************************************
    ** Setter for size.
    *******************************************************************************/
   public void setSize(Long size)
   {
      this.size = size;
   }



   /********************************************************************************
    ** Fluent setter for size.
    *******************************************************************************/
   public ObjectSummary withSize(Long size)
   {
      this.size = size;
      return (this);
   }



   /********************************************************************************
    ** Getter for etag.
    *******************************************************************************/
   public String getEtag()
   {
      return (this.etag);
   }



   /********************************************************************************
    ** Setter for etag.
    *******************************************************************************/
   public void setEtag(String etag)
   {
      this.etag = etag;
   }



   /********************************************************************************
    ** Fluent setter for etag.
    *******************************************************************************/
   public ObjectSummary withEtag(String etag)
   {
      this.etag = etag;
      return (this);
   }



   /********************************************************************************
    ** Getter for lastModified.
    *******************************************************************************/
   public Instant getLastModified()
   {
      return (this.lastModified);
   }



   /********************************************************************************
    ** Setter for lastModified.
    *******************************************************************************/
   public void setLastModified(Instant lastModified)
   {
      this.lastModified = lastModified;
   }



   /********************************************************************************
    ** Fluent setter for lastModified.
    *******************************************************************************/
   public ObjectSummary withLastModified(Instant lastModified)
   {
      this.lastModified = lastModified;
      return (this);
   }



   /********************************************************************************
    ** Getter for storageClass.
    *******************************************************************************/
   public String getStorageClass()
   {
      return (this.storageClass);
   }



   /********************************************************************************
    ** Setter for storageClass.
    *******************************************************************************/
   public void setStorageClass(String storageClass)
   {
      this.storageClass = storageClass;
   }



   /********************************************************************************
    ** Fluent setter for storageClass.
    *******************************************************************************/
   public ObjectSummary withStorageClass(String storageClass)
   {
      this.storageClass = storageClass;
      return (this);
   }

}
