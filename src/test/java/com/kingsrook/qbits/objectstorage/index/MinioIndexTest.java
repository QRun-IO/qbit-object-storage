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


import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import com.kingsrook.qbits.objectstorage.api.CopyRequest;
import com.kingsrook.qbits.objectstorage.api.DeleteRequest;
import com.kingsrook.qbits.objectstorage.api.FieldReferenceProvider;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaders;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.api.PutRequest;
import com.kingsrook.qbits.objectstorage.api.StorageAlertSink;
import com.kingsrook.qbits.objectstorage.conformance.ConformanceTarget;
import com.kingsrook.qbits.objectstorage.conformance.Targets;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.DefaultObjectHeaderPolicy;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The object index against MinIO: scan, folders and rollups, HEAD backlog,
 ** write-through, soft-delete rows, resume, the mass-removal safety stop, the
 ** reference scan (orphaned and missing), usage snapshots and the nightly job.
 *******************************************************************************/
class MinioIndexTest
{
   private ConformanceTarget target;
   private List<String>      alerts = new ArrayList<>();



   /*******************************************************************************
    ** Install the qbit against MinIO with an empty index bucket and a media table.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      Assumptions.assumeTrue(Targets.dockerAvailable(), "Docker is not available");
      target = Targets.minio();
      emptyBucket(target.indexBucket);
      emptyBucket(target.versionedBucket);
      emptyBucket(target.trashBucket);
      alerts.clear();
      StorageTestSupport.setUp(StorageTestSupport.config(target, com.kingsrook.qbits.objectstorage.config.ChecksumMode.WHEN_REQUIRED)
         .withReferenceProvider(FieldReferenceProvider.keyPrefix("indexed", "media", "storageKey"))
         .withReferenceProvider(FieldReferenceProvider.url("indexed", "media", "thumbUrl")), instance ->
         instance.addTable(new QTableMetaData().withName("media").withBackendName(StorageTestSupport.MEMORY).withPrimaryKeyField("id")
            .withField(new QFieldMetaData("id", QFieldType.INTEGER)).withField(new QFieldMetaData("storageKey", QFieldType.STRING)).withField(new QFieldMetaData("thumbUrl", QFieldType.STRING))));
      ObjectStorageRuntime.setAlertSink(new StorageAlertSink()
      {
         /*******************************************************************************
          ** Record.
          *******************************************************************************/
         @Override
         public void raise(String alertType, String dedupeKey, String severity, String message, Map<String, Object> details)
         {
            alerts.add("raise " + alertType + " " + dedupeKey + " " + severity);
         }



