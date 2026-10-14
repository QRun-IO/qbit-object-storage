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

package com.kingsrook.qbits.objectstorage.app;


import java.util.List;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaderPolicy;
import com.kingsrook.qbits.objectstorage.app.StorageSetupStatus.Step;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.ProviderType;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The Setup card: states follow what is configured, resolved the way the
 ** runtime resolves it (a host's own header policy beats the rows).
 *******************************************************************************/
class StorageSetupStatusTest
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
    ** A bare install needs a connection and a bucket; headers use defaults; the
    ** protection rules are optional.
    *******************************************************************************/
   @Test
   void bareInstall()
   {
      List<Step> steps = StorageSetupStatus.steps(0, 0, 0, false, 0);
      assertThat(steps).extracting(Step::label).containsExactly("Connections", "Buckets", "Header rules", "Protection rules");
      assertThat(steps).extracting(Step::state).containsExactly("Needs setup", "Needs setup", "Using defaults", "Optional");
      assertThat(steps).allSatisfy(step -> assertThat(step.next()).isNotBlank());
   }



   /*******************************************************************************
    ** A host policy in code means the rows are not what decides, so it is never
    ** reported as needing setup.
    *******************************************************************************/
   @Test
   void hostPolicyIsRespected()
   {
      assertThat(StorageSetupStatus.steps(1, 1, 0, true, 0).get(2)).satisfies(step ->
      {
         assertThat(step.state()).isEqualTo("Set up");
         assertThat(step.next()).contains("host's own header policy");
      });
      assertThat(StorageSetupStatus.steps(1, 1, 2, false, 1)).extracting(Step::state).containsOnly("Set up");
   }



   /*******************************************************************************
    ** The live card reads the tables and the config.
    *******************************************************************************/
   @Test
   void liveStatus() throws Exception
   {
      ObjectStorageQBitConfig config = new ObjectStorageQBitConfig().withBackendName(StorageTestSupport.MEMORY).withBucketScoping(false).withOpenWhenAnonymous(true).withAdminApp(true)
         .withConnection(new StorageConnectionConfig("main").withProviderType(ProviderType.MINIO).withEndpoint("http://localhost:1").withRegion("us-east-1"))
         .withBucket(new StorageBucketConfig("media").withConnection("main").withBucketName("media-bucket").withRole(BucketRole.PRIVATE).withIndexed(false));
      StorageTestSupport.setUp(config);
      assertThat(StorageSetupStatus.load()).extracting(Step::state).containsExactly("Set up", "Set up", "Using defaults", "Optional");

      StorageTestSupport.insert(ObjectStorageTables.HEADER_RULE, new QRecord().withValue("name", "r").withValue("keyGlob", "*").withValue("cacheControl", "no-cache").withValue("priority", 1).withValue("enabled", true));
      assertThat(StorageSetupStatus.load().get(2).state()).isEqualTo("Set up");

      ObjectHeaderPolicy hostPolicy = (bucket, key, contentType) -> new com.kingsrook.qbits.objectstorage.api.ObjectHeaders();
      config.withHeaderPolicy(hostPolicy);
      assertThat(StorageSetupStatus.load().get(2).next()).contains("host's own");
   }



   /*******************************************************************************
    ** A bucket-scoped user with no bucket keys sees no rows, but the card reads
    ** the runtime config and counts rules as the system, so it never shows a
    ** false "needs setup".
    *******************************************************************************/
   @Test
   void scopedUserSeesTrueStatus() throws Exception
   {
      ObjectStorageQBitConfig config = new ObjectStorageQBitConfig().withBackendName(StorageTestSupport.MEMORY).withBucketScoping(true).withOpenWhenAnonymous(true).withAdminApp(true)
         .withConnection(new StorageConnectionConfig("main").withProviderType(ProviderType.MINIO).withEndpoint("http://localhost:1").withRegion("us-east-1"))
         .withBucket(new StorageBucketConfig("media").withConnection("main").withBucketName("media-bucket").withRole(BucketRole.PRIVATE).withIndexed(false));
      StorageTestSupport.setUp(config);
      StorageTestSupport.insert(ObjectStorageTables.HEADER_RULE, new QRecord().withValue("name", "r").withValue("keyGlob", "*").withValue("cacheControl", "no-cache").withValue("priority", 1).withValue("enabled", true));
      assertThat(StorageSetupStatus.load()).extracting(Step::state).containsExactly("Set up", "Set up", "Set up", "Optional");
   }
}
