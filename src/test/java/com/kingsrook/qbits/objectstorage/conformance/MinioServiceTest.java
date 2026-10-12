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

package com.kingsrook.qbits.objectstorage.conformance;


import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.kingsrook.qbits.objectstorage.api.Capability;
import com.kingsrook.qbits.objectstorage.api.CapabilityStatus;
import com.kingsrook.qbits.objectstorage.api.ChangeOperation;
import com.kingsrook.qbits.objectstorage.api.CopyRequest;
import com.kingsrook.qbits.objectstorage.api.DeleteMode;
import com.kingsrook.qbits.objectstorage.api.DeleteRequest;
import com.kingsrook.qbits.objectstorage.api.ListRequest;
import com.kingsrook.qbits.objectstorage.api.LockMode;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaders;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.api.PresignGet;
import com.kingsrook.qbits.objectstorage.api.PutRequest;
import com.kingsrook.qbits.objectstorage.api.StorageChange;
import com.kingsrook.qbits.objectstorage.api.StorageException;
import com.kingsrook.qbits.objectstorage.api.StoredObject;
import com.kingsrook.qbits.objectstorage.internal.CapabilityProbe;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.DefaultObjectHeaderPolicy;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.IndexHook;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.processes.TestConnectionProcess;
import com.kingsrook.qbits.objectstorage.testsupport.ProcessRunner;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Service behaviour that is specific to this qbit (not provider conformance),
 ** run against MinIO: the header policy on put, listeners, the index hook,
 ** protection and system callers (SF1-SF5), object lock, and the Test
 ** connection actions.
 *******************************************************************************/
class MinioServiceTest
{
   private ConformanceTarget target;
   private String            prefix;



   /*******************************************************************************
    ** Install the qbit against MinIO, with a signed-in user.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      Assumptions.assumeTrue(Targets.dockerAvailable(), "Docker is not available");
      target = Targets.minio();
      prefix = "svc/" + UUID.randomUUID() + "/";
      StorageTestSupport.setUp(target, c -> c.setMaxPresignsPerUserPerHour(3));
      QContext.setQSession(new QSession().withUser(new QUser().withIdReference("alice@example.com")));
   }



   /*******************************************************************************
    ** Tear down.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      StorageTestSupport.tearDown();
   }



   @Test
   void putAppliesTheHeaderPolicyUnlessHeadersAreGiven() throws Exception
   {
      StorageTestSupport.insert(ObjectStorageTables.HEADER_RULE,
         new QRecord().withValue("name", "images").withValue("contentTypePattern", "image/*").withValue("cacheControl", "public, max-age=600").withValue("priority", 10).withValue("enabled", true));
      DefaultObjectHeaderPolicy.clearCache();

      ObjectStorage.bucket("files").put(PutRequest.of(prefix + "a.png", new byte[] { 1, 2 }));
      assertThat(ObjectStorage.bucket("files").head(prefix + "a.png").orElseThrow().getHeaders().getCacheControl()).isEqualTo("public, max-age=600");
      assertThat(ObjectStorage.bucket("files").head(prefix + "a.png").orElseThrow().getHeaders().getContentType()).isEqualTo("image/png");

      ObjectStorage.bucket("files").put(PutRequest.of(prefix + "b.png", new byte[] { 1 }).withCacheControl("no-store"));
      assertThat(ObjectStorage.bucket("files").head(prefix + "b.png").orElseThrow().getHeaders().getCacheControl()).isEqualTo("no-store");

      // SF1: a BACKUP bucket never gets policy headers
      ObjectStorage.asSystem("backups", "r1").bucket("locked").put(PutRequest.of(prefix + "c.png", new byte[] { 1 }));
      assertThat(ObjectStorage.bucket("locked").head(prefix + "c.png").orElseThrow().getHeaders().getCacheControl()).isNull();
   }



   @Test
   void changeListenersHearEveryWriteAndNeverFailIt() throws Exception
   {
      List<StorageChange> heard = new ArrayList<>();
      ObjectStorage.addChangeListener(heard::add);
      ObjectStorage.addChangeListener(change ->
      {
         throw new IllegalStateException("purge planner is down");
      });

      String key = prefix + "listened.txt";
      ObjectStorage.bucket("files").put(PutRequest.of(key, "x".getBytes(StandardCharsets.UTF_8)));
      ObjectStorage.bucket("files").copy(CopyRequest.of(key, key + ".copy"));
      ObjectStorage.bucket("files").updateHeaders(key, ObjectHeaders.cacheControl("no-cache"));
      ObjectStorage.bucket("files").delete(DeleteRequest.of(key));
      ObjectStorage.bucket("soft").put(PutRequest.of(key + ".soft", "y".getBytes(StandardCharsets.UTF_8)));
      ObjectStorage.bucket("soft").delete(DeleteRequest.of(key + ".soft"));
      ObjectStorage.bucket("soft").restore(key + ".soft");

      assertThat(heard).extracting(StorageChange::getOperation).containsExactly(ChangeOperation.PUT, ChangeOperation.COPY, ChangeOperation.HEADERS, ChangeOperation.DELETE,
         ChangeOperation.PUT, ChangeOperation.DELETE, ChangeOperation.RESTORE);
      assertThat(heard.get(1).getKey()).isEqualTo(key);
      assertThat(heard.get(1).getDestinationKey()).isEqualTo(key + ".copy");
      assertThat(heard.get(0).getActor()).isEqualTo("alice@example.com");

      List<QRecord> rows = Db.query(ObjectStorageTables.OPERATION, "objectKey", key, "action", "PUT");
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).getValueString("detail")).contains("listener errors").contains("purge planner is down");
      assertThat(rows.get(0).getValueString("result")).isEqualTo("OK");
   }



   @Test
   void theIndexHookIsToldAboutWritesAndDeletes() throws Exception
   {
      List<String> events = new ArrayList<>();
      ObjectStorageRuntime.setIndexHook(new IndexHook()
      {
         /*******************************************************************************
          ** Record.
          *******************************************************************************/
         @Override
         public void objectWritten(StorageBucketConfig bucket, StoredObject object)
         {
            events.add("written " + bucket.getAlias() + " " + object.getKey() + " " + object.getSize());
         }



