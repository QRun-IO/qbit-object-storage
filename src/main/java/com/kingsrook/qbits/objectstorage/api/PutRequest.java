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


import java.io.InputStream;
import java.time.Instant;
import java.util.Map;


/********************************************************************************
 ** A put of one object from bytes or a stream.
 *******************************************************************************/
public class PutRequest
{
   private String key;
   private byte[] bytes;
   private InputStream stream;
   private Long contentLength;
   private String contentType;
   private String cacheControl;
   private String contentDisposition;
   private Map<String, String> metadata;
   private LockMode lockMode;
   private Instant lockRetainUntil;
   private String storageClass;
   private Boolean ifNoneMatch = false;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public PutRequest()
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
   public PutRequest withKey(String key)
   {
      this.key = key;
      return (this);
   }



   /********************************************************************************
    ** Getter for bytes.
    *******************************************************************************/
   public byte[] getBytes()
   {
      return (this.bytes);
   }



   /********************************************************************************
    ** Setter for bytes.
    *******************************************************************************/
   public void setBytes(byte[] bytes)
   {
      this.bytes = bytes;
   }



   /********************************************************************************
    ** Fluent setter for bytes.
    *******************************************************************************/
   public PutRequest withBytes(byte[] bytes)
   {
      this.bytes = bytes;
      return (this);
   }



   /********************************************************************************
    ** Getter for stream.
    *******************************************************************************/
   public InputStream getStream()
   {
      return (this.stream);
   }



   /********************************************************************************
    ** Setter for stream.
    *******************************************************************************/
   public void setStream(InputStream stream)
   {
      this.stream = stream;
   }



   /********************************************************************************
    ** Fluent setter for stream.
    *******************************************************************************/
   public PutRequest withStream(InputStream stream)
   {
      this.stream = stream;
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
   public PutRequest withContentLength(Long contentLength)
   {
      this.contentLength = contentLength;
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
   public PutRequest withContentType(String contentType)
   {
      this.contentType = contentType;
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
   public PutRequest withCacheControl(String cacheControl)
   {
      this.cacheControl = cacheControl;
      return (this);
   }



   /********************************************************************************
    ** Getter for contentDisposition.
    *******************************************************************************/
   public String getContentDisposition()
   {
      return (this.contentDisposition);
   }



   /********************************************************************************
    ** Setter for contentDisposition.
    *******************************************************************************/
   public void setContentDisposition(String contentDisposition)
   {
      this.contentDisposition = contentDisposition;
   }



   /********************************************************************************
    ** Fluent setter for contentDisposition.
    *******************************************************************************/
   public PutRequest withContentDisposition(String contentDisposition)
   {
      this.contentDisposition = contentDisposition;
      return (this);
   }



   /********************************************************************************
    ** Getter for metadata.
    *******************************************************************************/
   public Map<String, String> getMetadata()
   {
      return (this.metadata);
   }



   /********************************************************************************
    ** Setter for metadata.
    *******************************************************************************/
   public void setMetadata(Map<String, String> metadata)
   {
      this.metadata = metadata;
   }



   /********************************************************************************
    ** Fluent setter for metadata.
    *******************************************************************************/
   public PutRequest withMetadata(Map<String, String> metadata)
   {
      this.metadata = metadata;
      return (this);
   }



   /********************************************************************************
    ** Getter for lockMode.
    *******************************************************************************/
   public LockMode getLockMode()
   {
      return (this.lockMode);
   }



   /********************************************************************************
    ** Setter for lockMode.
    *******************************************************************************/
   public void setLockMode(LockMode lockMode)
   {
      this.lockMode = lockMode;
   }



   /********************************************************************************
    ** Fluent setter for lockMode.
    *******************************************************************************/
   public PutRequest withLockMode(LockMode lockMode)
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
   public PutRequest withLockRetainUntil(Instant lockRetainUntil)
   {
      this.lockRetainUntil = lockRetainUntil;
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
   public PutRequest withStorageClass(String storageClass)
   {
      this.storageClass = storageClass;
      return (this);
   }



   /********************************************************************************
    ** Getter for ifNoneMatch.
    *******************************************************************************/
   public Boolean getIfNoneMatch()
   {
      return (this.ifNoneMatch);
   }



   /********************************************************************************
    ** Setter for ifNoneMatch.
    *******************************************************************************/
   public void setIfNoneMatch(Boolean ifNoneMatch)
   {
      this.ifNoneMatch = ifNoneMatch;
   }



   /********************************************************************************
    ** Fluent setter for ifNoneMatch.
    *******************************************************************************/
   public PutRequest withIfNoneMatch(Boolean ifNoneMatch)
   {
      this.ifNoneMatch = ifNoneMatch;
      return (this);
   }



   /*******************************************************************************
    ** A put of bytes.
    *******************************************************************************/
   public static PutRequest of(String key, byte[] bytes)
   {
      return (new PutRequest().withKey(key).withBytes(bytes).withContentLength((long) bytes.length));
   }



   /*******************************************************************************
    ** A put of a stream.
    *******************************************************************************/
   public static PutRequest of(String key, InputStream stream)
   {
      return (new PutRequest().withKey(key).withStream(stream));
   }



   /*******************************************************************************
    ** Request object lock retention (offered only where the bucket supports it).
    *******************************************************************************/
   public PutRequest withObjectLock(LockMode mode, Instant retainUntil)
   {
      this.lockMode = mode;
      this.lockRetainUntil = retainUntil;
      return (this);
   }

}
