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

package com.kingsrook.qbits.objectstorage;


import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import com.kingsrook.qbits.objectstorage.internal.DefaultsChangelogTest;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageHelp;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Guardrail (owner requirement 2026-10-14: "all tables should be populated and
 ** the qbit should be fully using them"). For every object_storage_* table:
 ** - runtime code (not just the model, metadata or home screen) reads or writes
 **   it, or it is listed in NOT_USED_YET with what is missing;
 ** - it is either seeded by the shipped defaults changelog, or listed in
 **   STARTS_EMPTY with why;
 ** - it has bundle help that says how its rows get created (shown on the table
 **   screen and when it is empty).
 ** A new table fails this test until it is wired, seeded or explained.
 *******************************************************************************/
class ObjectStorageTablesGuardrailTest
{
   private static final Path MAIN = Path.of("src", "main", "java", "com", "kingsrook", "qbits", "objectstorage");

   /*******************************************************************************
    ** Tables that no runtime code writes yet, and what is missing. Their help says
    ** "Not used yet".
    *******************************************************************************/
   static final Map<String, String> NOT_USED_YET = Map.of(
      ObjectStorageTables.OBJECT_VERSION, "the indexer lists current versions only (ListObjectsV2); indexing version history is a later release");

   /*******************************************************************************
    ** Tables that legitimately start empty, and where their rows come from.
    *******************************************************************************/
   static final Map<String, String> STARTS_EMPTY = Map.ofEntries(
      Map.entry(ObjectStorageTables.CONNECTION, "written from code by the config sync: the host declares its connections"),
      Map.entry(ObjectStorageTables.BUCKET, "written from code by the config sync: the host declares its buckets"),
      Map.entry(ObjectStorageTables.CONNECTION_CAPABILITY, "runtime: Test connection probes the provider"),
      Map.entry(ObjectStorageTables.BUCKET_CAPABILITY, "runtime: Test connection and Check configuration probe the bucket"),
      Map.entry(ObjectStorageTables.FOLDER, "runtime: the index scan and uploads"),
      Map.entry(ObjectStorageTables.OBJECT, "runtime: the index scan and write-through"),
      Map.entry(ObjectStorageTables.OBJECT_VERSION, "not used yet: see NOT_USED_YET"),
      Map.entry(ObjectStorageTables.OBJECT_REFERENCE, "runtime: the reference scan, when the host registers a reference provider"),
      Map.entry(ObjectStorageTables.PROTECTION_RULE, "a rule needs a bucket, which is host-specific: an administrator adds each rule"),
      Map.entry(ObjectStorageTables.OPERATION, "log: every operation the qbit performs"),
      Map.entry(ObjectStorageTables.USAGE_DAILY, "runtime: the nightly job"));



   /*******************************************************************************
    ** Tear down.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      StorageTestSupport.tearDown();
   }



   /*******************************************************************************
    ** Every table is read or written by runtime code, or explicitly not used yet.
    *******************************************************************************/
   @Test
   void everyTableIsUsedAtRuntime() throws Exception
   {
      List<String> runtimeSource;
      try(Stream<Path> files = Files.walk(MAIN))
      {
         runtimeSource = files.filter(p -> p.toString().endsWith(".java"))
            .filter(p -> !p.startsWith(MAIN.resolve("model")) && !p.startsWith(MAIN.resolve("app")))
            .map(ObjectStorageTablesGuardrailTest::read).toList();
      }
      for(String table : ObjectStorageTables.ALL)
      {
         String  constant = "ObjectStorageTables." + constantFor(table);
         boolean used     = runtimeSource.stream().anyMatch(source -> source.contains(constant));
         if(NOT_USED_YET.containsKey(table))
         {
            assertThat(used).as(table + " is listed as not used yet, but runtime code uses it: remove it from NOT_USED_YET").isFalse();
         }
         else
         {
            assertThat(used).as(table + " must be read or written by runtime code, not only declared").isTrue();
         }
      }
   }



   /*******************************************************************************
    ** Every table is seeded or explained.
    *******************************************************************************/
   @Test
   void everyTableIsSeededOrExplained() throws Exception
   {
      String changelog = DefaultsChangelogTest.changelogText();
      for(String table : ObjectStorageTables.ALL)
      {
         boolean seeded = DefaultsChangelogTest.SEEDED_TABLES.contains(table);
         assertThat(seeded || STARTS_EMPTY.containsKey(table)).as(table + " must be seeded by the defaults changelog or listed in STARTS_EMPTY").isTrue();
         assertThat(seeded && STARTS_EMPTY.containsKey(table)).as(table + " cannot be both seeded and listed as starting empty").isFalse();
         assertThat(changelog.contains("INSERT INTO " + table + " ")).as(table + " seeded iff the changelog inserts it").isEqualTo(seeded);
      }
      assertThat(STARTS_EMPTY.keySet()).allMatch(ObjectStorageTables.ALL::contains, "STARTS_EMPTY names only real tables");
      assertThat(NOT_USED_YET.keySet()).allMatch(ObjectStorageTables.ALL::contains, "NOT_USED_YET names only real tables");
   }



   /*******************************************************************************
    ** Every table and the app have bundle help that says how rows get created,
    ** and the not-yet-used tables say so.
    *******************************************************************************/
   @Test
   void everyTableHasEmptyStateHelp() throws Exception
   {
      QInstance instance = StorageTestSupport.setUp(new ObjectStorageQBitConfig().withBackendName(StorageTestSupport.MEMORY).withBucketScoping(false).withOpenWhenAnonymous(true).withAdminApp(true));
      assertThat(ObjectStorageHelp.entries()).allSatisfy((key, entry) -> assertThat(entry.path("summary").asText().length()).as(key).isLessThanOrEqualTo(120));
      for(String table : ObjectStorageTables.ALL)
      {
         List<QHelpContent> help = instance.getTable(table).getHelpContent() == null ? null : instance.getTable(table).getHelpContent().get(ObjectStorageHelp.SLOT);
         assertThat(help).as(table + " table help").isNotEmpty();
         assertThat(help.get(0).getContent()).as(table + " help says how rows get created").contains("How rows get created");
         if(NOT_USED_YET.containsKey(table))
         {
            assertThat(help.get(0).getContent()).as(table + " help says it is not used yet").contains("Not used yet");
         }
      }
      assertThat(instance.getApp("objectStorageAdmin").getHelpContent()).containsKey(ObjectStorageHelp.SLOT);
      assertThat(ObjectStorageHelp.entries().keySet()).allMatch(key -> !key.startsWith("table:") || instance.getTable(key.substring(6, key.indexOf(';'))) != null,
         "help names only real tables");
   }



   /*******************************************************************************
    ** The ObjectStorageTables constant name of a table.
    *******************************************************************************/
   private static String constantFor(String table) throws Exception
   {
      for(Field field : ObjectStorageTables.class.getFields())
      {
         if(field.getType() == String.class && table.equals(field.get(null)) && !field.getName().startsWith("KEY_") && !field.getName().startsWith("PVS_") && !field.getName().startsWith("ALL_"))
         {
            return (field.getName());
         }
      }
      throw (new IllegalStateException("no constant for " + table));
   }



   /*******************************************************************************
    ** Read a file.
    *******************************************************************************/
   private static String read(Path path)
   {
      try
      {
         return (Files.readString(path, StandardCharsets.UTF_8));
      }
      catch(Exception e)
      {
         throw (new IllegalStateException(e));
      }
   }

}
