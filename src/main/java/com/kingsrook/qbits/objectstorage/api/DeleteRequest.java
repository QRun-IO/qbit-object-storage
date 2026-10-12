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
 ** A delete of one object key.
 *******************************************************************************/
public class DeleteRequest
{
   private String key;
   private Boolean permanent = false;
   private Boolean allowPermanentWhenNoSoftDelete = false;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public DeleteRequest()
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
   public DeleteRequest withKey(String key)
   {
      this.key = key;
      return (this);
   }



   /********************************************************************************
    ** Getter for permanent.
    *******************************************************************************/
   public Boolean getPermanent()
   {
      return (this.permanent);
   }



   /********************************************************************************
    ** Setter for permanent.
    *******************************************************************************/
   public void setPermanent(Boolean permanent)
   {
      this.permanent = permanent;
   }



   /********************************************************************************
    ** Fluent setter for permanent.
    *******************************************************************************/
   public DeleteRequest withPermanent(Boolean permanent)
   {
      this.permanent = permanent;
      return (this);
   }



   /********************************************************************************
    ** Getter for allowPermanentWhenNoSoftDelete.
    *******************************************************************************/
   public Boolean getAllowPermanentWhenNoSoftDelete()
   {
      return (this.allowPermanentWhenNoSoftDelete);
   }



   /********************************************************************************
    ** Setter for allowPermanentWhenNoSoftDelete.
    *******************************************************************************/
   public void setAllowPermanentWhenNoSoftDelete(Boolean allowPermanentWhenNoSoftDelete)
   {
      this.allowPermanentWhenNoSoftDelete = allowPermanentWhenNoSoftDelete;
   }



   /********************************************************************************
    ** Fluent setter for allowPermanentWhenNoSoftDelete.
    *******************************************************************************/
   public DeleteRequest withAllowPermanentWhenNoSoftDelete(Boolean allowPermanentWhenNoSoftDelete)
   {
      this.allowPermanentWhenNoSoftDelete = allowPermanentWhenNoSoftDelete;
      return (this);
   }



   /*******************************************************************************
    ** A delete of a key.
    *******************************************************************************/
   public static DeleteRequest of(String key)
   {
      return (new DeleteRequest().withKey(key));
   }



   /*******************************************************************************
    ** Permanently delete every version and delete marker of the key (system callers only).
    *******************************************************************************/
   public DeleteRequest permanent()
   {
      this.permanent = true;
      return (this);
   }

}
