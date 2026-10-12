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


import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import com.kingsrook.qbits.objectstorage.api.Capability;
import com.kingsrook.qbits.objectstorage.api.CapabilityStatus;
import com.kingsrook.qbits.objectstorage.config.ProviderType;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;


/*******************************************************************************
 ** The pinned stores: MinIO (the digest-pinned Chainguard build the qqq tests
 ** use, because Docker Hub's minio/minio is gone) and Garage (digest-pinned),
 ** each started once per JVM with Testcontainers.
 *******************************************************************************/
public final class Targets
{
   public static final String MINIO_IMAGE = System.getProperty("qqq.test.minioImage", "cgr.dev/chainguard/minio@sha256:dc455cdd4db6e390b1b26514f15ff37681a777bf8dab41aac445a0259f751e56");
   public static final String GARAGE_IMAGE = System.getProperty("qqq.test.garageImage", "dxflrs/garage@sha256:a5706cf1f3d7b349ac5133ec59ad8181b709270b75e5f2fa7b3e1a5c07d67137");

   private static ConformanceTarget minio;
   private static ConformanceTarget garage;



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private Targets()
   {
   }



   /*******************************************************************************
    ** Whether Docker is available. In CI (the CI env var is set) a missing Docker
    ** is a failure, not a skip.
    *******************************************************************************/
   public static boolean dockerAvailable()
   {
      try
      {
         return (DockerClientFactory.instance().isDockerAvailable());
      }
      catch(RuntimeException e)
      {
         return (false);
      }
   }



   /*******************************************************************************
    ** Whether a missing Docker must fail the build.
    *******************************************************************************/
   public static boolean dockerRequired()
   {
      return (System.getenv("CI") != null);
   }



