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
 ** What a change listener is told after a successful write.
 *******************************************************************************/
public class StorageChange
{
   private String bucketAlias;
   private String key;
   private String destinationKey;
   private ChangeOperation operation;
   private String actor;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public StorageChange()
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
   public StorageChange withBucketAlias(String bucketAlias)
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
   public StorageChange withKey(String key)
   {
      this.key = key;
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
   public StorageChange withDestinationKey(String destinationKey)
   {
      this.destinationKey = destinationKey;
      return (this);
   }



   /********************************************************************************
    ** Getter for operation.
    *******************************************************************************/
   public ChangeOperation getOperation()
   {
      return (this.operation);
   }



   /********************************************************************************
    ** Setter for operation.
    *******************************************************************************/
   public void setOperation(ChangeOperation operation)
   {
      this.operation = operation;
   }



   /********************************************************************************
    ** Fluent setter for operation.
    *******************************************************************************/
   public StorageChange withOperation(ChangeOperation operation)
   {
      this.operation = operation;
      return (this);
   }



   /********************************************************************************
    ** Getter for actor.
    *******************************************************************************/
   public String getActor()
   {
      return (this.actor);
   }



   /********************************************************************************
    ** Setter for actor.
    *******************************************************************************/
   public void setActor(String actor)
   {
      this.actor = actor;
   }



   /********************************************************************************
    ** Fluent setter for actor.
    *******************************************************************************/
   public StorageChange withActor(String actor)
   {
      this.actor = actor;
      return (this);
   }

}
