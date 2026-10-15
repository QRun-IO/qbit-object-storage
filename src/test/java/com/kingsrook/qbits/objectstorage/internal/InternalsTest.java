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


import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.kingsrook.qbits.objectstorage.api.Capability;
import com.kingsrook.qbits.objectstorage.api.CapabilityStatus;
import com.kingsrook.qbits.objectstorage.api.PresignedPost;
import com.kingsrook.qbits.objectstorage.api.StorageException;
import com.kingsrook.qbits.objectstorage.config.AddressingStyle;
import com.kingsrook.qbits.objectstorage.config.ChecksumMode;
import com.kingsrook.qbits.objectstorage.config.ProviderType;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.model.S3Exception;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Unit tests for the small internals: secret references, globs, the rate
 ** limiter, provider defaults, error mapping and POST policy signing.
 *******************************************************************************/
class InternalsTest
{
   /*******************************************************************************
    ** Only ${...} references matching the allow-list are accepted; a raw value is not.
    *******************************************************************************/
   @Test
   void testSecretRefAllowList()
   {
      List<String> allowed = SecretRefs.DEFAULT_ALLOWED_PATTERNS;
      assertThat(SecretRefs.isAllowed("${env.S3_SECRET_KEY}", allowed)).isTrue();
      assertThat(SecretRefs.isAllowed("${env.STORAGE_KEY}", allowed)).isTrue();
      assertThat(SecretRefs.isAllowed("${file./var/run/secrets/storage/key}", allowed)).isTrue();
      assertThat(SecretRefs.isAllowed("${env.AWS_SECRET_ACCESS_KEY}", allowed)).isFalse();
      assertThat(SecretRefs.isAllowed("${env.DATABASE_PASSWORD}", allowed)).isFalse();
      assertThat(SecretRefs.isAllowed("plain-literal-secret", allowed)).isFalse();
      assertThat(SecretRefs.isAllowed(null, allowed)).isTrue();
      assertThat(SecretRefs.isAllowed("", allowed)).isTrue();
   }



   /*******************************************************************************
    ** References resolve from properties and mounted files; unset ones are null.
    *******************************************************************************/
   @Test
   void testSecretRefResolution(@TempDir Path dir) throws Exception
   {
      System.setProperty("os.unit.secret", "s3cr3t");
      assertThat(SecretRefs.resolve("${prop.os.unit.secret}")).isEqualTo("s3cr3t");
      assertThat(SecretRefs.resolve("${prop.os.unit.not.set}")).isNull();
      assertThat(SecretRefs.resolve(null)).isNull();
      assertThat(SecretRefs.resolveSetting("")).isNull();

      //////////////////////////////////////////////////////////////////////
      // core reads ${file.PATH} only inside its allowed roots (by default //
      // /var/run/secrets and /run/secrets); point them at the temp dir.   //
      //////////////////////////////////////////////////////////////////////
      Path file = dir.resolve("key");
      Files.writeString(file, "  from-file\n");
      assertThat(SecretRefs.resolve("${file." + file + "}")).as("outside the allowed roots").isNull();
      System.setProperty("qqq.fileReference.roots", dir.toString());
      try
      {
         assertThat(SecretRefs.resolve("${file." + file + "}")).isEqualTo("from-file");
         assertThat(SecretRefs.resolve("${file." + dir.resolve("missing") + "}")).isNull();
      }
      finally
      {
         System.clearProperty("qqq.fileReference.roots");
      }
   }



   /*******************************************************************************
    ** Globs: * crosses slashes, ? is one character, the rest is literal.
    *******************************************************************************/
   @Test
   void testGlobs()
   {
      assertThat(Globs.matches("media/*", "media/42/hero.jpg")).isTrue();
      assertThat(Globs.matches("*.jpg", "a/b.jpg")).isTrue();
      assertThat(Globs.matches("a?c", "abc")).isTrue();
      assertThat(Globs.matches("a?c", "abbc")).isFalse();
      assertThat(Globs.matches("a.c", "abc")).isFalse();
      assertThat(Globs.matches("*", null)).isTrue();
   }



