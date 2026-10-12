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

package com.kingsrook.qbits.objectstorage.testsupport;


import java.util.List;
import java.util.function.Consumer;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitProducer;
import com.kingsrook.qbits.objectstorage.conformance.ConformanceTarget;
import com.kingsrook.qbits.objectstorage.config.AddressingStyle;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.ChecksumMode;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qbits.objectstorage.internal.CapabilityStore;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.DefaultObjectHeaderPolicy;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;


/*******************************************************************************
 ** A memory-backend QQQ instance with the qbit installed against a target store.
 ** Secrets reach the config as ${prop.X} references (system properties), the
 ** same reference mechanism an app uses with ${env.X}.
 *******************************************************************************/
public final class StorageTestSupport
{
   public static final String MEMORY = "memory";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private StorageTestSupport()
   {
   }



   /*******************************************************************************
    ** The config for a target: connection "primary" and buckets files (the
    ** scratch bucket), versioned (when the store has versioning), soft (trash in
    ** the trash bucket), and trashbin (the private trash).
    *******************************************************************************/
   public static ObjectStorageQBitConfig config(ConformanceTarget t, ChecksumMode checksumMode)
   {
      System.setProperty("os.test.endpoint", t.endpoint);
      System.setProperty("os.test.ak", t.accessKey);
      System.setProperty("os.test.sk", t.secretKey);
      System.setProperty("os.test.files", t.bucket);
      ObjectStorageQBitConfig config = new ObjectStorageQBitConfig()
         .withBackendName(MEMORY)
         .withBucketScoping(false)
         .withOpenWhenAnonymous(true)
         .withAdminApp(true)
         .withAllowedSecretRefPatterns(List.of("prop.os.test.*"))
         .withConnection(new StorageConnectionConfig("primary")
            .withProviderType(t.providerType)
            .withEndpoint("${prop.os.test.endpoint}")
            .withRegion(t.region)
            .withAddressingStyle(AddressingStyle.PATH)
            .withChecksumMode(checksumMode)
            .withAccessKeyRef("${prop.os.test.ak}")
            .withSecretKeyRef("${prop.os.test.sk}")
            .withMaxOpsPerSecond(2000)
            .withMaxConcurrency(16))
         .withBucket(new StorageBucketConfig("files").withConnection("primary").withBucketName("${prop.os.test.files}").withRole(BucketRole.PRIVATE).withIndexed(true));
      if(t.trashBucket != null)
      {
         System.setProperty("os.test.trash", t.trashBucket);
         config.withBucket(new StorageBucketConfig("trashbin").withConnection("primary").withBucketName("${prop.os.test.trash}").withRole(BucketRole.PRIVATE).withIndexed(false));
         config.withBucket(new StorageBucketConfig("soft").withConnection("primary").withBucketName("${prop.os.test.files}").withRole(BucketRole.PRIVATE).withTrashEnabled(true).withTrashBucket("trashbin")
            .withTrashPrefix(".trash/").withIndexed(false));
      }
      if(t.versionedBucket != null)
      {
         System.setProperty("os.test.versioned", t.versionedBucket);
         config.withBucket(new StorageBucketConfig("versioned").withConnection("primary").withBucketName("${prop.os.test.versioned}").withRole(BucketRole.PRIVATE).withIndexed(false));
      }
      if(t.lockBucket != null)
      {
         System.setProperty("os.test.lock", t.lockBucket);
         config.withBucket(new StorageBucketConfig("locked").withConnection("primary").withBucketName("${prop.os.test.lock}").withRole(BucketRole.BACKUP).withIndexed(false));
      }
      return (config);
   }



   /*******************************************************************************
    ** Fresh instance, store and runtime for a target.
    *******************************************************************************/
   public static QInstance setUp(ConformanceTarget t, Consumer<ObjectStorageQBitConfig> customize) throws QException
   {
      ObjectStorageQBitConfig config = config(t, t.providerType == null || t.checksumsSupportedWorks ? ChecksumMode.WHEN_REQUIRED : ChecksumMode.WHEN_REQUIRED);
      if(customize != null)
      {
         customize.accept(config);
      }
      return (setUp(config));
   }



   /*******************************************************************************
    ** Fresh instance, store and runtime for a config.
    *******************************************************************************/
   public static QInstance setUp(ObjectStorageQBitConfig config) throws QException
   {
      MemoryRecordStore.getInstance().reset();
      ObjectStorageRuntime.reset();
      ConfigSync.reset();
      CapabilityStore.clearCache();
      DefaultObjectHeaderPolicy.clearCache();

      QInstance instance = new QInstance();
      instance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      instance.addBackend(new QBackendMetaData().withName(MEMORY).withBackendType(MemoryBackendModule.class));
      new ObjectStorageQBitProducer().withQBitConfig(config).produce(instance);
      QContext.init(instance, new QSession());
      ConfigSync.sync();
      return (instance);
   }



   /*******************************************************************************
    ** Insert rows.
    *******************************************************************************/
   public static List<QRecord> insert(String table, QRecord... records) throws QException
   {
      return (new InsertAction().execute(new InsertInput(table).withRecords(List.of(records))).getRecords());
   }



   /*******************************************************************************
    ** Tear down.
    *******************************************************************************/
   public static void tearDown()
   {
      ObjectStorageRuntime.reset();
      ConfigSync.reset();
      QContext.clear();
   }
}
