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


import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;


/*******************************************************************************
 ** Builds and validates object keys, and percent-encodes them for URLs.
 ** Apps keep their own key layouts; this only makes them consistent and safe.
 *******************************************************************************/
public final class StorageKeys
{
   public static final int MAX_KEY_BYTES = 1024;

   private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private StorageKeys()
   {
   }



   /*******************************************************************************
    ** Normalize and validate: collapse //, strip a leading /, reject .. segments,
    ** control characters and keys over 1,024 bytes. Returns the normalized key.
    *******************************************************************************/
   public static String normalize(String key)
   {
      if(key == null || key.isEmpty())
      {
         throw (new IllegalArgumentException("An object key is required"));
      }
      if(CONTROL_CHARS.matcher(key).find())
      {
         throw (new IllegalArgumentException("An object key may not contain control characters"));
      }
      String normalized = key.replaceAll("/{2,}", "/");
      while(normalized.startsWith("/"))
      {
         normalized = normalized.substring(1);
      }
      if(normalized.isEmpty())
      {
         throw (new IllegalArgumentException("An object key is required"));
      }
      for(String segment : normalized.split("/", -1))
      {
         if("..".equals(segment))
         {
            throw (new IllegalArgumentException("An object key may not contain .. segments"));
         }
      }
      if(normalized.getBytes(StandardCharsets.UTF_8).length > MAX_KEY_BYTES)
      {
         throw (new IllegalArgumentException("An object key may not be longer than " + MAX_KEY_BYTES + " bytes"));
      }
      return (normalized);
   }



   /*******************************************************************************
    ** Normalize a prefix (a key that may be empty or end with a slash).
    *******************************************************************************/
   public static String normalizePrefix(String prefix)
   {
      if(prefix == null || prefix.isEmpty() || "/".equals(prefix))
      {
         return ("");
      }
      boolean trailing = prefix.endsWith("/");
      String  n        = normalize(prefix);
      return (trailing && !n.endsWith("/") ? n + "/" : n);
   }



   /*******************************************************************************
    ** A safe file name: no path separators or control characters, no leading dots.
    *******************************************************************************/
   public static String safeFilename(String name)
   {
      String n = name == null ? "" : name.replace('\\', '/');
      n = n.substring(n.lastIndexOf('/') + 1);
      n = CONTROL_CHARS.matcher(n).replaceAll("");
      n = n.replaceAll("^\\.+", "").trim();
      return (n.isEmpty() ? "file" : n);
   }



   /*******************************************************************************
    ** The content-addressed layout {scope}/{slug}/{sha256}/{name}.
    *******************************************************************************/
   public static String contentAddressed(String scope, String slug, byte[] content, String name)
   {
      return (normalize(scope + "/" + slug + "/" + sha256Hex(content) + "/" + safeFilename(name)));
   }



   /*******************************************************************************
    ** Lowercase hex SHA-256.
    *******************************************************************************/
   public static String sha256Hex(byte[] content)
   {
      try
      {
         return (HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
      }
      catch(NoSuchAlgorithmException e)
      {
         throw (new IllegalStateException(e));
      }
   }



   /*******************************************************************************
    ** Percent-encode a key for use in a URL path: slashes stay, and '+', space and
    ** non-ASCII are encoded (the usual source of "works on MinIO, 403 on AWS").
    *******************************************************************************/
   public static String encodeForUrl(String key)
   {
      StringBuilder out = new StringBuilder();
      for(byte b : key.getBytes(StandardCharsets.UTF_8))
      {
         char c = (char) (b & 0xFF);
         if((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~' || c == '/')
         {
            out.append(c);
         }
         else
         {
            out.append('%').append(String.format("%02X", b & 0xFF));
         }
      }
      return (out.toString());
   }



   /*******************************************************************************
    ** The reverse of {@link #encodeForUrl(String)}.
    *******************************************************************************/
   public static String decodeFromUrl(String encoded)
   {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      for(int i = 0; i < encoded.length(); i++)
      {
         char c = encoded.charAt(i);
         if(c == '%' && i + 2 < encoded.length())
         {
            out.write(Integer.parseInt(encoded.substring(i + 1, i + 3), 16));
            i += 2;
         }
         else
         {
            byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            out.write(bytes, 0, bytes.length);
         }
      }
      return (out.toString(StandardCharsets.UTF_8));
   }
}
