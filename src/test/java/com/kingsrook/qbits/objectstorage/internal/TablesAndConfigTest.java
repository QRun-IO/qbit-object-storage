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
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaders;
import com.kingsrook.qbits.objectstorage.api.StorageCaller;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.ProviderType;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests of the config validation, the table naming rules, the config upsert,
 ** header policy, public URL resolution and protection rules (no store needed).
 *******************************************************************************/
class TablesAndConfigTest
{
   /*******************************************************************************
    ** Tear down.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      StorageTestSupport.tearDown();
   }



   /*******************************************************************************
    ** A config with a connection and buckets that never touches a real store.
    *******************************************************************************/
   private static ObjectStorageQBitConfig config()
   {
      System.setProperty("os.unit.ak", "ak");
      System.setProperty("os.unit.sk", "sk");
      return (new ObjectStorageQBitConfig().withBackendName(StorageTestSupport.MEMORY).withBucketScoping(false).withOpenWhenAnonymous(true).withAdminApp(true)
         .withAllowedSecretRefPatterns(List.of("prop.os.unit.*"))
         .withConnection(new StorageConnectionConfig("main").withProviderType(ProviderType.MINIO).withEndpoint("http://localhost:1").withRegion("us-east-1")
            .withAccessKeyRef("${prop.os.unit.ak}").withSecretKeyRef("${prop.os.unit.sk}").withPresignEndpoint("https://files.example.com"))
         .withBucket(new StorageBucketConfig("media").withConnection("main").withBucketName("media-bucket").withRole(BucketRole.PUBLIC).withPublicBaseUrl("https://cdn.example.com/media/").withReferenceTracked(true))
         .withBucket(new StorageBucketConfig("backups").withConnection("main").withBucketName("backup-bucket").withRole(BucketRole.BACKUP).withIndexed(false))
         .withBucket(new StorageBucketConfig("incoming").withConnection("main").withBucketName("incoming-bucket").withRole(BucketRole.INCOMING).withIndexed(false)));
   }



   /*******************************************************************************
    ** Validation: names, unknown connections, BACKUP is always private, trash is
    ** never public, and secret references must match the allow-list.
    *******************************************************************************/
   @Test
   void testValidation()
   {
      assertThat(errors(config())).isEmpty();

      assertThat(errors(config().withBucket(new StorageBucketConfig("media").withConnection("main").withBucketName("x")))).anyMatch(e -> e.contains("Duplicate storage bucket alias"));
      assertThat(errors(config().withBucket(new StorageBucketConfig("orphan").withConnection("nope").withBucketName("x")))).anyMatch(e -> e.contains("unknown connection"));
      assertThat(errors(config().withBucket(new StorageBucketConfig("nobucket").withConnection("main")))).anyMatch(e -> e.contains("needs a bucketName"));
      assertThat(errors(config().withConnection(new StorageConnectionConfig("main")))).anyMatch(e -> e.contains("Duplicate storage connection"));
      assertThat(errors(config().withConnection(new StorageConnectionConfig("")))).anyMatch(e -> e.contains("needs a name"));
      assertThat(errors(config().withBucket(new StorageBucketConfig("")))).anyMatch(e -> e.contains("needs an alias"));
      assertThat(errors(config().withBackendName(null))).anyMatch(e -> e.contains("backendName"));

      assertThat(errors(config().withBucket(new StorageBucketConfig("bad-backup").withConnection("main").withBucketName("b").withRole(BucketRole.BACKUP).withPublicBaseUrl("https://x"))))
         .anyMatch(e -> e.contains("BACKUP") && e.contains("public base URL"));

      assertThat(errors(config().withBucket(new StorageBucketConfig("pub-trash").withConnection("main").withBucketName("b").withRole(BucketRole.PUBLIC).withTrashEnabled(true))))
         .anyMatch(e -> e.contains("trash location must be a private bucket"));
      assertThat(errors(config().withBucket(new StorageBucketConfig("trash-to-unknown").withConnection("main").withBucketName("b").withTrashEnabled(true).withTrashBucket("ghost"))))
         .anyMatch(e -> e.contains("unknown trash bucket"));
      assertThat(errors(config().withBucket(new StorageBucketConfig("ok-trash").withConnection("main").withBucketName("b").withTrashEnabled(true).withTrashBucket("backups")))).isEmpty();
      assertThat(errors(config().withBucket(new StorageBucketConfig("trash-to-public").withConnection("main").withBucketName("b").withTrashEnabled(true).withTrashBucket("media"))))
         .anyMatch(e -> e.contains("trash location must be a private bucket"));

      assertThat(errors(config().withConnection(new StorageConnectionConfig("leaky").withSecretKeyRef("${env.DATABASE_PASSWORD}"))))
         .anyMatch(e -> e.contains("not an allowed reference"));
      assertThat(errors(config().withConnection(new StorageConnectionConfig("literal").withSecretKeyRef("plain-value")))).anyMatch(e -> e.contains("not an allowed reference"));
      assertThat(errors(config().withConnection(new StorageConnectionConfig("insecure").withEndpoint("https://store.example.com").withTlsVerify(false))))
         .anyMatch(e -> e.contains("tlsVerify=false"));
      assertThat(errors(config().withConnection(new StorageConnectionConfig("devhttp").withEndpoint("http://localhost:9000").withTlsVerify(false)))).isEmpty();
   }



