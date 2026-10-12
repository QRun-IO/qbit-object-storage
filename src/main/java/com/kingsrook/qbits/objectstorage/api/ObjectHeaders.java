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


import java.util.Map;
import java.util.Objects;


/********************************************************************************
 ** The HTTP headers and user metadata stored with an object.
 *******************************************************************************/
public class ObjectHeaders
{
   private String cacheControl;
   private String contentType;
   private String contentDisposition;
   private String contentEncoding;
   private Map<String, String> userMetadata;



   /********************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public ObjectHeaders()
   {
   }



   /********************************************************************************
    ** Getter for cacheControl.
    *******************************************************************************/
   public String getCacheControl()
   {
      return (this.cacheControl);
   }



   /********************************************************************************
    ** Setter for cacheControl.
    *******************************************************************************/
   public void setCacheControl(String cacheControl)
   {
      this.cacheControl = cacheControl;
   }



   /********************************************************************************
    ** Fluent setter for cacheControl.
    *******************************************************************************/
   public ObjectHeaders withCacheControl(String cacheControl)
   {
      this.cacheControl = cacheControl;
      return (this);
   }



   /********************************************************************************
    ** Getter for contentType.
    *******************************************************************************/
   public String getContentType()
   {
      return (this.contentType);
   }



   /********************************************************************************
    ** Setter for contentType.
    *******************************************************************************/
   public void setContentType(String contentType)
   {
      this.contentType = contentType;
   }



   /********************************************************************************
    ** Fluent setter for contentType.
    *******************************************************************************/
   public ObjectHeaders withContentType(String contentType)
   {
      this.contentType = contentType;
      return (this);
   }



   /********************************************************************************
    ** Getter for contentDisposition.
    *******************************************************************************/
   public String getContentDisposition()
   {
      return (this.contentDisposition);
   }



   /********************************************************************************
    ** Setter for contentDisposition.
    *******************************************************************************/
   public void setContentDisposition(String contentDisposition)
   {
      this.contentDisposition = contentDisposition;
   }



   /********************************************************************************
    ** Fluent setter for contentDisposition.
    *******************************************************************************/
   public ObjectHeaders withContentDisposition(String contentDisposition)
   {
      this.contentDisposition = contentDisposition;
      return (this);
   }



   /********************************************************************************
    ** Getter for contentEncoding.
    *******************************************************************************/
   public String getContentEncoding()
   {
      return (this.contentEncoding);
   }



   /********************************************************************************
    ** Setter for contentEncoding.
    *******************************************************************************/
   public void setContentEncoding(String contentEncoding)
   {
      this.contentEncoding = contentEncoding;
   }



   /********************************************************************************
    ** Fluent setter for contentEncoding.
    *******************************************************************************/
   public ObjectHeaders withContentEncoding(String contentEncoding)
   {
      this.contentEncoding = contentEncoding;
      return (this);
   }



   /********************************************************************************
    ** Getter for userMetadata.
    *******************************************************************************/
   public Map<String, String> getUserMetadata()
   {
      return (this.userMetadata);
   }



   /********************************************************************************
    ** Setter for userMetadata.
    *******************************************************************************/
   public void setUserMetadata(Map<String, String> userMetadata)
   {
      this.userMetadata = userMetadata;
   }



   /********************************************************************************
    ** Fluent setter for userMetadata.
    *******************************************************************************/
   public ObjectHeaders withUserMetadata(Map<String, String> userMetadata)
   {
      this.userMetadata = userMetadata;
      return (this);
   }



   /*******************************************************************************
    ** Headers with only a Cache-Control.
    *******************************************************************************/
   public static ObjectHeaders cacheControl(String cacheControl)
   {
      return (new ObjectHeaders().withCacheControl(cacheControl));
   }



   /*******************************************************************************
    ** Whether no header or metadata is set.
    *******************************************************************************/
   public boolean isEmpty()
   {
      return (cacheControl == null && contentType == null && contentDisposition == null && contentEncoding == null && (userMetadata == null || userMetadata.isEmpty()));
   }



   /*******************************************************************************
    ** Equality on the headers that matter for policy checks (Cache-Control and
    ** Content-Disposition; null and blank are the same).
    *******************************************************************************/
   public boolean policyEquals(ObjectHeaders other)
   {
      return (other != null
         && Objects.equals(blankToNull(cacheControl), blankToNull(other.cacheControl))
         && Objects.equals(blankToNull(contentDisposition), blankToNull(other.contentDisposition)));
   }



   /*******************************************************************************
    ** Blank to null.
    *******************************************************************************/
   private static String blankToNull(String s)
   {
      return (s == null || s.isBlank() ? null : s.trim());
   }

}
