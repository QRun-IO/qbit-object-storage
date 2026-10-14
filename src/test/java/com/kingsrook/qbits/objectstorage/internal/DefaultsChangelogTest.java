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


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaders;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.ProviderType;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageSchema;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The shipped starter defaults (db/liquibase/qbit-object-storage/
 ** object-storage-defaults.changelog.sql), run through real Liquibase on H2 in
 ** PostgreSQL mode against the schema ObjectStorageSchema emits: they apply,
 ** a second run inserts nothing and keeps an administrator's edits, a host that
 ** already has the rule or lacks the table is skipped (MARK_RAN), and enabling
 ** the seeded rule changes the headers the policy gives.
 *******************************************************************************/
public class DefaultsChangelogTest
{
   static final String CHANGELOG = "/db/liquibase/qbit-object-storage/object-storage-defaults.changelog.sql";

   /** The tables the changelog seeds. */
   public static final Set<String> SEEDED_TABLES = Set.of(ObjectStorageTables.HEADER_RULE);



   /*******************************************************************************
    ** Tear down.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      StorageTestSupport.tearDown();
   }



   /*******************************************************************************
    ** Fresh schema, seed twice: the same single disabled rule, and an edit survives.
    *******************************************************************************/
   @Test
   void seedsAFreshSchemaInsertOnly() throws Exception
   {
      try(Connection connection = freshSchema(); Statement statement = connection.createStatement())
      {
         runChangelog(connection);
         assertThat(scalar(connection, "SELECT COUNT(*) FROM object_storage_header_rule")).isEqualTo("1");
         assertThat(scalar(connection, "SELECT name || '|' || key_glob || '|' || cache_control || '|' || priority || '|' || enabled FROM object_storage_header_rule"))
            .isEqualTo("default-mutable|**|public, max-age=3600|1000|FALSE");
         assertThat(scalar(connection, "SELECT bucket_id FROM object_storage_header_rule")).as("generic: not tied to a bucket").isNull();

         statement.execute("UPDATE object_storage_header_rule SET cache_control = 'no-cache', enabled = TRUE WHERE name = 'default-mutable'");
         runChangelog(connection);
         assertThat(scalar(connection, "SELECT COUNT(*) FROM object_storage_header_rule")).isEqualTo("1");
         assertThat(scalar(connection, "SELECT cache_control || '|' || enabled FROM object_storage_header_rule")).as("the edit survives").isEqualTo("no-cache|TRUE");
         assertThat(ranChangesets(connection)).containsEntry("defaults-1", "EXECUTED");
      }
   }



