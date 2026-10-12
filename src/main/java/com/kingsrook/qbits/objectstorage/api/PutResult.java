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


/********************************************************************************
 ** The result of a put.
 *******************************************************************************/
public class PutResult
{
   private String key;
   private String etag;
   private String versionId;
   private Long bytes;
   private Boolean multipart = false;
   private String requestId;
   private ObjectHeaders appliedHeaders;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public PutResult()
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
   public PutResult withKey(String key)
   {
      this.key = key;
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
   public PutResult withEtag(String etag)
   {
      this.etag = etag;
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
   public PutResult withVersionId(String versionId)
   {
      this.versionId = versionId;
      return (this);
   }



   /********************************************************************************
    ** Getter for bytes.
    *******************************************************************************/
   public Long getBytes()
   {
      return (this.bytes);
   }



   /********************************************************************************
    ** Setter for bytes.
    *******************************************************************************/
   public void setBytes(Long bytes)
   {
      this.bytes = bytes;
   }



   /********************************************************************************
    ** Fluent setter for bytes.
    *******************************************************************************/
   public PutResult withBytes(Long bytes)
   {
      this.bytes = bytes;
      return (this);
   }



   /********************************************************************************
    ** Getter for multipart.
    *******************************************************************************/
   public Boolean getMultipart()
   {
      return (this.multipart);
   }



   /********************************************************************************
    ** Setter for multipart.
    *******************************************************************************/
   public void setMultipart(Boolean multipart)
   {
      this.multipart = multipart;
   }



   /********************************************************************************
    ** Fluent setter for multipart.
    *******************************************************************************/
   public PutResult withMultipart(Boolean multipart)
   {
      this.multipart = multipart;
      return (this);
   }



   /********************************************************************************
    ** Getter for requestId.
    *******************************************************************************/
   public String getRequestId()
   {
      return (this.requestId);
   }



   /********************************************************************************
    ** Setter for requestId.
    *******************************************************************************/
   public void setRequestId(String requestId)
   {
      this.requestId = requestId;
   }



   /********************************************************************************
    ** Fluent setter for requestId.
    *******************************************************************************/
   public PutResult withRequestId(String requestId)
   {
      this.requestId = requestId;
      return (this);
   }



   /********************************************************************************
    ** Getter for appliedHeaders.
    *******************************************************************************/
   public ObjectHeaders getAppliedHeaders()
   {
      return (this.appliedHeaders);
   }



   /********************************************************************************
    ** Setter for appliedHeaders.
    *******************************************************************************/
   public void setAppliedHeaders(ObjectHeaders appliedHeaders)
   {
      this.appliedHeaders = appliedHeaders;
   }



   /********************************************************************************
    ** Fluent setter for appliedHeaders.
    *******************************************************************************/
   public PutResult withAppliedHeaders(ObjectHeaders appliedHeaders)
   {
      this.appliedHeaders = appliedHeaders;
      return (this);
   }

}
