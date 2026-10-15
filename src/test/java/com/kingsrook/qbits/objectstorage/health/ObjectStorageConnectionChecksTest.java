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

package com.kingsrook.qbits.objectstorage.health;


import javax.net.ssl.SSLHandshakeException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qbits.objectstorage.internal.CapabilityProbe;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.ConnectionRuntime;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.internal.RateLimiter;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.processes.TestConnectionProcess;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.health.HealthCategory;
import com.kingsrook.qqq.backend.core.health.HealthCheckInput;
import com.kingsrook.qqq.backend.core.health.HealthCheckKind;
import com.kingsrook.qqq.backend.core.health.HealthCheckMetaData;
import com.kingsrook.qqq.backend.core.health.HealthCheckOutcome;
import com.kingsrook.qqq.backend.core.health.HealthChecksMetaData;
import com.kingsrook.qqq.backend.core.health.HealthStatus;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Request;
import software.amazon.awssdk.utils.builder.SdkBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Exercise the real probe using an in-memory S3 client and QQQ record store.
 *******************************************************************************/
class ObjectStorageConnectionChecksTest
{
   private static final String SECRET = "S3NT1NEL-secret";
   private QInstance instance;
   private ObjectStorageQBitConfig config;
   private final List<String> calls = new ArrayList<>();
   private RuntimeException failure;
   private long skew;
   private boolean expireAfterList;
   private Instant deadline;



   /*******************************************************************************
    ** Poison every credential reference; the fake prevents all network access.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      System.setProperty("os.health.secret", SECRET);
      String ref = "${prop.os.health.secret}";
      config = new ObjectStorageQBitConfig().withBackendName(StorageTestSupport.MEMORY)
         .withAllowedSecretRefPatterns(List.of("prop.os.health.*"))
         .withConnection(new StorageConnectionConfig("PRIMARY").withEndpoint("https://user:" + SECRET + "@private.example")
            .withAccessKeyRef(ref).withSecretKeyRef(ref).withSessionTokenRef(ref)
            .withPresignAccessKeyRef(ref).withPresignSecretKeyRef(ref).withCaBundleRef(ref).withMaxConcurrency(1))
         .withBucket(new StorageBucketConfig("files").withConnection("PRIMARY").withBucketName("private-bucket").withIndexed(false));
      instance = StorageTestSupport.setUp(config);
      S3Client client = (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(), new Class<?>[] { S3Client.class }, (proxy, method, args) ->
      {
         if(method.getName().equals("close"))
         {
            return (null);
         }
         if(args != null && args.length == 1 && args[0] instanceof java.util.function.Consumer<?>)
         {
            return (InvocationHandler.invokeDefault(proxy, method, args));
         }
         calls.add(method.getName());
         assertThat(method.getName()).matches("listObjectsV2|getBucket.*|getObjectLockConfiguration|getObjectTagging");
         S3Request request = (S3Request) args[0];
         Duration timeout = request.overrideConfiguration().orElseThrow().apiCallTimeout().orElseThrow();
         assertThat(timeout).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(20));
         assertThat(request.overrideConfiguration().orElseThrow().apiCallAttemptTimeout()).contains(timeout);
         if(failure != null)
         {
            throw (failure);
         }
         if(method.getName().equals("listObjectsV2"))
         {
            if(expireAfterList)
            {
               Thread.sleep(Math.max(1, Duration.between(Instant.now(), deadline).toMillis() + 10));
            }
            return (ListObjectsV2Response.builder().sdkHttpResponse(SdkHttpResponse.builder().statusCode(200)
               .putHeader("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.now().minusSeconds(skew).atZone(ZoneOffset.UTC))).build()).build());
         }
         return (((SdkBuilder<?, ?>) method.getReturnType().getMethod("builder").invoke(null)).build());
      });
      Field field = ConnectionRuntime.class.getDeclaredField("client");
      field.setAccessible(true);
      field.set(ObjectStorageRuntime.connection("PRIMARY"), client);
   }



   /*******************************************************************************
    ** Restore process-wide fixtures and the poisoned property.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      StorageTestSupport.tearDown();
      System.clearProperty("os.health.secret");
   }



   /*******************************************************************************
    ** Producer registration includes the required metadata and keeps both actions.
    *******************************************************************************/
   @Test
   void registrationAndRefresh() throws Exception
   {
      var registry = HealthChecksMetaData.ofOrWithNew(instance);
      assertThat(registry.getSuppliers()).singleElement().satisfies(supplier ->
      {
         assertThat(supplier.getNamePrefix()).isEqualTo("object_storage.connection");
         assertThat(supplier.getComponent()).isEqualTo("object_storage");
      });
      HealthCheckMetaData check = check();
      assertThat(check.getName()).isEqualTo("object_storage.connection." + ConfigSync.connectionId("PRIMARY")).matches("[a-z0-9]+([._-][a-z0-9]+)*");
      assertThat(check.getKind()).isEqualTo(HealthCheckKind.DEPENDENCY);
      assertThat(check.getCategory()).isEqualTo(HealthCategory.STORAGE);
      assertThat(check.getComponent()).isEqualTo("object_storage");
      assertThat(check.getIntervalSeconds()).isEqualTo(300);
      assertThat(check.getTimeoutMs()).isEqualTo(20000);
      assertThat(instance.getProcess(TestConnectionProcess.READ_NAME)).isNotNull();
      assertThat(instance.getProcess(TestConnectionProcess.WRITE_NAME)).isNotNull();
      ConfigSync.sync();
      assertThat(registry.resolveAll(instance)).hasSize(1);
      config.withConnection(new StorageConnectionConfig("SECOND"));
      ConfigSync.sync();
      assertThat(registry.resolveAll(instance)).hasSize(2);
      config.getConnections().clear();
      assertThat(registry.resolveAll(instance)).isEmpty();
      assertClean(check.getCheckInstance().run(input()), HealthStatus.UNKNOWN);
   }