   /*******************************************************************************
    ** The validation errors of a config.
    *******************************************************************************/
   private static List<String> errors(ObjectStorageQBitConfig config)
   {
      List<String> errors = new java.util.ArrayList<>();
      config.validate(new QInstance(), errors);
      return (errors);
   }



   /*******************************************************************************
    ** Owner naming rules: every table is lowercase snake_case with the full qbit
    ** name as prefix; no table has a field that could hold a secret value; the nav
    ** lists only the main tables and the app has a home widget.
    *******************************************************************************/
   @Test
   void testNamingAndNoSecretValues() throws Exception
   {
      QInstance instance = StorageTestSupport.setUp(config());
      for(String name : ObjectStorageTables.ALL)
      {
         QTableMetaData table = instance.getTable(name);
         assertThat(table).as(name).isNotNull();
         assertThat(name).matches("^object_storage_[a-z]+(_[a-z]+)*$");
         assertThat(table.getBackendName()).isEqualTo(StorageTestSupport.MEMORY);
         for(QFieldMetaData field : table.getFields().values())
         {
            String lower = field.getName().toLowerCase();
            boolean secretish = lower.contains("secret") || lower.contains("password") || lower.contains("token");
            assertThat(!secretish || lower.endsWith("ref")).as(name + "." + field.getName() + " must be a reference, not a value").isTrue();
         }
      }
      assertThat(instance.getApp("objectStorageAdmin").getChildren()).extracting(c -> c.getName()).containsExactlyInAnyOrderElementsOf(ObjectStorageTables.NAV_TABLES);
      assertThat(instance.getApp("objectStorageAdmin").getWidgets()).containsExactly("objectStorageHome");
      assertThat(instance.getProcesses().keySet()).allMatch(n -> n.startsWith("objectStorage") || n.matches("^object_storage_[a-z_]+[.]bulk[A-Za-z]+$"));
      assertThat(instance.getApp("objectStorageAdmin").getSections().stream().flatMap(s -> s.getProcesses() == null ? java.util.stream.Stream.empty() : s.getProcesses().stream()).toList())
         .as("processes are never in the nav").isEmpty();
      assertThat(instance.getTable(ObjectStorageTables.BUCKET).getExposedJoins()).extracting(j -> j.getJoinTable()).contains(ObjectStorageTables.OBJECT, ObjectStorageTables.BUCKET_CAPABILITY);
   }



   /*******************************************************************************
    ** Without the admin app a headless consumer still gets the tables and no app.
    *******************************************************************************/
   @Test
   void testHeadlessHasNoApp() throws Exception
   {
      QInstance instance = StorageTestSupport.setUp(config().withAdminApp(false));
      assertThat(instance.getApp("objectStorageAdmin")).isNull();
      assertThat(instance.getTable(ObjectStorageTables.OBJECT)).isNotNull();
   }



