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


import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.api.Capability;
import com.kingsrook.qbits.objectstorage.api.CapabilityStatus;
import com.kingsrook.qbits.objectstorage.config.ProviderType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;


/*******************************************************************************
 ** One S3-compatible store the conformance suite runs against: its connection
 ** settings, the scratch buckets, and the capability set the probe is EXPECTED
 ** to report. The suite asserts the probe equals this set.
 *******************************************************************************/
public class ConformanceTarget
{
   public String       name;
   public ProviderType providerType;
   public String       endpoint;
   public String       accessKey;
   public String       secretKey;
   public String       region;

   /** The scratch bucket for most tests. */
   public String bucket;
   /** A versioned bucket, or null when the provider has no versioning. */
   public String versionedBucket;
   /** A private bucket used as the soft-delete trash, when there is no versioning. */
   public String trashBucket;
   /** A bucket with object lock enabled, or null. */
   public String lockBucket;

   /** What the probe must report for the scratch bucket. */
   public final Map<Capability, CapabilityStatus> expected = new EnumMap<>(Capability.class);

   /** Whether WHEN_SUPPORTED (SDK default checksums) is expected to work. */
   public boolean checksumsSupportedWorks = false;



   /*******************************************************************************
    ** A raw admin client (always WHEN_REQUIRED), for creating buckets and
    ** inspecting results independently of the code under test.
    *******************************************************************************/
   public S3Client rawClient()
   {
      return (S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.of(region)).forcePathStyle(true)
         .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
         .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED).responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED).build());
   }
}
