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


import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qbits.objectstorage.api.StorageException.Reason;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.internal.CapabilityStore;
import com.kingsrook.qbits.objectstorage.internal.ConnectionRuntime;
import com.kingsrook.qbits.objectstorage.internal.ErrorMapper;
import com.kingsrook.qbits.objectstorage.internal.IndexHook;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.internal.OperationLog;
import com.kingsrook.qbits.objectstorage.internal.PostPolicySigner;
import com.kingsrook.qbits.objectstorage.internal.ProtectionRules;
import com.kingsrook.qbits.objectstorage.internal.RateLimiter;
import com.kingsrook.qqq.backend.core.actions.permissions.CorePermissionEnforcer;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartCopyRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** One bucket alias, as a typed storage API: put, get, head, copy, delete,
 ** restore, list, header and metadata changes, presigned URLs and public URLs.
 **
 ** All calls are synchronous, use the connection's cached client and rate
 ** limiter, and throw StorageException with a stable reason (never a raw SDK
 ** exception). Every write is logged to object_storage_operation, applies the
 ** header policy unless headers are given, updates the index (write-through) and
 ** then runs the change listeners, which never fail the write.
 *******************************************************************************/
public final class StorageBucket
{
   private static final QLogger LOG = QLogger.getLogger(StorageBucket.class);

   private static final Map<String, long[]> PRESIGN_COUNTS = new ConcurrentHashMap<>();

   private final StorageBucketConfig bucket;
   private final ConnectionRuntime   connection;
   private final StorageCaller       caller;

   private volatile long    versioningCheckedAt = 0;
   private volatile boolean versioningEnabled   = false;



   /*******************************************************************************
    ** Constructor (use ObjectStorage.bucket(alias)).
    *******************************************************************************/
   StorageBucket(String alias, StorageCaller caller)
   {
      this.bucket = ObjectStorageRuntime.bucketConfig(alias);
      this.connection = ObjectStorageRuntime.connection(bucket.getConnection());
      this.caller = caller;
   }



   /*******************************************************************************
    ** The alias.
    *******************************************************************************/
   public String alias()
   {
      return (bucket.getAlias());
   }



   /*******************************************************************************
    ** The largest object this bucket accepts on upload, in bytes, or null when
    ** the bucket sets no limit.  Callers outside this qbit use this instead of
    ** config(), which returns an internal type.
    *******************************************************************************/
   public Long maxUploadBytes()
   {
      return (bucket.getMaxUploadBytes());
   }



   /*******************************************************************************
    ** The bucket configuration.
    *******************************************************************************/
   public StorageBucketConfig config()
   {
      return (bucket);
   }



   /*******************************************************************************
    ** The status of a capability for this bucket.
    *******************************************************************************/
   public CapabilityStatus capability(Capability capability)
   {
      return (CapabilityStore.status(bucket.getAlias(), capability));
   }



   /*******************************************************************************
    ** The real bucket name on the provider.
    *******************************************************************************/
   private String name()
   {
      return (com.kingsrook.qbits.objectstorage.internal.SecretRefs.resolveSetting(bucket.getBucketName()));
   }



   // ----------------------------------------------------------------- put

   /*******************************************************************************
    ** Put an object. Headers come from the header policy unless the request gives
    ** them. A stream is buffered one part at a time and sent as multipart above
    ** the part size (default 16 MB).
    *******************************************************************************/
   public PutResult put(PutRequest request)
   {
      checkPermission("object_storage.write");
      String key = StorageKeys.normalize(request.getKey());
      guardWrite(key, ProtectionRules.Op.WRITE, "put");
      if(request.getLockMode() != null && CapabilityStore.ruledOut(capability(Capability.OBJECT_LOCK)))
      {
         throw (new StorageException(Reason.UNSUPPORTED, "Object lock is not supported for bucket " + alias() + " (" + capability(Capability.OBJECT_LOCK) + ")"));
      }

      ObjectHeaders headers = headersForPut(request, key);
      OperationLog.Entry log = new OperationLog.Entry(caller, "PUT", alias()).key(key);
      try
      {
         PutResult result = doPut(request, key, headers);
         log.bytes(result.getBytes()).requestId(result.getRequestId());
         StoredObject stored = new StoredObject().withBucketAlias(alias()).withKey(key).withSize(result.getBytes()).withEtag(result.getEtag())
            .withLastModified(ObjectStorageRuntime.clock().instant()).withHeaders(headers).withVersionId(result.getVersionId());
         log.detail(afterWrite(ChangeOperation.PUT, key, null, stored));
         log.write();
         return (result);
      }
      catch(RuntimeException e)
      {
         StorageException se = ErrorMapper.map(e, "put " + key);
         log.failed(se.getMessage()).requestId(se.getRequestId()).write();
         throw (se);
      }
   }



   /*******************************************************************************
    ** The headers to store: the request's own, else the policy's (BACKUP buckets
    ** never get policy headers).
    *******************************************************************************/
   private ObjectHeaders headersForPut(PutRequest request, String key)
   {
      String contentType = StringUtils.hasContent(request.getContentType()) ? request.getContentType() : guessContentType(key);
      ObjectHeaders headers = new ObjectHeaders().withContentType(contentType).withCacheControl(request.getCacheControl())
         .withContentDisposition(request.getContentDisposition()).withUserMetadata(request.getMetadata());
      if(bucket.getRole() != BucketRole.BACKUP && headers.getCacheControl() == null)
      {
         ObjectHeaders fromPolicy = ObjectStorageRuntime.headerPolicy().headersFor(bucket, key, contentType);
         if(fromPolicy != null)
         {
            headers.setCacheControl(fromPolicy.getCacheControl());
            if(headers.getContentDisposition() == null)
            {
               headers.setContentDisposition(fromPolicy.getContentDisposition());
            }
         }
      }
      return (headers);
   }



