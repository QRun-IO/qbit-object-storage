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


import java.util.List;
import java.util.regex.Pattern;
import com.kingsrook.qqq.backend.core.instances.QMetaDataVariableInterpreter;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Enforces the qbit's secret-reference allow-list and resolves references
 ** (${env.X}, ${prop.X}, ${file.PATH}) through core QMetaDataVariableInterpreter.
 ** Core reads ${file.PATH} read-only from its allowed roots (default
 ** /var/run/secrets and /run/secrets; qqq.fileReference.roots or
 ** QQQ_FILE_REFERENCE_ROOTS). Resolved values live only in memory, never in a
 ** record, a log line or a process value.
 *******************************************************************************/
public final class SecretRefs
{
   public static final List<String> DEFAULT_ALLOWED_PATTERNS = List.of("env.S3_*", "env.STORAGE_*", "file./var/run/secrets/storage/*");



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private SecretRefs()
   {
   }



   /*******************************************************************************
    ** Whether the reference is of the form ${...} and its inside matches an
    ** allowed glob.
    *******************************************************************************/
   public static boolean isAllowed(String ref, List<String> allowedPatterns)
   {
      if(!StringUtils.hasContent(ref))
      {
         return (true);
      }
      String trimmed = ref.trim();
      if(!trimmed.startsWith("${") || !trimmed.endsWith("}"))
      {
         return (false);
      }
      String inside = trimmed.substring(2, trimmed.length() - 1);
      for(String pattern : allowedPatterns)
      {
         if(globToRegex(pattern).matcher(inside).matches())
         {
            return (true);
         }
      }
      return (false);
   }



   /*******************************************************************************
    ** The value a reference points at, or null when it is empty or unset.
    *******************************************************************************/
   public static String resolve(String ref)
   {
      if(!StringUtils.hasContent(ref))
      {
         return (null);
      }
      String value = new QMetaDataVariableInterpreter().interpret(ref.trim());
      return (value == null || value.startsWith("${") ? null : value);
   }



   /*******************************************************************************
    ** Resolve a non-secret setting (endpoint, region) that may be a variable.
    *******************************************************************************/
   public static String resolveSetting(String value)
   {
      return (StringUtils.hasContent(value) ? resolve(value) : null);
   }



   /*******************************************************************************
    ** Glob (only * is special) to regex.
    *******************************************************************************/
   private static Pattern globToRegex(String glob)
   {
      StringBuilder sb = new StringBuilder();
      for(char c : glob.toCharArray())
      {
         sb.append(c == '*' ? ".*" : Pattern.quote(String.valueOf(c)));
      }
      return (Pattern.compile(sb.toString()));
   }
}
