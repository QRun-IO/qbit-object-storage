/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
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


import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.api.Capability;
import com.kingsrook.qbits.objectstorage.api.CapabilityStatus;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.ProviderType;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.cluster.ClusterBus;
import com.kingsrook.qqq.backend.core.cluster.ClusterBusInterface;
import com.kingsrook.qqq.backend.core.cluster.ClusterBusMetaData;
import com.kingsrook.qqq.backend.core.cluster.ClusterCacheAudit;
import com.kingsrook.qqq.backend.core.cluster.ClusterEvent;
import com.kingsrook.qqq.backend.core.cluster.QNodeRoles;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The Object Storage caches are correct on several nodes: every static cache
 ** is registered with ClusterCaches, a probe result recorded on one node clears
 ** that capability on the others, and an edited bucket row or header rule
 ** clears its cache on every node.
 *******************************************************************************/
class ObjectStorageClusterCachesTest
{
   /** the "wire": events every node's transport sees */
   static final List<Consumer<ClusterEvent>> WIRE = new CopyOnWriteArrayList<>();



   /*******************************************************************************
    ** A transport over the static WIRE, so a "remote" publish reaches us.
    *******************************************************************************/
   public static class WireBus implements ClusterBusInterface
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void publish(ClusterEvent event)
      {
         WIRE.forEach(listener -> listener.accept(event));
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public AutoCloseable subscribe(Consumer<ClusterEvent> listener)
      {
         WIRE.add(listener);
         return (() -> WIRE.remove(listener));
      }
   }



   /*******************************************************************************
    ** A synced instance whose buckets never touch a real store, on the wire as
    ** node-a.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      System.setProperty("os.unit.ak", "ak");
      System.setProperty("os.unit.sk", "sk");
      StorageTestSupport.setUp(new ObjectStorageQBitConfig().withBackendName(StorageTestSupport.MEMORY).withBucketScoping(false).withOpenWhenAnonymous(true).withAdminApp(true)
            .withAllowedSecretRefPatterns(List.of("prop.os.unit.*"))
            .withConnection(new StorageConnectionConfig("main").withProviderType(ProviderType.MINIO).withEndpoint("http://localhost:1").withRegion("us-east-1")
               .withAccessKeyRef("${prop.os.unit.ak}").withSecretKeyRef("${prop.os.unit.sk}"))
            .withBucket(new StorageBucketConfig("media").withConnection("main").withBucketName("media-bucket").withRole(BucketRole.PRIVATE).withIndexed(false)),
         instance -> instance.withSupplementalMetaData(new ClusterBusMetaData().withBus(new QCodeReference(WireBus.class))));
      QNodeRoles.set(new QNodeRoles.Settings(EnumSet.allOf(QNodeRoles.Role.class), true, true, true, null, "node-a"));
      ClusterBus.connect(QContext.getQInstance());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      ClusterBus.close(QContext.getQInstance());
      WIRE.clear();
      QNodeRoles.set(null);
      StorageTestSupport.tearDown();
   }



   /*******************************************************************************
    ** Every static Memoization in the QBit is registered (shared or node-local).
    *******************************************************************************/
   @Test
   void testNoUnregisteredStaticCaches() throws Exception
   {
      assertThat(ClusterCacheAudit.unregisteredStaticCaches("com.kingsrook.qbits.objectstorage")).isEmpty();
   }