   /*******************************************************************************
    ** The token bucket slows callers down and the permit releases.
    *******************************************************************************/
   @Test
   void testRateLimiter()
   {
      RateLimiter limiter = new RateLimiter(50, 2);
      long start = System.nanoTime();
      for(int i = 0; i < 60; i++)
      {
         try(RateLimiter.Permit permit = limiter.acquire())
         {
            assertThat(permit).isNotNull();
         }
      }
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThan(Duration.ofMillis(100));
      assertThat(limiter.getThrottledWaits()).isGreaterThan(0);
   }



   /*******************************************************************************
    ** Provider defaults: checksums, addressing, and the documented capability cells.
    *******************************************************************************/
   @Test
   void testProviderDefaults()
   {
      assertThat(new StorageConnectionConfig("a").withProviderType(ProviderType.AWS_S3).effectiveChecksumMode()).isEqualTo(ChecksumMode.WHEN_SUPPORTED);
      for(ProviderType type : ProviderType.values())
      {
         if(type != ProviderType.AWS_S3)
         {
            assertThat(new StorageConnectionConfig("a").withProviderType(type).effectiveChecksumMode()).as(type.name()).isEqualTo(ChecksumMode.WHEN_REQUIRED);
         }
      }
      assertThat(new StorageConnectionConfig("a").withProviderType(ProviderType.MINIO).effectiveAddressingStyle()).isEqualTo(AddressingStyle.PATH);
      assertThat(new StorageConnectionConfig("a").withProviderType(ProviderType.CLOUDFLARE_R2).effectiveAddressingStyle()).isEqualTo(AddressingStyle.VIRTUAL_HOST);
      assertThat(new StorageConnectionConfig("a").withProviderType(ProviderType.GARAGE).withAddressingStyle(AddressingStyle.VIRTUAL_HOST).effectiveAddressingStyle()).isEqualTo(AddressingStyle.VIRTUAL_HOST);

      assertThat(ProviderDefaults.defaultStatus(ProviderType.GARAGE, Capability.VERSIONING)).isEqualTo(CapabilityStatus.UNSUPPORTED);
      assertThat(ProviderDefaults.defaultStatus(ProviderType.CLOUDFLARE_R2, Capability.LIFECYCLE)).isEqualTo(CapabilityStatus.PARTIAL);
      assertThat(ProviderDefaults.defaultStatus(ProviderType.AWS_S3, Capability.OBJECT_LOCK)).isEqualTo(CapabilityStatus.SUPPORTED);
      assertThat(ProviderDefaults.defaultStatus(ProviderType.BACKBLAZE_B2, Capability.LIFECYCLE)).isEqualTo(CapabilityStatus.UNKNOWN);
      assertThat(ProviderDefaults.defaultStatus(ProviderType.OTHER, Capability.PUT_GET_DELETE)).isEqualTo(CapabilityStatus.UNKNOWN);
      assertThat(ProviderDefaults.defaultStatus(ProviderType.MINIO, Capability.PUT_GET_DELETE)).isEqualTo(CapabilityStatus.SUPPORTED);
      assertThat(ProviderDefaults.defaultStatus(ProviderType.MINIO, Capability.USAGE_ADMIN_API)).isEqualTo(CapabilityStatus.UNSUPPORTED);
      assertThat(ProviderDefaults.defaultStatus(ProviderType.OTHER, Capability.CORS)).isEqualTo(CapabilityStatus.UNKNOWN);
   }



