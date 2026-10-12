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


import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Unit test for StorageKeys.
 *******************************************************************************/
class StorageKeysTest
{
   /*******************************************************************************
    ** Normalization collapses slashes, strips a leading slash, and rejects the unsafe.
    *******************************************************************************/
   @Test
   void testNormalize()
   {
      assertThat(StorageKeys.normalize("//a///b/c.txt")).isEqualTo("a/b/c.txt");
      assertThat(StorageKeys.normalize("name.")).isEqualTo("name.");
      assertThatThrownBy(() -> StorageKeys.normalize("a/../b")).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> StorageKeys.normalize("a\nb")).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> StorageKeys.normalize("")).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> StorageKeys.normalize(null)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> StorageKeys.normalize("///")).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> StorageKeys.normalize("x".repeat(1025))).isInstanceOf(IllegalArgumentException.class);
      assertThat(StorageKeys.normalize("x".repeat(1024))).hasSize(1024);
      assertThat(StorageKeys.normalize("\u00e9".repeat(512))).hasSize(512);
      assertThatThrownBy(() -> StorageKeys.normalize("\u00e9".repeat(513))).isInstanceOf(IllegalArgumentException.class);
   }



   /*******************************************************************************
    ** Prefixes keep a trailing slash and may be empty.
    *******************************************************************************/
   @Test
   void testNormalizePrefix()
   {
      assertThat(StorageKeys.normalizePrefix(null)).isEmpty();
      assertThat(StorageKeys.normalizePrefix("/")).isEmpty();
      assertThat(StorageKeys.normalizePrefix("//media//42/")).isEqualTo("media/42/");
      assertThat(StorageKeys.normalizePrefix("media")).isEqualTo("media");
   }



   /*******************************************************************************
    ** Safe file names and the content-addressed layout.
    *******************************************************************************/
   @Test
   void testFilenamesAndContentAddressing()
   {
      assertThat(StorageKeys.safeFilename("../../etc/passwd")).isEqualTo("passwd");
      assertThat(StorageKeys.safeFilename("C:\\temp\\photo.jpg")).isEqualTo("photo.jpg");
      assertThat(StorageKeys.safeFilename("...")).isEqualTo("file");
      assertThat(StorageKeys.safeFilename(null)).isEqualTo("file");
      assertThat(StorageKeys.safeFilename(".hidden")).isEqualTo("hidden");

      String key = StorageKeys.contentAddressed("media", "hero", "abc".getBytes(), "../Photo One.jpg");
      assertThat(key).isEqualTo("media/hero/ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad/Photo One.jpg");
   }



   /*******************************************************************************
    ** URL encoding handles the characters behind "works on MinIO, 403 on AWS".
    *******************************************************************************/
   @Test
   void testUrlEncoding()
   {
      String key = "a b/c+d/100%/caf\u00e9.txt";
      String encoded = StorageKeys.encodeForUrl(key);
      assertThat(encoded).isEqualTo("a%20b/c%2Bd/100%25/caf%C3%A9.txt");
      assertThat(StorageKeys.decodeFromUrl(encoded)).isEqualTo(key);
      assertThat(StorageKeys.decodeFromUrl("plain/path.txt")).isEqualTo("plain/path.txt");
      assertThat(StorageKeys.decodeFromUrl("trailing%")).isEqualTo("trailing%");
   }
}