   /*******************************************************************************
    ** The put itself (single request or multipart).
    *******************************************************************************/
   private PutResult doPut(PutRequest request, String key, ObjectHeaders headers)
   {
      S3Client client   = connection.client();
      long     partSize = Math.max(5, connection.getConfig().getMultipartPartSizeMb()) * 1024L * 1024L;

      byte[] first;
      InputStream rest = null;
      if(request.getBytes() != null)
      {
         first = request.getBytes();
      }
      else
      {
         try
         {
            InputStream in = request.getStream();
            first = in.readNBytes((int) partSize + 1);
            if(first.length > partSize)
            {
               rest = in;
            }
         }
         catch(IOException e)
         {
            throw (new StorageException(Reason.PROVIDER_ERROR, "Could not read the upload stream: " + e.getMessage(), null, null, e));
         }
      }

      if(rest == null)
      {
         PutObjectRequest.Builder b = PutObjectRequest.builder().bucket(name()).key(key);
         applyPutHeaders(b, headers, request);
         if(Boolean.TRUE.equals(request.getIfNoneMatch()))
         {
            b.ifNoneMatch("*");
         }
         try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
         {
            PutObjectResponse response = client.putObject(b.build(), RequestBody.fromBytes(first));
            return (new PutResult().withKey(key).withEtag(response.eTag()).withVersionId(response.versionId()).withBytes((long) first.length)
               .withRequestId(response.responseMetadata().requestId()).withAppliedHeaders(headers));
         }
      }
      return (multipartPut(client, key, headers, request, first, rest, partSize));
   }



   /*******************************************************************************
    ** Apply headers, metadata, lock and storage class to a put builder.
    *******************************************************************************/
   private static void applyPutHeaders(PutObjectRequest.Builder b, ObjectHeaders headers, PutRequest request)
   {
      if(headers.getContentType() != null)
      {
         b.contentType(headers.getContentType());
      }
      if(headers.getCacheControl() != null)
      {
         b.cacheControl(headers.getCacheControl());
      }
      if(headers.getContentDisposition() != null)
      {
         b.contentDisposition(headers.getContentDisposition());
      }
      if(headers.getUserMetadata() != null && !headers.getUserMetadata().isEmpty())
      {
         b.metadata(headers.getUserMetadata());
      }
      if(request.getStorageClass() != null)
      {
         b.storageClass(request.getStorageClass());
      }
      if(request.getLockMode() != null)
      {
         b.objectLockMode(request.getLockMode().name());
         b.objectLockRetainUntilDate(request.getLockRetainUntil());
      }
   }



