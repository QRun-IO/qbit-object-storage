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
 ** A presigned download link request.
 *******************************************************************************/
public class PresignGet
{
   private String key;
   private Duration ttl;
   private String downloadAs;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public PresignGet()
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
   public PresignGet withKey(String key)
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
   public PresignGet withTtl(Duration ttl)
   {
      this.ttl = ttl;
      return (this);
   }



   /********************************************************************************
    ** Getter for downloadAs.
    *******************************************************************************/
   public String getDownloadAs()
   {
      return (this.downloadAs);
   }



   /********************************************************************************
    ** Setter for downloadAs.
    *******************************************************************************/
   public void setDownloadAs(String downloadAs)
   {
      this.downloadAs = downloadAs;
   }



   /********************************************************************************
    ** Fluent setter for downloadAs.
    *******************************************************************************/
   public PresignGet withDownloadAs(String downloadAs)
   {
      this.downloadAs = downloadAs;
      return (this);
   }



   /*******************************************************************************
    ** A presigned GET for a key.
    *******************************************************************************/
   public static PresignGet of(String key)
   {
      return (new PresignGet().withKey(key));
   }



   /*******************************************************************************
    ** Short-form fluent setter for ttl.
    *******************************************************************************/
   public PresignGet ttl(Duration ttl)
   {
      this.ttl = ttl;
      return (this);
   }



   /*******************************************************************************
    ** Short-form fluent setter for downloadAs.
    *******************************************************************************/
   public PresignGet downloadAs(String downloadAs)
   {
      this.downloadAs = downloadAs;
      return (this);
   }

}
