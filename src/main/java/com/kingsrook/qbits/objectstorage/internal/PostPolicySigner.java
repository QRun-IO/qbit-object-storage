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


import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import com.kingsrook.qbits.objectstorage.api.PresignedPost;
import com.kingsrook.qbits.objectstorage.api.StorageException;
import com.kingsrook.qbits.objectstorage.config.AddressingStyle;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Signs a browser-form POST policy (SigV4) with a content-length-range
 ** condition, which can express a maximum upload size. The AWS SDK v2 has no
 ** helper for this.
 *******************************************************************************/
public final class PostPolicySigner
{
   private static final DateTimeFormatter DATE      = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
   private static final DateTimeFormatter AMZ_DATE  = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private PostPolicySigner()
   {
   }



   /*******************************************************************************
    ** Build the URL and form fields.
    *******************************************************************************/
   public static PresignedPost sign(String accessKey, String secretKey, String region, String endpoint, AddressingStyle style, String bucket, String key,
      String contentType, Long minBytes, Long maxBytes, Instant now, Duration ttl)
   {
      if(accessKey == null || secretKey == null)
      {
         throw (new StorageException(StorageException.Reason.ACCESS_DENIED, "No static credentials are available to sign a POST policy"));
      }
      String date       = DATE.format(now);
      String amzDate    = AMZ_DATE.format(now);
      String credential = accessKey + "/" + date + "/" + region + "/s3/aws4_request";
      String expiration = DateTimeFormatter.ISO_INSTANT.format(now.plus(ttl));

      StringBuilder policy = new StringBuilder("{\"expiration\":\"").append(expiration).append("\",\"conditions\":[");
      policy.append("{\"bucket\":\"").append(json(bucket)).append("\"},");
      policy.append("{\"key\":\"").append(json(key)).append("\"},");
      if(StringUtils.hasContent(contentType))
      {
         policy.append("{\"Content-Type\":\"").append(json(contentType)).append("\"},");
      }
      if(maxBytes != null)
      {
         policy.append("[\"content-length-range\",").append(minBytes == null ? 0 : minBytes).append(",").append(maxBytes).append("],");
      }
      policy.append("{\"x-amz-algorithm\":\"AWS4-HMAC-SHA256\"},{\"x-amz-credential\":\"").append(json(credential)).append("\"},{\"x-amz-date\":\"").append(amzDate).append("\"}]}");

      String policyBase64 = Base64.getEncoder().encodeToString(policy.toString().getBytes(StandardCharsets.UTF_8));
      byte[] signingKey   = hmac(hmac(hmac(hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), date), region), "s3"), "aws4_request");
      String signature    = HexFormat.of().formatHex(hmac(signingKey, policyBase64));

      PresignedPost post = new PresignedPost().withUrl(formUrl(endpoint, style, bucket, region));
      post.getFields().put("bucket", bucket);
      post.getFields().put("key", key);
      if(StringUtils.hasContent(contentType))
      {
         post.getFields().put("Content-Type", contentType);
      }
      post.getFields().put("x-amz-algorithm", "AWS4-HMAC-SHA256");
      post.getFields().put("x-amz-credential", credential);
      post.getFields().put("x-amz-date", amzDate);
      post.getFields().put("policy", policyBase64);
      post.getFields().put("x-amz-signature", signature);
      return (post);
   }



   /*******************************************************************************
    ** The form action URL for the endpoint and addressing style.
    *******************************************************************************/
   static String formUrl(String endpoint, AddressingStyle style, String bucket, String region)
   {
      if(!StringUtils.hasContent(endpoint))
      {
         return ("https://" + bucket + ".s3." + region + ".amazonaws.com/");
      }
      URI    uri  = URI.create(endpoint);
      String base = uri.getScheme() + "://" + (style == AddressingStyle.VIRTUAL_HOST ? bucket + "." : "") + uri.getAuthority();
      return (style == AddressingStyle.VIRTUAL_HOST ? base + "/" : base + "/" + bucket);
   }



   /*******************************************************************************
    ** HMAC-SHA256.
    *******************************************************************************/
   private static byte[] hmac(byte[] key, String data)
   {
      try
      {
         Mac mac = Mac.getInstance("HmacSHA256");
         mac.init(new SecretKeySpec(key, "HmacSHA256"));
         return (mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
      }
      catch(Exception e)
      {
         throw (new IllegalStateException("HmacSHA256 is not available", e));
      }
   }



   /*******************************************************************************
    ** Escape a JSON string value.
    *******************************************************************************/
   private static String json(String s)
   {
      return (s.replace("\\", "\\\\").replace("\"", "\\\""));
   }
}
