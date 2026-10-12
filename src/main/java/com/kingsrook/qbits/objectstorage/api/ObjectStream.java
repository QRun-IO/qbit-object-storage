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


import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;


/*******************************************************************************
 ** The content of an object plus its basic metadata. Close it when done.
 *******************************************************************************/
public class ObjectStream implements AutoCloseable
{
   private final InputStream   inputStream;
   private final Long          contentLength;
   private final String        contentType;
   private final String        etag;
   private final Instant       lastModified;
   private final ObjectHeaders headers;



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   public ObjectStream(InputStream inputStream, Long contentLength, String contentType, String etag, Instant lastModified, ObjectHeaders headers)
   {
      this.inputStream = inputStream;
      this.contentLength = contentLength;
      this.contentType = contentType;
      this.etag = etag;
      this.lastModified = lastModified;
      this.headers = headers;
   }



   /*******************************************************************************
    ** The content.
    *******************************************************************************/
   public InputStream getInputStream()
   {
      return (inputStream);
   }



   /*******************************************************************************
    ** Read everything into memory (small objects only).
    *******************************************************************************/
   public byte[] readAllBytes() throws IOException
   {
      return (inputStream.readAllBytes());
   }



   /*******************************************************************************
    ** Getter for contentLength.
    *******************************************************************************/
   public Long getContentLength()
   {
      return (contentLength);
   }



   /*******************************************************************************
    ** Getter for contentType.
    *******************************************************************************/
   public String getContentType()
   {
      return (contentType);
   }



   /*******************************************************************************
    ** Getter for etag.
    *******************************************************************************/
   public String getEtag()
   {
      return (etag);
   }



   /*******************************************************************************
    ** Getter for lastModified.
    *******************************************************************************/
   public Instant getLastModified()
   {
      return (lastModified);
   }



   /*******************************************************************************
    ** Getter for headers.
    *******************************************************************************/
   public ObjectHeaders getHeaders()
   {
      return (headers);
   }



   /*******************************************************************************
    ** Close the stream.
    *******************************************************************************/
   @Override
   public void close() throws IOException
   {
      inputStream.close();
   }
}