   /*******************************************************************************
    ** Successful checks read buckets without persisting any capability diagnostics.
    *******************************************************************************/
   @Test
   void upReadOnlyAndNoSecrets() throws Exception
   {
      HealthCheckOutcome outcome = run();
      assertClean(outcome, HealthStatus.UP);
      assertThat(outcome.getDetails().getValues()).containsEntry("bucketsProbed", 1);
      assertThat(calls).contains("listObjectsV2", "getBucketVersioning", "getBucketAcl");
      assertThat(Db.asSystem(() -> Db.query(ObjectStorageTables.CONNECTION_CAPABILITY))).isEmpty();
      assertThat(Db.asSystem(() -> Db.query(ObjectStorageTables.BUCKET_CAPABILITY))).isEmpty();
      assertThat(Db.asSystem(() -> Db.first(ObjectStorageTables.CONNECTION, "name", "PRIMARY")).getValue("lastTestedAt")).isNull();
   }



   /*******************************************************************************
    ** Clock skew uses the same threshold as the record action.
    *******************************************************************************/
   @Test
   void clockSkew() throws Exception
   {
      skew = CapabilityProbe.CLOCK_SKEW_THRESHOLD_SECONDS - 2;
      assertClean(run(), HealthStatus.UP);
      skew = CapabilityProbe.CLOCK_SKEW_THRESHOLD_SECONDS + 10;
      HealthCheckOutcome outcome = run();
      assertClean(outcome, HealthStatus.DEGRADED);
      assertThat((Long) outcome.getDetails().getValues().get("clockSkewSeconds")).isGreaterThan(900);
   }



   /*******************************************************************************
    ** Expected failures carry only a fixed kind even when errors contain secrets.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = { "auth", "tls", "dns", "clock", "connection" })
   void downAndNoSecrets(String kind) throws Exception
   {
      failure = switch(kind)
      {
         case "auth" -> (S3Exception) S3Exception.builder().statusCode(403).message(SECRET).build();
         case "tls" -> new IllegalStateException(SECRET, new SSLHandshakeException(SECRET));
         case "dns" -> new IllegalStateException(SECRET, new UnknownHostException(SECRET));
         case "clock" -> (S3Exception) S3Exception.builder().statusCode(403).message(SECRET)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode("RequestTimeTooSkewed").build()).build();
         default -> new IllegalStateException(SECRET);
      };
      skew = 1000;
      HealthCheckOutcome outcome = run();
      assertClean(outcome, HealthStatus.DOWN);
      assertThat(outcome.getDetails().getValues()).containsEntry("problemKind", kind);
      assertThat(Db.asSystem(() -> Db.query(ObjectStorageTables.CONNECTION_CAPABILITY))).isEmpty();
   }



   /*******************************************************************************
    ** An empty configuration cannot establish connectivity.
    *******************************************************************************/
   @Test
   void noBuckets() throws Exception
   {
      config.getBuckets().clear();
      assertClean(run(), HealthStatus.UNKNOWN);
      assertThat(calls).isEmpty();
   }



