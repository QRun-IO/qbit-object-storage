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


import java.util.LinkedHashMap;
import java.util.Map;


/********************************************************************************
 ** The URL and form fields for a presigned POST.
 *******************************************************************************/
public class PresignedPost
{
   private String url;
   private Map<String, String> fields = new LinkedHashMap<>();



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public PresignedPost()
   {
   }



   /********************************************************************************
    ** Getter for url.
    *******************************************************************************/
   public String getUrl()
   {
      return (this.url);
   }



   /********************************************************************************
    ** Setter for url.
    *******************************************************************************/
   public void setUrl(String url)
   {
      this.url = url;
   }



   /********************************************************************************
    ** Fluent setter for url.
    *******************************************************************************/
   public PresignedPost withUrl(String url)
   {
      this.url = url;
      return (this);
   }



   /********************************************************************************
    ** Getter for fields.
    *******************************************************************************/
   public Map<String, String> getFields()
   {
      return (this.fields);
   }



   /********************************************************************************
    ** Setter for fields.
    *******************************************************************************/
   public void setFields(Map<String, String> fields)
   {
      this.fields = fields;
   }



   /********************************************************************************
    ** Fluent setter for fields.
    *******************************************************************************/
   public PresignedPost withFields(Map<String, String> fields)
   {
      this.fields = fields;
      return (this);
   }

}
