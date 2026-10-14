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


import java.net.URL;
import java.time.Duration;
import java.util.List;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.api.PresignGet;
import com.kingsrook.qbits.objectstorage.api.PresignPut;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.ProviderType;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.processes.ActionSupport;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The presign limits on the bucket row (Settings on object_storage_bucket) are
 ** read at runtime, the way ConfigSync.withOverrides reads a connection row:
 ** editing the row changes how long presigned links last, and the identity
 ** fields stay owned by code.
 *******************************************************************************/
class BucketRowOverridesTest
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
    ** A config whose buckets never touch a real store (presigning is offline).
    *******************************************************************************/
   private static ObjectStorageQBitConfig config()
   {
      System.setProperty("os.unit.ak", "ak");
      System.setProperty("os.unit.sk", "sk");
      return (new ObjectStorageQBitConfig().withBackendName(StorageTestSupport.MEMORY).withBucketScoping(false).withOpenWhenAnonymous(true).withAdminApp(true)
         .withAllowedSecretRefPatterns(List.of("prop.os.unit.*"))
         .withConnection(new StorageConnectionConfig("main").withProviderType(ProviderType.MINIO).withEndpoint("http://localhost:1").withRegion("us-east-1")
            .withAccessKeyRef("${prop.os.unit.ak}").withSecretKeyRef("${prop.os.unit.sk}"))
         .withBucket(new StorageBucketConfig("media").withConnection("main").withBucketName("media-bucket").withRole(BucketRole.PRIVATE).withIndexed(false)
            .withPublicBaseUrl("https://cdn.example.com/media/")));
   }



   /*******************************************************************************
    ** The presigned URL's lifetime in seconds.
    *******************************************************************************/
   private static int expires(URL url)
   {
      return (Integer.parseInt(url.getQuery().replaceAll(".*X-Amz-Expires=(\\d+).*", "$1")));
   }



   /*******************************************************************************
    ** Editing the row's presign limits caps new links; the config value applies
    ** until then; a nonsense value (zero) is ignored.
    *******************************************************************************/
   @Test
   void rowPresignLimitsCapNewLinks() throws Exception
   {
      StorageTestSupport.setUp(config());
      Integer bucketId = ConfigSync.bucketId("media");

      assertThat(expires(ObjectStorage.bucket("media").presignGet(PresignGet.of("a.txt").ttl(Duration.ofMinutes(30))))).as("the config limit (an hour)").isEqualTo(1800);

      Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", bucketId).withValue("maxPresignGetSeconds", 60).withValue("maxPresignPutSeconds", 120)));
      assertThat(expires(ObjectStorage.bucket("media").presignGet(PresignGet.of("a.txt").ttl(Duration.ofMinutes(30))))).as("the row's limit caps the link").isEqualTo(60);
      assertThat(expires(ObjectStorage.bucket("media").presignPut(PresignPut.of("a.txt").ttl(Duration.ofMinutes(10))))).isEqualTo(120);
      assertThat(ActionSupport.config("media").getMaxPresignGetSeconds()).as("the Create download link action reads it too").isEqualTo(60);

      Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", bucketId).withValue("maxPresignGetSeconds", 0)));
      assertThat(ObjectStorageRuntime.bucketConfig("media").getMaxPresignGetSeconds()).as("zero is ignored").isEqualTo(60);
   }



   /*******************************************************************************
    ** Identity fields stay code-owned: a tampered public base URL on the row is
    ** rewritten from code and never read back as an override.
    *******************************************************************************/
   @Test
   void identityStaysWithCode() throws Exception
   {
      StorageTestSupport.setUp(config());
      Integer bucketId = ConfigSync.bucketId("media");
      Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", bucketId).withValue("publicBaseUrl", "https://evil.example.com/").withValue("trashPrefix", "x/")));
      assertThat(ObjectStorageRuntime.bucketConfig("media").getPublicBaseUrl()).isEqualTo("https://cdn.example.com/media/");
      ConfigSync.sync();
      assertThat(Db.first(ObjectStorageTables.BUCKET, "alias", "media").getValueString("publicBaseUrl")).isEqualTo("https://cdn.example.com/media/");
   }
}
