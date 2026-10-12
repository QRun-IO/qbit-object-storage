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


import java.util.ArrayList;
import java.util.List;


/********************************************************************************
 ** One page of results plus a continuation token (null at the end) and, for delimiter listings, the common prefixes.
 *******************************************************************************/
public class Page<T>
{
   private List<T> items = new ArrayList<>();
   private List<String> prefixes = new ArrayList<>();
   private String nextToken;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public Page()
   {
   }



   /********************************************************************************
    ** Getter for items.
    *******************************************************************************/
   public List<T> getItems()
   {
      return (this.items);
   }



   /********************************************************************************
    ** Setter for items.
    *******************************************************************************/
   public void setItems(List<T> items)
   {
      this.items = items;
   }



   /********************************************************************************
    ** Fluent setter for items.
    *******************************************************************************/
   public Page<T> withItems(List<T> items)
   {
      this.items = items;
      return (this);
   }



   /********************************************************************************
    ** Getter for prefixes.
    *******************************************************************************/
   public List<String> getPrefixes()
   {
      return (this.prefixes);
   }



   /********************************************************************************
    ** Setter for prefixes.
    *******************************************************************************/
   public void setPrefixes(List<String> prefixes)
   {
      this.prefixes = prefixes;
   }



   /********************************************************************************
    ** Fluent setter for prefixes.
    *******************************************************************************/
   public Page<T> withPrefixes(List<String> prefixes)
   {
      this.prefixes = prefixes;
      return (this);
   }



   /********************************************************************************
    ** Getter for nextToken.
    *******************************************************************************/
   public String getNextToken()
   {
      return (this.nextToken);
   }



   /********************************************************************************
    ** Setter for nextToken.
    *******************************************************************************/
   public void setNextToken(String nextToken)
   {
      this.nextToken = nextToken;
   }



   /********************************************************************************
    ** Fluent setter for nextToken.
    *******************************************************************************/
   public Page<T> withNextToken(String nextToken)
   {
      this.nextToken = nextToken;
      return (this);
   }

}
