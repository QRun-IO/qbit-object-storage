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


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import com.kingsrook.qbits.objectstorage.api.Capability;
import com.kingsrook.qbits.objectstorage.api.CapabilityStatus;
import com.kingsrook.qbits.objectstorage.api.CopyRequest;
import com.kingsrook.qbits.objectstorage.api.DeleteMode;
import com.kingsrook.qbits.objectstorage.api.DeleteRequest;
import com.kingsrook.qbits.objectstorage.api.ListRequest;
import com.kingsrook.qbits.objectstorage.api.MetadataDirective;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaders;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.api.ObjectStream;
import com.kingsrook.qbits.objectstorage.api.Page;
import com.kingsrook.qbits.objectstorage.api.ObjectSummary;
import com.kingsrook.qbits.objectstorage.api.PresignGet;
import com.kingsrook.qbits.objectstorage.api.PresignPost;
import com.kingsrook.qbits.objectstorage.api.PresignPut;
import com.kingsrook.qbits.objectstorage.api.PresignedPost;
import com.kingsrook.qbits.objectstorage.api.PutRequest;
import com.kingsrook.qbits.objectstorage.api.PutResult;
import com.kingsrook.qbits.objectstorage.api.Range;
import com.kingsrook.qbits.objectstorage.api.StorageBucket;
import com.kingsrook.qbits.objectstorage.api.StorageException;
import com.kingsrook.qbits.objectstorage.api.StoredObject;
import com.kingsrook.qbits.objectstorage.config.ChecksumMode;
import com.kingsrook.qbits.objectstorage.internal.CapabilityProbe;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The conformance suite: runs the service API end to end against one
 ** S3-compatible store (a ConformanceTarget). Subclasses supply the target
 ** (MinIO and Garage in CI on every PR). Every test uses its own key prefix, so
 ** tests share the scratch buckets safely.
 *******************************************************************************/
public abstract class ObjectStorageConformanceSuite
{
   protected ConformanceTarget target;
   protected String            prefix;

   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();



   /*******************************************************************************
    ** The store under test.
    *******************************************************************************/
   protected abstract ConformanceTarget createTarget();



   /*******************************************************************************
    ** Start (or reuse) the store and install the qbit against it.
    *******************************************************************************/
   @BeforeEach
   void setUpStore() throws Exception
   {
      if(!Targets.dockerAvailable())
      {
         if(Targets.dockerRequired())
         {
            org.junit.jupiter.api.Assertions.fail("Docker is required for the conformance suite in CI");
         }
         Assumptions.assumeTrue(false, "Docker is not available");
      }
      target = createTarget();
      prefix = "t/" + UUID.randomUUID() + "/";
      StorageTestSupport.setUp(target, null);
   }



   /*******************************************************************************
    ** Tear down.
    *******************************************************************************/
   @AfterEach
   void tearDownStore()
   {
      StorageTestSupport.tearDown();
   }



   /*******************************************************************************
    ** The default bucket.
    *******************************************************************************/
   protected StorageBucket files()
   {
      return (ObjectStorage.bucket("files"));
   }



