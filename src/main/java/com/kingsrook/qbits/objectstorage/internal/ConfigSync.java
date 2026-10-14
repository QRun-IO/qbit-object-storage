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


import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.config.CredentialsMode;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Upserts one object_storage_connection row and one object_storage_bucket row
 ** per configured entry, so the tables always show the real configuration.
 ** Identity and secret-reference fields come from code and are read-only in the
 ** UI; operational fields (rate limits, timeouts, presign TTLs, thresholds) are
 ** set on insert and then belong to the row.
 *******************************************************************************/
public final class ConfigSync
{
   private static final QLogger LOG = QLogger.getLogger(ConfigSync.class);

   private static final Map<String, Integer> BUCKET_IDS     = new ConcurrentHashMap<>();
   private static final Map<String, Integer> CONNECTION_IDS = new ConcurrentHashMap<>();

   private static volatile boolean synced = false;



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ConfigSync()
   {
   }



   /*******************************************************************************
    ** Forget what was synced (tests, reinstall).
    *******************************************************************************/
   public static void reset()
   {
      synced = false;
      BUCKET_IDS.clear();
      CONNECTION_IDS.clear();
   }



   /*******************************************************************************
    ** Sync once per process (and per context that has the tables); never throws.
    *******************************************************************************/
   public static void ensureSynced()
   {
      if(!synced && Db.available(ObjectStorageTables.CONNECTION))
      {
         try
         {
            sync();
         }
         catch(Exception e)
         {
            LOG.warn("Could not sync the storage configuration to its tables", e);
         }
      }
   }



   /*******************************************************************************
    ** Upsert every configured connection and bucket.
    *******************************************************************************/
   public static synchronized void sync() throws QException
   {
      ObjectStorageQBitConfig config = ObjectStorageRuntime.getConfig();
      Db.asSystem(() ->
      {
         for(StorageConnectionConfig connection : config.getConnections())
         {
            CONNECTION_IDS.put(connection.getName(), upsertConnection(connection, config));
         }
         for(StorageBucketConfig bucket : config.getBuckets())
         {
            BUCKET_IDS.put(bucket.getAlias(), upsertBucket(bucket, config));
         }
         return (null);
      });
      synced = true;
   }



   /*******************************************************************************
    ** The row id of a bucket alias (syncing first if needed), or null.
    *******************************************************************************/
   public static Integer bucketId(String alias)
   {
      Integer id = BUCKET_IDS.get(alias);
      if(id == null)
      {
         ensureSynced();
         id = BUCKET_IDS.get(alias);
      }
      return (id);
   }



   /*******************************************************************************
    ** The row id of a connection name (syncing first if needed), or null.
    *******************************************************************************/
   public static Integer connectionId(String name)
   {
      Integer id = CONNECTION_IDS.get(name);
      if(id == null)
      {
         ensureSynced();
         id = CONNECTION_IDS.get(name);
      }
      return (id);
   }



