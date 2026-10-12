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
 ** One object as reported by HEAD.
 *******************************************************************************/
public class StoredObject
{
   private String bucketAlias;
   private String key;
   private Long size;
   private String etag;
   private Instant lastModified;
   private ObjectHeaders headers = new ObjectHeaders();
   private String versionId;
   private String storageClass;
   private String lockMode;
   private Instant lockRetainUntil;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public StoredObject()
   {
   }



   /********************************************************************************
    ** Getter for bucketAlias.
    *******************************************************************************/
   public String getBucketAlias()
   {
      return (this.bucketAlias);
   }



   /********************************************************************************
    ** Setter for bucketAlias.
    *******************************************************************************/
   public void setBucketAlias(String bucketAlias)
   {
      this.bucketAlias = bucketAlias;
   }



   /********************************************************************************
    ** Fluent setter for bucketAlias.
    *******************************************************************************/
   public StoredObject withBucketAlias(String bucketAlias)
   {
      this.bucketAlias = bucketAlias;
      return (this);
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
   public StoredObject withKey(String key)
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
   public StoredObject withSize(Long size)
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
   public StoredObject withEtag(String etag)
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
   public StoredObject withLastModified(Instant lastModified)
   {
      this.lastModified = lastModified;
      return (this);
   }



   /********************************************************************************
    ** Getter for headers.
    *******************************************************************************/
   public ObjectHeaders getHeaders()
   {
      return (this.headers);
   }



   /********************************************************************************
    ** Setter for headers.
    *******************************************************************************/
   public void setHeaders(ObjectHeaders headers)
   {
      this.headers = headers;
   }



   /********************************************************************************
    ** Fluent setter for headers.
    *******************************************************************************/
   public StoredObject withHeaders(ObjectHeaders headers)
   {
      this.headers = headers;
      return (this);
   }



   /********************************************************************************
    ** Getter for versionId.
    *******************************************************************************/
   public String getVersionId()
   {
      return (this.versionId);
   }



   /********************************************************************************
    ** Setter for versionId.
    *******************************************************************************/
   public void setVersionId(String versionId)
   {
      this.versionId = versionId;
   }



   /********************************************************************************
    ** Fluent setter for versionId.
    *******************************************************************************/
   public StoredObject withVersionId(String versionId)
   {
      this.versionId = versionId;
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
   public StoredObject withStorageClass(String storageClass)
   {
      this.storageClass = storageClass;
      return (this);
   }



   /********************************************************************************
    ** Getter for lockMode.
    *******************************************************************************/
   public String getLockMode()
   {
      return (this.lockMode);
   }



   /********************************************************************************
    ** Setter for lockMode.
    *******************************************************************************/
   public void setLockMode(String lockMode)
   {
      this.lockMode = lockMode;
   }



   /********************************************************************************
    ** Fluent setter for lockMode.
    *******************************************************************************/
   public StoredObject withLockMode(String lockMode)
   {
      this.lockMode = lockMode;
      return (this);
   }



   /********************************************************************************
    ** Getter for lockRetainUntil.
    *******************************************************************************/
   public Instant getLockRetainUntil()
   {
      return (this.lockRetainUntil);
   }



   /********************************************************************************
    ** Setter for lockRetainUntil.
    *******************************************************************************/
   public void setLockRetainUntil(Instant lockRetainUntil)
   {
      this.lockRetainUntil = lockRetainUntil;
   }



   /********************************************************************************
    ** Fluent setter for lockRetainUntil.
    *******************************************************************************/
   public StoredObject withLockRetainUntil(Instant lockRetainUntil)
   {
      this.lockRetainUntil = lockRetainUntil;
      return (this);
   }

}
