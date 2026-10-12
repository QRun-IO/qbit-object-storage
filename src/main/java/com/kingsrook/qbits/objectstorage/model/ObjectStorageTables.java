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

package com.kingsrook.qbits.objectstorage.model;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.permissions.ObjectStoragePermissionChecker;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.ChildRecordListRenderer;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.AdornmentType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinOn;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.ExposedJoin;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QFieldSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.SectionFactory;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Tier;


/*******************************************************************************
 ** The qbit's tables: standard, visible QQQ tables named object_storage_*
 ** (lowercase snake_case, the full qbit name as prefix). Field names are
 ** camelCase. No table stores a secret value.
 *******************************************************************************/
public final class ObjectStorageTables
{
   public static final String CONNECTION            = "object_storage_connection";
   public static final String CONNECTION_CAPABILITY = "object_storage_connection_capability";
   public static final String BUCKET                = "object_storage_bucket";
   public static final String BUCKET_CAPABILITY     = "object_storage_bucket_capability";
   public static final String FOLDER                = "object_storage_folder";
   public static final String OBJECT                = "object_storage_object";
   public static final String OBJECT_VERSION        = "object_storage_object_version";
   public static final String OBJECT_REFERENCE      = "object_storage_object_reference";
   public static final String HEADER_RULE           = "object_storage_header_rule";
   public static final String PROTECTION_RULE       = "object_storage_protection_rule";
   public static final String OPERATION             = "object_storage_operation";
   public static final String USAGE_DAILY           = "object_storage_usage_daily";

   /** Tables written only by the qbit (history and observed state): read-only in the UI and API. */
   public static final List<String> READ_ONLY = List.of(CONNECTION_CAPABILITY, BUCKET_CAPABILITY, FOLDER, OBJECT, OBJECT_VERSION, OBJECT_REFERENCE, OPERATION, USAGE_DAILY);

   public static final List<String> ALL = List.of(CONNECTION, CONNECTION_CAPABILITY, BUCKET, BUCKET_CAPABILITY, FOLDER, OBJECT, OBJECT_VERSION,
      OBJECT_REFERENCE, HEADER_RULE, PROTECTION_RULE, OPERATION, USAGE_DAILY);

   public static final String KEY_TYPE_BUCKET     = "object_storage_bucket";
   public static final String KEY_TYPE_CONNECTION = "object_storage_connection";
   public static final String ALL_ACCESS_BUCKET   = "object_storage_bucket_all_access";
   public static final String ALL_ACCESS_CONN     = "object_storage_connection_all_access";

   public static final String PVS_PROVIDER_TYPE = "object_storage_provider_type";
   public static final String PVS_BUCKET_ROLE   = "object_storage_bucket_role";
   public static final String PVS_MANAGED_BY    = "object_storage_managed_by";
   public static final String PVS_CAPABILITY    = "object_storage_capability";
   public static final String PVS_CAP_STATUS    = "object_storage_capability_status";
   public static final String PVS_CAP_SOURCE    = "object_storage_capability_source";
   public static final String PVS_PROTECTION    = "object_storage_protection_mode";
   public static final String PVS_OPERATION     = "object_storage_operation_action";
   public static final String PVS_RESULT        = "object_storage_operation_result";
   public static final String PVS_SOURCE        = "object_storage_config_source";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ObjectStorageTables()
   {
   }



   /*******************************************************************************
    ** Permission rules shared by every qbit table, app, widget and process.
    *******************************************************************************/
   public static QPermissionRules permissionRules()
   {
      return (new QPermissionRules()
         .withLevel(PermissionLevel.HAS_ACCESS_PERMISSION)
         .withPermissionBaseName(ObjectStorageQBitConfig.PERMISSION_BASE_NAME)
         .withCustomPermissionChecker(new QCodeReference(ObjectStoragePermissionChecker.class)));
   }



