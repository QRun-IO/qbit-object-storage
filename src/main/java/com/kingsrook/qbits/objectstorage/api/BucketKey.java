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
 ** A bucket alias and key, the result of parsing a public URL.
 *******************************************************************************/
public class BucketKey
{
   private String bucketAlias;
   private String key;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public BucketKey()
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
   public BucketKey withBucketAlias(String bucketAlias)
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
   public BucketKey withKey(String key)
   {
      this.key = key;
      return (this);
   }



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   public BucketKey(String bucketAlias, String key)
   {
      this.bucketAlias = bucketAlias;
      this.key = key;
   }

}
