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


import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.objectstorage.config.AddressingStyle;
import com.kingsrook.qbits.objectstorage.config.ChecksumMode;
import com.kingsrook.qbits.objectstorage.config.CredentialsMode;
import com.kingsrook.qbits.objectstorage.config.ProviderType;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qqq.backend.module.filesystem.s3.model.metadata.S3BackendMetaData;
import com.kingsrook.qqq.backend.module.filesystem.s3.model.metadata.S3ChecksumMode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Mapping and lifecycle checks plus real SDK signing against a loopback server.
 *******************************************************************************/
class ConnectionRuntimeTest
{
   /*******************************************************************************
    ** Core reads ${file.PATH} only inside its allowed roots; reference() points
    ** them at each test's temporary directory.
    *******************************************************************************/
   @AfterEach
   void afterEach()
   {
      System.clearProperty("qqq.fileReference.roots");
   }



   /*******************************************************************************
    ** Every core-supported setting is mapped after resolving references. Session
    ** tokens and presign settings are exercised below.
    *******************************************************************************/
   @Test
   void testBackendMetaDataCarriesEverySetting(@TempDir Path dir) throws Exception
   {
      StorageConnectionConfig config = new StorageConnectionConfig("mapping")
         .withEndpoint(reference(dir, "endpoint", "https://storage.example.test"))
         .withRegion(reference(dir, "region", "eu-west-1"))
         .withAddressingStyle(AddressingStyle.VIRTUAL_HOST)
         .withChecksumMode(ChecksumMode.WHEN_SUPPORTED)
         .withTlsVerify(false)
         .withCaBundleRef(reference(dir, "ca", "test-pem-bundle"))
         .withCredentialsMode(CredentialsMode.STATIC_REFS)
         .withAccessKeyRef(reference(dir, "access", "test-access"))
         .withSecretKeyRef(reference(dir, "secret", "test-secret"))
         .withConnectTimeoutMs(1234)
         .withReadTimeoutMs(5678);
      S3BackendMetaData backend = new ConnectionRuntime(config).backendMetaData();
      assertThat(backend.getEndpoint()).isEqualTo("https://storage.example.test");
      assertThat(backend.getRegion()).isEqualTo("eu-west-1");
      assertThat(backend.getPathStyleAccess()).isFalse();
      assertThat(backend.getChecksumMode()).isEqualTo(S3ChecksumMode.WHEN_SUPPORTED);
      assertThat(backend.getTlsVerify()).isFalse();
      assertThat(backend.getCaCertificatesPem()).isEqualTo("test-pem-bundle");
      assertThat(backend.getAccessKey()).isEqualTo("test-access");
      assertThat(backend.getSecretKey()).isEqualTo("test-secret");
      assertThat(backend.getConnectTimeoutMillis()).isEqualTo(1234);
      assertThat(backend.getSocketTimeoutMillis()).isEqualTo(5678);
      assertThat(backend.getBucketName()).isNull();
   }



   /*******************************************************************************
    ** Provider defaults remain the qbit's defaults, including its 5s/60s timeouts.
    *******************************************************************************/
   @Test
   void testBackendDefaults()
   {
      S3BackendMetaData compatible = new ConnectionRuntime(new StorageConnectionConfig("compatible")
         .withEndpoint("http://localhost:9000")).backendMetaData();
      assertThat(compatible.getRegion()).isEqualTo("us-east-1");
      assertThat(compatible.getPathStyleAccess()).isTrue();
      assertThat(compatible.getChecksumMode()).isEqualTo(S3ChecksumMode.WHEN_REQUIRED);
      assertThat(compatible.getTlsVerify()).isTrue();
      assertThat(compatible.getCaCertificatesPem()).isNull();
      assertThat(compatible.getConnectTimeoutMillis()).isEqualTo(5000);
      assertThat(compatible.getSocketTimeoutMillis()).isEqualTo(60000);

      S3BackendMetaData aws = new ConnectionRuntime(new StorageConnectionConfig("aws").withProviderType(ProviderType.AWS_S3)).backendMetaData();
      assertThat(aws.getEndpoint()).isNull();
      assertThat(aws.getRegion()).isNull();
      assertThat(aws.getPathStyleAccess()).isFalse();
      assertThat(aws.getChecksumMode()).isEqualTo(S3ChecksumMode.WHEN_SUPPORTED);
   }



