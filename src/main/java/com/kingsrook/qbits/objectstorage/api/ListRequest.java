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
 ** A listing request: prefix, optional delimiter, page size and continuation token.
 *******************************************************************************/
public class ListRequest
{
   private String prefix = "";
   private String delimiter;
   private Integer pageSize = 1000;
   private String after;
   private String startAfter;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public ListRequest()
   {
   }



   /********************************************************************************
    ** Getter for prefix.
    *******************************************************************************/
   public String getPrefix()
   {
      return (this.prefix);
   }



   /********************************************************************************
    ** Setter for prefix.
    *******************************************************************************/
   public void setPrefix(String prefix)
   {
      this.prefix = prefix;
   }



   /********************************************************************************
    ** Fluent setter for prefix.
    *******************************************************************************/
   public ListRequest withPrefix(String prefix)
   {
      this.prefix = prefix;
      return (this);
   }



   /********************************************************************************
    ** Getter for delimiter.
    *******************************************************************************/
   public String getDelimiter()
   {
      return (this.delimiter);
   }



   /********************************************************************************
    ** Setter for delimiter.
    *******************************************************************************/
   public void setDelimiter(String delimiter)
   {
      this.delimiter = delimiter;
   }



   /********************************************************************************
    ** Fluent setter for delimiter.
    *******************************************************************************/
   public ListRequest withDelimiter(String delimiter)
   {
      this.delimiter = delimiter;
      return (this);
   }



   /********************************************************************************
    ** Getter for pageSize.
    *******************************************************************************/
   public Integer getPageSize()
   {
      return (this.pageSize);
   }



   /********************************************************************************
    ** Setter for pageSize.
    *******************************************************************************/
   public void setPageSize(Integer pageSize)
   {
      this.pageSize = pageSize;
   }



   /********************************************************************************
    ** Fluent setter for pageSize.
    *******************************************************************************/
   public ListRequest withPageSize(Integer pageSize)
   {
      this.pageSize = pageSize;
      return (this);
   }



   /********************************************************************************
    ** Getter for after.
    *******************************************************************************/
   public String getAfter()
   {
      return (this.after);
   }



   /********************************************************************************
    ** Setter for after.
    *******************************************************************************/
   public void setAfter(String after)
   {
      this.after = after;
   }



   /********************************************************************************
    ** Fluent setter for after.
    *******************************************************************************/
   public ListRequest withAfter(String after)
   {
      this.after = after;
      return (this);
   }



   /********************************************************************************
    ** Getter for startAfter.
    *******************************************************************************/
   public String getStartAfter()
   {
      return (this.startAfter);
   }



   /********************************************************************************
    ** Setter for startAfter.
    *******************************************************************************/
   public void setStartAfter(String startAfter)
   {
      this.startAfter = startAfter;
   }



   /********************************************************************************
    ** Fluent setter for startAfter.
    *******************************************************************************/
   public ListRequest withStartAfter(String startAfter)
   {
      this.startAfter = startAfter;
      return (this);
   }



   /*******************************************************************************
    ** A request for a prefix.
    *******************************************************************************/
   public static ListRequest prefix(String prefix)
   {
      return (new ListRequest().withPrefix(prefix));
   }



   /*******************************************************************************
    ** Short-form fluent setter for delimiter.
    *******************************************************************************/
   public ListRequest delimiter(String delimiter)
   {
      this.delimiter = delimiter;
      return (this);
   }



   /*******************************************************************************
    ** Short-form fluent setter for pageSize.
    *******************************************************************************/
   public ListRequest pageSize(Integer pageSize)
   {
      this.pageSize = pageSize;
      return (this);
   }



   /*******************************************************************************
    ** Short-form fluent setter for after.
    *******************************************************************************/
   public ListRequest after(String after)
   {
      this.after = after;
      return (this);
   }



   /*******************************************************************************
    ** Short-form fluent setter for startAfter.
    *******************************************************************************/
   public ListRequest startAfter(String startAfter)
   {
      this.startAfter = startAfter;
      return (this);
   }

}