   /*******************************************************************************
    ** A parent app name places the Storage app under Admin.
    *******************************************************************************/
   @Test
   void testParentApp() throws Exception
   {
      QInstance instance = new QInstance();
      instance.addApp(new com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData().withName("admin"));
      ObjectStorageQBitConfig config = config().withParentAppName("admin");
      new com.kingsrook.qbits.objectstorage.ObjectStorageQBitProducer().withQBitConfig(config).produce(instance);
      assertThat(instance.getApp("objectStorageAdmin").getParentAppName()).isEqualTo("admin");
      assertThat(com.kingsrook.qbits.objectstorage.app.StorageWidgets.tablePathPrefix()).isEqualTo("/admin/objectStorageAdmin");
   }



   /*******************************************************************************
    ** Config is upserted into visible rows; identity comes from code, operational
    ** fields belong to the row after the first sync; no secret value is stored.
    *******************************************************************************/
   @Test
   void testConfigUpsert() throws Exception
   {
      StorageTestSupport.setUp(config());
      QRecord connection = Db.first(ObjectStorageTables.CONNECTION, "name", "main");
      assertThat(connection.getValueString("source")).isEqualTo("CONFIG");
      assertThat(connection.getValueString("providerType")).isEqualTo("MINIO");
      assertThat(connection.getValueString("checksumMode")).isEqualTo("WHEN_REQUIRED");
      assertThat(connection.getValueString("addressingStyle")).isEqualTo("PATH");
      assertThat(connection.getValueBoolean("credentialsResolved")).isTrue();
      assertThat(connection.getValueBoolean("presignKeyConfigured")).isFalse();
      assertThat(connection.getValueString("presignWarning")).contains("least-privilege");
      assertThat(connection.getValueString("secretKeyRef")).isEqualTo("${prop.os.unit.sk}");
      assertThat(String.valueOf(connection.getValues())).doesNotContain("\"sk\"").doesNotContain("=sk,");

      assertThat(Db.query(ObjectStorageTables.BUCKET).stream().map(r -> r.getValueString("alias")).toList()).containsExactlyInAnyOrder("media", "backups", "incoming");
      QRecord media = Db.first(ObjectStorageTables.BUCKET, "alias", "media");
      assertThat(media.getValueString("role")).isEqualTo("PUBLIC");
      assertThat(media.getValueString("managedBy")).isEqualTo("IAC");
      assertThat(media.getValueBoolean("referenceTracked")).isTrue();
      assertThat(media.getValueInteger("connectionId")).isEqualTo(connection.getValueInteger("id"));
      assertThat(ConfigSync.bucketId("media")).isEqualTo(media.getValueInteger("id"));
      assertThat(ConfigSync.aliasOf(media.getValueInteger("id"))).isEqualTo("media");
      assertThat(ConfigSync.connectionId("main")).isEqualTo(connection.getValueInteger("id"));

      // an operational edit survives the next sync; identity is rewritten from code
      Db.update(ObjectStorageTables.CONNECTION, List.of(new QRecord().withValue("id", connection.getValue("id")).withValue("maxOpsPerSecond", 7).withValue("endpoint", "http://tampered")));
      ConfigSync.sync();
      QRecord after = Db.first(ObjectStorageTables.CONNECTION, "name", "main");
      assertThat(after.getValueInteger("maxOpsPerSecond")).isEqualTo(7);
      assertThat(after.getValueString("endpoint")).isEqualTo("http://localhost:1");
      assertThat(Db.query(ObjectStorageTables.CONNECTION)).hasSize(1);
      assertThat(ConfigSync.withOverrides(new StorageConnectionConfig("main")).getMaxOpsPerSecond()).isEqualTo(7);
      assertThat(ConfigSync.bucketId("nope")).isNull();
   }



