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
 ** A copy of one object to another key or bucket.
 *******************************************************************************/
public class CopyRequest
{
   private String sourceKey;
   private String destinationKey;
   private String destinationBucket;
   private MetadataDirective directive = MetadataDirective.COPY;
   private ObjectHeaders headers;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public CopyRequest()
   {
   }



   /********************************************************************************
    ** Getter for sourceKey.
    *******************************************************************************/
   public String getSourceKey()
   {
      return (this.sourceKey);
   }



   /********************************************************************************
    ** Setter for sourceKey.
    *******************************************************************************/
   public void setSourceKey(String sourceKey)
   {
      this.sourceKey = sourceKey;
   }



   /********************************************************************************
    ** Fluent setter for sourceKey.
    *******************************************************************************/
   public CopyRequest withSourceKey(String sourceKey)
   {
      this.sourceKey = sourceKey;
      return (this);
   }



   /********************************************************************************
    ** Getter for destinationKey.
    *******************************************************************************/
   public String getDestinationKey()
   {
      return (this.destinationKey);
   }



   /********************************************************************************
    ** Setter for destinationKey.
    *******************************************************************************/
   public void setDestinationKey(String destinationKey)
   {
      this.destinationKey = destinationKey;
   }



   /********************************************************************************
    ** Fluent setter for destinationKey.
    *******************************************************************************/
   public CopyRequest withDestinationKey(String destinationKey)
   {
      this.destinationKey = destinationKey;
      return (this);
   }



   /********************************************************************************
    ** Getter for destinationBucket.
    *******************************************************************************/
   public String getDestinationBucket()
   {
      return (this.destinationBucket);
   }



   /********************************************************************************
    ** Setter for destinationBucket.
    *******************************************************************************/
   public void setDestinationBucket(String destinationBucket)
   {
      this.destinationBucket = destinationBucket;
   }



   /********************************************************************************
    ** Fluent setter for destinationBucket.
    *******************************************************************************/
   public CopyRequest withDestinationBucket(String destinationBucket)
   {
      this.destinationBucket = destinationBucket;
      return (this);
   }



   /********************************************************************************
    ** Getter for directive.
    *******************************************************************************/
   public MetadataDirective getDirective()
   {
      return (this.directive);
   }



   /********************************************************************************
    ** Setter for directive.
    *******************************************************************************/
   public void setDirective(MetadataDirective directive)
   {
      this.directive = directive;
   }



   /********************************************************************************
    ** Fluent setter for directive.
    *******************************************************************************/
   public CopyRequest withDirective(MetadataDirective directive)
   {
      this.directive = directive;
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
   public CopyRequest withHeaders(ObjectHeaders headers)
   {
      this.headers = headers;
      return (this);
   }



   /*******************************************************************************
    ** A copy from one key to another in the same bucket.
    *******************************************************************************/
   public static CopyRequest of(String sourceKey, String destinationKey)
   {
      return (new CopyRequest().withSourceKey(sourceKey).withDestinationKey(destinationKey));
   }



   /*******************************************************************************
    ** Set the metadata directive.
    *******************************************************************************/
   public CopyRequest withMetadata(MetadataDirective directive)
   {
      this.directive = directive;
      return (this);
   }

}
