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
 ** The result of a delete.
 *******************************************************************************/
public class DeleteResult
{
   private String key;
   private DeleteMode mode;
   private Integer versionsDeleted = 0;
   private String detail;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public DeleteResult()
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
   public DeleteResult withKey(String key)
   {
      this.key = key;
      return (this);
   }



   /********************************************************************************
    ** Getter for mode.
    *******************************************************************************/
   public DeleteMode getMode()
   {
      return (this.mode);
   }



   /********************************************************************************
    ** Setter for mode.
    *******************************************************************************/
   public void setMode(DeleteMode mode)
   {
      this.mode = mode;
   }



   /********************************************************************************
    ** Fluent setter for mode.
    *******************************************************************************/
   public DeleteResult withMode(DeleteMode mode)
   {
      this.mode = mode;
      return (this);
   }



   /********************************************************************************
    ** Getter for versionsDeleted.
    *******************************************************************************/
   public Integer getVersionsDeleted()
   {
      return (this.versionsDeleted);
   }



   /********************************************************************************
    ** Setter for versionsDeleted.
    *******************************************************************************/
   public void setVersionsDeleted(Integer versionsDeleted)
   {
      this.versionsDeleted = versionsDeleted;
   }



   /********************************************************************************
    ** Fluent setter for versionsDeleted.
    *******************************************************************************/
   public DeleteResult withVersionsDeleted(Integer versionsDeleted)
   {
      this.versionsDeleted = versionsDeleted;
      return (this);
   }



   /********************************************************************************
    ** Getter for detail.
    *******************************************************************************/
   public String getDetail()
   {
      return (this.detail);
   }



   /********************************************************************************
    ** Setter for detail.
    *******************************************************************************/
   public void setDetail(String detail)
   {
      this.detail = detail;
   }



   /********************************************************************************
    ** Fluent setter for detail.
    *******************************************************************************/
   public DeleteResult withDetail(String detail)
   {
      this.detail = detail;
      return (this);
   }

}