         /*******************************************************************************
          ** Record.
          *******************************************************************************/
         @Override
         public void resolve(String alertType, String dedupeKey)
         {
            alerts.add("resolve " + alertType + " " + dedupeKey);
         }
      });
   }



   /*******************************************************************************
    ** Tear down.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      ObjectIndex.failAfterPages = null;
      StorageTestSupport.tearDown();
   }



   /*******************************************************************************
    ** Delete everything in a bucket (raw client, independent of the code under test).
    *******************************************************************************/
   private void emptyBucket(String bucket)
   {
      if(bucket == null)
      {
         return;
      }
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
    ** Put an object with the raw client (a write made outside QQQ).
    *******************************************************************************/
   private void rawPut(String bucket, String key, int size)
   {
      try(S3Client raw = target.rawClient())
      {
         raw.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(key.endsWith(".png") ? "image/png" : "text/plain").build(), RequestBody.fromBytes(new byte[size]));
      }
   }



   /*******************************************************************************
    ** The object rows of the index bucket by key.
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
    ** The folder rows by path.
    *******************************************************************************/
   private Map<String, QRecord> folders(String alias) throws Exception
   {
      Map<String, QRecord> map = new java.util.TreeMap<>();
      for(QRecord r : Db.query(ObjectStorageTables.FOLDER, "bucketId", ConfigSync.bucketId(alias)))
      {
         map.put(r.getValueString("path"), r);
      }
      return (map);
   }



   @Test
   void scanBuildsRowsFoldersAndRollupsAndSecondScanRepairsDrift() throws Exception
   {
      rawPut(target.indexBucket, "a/1.txt", 10);
      rawPut(target.indexBucket, "a/b/2.png", 20);
      rawPut(target.indexBucket, "a/b/c/3.bin", 30);
      rawPut(target.indexBucket, "top.txt", 5);

      ObjectIndex.ScanResult first = ObjectIndex.scan("indexed");
      assertThat(first.status()).isEqualTo("OK");
      assertThat(first.seen()).isEqualTo(4);
      assertThat(first.added()).isEqualTo(4);

      Map<String, QRecord> rows = rows("indexed");
      assertThat(rows.keySet()).containsExactly("a/1.txt", "a/b/2.png", "a/b/c/3.bin", "top.txt");
      QRecord png = rows.get("a/b/2.png");
      assertThat(png.getValueString("name")).isEqualTo("2.png");
      assertThat(png.getValueString("extension")).isEqualTo("png");
      assertThat(png.getValueLong("size")).isEqualTo(20);
      assertThat(png.getValueString("etag")).doesNotContain("\"").hasSize(32);
      assertThat(png.getValueBoolean("headersKnown")).isFalse();
      assertThat(rows.get("top.txt").getValueInteger("folderId")).isNull();

      Map<String, QRecord> folders = folders("indexed");
      assertThat(folders.keySet()).containsExactly("a/", "a/b/", "a/b/c/");
      assertThat(folders.get("a/").getValueLong("objectCount")).isEqualTo(3);
      assertThat(folders.get("a/").getValueLong("totalBytes")).isEqualTo(60);
      assertThat(folders.get("a/b/").getValueLong("objectCount")).isEqualTo(2);
      assertThat(folders.get("a/b/c/").getValueLong("totalBytes")).isEqualTo(30);
      assertThat(folders.get("a/b/").getValueInteger("parentFolderId")).isEqualTo(folders.get("a/").getValueInteger("id"));
      assertThat(folders.get("a/").getValueInteger("parentFolderId")).isNull();
      assertThat(folders.get("a/").getValueInstant("newestChange")).isNotNull();

      QRecord bucket = Db.first(ObjectStorageTables.BUCKET, "alias", "indexed");
      assertThat(bucket.getValueLong("objectCount")).isEqualTo(4);
      assertThat(bucket.getValueLong("totalBytes")).isEqualTo(65);
      assertThat(bucket.getValueString("lastScanStatus")).isEqualTo("OK");
      assertThat(bucket.getValueInstant("lastScanDate")).isNotNull();
      assertThat(bucket.getValueLong("scanGeneration")).isEqualTo(1);

      // drift: change, remove and add outside QQQ
      rawPut(target.indexBucket, "a/1.txt", 11);
      try(S3Client raw = target.rawClient())
      {
         raw.deleteObject(DeleteObjectRequest.builder().bucket(target.indexBucket).key("top.txt").build());
      }
      rawPut(target.indexBucket, "new/4.txt", 7);
      ObjectIndex.ScanResult second = ObjectIndex.scan("indexed");
      assertThat(second.added()).isEqualTo(1);
      assertThat(second.updated()).isEqualTo(3);
      assertThat(second.removed()).isEqualTo(1);
      assertThat(rows("indexed").keySet()).containsExactly("a/1.txt", "a/b/2.png", "a/b/c/3.bin", "new/4.txt");
      assertThat(rows("indexed").get("a/1.txt").getValueLong("size")).isEqualTo(11);
      assertThat(rows("indexed").get("a/1.txt").getValueBoolean("headersKnown")).as("a changed object needs its headers again").isFalse();
      assertThat(folders("indexed").get("a/").getValueLong("totalBytes")).isEqualTo(61);
      assertThat(folders("indexed").get("new/").getValueLong("objectCount")).isEqualTo(1);
      assertThat(Db.first(ObjectStorageTables.BUCKET, "alias", "indexed").getValueLong("scanGeneration")).isEqualTo(2);
      assertThat(alerts).contains("resolve storage.scan.stale bucket:indexed");
   }



   @Test
   void headBacklogFillsHeadersAndTracksPolicyCoverage() throws Exception
   {
      StorageTestSupport.insert(ObjectStorageTables.HEADER_RULE,
         new QRecord().withValue("name", "png").withValue("contentTypePattern", "image/png").withValue("cacheControl", "public, max-age=60").withValue("priority", 1).withValue("enabled", true));
      DefaultObjectHeaderPolicy.clearCache();
      rawPut(target.indexBucket, "img/a.png", 3);
      rawPut(target.indexBucket, "img/b.png", 3);
      rawPut(target.indexBucket, "doc/c.txt", 3);
      ObjectIndex.scan("indexed");

      assertThat(ObjectIndex.headBacklog("indexed", 2)).isEqualTo(2);
      assertThat(ObjectIndex.headBacklog("indexed", 100)).isEqualTo(1);
      assertThat(ObjectIndex.headBacklog("indexed", 100)).isZero();

      Map<String, QRecord> rows = rows("indexed");
      assertThat(rows.get("img/a.png").getValueString("contentType")).isEqualTo("image/png");
      assertThat(rows.get("img/a.png").getValueBoolean("headersKnown")).isTrue();
      assertThat(rows.get("img/a.png").getValueBoolean("matchesHeaderPolicy")).as("the object has no Cache-Control but the policy wants one").isFalse();
      assertThat(rows.get("doc/c.txt").getValueBoolean("matchesHeaderPolicy")).as("no rule, no opinion").isTrue();
      QRecord bucket = Db.first(ObjectStorageTables.BUCKET, "alias", "indexed");
      assertThat(bucket.getValueLong("headersKnownCount")).isEqualTo(3);
      assertThat(bucket.getValueLong("headersMatchingPolicyCount")).isEqualTo(1);

      // fix one object through the service: write-through marks it matching
      ObjectStorage.bucket("indexed").updateHeaders("img/a.png", ObjectHeaders.cacheControl("public, max-age=60"));
      assertThat(rows("indexed").get("img/a.png").getValueBoolean("matchesHeaderPolicy")).isTrue();
      assertThat(rows("indexed").get("img/a.png").getValueString("cacheControl")).isEqualTo("public, max-age=60");
   }



   @Test
   void writesThroughTheServiceUpdateTheIndexAtOnce() throws Exception
   {
      ObjectStorage.bucket("indexed").put(PutRequest.of("w/one.txt", "12345".getBytes(StandardCharsets.UTF_8)).withContentType("text/plain").withMetadata(Map.of("k", "v")));
      Map<String, QRecord> rows = rows("indexed");
      assertThat(rows.keySet()).containsExactly("w/one.txt");
      QRecord one = rows.get("w/one.txt");
      assertThat(one.getValueLong("size")).isEqualTo(5);
      assertThat(one.getValueBoolean("headersKnown")).isTrue();
      assertThat(one.getValueString("contentType")).isEqualTo("text/plain");
      assertThat(one.getValueString("userMetadata")).contains("\"k\"").contains("\"v\"");
      assertThat(folders("indexed").get("w/").getValueLong("objectCount")).isEqualTo(1);
      assertThat(folders("indexed").get("w/").getValueLong("totalBytes")).isEqualTo(5);

      ObjectStorage.bucket("indexed").copy(CopyRequest.of("w/one.txt", "w/deeper/two.txt"));
      assertThat(rows("indexed").keySet()).containsExactly("w/deeper/two.txt", "w/one.txt");
      assertThat(folders("indexed").get("w/").getValueLong("objectCount")).isEqualTo(2);
      assertThat(folders("indexed").get("w/deeper/").getValueLong("totalBytes")).isEqualTo(5);

      ObjectStorage.bucket("indexed").put(PutRequest.of("w/one.txt", "123456789".getBytes(StandardCharsets.UTF_8)));
      assertThat(folders("indexed").get("w/").getValueLong("totalBytes")).isEqualTo(14);
      assertThat(folders("indexed").get("w/").getValueLong("objectCount")).isEqualTo(2);

      ObjectStorage.asSystem("test", "r").bucket("indexed").delete(DeleteRequest.of("w/deeper/two.txt").permanent());
      ObjectStorage.bucket("indexed").delete(DeleteRequest.of("w/one.txt"));
      assertThat(rows("indexed")).as("a permanent delete removes the row").doesNotContainKey("w/deeper/two.txt").doesNotContainKey("w/one.txt");
      Map<String, QRecord> bucketRow = Map.of("b", Db.first(ObjectStorageTables.BUCKET, "alias", "indexed"));
      assertThat(bucketRow.get("b").getValueLong("objectCount")).isZero();
      assertThat(bucketRow.get("b").getValueLong("totalBytes")).isZero();
   }



   @Test
   void deletedObjectsOfAVersionedBucketStayAsDeletedRowsUntilTheRetentionPasses() throws Exception
   {
      ObjectStorage.bucket("versioned").put(PutRequest.of("v/keep.txt", "x".getBytes(StandardCharsets.UTF_8)));
      ObjectStorage.bucket("versioned").put(PutRequest.of("v/gone.txt", "yy".getBytes(StandardCharsets.UTF_8)));
      ObjectStorage.bucket("versioned").delete(DeleteRequest.of("v/gone.txt"));
      QRecord gone = rows("versioned").get("v/gone.txt");
      assertThat(gone.getValueBoolean("deleted")).isTrue();
      assertThat(folders("versioned").get("v/").getValueLong("objectCount")).isEqualTo(1);

      ObjectStorage.bucket("versioned").restore("v/gone.txt");
      assertThat(rows("versioned").get("v/gone.txt").getValueBoolean("deleted")).isFalse();
      assertThat(folders("versioned").get("v/").getValueLong("objectCount")).isEqualTo(2);

      // a delete made outside QQQ is found by the scan and kept as a deleted row, then purged
      try(S3Client raw = target.rawClient())
      {
         raw.deleteObject(DeleteObjectRequest.builder().bucket(target.versionedBucket).key("v/keep.txt").build());
      }
      ObjectIndex.ScanResult scan = ObjectIndex.scan("versioned");
      assertThat(scan.markedDeleted()).isEqualTo(1);
      assertThat(rows("versioned").get("v/keep.txt").getValueBoolean("deleted")).isTrue();

      ObjectStorageRuntime.setClock(Clock.fixed(Instant.now().plus(Duration.ofDays(31)), ZoneOffset.UTC));
      ObjectIndex.scan("versioned");
      assertThat(rows("versioned")).doesNotContainKey("v/keep.txt");
   }



   @Test
   void aCrashedScanResumesFromItsSavedCursor() throws Exception
   {
      try(S3Client raw = target.rawClient())
      {
         IntStream.range(0, 1500).parallel().forEach(i -> raw.putObject(PutObjectRequest.builder().bucket(target.indexBucket).key("many/" + String.format("%05d", i)).build(), RequestBody.empty()));
      }
      ObjectIndex.failAfterPages = 1;
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> ObjectIndex.scan("indexed")).hasMessageContaining("Simulated crash");
      QRecord crashed = Db.first(ObjectStorageTables.BUCKET, "alias", "indexed");
      assertThat(crashed.getValueString("lastScanStatus")).isEqualTo("FAILED");
      assertThat(crashed.getValueString("scanCursor")).isNotBlank();
      assertThat(rows("indexed")).hasSize(1000);

      ObjectIndex.ScanResult resumed = ObjectIndex.scan("indexed");
      assertThat(resumed.status()).isEqualTo("OK");
      assertThat(resumed.pages()).as("only the remaining page is listed").isEqualTo(1);
      assertThat(rows("indexed")).hasSize(1500);
      assertThat(Db.first(ObjectStorageTables.BUCKET, "alias", "indexed").getValueLong("scanGeneration")).as("a resume keeps the generation").isEqualTo(1);
      assertThat(Db.first(ObjectStorageTables.BUCKET, "alias", "indexed").getValueLong("objectCount")).isEqualTo(1500);
   }



   @Test
   void theMassRemovalSafetyStopLeavesTheIndexAloneAndRaisesTheStaleAlert() throws Exception
   {
      for(int i = 0; i < 30; i++)
      {
         rawPut(target.indexBucket, "s/" + i, 1);
      }
      ObjectIndex.scan("indexed");
      try(S3Client raw = target.rawClient())
      {
         for(int i = 0; i < 25; i++)
         {
            raw.deleteObject(DeleteObjectRequest.builder().bucket(target.indexBucket).key("s/" + i).build());
         }
      }
      alerts.clear();
      ObjectIndex.ScanResult stopped = ObjectIndex.scan("indexed");
      assertThat(stopped.status()).isEqualTo("STOPPED_SAFETY");
      assertThat(stopped.note()).contains("25 of 30").contains("wrong prefix");
      assertThat(rows("indexed")).hasSize(30);
      assertThat(Db.first(ObjectStorageTables.BUCKET, "alias", "indexed").getValueString("lastScanStatus")).isEqualTo("STOPPED_SAFETY");
      assertThat(alerts).contains("raise storage.scan.stale bucket:indexed warning");
   }



   @Test
   void aScanOfANonIndexedBucketOrAConcurrentScanIsRefused() throws Exception
   {
      assertThat(ObjectIndex.scan("files").status()).isEqualTo("NOT_INDEXED");
      assertThat(ObjectIndex.isScanning("indexed")).isFalse();
   }



   @Test
   void theReferenceScanFindsOrphanedAndMissingObjectsAndProtectsReferencedOnes() throws Exception
   {
      rawPut(target.indexBucket, "media/1/a.jpg", 10);
      rawPut(target.indexBucket, "media/1/b.jpg", 20);
      rawPut(target.indexBucket, "media/9/orphan.jpg", 30);
      rawPut(target.indexBucket, "exempt/tool.bin", 40);
      rawPut(target.indexBucket, "single/direct.png", 50);
      rawPut(target.indexBucket, "tombstone/other.txt", 1);
      ObjectIndex.scan("indexed");

      StorageTestSupport.insert("media",
         new QRecord().withValue("storageKey", "media/1/"),
         new QRecord().withValue("storageKey", "media/2/gone/"),
         new QRecord().withValue("thumbUrl", "https://cdn.example.com/idx/single/direct.png?v=3"),
         new QRecord().withValue("thumbUrl", "https://cdn.example.com/idx/single/not-there.png"),
         new QRecord().withValue("thumbUrl", "https://elsewhere.example.com/x.png"),
         new QRecord().withValue("storageKey", null));
      ReferenceScan.Result result = ReferenceScan.run("indexed");

      assertThat(result.references()).isEqualTo(4);
      assertThat(result.missing()).isEqualTo(2);
      assertThat(result.orphaned()).as("orphan.jpg and other.txt; exempt/ is never reported").isEqualTo(2);
      assertThat(result.orphanedBytes()).isEqualTo(31);

      Map<String, QRecord> rows = rows("indexed");
      assertThat(rows.get("media/1/a.jpg").getValueInteger("referenceCount")).isEqualTo(1);
      assertThat(rows.get("media/1/b.jpg").getValueInteger("referenceCount")).isEqualTo(1);
      assertThat(rows.get("single/direct.png").getValueInteger("referenceCount")).isEqualTo(1);
      assertThat(rows.get("media/9/orphan.jpg").getValueInteger("referenceCount")).isZero();

      List<QRecord> refs = Db.query(ObjectStorageTables.OBJECT_REFERENCE, "bucketId", ConfigSync.bucketId("indexed"));
      assertThat(refs).hasSize(4);
      assertThat(refs.stream().filter(r -> Boolean.TRUE.equals(r.getValueBoolean("missing"))).map(r -> r.getValueString("keyOrPrefix")).toList())
         .containsExactlyInAnyOrder("media/2/gone/", "single/not-there.png");
      QRecord direct = refs.stream().filter(r -> "single/direct.png".equals(r.getValueString("keyOrPrefix"))).findFirst().orElseThrow();
      assertThat(direct.getValueString("sourceTable")).isEqualTo("media");
      assertThat(direct.getValueString("sourceField")).isEqualTo("thumbUrl");
      assertThat(direct.getValueInteger("objectId")).isEqualTo(rows.get("single/direct.png").getValueInteger("id"));

      QRecord bucket = Db.first(ObjectStorageTables.BUCKET, "alias", "indexed");
      assertThat(bucket.getValueLong("orphanedCount")).isEqualTo(2);
      assertThat(bucket.getValueLong("missingCount")).isEqualTo(2);
      assertThat(bucket.getValueInstant("lastReferenceScanDate")).isNotNull();
      assertThat(alerts).contains("raise storage.objects.missing bucket:indexed error");

      // a second scan after the data is fixed: counts go back down and the alert resolves
      for(QRecord m : Db.query("media"))
      {
         if("media/2/gone/".equals(m.getValueString("storageKey")) || "https://cdn.example.com/idx/single/not-there.png".equals(m.getValueString("thumbUrl")))
         {
            com.kingsrook.qqq.backend.core.actions.tables.DeleteAction da = new com.kingsrook.qqq.backend.core.actions.tables.DeleteAction();
            da.execute(new com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput("media").withPrimaryKeys(List.of(m.getValueInteger("id"))));
         }
      }
      alerts.clear();
      ReferenceScan.run("indexed");
      assertThat(Db.first(ObjectStorageTables.BUCKET, "alias", "indexed").getValueLong("missingCount")).isZero();
      assertThat(alerts).contains("resolve storage.objects.missing bucket:indexed");
   }



   @Test
   void backupBucketsAreNeverReferenceScanned() throws Exception
   {
      assertThat(ReferenceScan.applies(ObjectStorageRuntime.bucketConfig("locked"))).isFalse();
      assertThat(ReferenceScan.run("locked").references()).isZero();
      assertThat(ReferenceScan.applies(ObjectStorageRuntime.bucketConfig("files"))).isFalse();
      assertThat(ReferenceScan.applies(ObjectStorageRuntime.bucketConfig("indexed"))).isTrue();
   }



   @Test
   void usageSnapshotsRecordBytesAndPrefixesOncePerDayAndAlertNearTheQuota() throws Exception
   {
      rawPut(target.indexBucket, "p1/a", 100);
      rawPut(target.indexBucket, "p1/b", 100);
      rawPut(target.indexBucket, "p2/c", 50);
      ObjectIndex.scan("indexed");
      Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", ConfigSync.bucketId("indexed")).withValue("quotaBytes", 260L)));

      StorageJobs.usageSnapshot("indexed");
      StorageJobs.usageSnapshot("indexed");
      List<QRecord> usage = Db.query(ObjectStorageTables.USAGE_DAILY, "bucketId", ConfigSync.bucketId("indexed"));
      assertThat(usage).hasSize(1);
      assertThat(usage.get(0).getValueLong("totalBytes")).isEqualTo(250);
      assertThat(usage.get(0).getValueLong("objectCount")).isEqualTo(3);
      assertThat(usage.get(0).getValueString("byPrefix")).contains("p1/").contains("200").contains("p2/");
      assertThat(alerts).contains("raise storage.quota.near bucket:indexed error");

      Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", ConfigSync.bucketId("indexed")).withValue("quotaBytes", 10_000L)));
      alerts.clear();
      StorageJobs.usageSnapshot("indexed");
      assertThat(alerts).contains("resolve storage.quota.near bucket:indexed");
   }



   @Test
   void theNightlyJobScansHeadsSnapshotsAndPurgesExpiredTrash() throws Exception
   {
      rawPut(target.indexBucket, "n/1.txt", 4);
      ObjectStorage.bucket("soft").put(PutRequest.of("n/trashed.txt", "x".getBytes(StandardCharsets.UTF_8)));
      ObjectStorage.bucket("soft").delete(DeleteRequest.of("n/trashed.txt"));
      assertThat(ObjectStorage.bucket("trashbin").head(".trash/n/trashed.txt")).isPresent();

      ObjectStorageRuntime.setClock(Clock.fixed(Instant.now().plus(Duration.ofDays(31)), ZoneOffset.UTC));
      List<String> log = StorageJobs.nightly();
      assertThat(log).anyMatch(l -> l.startsWith("indexed scan: OK")).anyMatch(l -> l.startsWith("indexed HEAD backlog: 1")).anyMatch(l -> l.startsWith("indexed references:"))
         .anyMatch(l -> l.equals("soft trash purge: 1 objects"));
      assertThat(ObjectStorage.bucket("trashbin").head(".trash/n/trashed.txt")).isEmpty();
      assertThat(Db.query(ObjectStorageTables.USAGE_DAILY, "bucketId", ConfigSync.bucketId("indexed"))).hasSize(1);
      assertThat(Db.first(ObjectStorageTables.BUCKET, "alias", "indexed").getValueLong("headersKnownCount")).isEqualTo(1);
   }



   @Test
   void theHomeShowsRealNumbersFromTheIndexAndHonestEmptyStates() throws Exception
   {
      String empty = com.kingsrook.qbits.objectstorage.app.StorageHome.render("/x", Instant.now());
      assertThat(empty).contains("No storage writes have been recorded yet").contains("not scanned yet").contains("No usage snapshots yet").contains("Never tested");

      rawPut(target.indexBucket, "media/1/a.jpg", 2048);
      rawPut(target.indexBucket, "media/9/orphan.jpg", 1024);
      ObjectIndex.scan("indexed");
      ObjectIndex.headBacklog("indexed", 10);
      StorageTestSupport.insert("media", new QRecord().withValue("storageKey", "media/1/"));
      ReferenceScan.run("indexed");
      StorageJobs.usageSnapshot("indexed");
      ObjectStorage.bucket("indexed").put(PutRequest.of("media/1/c.jpg", new byte[3]));

      String html = com.kingsrook.qbits.objectstorage.app.StorageHome.render("/x", Instant.now());
      assertThat(html).contains("Buckets").contains("indexed").contains("/x/object_storage_bucket/").contains("KB")
         .contains("Data health").contains("Orphaned").contains("object_storage_object?filter=")
         .contains("Recent changes").contains("PUT").contains("media/1/c.jpg")
         .contains("Cache headers").contains("objects match the header policy")
         .contains("Usage trend").contains("collecting since")
         .contains("Connections").contains("Never tested");
   }
}
