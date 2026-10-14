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

package com.kingsrook.qbits.objectstorage.index;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.api.FieldReferenceProvider;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.conformance.ConformanceTarget;
import com.kingsrook.qbits.objectstorage.conformance.Targets;
import com.kingsrook.qbits.objectstorage.config.ChecksumMode;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageSchema;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.processes.DeleteRestoreProcesses;
import com.kingsrook.qbits.objectstorage.testsupport.ProcessRunner;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinOn;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import com.kingsrook.qqq.backend.module.postgres.model.metadata.PostgreSQLBackendMetaData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The object index on its intended store: PostgreSQL, with the schema this qbit
 ** generates (ObjectStorageSchema) and the real SQL of scans, references, record
 ** locks and actions, over a MinIO bucket.
 *******************************************************************************/
class PostgresIndexTest
{
   private static PostgreSQLContainer postgres;

   private ConformanceTarget target;



   /*******************************************************************************
    ** Start PostgreSQL once.
    *******************************************************************************/
   @BeforeAll
   static void startPostgres()
   {
      Assumptions.assumeTrue(Targets.dockerAvailable(), "Docker is not available");
      postgres = new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("storage").withUsername("qqq").withPassword("qqq");
      postgres.start();
   }



   /*******************************************************************************
    ** Stop PostgreSQL.
    *******************************************************************************/
   @AfterAll
   static void stopPostgres()
   {
      if(postgres != null)
      {
         postgres.stop();
      }
   }



   /*******************************************************************************
    ** A clean schema generated by the qbit, and the qbit installed on it.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      target = Targets.minio();
      try(Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), "qqq", "qqq"); Statement st = c.createStatement())
      {
         st.execute("DROP SCHEMA public CASCADE");
         st.execute("CREATE SCHEMA public");
         ObjectStorageQBitConfig forSchema = new ObjectStorageQBitConfig().withBackendName("rdbms");
         for(String sql : ObjectStorageSchema.postgresStatements(forSchema))
         {
            st.execute(sql);
         }
         st.execute("CREATE TABLE media (id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, storage_key VARCHAR(500))");
      }
      empty(target.indexBucket);
      install(false);
   }



   /*******************************************************************************
    ** Install the qbit on the PostgreSQL backend.
    *******************************************************************************/
   private void install(boolean scoping) throws Exception
   {
      ObjectStorageQBitConfig config = StorageTestSupport.config(target, ChecksumMode.WHEN_REQUIRED).withBackendName("rdbms").withBucketScoping(scoping).withOpenWhenAnonymous(true)
         .withReferenceProvider(FieldReferenceProvider.key("indexed", "media", "storageKey"));
      StorageTestSupport.setUp(config, instance ->
      {
         PostgreSQLBackendMetaData backend = new PostgreSQLBackendMetaData().withName("rdbms").withHostName(postgres.getHost()).withPort(postgres.getFirstMappedPort())
            .withDatabaseName("storage").withUsername("qqq").withPassword("qqq");
         instance.addBackend(backend);
         instance.addTable(new QTableMetaData().withName("media").withBackendName("rdbms").withPrimaryKeyField("id")
            .withField(new QFieldMetaData("id", QFieldType.INTEGER)).withField(new QFieldMetaData("storageKey", QFieldType.STRING).withBackendName("storage_key")));
      });
      QContext.setQSession(new QSession().withUser(new QUser().withIdReference("pg@example.com")));
   }