   /*******************************************************************************
    ** SHA-256 hex of bytes.
    *******************************************************************************/
   private static String sha(byte[] bytes) throws Exception
   {
      return (HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
   }



   /*******************************************************************************
    ** Deterministic pseudo-random content.
    *******************************************************************************/
   private static byte[] content(int size)
   {
      byte[] b = new byte[size];
      new Random(size).nextBytes(b);
      return (b);
   }



   @Test
   void putHeadGetDeleteAcrossSizes() throws Exception
   {
      for(int size : new int[] { 0, 1234, 5 * 1024 * 1024 })
      {
         String key = prefix + "size-" + size + ".bin";
         byte[] bytes = content(size);
         PutResult put = files().put(PutRequest.of(key, bytes).withContentType("application/octet-stream"));
         assertThat(put.getBytes()).isEqualTo(size);

         Optional<StoredObject> head = files().head(key);
         assertThat(head).isPresent();
         assertThat(head.get().getSize()).isEqualTo(size);
         assertThat(head.get().getHeaders().getContentType()).isEqualTo("application/octet-stream");

         try(ObjectStream in = files().get(key, Range.all()))
         {
            assertThat(sha(in.readAllBytes())).isEqualTo(sha(bytes));
         }
         if(size > 100)
         {
            try(ObjectStream in = files().get(key, Range.bytes(10, 19)))
            {
               byte[] part = in.readAllBytes();
               assertThat(part).hasSize(10).isEqualTo(java.util.Arrays.copyOfRange(bytes, 10, 20));
            }
         }
         files().delete(DeleteRequest.of(key));
         assertThat(files().head(key)).isEmpty();
      }
   }



   @Test
   void streamedUploadAboveThePartSizeIsMultipart() throws Exception
   {
      byte[] bytes = content(20 * 1024 * 1024);
      String key = prefix + "big-20mib.bin";
      PutResult result = files().put(PutRequest.of(key, (InputStream) new ByteArrayInputStream(bytes)).withContentType("application/octet-stream"));
      assertThat(result.getMultipart()).isTrue();
      assertThat(result.getBytes()).isEqualTo(bytes.length);
      try(ObjectStream in = files().get(key, Range.all()))
      {
         assertThat(sha(in.readAllBytes())).isEqualTo(sha(bytes));
      }
   }



   @Test
   void awkwardKeysRoundTrip() throws Exception
   {
      List<String> keys = List.of(prefix + "with space/a b.txt", prefix + "plus+sign/x+y.txt", prefix + "percent%41/p%25.txt", prefix + "unicode/café-日本.txt",
         prefix + "trailing.dot.", prefix + "slashes//collapsed.txt");
      for(String key : keys)
      {
         PutResult result = files().put(PutRequest.of(key, "x".getBytes(StandardCharsets.UTF_8)));
         String normalized = result.getKey();
         assertThat(files().head(normalized)).as(normalized).isPresent();
         try(ObjectStream in = files().get(normalized, Range.all()))
         {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("x");
         }
      }
      Page<ObjectSummary> page = files().list(ListRequest.prefix(prefix));
      assertThat(page.getItems()).extracting(ObjectSummary::getKey).contains(prefix + "slashes/collapsed.txt", prefix + "with space/a b.txt", prefix + "unicode/café-日本.txt");
   }



   @Test
   void listPagesOverTwoThousandFiveHundredKeys() throws Exception
   {
      try(S3Client raw = target.rawClient())
      {
         IntStream.range(0, 2500).parallel().forEach(i -> raw.putObject(PutObjectRequest.builder().bucket(target.bucket).key(prefix + "many/" + String.format("%05d", i)).build(), RequestBody.empty()));
      }
      int pages = 0;
      int total = 0;
      String token = null;
      String last = "";
      do
      {
         Page<ObjectSummary> page = files().list(ListRequest.prefix(prefix + "many/").pageSize(1000).withAfter(token));
         pages++;
         total += page.getItems().size();
         for(ObjectSummary o : page.getItems())
         {
            assertThat(o.getKey()).isGreaterThan(last);
            last = o.getKey();
         }
         token = page.getNextToken();
      }
      while(token != null);
      assertThat(total).isEqualTo(2500);
      assertThat(pages).isEqualTo(3);
   }



   @Test
   void delimiterListingAndStartAfter() throws Exception
   {
      for(String name : new String[] { "a/1.txt", "a/2.txt", "b/1.txt", "top.txt" })
      {
         files().put(PutRequest.of(prefix + "tree/" + name, "x".getBytes(StandardCharsets.UTF_8)));
      }
      Page<ObjectSummary> page = files().list(ListRequest.prefix(prefix + "tree/").withDelimiter("/"));
      assertThat(page.getPrefixes()).containsExactly(prefix + "tree/a/", prefix + "tree/b/");
      assertThat(page.getItems()).extracting(ObjectSummary::getKey).containsExactly(prefix + "tree/top.txt");

      Page<ObjectSummary> after = files().list(ListRequest.prefix(prefix + "tree/").withStartAfter(prefix + "tree/a/2.txt"));
      assertThat(after.getItems()).extracting(ObjectSummary::getKey).containsExactly(prefix + "tree/b/1.txt", prefix + "tree/top.txt");
   }



   @Test
   void copyAndCopyInPlaceWithReplaceMetadata() throws Exception
   {
      String src = prefix + "copy-src.txt";
      files().put(PutRequest.of(src, "hello".getBytes(StandardCharsets.UTF_8)).withContentType("text/plain").withCacheControl("max-age=10").withMetadata(Map.of("alt", "one")));

      String copy = prefix + "copy-dst.txt";
      files().copy(CopyRequest.of(src, copy));
      StoredObject copied = files().head(copy).orElseThrow();
      assertThat(copied.getHeaders().getCacheControl()).isEqualTo("max-age=10");
      assertThat(copied.getHeaders().getUserMetadata()).containsEntry("alt", "one");

      files().copy(CopyRequest.of(src, src).withMetadata(MetadataDirective.REPLACE).withHeaders(new ObjectHeaders().withCacheControl("public, max-age=31536000, immutable").withContentType("text/plain")));
      StoredObject replaced = files().head(src).orElseThrow();
      assertThat(replaced.getHeaders().getCacheControl()).isEqualTo("public, max-age=31536000, immutable");
      try(ObjectStream in = files().get(src, Range.all()))
      {
         assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
      }
   }



   @Test
   void updateHeadersKeepsTheRestAndRoundTripsCacheControl() throws Exception
   {
      String key = prefix + "headers.json";
      files().put(PutRequest.of(key, "{}".getBytes(StandardCharsets.UTF_8)).withContentType("application/json").withMetadata(Map.of("owner", "me")));
      files().updateHeaders(key, ObjectHeaders.cacheControl("no-cache"));
      StoredObject o = files().head(key).orElseThrow();
      assertThat(o.getHeaders().getCacheControl()).isEqualTo("no-cache");
      assertThat(o.getHeaders().getContentType()).isEqualTo("application/json");
      assertThat(o.getHeaders().getUserMetadata()).containsEntry("owner", "me");
   }



   @Test
   void multipartCopyOfAnObjectOverTheThreshold() throws Exception
   {
      // reinstall with a low threshold so the 12 MiB copy is made in parts
      StorageTestSupport.setUp(target, c -> c.findConnection("primary").setMultipartCopyThresholdBytes(6L * 1024 * 1024));
      byte[] bytes = content(12 * 1024 * 1024);
      String src = prefix + "mp-src.bin";
      files().put(PutRequest.of(src, bytes));
      String dst = prefix + "mp-dst.bin";
      files().copy(CopyRequest.of(src, dst));
      try(ObjectStream in = files().get(dst, Range.all()))
      {
         assertThat(sha(in.readAllBytes())).isEqualTo(sha(bytes));
      }
   }



   @Test
   void purgeKeysUsesBatchDeleteWithSingleDeleteFallback() throws Exception
   {
      List<String> keys = new ArrayList<>();
      for(int i = 0; i < 5; i++)
      {
         String key = prefix + "purge/" + i;
         files().put(PutRequest.of(key, new byte[] { 1 }));
         keys.add(key);
      }
      int deleted = ObjectStorage.asSystem("conformance", "run-1").bucket("files").purgeKeys(keys);
      assertThat(deleted).isEqualTo(5);
      assertThat(files().list(ListRequest.prefix(prefix + "purge/")).getItems()).isEmpty();
   }



   @Test
   void presignedGetAndPutWorkWithAPlainHttpClient() throws Exception
   {
      String key = prefix + "presigned.txt";
      URL put = files().presignPut(PresignPut.of(key).contentType("text/plain").withTtl(Duration.ofMinutes(5)));
      HttpResponse<String> uploaded = http.send(HttpRequest.newBuilder(put.toURI()).header("Content-Type", "text/plain").PUT(HttpRequest.BodyPublishers.ofString("via presigned put")).build(),
         HttpResponse.BodyHandlers.ofString());
      assertThat(uploaded.statusCode()).as(uploaded.body()).isBetween(200, 299);

      URL get = files().presignGet(PresignGet.of(key).withTtl(Duration.ofMinutes(5)).withDownloadAs("photo.txt"));
      HttpResponse<String> downloaded = http.send(HttpRequest.newBuilder(get.toURI()).GET().build(), HttpResponse.BodyHandlers.ofString());
      assertThat(downloaded.statusCode()).isEqualTo(200);
      assertThat(downloaded.body()).isEqualTo("via presigned put");
      assertThat(downloaded.headers().firstValue("Content-Disposition")).hasValueSatisfying(v -> assertThat(v).contains("photo.txt"));
   }



   @Test
   void presignedPutPinsContentTypeAndLength() throws Exception
   {
      String key = prefix + "pinned.txt";
      URL put = files().presignPut(PresignPut.of(key).contentType("text/plain").withContentLength(5L));
      HttpResponse<String> wrongType = http.send(HttpRequest.newBuilder(put.toURI()).header("Content-Type", "image/png").PUT(HttpRequest.BodyPublishers.ofString("12345")).build(),
         HttpResponse.BodyHandlers.ofString());
      assertThat(wrongType.statusCode()).isGreaterThanOrEqualTo(400);

      HttpResponse<String> right = http.send(HttpRequest.newBuilder(put.toURI()).header("Content-Type", "text/plain").PUT(HttpRequest.BodyPublishers.ofString("12345")).build(),
         HttpResponse.BodyHandlers.ofString());
      assertThat(right.statusCode()).as(right.body()).isBetween(200, 299);
   }



   @Test
   void presignedUrlsExpire() throws Exception
   {
      String key = prefix + "expiring.txt";
      files().put(PutRequest.of(key, "x".getBytes(StandardCharsets.UTF_8)));
      URL get = files().presignGet(PresignGet.of(key).withTtl(Duration.ofSeconds(1)));
      Thread.sleep(3000);
      HttpResponse<String> r = http.send(HttpRequest.newBuilder(get.toURI()).GET().build(), HttpResponse.BodyHandlers.ofString());
      assertThat(r.statusCode()).isGreaterThanOrEqualTo(400);
   }



   @Test
   void presignedTtlIsCappedByTheBucketMaximum() throws Exception
   {
      String key = prefix + "capped.txt";
      URL get = files().presignGet(PresignGet.of(key).withTtl(Duration.ofDays(5)));
      assertThat(get.getQuery()).contains("X-Amz-Expires=3600");
   }



   @Test
   void presignedPostEnforcesTheSizeRange() throws Exception
   {
      Assumptions.assumeTrue(target.expected.get(Capability.PRESIGNED_POST) == CapabilityStatus.SUPPORTED);
      String key = prefix + "posted.txt";
      PresignedPost post = files().presignPost(new PresignPost().withKey(key).withTtl(Duration.ofMinutes(5)).withMinBytes(1L).withMaxBytes(10L));
      HttpResponse<String> posted = postForm(post, "12345");
      assertThat(posted.statusCode()).as(posted.body() + " url=" + post.getUrl()).isBetween(200, 299);
      assertThat(files().head(key)).isPresent();
      PresignedPost second = files().presignPost(new PresignPost().withKey(prefix + "toobig.txt").withTtl(Duration.ofMinutes(5)).withMinBytes(1L).withMaxBytes(10L));
      assertThat(postForm(second, "this is more than ten bytes").statusCode()).isGreaterThanOrEqualTo(400);
      assertThat(files().head(prefix + "toobig.txt")).isEmpty();
   }



   /*******************************************************************************
    ** POST a multipart/form-data upload built from a presigned POST.
    *******************************************************************************/
   private HttpResponse<String> postForm(PresignedPost post, String content) throws Exception
   {
      String boundary = "----qqq" + UUID.randomUUID();
      StringBuilder body = new StringBuilder();
      for(Map.Entry<String, String> field : post.getFields().entrySet())
      {
         body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(field.getKey()).append("\"\r\n\r\n").append(field.getValue()).append("\r\n");
      }
      body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f.txt\"\r\nContent-Type: text/plain\r\n\r\n").append(content).append("\r\n--").append(boundary).append("--\r\n");
      return (http.send(HttpRequest.newBuilder(URI.create(post.getUrl())).header("Content-Type", "multipart/form-data; boundary=" + boundary)
         .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString()));
   }