   /*******************************************************************************
    ** The enum possible-value sources and the table references.
    *******************************************************************************/
   public static List<QPossibleValueSource> possibleValueSources()
   {
      List<QPossibleValueSource> list = new ArrayList<>();
      list.add(enumSource(PVS_PROVIDER_TYPE, "AWS_S3", "MINIO", "CLOUDFLARE_R2", "BACKBLAZE_B2", "WASABI", "DO_SPACES", "CEPH_RGW", "GARAGE", "OTHER"));
      list.add(enumSource(PVS_BUCKET_ROLE, "PUBLIC", "PRIVATE", "INCOMING", "BACKUP"));
      list.add(enumSource(PVS_MANAGED_BY, "IAC", "UI"));
      list.add(enumSource(PVS_CAPABILITY, "LIST_V2", "PUT_GET_DELETE", "COPY_REPLACE_METADATA", "BATCH_DELETE", "MULTIPART", "PRESIGNED_GET", "PRESIGNED_PUT",
         "PRESIGNED_POST", "CONDITIONAL_WRITE", "VERSIONING", "OBJECT_LOCK", "LIFECYCLE", "BUCKET_POLICY", "CORS", "OBJECT_TAGGING", "ACL", "USAGE_ADMIN_API", "QUOTA_ADMIN_API"));
      list.add(enumSource(PVS_CAP_STATUS, "SUPPORTED", "UNSUPPORTED", "NOT_PERMITTED", "PARTIAL", "UNKNOWN"));
      list.add(enumSource(PVS_CAP_SOURCE, "DEFAULT", "PROBE", "OVERRIDE"));
      list.add(enumSource(PVS_PROTECTION, "READ_ONLY", "NO_DELETE", "WARN"));
      list.add(enumSource(PVS_OPERATION, "PUT", "COPY", "MOVE", "HEADERS", "DELETE", "RESTORE", "PRESIGN_GET", "PRESIGN_PUT", "PRESIGN_POST", "UPLOAD", "SCAN", "PROBE", "PURGE_TRASH"));
      list.add(enumSource(PVS_RESULT, "OK", "FAILED", "SKIPPED"));
      list.add(enumSource(PVS_SOURCE, "CONFIG", "UI"));
      for(String table : ALL)
      {
         list.add(QPossibleValueSource.newForTable(table));
      }
      return (list);
   }



   /*******************************************************************************
    ** An enum possible-value source from plain string ids (the id is the label).
    *******************************************************************************/
   private static QPossibleValueSource enumSource(String name, String... values)
   {
      List<QPossibleValue<?>> list = new ArrayList<>();
      for(String value : values)
      {
         list.add(new QPossibleValue<>(value, labelOf(value)));
      }
      return (new QPossibleValueSource().withName(name).withType(QPossibleValueSourceType.ENUM).withEnumValues(list));
   }



   /*******************************************************************************
    ** A readable label for an UPPER_SNAKE id.
    *******************************************************************************/
   static String labelOf(String id)
   {
      StringBuilder sb = new StringBuilder();
      for(String part : id.toLowerCase().split("_"))
      {
         if(!part.isEmpty())
         {
            sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
         }
      }
      return (sb.toString());
   }



   /*******************************************************************************
    ** The security key types for per-bucket and per-connection scoping.
    *******************************************************************************/
   public static List<QSecurityKeyType> securityKeyTypes()
   {
      return (List.of(
         new QSecurityKeyType().withName(KEY_TYPE_BUCKET).withAllAccessKeyName(ALL_ACCESS_BUCKET).withPossibleValueSourceName(BUCKET),
         new QSecurityKeyType().withName(KEY_TYPE_CONNECTION).withAllAccessKeyName(ALL_ACCESS_CONN).withPossibleValueSourceName(CONNECTION)));
   }



   /** Parent to child relations: {parent, child, foreign key on the child, label of the child list}. */
   private static final List<String[]> RELATIONS = List.of(
      new String[] { CONNECTION, BUCKET, "connectionId", "Buckets" },
      new String[] { CONNECTION, CONNECTION_CAPABILITY, "connectionId", "Capabilities" },
      new String[] { BUCKET, BUCKET_CAPABILITY, "bucketId", "Capabilities" },
      new String[] { BUCKET, FOLDER, "bucketId", "Folders" },
      new String[] { BUCKET, OBJECT, "bucketId", "Objects" },
      new String[] { BUCKET, OBJECT_REFERENCE, "bucketId", "References" },
      new String[] { BUCKET, USAGE_DAILY, "bucketId", "Usage" },
      new String[] { BUCKET, OPERATION, "bucketId", "Operations" },
      new String[] { BUCKET, HEADER_RULE, "bucketId", "Header rules" },
      new String[] { BUCKET, PROTECTION_RULE, "bucketId", "Protection rules" },
      new String[] { FOLDER, OBJECT, "folderId", "Objects" },
      new String[] { OBJECT, OBJECT_VERSION, "objectId", "Versions" },
      new String[] { OBJECT, OBJECT_REFERENCE, "objectId", "Referenced by" });

   /** The columns a child list shows for each child table (the rest is one click away on the record). */
   private static final Map<String, List<String>> CHILD_LIST_COLUMNS = Map.ofEntries(
      Map.entry(OBJECT, List.of("objectKey", "size", "contentType", "lastModified", "referenceCount")),
      Map.entry(FOLDER, List.of("path", "objectCount", "totalBytes")),
      Map.entry(OBJECT_REFERENCE, List.of("label", "keyOrPrefix", "missing", "sourceTable")),
      Map.entry(OPERATION, List.of("occurredAt", "action", "objectKey", "result", "actor")),
      Map.entry(USAGE_DAILY, List.of("snapshotDate", "objectCount", "totalBytes")),
      Map.entry(CONNECTION_CAPABILITY, List.of("capability", "status", "detail")),
      Map.entry(BUCKET_CAPABILITY, List.of("capability", "status", "detail")),
      Map.entry(BUCKET, List.of("alias", "role", "indexed", "objectCount")),
      Map.entry(HEADER_RULE, List.of("name", "keyGlob", "cacheControl", "priority", "enabled")),
      Map.entry(PROTECTION_RULE, List.of("prefixGlob", "mode", "enabled", "reason")),
      Map.entry(OBJECT_VERSION, List.of("versionId", "isDeleteMarker", "isLatest", "lastModified")));