   /*******************************************************************************
    ** SDK exceptions map to the stable reasons.
    *******************************************************************************/
   @Test
   void testErrorMapping()
   {
      assertThat(ErrorMapper.map(s3(404, "NoSuchKey"), "x").getReason()).isEqualTo(StorageException.Reason.NOT_FOUND);
      assertThat(ErrorMapper.map(s3(403, "AccessDenied"), "x").getReason()).isEqualTo(StorageException.Reason.ACCESS_DENIED);
      assertThat(ErrorMapper.map(s3(412, "PreconditionFailed"), "x").getReason()).isEqualTo(StorageException.Reason.PRECONDITION_FAILED);
      assertThat(ErrorMapper.map(s3(503, "SlowDown"), "x").getReason()).isEqualTo(StorageException.Reason.THROTTLED);
      assertThat(ErrorMapper.map(s3(501, "NotImplemented"), "x").getReason()).isEqualTo(StorageException.Reason.UNSUPPORTED);
      assertThat(ErrorMapper.map(s3(500, "InternalError"), "x").getReason()).isEqualTo(StorageException.Reason.PROVIDER_ERROR);
      assertThat(ErrorMapper.map(new IllegalStateException("boom"), "x").getReason()).isEqualTo(StorageException.Reason.PROVIDER_ERROR);
      StorageException passthrough = new StorageException(StorageException.Reason.UNSUPPORTED, "u");
      assertThat(ErrorMapper.map(passthrough, "x")).isSameAs(passthrough);
      assertThat(ErrorMapper.errorCode(new RuntimeException())).isNull();
      assertThat(ErrorMapper.map(s3(404, "NoSuchKey"), "head k").getRequestId()).isEqualTo("req-1");
   }



   /*******************************************************************************
    ** An S3 exception with a status and code.
    *******************************************************************************/
   private static S3Exception s3(int status, String code)
   {
      return ((S3Exception) S3Exception.builder().statusCode(status).requestId("req-1").awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build()).message(code).build());
   }



   /*******************************************************************************
    ** A POST policy: the signature verifies independently, the size range is in the
    ** policy, and the form URL follows the addressing style.
    *******************************************************************************/
   @Test
   void testPostPolicySigning() throws Exception
   {
      Instant now = Instant.parse("2026-10-12T10:15:30Z");
      PresignedPost post = PostPolicySigner.sign("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "us-east-1", "http://minio.local:9000", AddressingStyle.PATH,
         "uploads", "in/a \"q\".txt", "image/png", 1L, 5000L, now, Duration.ofMinutes(10));

      assertThat(post.getUrl()).isEqualTo("http://minio.local:9000/uploads");
      assertThat(post.getFields()).containsEntry("key", "in/a \"q\".txt").containsEntry("bucket", "uploads").containsEntry("Content-Type", "image/png")
         .containsEntry("x-amz-algorithm", "AWS4-HMAC-SHA256").containsEntry("x-amz-date", "20261012T101530Z")
         .containsEntry("x-amz-credential", "AKIDEXAMPLE/20261012/us-east-1/s3/aws4_request");
      String policyJson = new String(java.util.Base64.getDecoder().decode(post.getFields().get("policy")), StandardCharsets.UTF_8);
      assertThat(policyJson).contains("\"expiration\":\"2026-10-12T10:25:30Z\"").contains("[\"content-length-range\",1,5000]").contains("{\"bucket\":\"uploads\"}")
         .contains("{\"key\":\"in/a \\\"q\\\".txt\"}");

      byte[] k = hmac(hmac(hmac(hmac("AWS4wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY".getBytes(StandardCharsets.UTF_8), "20261012"), "us-east-1"), "s3"), "aws4_request");
      assertThat(post.getFields().get("x-amz-signature")).isEqualTo(HexFormat.of().formatHex(hmac(k, post.getFields().get("policy"))));

      assertThat(PostPolicySigner.sign("a", "b", "eu-west-1", null, AddressingStyle.VIRTUAL_HOST, "bkt", "k", null, null, null, now, Duration.ofMinutes(1)).getUrl())
         .isEqualTo("https://bkt.s3.eu-west-1.amazonaws.com/");
      assertThat(PostPolicySigner.sign("a", "b", "r", "https://s3.example.com", AddressingStyle.VIRTUAL_HOST, "bkt", "k", null, null, null, now, Duration.ofMinutes(1)).getUrl())
         .isEqualTo("https://bkt.s3.example.com/");
      assertThatThrownBy(() -> PostPolicySigner.sign(null, null, "r", null, AddressingStyle.PATH, "b", "k", null, null, null, now, Duration.ofMinutes(1)))
         .isInstanceOf(StorageException.class);
   }



   /*******************************************************************************
    ** HMAC-SHA256.
    *******************************************************************************/
   private static byte[] hmac(byte[] key, String data) throws Exception
   {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return (mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
   }
}