   /*******************************************************************************
    ** The default header policy: first matching enabled rule by priority; BACKUP
    ** buckets are never touched; rules can be scoped to a bucket.
    *******************************************************************************/
   @Test
   void testHeaderPolicy() throws Exception
   {
      ObjectStorageQBitConfig config = config();
      StorageTestSupport.setUp(config);
      Integer mediaId = ConfigSync.bucketId("media");
      StorageTestSupport.insert(ObjectStorageTables.HEADER_RULE,
         new QRecord().withValue("name", "hashed").withValue("keyRegex", ".*/[0-9a-f]{64}/.*").withValue("cacheControl", "public, max-age=31536000, immutable").withValue("priority", 10).withValue("enabled", true),
         new QRecord().withValue("name", "images").withValue("contentTypePattern", "image/*").withValue("cacheControl", "public, max-age=3600").withValue("priority", 50).withValue("enabled", true),
         new QRecord().withValue("name", "pdf").withValue("keyGlob", "*.pdf").withValue("cacheControl", "no-cache").withValue("contentDisposition", "inline").withValue("priority", 60).withValue("bucketId", mediaId).withValue("enabled", true),
         new QRecord().withValue("name", "off").withValue("keyGlob", "*").withValue("cacheControl", "never").withValue("priority", 1).withValue("enabled", false));
      DefaultObjectHeaderPolicy.clearCache();

      DefaultObjectHeaderPolicy policy = new DefaultObjectHeaderPolicy();
      StorageBucketConfig media = config.findBucket("media");
      String sha = "a".repeat(64);
      assertThat(policy.headersFor(media, "media/42/" + sha + "/x.jpg", "image/jpeg").getCacheControl()).isEqualTo("public, max-age=31536000, immutable");
      assertThat(policy.headersFor(media, "media/42/x.jpg", "image/jpeg").getCacheControl()).isEqualTo("public, max-age=3600");
      ObjectHeaders pdf = policy.headersFor(media, "docs/a.pdf", "application/pdf");
      assertThat(pdf.getCacheControl()).isEqualTo("no-cache");
      assertThat(pdf.getContentDisposition()).isEqualTo("inline");
      assertThat(policy.headersFor(config.findBucket("incoming"), "docs/a.pdf", "application/pdf").isEmpty()).as("the pdf rule is scoped to the media bucket").isTrue();
      assertThat(policy.headersFor(media, "other.bin", "application/octet-stream").isEmpty()).isTrue();
      assertThat(policy.headersFor(config.findBucket("backups"), "media/42/x.jpg", "image/jpeg").isEmpty()).as("BACKUP buckets are excluded from the policy").isTrue();
   }



   /*******************************************************************************
    ** Public URLs: base + encoded key, and parsed back to the bucket and key.
    *******************************************************************************/
   @Test
   void testPublicUrls() throws Exception
   {
      StorageTestSupport.setUp(config());
      DefaultPublicUrlResolver resolver = new DefaultPublicUrlResolver();
      StorageBucketConfig media = ObjectStorageRuntime.bucketConfig("media");
      String url = resolver.resolve(media, "photos/café one+two.jpg");
      assertThat(url).isEqualTo("https://cdn.example.com/media/photos/caf%C3%A9%20one%2Btwo.jpg");
      assertThat(resolver.parse(url + "?v=2")).hasValueSatisfying(bk ->
      {
         assertThat(bk.getBucketAlias()).isEqualTo("media");
         assertThat(bk.getKey()).isEqualTo("photos/café one+two.jpg");
      });
      assertThat(resolver.parse("https://elsewhere.example.com/x")).isEmpty();
      assertThat(resolver.parse(null)).isEmpty();
      assertThat(resolver.resolve(ObjectStorageRuntime.bucketConfig("backups"), "x")).isNull();
   }