   /** Tables that are nav entries (the rest appear as relations and child lists on their parents). */
   public static final List<String> NAV_TABLES = List.of(BUCKET, OBJECT, FOLDER, CONNECTION, OPERATION, HEADER_RULE, PROTECTION_RULE);



   /*******************************************************************************
    ** All tables, with their child lists exposed (relations on the parent record).
    *******************************************************************************/
   public static List<QTableMetaData> tables(ObjectStorageQBitConfig config)
   {
      List<QTableMetaData> tables = new ArrayList<>(List.of(connection(config), connectionCapability(config), bucket(config), bucketCapability(config),
         folder(config), object(config), objectVersion(config), objectReference(config), headerRule(config), protectionRule(config), operation(config), usageDaily(config)));
      for(QTableMetaData table : tables)
      {
         layout(table);
         QInstanceEnricher.setInferredFieldBackendNames(table);
      }
      for(String[] relation : RELATIONS)
      {
         for(QTableMetaData table : tables)
         {
            if(table.getName().equals(relation[0]))
            {
               table.withSection(new QFieldSection(joinName(relation), new QIcon("list"), Tier.T2).withLabel(relation[3]).withWidgetName(joinName(relation)));
               table.withExposedJoin(new ExposedJoin().withLabel(relation[3]).withJoinTable(relation[1]).withJoinPath(List.of(joinName(relation))));
            }
         }
      }
      return (tables);
   }



   /** Record screen sections per table: "T1" identity, "T2:Label" detail groups, "T3" bookkeeping. The first name of each row is the section. */
   private static final Map<String, String[][]> LAYOUTS = Map.ofEntries(
      Map.entry(CONNECTION, new String[][] {
         { "T1", "name", "providerType", "source", "lastTestedAt", "lastTestStatus" },
         { "T2:Endpoint and signing", "endpoint", "presignEndpoint", "region", "addressingStyle", "checksumMode", "payloadSigning", "signatureVersion", "tlsVerify", "credentialsMode", "adminApiType" },
         { "T2:Credentials (references only, never values)", "accessKeyRef", "secretKeyRef", "presignAccessKeyRef", "presignSecretKeyRef", "credentialsResolved", "presignKeyConfigured", "presignWarning" },
         { "T2:Limits (editable with object_storage.admin)", "maxOpsPerSecond", "maxConcurrency", "connectTimeoutMs", "readTimeoutMs" },
         { "T2:Last test", "lastTestSummary", "clockSkewSeconds" },
         { "T3", "settingsHash", "id", "createDate", "modifyDate" } }),
      Map.entry(BUCKET, new String[][] {
         { "T1", "alias", "role", "connectionId", "bucketName", "indexed", "managedBy", "referenceTracked", "source" },
         { "T2:Observed settings", "versioningState", "lifecycleSummary", "policySummary", "corsSummary", "configDriftNote" },
         { "T2:Index and health", "objectCount", "totalBytes", "lastScanDate", "lastScanStatus", "headersKnownCount", "headersMatchingPolicyCount", "orphanedCount", "orphanedBytes", "missingCount", "lastReferenceScanDate" },
         { "T2:Settings", "publicBaseUrl", "trashBucketAlias", "trashPrefix", "maxPresignGetSeconds", "maxPresignPutSeconds", "orphanCountAlertThreshold", "quotaBytes" },
         { "T3", "id", "scanGeneration", "scanCursor", "createDate", "modifyDate" } }),
      Map.entry(OBJECT, new String[][] {
         { "T1", "previewUrl", "objectKey", "bucketId", "folderId", "size", "contentType", "lastModified", "referenceCount" },
         { "T2:Headers and metadata", "cacheControl", "contentDisposition", "userMetadata", "tags", "matchesHeaderPolicy", "headersKnown" },
         { "T2:Storage", "name", "extension", "etag", "storageClass", "versionId", "protection", "deleted" },
         { "T3", "id", "scanGeneration", "indexedAt", "createDate", "modifyDate" } }),
      Map.entry(FOLDER, new String[][] {
         { "T1", "path", "name", "bucketId", "parentFolderId", "objectCount", "totalBytes", "newestChange" },
         { "T3", "id", "createDate", "modifyDate" } }),
      Map.entry(OBJECT_VERSION, new String[][] {
         { "T1", "objectKey", "bucketId", "objectId", "versionId", "isDeleteMarker", "isLatest", "size", "lastModified" },
         { "T3", "id", "createDate", "modifyDate" } }),
      Map.entry(OBJECT_REFERENCE, new String[][] {
         { "T1", "label", "bucketId", "objectId", "keyOrPrefix", "matchMode", "missing" },
         { "T2:Referencing record", "sourceTable", "sourceRecordId", "sourceField", "scannedAt" },
         { "T3", "id", "createDate", "modifyDate" } }),
      Map.entry(HEADER_RULE, new String[][] {
         { "T1", "name", "bucketId", "enabled", "priority" },
         { "T2:Matches", "keyGlob", "keyRegex", "contentTypePattern" },
         { "T2:Headers to set", "cacheControl", "contentDisposition" },
         { "T3", "id", "createDate", "modifyDate" } }),
      Map.entry(PROTECTION_RULE, new String[][] {
         { "T1", "bucketId", "prefixGlob", "mode", "enabled" },
         { "T2:Why", "reason", "allowedSystemCallers" },
         { "T3", "id", "createDate", "modifyDate" } }),
      Map.entry(OPERATION, new String[][] {
         { "T1", "occurredAt", "action", "bucketAlias", "objectKey", "destinationKey", "result", "actor" },
         { "T2:Detail", "bytes", "errorMessage", "providerRequestId", "processRunId", "detail", "bucketId" },
         { "T3", "id", "createDate", "modifyDate" } }),
      Map.entry(USAGE_DAILY, new String[][] {
         { "T1", "bucketId", "snapshotDate", "objectCount", "totalBytes" },
         { "T2:By top-level folder", "byPrefix" },
         { "T3", "id", "createDate", "modifyDate" } }),
      Map.entry(CONNECTION_CAPABILITY, new String[][] {
         { "T1", "connectionId", "capability", "status", "source", "probedAt" },
         { "T2:Detail", "detail" },
         { "T3", "id", "createDate", "modifyDate" } }),
      Map.entry(BUCKET_CAPABILITY, new String[][] {
         { "T1", "bucketId", "capability", "status", "source", "probedAt" },
         { "T2:Detail", "detail" },
         { "T3", "id", "createDate", "modifyDate" } }));