   /*******************************************************************************
    ** Multipart upload, one part buffered at a time; aborted on any failure.
    *******************************************************************************/
   private PutResult multipartPut(S3Client client, String key, ObjectHeaders headers, PutRequest request, byte[] first, InputStream rest, long partSize)
   {
      CreateMultipartUploadRequest.Builder cb = CreateMultipartUploadRequest.builder().bucket(name()).key(key);
      if(headers.getContentType() != null)
      {
         cb.contentType(headers.getContentType());
      }
      if(headers.getCacheControl() != null)
      {
         cb.cacheControl(headers.getCacheControl());
      }
      if(headers.getContentDisposition() != null)
      {
         cb.contentDisposition(headers.getContentDisposition());
      }
      if(headers.getUserMetadata() != null && !headers.getUserMetadata().isEmpty())
      {
         cb.metadata(headers.getUserMetadata());
      }
      if(request.getStorageClass() != null)
      {
         cb.storageClass(request.getStorageClass());
      }
      if(request.getLockMode() != null)
      {
         cb.objectLockMode(request.getLockMode().name());
         cb.objectLockRetainUntilDate(request.getLockRetainUntil());
      }

      String uploadId = null;
      try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
      {
         CreateMultipartUploadResponse created = client.createMultipartUpload(cb.build());
         uploadId = created.uploadId();
         List<CompletedPart> parts = new ArrayList<>();
         long total = 0;
         int  partNumber = 1;
         byte[] chunk = first;
         while(chunk.length > 0)
         {
            var response = client.uploadPart(UploadPartRequest.builder().bucket(name()).key(key).uploadId(uploadId).partNumber(partNumber).build(), RequestBody.fromBytes(chunk));
            parts.add(CompletedPart.builder().partNumber(partNumber).eTag(response.eTag()).build());
            total += chunk.length;
            partNumber++;
            chunk = rest == null ? new byte[0] : rest.readNBytes((int) partSize);
         }
         var done = client.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(name()).key(key).uploadId(uploadId)
            .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build()).build());
         return (new PutResult().withKey(key).withEtag(done.eTag()).withVersionId(done.versionId()).withBytes(total).withMultipart(true)
            .withRequestId(done.responseMetadata().requestId()).withAppliedHeaders(headers));
      }
      catch(IOException e)
      {
         abort(client, key, uploadId);
         throw (new StorageException(Reason.PROVIDER_ERROR, "Could not read the upload stream: " + e.getMessage(), null, null, e));
      }
      catch(RuntimeException e)
      {
         abort(client, key, uploadId);
         throw (ErrorMapper.map(e, "multipart put " + key));
      }
   }



   /*******************************************************************************
    ** Abort a multipart upload, quietly.
    *******************************************************************************/
   private void abort(S3Client client, String key, String uploadId)
   {
      if(uploadId != null)
      {
         try
         {
            client.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(name()).key(key).uploadId(uploadId).build());
         }
         catch(RuntimeException e)
         {
            LOG.warn("Could not abort a multipart upload", e, logPair("key", key));
         }
      }
   }



   // ------------------------------------------------------------ head/get

   /*******************************************************************************
    ** HEAD an object: its size, ETag, headers and metadata, or empty when absent.
    *******************************************************************************/
   public Optional<StoredObject> head(String rawKey)
   {
      checkPermission("object_storage.view");
      String key = StorageKeys.normalize(rawKey);
      try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
      {
         HeadObjectResponse h = connection.client().headObject(HeadObjectRequest.builder().bucket(name()).key(key).build());
         return (Optional.of(new StoredObject().withBucketAlias(alias()).withKey(key).withSize(h.contentLength()).withEtag(h.eTag())
            .withLastModified(h.lastModified()).withVersionId(h.versionId()).withStorageClass(h.storageClassAsString())
            .withLockMode(h.objectLockModeAsString()).withLockRetainUntil(h.objectLockRetainUntilDate())
            .withHeaders(new ObjectHeaders().withCacheControl(h.cacheControl()).withContentType(h.contentType())
               .withContentDisposition(h.contentDisposition()).withContentEncoding(h.contentEncoding()).withUserMetadata(h.metadata()))));
      }
      catch(S3Exception e)
      {
         if(e.statusCode() == 404)
         {
            return (Optional.empty());
         }
         throw (ErrorMapper.map(e, "head " + key));
      }
      catch(RuntimeException e)
      {
         throw (ErrorMapper.map(e, "head " + key));
      }
   }



   /*******************************************************************************
    ** Get an object's content (all of it, or a byte range). Close the result.
    *******************************************************************************/
   public ObjectStream get(String rawKey, Range range)
   {
      checkPermission("object_storage.view");
      String key = StorageKeys.normalize(rawKey);
      GetObjectRequest.Builder b = GetObjectRequest.builder().bucket(name()).key(key);
      if(range != null && range.getFrom() != null)
      {
         b.range("bytes=" + range.getFrom() + "-" + (range.getTo() == null ? "" : range.getTo()));
      }
      try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
      {
         ResponseInputStream<GetObjectResponse> in = connection.client().getObject(b.build());
         GetObjectResponse r = in.response();
         return (new ObjectStream(in, r.contentLength(), r.contentType(), r.eTag(), r.lastModified(), new ObjectHeaders().withCacheControl(r.cacheControl())
            .withContentType(r.contentType()).withContentDisposition(r.contentDisposition()).withUserMetadata(r.metadata())));
      }
      catch(RuntimeException e)
      {
         throw (ErrorMapper.map(e, "get " + key));
      }
   }



   // ---------------------------------------------------------------- copy

   /*******************************************************************************
    ** Copy an object (within the bucket, or to another bucket alias). With
    ** MetadataDirective.REPLACE the given headers replace the source's; a
    ** copy-in-place with REPLACE changes Last-Modified and can change the ETag.
    ** Objects over the connection's multipart-copy threshold are copied in parts.
    *******************************************************************************/
   public void copy(CopyRequest request)
   {
      checkPermission("object_storage.write");
      String src  = StorageKeys.normalize(request.getSourceKey());
      String dest = StorageKeys.normalize(request.getDestinationKey());
      String destAlias = StringUtils.hasContent(request.getDestinationBucket()) ? request.getDestinationBucket() : alias();
      StorageBucketConfig destConfig = ObjectStorageRuntime.bucketConfig(destAlias);
      guardWrite(dest, destAlias, ProtectionRules.Op.WRITE, "copy to");
      OperationLog.Entry log = new OperationLog.Entry(caller, "COPY", alias()).key(src).destination(dest);
      try
      {
         if(!destConfig.getConnection().equals(bucket.getConnection()))
         {
            copyAcrossConnections(src, dest, destAlias, request);
         }
         else
         {
            doCopy(src, dest, destConfig, request);
         }
         StorageBucket destBucket = destAlias.equals(alias()) ? this : new StorageBucket(destAlias, caller);
         StoredObject  stored     = destBucket.headQuietly(dest);
         log.bytes(stored == null ? null : stored.getSize());
         log.detail(afterWrite(ChangeOperation.COPY, destAlias, src, dest, stored));
         log.write();
      }
      catch(RuntimeException e)
      {
         StorageException se = ErrorMapper.map(e, "copy " + src);
         log.failed(se.getMessage()).requestId(se.getRequestId()).write();
         throw (se);
      }
   }



   /*******************************************************************************
    ** The server-side copy (single request, or parts for very large objects).
    *******************************************************************************/
   private void doCopy(String src, String dest, StorageBucketConfig destConfig, CopyRequest request)
   {
      S3Client client = connection.client();
      String   destBucketName = com.kingsrook.qbits.objectstorage.internal.SecretRefs.resolveSetting(destConfig.getBucketName());
      try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
      {
         HeadObjectResponse head = client.headObject(HeadObjectRequest.builder().bucket(name()).key(src).build());
         long threshold = connection.getConfig().getMultipartCopyThresholdBytes();
         if(head.contentLength() != null && head.contentLength() > threshold)
         {
            multipartCopy(client, src, dest, destBucketName, head, request);
            return;
         }
         CopyObjectRequest.Builder b = CopyObjectRequest.builder().sourceBucket(name()).sourceKey(src).destinationBucket(destBucketName).destinationKey(dest);
         if(request.getDirective() == MetadataDirective.REPLACE)
         {
            b.metadataDirective(software.amazon.awssdk.services.s3.model.MetadataDirective.REPLACE);
            ObjectHeaders h = request.getHeaders() == null ? new ObjectHeaders() : request.getHeaders();
            b.contentType(h.getContentType() != null ? h.getContentType() : head.contentType());
            b.cacheControl(h.getCacheControl());
            b.contentDisposition(h.getContentDisposition());
            b.contentEncoding(h.getContentEncoding() != null ? h.getContentEncoding() : head.contentEncoding());
            b.metadata(h.getUserMetadata() != null ? h.getUserMetadata() : head.metadata());
         }
         client.copyObject(b.build());
      }
   }



   /*******************************************************************************
    ** A multipart (UploadPartCopy) copy for objects over the single-copy limit.
    *******************************************************************************/
   private void multipartCopy(S3Client client, String src, String dest, String destBucketName, HeadObjectResponse head, CopyRequest request)
   {
      ObjectHeaders h = request.getDirective() == MetadataDirective.REPLACE && request.getHeaders() != null ? request.getHeaders() : new ObjectHeaders();
      CreateMultipartUploadRequest.Builder cb = CreateMultipartUploadRequest.builder().bucket(destBucketName).key(dest)
         .contentType(h.getContentType() != null ? h.getContentType() : head.contentType())
         .cacheControl(request.getDirective() == MetadataDirective.REPLACE ? h.getCacheControl() : head.cacheControl())
         .contentDisposition(request.getDirective() == MetadataDirective.REPLACE ? h.getContentDisposition() : head.contentDisposition())
         .metadata(h.getUserMetadata() != null ? h.getUserMetadata() : head.metadata());
      String uploadId = client.createMultipartUpload(cb.build()).uploadId();
      try
      {
         long partSize = Math.max(5, connection.getConfig().getMultipartPartSizeMb()) * 1024L * 1024L;
         long size = head.contentLength();
         List<CompletedPart> parts = new ArrayList<>();
         int  n = 1;
         for(long start = 0; start < size; start += partSize)
         {
            long end = Math.min(size - 1, start + partSize - 1);
            var r = client.uploadPartCopy(UploadPartCopyRequest.builder().sourceBucket(name()).sourceKey(src).destinationBucket(destBucketName).destinationKey(dest)
               .uploadId(uploadId).partNumber(n).copySourceRange("bytes=" + start + "-" + end).build());
            parts.add(CompletedPart.builder().partNumber(n).eTag(r.copyPartResult().eTag()).build());
            n++;
         }
         client.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(destBucketName).key(dest).uploadId(uploadId)
            .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build()).build());
      }
      catch(RuntimeException e)
      {
         client.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(destBucketName).key(dest).uploadId(uploadId).build());
         throw (e);
      }
   }



   /*******************************************************************************
    ** A copy between two connections: stream it through the app server.
    *******************************************************************************/
   private void copyAcrossConnections(String src, String dest, String destAlias, CopyRequest request)
   {
      try(ObjectStream in = get(src, Range.all()))
      {
         ObjectHeaders h = request.getDirective() == MetadataDirective.REPLACE && request.getHeaders() != null ? request.getHeaders() : in.getHeaders();
         new StorageBucket(destAlias, caller).put(PutRequest.of(dest, in.getInputStream()).withContentType(h.getContentType()).withCacheControl(h.getCacheControl())
            .withContentDisposition(h.getContentDisposition()).withMetadata(h.getUserMetadata()));
      }
      catch(IOException e)
      {
         throw (new StorageException(Reason.PROVIDER_ERROR, "Could not copy across connections: " + e.getMessage(), null, null, e));
      }
   }



   /*******************************************************************************
    ** Change an object's headers in place (copy onto itself with REPLACE). Fields
    ** not given keep their current values.
    *******************************************************************************/
   public void updateHeaders(String rawKey, ObjectHeaders changes)
   {
      checkPermission("object_storage.write");
      String key = StorageKeys.normalize(rawKey);
      guardWrite(key, ProtectionRules.Op.WRITE, "change headers of");
      OperationLog.Entry log = new OperationLog.Entry(caller, "HEADERS", alias()).key(key);
      try
      {
         StoredObject current = head(key).orElseThrow(() -> new StorageException(Reason.NOT_FOUND, "No such object: " + key));
         ObjectHeaders merged = new ObjectHeaders()
            .withCacheControl(changes.getCacheControl() != null ? changes.getCacheControl() : current.getHeaders().getCacheControl())
            .withContentType(changes.getContentType() != null ? changes.getContentType() : current.getHeaders().getContentType())
            .withContentDisposition(changes.getContentDisposition() != null ? changes.getContentDisposition() : current.getHeaders().getContentDisposition())
            .withContentEncoding(changes.getContentEncoding() != null ? changes.getContentEncoding() : current.getHeaders().getContentEncoding())
            .withUserMetadata(changes.getUserMetadata() != null ? changes.getUserMetadata() : current.getHeaders().getUserMetadata());
         doCopy(key, key, bucket, CopyRequest.of(key, key).withMetadata(MetadataDirective.REPLACE).withHeaders(merged));
         StoredObject stored = headQuietly(key);
         log.bytes(stored == null ? null : stored.getSize());
         log.detail(afterWrite(ChangeOperation.HEADERS, key, null, stored));
         log.write();
      }
      catch(RuntimeException e)
      {
         StorageException se = ErrorMapper.map(e, "change headers of " + key);
         log.failed(se.getMessage()).requestId(se.getRequestId()).write();
         throw (se);
      }
   }



   // -------------------------------------------------------------- delete

   /*******************************************************************************
    ** Delete an object. Versioned bucket: creates a delete marker. Otherwise, with
    ** trash configured: moves it to the private trash location. Otherwise it is
    ** permanent and needs object_storage.admin (or a system caller). With
    ** permanent(): removes every version and delete marker (system callers only;
    ** objects under object-lock retention are skipped and the result says so).
    *******************************************************************************/
   public DeleteResult delete(DeleteRequest request)
   {
      String key = StorageKeys.normalize(request.getKey());
      boolean permanent = Boolean.TRUE.equals(request.getPermanent());
      if(permanent && !caller.isSystem())
      {
         throw (new StorageException(Reason.ACCESS_DENIED, "A permanent delete of every version is available to system callers only"));
      }
      if(!permanent)
      {
         checkPermission("object_storage.delete");
      }
      guardWrite(key, ProtectionRules.Op.DELETE, "delete");
      OperationLog.Entry log = new OperationLog.Entry(caller, "DELETE", alias()).key(key);
      try
      {
         DeleteResult result = permanent ? deleteEverything(key) : softDelete(key, request);
         log.detail(result.getMode() + (result.getDetail() == null ? "" : ": " + result.getDetail()));
         if(result.getMode() == DeleteMode.SKIPPED_OBJECT_LOCK)
         {
            log.skipped("object lock retention");
         }
         else if(result.getMode() != DeleteMode.NOT_FOUND)
         {
            String listenerNote = afterDelete(key, result.getMode() == DeleteMode.SOFT_DELETE_MARKER || result.getMode() == DeleteMode.SOFT_TRASH);
            if(listenerNote != null)
            {
               log.detail(result.getMode() + "; " + listenerNote);
            }
         }
         log.write();
         return (result);
      }
      catch(RuntimeException e)
      {
         StorageException se = ErrorMapper.map(e, "delete " + key);
         log.failed(se.getMessage()).requestId(se.getRequestId()).write();
         throw (se);
      }
   }



   /*******************************************************************************
    ** Soft delete by bucket state; permanent only when allowed.
    *******************************************************************************/
   private DeleteResult softDelete(String key, DeleteRequest request)
   {
      S3Client client = connection.client();
      if(isVersioned())
      {
         try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
         {
            DeleteObjectResponse r = client.deleteObject(DeleteObjectRequest.builder().bucket(name()).key(key).build());
            return (new DeleteResult().withKey(key).withMode(DeleteMode.SOFT_DELETE_MARKER).withDetail("delete marker " + r.versionId()));
         }
      }
      if(Boolean.TRUE.equals(bucket.getTrashEnabled()))
      {
         String trashAlias = StringUtils.hasContent(bucket.getTrashBucket()) ? bucket.getTrashBucket() : alias();
         String trashKey   = bucket.getTrashPrefix() + key;
         StorageBucketConfig trashConfig = ObjectStorageRuntime.bucketConfig(trashAlias);
         if(!trashConfig.getConnection().equals(bucket.getConnection()))
         {
            throw (new StorageException(Reason.UNSUPPORTED, "The trash bucket must be on the same connection"));
         }
         if(headQuietly(key) == null)
         {
            return (new DeleteResult().withKey(key).withMode(DeleteMode.NOT_FOUND));
         }
         doCopy(key, trashKey, trashConfig, CopyRequest.of(key, trashKey));
         try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
         {
            client.deleteObject(DeleteObjectRequest.builder().bucket(name()).key(key).build());
         }
         return (new DeleteResult().withKey(key).withMode(DeleteMode.SOFT_TRASH).withDetail("moved to " + trashAlias + "/" + trashKey));
      }
      checkPermission("object_storage.admin");
      try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
      {
         client.deleteObject(DeleteObjectRequest.builder().bucket(name()).key(key).build());
      }
      return (new DeleteResult().withKey(key).withMode(DeleteMode.PERMANENT).withDetail("permanent"));
   }



   /*******************************************************************************
    ** Remove every version and delete marker of a key.
    *******************************************************************************/
   private DeleteResult deleteEverything(String key)
   {
      S3Client client = connection.client();
      int deleted = 0;
      int locked  = 0;
      List<String[]> versions = new ArrayList<>();
      boolean listed = false;
      try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
      {
         ListObjectVersionsResponse r = client.listObjectVersions(ListObjectVersionsRequest.builder().bucket(name()).prefix(key).build());
         for(ObjectVersion v : r.versions())
         {
            if(v.key().equals(key))
            {
               versions.add(new String[] { v.versionId() });
            }
         }
         for(DeleteMarkerEntry m : r.deleteMarkers())
         {
            if(m.key().equals(key))
            {
               versions.add(new String[] { m.versionId() });
            }
         }
         listed = true;
      }
      catch(S3Exception e)
      {
         if(e.statusCode() != 501 && e.statusCode() != 405 && e.statusCode() != 400)
         {
            throw (e);
         }
      }
      if(!listed || versions.isEmpty())
      {
         boolean existed = headQuietly(key) != null;
         try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
         {
            client.deleteObject(DeleteObjectRequest.builder().bucket(name()).key(key).build());
         }
         return (new DeleteResult().withKey(key).withMode(existed ? DeleteMode.PERMANENT : DeleteMode.NOT_FOUND).withVersionsDeleted(existed ? 1 : 0));
      }
      for(String[] v : versions)
      {
         try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
         {
            client.deleteObject(DeleteObjectRequest.builder().bucket(name()).key(key).versionId(v[0]).build());
            deleted++;
         }
         catch(S3Exception e)
         {
            String text = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if(e.statusCode() == 403 || "AccessDenied".equals(ErrorMapper.errorCode(e)) || text.contains("worm") || text.contains("object lock") || text.contains("retention"))
            {
               locked++;
            }
            else
            {
               throw (e);
            }
         }
      }
      if(locked > 0 && deleted == 0)
      {
         return (new DeleteResult().withKey(key).withMode(DeleteMode.SKIPPED_OBJECT_LOCK).withDetail(locked + " version(s) under object-lock retention"));
      }
      return (new DeleteResult().withKey(key).withMode(DeleteMode.PERMANENT).withVersionsDeleted(deleted)
         .withDetail(locked > 0 ? locked + " version(s) skipped: object-lock retention" : null));
   }



   /*******************************************************************************
    ** Permanently delete many keys in batches (DeleteObjects, up to 1,000 per
    ** call), falling back to one call per key where the provider has no batch
    ** delete. System callers only; protected keys are skipped. Returns how many
    ** keys were deleted.
    *******************************************************************************/
   public int purgeKeys(List<String> rawKeys)
   {
      if(!caller.isSystem())
      {
         throw (new StorageException(Reason.ACCESS_DENIED, "A bulk permanent delete is available to system callers only"));
      }
      List<String> keys = new ArrayList<>();
      for(String raw : rawKeys)
      {
         String key = StorageKeys.normalize(raw);
         if(!ProtectionRules.check(alias(), key, ProtectionRules.Op.DELETE, caller).blocked())
         {
            keys.add(key);
         }
      }
      int deleted = 0;
      boolean batch = !CapabilityStore.ruledOut(capability(Capability.BATCH_DELETE));
      OperationLog.Entry log = new OperationLog.Entry(caller, "DELETE", alias()).detail("batch purge of " + keys.size() + " key(s)");
      try
      {
         for(int i = 0; i < keys.size(); i += 1000)
         {
            List<String> slice = keys.subList(i, Math.min(keys.size(), i + 1000));
            boolean done = false;
            if(batch)
            {
               try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
               {
                  var r = connection.client().deleteObjects(software.amazon.awssdk.services.s3.model.DeleteObjectsRequest.builder().bucket(name())
                     .delete(software.amazon.awssdk.services.s3.model.Delete.builder().objects(slice.stream()
                        .map(k -> software.amazon.awssdk.services.s3.model.ObjectIdentifier.builder().key(k).build()).toList()).build()).build());
                  if(r.errors().isEmpty())
                  {
                     deleted += slice.size();
                     done = true;
                  }
               }
               catch(S3Exception e)
               {
                  if(e.statusCode() != 501 && e.statusCode() != 405 && e.statusCode() != 400)
                  {
                     throw (e);
                  }
               }
            }
            if(!done)
            {
               for(String key : slice)
               {
                  try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
                  {
                     connection.client().deleteObject(DeleteObjectRequest.builder().bucket(name()).key(key).build());
                     deleted++;
                  }
               }
            }
         }
         log.bytes((long) deleted).write();
         for(String key : keys)
         {
            afterDelete(key, false);
         }
         return (deleted);
      }
      catch(RuntimeException e)
      {
         StorageException se = ErrorMapper.map(e, "purge");
         log.failed(se.getMessage()).requestId(se.getRequestId()).write();
         throw (se);
      }
   }



   /*******************************************************************************
    ** Undo a soft delete: remove the delete marker, or copy back from the trash.
    ** Returns whether anything was restored.
    *******************************************************************************/
   public boolean restore(String rawKey)
   {
      checkPermission("object_storage.write");
      String key = StorageKeys.normalize(rawKey);
      guardWrite(key, ProtectionRules.Op.WRITE, "restore");
      OperationLog.Entry log = new OperationLog.Entry(caller, "RESTORE", alias()).key(key);
      try
      {
         boolean restored = false;
         S3Client client = connection.client();
         if(isVersioned())
         {
            try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
            {
               ListObjectVersionsResponse r = client.listObjectVersions(ListObjectVersionsRequest.builder().bucket(name()).prefix(key).build());
               for(DeleteMarkerEntry m : r.deleteMarkers())
               {
                  if(m.key().equals(key) && Boolean.TRUE.equals(m.isLatest()))
                  {
                     client.deleteObject(DeleteObjectRequest.builder().bucket(name()).key(key).versionId(m.versionId()).build());
                     restored = true;
                  }
               }
            }
         }
         else if(Boolean.TRUE.equals(bucket.getTrashEnabled()))
         {
            String trashAlias = StringUtils.hasContent(bucket.getTrashBucket()) ? bucket.getTrashBucket() : alias();
            String trashKey   = bucket.getTrashPrefix() + key;
            StorageBucket trash = new StorageBucket(trashAlias, StorageCaller.system("object-storage", null));
            if(trash.headQuietly(trashKey) != null)
            {
               trash.doCopy(trashKey, key, bucket, CopyRequest.of(trashKey, key));
               try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
               {
                  trash.connection.client().deleteObject(DeleteObjectRequest.builder().bucket(trash.name()).key(trashKey).build());
               }
               restored = true;
            }
         }
         if(restored)
         {
            log.detail(afterWrite(ChangeOperation.RESTORE, key, null, headQuietly(key)));
         }
         else
         {
            log.skipped("nothing to restore");
         }
         log.write();
         return (restored);
      }
      catch(RuntimeException e)
      {
         StorageException se = ErrorMapper.map(e, "restore " + key);
         log.failed(se.getMessage()).requestId(se.getRequestId()).write();
         throw (se);
      }
   }



   /*******************************************************************************
    ** Whether the bucket has versioning enabled (checked live, cached a minute).
    *******************************************************************************/
   public boolean isVersioned()
   {
      if(CapabilityStore.ruledOut(capability(Capability.VERSIONING)))
      {
         return (false);
      }
      if(System.currentTimeMillis() - versioningCheckedAt > 60_000)
      {
         try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
         {
            versioningEnabled = connection.client().getBucketVersioning(b -> b.bucket(name())).status() == BucketVersioningStatus.ENABLED;
         }
         catch(RuntimeException e)
         {
            versioningEnabled = false;
         }
         versioningCheckedAt = System.currentTimeMillis();
      }
      return (versioningEnabled);
   }



   // ---------------------------------------------------------------- list

   /*******************************************************************************
    ** One page of a key listing (up to 1,000 keys per page).
    *******************************************************************************/
   public Page<ObjectSummary> list(ListRequest request)
   {
      checkPermission("object_storage.view");
      ListObjectsV2Request.Builder b = ListObjectsV2Request.builder().bucket(name()).maxKeys(Math.min(1000, request.getPageSize()))
         .prefix(StorageKeys.normalizePrefix(request.getPrefix()));
      if(StringUtils.hasContent(request.getDelimiter()))
      {
         b.delimiter(request.getDelimiter());
      }
      if(StringUtils.hasContent(request.getAfter()))
      {
         b.continuationToken(request.getAfter());
      }
      if(StringUtils.hasContent(request.getStartAfter()))
      {
         b.startAfter(request.getStartAfter());
      }
      try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
      {
         ListObjectsV2Response r = connection.client().listObjectsV2(b.build());
         Page<ObjectSummary> page = new Page<>();
         r.contents().forEach(o -> page.getItems().add(new ObjectSummary().withKey(o.key()).withSize(o.size()).withEtag(o.eTag())
            .withLastModified(o.lastModified()).withStorageClass(o.storageClassAsString())));
         r.commonPrefixes().forEach(p -> page.getPrefixes().add(p.prefix()));
         page.setNextToken(Boolean.TRUE.equals(r.isTruncated()) ? r.nextContinuationToken() : null);
         return (page);
      }
      catch(RuntimeException e)
      {
         throw (ErrorMapper.map(e, "list " + request.getPrefix()));
      }
   }



   // ------------------------------------------------------------- presign

   /*******************************************************************************
    ** A presigned download URL. The TTL is capped by the bucket's maximum.
    *******************************************************************************/
   public URL presignGet(PresignGet request)
   {
      checkPermission("object_storage.view");
      String key = StorageKeys.normalize(request.getKey());
      Duration ttl = cap(request.getTtl(), Duration.ofMinutes(5), bucket.getMaxPresignGetSeconds());
      countPresign();
      GetObjectRequest.Builder b = GetObjectRequest.builder().bucket(name()).key(key);
      if(StringUtils.hasContent(request.getDownloadAs()))
      {
         b.responseContentDisposition("attachment; filename=\"" + request.getDownloadAs().replace("\"", "") + "\"");
      }
      try
      {
         URL url = connection.presigner().presignGetObject(GetObjectPresignRequest.builder().signatureDuration(ttl).getObjectRequest(b.build()).build()).url();
         logPresign("PRESIGN_GET", key, ttl);
         return (url);
      }
      catch(RuntimeException e)
      {
         throw (ErrorMapper.map(e, "presign get " + key));
      }
   }



   /*******************************************************************************
    ** A short-lived (5 minute) presigned GET used only to render a preview or
    ** thumbnail in the admin app for a record the user is already allowed to see.
    ** It is not counted against the per-user presign cap and not logged per row
    ** (a list of thumbnails would flood the audit log); links a user asks for are
    ** created with presignGet and logged.
    *******************************************************************************/
   public URL previewUrl(String rawKey)
   {
      String key = StorageKeys.normalize(rawKey);
      try
      {
         return (connection.presigner().presignGetObject(GetObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(5))
            .getObjectRequest(GetObjectRequest.builder().bucket(name()).key(key).build()).build()).url());
      }
      catch(RuntimeException e)
      {
         throw (ErrorMapper.map(e, "preview " + key));
      }
   }



   /*******************************************************************************
    ** What a delete does in this bucket: a delete marker (versioned), a move to the
    ** trash, or a permanent delete.
    *******************************************************************************/
   public DeleteMode plannedDeleteMode()
   {
      if(isVersioned())
      {
         return (DeleteMode.SOFT_DELETE_MARKER);
      }
      return (Boolean.TRUE.equals(bucket.getTrashEnabled()) ? DeleteMode.SOFT_TRASH : DeleteMode.PERMANENT);
   }



   /*******************************************************************************
    ** The object's tags (empty where tagging is not supported or there are none).
    *******************************************************************************/
   public Map<String, String> getTags(String rawKey)
   {
      checkPermission("object_storage.view");
      String key = StorageKeys.normalize(rawKey);
      if(CapabilityStore.ruledOut(capability(Capability.OBJECT_TAGGING)))
      {
         return (new java.util.LinkedHashMap<>());
      }
      try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
      {
         Map<String, String> tags = new java.util.LinkedHashMap<>();
         connection.client().getObjectTagging(b -> b.bucket(name()).key(key)).tagSet().forEach(t -> tags.put(t.key(), t.value()));
         return (tags);
      }
      catch(RuntimeException e)
      {
         throw (ErrorMapper.map(e, "get tags of " + key));
      }
   }



   /*******************************************************************************
    ** Replace the object's tags. UNSUPPORTED where the provider has no tagging.
    *******************************************************************************/
   public void setTags(String rawKey, Map<String, String> tags)
   {
      checkPermission("object_storage.write");
      String key = StorageKeys.normalize(rawKey);
      if(CapabilityStore.ruledOut(capability(Capability.OBJECT_TAGGING)))
      {
         throw (new StorageException(Reason.UNSUPPORTED, "Object tagging is not supported on this provider"));
      }
      guardWrite(key, ProtectionRules.Op.WRITE, "tag");
      OperationLog.Entry log = new OperationLog.Entry(caller, "HEADERS", alias()).key(key).detail("tags: " + tags.keySet());
      try(RateLimiter.Permit ignored = connection.getRateLimiter().acquire())
      {
         connection.client().putObjectTagging(b -> b.bucket(name()).key(key).tagging(t -> t.tagSet(tags.entrySet().stream()
            .map(e -> software.amazon.awssdk.services.s3.model.Tag.builder().key(e.getKey()).value(e.getValue()).build()).toList())));
         log.write();
      }
      catch(RuntimeException e)
      {
         StorageException se = ErrorMapper.map(e, "set tags of " + key);
         log.failed(se.getMessage()).requestId(se.getRequestId()).write();
         throw (se);
      }
   }



   /*******************************************************************************
    ** A presigned upload URL. The content type and length are signed when given,
    ** which pins the upload to that exact type and size. (A presigned PUT cannot
    ** express a maximum size; use presignPost where supported.)
    *******************************************************************************/
   public URL presignPut(PresignPut request)
   {
      checkPermission("object_storage.write");
      String key = StorageKeys.normalize(request.getKey());
      Duration ttl = cap(request.getTtl(), Duration.ofMinutes(15), bucket.getMaxPresignPutSeconds());
      countPresign();
      PutObjectRequest.Builder b = PutObjectRequest.builder().bucket(name()).key(key);
      if(StringUtils.hasContent(request.getContentType()))
      {
         b.contentType(request.getContentType());
      }
      if(request.getContentLength() != null)
      {
         b.contentLength(request.getContentLength());
      }
      if(StringUtils.hasContent(request.getCacheControl()))
      {
         b.cacheControl(request.getCacheControl());
      }
      try
      {
         URL url = connection.presigner().presignPutObject(PutObjectPresignRequest.builder().signatureDuration(ttl).putObjectRequest(b.build()).build()).url();
         logPresign("PRESIGN_PUT", key, ttl);
         return (url);
      }
      catch(RuntimeException e)
      {
         throw (ErrorMapper.map(e, "presign put " + key));
      }
   }



   /*******************************************************************************
    ** A presigned POST (browser form upload) with a content-length-range policy,
    ** which can express a maximum size. UNSUPPORTED where the provider does not do
    ** POST uploads ("verify size after upload" is the honest fallback).
    *******************************************************************************/
   public PresignedPost presignPost(PresignPost request)
   {
      checkPermission("object_storage.write");
      if(CapabilityStore.ruledOut(capability(Capability.PRESIGNED_POST)))
      {
         throw (new StorageException(Reason.UNSUPPORTED, "Presigned POST is not supported on this provider; verify the size after upload"));
      }
      String key = StorageKeys.normalize(request.getKey());
      Duration ttl = cap(request.getTtl(), Duration.ofMinutes(15), bucket.getMaxPresignPutSeconds());
      countPresign();
      String[] creds = connection.presignCredentials();
      PresignedPost post = PostPolicySigner.sign(creds[0], creds[1], creds[2], creds[3], connection.getConfig().effectiveAddressingStyle(), name(), key,
         request.getContentType(), request.getMinBytes(), request.getMaxBytes(), ObjectStorageRuntime.clock().instant(), ttl);
      logPresign("PRESIGN_POST", key, ttl);
      return (post);
   }



   /*******************************************************************************
    ** The public URL of a key (via the PublicUrlResolver), or null.
    *******************************************************************************/
   public String publicUrl(String rawKey)
   {
      return (ObjectStorageRuntime.publicUrlResolver().resolve(bucket, StorageKeys.normalize(rawKey)));
   }



   /*******************************************************************************
    ** A requested TTL (or the default), capped by the maximum.
    *******************************************************************************/
   private static Duration cap(Duration requested, Duration defaultTtl, Integer maxSeconds)
   {
      Duration ttl = requested == null ? defaultTtl : requested;
      Duration max = Duration.ofSeconds(maxSeconds == null ? 3600 : maxSeconds);
      return (ttl.compareTo(max) > 0 ? max : ttl);
   }



   /*******************************************************************************
    ** Per-user cap on presigns per hour.
    *******************************************************************************/
   private void countPresign()
   {
      int limit = ObjectStorageRuntime.getConfig().getMaxPresignsPerUserPerHour();
      if(caller.isSystem())
      {
         return;
      }
      String who = caller.describe();
      long[] state = PRESIGN_COUNTS.computeIfAbsent(who, w -> new long[] { System.currentTimeMillis(), 0 });
      synchronized(state)
      {
         if(System.currentTimeMillis() - state[0] > 3_600_000L)
         {
            state[0] = System.currentTimeMillis();
            state[1] = 0;
         }
         if(++state[1] > limit)
         {
            throw (new StorageException(Reason.THROTTLED, "More than " + limit + " presigned links were requested in an hour"));
         }
      }
   }



   /*******************************************************************************
    ** Log a presign without the signature (key, method, expiry and issuer only).
    *******************************************************************************/
   private void logPresign(String action, String key, Duration ttl)
   {
      new OperationLog.Entry(caller, action, alias()).key(key).detail("expires " + ObjectStorageRuntime.clock().instant().plus(ttl)).write();
   }



   // ------------------------------------------------------------ helpers

   /*******************************************************************************
    ** HEAD without throwing (null when absent or unreadable).
    *******************************************************************************/
   private StoredObject headQuietly(String key)
   {
      try
      {
         return (head(key).orElse(null));
      }
      catch(RuntimeException e)
      {
         return (null);
      }
   }



   /*******************************************************************************
    ** Throw when a protection rule (or the protected-bucket flag) blocks a write.
    *******************************************************************************/
   private void guardWrite(String key, ProtectionRules.Op op, String what)
   {
      guardWrite(key, alias(), op, what);
   }



   /*******************************************************************************
    ** Throw when a protection rule blocks a write to a bucket alias.
    *******************************************************************************/
   private void guardWrite(String key, String targetAlias, ProtectionRules.Op op, String what)
   {
      ProtectionRules.Result result = ProtectionRules.check(targetAlias, key, op, caller);
      if(result.blocked())
      {
         throw (new StorageException(Reason.ACCESS_DENIED, "Cannot " + what + " " + key + ": " + result.reason()));
      }
      if(result.warned())
      {
         LOG.info("Protection rule warning", logPair("key", key), logPair("note", result.reason()));
      }
   }



   /*******************************************************************************
    ** Whether the current user holds a permission.
    *******************************************************************************/
   private boolean hasPermission(String permission)
   {
      return (QContext.getQSession() != null && QContext.getQSession().hasPermission(permission));
   }



   /*******************************************************************************
    ** When core permission enforcement is on, apply the same object_storage.*
    ** checks to the session's user. System callers are not subject to them.
    *******************************************************************************/
   private void checkPermission(String permission)
   {
      if(caller.isSystem() || QContext.getQSession() == null || !CorePermissionEnforcer.isInstanceEnforcementEnabled())
      {
         return;
      }
      if(!hasPermission(permission) && !hasPermission("object_storage.admin"))
      {
         throw (new StorageException(Reason.ACCESS_DENIED, "This needs the " + permission + " permission"));
      }
   }



   /*******************************************************************************
    ** After a write: update the index and run the listeners. Returns a note with
    ** any listener errors (recorded on the operation row), or null.
    *******************************************************************************/
   private String afterWrite(ChangeOperation op, String key, String dest, StoredObject stored)
   {
      return (afterWrite(op, alias(), key, dest, stored));
   }



   /*******************************************************************************
    ** After a write to a bucket alias (the destination of a copy).
    *******************************************************************************/
   private String afterWrite(ChangeOperation op, String targetAlias, String key, String dest, StoredObject stored)
   {
      String effectiveKey = op == ChangeOperation.COPY ? dest : key;
      IndexHook hook = ObjectStorageRuntime.indexHook();
      if(hook != null && stored != null)
      {
         try
         {
            hook.objectWritten(ObjectStorageRuntime.bucketConfig(targetAlias), stored);
         }
         catch(RuntimeException e)
         {
            LOG.warn("Index write-through failed; the next scan repairs it", e, logPair("key", effectiveKey));
         }
      }
      StorageChange change = new StorageChange().withBucketAlias(targetAlias).withKey(op == ChangeOperation.COPY ? key : effectiveKey)
         .withDestinationKey(op == ChangeOperation.COPY ? dest : null).withOperation(op).withActor(caller.describe());
      return (notifyListeners(change));
   }



   /*******************************************************************************
    ** After a delete: update the index and run the listeners.
    *******************************************************************************/
   private String afterDelete(String key, boolean soft)
   {
      IndexHook hook = ObjectStorageRuntime.indexHook();
      if(hook != null)
      {
         try
         {
            hook.objectRemoved(bucket, key, soft);
         }
         catch(RuntimeException e)
         {
            LOG.warn("Index write-through failed; the next scan repairs it", e, logPair("key", key));
         }
      }
      return (notifyListeners(new StorageChange().withBucketAlias(alias()).withKey(key).withOperation(ChangeOperation.DELETE).withActor(caller.describe())));
   }



   /*******************************************************************************
    ** Run every listener; their errors are collected, never thrown.
    *******************************************************************************/
   private String notifyListeners(StorageChange change)
   {
      List<String> errors = new ArrayList<>();
      for(StorageChangeListener listener : ObjectStorageRuntime.listeners())
      {
         try
         {
            listener.onChange(change);
         }
         catch(Exception e)
         {
            errors.add(listener.getClass().getSimpleName() + ": " + e.getMessage());
            LOG.warn("A storage change listener failed", e, logPair("listener", listener.getClass().getName()));
         }
      }
      return (errors.isEmpty() ? null : "listener errors: " + String.join("; ", errors));
   }



   /*******************************************************************************
    ** A content type from the key's extension.
    *******************************************************************************/
   static String guessContentType(String key)
   {
      String guessed = URLConnection.guessContentTypeFromName(key);
      if(guessed != null)
      {
         return (guessed);
      }
      String lower = key.toLowerCase();
      Map<String, String> known = new HashMap<>(Map.of("webp", "image/webp", "svg", "image/svg+xml", "json", "application/json", "css", "text/css",
         "js", "text/javascript", "mp4", "video/mp4", "webm", "video/webm", "txt", "text/plain", "avif", "image/avif"));
      int dot = lower.lastIndexOf('.');
      return (dot >= 0 && known.containsKey(lower.substring(dot + 1)) ? known.get(lower.substring(dot + 1)) : "application/octet-stream");
   }



}