   /*******************************************************************************
    ** Tear down.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      StorageTestSupport.tearDown();
   }



   /*******************************************************************************
    ** Empty a bucket (raw client).
    *******************************************************************************/
   private void empty(String bucket)
   {
      try(S3Client raw = target.rawClient())
      {
         raw.listObjectsV2Paginator(b -> b.bucket(bucket)).forEach(page -> page.contents().forEach(o -> raw.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(o.key()).build())));
      }
   }



   /*******************************************************************************
    ** Put with the raw client.
    *******************************************************************************/
   private void rawPut(String key, int size)
   {
      try(S3Client raw = target.rawClient())
      {
         raw.putObject(PutObjectRequest.builder().bucket(target.indexBucket).key(key).contentType("text/plain").build(), RequestBody.fromBytes(new byte[size]));
      }
   }



   @Test
   void theGeneratedSchemaAndTheQbitAgree() throws Exception
   {
      List<String> statements = ObjectStorageSchema.postgresStatements(new ObjectStorageQBitConfig().withBackendName("rdbms"));
      assertThat(statements).anyMatch(s -> s.startsWith("CREATE TABLE IF NOT EXISTS object_storage_object (")).anyMatch(s -> s.contains("UNIQUE (bucket_id, object_key)"));
      assertThat(String.join("\n", statements)).contains("object_key VARCHAR(1100)").contains("user_metadata TEXT").contains("last_modified TIMESTAMP WITHOUT TIME ZONE")
         .contains("id INTEGER GENERATED BY DEFAULT AS IDENTITY NOT NULL").doesNotContain("preview_url VARCHAR(2000) NOT NULL");

      // the config upsert wrote through the real backend
      assertThat(Db.query(ObjectStorageTables.BUCKET).stream().map(r -> r.getValueString("alias")).toList()).contains("indexed", "files", "soft");
      assertThat(Db.first(ObjectStorageTables.CONNECTION, "name", "primary").getValueString("providerType")).isEqualTo("MINIO");
   }



   @Test
   void scanFoldersRollupsAndReferencesOnPostgres() throws Exception
   {
      rawPut("a/1.txt", 10);
      rawPut("a/b/2.txt", 20);
      rawPut("top.txt", 5);
      ObjectIndex.ScanResult scan = ObjectIndex.scan("indexed");
      assertThat(scan.status()).isEqualTo("OK");
      assertThat(scan.added()).isEqualTo(3);

      Map<String, QRecord> folders = new java.util.TreeMap<>();
      for(QRecord f : Db.query(ObjectStorageTables.FOLDER, "bucketId", ConfigSync.bucketId("indexed")))
      {
         folders.put(f.getValueString("path"), f);
      }
      assertThat(folders.keySet()).containsExactly("a/", "a/b/");
      assertThat(folders.get("a/").getValueLong("totalBytes")).isEqualTo(30);
      assertThat(folders.get("a/b/").getValueInteger("parentFolderId")).isEqualTo(folders.get("a/").getValueInteger("id"));

      // outside change: removal and rollups through the real SQL
      try(S3Client raw = target.rawClient())
      {
         raw.deleteObject(DeleteObjectRequest.builder().bucket(target.indexBucket).key("top.txt").build());
      }
      rawPut("a/3.txt", 7);
      ObjectIndex.scan("indexed");
      assertThat(Db.query(ObjectStorageTables.OBJECT, "bucketId", ConfigSync.bucketId("indexed")).stream().map(r -> r.getValueString("objectKey")).sorted().toList())
         .containsExactly("a/1.txt", "a/3.txt", "a/b/2.txt");

      Db.insert("media", List.of(new QRecord().withValue("storageKey", "a/1.txt"), new QRecord().withValue("storageKey", "a/missing.txt")));
      ReferenceScan.Result refs = ReferenceScan.run("indexed");
      assertThat(refs.missing()).isEqualTo(1);
      assertThat(refs.orphaned()).isEqualTo(2);
      assertThat(Db.first(ObjectStorageTables.BUCKET, "alias", "indexed").getValueLong("orphanedBytes")).isEqualTo(27);

      // the write-through hook on the same backend
      ObjectStorage.bucket("indexed").put(com.kingsrook.qbits.objectstorage.api.PutRequest.of("w/new.txt", new byte[4]));
      assertThat(Db.first(ObjectStorageTables.OBJECT, "objectKey", "w/new.txt").getValueBoolean("headersKnown")).isTrue();
   }



   @Test
   void actionsAndRecordLocksWorkOnPostgres() throws Exception
   {
      rawPut("d/ref.txt", 1);
      rawPut("d/free.txt", 1);
      ObjectIndex.scan("indexed");
      Db.insert("media", List.of(new QRecord().withValue("storageKey", "d/ref.txt")));
      ReferenceScan.run("indexed");
      Map<String, QRecord> rows = new java.util.TreeMap<>();
      for(QRecord r : Db.query(ObjectStorageTables.OBJECT, "bucketId", ConfigSync.bucketId("indexed")))
      {
         rows.put(r.getValueString("objectKey"), r);
      }
      String ids = rows.get("d/ref.txt").getValue("id") + "," + rows.get("d/free.txt").getValue("id");
      ProcessRunner.Run run = ProcessRunner.run(DeleteRestoreProcesses.DELETE_NAME, ids, null);
      assertThat(String.join("|", run.review())).contains("1 object is skipped: it is referenced").contains("1 object will be deleted permanently");
      assertThat(String.join("|", run.result())).contains("1 object was deleted permanently");
      assertThat(ObjectStorage.bucket("indexed").head("d/free.txt")).isEmpty();
      assertThat(ObjectStorage.bucket("indexed").head("d/ref.txt")).isPresent();

      // per-bucket record locks on the real backend
      install(true);
      QContext.setQSession(new QSession().withUser(new QUser().withIdReference("scoped@example.com")).withPermissions(Set.of("object_storage.view")));
      assertThat(new QueryAction().execute(new QueryInput(ObjectStorageTables.OBJECT)).getRecords()).as("no bucket key, no rows").isEmpty();
      QContext.setQSession(new QSession().withUser(new QUser().withIdReference("scoped@example.com")).withPermissions(Set.of("object_storage.view"))
         .withSecurityKeyValue(ObjectStorageTables.KEY_TYPE_BUCKET, ConfigSync.bucketId("indexed")));
      assertThat(new QueryAction().execute(new QueryInput(ObjectStorageTables.OBJECT)).getRecords()).extracting(r -> r.getValueString("objectKey")).containsExactly("d/ref.txt");
   }



   /** Relations (child.column -> parent) that are deliberately not database foreign keys,
    ** each with its reason. Empty: every join and table possible value is enforced. */
   private static final Map<String, String> FK_EXEMPTIONS = Map.of();



   /*******************************************************************************
    ** The guardrail: every join and table possible-value field between the qbit's
    ** tables is a foreign key in the generated schema (or exempt, with a reason),
    ** with the declared ON DELETE rule, and every foreign key column leads an index.
    ** A new relation without a foreign key fails here.
    *******************************************************************************/
   @Test
   void everyRelationIsAForeignKeyWithAnIndex() throws Exception
   {
      Set<String> expected = new TreeSet<>();
      for(QJoinMetaData join : QContext.getQInstance().getJoins().values())
      {
         if(ObjectStorageTables.ALL.contains(join.getLeftTable()) && ObjectStorageTables.ALL.contains(join.getRightTable()) && join.getType() == JoinType.ONE_TO_MANY)
         {
            for(JoinOn on : join.getJoinOns())
            {
               expected.add(join.getRightTable() + "." + ObjectStorageSchemaColumns.of(on.getRightField()) + " -> " + join.getLeftTable());
            }
         }
      }
      for(String tableName : ObjectStorageTables.ALL)
      {
         for(QFieldMetaData field : QContext.getQInstance().getTable(tableName).getFields().values())
         {
            QPossibleValueSource pvs = field.getPossibleValueSourceName() == null ? null : QContext.getQInstance().getPossibleValueSource(field.getPossibleValueSourceName());
            if(pvs != null && pvs.getType() == QPossibleValueSourceType.TABLE && ObjectStorageTables.ALL.contains(pvs.getTableName()))
            {
               expected.add(tableName + "." + ObjectStorageSchemaColumns.of(field.getName()) + " -> " + pvs.getTableName());
            }
         }
      }
      assertThat(expected).as("the qbit declares relations").hasSizeGreaterThan(10);

      Map<String, String> actual  = new TreeMap<>();
      List<String>        noIndex = new ArrayList<>();
      try(Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), "qqq", "qqq"); Statement st = c.createStatement();
         ResultSet rs = st.executeQuery("""
            SELECT cl.relname AS child, a.attname AS col, pcl.relname AS parent, con.confdeltype AS rule,
               EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid = con.conrelid AND i.indkey[0] = con.conkey[1]) AS indexed
            FROM pg_constraint con
            JOIN pg_class cl ON cl.oid = con.conrelid
            JOIN pg_class pcl ON pcl.oid = con.confrelid
            JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = con.conkey[1]
            WHERE con.contype = 'f'"""))
      {
         while(rs.next())
         {
            String key = rs.getString("child") + "." + rs.getString("col") + " -> " + rs.getString("parent");
            actual.put(key, rs.getString("rule"));
            if(!rs.getBoolean("indexed"))
            {
               noIndex.add(key);
            }
         }
      }

      List<String> missing = new ArrayList<>();
      for(String relation : expected)
      {
         if(!actual.containsKey(relation) && !FK_EXEMPTIONS.containsKey(relation))
         {
            missing.add(relation);
         }
      }
      assertThat(missing).as("relations without a database foreign key (add one to ObjectStorageSchema, or an exemption with a reason)").isEmpty();
      assertThat(noIndex).as("foreign key columns that do not lead an index").isEmpty();

      Map<String, String> rules = Map.of("NO ACTION", "a", "RESTRICT", "r", "CASCADE", "c", "SET NULL", "n");
      for(ObjectStorageSchema.ForeignKey fk : ObjectStorageSchema.foreignKeys())
      {
         String key = fk.table() + "." + ObjectStorageSchemaColumns.of(fk.field()) + " -> " + fk.parent();
         assertThat(actual.get(key)).as(fk.name() + " ON DELETE").isEqualTo(rules.get(fk.onDelete()));
         assertThat(fk.name().length()).as(fk.name()).isLessThanOrEqualTo(63);
      }
   }



   /*******************************************************************************
    ** Upgrading a schema created before the foreign keys: orphans are recorded in
    ** qqq_fk_quarantine and cleared (nullable) or deleted (required), the keys are
    ** added, and running the statements again changes nothing.
    *******************************************************************************/
   @Test
   void foreignKeyUpgradeQuarantinesOrphansAndIsIdempotent() throws Exception
   {
      Integer bucketId = ConfigSync.bucketId("indexed");
      try(Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), "qqq", "qqq"); Statement st = c.createStatement())
      {
         for(ObjectStorageSchema.ForeignKey fk : ObjectStorageSchema.foreignKeys())
         {
            st.execute("ALTER TABLE " + fk.table() + " DROP CONSTRAINT " + fk.name());
         }
         st.execute("INSERT INTO object_storage_object (bucket_id, object_key, folder_id) VALUES (" + bucketId + ", 'kept.txt', 424242)");
         st.execute("INSERT INTO object_storage_protection_rule (bucket_id, prefix_glob, mode) VALUES (434343, 'x/*', 'NO_DELETE')");
         st.execute("INSERT INTO object_storage_protection_rule (bucket_id, prefix_glob, mode) VALUES (" + bucketId + ", 'y/*', 'NO_DELETE')");

         ObjectStorageQBitConfig forSchema = new ObjectStorageQBitConfig().withBackendName("rdbms");
         for(int pass = 0; pass < 2; pass++)
         {
            for(String sql : ObjectStorageSchema.postgresStatements(forSchema))
            {
               st.execute(sql);
            }
         }

         try(ResultSet rs = st.executeQuery("SELECT table_name, column_name, missing_parent_id, action, row_data FROM qqq_fk_quarantine ORDER BY id"))
         {
            List<String> rows = new ArrayList<>();
            while(rs.next())
            {
               rows.add(rs.getString(1) + "." + rs.getString(2) + "=" + rs.getString(3) + " " + rs.getString(4) + (rs.getString(5).contains("x/*") ? " with row" : ""));
            }
            assertThat(rows).as("recorded once, although the statements ran twice")
               .containsExactly("object_storage_protection_rule.bucket_id=434343 DELETED with row", "object_storage_object.folder_id=424242 NULLED");
         }
         try(ResultSet rs = st.executeQuery("SELECT folder_id FROM object_storage_object WHERE object_key = 'kept.txt'"))
         {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getObject(1)).isNull();
         }
         try(ResultSet rs = st.executeQuery("SELECT prefix_glob FROM object_storage_protection_rule ORDER BY id"))
         {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("y/*");
            assertThat(rs.next()).isFalse();
         }
         try(ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM pg_constraint WHERE contype = 'f' AND conname LIKE 'fk_object_storage_%'"))
         {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(ObjectStorageSchema.foreignKeys().size());
         }
      }
   }



   /*******************************************************************************
    ** The delete paths the qbit uses still work with the keys: removing an object
    ** row (scan removal, hard-delete write-through) takes its versions and leaves
    ** its references pointing nowhere, and a bucket row with an audit history or
    ** rules cannot be deleted.
    *******************************************************************************/
   @Test
   void deletesFollowTheForeignKeyRules() throws Exception
   {
      rawPut("f/one.txt", 3);
      ObjectIndex.scan("indexed");
      Integer bucketId = ConfigSync.bucketId("indexed");
      Integer objectId = Db.first(ObjectStorageTables.OBJECT, "objectKey", "f/one.txt").getValueInteger("id");
      try(Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), "qqq", "qqq"); Statement st = c.createStatement())
      {
         st.execute("INSERT INTO object_storage_object_version (bucket_id, object_id, object_key) VALUES (" + bucketId + ", " + objectId + ", 'f/one.txt')");
      }
      Db.insert("media", List.of(new QRecord().withValue("storageKey", "f/one.txt")));
      ReferenceScan.run("indexed");
      assertThat(Db.first(ObjectStorageTables.OBJECT_REFERENCE, "keyOrPrefix", "f/one.txt").getValueInteger("objectId")).isEqualTo(objectId);

      try(S3Client raw = target.rawClient())
      {
         raw.deleteObject(DeleteObjectRequest.builder().bucket(target.indexBucket).key("f/one.txt").build());
      }
      assertThat(ObjectIndex.scan("indexed").status()).isEqualTo("OK");
      assertThat(Db.first(ObjectStorageTables.OBJECT, "objectKey", "f/one.txt")).isNull();
      assertThat(Db.query(ObjectStorageTables.OBJECT_VERSION, "objectId", objectId)).as("versions cascade").isEmpty();
      assertThat(Db.first(ObjectStorageTables.OBJECT_REFERENCE, "keyOrPrefix", "f/one.txt").getValueInteger("objectId")).as("reference set null").isNull();

      try(Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), "qqq", "qqq"); Statement st = c.createStatement())
      {
         st.execute("INSERT INTO object_storage_operation (bucket_id, bucket_alias, action) VALUES (" + bucketId + ", 'indexed', 'PUT')");
         org.assertj.core.api.Assertions.assertThatThrownBy(() -> st.execute("DELETE FROM object_storage_bucket WHERE id = " + bucketId))
            .hasMessageContaining("fk_object_storage_operation_bucket_id");
      }
   }



   /*******************************************************************************
    ** Column names as the schema derives them.
    *******************************************************************************/
   private static final class ObjectStorageSchemaColumns
   {
      /*******************************************************************************
       ** The snake_case column of a field.
       *******************************************************************************/
      static String of(String field)
      {
         return (com.kingsrook.qqq.backend.core.instances.QInstanceEnricher.inferBackendName(field));
      }
   }
}