   /*******************************************************************************
    ** MinIO, started once.
    *******************************************************************************/
   public static synchronized ConformanceTarget minio()
   {
      if(minio == null)
      {
         GenericContainer<?> container = new GenericContainer<>(MINIO_IMAGE)
            .withEnv("MINIO_ROOT_USER", "qqq-test-access-key")
            .withEnv("MINIO_ROOT_PASSWORD", "qqq-test-secret-key")
            .withCommand("server", "/tmp/minio-data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).withStartupTimeout(Duration.ofMinutes(2)));
         container.start();

         ConformanceTarget t = new ConformanceTarget();
         t.name = "minio";
         t.providerType = ProviderType.MINIO;
         t.endpoint = "http://" + container.getHost() + ":" + container.getMappedPort(9000);
         t.accessKey = "qqq-test-access-key";
         t.secretKey = "qqq-test-secret-key";
         t.region = "us-east-1";
         String suffix = UUID.randomUUID().toString().substring(0, 8);
         t.bucket = "scratch-" + suffix;
         t.versionedBucket = "versioned-" + suffix;
         t.trashBucket = "trash-" + suffix;
         t.lockBucket = "locked-" + suffix;
         t.indexBucket = "index-" + suffix;
         try(S3Client s3 = t.rawClient())
         {
            s3.createBucket(CreateBucketRequest.builder().bucket(t.bucket).build());
            s3.createBucket(CreateBucketRequest.builder().bucket(t.versionedBucket).build());
            s3.putBucketVersioning(b -> b.bucket(t.versionedBucket).versioningConfiguration(VersioningConfiguration.builder().status(BucketVersioningStatus.ENABLED).build()));
            s3.createBucket(CreateBucketRequest.builder().bucket(t.trashBucket).build());
            s3.createBucket(CreateBucketRequest.builder().bucket(t.indexBucket).build());
            s3.createBucket(CreateBucketRequest.builder().bucket(t.lockBucket).objectLockEnabledForBucket(true).build());
         }
         t.checksumsSupportedWorks = true;
         // what the probe must report for the plain scratch bucket
         t.expected.put(Capability.LIST_V2, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.PUT_GET_DELETE, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.COPY_REPLACE_METADATA, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.BATCH_DELETE, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.MULTIPART, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.PRESIGNED_GET, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.PRESIGNED_PUT, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.PRESIGNED_POST, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.CONDITIONAL_WRITE, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.VERSIONING, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.OBJECT_LOCK, CapabilityStatus.UNSUPPORTED);
         minio = t;
      }
      return (minio);
   }



   /*******************************************************************************
    ** Garage, started once, with a single-node layout, one key and the buckets.
    *******************************************************************************/
   public static synchronized ConformanceTarget garage()
   {
      if(garage == null)
      {
         String toml = """
            metadata_dir = "/tmp/meta"
            data_dir = "/tmp/data"
            db_engine = "sqlite"
            replication_factor = 1
            rpc_bind_addr = "[::]:3901"
            rpc_public_addr = "127.0.0.1:3901"
            rpc_secret = "1799bccfd7411eddcf9ebd316bc1f5287ad12a68094e1c6ac6abde7e6feae1ec"

            [s3_api]
            s3_region = "garage"
            api_bind_addr = "[::]:3900"
            root_domain = ".s3.garage.localhost"

            [admin]
            api_bind_addr = "[::]:3903"
            admin_token = "qqq-test-admin-token"
            """;
         GenericContainer<?> container = new GenericContainer<>(GARAGE_IMAGE)
            .withCopyToContainer(Transferable.of(toml), "/etc/garage.toml")
            .withExposedPorts(3900, 3903)
            .waitingFor(Wait.forHttp("/health").forPort(3903).forStatusCodeMatching(c -> c == 200 || c == 503).withStartupTimeout(Duration.ofMinutes(2)));
         container.start();

         String accessKey = "GK" + "0123456789abcdef01234567";
         String secretKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
         ConformanceTarget t = new ConformanceTarget();
         t.name = "garage";
         t.providerType = ProviderType.GARAGE;
         t.endpoint = "http://" + container.getHost() + ":" + container.getMappedPort(3900);
         t.accessKey = accessKey;
         t.secretKey = secretKey;
         t.region = "garage";
         t.bucket = "scratch";
         t.trashBucket = "trash";
         t.indexBucket = "indexed";
         try
         {
            String status = exec(container, "/garage", "status");
            String nodeId = null;
            for(String line : status.split("\n"))
            {
               String trimmed = line.trim();
               if(trimmed.matches("^[0-9a-f]{16}\\b.*"))
               {
                  nodeId = trimmed.substring(0, 16);
                  break;
               }
            }
            if(nodeId == null)
            {
               throw (new IllegalStateException("No Garage node id in: " + status));
            }
            exec(container, "/garage", "layout", "assign", "-z", "dc1", "-c", "1G", nodeId);
            exec(container, "/garage", "layout", "apply", "--version", "1");
            exec(container, "/garage", "key", "import", "--yes", "-n", "qqq-test", accessKey, secretKey);
            for(String b : new String[] { t.bucket, t.trashBucket, t.indexBucket })
            {
               exec(container, "/garage", "bucket", "create", b);
               exec(container, "/garage", "bucket", "allow", "--read", "--write", "--owner", b, "--key", "qqq-test");
            }
         }
         catch(IOException | InterruptedException e)
         {
            throw (new IllegalStateException("Could not set up Garage", e));
         }
         waitForS3(t);
         t.checksumsSupportedWorks = false;
         t.expected.put(Capability.LIST_V2, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.PUT_GET_DELETE, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.COPY_REPLACE_METADATA, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.BATCH_DELETE, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.MULTIPART, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.PRESIGNED_GET, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.PRESIGNED_PUT, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.PRESIGNED_POST, CapabilityStatus.SUPPORTED);
         t.expected.put(Capability.VERSIONING, CapabilityStatus.UNSUPPORTED);
         t.expected.put(Capability.OBJECT_LOCK, CapabilityStatus.UNSUPPORTED);
         t.expected.put(Capability.OBJECT_TAGGING, CapabilityStatus.UNSUPPORTED);
         t.expected.put(Capability.BUCKET_POLICY, CapabilityStatus.UNSUPPORTED);
         garage = t;
      }
      return (garage);
   }



   /*******************************************************************************
    ** Run a command in a container, failing on a non-zero exit.
    *******************************************************************************/
   private static String exec(GenericContainer<?> container, String... command) throws IOException, InterruptedException
   {
      Container.ExecResult result = container.execInContainer(command);
      if(result.getExitCode() != 0)
      {
         throw (new IllegalStateException("Command failed (" + String.join(" ", command) + "): " + result.getStderr() + result.getStdout()));
      }
      return (result.getStdout());
   }



   /*******************************************************************************
    ** Wait until the S3 API answers (the layout takes a moment to apply).
    *******************************************************************************/
   private static void waitForS3(ConformanceTarget t)
   {
      long deadline = System.currentTimeMillis() + 60_000;
      RuntimeException last = null;
      while(System.currentTimeMillis() < deadline)
      {
         try(S3Client s3 = t.rawClient())
         {
            s3.listObjectsV2(b -> b.bucket(t.bucket).maxKeys(1));
            return;
         }
         catch(RuntimeException e)
         {
            last = e;
            try
            {
               Thread.sleep(500);
            }
            catch(InterruptedException ie)
            {
               Thread.currentThread().interrupt();
               break;
            }
         }
      }
      throw (new IllegalStateException("Garage S3 never became ready", last));
   }
}