   /*******************************************************************************
    ** Client initialization errors also cannot expose poisoned configuration.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void clientInitializationFailure() throws Exception
   {
      Field connections = ObjectStorageRuntime.class.getDeclaredField("CONNECTIONS");
      connections.setAccessible(true);
      ((Map<String, ConnectionRuntime>) connections.get(null)).put("PRIMARY", new ConnectionRuntime(config.getConnections().getFirst())
      {
         /*******************************************************************************
          ** Simulate a credential or endpoint failure before any request is sent.
          *******************************************************************************/
         @Override
         public synchronized S3Client client()
         {
            throw (new IllegalStateException(SECRET));
         }
      });
      assertClean(run(), HealthStatus.DOWN);
      assertThat(calls).isEmpty();
   }



   /*******************************************************************************
    ** Expired checks perform no I/O; a deadline reached mid-probe stops SDK calls.
    *******************************************************************************/
   @Test
   void deadlines() throws Exception
   {
      assertClean(check().getCheckInstance().run(input().withDeadline(Instant.now().minusSeconds(1))), HealthStatus.DOWN);
      assertThat(calls).isEmpty();
      expireAfterList = true;
      deadline = Instant.now().plusMillis(500);
      HealthCheckOutcome outcome = check().getCheckInstance().run(input().withDeadline(deadline));
      assertClean(outcome, HealthStatus.DOWN);
      assertThat(outcome.getDetails().getValues()).containsEntry("problemKind", "timeout");
      assertThat(calls).containsExactly("listObjectsV2");
   }



   /*******************************************************************************
    ** Rate limiting also respects the budget, both for slots and depleted tokens.
    *******************************************************************************/
   @Test
   void rateLimiterDeadline() throws Exception
   {
      RateLimiter limiter = ObjectStorageRuntime.connection("PRIMARY").getRateLimiter();
      try(var held = limiter.acquire())
      {
         assertClean(check().getCheckInstance().run(input().withDeadline(Instant.now().plusMillis(50))), HealthStatus.DOWN);
         assertThat(calls).isEmpty();
      }
      RateLimiter onePerSecond = new RateLimiter(1, 1);
      onePerSecond.acquire().close();
      assertThatThrownBy(() -> onePerSecond.acquire(Instant.now().plusMillis(20))).isInstanceOf(IllegalStateException.class);
      try(var permit = onePerSecond.acquire(Instant.now().plusSeconds(2)))
      {
         assertThat(permit).isNotNull();
      }
   }



   /*******************************************************************************
    ** Fetch checks through the registry, exercising the supplier code reference.
    *******************************************************************************/
   private HealthCheckMetaData check() throws Exception
   {
      return (HealthChecksMetaData.ofOrWithNew(instance).resolveAll(instance).getFirst());
   }



   /*******************************************************************************
    ** Default bounded input.
    *******************************************************************************/
   private HealthCheckInput input()
   {
      return (new HealthCheckInput().withQInstance(instance).withDeadline(Instant.now().plusSeconds(20)));
   }



   /*******************************************************************************
    ** Run the registered check.
    *******************************************************************************/
   private HealthCheckOutcome run() throws Exception
   {
      return (check().getCheckInstance().run(input()));
   }



   /*******************************************************************************
    ** HealthRunner.sanitize is package-private; enforce cleanliness on raw output.
    *******************************************************************************/
   private void assertClean(HealthCheckOutcome outcome, HealthStatus expected)
   {
      assertThat(outcome.getStatus()).isEqualTo(expected);
      assertThat(outcome.getMessage() + outcome.getDetails().toJson()).doesNotContain(SECRET, "private.example", "private-bucket");
      assertThat(outcome.getMessage()).doesNotContain("\n", "\r");
   }
}