   /*******************************************************************************
    ** A host that already has a rule of that name keeps it untouched, and the
    ** changeset is marked ran. A host without the table is skipped, not failed.
    *******************************************************************************/
   @Test
   void existingRuleAndMissingTableAreSkipped() throws Exception
   {
      try(Connection connection = freshSchema(); Statement statement = connection.createStatement())
      {
         statement.execute("INSERT INTO object_storage_header_rule (name, key_glob, cache_control, priority, enabled, create_date, modify_date) "
            + "VALUES ('default-mutable', '*.css', 'immutable', 5, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
         runChangelog(connection);
         assertThat(scalar(connection, "SELECT COUNT(*) FROM object_storage_header_rule")).isEqualTo("1");
         assertThat(scalar(connection, "SELECT key_glob || '|' || enabled FROM object_storage_header_rule")).isEqualTo("*.css|TRUE");
         assertThat(ranChangesets(connection)).containsEntry("defaults-1", "MARK_RAN");
      }
      try(Connection connection = freshSchema(); Statement statement = connection.createStatement())
      {
         statement.execute("DROP TABLE object_storage_header_rule");
         runChangelog(connection);
         assertThat(ranChangesets(connection)).containsEntry("defaults-1", "MARK_RAN");
      }
   }



   /*******************************************************************************
    ** Every changeset guards itself, uses only the sql-check precondition form,
    ** and inserts disabled rows only.
    *******************************************************************************/
   @Test
   void everyChangesetIsGuarded() throws Exception
   {
      String text = changelogText();
      String[] changesets = text.split("(?m)^-- changeset ");
      assertThat(changesets).hasSize(2);
      assertThat(changesets[1]).contains("-- preconditions onFail:MARK_RAN onError:MARK_RAN").contains("-- precondition-sql-check expectedResult:0 ")
         .contains("-- rollback ").contains("NOT EXISTS").contains("FALSE, CURRENT_TIMESTAMP");
      assertThat(text).doesNotContain("precondition-table-exists");
      for(String table : SEEDED_TABLES)
      {
         assertThat(text).contains("INSERT INTO " + table + " ");
      }
   }



   /*******************************************************************************
    ** The seeded rule is inert until enabled (an existing host's behavior does not
    ** change on deploy), and enabling it gives every object the header.
    *******************************************************************************/
   @Test
   void enablingTheSeededRuleChangesHeaders() throws Exception
   {
      ObjectStorageQBitConfig config = config();
      StorageTestSupport.setUp(config);
      StorageTestSupport.insert(ObjectStorageTables.HEADER_RULE, new QRecord().withValue("name", "default-mutable").withValue("keyGlob", "**")
         .withValue("cacheControl", "public, max-age=3600").withValue("priority", 1000).withValue("enabled", false));
      DefaultObjectHeaderPolicy.clearCache();
      StorageBucketConfig media = config.findBucket("media");

      assertThat(new DefaultObjectHeaderPolicy().headersFor(media, "any/key.bin", "application/octet-stream").isEmpty()).as("disabled: no behavior change").isTrue();

      Db.update(ObjectStorageTables.HEADER_RULE, List.of(new QRecord().withValue("id", Db.first(ObjectStorageTables.HEADER_RULE, "name", "default-mutable").getValue("id")).withValue("enabled", true)));
      DefaultObjectHeaderPolicy.clearCache();
      ObjectHeaders headers = new DefaultObjectHeaderPolicy().headersFor(media, "any/key.bin", "application/octet-stream");
      assertThat(headers.getCacheControl()).isEqualTo("public, max-age=3600");
   }



   /*******************************************************************************
    ** A small config.
    *******************************************************************************/
   private static ObjectStorageQBitConfig config()
   {
      return (new ObjectStorageQBitConfig().withBackendName(StorageTestSupport.MEMORY).withBucketScoping(false).withOpenWhenAnonymous(true).withAdminApp(true)
         .withConnection(new StorageConnectionConfig("main").withProviderType(ProviderType.MINIO).withEndpoint("http://localhost:1").withRegion("us-east-1"))
         .withBucket(new StorageBucketConfig("media").withConnection("main").withBucketName("media-bucket").withRole(BucketRole.PRIVATE).withIndexed(false)));
   }



   /*******************************************************************************
    ** A fresh H2 (PostgreSQL mode) with the schema the qbit emits.
    *******************************************************************************/
   static Connection freshSchema() throws Exception
   {
      Connection connection = DriverManager.getConnection("jdbc:h2:mem:osdefaults" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DEFAULT_NULL_ORDERING=HIGH");
      try(Statement statement = connection.createStatement())
      {
         for(String sql : ObjectStorageSchema.postgresStatements(config()))
         {
            statement.execute(sql);
         }
      }
      return (connection);
   }



   /*******************************************************************************
    ** Run the changelog through Liquibase, as a host does.
    *******************************************************************************/
   static void runChangelog(Connection connection) throws Exception
   {
      Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
      new Liquibase(CHANGELOG.substring(1), new ClassLoaderResourceAccessor(), database).update(new Contexts());
   }



   /*******************************************************************************
    ** changeset id to EXECTYPE (EXECUTED, MARK_RAN, ...).
    *******************************************************************************/
   static Map<String, String> ranChangesets(Connection connection) throws Exception
   {
      Map<String, String> rs = new HashMap<>();
      try(Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT id, exectype FROM databasechangelog"))
      {
         while(result.next())
         {
            rs.put(result.getString(1), result.getString(2));
         }
      }
      return (rs);
   }



   /*******************************************************************************
    ** The first column of the first row, as text (null when none).
    *******************************************************************************/
   static String scalar(Connection connection, String sql) throws Exception
   {
      try(Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql))
      {
         return (result.next() ? result.getString(1) : null);
      }
   }



   /*******************************************************************************
    ** The changelog's text.
    *******************************************************************************/
   public static String changelogText() throws Exception
   {
      try(InputStream stream = DefaultsChangelogTest.class.getResourceAsStream(CHANGELOG))
      {
         assertThat(stream).as(CHANGELOG + " is on the classpath").isNotNull();
         return (new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
   }
}