   /*******************************************************************************
    ** The alias of a bucket row id, or null.
    *******************************************************************************/
   public static String aliasOf(Integer bucketId)
   {
      for(Map.Entry<String, Integer> entry : BUCKET_IDS.entrySet())
      {
         if(entry.getValue().equals(bucketId))
         {
            return (entry.getKey());
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** Upsert one connection row.
    *******************************************************************************/
   private static Integer upsertConnection(StorageConnectionConfig c, ObjectStorageQBitConfig config) throws QException
   {
      boolean accessResolved = c.getCredentialsMode() != CredentialsMode.STATIC_REFS
         || (SecretRefs.resolve(c.getAccessKeyRef()) != null && SecretRefs.resolve(c.getSecretKeyRef()) != null);
      boolean hasIncoming    = config.getBuckets().stream().anyMatch(b -> c.getName().equals(b.getConnection())
         && b.getRole() != null && (b.getRole().name().equals("INCOMING") || b.getRole().name().equals("PUBLIC")));
      String  warning        = !c.hasPresignKey() && (StringUtils.hasContent(c.getPresignEndpoint()) || hasIncoming)
         ? "Presigned URLs are signed with the main key, whose access key ID is visible in every URL. Configure a separate least-privilege presign key." : null;

      QRecord identity = new QRecord()
         .withValue("name", c.getName()).withValue("source", "CONFIG").withValue("providerType", c.getProviderType().name())
         .withValue("endpoint", c.getEndpoint()).withValue("presignEndpoint", c.getPresignEndpoint()).withValue("region", c.getRegion())
         .withValue("addressingStyle", c.effectiveAddressingStyle().name()).withValue("signatureVersion", c.getSignatureVersion())
         .withValue("checksumMode", c.effectiveChecksumMode().name()).withValue("payloadSigning", c.getPayloadSigning().name())
         .withValue("tlsVerify", c.getTlsVerify()).withValue("credentialsMode", c.getCredentialsMode().name())
         .withValue("accessKeyRef", c.getAccessKeyRef()).withValue("secretKeyRef", c.getSecretKeyRef())
         .withValue("presignAccessKeyRef", c.getPresignAccessKeyRef()).withValue("presignSecretKeyRef", c.getPresignSecretKeyRef())
         .withValue("credentialsResolved", accessResolved).withValue("presignKeyConfigured", c.hasPresignKey())
         .withValue("presignWarning", warning).withValue("adminApiType", c.getAdminApiType().name())
         .withValue("settingsHash", new ConnectionRuntime(c).getSettingsHash());

      QRecord existing = Db.first(ObjectStorageTables.CONNECTION, "name", c.getName());
      if(existing == null)
      {
         identity.withValue("maxOpsPerSecond", c.getMaxOpsPerSecond()).withValue("maxConcurrency", c.getMaxConcurrency())
            .withValue("connectTimeoutMs", c.getConnectTimeoutMs()).withValue("readTimeoutMs", c.getReadTimeoutMs());
         return (Db.insert(ObjectStorageTables.CONNECTION, identity).getValueInteger("id"));
      }
      identity.withValue("id", existing.getValue("id"));
      Db.update(ObjectStorageTables.CONNECTION, List.of(identity));
      return (existing.getValueInteger("id"));
   }



   /*******************************************************************************
    ** Upsert one bucket row.
    *******************************************************************************/
   private static Integer upsertBucket(StorageBucketConfig b, ObjectStorageQBitConfig config) throws QException
   {
      QRecord identity = new QRecord()
         .withValue("alias", b.getAlias()).withValue("connectionId", CONNECTION_IDS.get(b.getConnection())).withValue("bucketName", b.getBucketName())
         .withValue("source", "CONFIG").withValue("role", b.getRole().name()).withValue("publicBaseUrl", b.getPublicBaseUrl())
         .withValue("indexed", b.getIndexed()).withValue("managedBy", b.getManagedBy().name())
         .withValue("trashBucketAlias", Boolean.TRUE.equals(b.getTrashEnabled()) ? (b.getTrashBucket() == null ? b.getAlias() : b.getTrashBucket()) : null)
         .withValue("trashPrefix", Boolean.TRUE.equals(b.getTrashEnabled()) ? b.getTrashPrefix() : null)
         .withValue("referenceTracked", b.getReferenceTracked());

      QRecord existing = Db.first(ObjectStorageTables.BUCKET, "alias", b.getAlias());
      if(existing == null)
      {
         identity.withValue("maxPresignGetSeconds", b.getMaxPresignGetSeconds()).withValue("maxPresignPutSeconds", b.getMaxPresignPutSeconds());
         return (Db.insert(ObjectStorageTables.BUCKET, identity).getValueInteger("id"));
      }
      identity.withValue("id", existing.getValue("id"));
      Db.update(ObjectStorageTables.BUCKET, List.of(identity));
      LOG.debug("Updated a storage bucket row", logPair("alias", b.getAlias()), logPair("at", Instant.now()));
      return (existing.getValueInteger("id"));
   }



   /*******************************************************************************
    ** Operational overrides edited in the UI (rate limits, timeouts), applied to a
    ** copy of the code config when a connection runtime is first built.
    *******************************************************************************/
   public static StorageConnectionConfig withOverrides(StorageConnectionConfig base)
   {
      if(!Db.available(ObjectStorageTables.CONNECTION))
      {
         return (base);
      }
      try
      {
         QRecord row = Db.asSystem(() -> Db.first(ObjectStorageTables.CONNECTION, "name", base.getName()));
         if(row == null)
         {
            return (base);
         }
         if(row.getValueInteger("maxOpsPerSecond") != null)
         {
            base.setMaxOpsPerSecond(row.getValueInteger("maxOpsPerSecond"));
         }
         if(row.getValueInteger("maxConcurrency") != null)
         {
            base.setMaxConcurrency(row.getValueInteger("maxConcurrency"));
         }
         if(row.getValueInteger("connectTimeoutMs") != null)
         {
            base.setConnectTimeoutMs(row.getValueInteger("connectTimeoutMs"));
         }
         if(row.getValueInteger("readTimeoutMs") != null)
         {
            base.setReadTimeoutMs(row.getValueInteger("readTimeoutMs"));
         }
      }
      catch(Exception e)
      {
         LOG.debug("Could not read connection overrides", e);
      }
      return (base);
   }


   /*******************************************************************************
    ** The bucket's presign limits edited in the UI (Settings on the bucket row),
    ** applied to the code config so every presign cap reads the row. Only the
    ** presign TTLs are row-owned: the public base URL, trash bucket and trash
    ** prefix are identity, rewritten from code on every sync and read-only in
    ** the UI, so they are never read back from the row. A missing row, an unset
    ** value or a value below one second keeps the config value.
    *******************************************************************************/
   public static StorageBucketConfig withBucketOverrides(StorageBucketConfig base)
   {
      if(base == null || !Db.available(ObjectStorageTables.BUCKET))
      {
         return (base);
      }
      try
      {
         QRecord row = Db.asSystem(() -> Db.first(ObjectStorageTables.BUCKET, "alias", base.getAlias()));
         if(row == null)
         {
            return (base);
         }
         Integer get = row.getValueInteger("maxPresignGetSeconds");
         Integer put = row.getValueInteger("maxPresignPutSeconds");
         if(get != null && get > 0)
         {
            base.setMaxPresignGetSeconds(get);
         }
         if(put != null && put > 0)
         {
            base.setMaxPresignPutSeconds(put);
         }
      }
      catch(Exception e)
      {
         LOG.debug("Could not read bucket overrides", e);
      }
      return (base);
   }
}
