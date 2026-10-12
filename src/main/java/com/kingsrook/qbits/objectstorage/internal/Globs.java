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

package com.kingsrook.qbits.objectstorage.internal;


import java.util.regex.Pattern;


/*******************************************************************************
 ** Glob matching for keys: * matches any run of characters (slashes included),
 ** ? matches one character.
 *******************************************************************************/
public final class Globs
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private Globs()
   {
   }



   /*******************************************************************************
    ** Whether a glob matches a whole string.
    *******************************************************************************/
   public static boolean matches(String glob, String value)
   {
      return (toPattern(glob).matcher(value == null ? "" : value).matches());
   }



   /*******************************************************************************
    ** Compile a glob.
    *******************************************************************************/
   public static Pattern toPattern(String glob)
   {
      StringBuilder sb = new StringBuilder();
      for(char c : glob.toCharArray())
      {
         if(c == '*')
         {
            sb.append(".*");
         }
         else if(c == '?')
         {
            sb.append('.');
         }
         else
         {
            sb.append(Pattern.quote(String.valueOf(c)));
         }
      }
      return (Pattern.compile(sb.toString(), Pattern.DOTALL));
   }
}