   /*******************************************************************************
    ** Give a table its record-screen sections (identity first, then detail groups,
    ** then bookkeeping) and take the buttons away from tables that change only
    ** through actions or from code config.
    *******************************************************************************/
   private static void layout(QTableMetaData table)
   {
      String[][] rows = LAYOUTS.get(table.getName());
      if(rows != null)
      {
         for(String[] row : rows)
         {
            String   kind   = row[0];
            String[] fields = java.util.Arrays.copyOfRange(row, 1, row.length);
            if(kind.equals("T1"))
            {
               table.withSection(SectionFactory.defaultT1(fields));
            }
            else if(kind.equals("T3"))
            {
               table.withSection(SectionFactory.defaultT3(fields));
            }
            else
            {
               String label = kind.substring(kind.indexOf(':') + 1);
               table.withSection(new QFieldSection(label.toLowerCase().replaceAll("[^a-z0-9]+", "_"), label, new QIcon("list"), Tier.T2, List.of(fields)));
            }
         }
      }
      if(READ_ONLY.contains(table.getName()))
      {
         table.withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE, Capability.TABLE_DELETE);
      }
      else if(CONNECTION.equals(table.getName()) || BUCKET.equals(table.getName()))
      {
         table.withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_DELETE);
      }
   }




   /*******************************************************************************
    ** The join metadata for every parent/child relation.
    *******************************************************************************/
   public static List<QJoinMetaData> joins()
   {
      List<QJoinMetaData> joins = new ArrayList<>();
      for(String[] relation : RELATIONS)
      {
         joins.add(new QJoinMetaData().withName(joinName(relation)).withLeftTable(relation[0]).withRightTable(relation[1]).withType(JoinType.ONE_TO_MANY)
            .withJoinOn(new JoinOn("id", relation[2])));
      }
      return (joins);
   }



   /*******************************************************************************
    ** The child record lists shown on a parent's record screen: a bucket shows its
    ** folders, objects, references, usage, operations and rules; a folder and an
    ** object show what is beneath or about them.
    *******************************************************************************/
   public static List<QWidgetMetaData> childListWidgets()
   {
      List<QWidgetMetaData> widgets = new ArrayList<>();
      for(String[] relation : RELATIONS)
      {
         QJoinMetaData join = new QJoinMetaData().withName(joinName(relation)).withLeftTable(relation[0]).withRightTable(relation[1]).withType(JoinType.ONE_TO_MANY)
            .withJoinOn(new JoinOn("id", relation[2]));
         ChildRecordListRenderer.Builder builder = ChildRecordListRenderer.widgetMetaDataBuilder(join).withName(joinName(relation)).withLabel(relation[3]).withMaxRows(10);
         if(CHILD_LIST_COLUMNS.containsKey(relation[1]))
         {
            builder.withOnlyIncludeFieldNames(CHILD_LIST_COLUMNS.get(relation[1]));
         }
         widgets.add(builder.getWidgetMetaData());
      }
      return (widgets);
   }



   /*******************************************************************************
    ** A unique join name per relation.
    *******************************************************************************/
   private static String joinName(String[] relation)
   {
      return (relation[0] + "_" + relation[2] + "_" + relation[1]);
   }



   /*******************************************************************************
    ** A table with the common id/createDate/modifyDate fields.
    *******************************************************************************/
   private static QTableMetaData base(ObjectStorageQBitConfig config, String name, String label, String icon)
   {
      return (new QTableMetaData()
         .withName(name)
         .withLabel(label)
         .withIcon(new QIcon(icon))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withPermissionRules(permissionRules())
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withIsEditable(false)));
   }



   /*******************************************************************************
    ** A read-only field.
    *******************************************************************************/
   private static QFieldMetaData ro(String name, QFieldType type)
   {
      return (new QFieldMetaData(name, type).withIsEditable(false));
   }



   /*******************************************************************************
    ** A read-only string field with a max length.
    *******************************************************************************/
   private static QFieldMetaData ros(String name, int max)
   {
      return (ro(name, QFieldType.STRING).withMaxLength(max));
   }



   /*******************************************************************************
    ** An editable string field.
    *******************************************************************************/
   private static QFieldMetaData str(String name, int max)
   {
      return (new QFieldMetaData(name, QFieldType.STRING).withMaxLength(max));
   }



   /*******************************************************************************
    ** Apply a record security lock on a field (when scoping is on).
    *******************************************************************************/
   private static QTableMetaData lock(ObjectStorageQBitConfig config, QTableMetaData table, String keyType, String fieldName, boolean nullAllowed)
   {
      if(Boolean.TRUE.equals(config.getBucketScoping()))
      {
         table.withRecordSecurityLock(new RecordSecurityLock().withSecurityKeyType(keyType).withFieldName(fieldName)
            .withNullValueBehavior(nullAllowed ? RecordSecurityLock.NullValueBehavior.ALLOW : RecordSecurityLock.NullValueBehavior.DENY));
      }
      return (table);
   }



   /*******************************************************************************
    ** object_storage_connection: one S3 endpoint and its settings. Identity
    ** fields come from code config and are read-only; only operational fields are
    ** editable (with object_storage.admin).
    *******************************************************************************/
   static QTableMetaData connection(ObjectStorageQBitConfig config)
   {
      return (lock(config, base(config, CONNECTION, "Storage Connection", "cloud")
         .withRecordLabelFormatAndFields("%s", "name")
         .withField(ros("name", 100))
         .withField(ro("source", QFieldType.STRING).withPossibleValueSourceName(PVS_SOURCE))
         .withField(ro("providerType", QFieldType.STRING).withPossibleValueSourceName(PVS_PROVIDER_TYPE))
         .withField(ros("endpoint", 500))
         .withField(ros("presignEndpoint", 500))
         .withField(ros("region", 100))
         .withField(ros("addressingStyle", 20))
         .withField(ros("signatureVersion", 10))
         .withField(ros("checksumMode", 20))
         .withField(ros("payloadSigning", 30))
         .withField(ro("tlsVerify", QFieldType.BOOLEAN))
         .withField(ros("credentialsMode", 20))
         .withField(ros("accessKeyRef", 200))
         .withField(ros("secretKeyRef", 200))
         .withField(ros("presignAccessKeyRef", 200))
         .withField(ros("presignSecretKeyRef", 200))
         .withField(ro("credentialsResolved", QFieldType.BOOLEAN))
         .withField(ro("presignKeyConfigured", QFieldType.BOOLEAN))
         .withField(ro("presignWarning", QFieldType.STRING).withMaxLength(300))
         .withField(ros("adminApiType", 20))
         .withField(new QFieldMetaData("maxOpsPerSecond", QFieldType.INTEGER).withDefaultValue(20))
         .withField(new QFieldMetaData("maxConcurrency", QFieldType.INTEGER).withDefaultValue(4))
         .withField(new QFieldMetaData("connectTimeoutMs", QFieldType.INTEGER).withDefaultValue(5000))
         .withField(new QFieldMetaData("readTimeoutMs", QFieldType.INTEGER).withDefaultValue(60000))
         .withField(ro("lastTestedAt", QFieldType.DATE_TIME))
         .withField(ros("lastTestStatus", 30))
         .withField(ro("lastTestSummary", QFieldType.TEXT))
         .withField(ro("clockSkewSeconds", QFieldType.INTEGER))
         .withField(ros("settingsHash", 40)), KEY_TYPE_CONNECTION, "id", false));
   }



   /*******************************************************************************
    ** object_storage_connection_capability.
    *******************************************************************************/
   static QTableMetaData connectionCapability(ObjectStorageQBitConfig config)
   {
      return (lock(config, base(config, CONNECTION_CAPABILITY, "Storage Connection Capability", "fact_check")
         .withRecordLabelFormatAndFields("%s", "capability")
         .withField(ro("connectionId", QFieldType.INTEGER).withPossibleValueSourceName(CONNECTION))
         .withField(ro("capability", QFieldType.STRING).withPossibleValueSourceName(PVS_CAPABILITY))
         .withField(ro("status", QFieldType.STRING).withPossibleValueSourceName(PVS_CAP_STATUS))
         .withField(ro("detail", QFieldType.TEXT))
         .withField(ro("source", QFieldType.STRING).withPossibleValueSourceName(PVS_CAP_SOURCE))
         .withField(ro("probedAt", QFieldType.DATE_TIME)), KEY_TYPE_CONNECTION, "connectionId", false));
   }



   /*******************************************************************************
    ** object_storage_bucket: an alias, its config, and the observed state.
    *******************************************************************************/
   static QTableMetaData bucket(ObjectStorageQBitConfig config)
   {
      return (lock(config, base(config, BUCKET, "Storage Bucket", "inventory_2")
         .withRecordLabelFormatAndFields("%s", "alias")
         .withField(ros("alias", 100))
         .withField(ro("connectionId", QFieldType.INTEGER).withPossibleValueSourceName(CONNECTION))
         .withField(ros("bucketName", 200))
         .withField(ro("source", QFieldType.STRING).withPossibleValueSourceName(PVS_SOURCE))
         .withField(ro("role", QFieldType.STRING).withPossibleValueSourceName(PVS_BUCKET_ROLE))
         .withField(ro("publicBaseUrl", QFieldType.STRING).withMaxLength(500))
         .withField(ro("indexed", QFieldType.BOOLEAN))
         .withField(ro("managedBy", QFieldType.STRING).withPossibleValueSourceName(PVS_MANAGED_BY))
         .withField(ros("trashBucketAlias", 100))
         .withField(ros("trashPrefix", 200))
         .withField(new QFieldMetaData("maxPresignGetSeconds", QFieldType.INTEGER).withDefaultValue(3600))
         .withField(new QFieldMetaData("maxPresignPutSeconds", QFieldType.INTEGER).withDefaultValue(900))
         .withField(ro("referenceTracked", QFieldType.BOOLEAN))
         .withField(new QFieldMetaData("orphanCountAlertThreshold", QFieldType.INTEGER))
         .withField(new QFieldMetaData("quotaBytes", QFieldType.LONG))
         .withField(ro("versioningState", QFieldType.STRING).withMaxLength(30))
         .withField(ro("lifecycleSummary", QFieldType.TEXT))
         .withField(ro("policySummary", QFieldType.TEXT))
         .withField(ro("corsSummary", QFieldType.TEXT))
         .withField(ro("configDriftNote", QFieldType.TEXT))
         .withField(ro("objectCount", QFieldType.LONG))
         .withField(ro("totalBytes", QFieldType.LONG))
         .withField(ro("lastScanDate", QFieldType.DATE_TIME))
         .withField(ros("lastScanStatus", 30))
         .withField(ro("scanGeneration", QFieldType.LONG))
         .withField(ro("scanCursor", QFieldType.STRING).withMaxLength(1100))
         .withField(ro("headersKnownCount", QFieldType.LONG))
         .withField(ro("headersMatchingPolicyCount", QFieldType.LONG))
         .withField(ro("orphanedCount", QFieldType.LONG))
         .withField(ro("orphanedBytes", QFieldType.LONG))
         .withField(ro("missingCount", QFieldType.LONG))
         .withField(ro("lastReferenceScanDate", QFieldType.DATE_TIME)), KEY_TYPE_BUCKET, "id", false));
   }



   /*******************************************************************************
    ** object_storage_bucket_capability.
    *******************************************************************************/
   static QTableMetaData bucketCapability(ObjectStorageQBitConfig config)
   {
      return (lock(config, base(config, BUCKET_CAPABILITY, "Storage Bucket Capability", "fact_check")
         .withRecordLabelFormatAndFields("%s", "capability")
         .withField(ro("bucketId", QFieldType.INTEGER).withPossibleValueSourceName(BUCKET))
         .withField(ro("capability", QFieldType.STRING).withPossibleValueSourceName(PVS_CAPABILITY))
         .withField(ro("status", QFieldType.STRING).withPossibleValueSourceName(PVS_CAP_STATUS))
         .withField(ro("detail", QFieldType.TEXT))
         .withField(ro("source", QFieldType.STRING).withPossibleValueSourceName(PVS_CAP_SOURCE))
         .withField(ro("probedAt", QFieldType.DATE_TIME)), KEY_TYPE_BUCKET, "bucketId", false));
   }



   /*******************************************************************************
    ** object_storage_folder: a prefix in an indexed bucket, rolled up.
    *******************************************************************************/
   static QTableMetaData folder(ObjectStorageQBitConfig config)
   {
      return (lock(config, base(config, FOLDER, "Storage Folder", "folder")
         .withRecordLabelFormatAndFields("%s", "path")
         .withField(ro("bucketId", QFieldType.INTEGER).withPossibleValueSourceName(BUCKET))
         .withField(ro("parentFolderId", QFieldType.INTEGER).withPossibleValueSourceName(FOLDER))
         .withField(ros("path", 1100))
         .withField(ros("name", 300))
         .withField(ro("objectCount", QFieldType.LONG))
         .withField(ro("totalBytes", QFieldType.LONG))
         .withField(ro("newestChange", QFieldType.DATE_TIME)), KEY_TYPE_BUCKET, "bucketId", false));
   }



   /*******************************************************************************
    ** object_storage_object: one object in an indexed bucket. Mirrors S3 and is
    ** read-only; changes go through actions.
    *******************************************************************************/
   static QTableMetaData object(ObjectStorageQBitConfig config)
   {
      return (lock(config, base(config, OBJECT, "Storage Object", "description")
         .withRecordLabelFormatAndFields("%s", "objectKey")
         .withField(ro("bucketId", QFieldType.INTEGER).withPossibleValueSourceName(BUCKET))
         .withField(ro("folderId", QFieldType.INTEGER).withPossibleValueSourceName(FOLDER))
         .withField(ros("objectKey", 1100))
         .withField(ros("name", 300))
         .withField(ros("extension", 30))
         .withField(ro("size", QFieldType.LONG))
         .withField(ros("contentType", 200))
         .withField(ros("etag", 100))
         .withField(ro("lastModified", QFieldType.DATE_TIME))
         .withField(ros("storageClass", 40))
         .withField(ros("cacheControl", 300))
         .withField(ros("contentDisposition", 300))
         .withField(ro("userMetadata", QFieldType.TEXT))
         .withField(ro("tags", QFieldType.TEXT))
         .withField(ros("versionId", 200))
         .withField(ro("referenceCount", QFieldType.INTEGER))
         .withField(ros("protection", 30))
         .withField(ro("headersKnown", QFieldType.BOOLEAN))
         .withField(ro("matchesHeaderPolicy", QFieldType.BOOLEAN))
         .withField(ro("deleted", QFieldType.BOOLEAN))
         .withField(ro("scanGeneration", QFieldType.LONG))
         .withField(ro("indexedAt", QFieldType.DATE_TIME))
         .withField(ro("previewUrl", QFieldType.STRING).withMaxLength(2000).withLabel("Preview")
            .withFieldAdornment(AdornmentType.FilePreviewValues.newFieldAdornment()
               .withValue(AdornmentType.FilePreviewValues.FILE_NAME_FIELD, "name")
               .withValue(AdornmentType.FilePreviewValues.MIME_TYPE_FIELD, "contentType")
               .withValue(AdornmentType.FilePreviewValues.SIZE_BYTES_FIELD, "size")
               .withValue(AdornmentType.FilePreviewValues.THUMBNAIL_MAX_SIZE, 48)))
         .withCustomizer(TableCustomizers.POST_QUERY_RECORD, new QCodeReference(ObjectPreviewCustomizer.class)), KEY_TYPE_BUCKET, "bucketId", false));
   }



   /*******************************************************************************
    ** object_storage_object_version: a non-current version or delete marker.
    *******************************************************************************/
   static QTableMetaData objectVersion(ObjectStorageQBitConfig config)
   {
      return (lock(config, base(config, OBJECT_VERSION, "Storage Object Version", "history")
         .withRecordLabelFormatAndFields("%s", "objectKey")
         .withField(ro("bucketId", QFieldType.INTEGER).withPossibleValueSourceName(BUCKET))
         .withField(ro("objectId", QFieldType.INTEGER).withPossibleValueSourceName(OBJECT))
         .withField(ros("objectKey", 1100))
         .withField(ros("versionId", 200))
         .withField(ro("isDeleteMarker", QFieldType.BOOLEAN))
         .withField(ro("isLatest", QFieldType.BOOLEAN))
         .withField(ro("size", QFieldType.LONG))
         .withField(ro("lastModified", QFieldType.DATE_TIME)), KEY_TYPE_BUCKET, "bucketId", false));
   }



   /*******************************************************************************
    ** object_storage_object_reference: a record that points at a key or prefix.
    ** objectId is null when the referenced object does not exist (missing).
    *******************************************************************************/
   static QTableMetaData objectReference(ObjectStorageQBitConfig config)
   {
      return (lock(config, base(config, OBJECT_REFERENCE, "Storage Object Reference", "link")
         .withRecordLabelFormatAndFields("%s", "label")
         .withField(ro("bucketId", QFieldType.INTEGER).withPossibleValueSourceName(BUCKET))
         .withField(ro("objectId", QFieldType.INTEGER).withPossibleValueSourceName(OBJECT))
         .withField(ros("keyOrPrefix", 1100))
         .withField(ros("matchMode", 10))
         .withField(ros("sourceTable", 100))
         .withField(ros("sourceRecordId", 100))
         .withField(ros("sourceField", 100))
         .withField(ros("label", 300))
         .withField(ro("missing", QFieldType.BOOLEAN))
         .withField(ro("scannedAt", QFieldType.DATE_TIME)), KEY_TYPE_BUCKET, "bucketId", false));
   }



   /*******************************************************************************
    ** object_storage_header_rule: header policy rules (ordinary audited data).
    *******************************************************************************/
   static QTableMetaData headerRule(ObjectStorageQBitConfig config)
   {
      return (lock(config, base(config, HEADER_RULE, "Storage Header Rule", "rule")
         .withRecordLabelFormatAndFields("%s", "name")
         .withField(str("name", 100).withIsRequired(true))
         .withField(new QFieldMetaData("bucketId", QFieldType.INTEGER).withPossibleValueSourceName(BUCKET))
         .withField(str("keyGlob", 500))
         .withField(str("keyRegex", 500))
         .withField(str("contentTypePattern", 200))
         .withField(str("cacheControl", 300))
         .withField(str("contentDisposition", 300))
         .withField(new QFieldMetaData("priority", QFieldType.INTEGER).withDefaultValue(100))
         .withField(new QFieldMetaData("enabled", QFieldType.BOOLEAN).withDefaultValue(true)), KEY_TYPE_BUCKET, "bucketId", true));
   }



   /*******************************************************************************
    ** object_storage_protection_rule: a protected bucket/prefix.
    *******************************************************************************/
   static QTableMetaData protectionRule(ObjectStorageQBitConfig config)
   {
      return (lock(config, base(config, PROTECTION_RULE, "Storage Protection Rule", "shield")
         .withRecordLabelFormatAndFields("%s", "prefixGlob")
         .withField(new QFieldMetaData("bucketId", QFieldType.INTEGER).withPossibleValueSourceName(BUCKET).withIsRequired(true))
         .withField(str("prefixGlob", 500).withIsRequired(true))
         .withField(new QFieldMetaData("mode", QFieldType.STRING).withPossibleValueSourceName(PVS_PROTECTION).withIsRequired(true).withDefaultValue("NO_DELETE"))
         .withField(str("reason", 500))
         .withField(str("allowedSystemCallers", 300))
         .withField(new QFieldMetaData("enabled", QFieldType.BOOLEAN).withDefaultValue(true)), KEY_TYPE_BUCKET, "bucketId", false));
   }



   /*******************************************************************************
    ** object_storage_operation: the storage audit log, written only by the service.
    *******************************************************************************/
   static QTableMetaData operation(ObjectStorageQBitConfig config)
   {
      return (lock(config, base(config, OPERATION, "Storage Operation", "history")
         .withRecordLabelFormatAndFields("%s %s", "action", "objectKey")
         .withField(ro("occurredAt", QFieldType.DATE_TIME))
         .withField(ros("actor", 300))
         .withField(ro("action", QFieldType.STRING).withPossibleValueSourceName(PVS_OPERATION))
         .withField(ro("bucketId", QFieldType.INTEGER).withPossibleValueSourceName(BUCKET))
         .withField(ros("bucketAlias", 100))
         .withField(ros("objectKey", 1100))
         .withField(ros("destinationKey", 1100))
         .withField(ro("bytes", QFieldType.LONG))
         .withField(ro("result", QFieldType.STRING).withPossibleValueSourceName(PVS_RESULT))
         .withField(ro("errorMessage", QFieldType.TEXT))
         .withField(ros("providerRequestId", 100))
         .withField(ro("processRunId", QFieldType.STRING).withMaxLength(100))
         .withField(ro("detail", QFieldType.TEXT)), KEY_TYPE_BUCKET, "bucketId", true));
   }



   /*******************************************************************************
    ** object_storage_usage_daily: one snapshot per bucket per day.
    *******************************************************************************/
   static QTableMetaData usageDaily(ObjectStorageQBitConfig config)
   {
      return (lock(config, base(config, USAGE_DAILY, "Storage Usage", "insights")
         .withRecordLabelFormatAndFields("%s", "snapshotDate")
         .withField(ro("bucketId", QFieldType.INTEGER).withPossibleValueSourceName(BUCKET))
         .withField(ro("snapshotDate", QFieldType.DATE))
         .withField(ro("objectCount", QFieldType.LONG))
         .withField(ro("totalBytes", QFieldType.LONG))
         .withField(ro("byPrefix", QFieldType.TEXT)), KEY_TYPE_BUCKET, "bucketId", false));
   }
}
