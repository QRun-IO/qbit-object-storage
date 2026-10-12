package com.kingsrook.qbits.objectstorage.processes;


import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.kingsrook.qbits.objectstorage.api.DeleteRequest;
import com.kingsrook.qbits.objectstorage.api.FieldReferenceProvider;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaders;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.api.PutRequest;
import com.kingsrook.qbits.objectstorage.conformance.ConformanceTarget;
import com.kingsrook.qbits.objectstorage.conformance.Targets;
import com.kingsrook.qbits.objectstorage.config.ChecksumMode;
import com.kingsrook.qbits.objectstorage.index.ObjectIndex;
import com.kingsrook.qbits.objectstorage.index.ReferenceScan;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.DefaultObjectHeaderPolicy;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.testsupport.ProcessRunner;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The record and table actions, run the way the UI runs them (Review, then
 ** Result) against MinIO: what the Review promises is what the Result does, and
 ** the Review changes nothing.
 *******************************************************************************/
class MinioProcessTest
{
   private ConformanceTarget target;



   /*******************************************************************************
    ** Install the qbit with an empty, scanned index bucket (no anonymous admin).
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      Assumptions.assumeTrue(Targets.dockerAvailable(), "Docker is not available");
      target = Targets.minio();
      for(String bucket : new String[] { target.indexBucket, target.versionedBucket, target.trashBucket, target.bucket })
      {
         empty(bucket);
      }
      reinstall(c ->
      {
      });
   }



   /*******************************************************************************
    ** Tear down.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      UploadProcess.setOpener(null);
      StorageTestSupport.tearDown();
   }



   /*******************************************************************************
    ** (Re)install the qbit, with a media table, as a user holding all permissions.
    *******************************************************************************/
   private void reinstall(java.util.function.Consumer<com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig> customize) throws Exception
   {
      com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig config = StorageTestSupport.config(target, ChecksumMode.WHEN_REQUIRED)
         .withOpenWhenAnonymous(false).withReferenceProvider(FieldReferenceProvider.key("indexed", "media", "storageKey"));
      customize.accept(config);
      StorageTestSupport.setUp(config, instance -> instance.addTable(new QTableMetaData().withName("media").withBackendName(StorageTestSupport.MEMORY).withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER)).withField(new QFieldMetaData("storageKey", QFieldType.STRING))));
      as("object_storage.view", "object_storage.write", "object_storage.delete", "object_storage.admin");
   }



   /*******************************************************************************
    ** Act as a user with exactly these permissions.
    *******************************************************************************/
   private void as(String... permissions)
   {
      QContext.setQSession(new QSession().withUser(new QUser().withIdReference("operator@example.com")).withPermissions(Set.of(permissions)));
   }



   /*******************************************************************************
    ** Empty a bucket with the raw client.
    *******************************************************************************/
   private void empty(String bucket)
   {
      try(S3Client raw = target.rawClient())
      {
         raw.listObjectVersionsPaginator(b -> b.bucket(bucket)).forEach(page ->
         {
            page.versions().forEach(v -> raw.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(v.key()).versionId(v.versionId()).build()));
            page.deleteMarkers().forEach(m -> raw.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(m.key()).versionId(m.versionId()).build()));
         });
         raw.listObjectsV2Paginator(b -> b.bucket(bucket)).forEach(page -> page.contents().forEach(o -> raw.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(o.key()).build())));
      }
   }



   /*******************************************************************************
    ** Put with the raw client (outside QQQ) and scan, returning the rows by key.
    *******************************************************************************/
   private Map<String, QRecord> seed(String alias, String bucket, Map<String, Integer> keysAndSizes) throws Exception
   {
      try(S3Client raw = target.rawClient())
      {
         keysAndSizes.forEach((k, size) -> raw.putObject(PutObjectRequest.builder().bucket(bucket).key(k).contentType("text/plain").build(), RequestBody.fromBytes(new byte[size])));
      }
      ObjectIndex.scan(alias);
      ObjectIndex.headBacklog(alias, 1000);
      return (rows(alias));
   }



   /*******************************************************************************
    ** Object rows by key.
    *******************************************************************************/
   private Map<String, QRecord> rows(String alias) throws Exception
   {
      Map<String, QRecord> map = new java.util.TreeMap<>();
      for(QRecord r : Db.query(ObjectStorageTables.OBJECT, "bucketId", ConfigSync.bucketId(alias)))
      {
         map.put(r.getValueString("objectKey"), r);
      }
      return (map);
   }



   /*******************************************************************************
    ** Comma-separated ids of the rows of some keys.
    *******************************************************************************/
   private static String ids(Map<String, QRecord> rows, String... keys)
   {
      return (java.util.Arrays.stream(keys).map(k -> String.valueOf(rows.get(k).getValue("id"))).collect(Collectors.joining(",")));
   }



   /*******************************************************************************
    ** One line of a screen containing some text (fails the test with all lines).
    *******************************************************************************/
   private static void assertHas(List<String> lines, String text)
   {
      assertThat(lines).as("lines: " + lines).anyMatch(l -> l.contains(text));
   }



   @Test
   void everyActionIsATableOrRecordActionWithAPlainLabelAndAPermission() throws Exception
   {
      Set<String> labels = new java.util.HashSet<>();
      for(QProcessMetaData process : QContext.getQInstance().getProcesses().values())
      {
         if(!process.getName().startsWith("objectStorage"))
         {
            continue;
         }
         if(!Boolean.TRUE.equals(process.getIsHidden()))
         {
            assertThat(process.getTableName()).as(process.getName()).isNotNull();
            assertThat(process.getLabel()).as(process.getName()).doesNotContain("ETL").doesNotContain("Process").doesNotContain("Run ");
            labels.add(process.getLabel());
         }
         assertThat(com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime.processPermissions()).as(process.getName()).containsKey(process.getName());
      }
      assertThat(labels).contains("Test connection", "Rescan bucket", "Check configuration", "Refresh from bucket", "Apply header policy", "Change headers and metadata", "Copy objects",
         "Move or rename objects", "Move or rename folder", "Delete objects", "Delete folder contents", "Restore objects", "Edit tags", "Create download link", "Create upload link", "Upload files");
      assertThat(QContext.getQInstance().getApp("objectStorageAdmin").getSections().stream().flatMap(s -> s.getProcesses() == null ? java.util.stream.Stream.<String>empty() : s.getProcesses().stream()).toList()).isEmpty();
   }



   @Test
   void refreshFromBucketReadsObjectsAndMarksVanishedOnesDeleted() throws Exception
   {
      Map<String, QRecord> rows = seed("indexed", target.indexBucket, Map.of("r/a.txt", 3, "r/b.txt", 4));
      try(S3Client raw = target.rawClient())
      {
         raw.putObject(PutObjectRequest.builder().bucket(target.indexBucket).key("r/a.txt").cacheControl("max-age=5").build(), RequestBody.fromBytes(new byte[9]));
         raw.deleteObject(DeleteObjectRequest.builder().bucket(target.indexBucket).key("r/b.txt").build());
      }
      List<String> review = ProcessRunner.review(ObjectWriteProcesses.REFRESH_NAME, ids(rows, "r/a.txt", "r/b.txt"));
      assertHas(review, "2 objects will be read from the bucket");
      assertThat(rows("indexed").get("r/a.txt").getValueLong("size")).as("review changes nothing").isEqualTo(3);

      ProcessRunner.Run run = ProcessRunner.run(ObjectWriteProcesses.REFRESH_NAME, ids(rows, "r/a.txt", "r/b.txt"), null);
      assertHas(run.result(), "1 object was refreshed from the bucket");
      assertHas(run.result(), "1 object no longer exists and was marked deleted");
      assertThat(rows("indexed").get("r/a.txt").getValueLong("size")).isEqualTo(9);
      assertThat(rows("indexed").get("r/a.txt").getValueString("cacheControl")).isEqualTo("max-age=5");
      assertThat(rows("indexed").get("r/b.txt").getValueBoolean("deleted")).isTrue();
   }



   @Test
   void applyHeaderPolicyReviewsThenRewritesOnlyWhatIsOff() throws Exception
   {
      StorageTestSupport.insert(ObjectStorageTables.HEADER_RULE,
         new QRecord().withValue("name", "text").withValue("contentTypePattern", "text/*").withValue("cacheControl", "public, max-age=300").withValue("priority", 1).withValue("enabled", true));
      DefaultObjectHeaderPolicy.clearCache();
      Map<String, QRecord> rows = seed("indexed", target.indexBucket, Map.of("p/a.txt", 1, "p/b.txt", 1, "p/c.txt", 1));
      ObjectStorage.bucket("indexed").updateHeaders("p/c.txt", ObjectHeaders.cacheControl("public, max-age=300"));
      rows = rows("indexed");

      String all = ids(rows, "p/a.txt", "p/b.txt", "p/c.txt");
      List<String> review = ProcessRunner.review(ObjectWriteProcesses.POLICY_NAME, all);
      assertHas(review, "2 objects will change to match the policy");
      assertHas(review, "1 object already matches the policy");
      assertHas(review, "changes Last-Modified");

      ProcessRunner.Run run = ProcessRunner.run(ObjectWriteProcesses.POLICY_NAME, all, null);
      assertHas(run.result(), "2 objects were changed to match the policy");
      for(String key : List.of("p/a.txt", "p/b.txt", "p/c.txt"))
      {
         assertThat(ObjectStorage.bucket("indexed").head(key).orElseThrow().getHeaders().getCacheControl()).isEqualTo("public, max-age=300");
      }
      assertThat(ProcessRunner.review(ObjectWriteProcesses.POLICY_NAME, all)).as("run it again: nothing to do").anyMatch(l -> l.contains("3 objects already match the policy"));
   }



   @Test
   void changeHeadersShowsBeforeAndAfterAndMergesMetadata() throws Exception
   {
      Map<String, QRecord> rows = seed("indexed", target.indexBucket, Map.of("h/a.txt", 1, "h/b.txt", 1));
      ObjectStorage.bucket("indexed").updateHeaders("h/a.txt", new ObjectHeaders().withUserMetadata(Map.of("keep", "1", "drop", "2")).withCacheControl("old"));
      rows = rows("indexed");
      String both = ids(rows, "h/a.txt", "h/b.txt");

      Map<String, Serializable> none = new HashMap<>();
      assertHas(ProcessRunner.review(ObjectWriteProcesses.HEADERS_NAME, both), "Nothing to change");

      Map<String, Serializable> inputs = new HashMap<>();
      inputs.put(ObjectWriteProcesses.FIELD_CACHE_CONTROL, "public, max-age=10");
      inputs.put(ObjectWriteProcesses.FIELD_USER_METADATA, "added=yes\ndrop=");
      ProcessRunner.Run run = ProcessRunnerWithInputs.run(ObjectWriteProcesses.HEADERS_NAME, both, inputs);
      assertHas(run.review(), "2 objects will change");
      assertHas(run.review(), "h/a.txt: Cache-Control old -> public, max-age=10");
      assertHas(run.result(), "2 objects were changed");
      assertThat(ObjectStorage.bucket("indexed").head("h/a.txt").orElseThrow().getHeaders().getUserMetadata()).containsEntry("keep", "1").containsEntry("added", "yes").doesNotContainKey("drop");
      assertThat(ObjectStorage.bucket("indexed").head("h/b.txt").orElseThrow().getHeaders().getCacheControl()).isEqualTo("public, max-age=10");
      assertThat(none).isEmpty();

      Map<String, Serializable> clear = new HashMap<>();
      clear.put(ObjectWriteProcesses.FIELD_CACHE_CONTROL, "-");
      ProcessRunnerWithInputs.run(ObjectWriteProcesses.HEADERS_NAME, ids(rows("indexed"), "h/b.txt"), clear);
      assertThat(ObjectStorage.bucket("indexed").head("h/b.txt").orElseThrow().getHeaders().getCacheControl()).isNull();

      Map<String, Serializable> bad = new HashMap<>();
      bad.put(ObjectWriteProcesses.FIELD_USER_METADATA, "no equals sign");
      assertHas(ProcessRunnerWithInputs.run(ObjectWriteProcesses.HEADERS_NAME, both, bad).review(), "The metadata is not valid");
   }



   @Test
   void copyHonoursOverwriteAndDestinationChoices() throws Exception
   {
      Map<String, QRecord> rows = seed("indexed", target.indexBucket, Map.of("c/one.txt", 1, "c/two.txt", 2, "dst/one.txt", 9));
      String both = ids(rows, "c/one.txt", "c/two.txt");

      Map<String, Serializable> inputs = new HashMap<>();
      inputs.put(CopyMoveProcesses.FIELD_DEST_PREFIX, "dst");
      ProcessRunner.Run first = ProcessRunnerWithInputs.run(CopyMoveProcesses.COPY_NAME, both, inputs);
      assertHas(first.review(), "1 object will be copied");
      assertHas(first.review(), "1 object is skipped: the destination already exists");
      assertHas(first.result(), "1 object was copied");
      assertThat(ObjectStorage.bucket("indexed").head("dst/two.txt")).isPresent();
      assertThat(ObjectStorage.bucket("indexed").head("dst/one.txt").orElseThrow().getSize()).as("not overwritten").isEqualTo(9);
      assertThat(ObjectStorage.bucket("indexed").head("c/one.txt")).as("the original stays").isPresent();

      inputs.put(CopyMoveProcesses.FIELD_OVERWRITE, true);
      ProcessRunner.Run second = ProcessRunnerWithInputs.run(CopyMoveProcesses.COPY_NAME, both, inputs);
      assertHas(second.review(), "2 objects will replace ones that already exist");
      assertThat(ObjectStorage.bucket("indexed").head("dst/one.txt").orElseThrow().getSize()).isEqualTo(1);

      Map<String, Serializable> keep = new HashMap<>();
      keep.put(CopyMoveProcesses.FIELD_DEST_PREFIX, "mirror");
      keep.put(CopyMoveProcesses.FIELD_KEEP_PATH, true);
      keep.put(CopyMoveProcesses.FIELD_DEST_BUCKET, ConfigSync.bucketId("files"));
      ProcessRunnerWithInputs.run(CopyMoveProcesses.COPY_NAME, both, keep);
      assertThat(ObjectStorage.bucket("files").head("mirror/c/one.txt")).as("another bucket, whole path kept").isPresent();
      assertThat(Db.query(ObjectStorageTables.OPERATION, "action", "COPY")).hasSizeGreaterThanOrEqualTo(4);
   }



   @Test
   void moveRenamesOneObjectAndSkipsReferencedAndProtectedOnes() throws Exception
   {
      Map<String, QRecord> rows = seed("indexed", target.indexBucket, Map.of("m/solo.txt", 1, "m/ref.txt", 1, "m/held.txt", 1));
      StorageTestSupport.insert("media", new QRecord().withValue("storageKey", "m/ref.txt"));
      ReferenceScan.run("indexed");
      StorageTestSupport.insert(ObjectStorageTables.PROTECTION_RULE, new QRecord().withValue("bucketId", ConfigSync.bucketId("indexed")).withValue("prefixGlob", "m/held*")
         .withValue("mode", "NO_DELETE").withValue("enabled", true));
      rows = rows("indexed");

      Map<String, Serializable> rename = new HashMap<>();
      rename.put(CopyMoveProcesses.FIELD_NEW_NAME, "renamed.txt");
      ProcessRunner.Run one = ProcessRunnerWithInputs.run(CopyMoveProcesses.MOVE_NAME, ids(rows, "m/solo.txt"), rename);
      assertHas(one.review(), "1 object will be moved");
      assertHas(one.review(), "not one atomic step");
      assertHas(one.result(), "1 object was moved");
      assertThat(ObjectStorage.bucket("indexed").head("m/renamed.txt")).isPresent();
      assertThat(ObjectStorage.bucket("indexed").head("m/solo.txt")).isEmpty();

      Map<String, Serializable> move = new HashMap<>();
      move.put(CopyMoveProcesses.FIELD_DEST_PREFIX, "moved");
      ProcessRunner.Run two = ProcessRunnerWithInputs.run(CopyMoveProcesses.MOVE_NAME, ids(rows, "m/ref.txt", "m/held.txt"), move);
      assertHas(two.review(), "1 object is skipped: it is referenced");
      assertHas(two.review(), "1 object is skipped: it (or its destination) is protected");
      assertThat(ObjectStorage.bucket("indexed").head("m/ref.txt")).isPresent();
      assertThat(ObjectStorage.bucket("indexed").head("m/held.txt")).isPresent();
      assertThat(ObjectStorage.bucket("indexed").head("moved/ref.txt")).isEmpty();
   }



   @Test
   void deleteIsSoftWhereItCanBeAndPermanentOnlyForAdmins() throws Exception
   {
      Map<String, QRecord> vrows = seed("versioned", target.versionedBucket, Map.of("d/soft.txt", 1));
      ProcessRunner.Run soft = ProcessRunner.run(DeleteRestoreProcesses.DELETE_NAME, ids(vrows, "d/soft.txt"), null);
      assertHas(soft.review(), "1 object will be deleted (a delete marker is added; Restore undoes it)");
      assertHas(soft.result(), "1 object was deleted (a delete marker was added");
      assertThat(ObjectStorage.bucket("versioned").head("d/soft.txt")).isEmpty();
      assertThat(rows("versioned").get("d/soft.txt").getValueBoolean("deleted")).isTrue();

      ProcessRunner.Run restore = ProcessRunner.run(DeleteRestoreProcesses.RESTORE_NAME, ids(rows("versioned"), "d/soft.txt"), null);
      assertHas(restore.review(), "1 delete marker will be removed");
      assertHas(restore.result(), "1 object was restored");
      assertThat(ObjectStorage.bucket("versioned").head("d/soft.txt")).isPresent();
      assertHas(ProcessRunner.review(DeleteRestoreProcesses.RESTORE_NAME, ids(rows("versioned"), "d/soft.txt")), "1 object is not deleted (no change)");

      Map<String, QRecord> rows = seed("indexed", target.indexBucket, Map.of("d/perm.txt", 1));
      as("object_storage.view", "object_storage.write", "object_storage.delete");
      ProcessRunner.Run denied = ProcessRunner.run(DeleteRestoreProcesses.DELETE_NAME, ids(rows, "d/perm.txt"), null);
      assertHas(denied.review(), "1 object is skipped: this bucket has no versioning or trash");
      assertThat(ObjectStorage.bucket("indexed").head("d/perm.txt")).isPresent();

      as("object_storage.view", "object_storage.write", "object_storage.delete", "object_storage.admin");
      ProcessRunner.Run admin = ProcessRunner.run(DeleteRestoreProcesses.DELETE_NAME, ids(rows, "d/perm.txt"), null);
      assertHas(admin.review(), "1 object will be deleted permanently");
      assertHas(admin.result(), "1 object was deleted permanently");
      assertThat(ObjectStorage.bucket("indexed").head("d/perm.txt")).isEmpty();
   }



   @Test
   void deleteSkipsReferencedProtectedAndLockedObjectsAndSaysWhy() throws Exception
   {
      Map<String, QRecord> rows = seed("indexed", target.indexBucket, Map.of("x/ref.txt", 1, "x/prot.txt", 1, "x/free.txt", 1));
      StorageTestSupport.insert("media", new QRecord().withValue("storageKey", "x/ref.txt"));
      ReferenceScan.run("indexed");
      StorageTestSupport.insert(ObjectStorageTables.PROTECTION_RULE, new QRecord().withValue("bucketId", ConfigSync.bucketId("indexed")).withValue("prefixGlob", "x/prot*")
         .withValue("mode", "NO_DELETE").withValue("reason", "legal hold").withValue("enabled", true));
      rows = rows("indexed");

      ProcessRunner.Run run = ProcessRunner.run(DeleteRestoreProcesses.DELETE_NAME, ids(rows, "x/ref.txt", "x/prot.txt", "x/free.txt"), null);
      assertHas(run.review(), "1 object is skipped: it is referenced");
      assertHas(run.review(), "1 object is skipped: it is protected");
      assertHas(run.review(), "1 object will be deleted permanently");
      assertHas(run.result(), "1 object was deleted permanently");
      assertThat(ObjectStorage.bucket("indexed").head("x/ref.txt")).isPresent();
      assertThat(ObjectStorage.bucket("indexed").head("x/prot.txt")).isPresent();
      assertThat(ObjectStorage.bucket("indexed").head("x/free.txt")).isEmpty();
   }



   @Test
   void editTagsSetsTagsWhereTheProviderSupportsThem() throws Exception
   {
      Map<String, QRecord> rows = seed("indexed", target.indexBucket, Map.of("t/a.txt", 1));
      Map<String, Serializable> inputs = new HashMap<>();
      inputs.put(ObjectWriteProcesses.FIELD_TAGS, "team=media\nstatus=draft");
      ProcessRunner.Run run = ProcessRunnerWithInputs.run(ObjectWriteProcesses.TAGS_NAME, ids(rows, "t/a.txt"), inputs);
      assertHas(run.review(), "1 object will get these tags");
      assertHas(run.result(), "1 object got these tags");
      assertThat(ObjectStorage.bucket("indexed").getTags("t/a.txt")).containsEntry("team", "media").containsEntry("status", "draft");
   }



   @Test
   void downloadLinksAreShownOnceAndLoggedWithoutTheirSignature() throws Exception
   {
      Map<String, QRecord> rows = seed("indexed", target.indexBucket, Map.of("l/file.txt", 5));
      ProcessRunner.Run run = ProcessRunner.run(LinkProcesses.DOWNLOAD_NAME, ids(rows, "l/file.txt"), null);
      assertHas(run.review(), "1 download link will be created");
      Object lines = run.resultOutput().getValues().get("processResults");
      String url = ((List<?>) lines).stream().map(l -> ((com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine) l).getBulletsOfText())
         .filter(b -> b != null && !b.isEmpty()).map(b -> b.get(0)).findFirst().orElseThrow();
      HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).hasSize(5);
      String signature = url.replaceAll(".*X-Amz-Signature=([^&]+).*", "$1");
      for(QRecord row : Db.query(ObjectStorageTables.OPERATION, "action", "PRESIGN_GET"))
      {
         assertThat(String.valueOf(row.getValues())).doesNotContain(signature);
      }
   }



   @Test
   void uploadLinkPinsTheTypeAndRejectsBadKeys() throws Exception
   {
      String bucketId = String.valueOf(ConfigSync.bucketId("indexed"));
      Map<String, Serializable> inputs = new HashMap<>();
      inputs.put(BucketProcesses.FIELD_LINK_KEY, "../escape");
      assertHas(ProcessRunnerWithInputs.review(BucketProcesses.UPLOAD_LINK, bucketId, inputs), "The link cannot be created");

      inputs.put(BucketProcesses.FIELD_LINK_KEY, "in/pinned.txt");
      inputs.put(BucketProcesses.FIELD_LINK_TYPE, "text/plain");
      ProcessRunner.Run run = ProcessRunnerWithInputs.run(BucketProcesses.UPLOAD_LINK, bucketId, inputs);
      assertHas(run.review(), "1 upload link will be created");
      String url = ((List<?>) run.resultOutput().getValues().get("processResults")).stream().map(l -> ((com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine) l).getBulletsOfText())
         .filter(b -> b != null && !b.isEmpty()).map(b -> b.get(0)).findFirst().orElseThrow();
      HttpResponse<String> put = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "text/plain").PUT(HttpRequest.BodyPublishers.ofString("hello")).build(), HttpResponse.BodyHandlers.ofString());
      assertThat(put.statusCode()).isBetween(200, 299);
      assertThat(ObjectStorage.bucket("indexed").head("in/pinned.txt")).isPresent();
   }



   @Test
   void rescanBucketRunsTheScanAndReportsIt() throws Exception
   {
      try(S3Client raw = target.rawClient())
      {
         raw.putObject(PutObjectRequest.builder().bucket(target.indexBucket).key("s/new.txt").build(), RequestBody.fromBytes(new byte[2]));
      }
      String ids = String.valueOf(ConfigSync.bucketId("indexed")) + "," + ConfigSync.bucketId("files");
      ProcessRunner.Run run = ProcessRunner.run(BucketProcesses.RESCAN_NAME, ids, null);
      assertHas(run.review(), "1 bucket will be listed and re-indexed");
      assertHas(run.review(), "1 bucket is not indexed (skipped)");
      assertHas(run.result(), "indexed: scan OK - 1 objects seen, 1 new");
      assertThat(rows("indexed")).containsKey("s/new.txt");
   }



   @Test
   void checkConfigurationObservesAndFlagsDriftAgainstTheConfig() throws Exception
   {
      reinstall(c -> c.findBucket("versioned").withExpectVersioning(false).withExpectLifecycle(true));
      String id = String.valueOf(ConfigSync.bucketId("versioned"));
      ProcessRunner.Run run = ProcessRunner.run(BucketProcesses.CHECK_NAME, id, null);
      assertHas(run.review(), "changes nothing");
      assertHas(run.result(), "versioned: versioning Enabled");
      assertHas(run.result(), "drift - expected versioning off, observed Enabled");
      assertHas(run.result(), "drift - expected a lifecycle configuration");
      QRecord row = Db.first(ObjectStorageTables.BUCKET, "alias", "versioned");
      assertThat(row.getValueString("versioningState")).isEqualTo("Enabled");
      assertThat(row.getValueString("configDriftNote")).contains("expected versioning off");
      assertThat(row.getValueString("lifecycleSummary")).isEqualTo("none");

      QRecord plain = Db.first(ObjectStorageTables.BUCKET, "alias", "indexed");
      ProcessRunner.run(BucketProcesses.CHECK_NAME, String.valueOf(plain.getValue("id")), null);
      assertThat(Db.first(ObjectStorageTables.BUCKET, "alias", "indexed").getValueString("configDriftNote")).isNull();
   }



   @Test
   void applyHeaderPolicyToABucketUsesTheIndexAndCountsUnknownHeaders() throws Exception
   {
      StorageTestSupport.insert(ObjectStorageTables.HEADER_RULE,
         new QRecord().withValue("name", "all").withValue("keyGlob", "*").withValue("cacheControl", "public, max-age=1").withValue("priority", 1).withValue("enabled", true));
      DefaultObjectHeaderPolicy.clearCache();
      seed("indexed", target.indexBucket, Map.of("b/a.txt", 1, "b/b.txt", 1));
      try(S3Client raw = target.rawClient())
      {
         raw.putObject(PutObjectRequest.builder().bucket(target.indexBucket).key("b/unseen.txt").build(), RequestBody.fromBytes(new byte[1]));
      }
      ObjectIndex.scan("indexed");
      String id = String.valueOf(ConfigSync.bucketId("indexed"));
      ProcessRunner.Run run = ProcessRunner.run(BucketProcesses.POLICY_NAME, id, null);
      assertHas(run.review(), "2 objects in these buckets will change to match the policy");
      assertHas(run.review(), "1 object has unknown headers");
      assertHas(run.result(), "2 objects were changed to match the policy");
      assertThat(ObjectStorage.bucket("indexed").head("b/a.txt").orElseThrow().getHeaders().getCacheControl()).isEqualTo("public, max-age=1");
      assertThat(ObjectStorage.bucket("indexed").head("b/unseen.txt").orElseThrow().getHeaders().getCacheControl()).as("unknown headers wait for the HEAD backlog").isNull();
   }



   @Test
   void renameAndDeleteAFolderActOnTheObjectsUnderIt() throws Exception
   {
      Map<String, QRecord> rows = seed("indexed", target.indexBucket, Map.of("f/old/1.txt", 1, "f/old/deep/2.txt", 2, "f/other/3.txt", 3));
      QRecord old = Db.first(ObjectStorageTables.FOLDER, "path", "f/old/");
      Map<String, Serializable> inputs = new HashMap<>();
      inputs.put(FolderProcesses.FIELD_NEW_PATH, "f/new/");
      ProcessRunner.Run run = ProcessRunnerWithInputs.run(FolderProcesses.RENAME_NAME, String.valueOf(old.getValue("id")), inputs);
      assertHas(run.review(), "2 objects will be moved");
      assertHas(run.result(), "2 objects were moved");
      assertThat(ObjectStorage.bucket("indexed").head("f/new/1.txt")).isPresent();
      assertThat(ObjectStorage.bucket("indexed").head("f/new/deep/2.txt")).isPresent();
      assertThat(ObjectStorage.bucket("indexed").head("f/old/1.txt")).isEmpty();
      assertThat(ObjectStorage.bucket("indexed").head("f/other/3.txt")).isPresent();

      QRecord other = Db.first(ObjectStorageTables.FOLDER, "path", "f/other/");
      ProcessRunner.Run delete = ProcessRunner.run(FolderProcesses.DELETE_NAME, String.valueOf(other.getValue("id")), null);
      assertHas(delete.review(), "1 object will be deleted permanently");
      assertHas(delete.result(), "1 object was deleted permanently");
      assertThat(ObjectStorage.bucket("indexed").head("f/other/3.txt")).isEmpty();

      QRecord f = Db.first(ObjectStorageTables.FOLDER, "path", "f/");
      assertThat(rows).isNotEmpty();
      Map<String, Serializable> two = new HashMap<>();
      two.put(FolderProcesses.FIELD_NEW_PATH, "z/");
      assertHas(ProcessRunnerWithInputs.review(FolderProcesses.RENAME_NAME, f.getValue("id") + "," + old.getValue("id"), two), "Rename works on one folder at a time");
   }



   @Test
   void uploadStreamsFilesIntoAFolderAndNeverOverwritesByDefault() throws Exception
   {
      Map<String, byte[]> files = new HashMap<>();
      files.put("2026/up/hello.txt", "hello".getBytes(StandardCharsets.UTF_8));
      files.put("2026/up/exists.txt", "new".getBytes(StandardCharsets.UTF_8));
      files.put("2026/up/big.bin", new byte[3000]);
      UploadProcess.setOpener(ref -> new ByteArrayInputStream(files.get(String.valueOf(ref))));
      seed("indexed", target.indexBucket, Map.of("incoming/exists.txt", 1));
      ObjectStorage.bucket("indexed").put(PutRequest.of("incoming/exists.txt", "old".getBytes(StandardCharsets.UTF_8)));
      com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime.getConfig().findBucket("indexed").setMaxUploadBytes(2000L);

      Map<String, Serializable> inputs = new HashMap<>();
      inputs.put(UploadProcess.FIELD_FILE, new ArrayList<>(List.of("2026/up/hello.txt", "2026/up/exists.txt", "2026/up/big.bin")));
      inputs.put(UploadProcess.FIELD_BUCKET, ConfigSync.bucketId("indexed"));
      inputs.put(UploadProcess.FIELD_PREFIX, "incoming");
      ProcessRunner.Run run = ProcessRunnerWithInputs.run(UploadProcess.UPLOAD_NAME, "", inputs);
      assertHas(run.review(), "2 files will be uploaded");
      assertHas(run.review(), "1 file is skipped: an object with that name exists");
      assertHas(run.review(), "Each file may be up to");
      assertHas(run.result(), "1 file was uploaded");
      assertHas(run.result(), "record failed");
      assertThat(ObjectStorage.bucket("indexed").head("incoming/hello.txt")).isPresent();
      assertThat(ObjectStorage.bucket("indexed").head("incoming/big.bin")).as("over the upload limit").isEmpty();
      assertThat(ObjectStorage.bucket("indexed").head("incoming/exists.txt").orElseThrow().getSize()).isEqualTo(3);
      assertThat(rows("indexed")).containsKey("incoming/hello.txt");
   }



   @Test
   void bulkRunsOverTheCapNeedAdmin() throws Exception
   {
      reinstall(c -> c.setBulkCap(2));
      Map<String, QRecord> rows = seed("indexed", target.indexBucket, Map.of("cap/1", 1, "cap/2", 1, "cap/3", 1));
      String all = ids(rows, "cap/1", "cap/2", "cap/3");
      as("object_storage.view");
      assertHas(ProcessRunner.review(ObjectWriteProcesses.REFRESH_NAME, all), "1 record is over the bulk cap and needs object_storage.admin");
      as("object_storage.view", "object_storage.admin");
      assertThat(ProcessRunner.review(ObjectWriteProcesses.REFRESH_NAME, all)).as("admin may exceed the cap").anyMatch(l -> l.contains("3 objects will be read from the bucket"));
   }



   @Test
   void aUserWithoutTheBucketKeyCannotActOnItsObjects() throws Exception
   {
      reinstall(c -> c.setBucketScoping(true));
      Map<String, QRecord> rows;
      QContext.setQSession(new QSession().withUser(new QUser().withIdReference("scoped@example.com")).withPermissions(Set.of("object_storage.view", "object_storage.admin"))
         .withSecurityKeyValue(ObjectStorageTables.ALL_ACCESS_BUCKET, true).withSecurityKeyValue(ObjectStorageTables.ALL_ACCESS_CONN, true));
      rows = seed("indexed", target.indexBucket, Map.of("k/a", 1));
      String id = ids(rows, "k/a");
      QContext.setQSession(new QSession().withUser(new QUser().withIdReference("scoped@example.com")).withPermissions(Set.of("object_storage.view", "object_storage.admin"))
         .withSecurityKeyValue(ObjectStorageTables.KEY_TYPE_BUCKET, ConfigSync.bucketId("files")));
      List<String> review = ProcessRunner.review(ObjectWriteProcesses.REFRESH_NAME, id);
      assertThat(review).as("the record is not visible to this user at all: " + review).noneMatch(l -> l.contains("will be read from the bucket"));
   }
}
