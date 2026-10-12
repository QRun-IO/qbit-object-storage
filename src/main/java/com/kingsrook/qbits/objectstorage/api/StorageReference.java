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
 ** A record that points at a key or prefix in a bucket.
 *******************************************************************************/
public class StorageReference
{
   private String bucketAlias;
   private String keyOrPrefix;
   private MatchMode matchMode = MatchMode.KEY;
   private String tableName;
   private String recordId;
   private String fieldName;
   private String label;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public StorageReference()
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
   public StorageReference withBucketAlias(String bucketAlias)
   {
      this.bucketAlias = bucketAlias;
      return (this);
   }



   /********************************************************************************
    ** Getter for keyOrPrefix.
    *******************************************************************************/
   public String getKeyOrPrefix()
   {
      return (this.keyOrPrefix);
   }



   /********************************************************************************
    ** Setter for keyOrPrefix.
    *******************************************************************************/
   public void setKeyOrPrefix(String keyOrPrefix)
   {
      this.keyOrPrefix = keyOrPrefix;
   }



   /********************************************************************************
    ** Fluent setter for keyOrPrefix.
    *******************************************************************************/
   public StorageReference withKeyOrPrefix(String keyOrPrefix)
   {
      this.keyOrPrefix = keyOrPrefix;
      return (this);
   }



   /********************************************************************************
    ** Getter for matchMode.
    *******************************************************************************/
   public MatchMode getMatchMode()
   {
      return (this.matchMode);
   }



   /********************************************************************************
    ** Setter for matchMode.
    *******************************************************************************/
   public void setMatchMode(MatchMode matchMode)
   {
      this.matchMode = matchMode;
   }



   /********************************************************************************
    ** Fluent setter for matchMode.
    *******************************************************************************/
   public StorageReference withMatchMode(MatchMode matchMode)
   {
      this.matchMode = matchMode;
      return (this);
   }



   /********************************************************************************
    ** Getter for tableName.
    *******************************************************************************/
   public String getTableName()
   {
      return (this.tableName);
   }



   /********************************************************************************
    ** Setter for tableName.
    *******************************************************************************/
   public void setTableName(String tableName)
   {
      this.tableName = tableName;
   }



   /********************************************************************************
    ** Fluent setter for tableName.
    *******************************************************************************/
   public StorageReference withTableName(String tableName)
   {
      this.tableName = tableName;
      return (this);
   }



   /********************************************************************************
    ** Getter for recordId.
    *******************************************************************************/
   public String getRecordId()
   {
      return (this.recordId);
   }



   /********************************************************************************
    ** Setter for recordId.
    *******************************************************************************/
   public void setRecordId(String recordId)
   {
      this.recordId = recordId;
   }



   /********************************************************************************
    ** Fluent setter for recordId.
    *******************************************************************************/
   public StorageReference withRecordId(String recordId)
   {
      this.recordId = recordId;
      return (this);
   }



   /********************************************************************************
    ** Getter for fieldName.
    *******************************************************************************/
   public String getFieldName()
   {
      return (this.fieldName);
   }



   /********************************************************************************
    ** Setter for fieldName.
    *******************************************************************************/
   public void setFieldName(String fieldName)
   {
      this.fieldName = fieldName;
   }



   /********************************************************************************
    ** Fluent setter for fieldName.
    *******************************************************************************/
   public StorageReference withFieldName(String fieldName)
   {
      this.fieldName = fieldName;
      return (this);
   }



   /********************************************************************************
    ** Getter for label.
    *******************************************************************************/
   public String getLabel()
   {
      return (this.label);
   }



   /********************************************************************************
    ** Setter for label.
    *******************************************************************************/
   public void setLabel(String label)
   {
      this.label = label;
   }



   /********************************************************************************
    ** Fluent setter for label.
    *******************************************************************************/
   public StorageReference withLabel(String label)
   {
      this.label = label;
      return (this);
   }

}
