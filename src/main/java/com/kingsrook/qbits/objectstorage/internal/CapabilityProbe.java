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


import javax.net.ssl.SSLException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import com.kingsrook.qbits.objectstorage.api.Capability;
import com.kingsrook.qbits.objectstorage.api.CapabilityStatus;
import com.kingsrook.qbits.objectstorage.api.PresignedPost;
import com.kingsrook.qbits.objectstorage.config.AdminApiType;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.DelegatingS3Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Request;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The capability probe behind "Test connection". Bucket-setting probes are
 ** read-only Get* calls. Write probes (only when asked and the bucket is not
 ** protected) touch a single .qqq-probe/ key that is always deleted afterwards.
 ** The probe never changes bucket configuration and never throws: every
 ** unexpected result is recorded as UNKNOWN with the detail.
 *******************************************************************************/
public final class CapabilityProbe
{
   private static final QLogger LOG = QLogger.getLogger(CapabilityProbe.class);

   public static final String PROBE_PREFIX = ".qqq-probe/";
   public static final int CLOCK_SKEW_THRESHOLD_SECONDS = 900;

   private static final int MIN_PART_BYTES = 5 * 1024 * 1024;



   /*******************************************************************************
    ** The outcome of one connection test.
    *******************************************************************************/
   public static class Report
   {
      private final Map<String, Map<Capability, CapabilityStatus>> byBucket = new java.util.LinkedHashMap<>();
      private final Map<String, List<String>>                      notes    = new java.util.LinkedHashMap<>();
      private final List<String>                                   problems = new ArrayList<>();
      private Long                                                 clockSkewSeconds;
      private Boolean                                              presignHostReachable;
      private String                                               problemKind;



      /*******************************************************************************
       ** Safe connection failure category, without provider messages or identifiers.
       *******************************************************************************/
      public String getProblemKind()
      {
         return (problemKind);
      }



      /*******************************************************************************
       ** Per bucket alias, the status of each capability probed.
       *******************************************************************************/
      public Map<String, Map<Capability, CapabilityStatus>> getByBucket()
      {
         return (byBucket);
      }



      /*******************************************************************************
       ** Per bucket alias, notes (for example "write probes skipped: object lock").
       *******************************************************************************/
      public Map<String, List<String>> getNotes()
      {
         return (notes);
      }



      /*******************************************************************************
       ** Connection-level problems (auth, TLS, DNS, clock).
       *******************************************************************************/
      public List<String> getProblems()
      {
         return (problems);
      }



      /*******************************************************************************
       ** Getter for clockSkewSeconds.
       *******************************************************************************/
      public Long getClockSkewSeconds()
      {
         return (clockSkewSeconds);
      }



      /*******************************************************************************
       ** Getter for presignHostReachable.
       *******************************************************************************/
      public Boolean getPresignHostReachable()
      {
         return (presignHostReachable);
      }



      /*******************************************************************************
       ** One summary line for a bucket: "8 supported, 3 unsupported, 1 not permitted".
       *******************************************************************************/
      public String summaryFor(String alias)
      {
         Map<CapabilityStatus, Integer> counts = new EnumMap<>(CapabilityStatus.class);
         byBucket.getOrDefault(alias, Map.of()).values().forEach(s -> counts.merge(s, 1, Integer::sum));
         List<String> parts = new ArrayList<>();
         for(CapabilityStatus status : CapabilityStatus.values())
         {
            if(counts.containsKey(status))
            {
               parts.add(counts.get(status) + " " + status.name().toLowerCase().replace('_', ' '));
            }
         }
         return (parts.isEmpty() ? "nothing probed" : String.join(", ", parts));
      }
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private CapabilityProbe()
   {
   }