   /*******************************************************************************
    ** Default-chain and web-identity modes must ignore configured static refs.
    *******************************************************************************/
   @Test
   void testChainCredentialsIgnoreStaticRefs(@TempDir Path dir) throws Exception
   {
      for(CredentialsMode mode : List.of(CredentialsMode.DEFAULT_CHAIN, CredentialsMode.WEB_IDENTITY))
      {
         ConnectionRuntime runtime = new ConnectionRuntime(config(dir).withCredentialsMode(mode)
            .withSessionTokenRef(reference(dir, "token", "test-session")));
         try
         {
            assertThat(runtime.backendMetaData().getAccessKey()).isNull();
            assertThat(runtime.backendMetaData().getSecretKey()).isNull();
            assertThat(runtime.client().serviceClientConfiguration().credentialsProvider()).isInstanceOf(DefaultCredentialsProvider.class);
         }
         finally
         {
            runtime.close();
         }
      }
   }



   /*******************************************************************************
    ** Missing static refs preserve the prior fallback to the SDK default chain.
    *******************************************************************************/
   @Test
   void testMissingStaticCredentialsFallBack(@TempDir Path dir) throws Exception
   {
      ConnectionRuntime runtime = new ConnectionRuntime(config(dir).withSecretKeyRef(null));
      try
      {
         assertThat(runtime.client().serviceClientConfiguration().credentialsProvider()).isInstanceOf(DefaultCredentialsProvider.class);
      }
      finally
      {
         runtime.close();
      }
   }



   /*******************************************************************************
    ** Clients belong to a runtime, never core's shared cache; close rebuilds from
    ** changed settings and does not affect another connection with equal settings.
    *******************************************************************************/
   @Test
   void testClientOwnershipAndRebuild(@TempDir Path dir) throws Exception
   {
      StorageConnectionConfig config = config(dir);
      ConnectionRuntime runtime = new ConnectionRuntime(config);
      ConnectionRuntime sibling = new ConnectionRuntime(config);
      try
      {
         S3Client first = runtime.client();
         assertThat(runtime.client()).isSameAs(first);
         assertThat(sibling.client()).isNotSameAs(first);
         assertThat(first.serviceClientConfiguration().endpointOverride()).contains(URI.create("http://localhost:9000"));
         assertThat(first.serviceClientConfiguration().region()).isEqualTo(Region.US_EAST_1);
         assertThat(((AwsCredentialsProvider) first.serviceClientConfiguration().credentialsProvider()).resolveCredentials().accessKeyId()).isEqualTo("test-access");
         assertThat(first.serviceClientConfiguration().requestChecksumCalculation()).isEqualTo(RequestChecksumCalculation.WHEN_REQUIRED);
         assertThat(first.serviceClientConfiguration().responseChecksumValidation()).isEqualTo(ResponseChecksumValidation.WHEN_REQUIRED);
         runtime.close();
         config.withRegion("eu-west-1").withChecksumMode(ChecksumMode.WHEN_SUPPORTED);
         assertThat(runtime.client()).isNotSameAs(first);
         assertThat(runtime.client().serviceClientConfiguration().region()).isEqualTo(Region.EU_WEST_1);
         assertThat(runtime.client().serviceClientConfiguration().requestChecksumCalculation()).isEqualTo(RequestChecksumCalculation.WHEN_SUPPORTED);
         assertThat(sibling.client().serviceClientConfiguration().region()).isEqualTo(Region.US_EAST_1);
      }
      finally
      {
         runtime.close();
         sibling.close();
      }
   }