   /*******************************************************************************
    ** Another node's probe result clears the capability here; the next read
    ** reloads it from the capability row.
    *******************************************************************************/
   @Test
   void testRemoteProbeResultClearsCapability() throws Exception
   {
      StorageBucketConfig bucket = ObjectStorageRuntime.bucketConfig("media");
      CapabilityStore.record(bucket, Capability.VERSIONING, CapabilityStatus.UNSUPPORTED, "probe here");
      assertThat(CapabilityStore.isCached(bucket, Capability.VERSIONING)).isTrue();

      ////////////////////////////////////////////////////////////
      // node-b probes again and writes SUPPORTED to the row    //
      ////////////////////////////////////////////////////////////
      Integer bucketId = ConfigSync.bucketId("media");
      Db.asSystem(() ->
      {
         QRecord row = Db.first(ObjectStorageTables.BUCKET_CAPABILITY, "bucketId", bucketId, "capability", Capability.VERSIONING.name());
         Db.update(ObjectStorageTables.BUCKET_CAPABILITY, List.of(new QRecord().withValue("id", row.getValue("id")).withValue("status", CapabilityStatus.SUPPORTED.name())));
         return (null);
      });
      ClusterEvent remote = new ClusterEvent(ClusterEvent.TYPE_MEMO_INVALIDATE, CapabilityStore.CACHE_NAME, "b:media:" + Capability.VERSIONING);
      remote.setOriginNodeId("node-b");
      WIRE.forEach(listener -> listener.accept(remote));

      assertThat(CapabilityStore.isCached(bucket, Capability.VERSIONING)).isFalse();
      assertThat(CapabilityStore.status("media", Capability.VERSIONING)).isEqualTo(CapabilityStatus.SUPPORTED);
   }



   /*******************************************************************************
    ** A probe result recorded here tells the other nodes, and stays cached here.
    *******************************************************************************/
   @Test
   void testProbeResultPublishesInvalidation()
   {
      List<ClusterEvent> sent = sentEvents();
      StorageBucketConfig bucket = ObjectStorageRuntime.bucketConfig("media");
      CapabilityStore.record(bucket, Capability.VERSIONING, CapabilityStatus.SUPPORTED, "probe here");

      assertThat(CapabilityStore.status("media", Capability.VERSIONING)).isEqualTo(CapabilityStatus.SUPPORTED);
      assertThat(sent).anySatisfy(event -> assertInvalidation(event, CapabilityStore.CACHE_NAME, "b:media:VERSIONING"));
   }



   /*******************************************************************************
    ** An edited bucket row takes effect at once and tells the other nodes.
    *******************************************************************************/
   @Test
   void testBucketRowEditInvalidatesRowLimitsEverywhere() throws Exception
   {
      StorageBucketConfig bucket = ObjectStorageRuntime.bucketConfig("media");
      assertThat(ConfigSync.effectiveMaxPresignGetSeconds(bucket)).isEqualTo(3600);

      List<ClusterEvent> sent = sentEvents();
      Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", ConfigSync.bucketId("media")).withValue("maxPresignGetSeconds", 60)));

      assertThat(ConfigSync.effectiveMaxPresignGetSeconds(bucket)).as("no wait for the 15 s cache").isEqualTo(60);
      assertThat(sent).anySatisfy(event -> assertInvalidation(event, ConfigSync.ROW_LIMITS_CACHE_NAME, null));
   }



   /*******************************************************************************
    ** An edited header rule tells the other nodes.
    *******************************************************************************/
   @Test
   void testHeaderRuleEditInvalidatesRulesEverywhere() throws Exception
   {
      List<ClusterEvent> sent = sentEvents();
      StorageTestSupport.insert(ObjectStorageTables.HEADER_RULE, new QRecord().withValue("name", "all").withValue("keyGlob", "**").withValue("cacheControl", "no-store"));
      assertThat(sent).anySatisfy(event -> assertInvalidation(event, DefaultObjectHeaderPolicy.CACHE_NAME, null));
   }



   /*******************************************************************************
    ** Collect what this node puts on the wire.
    *******************************************************************************/
   private static List<ClusterEvent> sentEvents()
   {
      List<ClusterEvent> sent = new CopyOnWriteArrayList<>();
      WIRE.add(sent::add);
      return (sent);
   }



   /*******************************************************************************
    ** A memo invalidation from node-a for a cache (and key).
    *******************************************************************************/
   private static void assertInvalidation(ClusterEvent event, String name, String key)
   {
      assertThat(event.getType()).isEqualTo(ClusterEvent.TYPE_MEMO_INVALIDATE);
      assertThat(event.getName()).isEqualTo(name);
      assertThat(event.getKey()).isEqualTo(key);
      assertThat(event.getOriginNodeId()).isEqualTo("node-a");
   }
}