   /*******************************************************************************
    ** Protection rules: READ_ONLY blocks every write, NO_DELETE blocks deletes
    ** only, WARN only notes it, and a rule's allowedSystemCallers lets that
    ** caller's system-context delete through.
    *******************************************************************************/
   @Test
   void testProtectionRules() throws Exception
   {
      StorageTestSupport.setUp(config());
      Integer mediaId = ConfigSync.bucketId("media");
      StorageTestSupport.insert(ObjectStorageTables.PROTECTION_RULE,
         new QRecord().withValue("bucketId", mediaId).withValue("prefixGlob", "legal/*").withValue("mode", "READ_ONLY").withValue("reason", "retention").withValue("enabled", true),
         new QRecord().withValue("bucketId", mediaId).withValue("prefixGlob", "keep/*").withValue("mode", "NO_DELETE").withValue("allowedSystemCallers", "backups, other").withValue("enabled", true),
         new QRecord().withValue("bucketId", mediaId).withValue("prefixGlob", "careful/*").withValue("mode", "WARN").withValue("reason", "be careful").withValue("enabled", true),
         new QRecord().withValue("bucketId", mediaId).withValue("prefixGlob", "off/*").withValue("mode", "READ_ONLY").withValue("enabled", false));

      StorageCaller user = StorageCaller.user();
      StorageCaller backups = StorageCaller.system("backups", "run-9");
      StorageCaller stranger = StorageCaller.system("stranger", null);

      ProtectionRules.Result legal = ProtectionRules.check("media", "legal/contract.pdf", ProtectionRules.Op.WRITE, user);
      assertThat(legal.blocked()).isTrue();
      assertThat(legal.reason()).contains("retention");
      assertThat(ProtectionRules.check("media", "legal/contract.pdf", ProtectionRules.Op.DELETE, backups).blocked()).as("READ_ONLY has no system exception").isTrue();

      assertThat(ProtectionRules.check("media", "keep/a", ProtectionRules.Op.WRITE, user).blocked()).as("NO_DELETE allows writes").isFalse();
      assertThat(ProtectionRules.check("media", "keep/a", ProtectionRules.Op.DELETE, user).blocked()).isTrue();
      assertThat(ProtectionRules.check("media", "keep/a", ProtectionRules.Op.DELETE, stranger).blocked()).isTrue();
      assertThat(ProtectionRules.check("media", "keep/a", ProtectionRules.Op.DELETE, backups).blocked()).as("allowedSystemCallers names backups").isFalse();

      ProtectionRules.Result warn = ProtectionRules.check("media", "careful/a", ProtectionRules.Op.DELETE, user);
      assertThat(warn.blocked()).isFalse();
      assertThat(warn.warned()).isTrue();
      assertThat(ProtectionRules.check("media", "off/a", ProtectionRules.Op.WRITE, user).blocked()).isFalse();
      assertThat(ProtectionRules.check("incoming", "legal/x", ProtectionRules.Op.WRITE, user).blocked()).as("rules are per bucket").isFalse();
   }



   /*******************************************************************************
    ** Per-bucket scoping: a user without the bucket's key sees none of its rows.
    *******************************************************************************/
   @Test
   void testBucketScoping() throws Exception
   {
      StorageTestSupport.setUp(config().withBucketScoping(true));
      Integer mediaId = ConfigSync.bucketId("media");
      Integer incomingId = ConfigSync.bucketId("incoming");
      Db.asSystem(() -> Db.insert(ObjectStorageTables.OPERATION, List.of(new QRecord().withValue("bucketId", mediaId).withValue("action", "PUT").withValue("bucketAlias", "media"),
         new QRecord().withValue("bucketId", incomingId).withValue("action", "PUT").withValue("bucketAlias", "incoming"))));

      QSession none = new QSession();
      QContext.setQSession(none);
      assertThat(new QueryAction().execute(new QueryInput(ObjectStorageTables.OPERATION)).getRecords()).as("no key, no rows").isEmpty();
      assertThat(new QueryAction().execute(new QueryInput(ObjectStorageTables.BUCKET)).getRecords()).isEmpty();

      QSession mediaOnly = new QSession().withSecurityKeyValue(ObjectStorageTables.KEY_TYPE_BUCKET, mediaId);
      QContext.setQSession(mediaOnly);
      assertThat(new QueryAction().execute(new QueryInput(ObjectStorageTables.OPERATION)).getRecords()).extracting(r -> r.getValueString("bucketAlias")).containsExactly("media");
      assertThat(new QueryAction().execute(new QueryInput(ObjectStorageTables.BUCKET)).getRecords()).extracting(r -> r.getValueString("alias")).containsExactly("media");

      QSession all = new QSession().withSecurityKeyValue(ObjectStorageTables.ALL_ACCESS_BUCKET, true).withSecurityKeyValue(ObjectStorageTables.ALL_ACCESS_CONN, true);
      QContext.setQSession(all);
      assertThat(new QueryAction().execute(new QueryInput(ObjectStorageTables.OPERATION)).getRecords()).hasSize(2);

      // the audit log still writes for a scoped user (the service writes as the system user)
      QContext.setQSession(mediaOnly);
      new OperationLog.Entry(StorageCaller.user(), "PUT", "incoming").key("k").write();
      QContext.setQSession(all);
      assertThat(new QueryAction().execute(new QueryInput(ObjectStorageTables.OPERATION)).getRecords()).hasSize(3);
   }
}