   /*******************************************************************************
    ** Session credentials (core sessionToken) sign streaming reads/writes and
    ** paginator requests; request overrides are untouched.
    *******************************************************************************/
   @Test
   void testSessionSigningAndRequestOverrides(@TempDir Path dir) throws Exception
   {
      List<Headers> requests = new ArrayList<>();
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange ->
      {
         requests.add(exchange.getRequestHeaders());
         exchange.getRequestBody().readAllBytes();
         byte[] response = (exchange.getRequestURI().getQuery() != null && exchange.getRequestURI().getQuery().contains("list-type")
            ? "<ListBucketResult><IsTruncated>false</IsTruncated></ListBucketResult>" : "data").getBytes(StandardCharsets.UTF_8);
         exchange.sendResponseHeaders(200, response.length);
         exchange.getResponseBody().write(response);
         exchange.close();
      });
      server.start();
      ConnectionRuntime runtime = new ConnectionRuntime(config(dir)
         .withEndpoint("http://127.0.0.1:" + server.getAddress().getPort())
         .withSessionTokenRef(reference(dir, "token", "test-session")));
      try
      {
         GetObjectRequest request = GetObjectRequest.builder().bucket("bucket").key("key")
            .overrideConfiguration(o -> o.putHeader("x-test-preserved", "yes")).build();
         assertThat(runtime.client().getObjectAsBytes(request).asUtf8String()).isEqualTo("data");
         runtime.client().putObject(o -> o.bucket("bucket").key("key"), RequestBody.fromString("upload"));
         assertThat(runtime.client().listObjectsV2Paginator(ListObjectsV2Request.builder().bucket("bucket").build()).stream().count()).isEqualTo(1);
         assertThat(requests).hasSize(3);
         for(Headers headers : requests)
         {
            assertThat(headers.getFirst("x-amz-security-token")).isEqualTo("test-session");
            assertThat(headers.getFirst("Authorization")).contains("Credential=test-access/");
         }
         assertThat(requests.getFirst().getFirst("x-test-preserved")).isEqualTo("yes");
         assertThat(request.overrideConfiguration().orElseThrow().credentialsProvider()).isEmpty();
      }
      finally
      {
         runtime.close();
         server.stop(0);
      }
   }



   /*******************************************************************************
    ** Presigning keeps its separate public endpoint/key and session-token support.
    *******************************************************************************/
   @Test
   void testPresignerRemainsLocal(@TempDir Path dir) throws Exception
   {
      ConnectionRuntime runtime = new ConnectionRuntime(config(dir)
         .withPresignEndpoint("https://public.example.test")
         .withPresignAccessKeyRef(reference(dir, "presign-access", "presign-access"))
         .withPresignSecretKeyRef(reference(dir, "presign-secret", "presign-secret"))
         .withSessionTokenRef(reference(dir, "token", "test-session")));
      try
      {
         assertThat(runtime.presigner()).isSameAs(runtime.presigner());
         String url = runtime.presigner().presignGetObject(p -> p.signatureDuration(Duration.ofMinutes(5))
            .getObjectRequest(g -> g.bucket("bucket").key("key"))).url().toString();
         assertThat(url).startsWith("https://public.example.test/bucket/key?")
            .contains("X-Amz-Credential=presign-access%2F", "X-Amz-Security-Token=test-session");
         assertThat(runtime.presignCredentials()).containsExactly("presign-access", "presign-secret", "us-east-1", "https://public.example.test");
      }
      finally
      {
         runtime.close();
      }
   }



   /*******************************************************************************
    ** Core handles invalid CA bundles and the explicit TLS-verification opt-out.
    *******************************************************************************/
   @Test
   void testTlsSettingsReachCore(@TempDir Path dir) throws Exception
   {
      StorageConnectionConfig config = config(dir).withCaBundleRef(reference(dir, "ca", "invalid-pem"));
      ConnectionRuntime runtime = new ConnectionRuntime(config);
      assertThatThrownBy(runtime::client).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("caCertificatesPem");
      config.withTlsVerify(false);
      try
      {
         assertThat(runtime.client()).isNotNull();
      }
      finally
      {
         runtime.close();
      }
   }



   /*******************************************************************************
    ** Deterministic test connection without any external credentials or service.
    *******************************************************************************/
   private static StorageConnectionConfig config(Path dir) throws Exception
   {
      return (new StorageConnectionConfig("test").withEndpoint("http://localhost:9000")
         .withAccessKeyRef(reference(dir, "access", "test-access"))
         .withSecretKeyRef(reference(dir, "secret", "test-secret")));
   }



   /*******************************************************************************
    ** Mounted-file reference for test data, scoped to JUnit's temporary directory.
    *******************************************************************************/
   private static String reference(Path dir, String name, String value) throws Exception
   {
      System.setProperty("qqq.fileReference.roots", dir.toString());
      Path file = dir.resolve(name);
      Files.writeString(file, value);
      return ("${file." + file + "}");
   }
}