         /*******************************************************************************
          ** Record.
          *******************************************************************************/
         @Override
         public void objectRemoved(StorageBucketConfig bucket, String key, boolean soft)
         {
            events.add("removed " + bucket.getAlias() + " " + key + " " + soft);
         }
      });
      ObjectStorage.bucket("files").put(PutRequest.of(prefix + "i.txt", new byte[3]));
      ObjectStorage.bucket("files").copy(CopyRequest.of(prefix + "i.txt", prefix + "j.txt"));
      ObjectStorage.bucket("files").delete(DeleteRequest.of(prefix + "i.txt"));
      assertThat(events).containsExactly("written files " + prefix + "i.txt 3", "written files " + prefix + "j.txt 3", "removed files " + prefix + "i.txt false");

      ObjectStorageRuntime.setIndexHook(new IndexHook()
      {
         /*******************************************************************************
          ** Fails.
          *******************************************************************************/
         @Override
         public void objectWritten(StorageBucketConfig bucket, StoredObject object)
         {
            throw new IllegalStateException("index is down");
         }



         /*******************************************************************************
          ** Fails.
          *******************************************************************************/
         @Override
         public void objectRemoved(StorageBucketConfig bucket, String key, boolean soft)
         {
            throw new IllegalStateException("index is down");
         }
      });
      ObjectStorage.bucket("files").put(PutRequest.of(prefix + "k.txt", new byte[1]));
      assertThat(ObjectStorage.bucket("files").head(prefix + "k.txt")).as("an index failure never fails the write").isPresent();
      ObjectStorage.bucket("files").delete(DeleteRequest.of(prefix + "k.txt"));
   }



   @Test
   void systemCallersAreLoggedWithTheirRunAndTheUserWhoStartedIt() throws Exception
   {
      ObjectStorage.asSystem("backups", "run 1234").bucket("files").put(PutRequest.of(prefix + "sys.txt", new byte[1]));
      List<QRecord> rows = Db.query(ObjectStorageTables.OPERATION, "objectKey", prefix + "sys.txt");
      assertThat(rows.get(0).getValueString("actor")).isEqualTo("system: backups run run 1234 (started by user alice@example.com)");
      assertThat(rows.get(0).getValueString("processRunId")).isEqualTo("run 1234");
      assertThat(rows.get(0).getValueString("bucketAlias")).isEqualTo("files");
      assertThat(rows.get(0).getValueInteger("bucketId")).isEqualTo(ConfigSync.bucketId("files"));
   }



   @Test
   void failedWritesAreLoggedAsFailedWithTheProviderRequestId() throws Exception
   {
      assertThatThrownBy(() -> ObjectStorage.bucket("files").put(PutRequest.of(prefix + "x", new byte[1]).withIfNoneMatch(true).withObjectLock(LockMode.GOVERNANCE, Instant.now().plusSeconds(60))))
         .isInstanceOf(StorageException.class);
      List<QRecord> rows = Db.query(ObjectStorageTables.OPERATION, "objectKey", prefix + "x");
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).getValueString("result")).isEqualTo("FAILED");
      assertThat(rows.get(0).getValueString("errorMessage")).isNotBlank();
   }



   @Test
   void protectionRulesBlockWritesAndDeletesButAllowTheNamedSystemCaller() throws Exception
   {
      Integer filesId = ConfigSync.bucketId("files");
      StorageTestSupport.insert(ObjectStorageTables.PROTECTION_RULE,
         new QRecord().withValue("bucketId", filesId).withValue("prefixGlob", prefix + "ro/*").withValue("mode", "READ_ONLY").withValue("reason", "legal hold").withValue("enabled", true),
         new QRecord().withValue("bucketId", filesId).withValue("prefixGlob", prefix + "nd/*").withValue("mode", "NO_DELETE").withValue("allowedSystemCallers", "backups").withValue("enabled", true));
      assertThatThrownBy(() -> ObjectStorage.bucket("files").put(PutRequest.of(prefix + "ro/a", new byte[1]))).isInstanceOfSatisfying(StorageException.class,
         e -> assertThat(e.getReason()).isEqualTo(StorageException.Reason.ACCESS_DENIED));
      assertThat(ObjectStorage.bucket("files").head(prefix + "ro/a")).isEmpty();

      ObjectStorage.bucket("files").put(PutRequest.of(prefix + "nd/a", new byte[1]));
      assertThatThrownBy(() -> ObjectStorage.bucket("files").delete(DeleteRequest.of(prefix + "nd/a"))).hasMessageContaining("Protected by a rule");
      assertThatThrownBy(() -> ObjectStorage.asSystem("other", null).bucket("files").delete(DeleteRequest.of(prefix + "nd/a").permanent())).isInstanceOf(StorageException.class);
      assertThat(ObjectStorage.asSystem("backups", "r").bucket("files").delete(DeleteRequest.of(prefix + "nd/a").permanent()).getMode()).isEqualTo(DeleteMode.PERMANENT);
      assertThat(ObjectStorage.bucket("files").head(prefix + "nd/a")).isEmpty();
   }



   @Test
   void objectLockOnABackupBucketIsOfferedOnlyWhereSupportedAndPermanentDeleteSkipsLockedObjects() throws Exception
   {
      String key = prefix + "locked.bin";
      ObjectStorage.asSystem("backups", "r").bucket("locked").put(PutRequest.of(key, new byte[] { 1, 2, 3 }).withObjectLock(LockMode.COMPLIANCE, Instant.now().plus(Duration.ofHours(1)))
         .withStorageClass("STANDARD"));
      StoredObject head = ObjectStorage.bucket("locked").head(key).orElseThrow();
      assertThat(head.getLockMode()).isEqualTo("COMPLIANCE");
      assertThat(head.getLockRetainUntil()).isNotNull();

      var result = ObjectStorage.asSystem("backups", "r").bucket("locked").delete(DeleteRequest.of(key).permanent());
      assertThat(result.getMode()).isEqualTo(DeleteMode.SKIPPED_OBJECT_LOCK);
      assertThat(ObjectStorage.bucket("locked").head(key)).as("the locked object is still there").isPresent();
      assertThat(Db.query(ObjectStorageTables.OPERATION, "objectKey", key, "action", "DELETE").get(0).getValueString("result")).isEqualTo("SKIPPED");

      // a bucket without object lock: never silently dropped, either rejected by the store or by the capability
      assertThatThrownBy(() -> ObjectStorage.bucket("files").put(PutRequest.of(prefix + "nolock", new byte[1]).withObjectLock(LockMode.GOVERNANCE, Instant.now().plusSeconds(60))))
         .isInstanceOf(StorageException.class);
      CapabilityProbe.probeConnection("primary", false);
      assertThat(ObjectStorage.bucket("files").capability(Capability.OBJECT_LOCK)).isEqualTo(CapabilityStatus.UNSUPPORTED);
      assertThatThrownBy(() -> ObjectStorage.bucket("files").put(PutRequest.of(prefix + "nolock", new byte[1]).withObjectLock(LockMode.GOVERNANCE, Instant.now().plusSeconds(60))))
         .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getReason()).isEqualTo(StorageException.Reason.UNSUPPORTED));
   }



   @Test
   void writeProbesSkipABackupBucketWithObjectLock() throws Exception
   {
      CapabilityProbe.Report report = CapabilityProbe.probeConnection("primary", true);
      assertThat(report.getNotes().get("locked")).containsExactly("write probes skipped: object lock");
      assertThat(report.getByBucket().get("locked")).doesNotContainKey(Capability.PUT_GET_DELETE);
      assertThat(report.getByBucket().get("locked").get(Capability.OBJECT_LOCK)).isEqualTo(CapabilityStatus.SUPPORTED);
      assertThat(ObjectStorage.asSystem("x", null).bucket("locked").list(ListRequest.prefix(CapabilityProbe.PROBE_PREFIX)).getItems()).isEmpty();
      assertThat(report.getClockSkewSeconds()).isNotNull().isLessThan(60);
      assertThat(report.getPresignHostReachable()).isTrue();
      assertThat(report.summaryFor("files")).contains("supported");
      assertThat(report.summaryFor("nothing")).isEqualTo("nothing probed");
   }



   @Test
   void presignsAreCappedPerUserPerHourAndLoggedWithoutTheirSignature() throws Exception
   {
      for(int i = 0; i < 3; i++)
      {
         ObjectStorage.bucket("files").presignGet(PresignGet.of(prefix + "p"));
      }
      assertThatThrownBy(() -> ObjectStorage.bucket("files").presignGet(PresignGet.of(prefix + "p"))).isInstanceOfSatisfying(StorageException.class,
         e -> assertThat(e.getReason()).isEqualTo(StorageException.Reason.THROTTLED));
      assertThat(Db.query(ObjectStorageTables.OPERATION, "action", "PRESIGN_GET")).hasSize(3);
      ObjectStorage.asSystem("backups", "r").bucket("files").presignGet(PresignGet.of(prefix + "p"));
   }



   @Test
   void testConnectionActionsReviewThenRecordTheResult() throws Exception
   {
      QRecord connection = Db.first(ObjectStorageTables.CONNECTION, "name", "primary");
      String id = String.valueOf(connection.getValue("id"));

      List<String> review = ProcessRunner.review(TestConnectionProcess.READ_NAME, id);
      assertThat(review).anyMatch(l -> l.contains("connection will be tested")).anyMatch(l -> l.contains("buckets will get read-only probes"));
      assertThat(Db.first(ObjectStorageTables.CONNECTION, "name", "primary").getValue("lastTestedAt")).as("the review changes nothing").isNull();

      ProcessRunner.Run run = ProcessRunner.run(TestConnectionProcess.READ_NAME, id, null);
      assertThat(run.result()).anyMatch(l -> l.startsWith("files: ") && l.contains("supported")).anyMatch(l -> l.contains("Clock skew"));
      QRecord after = Db.first(ObjectStorageTables.CONNECTION, "name", "primary");
      assertThat(after.getValueInstant("lastTestedAt")).isNotNull();
      assertThat(after.getValueString("lastTestStatus")).isEqualTo("OK");
      assertThat(after.getValueString("lastTestSummary")).contains("files:");
      assertThat(Db.query(ObjectStorageTables.CONNECTION_CAPABILITY, "connectionId", connection.getValue("id"), "capability", "LIST_V2").get(0).getValueString("status")).isEqualTo("SUPPORTED");
      assertThat(Db.query(ObjectStorageTables.BUCKET_CAPABILITY, "bucketId", ConfigSync.bucketId("files"), "capability", "PUT_GET_DELETE")).as("read-only probes make no write test").isEmpty();

      List<String> writeReview = ProcessRunner.review(TestConnectionProcess.WRITE_NAME, id);
      assertThat(writeReview).anyMatch(l -> l.contains("will get write tests")).anyMatch(l -> l.contains("will not get write tests"));
      ProcessRunner.run(TestConnectionProcess.WRITE_NAME, id, null);
      assertThat(Db.query(ObjectStorageTables.BUCKET_CAPABILITY, "bucketId", ConfigSync.bucketId("files"), "capability", "PUT_GET_DELETE").get(0).getValueString("status")).isEqualTo("SUPPORTED");
      assertThat(Db.query(ObjectStorageTables.CONNECTION_CAPABILITY, "connectionId", connection.getValue("id"), "capability", "PRESIGNED_POST").get(0).getValueString("status")).isEqualTo("SUPPORTED");
   }



   @Test
   void testConnectionRaisesAndResolvesTheConnectionAlert() throws Exception
   {
      List<String> events = new ArrayList<>();
      ObjectStorageRuntime.setAlertSink(new com.kingsrook.qbits.objectstorage.api.StorageAlertSink()
      {
         /*******************************************************************************
          ** Record.
          *******************************************************************************/
         @Override
         public void raise(String alertType, String dedupeKey, String severity, String message, Map<String, Object> details)
         {
            events.add("raise " + alertType + " " + dedupeKey + " " + severity);
         }



         /*******************************************************************************
          ** Record.
          *******************************************************************************/
         @Override
         public void resolve(String alertType, String dedupeKey)
         {
            events.add("resolve " + alertType + " " + dedupeKey);
         }
      });
      String id = String.valueOf(Db.first(ObjectStorageTables.CONNECTION, "name", "primary").getValue("id"));
      ProcessRunner.run(TestConnectionProcess.READ_NAME, id, null);
      assertThat(events).containsExactly("resolve storage.connection.failed connection:primary");

      StorageTestSupport.setUp(target, c -> c.findConnection("primary").setEndpoint("http://127.0.0.1:1"));
      ObjectStorageRuntime.setAlertSink(new com.kingsrook.qbits.objectstorage.api.StorageAlertSink()
      {
         /*******************************************************************************
          ** Record.
          *******************************************************************************/
         @Override
         public void raise(String alertType, String dedupeKey, String severity, String message, Map<String, Object> details)
         {
            events.add("raise " + alertType + " " + dedupeKey + " " + severity);
         }



         /*******************************************************************************
          ** Record.
          *******************************************************************************/
         @Override
         public void resolve(String alertType, String dedupeKey)
         {
            events.add("resolve " + alertType + " " + dedupeKey);
         }
      });
      String id2 = String.valueOf(Db.first(ObjectStorageTables.CONNECTION, "name", "primary").getValue("id"));
      ProcessRunner.run(TestConnectionProcess.READ_NAME, id2, null);
      assertThat(events).contains("raise storage.connection.failed connection:primary error");
      assertThat(Db.first(ObjectStorageTables.CONNECTION, "name", "primary").getValueString("lastTestStatus")).isEqualTo("PROBLEMS");
   }
}