   @Test
   void conditionalWriteWhereExpected() throws Exception
   {
      Assumptions.assumeTrue(target.expected.get(Capability.CONDITIONAL_WRITE) == CapabilityStatus.SUPPORTED);
      String key = prefix + "once.txt";
      files().put(PutRequest.of(key, "1".getBytes(StandardCharsets.UTF_8)).withIfNoneMatch(true));
      assertThatThrownBy(() -> files().put(PutRequest.of(key, "2".getBytes(StandardCharsets.UTF_8)).withIfNoneMatch(true)))
         .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getReason()).isEqualTo(StorageException.Reason.PRECONDITION_FAILED));
   }



   @Test
   void bothChecksumModesWork() throws Exception
   {
      for(ChecksumMode mode : ChecksumMode.values())
      {
         StorageTestSupport.setUp(StorageTestSupport.config(target, mode));
         String key = prefix + "checksum-" + mode + ".bin";
         try
         {
            byte[] bytes = content(6 * 1024 * 1024);
            files().put(PutRequest.of(key, bytes));
            try(ObjectStream in = files().get(key, Range.all()))
            {
               assertThat(sha(in.readAllBytes())).isEqualTo(sha(bytes));
            }
            files().put(PutRequest.of(key + ".mp", (InputStream) new ByteArrayInputStream(content(20 * 1024 * 1024))));
         }
         catch(RuntimeException e)
         {
            // WHEN_REQUIRED must always work; WHEN_SUPPORTED only where the store takes SDK default checksums
            if(mode == ChecksumMode.WHEN_REQUIRED || target.checksumsSupportedWorks)
            {
               throw (e);
            }
         }
      }
   }



   @Test
   void capabilityProbeMatchesTheExpectedSetAndChangesNothing() throws Exception
   {
      String versioningBefore;
      try(S3Client raw = target.rawClient())
      {
         versioningBefore = String.valueOf(raw.getBucketVersioning(b -> b.bucket(target.bucket)).statusAsString());
      }
      CapabilityProbe.Report report = CapabilityProbe.probeConnection("primary", true);

      Map<Capability, CapabilityStatus> got = report.getByBucket().get("files");
      for(Map.Entry<Capability, CapabilityStatus> expected : target.expected.entrySet())
      {
         assertThat(got.get(expected.getKey())).as(expected.getKey().name()).isEqualTo(expected.getValue());
      }
      try(S3Client raw = target.rawClient())
      {
         assertThat(String.valueOf(raw.getBucketVersioning(b -> b.bucket(target.bucket)).statusAsString())).isEqualTo(versioningBefore);
         ListObjectsV2Response left = raw.listObjectsV2(b -> b.bucket(target.bucket).prefix(CapabilityProbe.PROBE_PREFIX));
         assertThat(left.contents()).as("probe objects are always deleted").isEmpty();
      }
      if(target.lockBucket != null)
      {
         assertThat(report.getNotes().get("locked")).anyMatch(n -> n.contains("object lock"));
      }
   }



   @Test
   void softDeleteWithATrashBucketAndRestore() throws Exception
   {
      StorageBucket soft = ObjectStorage.bucket("soft");
      String key = prefix + "trashed.txt";
      soft.put(PutRequest.of(key, "keep me".getBytes(StandardCharsets.UTF_8)));

      assertThat(soft.delete(DeleteRequest.of(key)).getMode()).isEqualTo(DeleteMode.SOFT_TRASH);
      assertThat(soft.head(key)).isEmpty();
      assertThat(ObjectStorage.bucket("trashbin").head(".trash/" + key)).isPresent();

      assertThat(soft.restore(key)).isTrue();
      try(ObjectStream in = soft.get(key, Range.all()))
      {
         assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("keep me");
      }
      assertThat(ObjectStorage.bucket("trashbin").head(".trash/" + key)).isEmpty();
   }



   @Test
   void softDeleteWithVersioningAndRestore() throws Exception
   {
      Assumptions.assumeTrue(target.versionedBucket != null, "the store has no versioning");
      StorageBucket versioned = ObjectStorage.bucket("versioned");
      String key = prefix + "versioned.txt";
      versioned.put(PutRequest.of(key, "v1".getBytes(StandardCharsets.UTF_8)));

      assertThat(versioned.delete(DeleteRequest.of(key)).getMode()).isEqualTo(DeleteMode.SOFT_DELETE_MARKER);
      assertThat(versioned.head(key)).isEmpty();

      assertThat(versioned.restore(key)).isTrue();
      assertThat(versioned.head(key)).isPresent();

      // a system caller can remove every version permanently
      DeleteMode mode = ObjectStorage.asSystem("conformance", "r1").bucket("versioned").delete(DeleteRequest.of(key).permanent()).getMode();
      assertThat(mode).isEqualTo(DeleteMode.PERMANENT);
      assertThat(versioned.head(key)).isEmpty();
      assertThat(versioned.restore(key)).isFalse();
   }



   @Test
   void permanentDeleteIsForSystemCallersOnly() throws Exception
   {
      String key = prefix + "perm.txt";
      files().put(PutRequest.of(key, "x".getBytes(StandardCharsets.UTF_8)));
      assertThatThrownBy(() -> files().delete(DeleteRequest.of(key).permanent()))
         .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getReason()).isEqualTo(StorageException.Reason.ACCESS_DENIED));
      assertThat(files().head(key)).isPresent();
   }



   @Test
   void missingObjectsAreNotFoundNotCrashes() throws Exception
   {
      assertThat(files().head(prefix + "nope")).isEmpty();
      assertThatThrownBy(() -> files().get(prefix + "nope", Range.all()))
         .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getReason()).isEqualTo(StorageException.Reason.NOT_FOUND));
   }



   @Test
   void everyWriteIsLoggedWithoutPresignedSignatures() throws Exception
   {
      String key = prefix + "logged.txt";
      files().put(PutRequest.of(key, "x".getBytes(StandardCharsets.UTF_8)));
      URL url = files().presignGet(PresignGet.of(key));
      files().delete(DeleteRequest.of(key));
      List<com.kingsrook.qqq.backend.core.model.data.QRecord> rows = com.kingsrook.qbits.objectstorage.internal.Db.query("object_storage_operation", "objectKey", key);
      assertThat(rows).extracting(r -> r.getValueString("action")).contains("PUT", "PRESIGN_GET", "DELETE");
      String signature = url.getQuery().replaceAll(".*X-Amz-Signature=([^&]+).*", "$1");
      for(com.kingsrook.qqq.backend.core.model.data.QRecord row : rows)
      {
         assertThat(String.valueOf(row.getValues())).doesNotContain(signature);
      }
   }



   /*******************************************************************************
    ** Count helper kept for subclasses.
    *******************************************************************************/
   protected static int countOf(AtomicInteger counter)
   {
      return (counter.get());
   }
}
