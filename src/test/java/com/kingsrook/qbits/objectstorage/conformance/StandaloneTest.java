package com.kingsrook.qbits.objectstorage.conformance;


import java.nio.charset.StandardCharsets;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.api.DeleteRequest;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.api.PutRequest;
import com.kingsrook.qbits.objectstorage.api.Range;
import com.kingsrook.qbits.objectstorage.config.ChecksumMode;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.context.QContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The service without a QQQ instance (command-line tools, tests): connections
 ** and buckets work, and nothing needs the object_storage_* tables.
 *******************************************************************************/
class StandaloneTest
{
   /*******************************************************************************
    ** Reset the runtime.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      StorageTestSupport.tearDown();
   }



   @Test
   void theServiceWorksWithoutAnInstanceOrTables() throws Exception
   {
      Assumptions.assumeTrue(Targets.dockerAvailable(), "Docker is not available");
      ConformanceTarget target = Targets.minio();
      ObjectStorageQBitConfig config = StorageTestSupport.config(target, ChecksumMode.WHEN_REQUIRED);
      QContext.clear();
      ObjectStorage.configureStandalone(config);

      String key = "standalone/" + System.nanoTime() + ".txt";
      ObjectStorage.bucket("files").put(PutRequest.of(key, "no tables needed".getBytes(StandardCharsets.UTF_8)).withContentType("text/plain"));
      try(var in = ObjectStorage.bucket("files").get(key, Range.all()))
      {
         assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("no tables needed");
      }
      ObjectStorage.bucket("files").delete(DeleteRequest.of(key));
      assertThat(ObjectStorage.bucket("files").head(key)).isEmpty();
      assertThat(ObjectStorage.bucket("files").maxUploadBytes()).isEqualTo(ObjectStorage.bucket("files").config().getMaxUploadBytes());
   }



   @Test
   void aBadConfigIsRejectedWithEveryProblem()
   {
      ObjectStorageQBitConfig bad = new ObjectStorageQBitConfig();
      assertThatThrownBy(() -> ObjectStorage.configureStandalone(bad)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("backendName");
   }
}