   /*******************************************************************************
    ** Whether a bucket gets write probes: not protected, and not a BACKUP bucket
    ** with object lock on (a probe object there could not be deleted: SF4).
    *******************************************************************************/
   public static String writeProbeSkipReason(StorageBucketConfig bucket, ConnectionRuntime connection)
   {
      if(Boolean.TRUE.equals(bucket.getProtectedBucket()))
      {
         return ("bucket is protected");
      }
      if(bucket.getRole() == BucketRole.BACKUP)
      {
         try
         {
            connection.client().getObjectLockConfiguration(b -> b.bucket(name(bucket)));
            return ("write probes skipped: object lock");
         }
         catch(RuntimeException e)
         {
            return (null);
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** Probe every bucket of a connection and record the results.
    *******************************************************************************/
   public static Report probeConnection(String connectionName, boolean includeWrite)
   {
      details.get().clear();
      Report                  report     = new Report();
      ConnectionRuntime       connection = ObjectStorageRuntime.connection(connectionName);
      StorageConnectionConfig config     = connection.getConfig();
      List<StorageBucketConfig> buckets  = new ArrayList<>();
      for(StorageBucketConfig bucket : ObjectStorageRuntime.getConfig().getBuckets())
      {
         if(connectionName.equals(bucket.getConnection()))
         {
            buckets.add(bucket);
         }
      }

      StorageBucketConfig writable = null;
      for(StorageBucketConfig bucket : buckets)
      {
         Map<Capability, CapabilityStatus> results = new EnumMap<>(Capability.class);
         report.byBucket.put(bucket.getAlias(), results);
         List<String> notes = new ArrayList<>();
         report.notes.put(bucket.getAlias(), notes);

         readProbes(connection, bucket, results, report, null);
         recordVersioningState(bucket);

         if(includeWrite)
         {
            String skip = writeProbeSkipReason(bucket, connection);
            if(skip != null)
            {
               notes.add(skip.startsWith("write probes skipped") ? skip : "write probes skipped: " + skip);
            }
            else
            {
               writeProbes(connection, bucket, results);
               if(writable == null)
               {
                  writable = bucket;
               }
            }
         }
         for(Map.Entry<Capability, CapabilityStatus> e : results.entrySet())
         {
            CapabilityStore.record(bucket, e.getKey(), e.getValue(), details.get().getOrDefault(bucket.getAlias() + ":" + e.getKey(), null));
         }
      }

      if(includeWrite && writable != null)
      {
         Map<Capability, CapabilityStatus> presign = new EnumMap<>(Capability.class);
         presignProbes(connection, writable, presign, report);
         report.byBucket.get(writable.getAlias()).putAll(presign);
         for(Map.Entry<Capability, CapabilityStatus> e : presign.entrySet())
         {
            CapabilityStore.record(writable, e.getKey(), e.getValue(), details.get().get(writable.getAlias() + ":" + e.getKey()));
         }
      }

      for(Capability admin : new Capability[] { Capability.USAGE_ADMIN_API, Capability.QUOTA_ADMIN_API })
      {
         CapabilityStatus status = config.getAdminApiType() == AdminApiType.NONE ? CapabilityStatus.UNSUPPORTED : CapabilityStatus.UNKNOWN;
         for(StorageBucketConfig bucket : buckets)
         {
            report.byBucket.get(bucket.getAlias()).put(admin, status);
         }
         if(!buckets.isEmpty())
         {
            CapabilityStore.record(buckets.get(0), admin, status, config.getAdminApiType() == AdminApiType.NONE ? "no admin API configured" : "admin API probing is not part of v1");
         }
      }
      return (report);
   }

   private static final ThreadLocal<Map<String, String>> details = ThreadLocal.withInitial(java.util.HashMap::new);



   /*******************************************************************************
    ** Reuse the read probes within a health deadline, without storing diagnostics
    ** or running write/presign probes. Only the returned report is consumed.
    *******************************************************************************/
   public static Report probeConnection(String connectionName, Instant deadline)
   {
      try
      {
         return (probeReadOnly(connectionName, deadline));
      }
      finally
      {
         details.remove();
      }
   }



   /*******************************************************************************
    ** Run the shared read-only calls without updating capability or bucket rows.
    *******************************************************************************/
   private static Report probeReadOnly(String connectionName, Instant deadline)
   {
      Report report = new Report();
      ConnectionRuntime connection = ObjectStorageRuntime.connection(connectionName);
      for(StorageBucketConfig bucket : ObjectStorageRuntime.getConfig().getBuckets())
      {
         if(!connectionName.equals(bucket.getConnection()))
         {
            continue;
         }
         if(!Instant.now().isBefore(deadline) || Thread.currentThread().isInterrupted())
         {
            report.problemKind = "timeout";
            break;
         }
         Map<Capability, CapabilityStatus> results = new EnumMap<>(Capability.class);
         report.byBucket.put(bucket.getAlias(), results);
         readProbes(connection, bucket, results, report, deadline);
      }
      if(!Instant.now().isBefore(deadline))
      {
         report.problemKind = "timeout";
      }
      return (report);
   }



   /*******************************************************************************
    ** Classify failures using types and known protocol codes, never message text.
    *******************************************************************************/
   public static String problemKind(Throwable error)
   {
      for(Throwable cause = error; cause != null; cause = cause.getCause())
      {
         if(cause instanceof UnknownHostException)
         {
            return ("dns");
         }
         if(cause instanceof SSLException)
         {
            return ("tls");
         }
         String code = ErrorMapper.errorCode(cause);
         if("RequestTimeTooSkewed".equals(code) || "RequestExpired".equals(code))
         {
            return ("clock");
         }
         if(cause instanceof S3Exception s3 && (s3.statusCode() == 401 || s3.statusCode() == 403
            || "InvalidAccessKeyId".equals(code) || "SignatureDoesNotMatch".equals(code)))
         {
            return ("auth");
         }
      }
      return ("connection");
   }



   /*******************************************************************************
    ** Bound each SDK call (including retries) by the remaining probe budget.
    *******************************************************************************/
   private static S3Client boundedClient(S3Client client, Instant deadline)
   {
      return (new DelegatingS3Client(client)
      {
         /*******************************************************************************
          ** Preserve credentials and other overrides while limiting the request.
          *******************************************************************************/
         @Override
         @SuppressWarnings("unchecked") // toBuilder retains the concrete request type.
         protected <T extends S3Request, ReturnT> ReturnT invokeOperation(T request, Function<T, ReturnT> operation)
         {
            Duration remaining = Duration.between(Instant.now(), deadline);
            if(remaining.isNegative() || remaining.isZero() || Thread.currentThread().isInterrupted())
            {
               throw (new IllegalStateException("Storage probe deadline exceeded"));
            }
            AwsRequestOverrideConfiguration.Builder overrides = request.overrideConfiguration()
               .map(AwsRequestOverrideConfiguration::toBuilder).orElseGet(AwsRequestOverrideConfiguration::builder);
            T bounded = (T) request.toBuilder().overrideConfiguration(overrides.apiCallTimeout(remaining).apiCallAttemptTimeout(remaining).build()).build();
            return (operation.apply(bounded));
         }
      });
   }



   /*******************************************************************************
    ** Show what the probe saw about versioning on the bucket row (Enabled,
    ** Suspended, Off), so the home and the record do not say "unknown".
    *******************************************************************************/
   private static void recordVersioningState(StorageBucketConfig bucket)
   {
      String seen = details.get().get(bucket.getAlias() + ":" + Capability.VERSIONING);
      if(seen == null || !Db.available(ObjectStorageTables.BUCKET))
      {
         return;
      }
      String state = seen.equals("Enabled") || seen.equals("Suspended") ? seen : seen.equals("not enabled") ? "Off" : seen.contains("stub") ? "not supported" : null;
      Integer id = ConfigSync.bucketId(bucket.getAlias());
      if(state == null || id == null)
      {
         return;
      }
      try
      {
         Db.asSystem(() -> Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", id).withValue("versioningState", state))));
      }
      catch(Exception e)
      {
         LOG.debug("Could not record the versioning state", e);
      }
   }



   /*******************************************************************************
    ** The read-only probes of one bucket.
    *******************************************************************************/
   private static void readProbes(ConnectionRuntime connection, StorageBucketConfig bucket, Map<Capability, CapabilityStatus> results, Report report, Instant deadline)
   {
      S3Client client = deadline == null ? connection.client() : boundedClient(connection.client(), deadline);
      String   name   = name(bucket);
      String   alias  = bucket.getAlias();
      String   sampleKey = null;

      try(RateLimiter.Permit ignored = deadline == null ? connection.getRateLimiter().acquire() : connection.getRateLimiter().acquire(deadline))
      {
         ListObjectsV2Response r = client.listObjectsV2(ListObjectsV2Request.builder().bucket(name).maxKeys(1).build());
         results.put(Capability.LIST_V2, CapabilityStatus.SUPPORTED);
         if(!r.contents().isEmpty())
         {
            sampleKey = r.contents().get(0).key();
         }
         r.sdkHttpResponse().firstMatchingHeader("Date").ifPresent(d ->
         {
            try
            {
               long skew = Math.abs(Duration.between(ZonedDateTime.parse(d, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(), Instant.now()).getSeconds());
               if(report.clockSkewSeconds == null || skew > report.clockSkewSeconds)
               {
                  report.clockSkewSeconds = skew;
               }
            }
            catch(RuntimeException e)
            {
               LOG.debug("Could not parse the Date header", e);
            }
         });
      }
      catch(RuntimeException e)
      {
         results.put(Capability.LIST_V2, classify(alias, Capability.LIST_V2, e));
         report.problems.add(alias + ": listing failed (" + shortMessage(e) + ")");
         report.problemKind = problemKind(e);
      }

      probe(results, alias, Capability.VERSIONING, () ->
      {
         String status = client.getBucketVersioning(b -> b.bucket(name)).statusAsString();
         if(status == null && ProviderDefaults.defaultStatus(connection.getConfig().getProviderType(), Capability.VERSIONING) == CapabilityStatus.UNSUPPORTED)
         {
            details.get().put(alias + ":" + Capability.VERSIONING, "the provider reports versioning off and documents it as a stub");
            return (CapabilityStatus.UNSUPPORTED);
         }
         details.get().put(alias + ":" + Capability.VERSIONING, status == null ? "not enabled" : status);
         return (CapabilityStatus.SUPPORTED);
      });
      probe(results, alias, Capability.OBJECT_LOCK, () ->
      {
         try
         {
            client.getObjectLockConfiguration(b -> b.bucket(name));
            return (CapabilityStatus.SUPPORTED);
         }
         catch(S3Exception e)
         {
            if("ObjectLockConfigurationNotFoundError".equals(ErrorMapper.errorCode(e)))
            {
               details.get().put(alias + ":" + Capability.OBJECT_LOCK, "object lock is not enabled on this bucket");
               return (CapabilityStatus.UNSUPPORTED);
            }
            throw (e);
         }
      });
      probe(results, alias, Capability.LIFECYCLE, () -> notConfiguredOk(connection, Capability.LIFECYCLE, "NoSuchLifecycleConfiguration", () -> client.getBucketLifecycleConfiguration(b -> b.bucket(name))));
      probe(results, alias, Capability.BUCKET_POLICY, () -> notConfiguredOk(connection, Capability.BUCKET_POLICY, "NoSuchBucketPolicy", () -> client.getBucketPolicy(b -> b.bucket(name))));
      probe(results, alias, Capability.CORS, () -> notConfiguredOk(connection, Capability.CORS, "NoSuchCORSConfiguration", () -> client.getBucketCors(b -> b.bucket(name))));
      probe(results, alias, Capability.ACL, () -> notConfiguredOk(connection, Capability.ACL, "NoSuchACL", () -> client.getBucketAcl(b -> b.bucket(name))));

      String key = sampleKey;
      if(key == null)
      {
         results.put(Capability.OBJECT_TAGGING, CapabilityStatus.UNKNOWN);
         details.get().put(alias + ":" + Capability.OBJECT_TAGGING, "no object in the bucket to test");
      }
      else
      {
         probe(results, alias, Capability.OBJECT_TAGGING, () ->
         {
            client.getObjectTagging(b -> b.bucket(name).key(key));
            return (CapabilityStatus.SUPPORTED);
         });
      }
   }



   /*******************************************************************************
    ** A bucket-setting call where "not configured" is the documented empty answer.
    ** PARTIAL when the provider's seed says the support is partial.
    *******************************************************************************/
   private static CapabilityStatus notConfiguredOk(ConnectionRuntime connection, Capability capability, String notConfiguredCode, Runnable call)
   {
      try
      {
         call.run();
      }
      catch(S3Exception e)
      {
         if(!notConfiguredCode.equals(ErrorMapper.errorCode(e)))
         {
            throw (e);
         }
      }
      return (ProviderDefaults.defaultStatus(connection.getConfig().getProviderType(), capability) == CapabilityStatus.PARTIAL ? CapabilityStatus.PARTIAL : CapabilityStatus.SUPPORTED);
   }



   /*******************************************************************************
    ** The write probes of one bucket.
    *******************************************************************************/
   private static void writeProbes(ConnectionRuntime connection, StorageBucketConfig bucket, Map<Capability, CapabilityStatus> results)
   {
      S3Client client = connection.client();
      String   name   = name(bucket);
      String   alias  = bucket.getAlias();
      String   base   = PROBE_PREFIX + UUID.randomUUID();
      List<String> cleanup = new ArrayList<>();
      try
      {
         probe(results, alias, Capability.PUT_GET_DELETE, () ->
         {
            String key = base + "/pgd";
            cleanup.add(key);
            client.putObject(PutObjectRequest.builder().bucket(name).key(key).contentType("text/plain").build(), RequestBody.fromString("probe"));
            client.headObject(HeadObjectRequest.builder().bucket(name).key(key).build());
            String body = client.getObjectAsBytes(b -> b.bucket(name).key(key)).asUtf8String();
            client.deleteObject(DeleteObjectRequest.builder().bucket(name).key(key).build());
            return ("probe".equals(body) ? CapabilityStatus.SUPPORTED : CapabilityStatus.UNKNOWN);
         });
         probe(results, alias, Capability.COPY_REPLACE_METADATA, () ->
         {
            String key = base + "/copy";
            cleanup.add(key);
            client.putObject(PutObjectRequest.builder().bucket(name).key(key).cacheControl("no-cache").build(), RequestBody.fromString("probe"));
            client.copyObject(CopyObjectRequest.builder().sourceBucket(name).sourceKey(key).destinationBucket(name).destinationKey(key)
               .metadataDirective(software.amazon.awssdk.services.s3.model.MetadataDirective.REPLACE).cacheControl("max-age=60").contentType("text/plain").build());
            String cc = client.headObject(HeadObjectRequest.builder().bucket(name).key(key).build()).cacheControl();
            return ("max-age=60".equals(cc) ? CapabilityStatus.SUPPORTED : CapabilityStatus.UNSUPPORTED);
         });
         probe(results, alias, Capability.BATCH_DELETE, () ->
         {
            String k1 = base + "/b1";
            String k2 = base + "/b2";
            cleanup.add(k1);
            cleanup.add(k2);
            client.putObject(PutObjectRequest.builder().bucket(name).key(k1).build(), RequestBody.fromString("1"));
            client.putObject(PutObjectRequest.builder().bucket(name).key(k2).build(), RequestBody.fromString("2"));
            DeleteObjectsResponse r = client.deleteObjects(DeleteObjectsRequest.builder().bucket(name)
               .delete(Delete.builder().objects(ObjectIdentifier.builder().key(k1).build(), ObjectIdentifier.builder().key(k2).build()).build()).build());
            return (r.errors().isEmpty() ? CapabilityStatus.SUPPORTED : CapabilityStatus.UNKNOWN);
         });
         probe(results, alias, Capability.MULTIPART, () ->
         {
            String key = base + "/mp";
            cleanup.add(key);
            String uploadId = client.createMultipartUpload(b -> b.bucket(name).key(key)).uploadId();
            try
            {
               String e1 = client.uploadPart(UploadPartRequest.builder().bucket(name).key(key).uploadId(uploadId).partNumber(1).build(), RequestBody.fromBytes(new byte[MIN_PART_BYTES])).eTag();
               String e2 = client.uploadPart(UploadPartRequest.builder().bucket(name).key(key).uploadId(uploadId).partNumber(2).build(), RequestBody.fromBytes(new byte[1024])).eTag();
               client.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(name).key(key).uploadId(uploadId).multipartUpload(CompletedMultipartUpload.builder()
                  .parts(CompletedPart.builder().partNumber(1).eTag(e1).build(), CompletedPart.builder().partNumber(2).eTag(e2).build()).build()).build());
               return (CapabilityStatus.SUPPORTED);
            }
            catch(RuntimeException e)
            {
               try
               {
                  client.abortMultipartUpload(b -> b.bucket(name).key(key).uploadId(uploadId));
               }
               catch(RuntimeException ignored)
               {
                  LOG.debug("abort after a failed multipart probe", ignored);
               }
               throw (e);
            }
         });
         probe(results, alias, Capability.CONDITIONAL_WRITE, () ->
         {
            String key = base + "/cond";
            cleanup.add(key);
            client.putObject(PutObjectRequest.builder().bucket(name).key(key).build(), RequestBody.fromString("1"));
            try
            {
               client.putObject(PutObjectRequest.builder().bucket(name).key(key).ifNoneMatch("*").build(), RequestBody.fromString("2"));
               details.get().put(alias + ":" + Capability.CONDITIONAL_WRITE, "If-None-Match was ignored");
               return (CapabilityStatus.UNSUPPORTED);
            }
            catch(S3Exception e)
            {
               if(e.statusCode() == 412)
               {
                  return (CapabilityStatus.SUPPORTED);
               }
               throw (e);
            }
         });
      }
      finally
      {
         for(String key : cleanup)
         {
            try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
            {
               client.deleteObject(DeleteObjectRequest.builder().bucket(name).key(key).build());
            }
            catch(RuntimeException e)
            {
               LOG.warn("Could not delete a probe object", e, logPair("key", key));
            }
         }
      }
   }



   /*******************************************************************************
    ** The presign probes: presign, then a plain HTTP request to the URL.
    *******************************************************************************/
   private static void presignProbes(ConnectionRuntime connection, StorageBucketConfig bucket, Map<Capability, CapabilityStatus> results, Report report)
   {
      String name  = name(bucket);
      String alias = bucket.getAlias();
      String key   = PROBE_PREFIX + UUID.randomUUID() + "/presign";
      HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
      try
      {
         probe(results, alias, Capability.PRESIGNED_PUT, () ->
         {
            String url = connection.presigner().presignPutObject(PutObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(2))
               .putObjectRequest(b -> b.bucket(name).key(key).contentType("text/plain")).build()).url().toString();
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "text/plain").PUT(HttpRequest.BodyPublishers.ofString("probe")).build(),
               HttpResponse.BodyHandlers.ofString());
            report.presignHostReachable = true;
            return (httpStatus(alias, Capability.PRESIGNED_PUT, r.statusCode(), r.body()));
         });
         probe(results, alias, Capability.PRESIGNED_GET, () ->
         {
            String url = connection.presigner().presignGetObject(GetObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(2))
               .getObjectRequest(b -> b.bucket(name).key(key)).build()).url().toString();
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
            report.presignHostReachable = true;
            if(r.statusCode() == 200 && !"probe".equals(r.body()))
            {
               details.get().put(alias + ":" + Capability.PRESIGNED_GET, "the presigned GET returned unexpected content (does it reach the same store?)");
               return (CapabilityStatus.UNKNOWN);
            }
            return (httpStatus(alias, Capability.PRESIGNED_GET, r.statusCode(), r.body()));
         });
         probe(results, alias, Capability.PRESIGNED_POST, () ->
         {
            String[] creds = connection.presignCredentials();
            PresignedPost post = PostPolicySigner.sign(creds[0], creds[1], creds[2], creds[3], connection.getConfig().effectiveAddressingStyle(), name, key + "-post",
               null, 0L, 1024L, Instant.now(), Duration.ofMinutes(2));
            String boundary = "----qqqprobe" + UUID.randomUUID();
            StringBuilder body = new StringBuilder();
            for(Map.Entry<String, String> field : post.getFields().entrySet())
            {
               body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(field.getKey()).append("\"\r\n\r\n").append(field.getValue()).append("\r\n");
            }
            body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"probe.txt\"\r\nContent-Type: text/plain\r\n\r\nprobe\r\n--").append(boundary).append("--\r\n");
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(post.getUrl())).header("Content-Type", "multipart/form-data; boundary=" + boundary)
               .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            return (httpStatus(alias, Capability.PRESIGNED_POST, r.statusCode(), r.body()));
         });
      }
      finally
      {
         for(String k : new String[] { key, key + "-post" })
         {
            try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
            {
               connection.client().deleteObject(DeleteObjectRequest.builder().bucket(name).key(k).build());
            }
            catch(RuntimeException e)
            {
               LOG.debug("Could not delete a presign probe object", e);
            }
         }
      }
      if(results.get(Capability.PRESIGNED_PUT) == CapabilityStatus.UNKNOWN && report.presignHostReachable == null)
      {
         report.presignHostReachable = false;
         report.problems.add(alias + ": the presign host could not be reached");
      }
   }



   /*******************************************************************************
    ** Map an HTTP status from a presigned request.
    *******************************************************************************/
   private static CapabilityStatus httpStatus(String alias, Capability capability, int status, String body)
   {
      if(status >= 200 && status < 300)
      {
         return (CapabilityStatus.SUPPORTED);
      }
      details.get().put(alias + ":" + capability, "HTTP " + status + (body == null ? "" : " " + body.replaceAll("\\s+", " ").substring(0, Math.min(200, body.length()))));
      if(status == 403)
      {
         return (CapabilityStatus.NOT_PERMITTED);
      }
      if(status == 501 || status == 405)
      {
         return (CapabilityStatus.UNSUPPORTED);
      }
      return (CapabilityStatus.UNKNOWN);
   }



   /*******************************************************************************
    ** Something a probe does.
    *******************************************************************************/
   @FunctionalInterface
   private interface ProbeCall
   {
      /*******************************************************************************
       ** Do it.
       *******************************************************************************/
      CapabilityStatus run() throws Exception;
   }



   /*******************************************************************************
    ** Run one probe, mapping any failure to a status. Never throws.
    *******************************************************************************/
   private static void probe(Map<Capability, CapabilityStatus> results, String alias, Capability capability, ProbeCall call)
   {
      try
      {
         results.put(capability, call.run());
      }
      catch(Exception e)
      {
         results.put(capability, classify(alias, capability, e));
      }
   }



   /*******************************************************************************
    ** Map a failure to a capability status.
    *******************************************************************************/
   static CapabilityStatus classify(String alias, Capability capability, Exception e)
   {
      String detail = shortMessage(e);
      details.get().put(alias + ":" + capability, detail);
      if(e instanceof S3Exception s3)
      {
         String code = ErrorMapper.errorCode(s3);
         String lower = (code == null ? "" : code.toLowerCase()) + " " + (s3.getMessage() == null ? "" : s3.getMessage().toLowerCase());
         if(s3.statusCode() == 403 || "AccessDenied".equals(code))
         {
            return (CapabilityStatus.NOT_PERMITTED);
         }
         if(s3.statusCode() == 501 || s3.statusCode() == 405 || lower.contains("notimplemented") || lower.contains("not implemented") || lower.contains("not supported")
            || lower.contains("unsupported"))
         {
            return (CapabilityStatus.UNSUPPORTED);
         }
      }
      return (CapabilityStatus.UNKNOWN);
   }



   /*******************************************************************************
    ** A short description of a failure (no secrets: SDK messages never carry them).
    *******************************************************************************/
   private static String shortMessage(Throwable e)
   {
      String code = ErrorMapper.errorCode(e);
      String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      String text = (code == null ? "" : code + ": ") + message;
      return (text.length() > 300 ? text.substring(0, 300) : text);
   }



   /*******************************************************************************
    ** The bucket name on the provider.
    *******************************************************************************/
   private static String name(StorageBucketConfig bucket)
   {
      return (SecretRefs.resolveSetting(bucket.getBucketName()));
   }
}
