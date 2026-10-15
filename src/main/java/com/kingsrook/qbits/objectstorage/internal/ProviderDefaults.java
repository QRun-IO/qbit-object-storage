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


import java.util.EnumMap;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.api.Capability;
import com.kingsrook.qbits.objectstorage.api.CapabilityStatus;
import com.kingsrook.qbits.objectstorage.config.ProviderType;
import com.kingsrook.qqq.backend.core.cluster.ClusterCaches;
import static com.kingsrook.qbits.objectstorage.api.CapabilityStatus.PARTIAL;
import static com.kingsrook.qbits.objectstorage.api.CapabilityStatus.SUPPORTED;
import static com.kingsrook.qbits.objectstorage.api.CapabilityStatus.UNKNOWN;
import static com.kingsrook.qbits.objectstorage.api.CapabilityStatus.UNSUPPORTED;


/*******************************************************************************
 ** Seed capabilities per provider type (spec section 3.3). Cells the provider's
 ** documentation does not state clearly are UNKNOWN, never a guess. The probe
 ** overrides every one of these.
 *******************************************************************************/
public final class ProviderDefaults
{
   private static final Map<ProviderType, Map<Capability, CapabilityStatus>> TABLE = ClusterCaches.nodeLocal(new EnumMap<>(ProviderType.class),
      "provider defaults from code, the same on every node");

   static
   {
      //                       versioning  lock         lifecycle    policy       cors         acl          tagging      post         conditional
      put(ProviderType.AWS_S3, SUPPORTED, SUPPORTED, SUPPORTED, SUPPORTED, SUPPORTED, PARTIAL, SUPPORTED, SUPPORTED, SUPPORTED);
      put(ProviderType.MINIO, SUPPORTED, UNKNOWN, SUPPORTED, PARTIAL, UNKNOWN, PARTIAL, SUPPORTED, SUPPORTED, SUPPORTED);
      put(ProviderType.CLOUDFLARE_R2, UNSUPPORTED, UNSUPPORTED, PARTIAL, UNSUPPORTED, SUPPORTED, UNSUPPORTED, UNSUPPORTED, UNKNOWN, UNKNOWN);
      put(ProviderType.BACKBLAZE_B2, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, PARTIAL, UNSUPPORTED, UNSUPPORTED, UNKNOWN);
      put(ProviderType.WASABI, SUPPORTED, SUPPORTED, UNKNOWN, SUPPORTED, SUPPORTED, PARTIAL, SUPPORTED, UNKNOWN, UNKNOWN);
      put(ProviderType.DO_SPACES, SUPPORTED, UNKNOWN, PARTIAL, PARTIAL, SUPPORTED, PARTIAL, UNKNOWN, UNKNOWN, UNKNOWN);
      put(ProviderType.CEPH_RGW, SUPPORTED, SUPPORTED, SUPPORTED, SUPPORTED, SUPPORTED, SUPPORTED, SUPPORTED, SUPPORTED, UNKNOWN);
      put(ProviderType.GARAGE, UNSUPPORTED, UNSUPPORTED, PARTIAL, UNSUPPORTED, SUPPORTED, UNSUPPORTED, UNSUPPORTED, SUPPORTED, UNKNOWN);
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ProviderDefaults()
   {
   }



   /*******************************************************************************
    ** One provider's row.
    *******************************************************************************/
   private static void put(ProviderType type, CapabilityStatus versioning, CapabilityStatus lock, CapabilityStatus lifecycle, CapabilityStatus policy,
      CapabilityStatus cors, CapabilityStatus acl, CapabilityStatus tagging, CapabilityStatus post, CapabilityStatus conditional)
   {
      Map<Capability, CapabilityStatus> m = new EnumMap<>(Capability.class);
      m.put(Capability.VERSIONING, versioning);
      m.put(Capability.OBJECT_LOCK, lock);
      m.put(Capability.LIFECYCLE, lifecycle);
      m.put(Capability.BUCKET_POLICY, policy);
      m.put(Capability.CORS, cors);
      m.put(Capability.ACL, acl);
      m.put(Capability.OBJECT_TAGGING, tagging);
      m.put(Capability.PRESIGNED_POST, post);
      m.put(Capability.CONDITIONAL_WRITE, conditional);
      TABLE.put(type, m);
   }



   /*******************************************************************************
    ** The default status of a capability for a provider type.
    *******************************************************************************/
   public static CapabilityStatus defaultStatus(ProviderType type, Capability capability)
   {
      switch(capability)
      {
         case LIST_V2, PUT_GET_DELETE, COPY_REPLACE_METADATA, BATCH_DELETE, MULTIPART, PRESIGNED_GET, PRESIGNED_PUT ->
         {
            return (type == ProviderType.OTHER ? UNKNOWN : SUPPORTED);
         }
         case USAGE_ADMIN_API, QUOTA_ADMIN_API ->
         {
            return (UNSUPPORTED);
         }
         default ->
         {
            Map<Capability, CapabilityStatus> m = TABLE.get(type);
            return (m == null ? UNKNOWN : m.getOrDefault(capability, UNKNOWN));
         }
      }
   }
}
