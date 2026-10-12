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
 ** A byte range for a get.
 *******************************************************************************/
public class Range
{
   private Long from;
   private Long to;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public Range()
   {
   }



   /********************************************************************************
    ** Getter for from.
    *******************************************************************************/
   public Long getFrom()
   {
      return (this.from);
   }



   /********************************************************************************
    ** Setter for from.
    *******************************************************************************/
   public void setFrom(Long from)
   {
      this.from = from;
   }



   /********************************************************************************
    ** Fluent setter for from.
    *******************************************************************************/
   public Range withFrom(Long from)
   {
      this.from = from;
      return (this);
   }



   /********************************************************************************
    ** Getter for to.
    *******************************************************************************/
   public Long getTo()
   {
      return (this.to);
   }



   /********************************************************************************
    ** Setter for to.
    *******************************************************************************/
   public void setTo(Long to)
   {
      this.to = to;
   }



   /********************************************************************************
    ** Fluent setter for to.
    *******************************************************************************/
   public Range withTo(Long to)
   {
      this.to = to;
      return (this);
   }



   /*******************************************************************************
    ** The whole object.
    *******************************************************************************/
   public static Range all()
   {
      return (new Range());
   }



   /*******************************************************************************
    ** Bytes from..to inclusive.
    *******************************************************************************/
   public static Range bytes(long from, long to)
   {
      return (new Range().withFrom(from).withTo(to));
   }

}
