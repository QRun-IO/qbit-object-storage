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

package com.kingsrook.qbits.objectstorage.processes;


import java.io.Serializable;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.kingsrook.qbits.objectstorage.api.Capability;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaders;
import com.kingsrook.qbits.objectstorage.api.PresignGet;
import com.kingsrook.qbits.objectstorage.api.PresignPut;
import com.kingsrook.qbits.objectstorage.api.StorageKeys;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.index.ObjectIndex;
import com.kingsrook.qbits.objectstorage.index.ReferenceScan;
import com.kingsrook.qbits.objectstorage.internal.CapabilityStore;
import com.kingsrook.qbits.objectstorage.internal.ConnectionRuntime;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.internal.ProtectionRules;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.permissions.ObjectStoragePermissions;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;


/*******************************************************************************
 ** Bucket record actions: Rescan bucket, Check configuration, Apply header policy
 ** (for a whole bucket), and Create upload link. Bucket creation and deletion
 ** stay in infrastructure-as-code; "Check configuration" only observes, and says
 ** when what it sees differs from what the config declares.
 *******************************************************************************/
public final class BucketProcesses
{
   public static final String RESCAN_NAME     = "objectStorageRescanBucket";
   public static final String CHECK_NAME      = "objectStorageCheckConfiguration";
   public static final String POLICY_NAME     = "objectStorageApplyHeaderPolicyToBucket";
   public static final String UPLOAD_LINK     = "objectStorageCreateUploadLink";

   public static final String FIELD_LINK_KEY    = "linkKey";
   public static final String FIELD_LINK_TYPE   = "linkContentType";
   public static final String FIELD_LINK_LENGTH = "linkContentLength";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private BucketProcesses()
   {
   }



   /*******************************************************************************
    ** The processes.
    *******************************************************************************/
   public static List<QProcessMetaData> processes()
   {
      return (List.of(
         EtlSupport.etl(RESCAN_NAME, "Rescan bucket", "refresh", ObjectStorageTables.BUCKET, RescanTransform.class, RescanLoad.class,
            "This is a preview of the buckets that will be listed and re-indexed.", ObjectStoragePermissions.VIEW),
         EtlSupport.etl(CHECK_NAME, "Check configuration", "fact_check", ObjectStorageTables.BUCKET, CheckTransform.class, CheckLoad.class,
            "This is a preview of the buckets whose settings will be read (read-only calls).", ObjectStoragePermissions.VIEW),
         EtlSupport.etl(POLICY_NAME, "Apply header policy", "rule", ObjectStorageTables.BUCKET, BucketPolicyTransform.class, BucketPolicyLoad.class,
            "This is a preview of the objects in these buckets whose headers will be rewritten to match the header policy.", ObjectStoragePermissions.WRITE),
         EtlSupport.withInput(EtlSupport.etl(UPLOAD_LINK, "Create upload link", "upload", ObjectStorageTables.BUCKET, UploadLinkTransform.class, UploadLinkLoad.class,
            "This is a preview of the presigned upload link that will be created.", ObjectStoragePermissions.WRITE),
            "Create upload link", "Creates a presigned PUT link for one object key. Anyone with the link can upload that one object until it expires; the content type and length are pinned when given.",
            EtlSupport.stringField(FIELD_LINK_KEY, "Object key (for example uploads/photo.jpg)", true), EtlSupport.stringField(FIELD_LINK_TYPE, "Content type (pins the upload to this type)", false),
            new QFieldMetaData(FIELD_LINK_LENGTH, QFieldType.LONG).withLabel("Content length in bytes (pins the upload to this size)"))));
   }



   /*******************************************************************************
    ** The bucket id of a bucket record is its own id.
    *******************************************************************************/
   abstract static class BucketTransform extends BaseTransform
   {
      /*******************************************************************************
       ** A bucket record's lock key is its id.
       *******************************************************************************/
      @Override
      protected Integer bucketIdOf(QRecord record)
      {
         return (record.getValueInteger("id"));
      }
   }



   /*******************************************************************************
    ** The bucket id of a bucket record is its own id.
    *******************************************************************************/
   abstract static class BucketLoad extends BaseLoad
   {
      /*******************************************************************************
       ** A bucket record's lock key is its id.
       *******************************************************************************/
      @Override
      protected Integer bucketIdOf(QRecord record)
      {
         return (record.getValueInteger("id"));
      }
   }



   /*******************************************************************************
    ** Rescan bucket, review.
    *******************************************************************************/
   public static class RescanTransform extends BucketTransform
   {
      private final ProcessSummaryLine will = EtlSupport.line(Status.OK, "bucket will be listed and re-indexed", "buckets will be listed and re-indexed", "bucket was re-indexed", "buckets were re-indexed");
      private final ProcessSummaryLine notIndexed = EtlSupport.line(Status.INFO, "bucket is not indexed (skipped)", "buckets are not indexed (skipped)", "bucket was not indexed (skipped)", "buckets were not indexed (skipped)");
      private final ProcessSummaryLine running = EtlSupport.line(Status.WARNING, "bucket already has a scan running (skipped)", "buckets already have scans running (skipped)",
         "bucket already had a scan running (skipped)", "buckets already had scans running (skipped)");
      private final List<String> expected = new ArrayList<>();



      /*******************************************************************************
       ** Classify one bucket.
       *******************************************************************************/
      @Override
      protected void handle(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         String              alias  = record.getValueString("alias");
         StorageBucketConfig config = ActionSupport.config(alias);
         if(config == null || !Boolean.TRUE.equals(config.getIndexed()))
         {
            notIndexed.incrementCountAndAddPrimaryKey(record.getValue("id"));
            return;
         }
         if(ObjectIndex.isScanning(alias))
         {
            running.incrementCountAndAddPrimaryKey(record.getValue("id"));
            return;
         }
         will.incrementCountAndAddPrimaryKey(record.getValue("id"));
         long objects = record.getValueLong("objectCount") == null ? 0 : record.getValueLong("objectCount");
         expected.add(alias + ": whole bucket; last scan " + (record.getValueInstant("lastScanDate") == null ? "never" : record.getValueInstant("lastScanDate")) + "; about " + (objects / 1000 + 1) + " list calls");
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Review lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         will.addSelfToListIfAnyCount(lines);
         if(will.getCount() != null && will.getCount() > 0)
         {
            will.setBulletsOfText(new ArrayList<>(expected));
         }
         notIndexed.addSelfToListIfAnyCount(lines);
         running.addSelfToListIfAnyCount(lines);
      }



      /*******************************************************************************
       ** The skips, as the Result screen shows them.
       *******************************************************************************/
      @Override
      protected List<ProcessSummaryLine> skipLines()
      {
         return (List.of(notIndexed, running));
      }
   }



   /*******************************************************************************
    ** Rescan bucket, execute: scan, HEAD backlog, references.
    *******************************************************************************/
   public static class RescanLoad extends BucketLoad
   {
      private final ArrayList<ProcessSummaryLineInterface> results = new ArrayList<>();



      /*******************************************************************************
       ** Scan one bucket.
       *******************************************************************************/
      @Override
      protected void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         String alias = record.getValueString("alias");
         ObjectIndex.ScanResult scan = ObjectIndex.scan(alias);
         boolean ok = "OK".equals(scan.status());
         results.add(new ProcessSummaryLine(ok ? Status.OK : Status.WARNING, alias + ": scan " + scan.status() + " - " + scan.seen() + " objects seen, " + scan.added() + " new, "
            + scan.updated() + " updated, " + scan.removed() + " removed" + (scan.markedDeleted() > 0 ? ", " + scan.markedDeleted() + " marked deleted" : "")
            + (scan.note() == null ? "" : ". " + scan.note())));
         if(ok)
         {
            int headed = ObjectIndex.headBacklog(alias, ObjectStorageRuntime.getConfig().getHeadBacklogPerRun());
            results.add(new ProcessSummaryLine(Status.INFO, alias + ": headers read for " + headed + " objects"));
            if(ReferenceScan.applies(ActionSupport.config(alias)))
            {
               ReferenceScan.Result r = ReferenceScan.run(alias);
               results.add(new ProcessSummaryLine(r.missing() > 0 ? Status.WARNING : Status.INFO, alias + ": " + r.references() + " references, " + r.missing() + " missing, " + r.orphaned() + " orphaned objects"));
            }
         }
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Result lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         lines.addAll(results);
      }
   }



   /*******************************************************************************
    ** Check configuration, review.
    *******************************************************************************/
   public static class CheckTransform extends BucketTransform
   {
      private final ProcessSummaryLine will = EtlSupport.line(Status.OK, "bucket's versioning, lifecycle, policy and CORS will be read", "buckets' versioning, lifecycle, policy and CORS will be read",
         "bucket's settings were read", "buckets' settings were read");



      /*******************************************************************************
       ** Count.
       *******************************************************************************/
      @Override
      protected void handle(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         will.incrementCountAndAddPrimaryKey(record.getValue("id"));
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Review lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         will.addSelfToListIfAnyCount(lines);
         lines.add(new ProcessSummaryLine(Status.INFO, "Buckets are created and configured in infrastructure-as-code. This only observes; it changes nothing."));
      }
   }



   /*******************************************************************************
    ** What the provider says a bucket is set to.
    *******************************************************************************/
   public record Observed(String versioning, String lifecycle, String policy, String cors, boolean versioningEnabled, boolean hasLifecycle, boolean hasPolicy, boolean hasCors)
   {
   }



   /*******************************************************************************
    ** Read a bucket's settings (read-only calls). Every call is isolated: a
    ** provider that does not implement one reports "not available".
    *******************************************************************************/
   public static Observed observe(StorageBucketConfig bucket)
   {
      ConnectionRuntime connection = ObjectStorageRuntime.connection(bucket.getConnection());
      S3Client          client     = connection.client();
      String            name       = com.kingsrook.qbits.objectstorage.internal.SecretRefs.resolveSetting(bucket.getBucketName());

      String  versioning        = "not available";
      boolean versioningEnabled = false;
      try
      {
         String status = client.getBucketVersioning(b -> b.bucket(name)).statusAsString();
         versioning = status == null ? "Off" : status;
         versioningEnabled = "Enabled".equals(status);
      }
      catch(RuntimeException e)
      {
         versioning = "not available (" + shortError(e) + ")";
      }

      String  lifecycle    = "not available";
      boolean hasLifecycle = false;
      try
      {
         var rules = client.getBucketLifecycleConfiguration(b -> b.bucket(name)).rules();
         hasLifecycle = !rules.isEmpty();
         List<String> parts = new ArrayList<>();
         rules.forEach(r -> parts.add((r.id() == null ? "rule" : r.id()) + (r.expiration() != null && r.expiration().days() != null ? " expires after " + r.expiration().days() + " days" : "")
            + (r.noncurrentVersionExpiration() != null && r.noncurrentVersionExpiration().noncurrentDays() != null ? ", noncurrent versions after " + r.noncurrentVersionExpiration().noncurrentDays() + " days" : "")));
         lifecycle = rules.isEmpty() ? "none" : rules.size() + " rule(s): " + String.join("; ", parts);
      }
      catch(RuntimeException e)
      {
         lifecycle = isNotConfigured(e, "NoSuchLifecycleConfiguration") ? "none" : "not available (" + shortError(e) + ")";
      }

      String  policy    = "not available";
      boolean hasPolicy = false;
      try
      {
         String json = client.getBucketPolicy(b -> b.bucket(name)).policy();
         int statements = json == null ? 0 : json.split("\"Effect\"", -1).length - 1;
         hasPolicy = statements > 0;
         policy = statements + " statement(s)";
      }
      catch(RuntimeException e)
      {
         policy = isNotConfigured(e, "NoSuchBucketPolicy") ? "none" : "not available (" + shortError(e) + ")";
      }

      String  cors    = "not available";
      boolean hasCors = false;
      try
      {
         int rules = client.getBucketCors(b -> b.bucket(name)).corsRules().size();
         hasCors = rules > 0;
         cors = rules == 0 ? "none" : rules + " rule(s)";
      }
      catch(RuntimeException e)
      {
         cors = isNotConfigured(e, "NoSuchCORSConfiguration") ? "none" : "not available (" + shortError(e) + ")";
      }
      return (new Observed(versioning, lifecycle, policy, cors, versioningEnabled, hasLifecycle, hasPolicy, hasCors));
   }



   /*******************************************************************************
    ** The differences between what the config declares and what is observed.
    *******************************************************************************/
   public static List<String> drift(StorageBucketConfig bucket, Observed o)
   {
      List<String> drift = new ArrayList<>();
      if(bucket.getExpectVersioning() != null && bucket.getExpectVersioning() != o.versioningEnabled())
      {
         drift.add("expected versioning " + (bucket.getExpectVersioning() ? "enabled" : "off") + ", observed " + o.versioning());
      }
      if(bucket.getExpectLifecycle() != null && bucket.getExpectLifecycle() != o.hasLifecycle())
      {
         drift.add("expected " + (bucket.getExpectLifecycle() ? "a lifecycle configuration" : "no lifecycle configuration") + ", observed " + o.lifecycle());
      }
      if(bucket.getExpectCors() != null && bucket.getExpectCors() != o.hasCors())
      {
         drift.add("expected " + (bucket.getExpectCors() ? "CORS rules" : "no CORS rules") + ", observed " + o.cors());
      }
      if(bucket.getExpectPolicy() != null && bucket.getExpectPolicy() != o.hasPolicy())
      {
         drift.add("expected " + (bucket.getExpectPolicy() ? "a bucket policy" : "no bucket policy") + ", observed " + o.policy());
      }
      return (drift);
   }



   /*******************************************************************************
    ** Whether an exception is the documented "not configured" answer.
    *******************************************************************************/
   private static boolean isNotConfigured(RuntimeException e, String code)
   {
      return (code.equals(com.kingsrook.qbits.objectstorage.internal.ErrorMapper.errorCode(e)));
   }



   /*******************************************************************************
    ** A short, safe description of an error.
    *******************************************************************************/
   private static String shortError(RuntimeException e)
   {
      String code = com.kingsrook.qbits.objectstorage.internal.ErrorMapper.errorCode(e);
      return (code != null ? code : e.getClass().getSimpleName());
   }



   /*******************************************************************************
    ** Check configuration, execute.
    *******************************************************************************/
   public static class CheckLoad extends BucketLoad
   {
      private final ArrayList<ProcessSummaryLineInterface> results = new ArrayList<>();



      /*******************************************************************************
       ** Observe one bucket and record what was seen.
       *******************************************************************************/
      @Override
      protected void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         String              alias  = record.getValueString("alias");
         StorageBucketConfig bucket = ActionSupport.config(alias);
         Observed            o      = observe(bucket);
         List<String>        drift  = drift(bucket, o);
         Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", record.getValue("id")).withValue("versioningState", o.versioning().length() > 30 ? o.versioning().substring(0, 30) : o.versioning())
            .withValue("lifecycleSummary", o.lifecycle()).withValue("policySummary", o.policy()).withValue("corsSummary", o.cors())
            .withValue("configDriftNote", drift.isEmpty() ? null : String.join("; ", drift))));
         results.add(new ProcessSummaryLine(Status.INFO, alias + ": versioning " + o.versioning() + ", lifecycle " + o.lifecycle() + ", policy " + o.policy() + ", CORS " + o.cors()
            + " (managed by " + bucket.getManagedBy() + ")"));
         for(String d : drift)
         {
            results.add(new ProcessSummaryLine(Status.WARNING, alias + ": drift - " + d));
         }
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Result lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         lines.addAll(results);
      }
   }



   /*******************************************************************************
    ** Apply header policy to a bucket, review: counts from the index.
    *******************************************************************************/
   public static class BucketPolicyTransform extends BucketTransform
   {
      private final ProcessSummaryLine change = EtlSupport.line(Status.OK, "object in this bucket will change to match the policy", "objects in these buckets will change to match the policy",
         "object was changed to match the policy", "objects were changed to match the policy");
      private final ProcessSummaryLine unknown = EtlSupport.line(Status.WARNING, "object has unknown headers (not yet read; refresh it or wait for the nightly HEAD backlog)",
         "objects have unknown headers (not yet read; refresh them or wait for the nightly HEAD backlog)", "object had unknown headers", "objects had unknown headers");
      private final ProcessSummaryLine backup = EtlSupport.line(Status.INFO, "bucket is a backup bucket (the policy never applies there)", "buckets are backup buckets (the policy never applies there)",
         "bucket was a backup bucket (skipped)", "buckets were backup buckets (skipped)");
      private final ProcessSummaryLine notIndexed = EtlSupport.line(Status.INFO, "bucket is not indexed, so its objects cannot be listed here (skipped)", "buckets are not indexed, so their objects cannot be listed here (skipped)",
         "bucket was not indexed (skipped)", "buckets were not indexed (skipped)");



      /*******************************************************************************
       ** Count what would change.
       *******************************************************************************/
      @Override
      protected void handle(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         StorageBucketConfig config = ActionSupport.config(record.getValueString("alias"));
         if(config == null || config.getRole() == BucketRole.BACKUP)
         {
            backup.incrementCountAndAddPrimaryKey(record.getValue("id"));
            return;
         }
         if(!Boolean.TRUE.equals(config.getIndexed()))
         {
            notIndexed.incrementCountAndAddPrimaryKey(record.getValue("id"));
            return;
         }
         Integer id = record.getValueInteger("id");
         int mismatched = Db.count(ObjectStorageTables.OBJECT, new QQueryFilter(Db.eq("bucketId", id), Db.eq("deleted", false), Db.eq("headersKnown", true), Db.eq("matchesHeaderPolicy", false)));
         int unknownCount = Db.count(ObjectStorageTables.OBJECT, new QQueryFilter(Db.eq("bucketId", id), Db.eq("deleted", false), Db.eq("headersKnown", false)));
         change.incrementCount(mismatched);
         unknown.incrementCount(unknownCount);
         if(mismatched > ObjectStorageRuntime.getConfig().getBulkCap() && !ActionSupport.isAdmin())
         {
            overCap.incrementCount(mismatched - ObjectStorageRuntime.getConfig().getBulkCap());
         }
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Review lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         change.addSelfToListIfAnyCount(lines);
         unknown.addSelfToListIfAnyCount(lines);
         backup.addSelfToListIfAnyCount(lines);
         notIndexed.addSelfToListIfAnyCount(lines);
         if(change.getCount() != null && change.getCount() > 0)
         {
            lines.add(new ProcessSummaryLine(Status.INFO, "Rewriting headers copies each object onto itself, which changes Last-Modified and can change the ETag."));
         }
      }



      /*******************************************************************************
       ** The skips, as the Result screen shows them.
       *******************************************************************************/
      @Override
      protected List<ProcessSummaryLine> skipLines()
      {
         return (List.of(unknown, backup, notIndexed));
      }
   }



   /*******************************************************************************
    ** Apply header policy to a bucket, execute: the objects the index says are off.
    *******************************************************************************/
   public static class BucketPolicyLoad extends BucketLoad
   {
      private final ProcessSummaryLine changed = EtlSupport.line(Status.OK, "object will be changed to match the policy", "objects will be changed to match the policy",
         "object was changed to match the policy", "objects were changed to match the policy");
      private final ProcessSummaryLine failedObjects = EtlSupport.line(Status.ERROR, "object could not be changed (see Operations)", "objects could not be changed (see Operations)",
         "object could not be changed (see Operations)", "objects could not be changed (see Operations)");



      /*******************************************************************************
       ** Rewrite the headers of one bucket's mismatching objects (up to the bulk cap
       ** unless admin).
       *******************************************************************************/
      @Override
      protected void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         String              alias  = record.getValueString("alias");
         StorageBucketConfig config = ActionSupport.config(alias);
         int                 limit  = ActionSupport.isAdmin() ? Integer.MAX_VALUE : ObjectStorageRuntime.getConfig().getBulkCap();
         List<QRecord>       todo   = new ArrayList<>();
         Db.forEachPage(ObjectStorageTables.OBJECT, new QQueryFilter(Db.eq("bucketId", record.getValue("id")), Db.eq("deleted", false), Db.eq("headersKnown", true), Db.eq("matchesHeaderPolicy", false)), 2000,
            rows -> rows.forEach(r ->
            {
               if(todo.size() < limit)
               {
                  todo.add(r);
               }
            }));
         for(QRecord row : todo)
         {
            String key = row.getValueString("objectKey");
            if(ProtectionRules.check(alias, key, ProtectionRules.Op.WRITE, com.kingsrook.qbits.objectstorage.api.StorageCaller.user()).blocked())
            {
               continue;
            }
            try
            {
               ObjectHeaders wanted = ObjectStorageRuntime.headerPolicy().headersFor(config, key, row.getValueString("contentType"));
               ActionSupport.storage(alias).updateHeaders(key, new ObjectHeaders().withCacheControl(wanted.getCacheControl()).withContentDisposition(wanted.getContentDisposition()));
               changed.incrementCount();
            }
            catch(RuntimeException e)
            {
               failedObjects.incrementCount();
            }
         }
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Result lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         changed.addSelfToListIfAnyCount(lines);
         failedObjects.addSelfToListIfAnyCount(lines);
      }
   }



   /*******************************************************************************
    ** Create upload link, review: the key must be valid; the Result shows the link once.
    *******************************************************************************/
   public static class UploadLinkTransform extends BucketTransform
   {
      private final ProcessSummaryLine will = EtlSupport.line(Status.OK, "upload link will be created", "upload links will be created", "upload link was created", "upload links were created");
      private String keyError;



      /*******************************************************************************
       ** Validate the key and the TTL cap.
       *******************************************************************************/
      @Override
      protected void handle(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         try
         {
            StorageKeys.normalize(input.getValueString(FIELD_LINK_KEY));
         }
         catch(IllegalArgumentException e)
         {
            keyError = e.getMessage();
            return;
         }
         StorageBucketConfig config = ActionSupport.config(record.getValueString("alias"));
         if(config == null || config.getRole() == BucketRole.BACKUP)
         {
            keyError = "upload links are not offered for backup buckets";
            return;
         }
         will.incrementCountAndAddPrimaryKey(record.getValue("id"));
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Review lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         if(keyError != null)
         {
            lines.add(new ProcessSummaryLine(Status.ERROR, "The link cannot be created: " + keyError));
            return;
         }
         will.addSelfToListIfAnyCount(lines);
         lines.add(new ProcessSummaryLine(Status.INFO, "A presigned PUT cannot limit how large the upload is, only pin one exact size and type. The link is a bearer token: share it only with the intended uploader."));
      }
   }



   /*******************************************************************************
    ** Create upload link, execute.
    *******************************************************************************/
   public static class UploadLinkLoad extends BucketLoad
   {
      private final ArrayList<ProcessSummaryLineInterface> results = new ArrayList<>();



      /*******************************************************************************
       ** Create the link and show it once.
       *******************************************************************************/
      @Override
      protected void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         String alias = record.getValueString("alias");
         PresignPut request = PresignPut.of(StorageKeys.normalize(input.getValueString(FIELD_LINK_KEY)));
         if(StringUtils.hasContent(input.getValueString(FIELD_LINK_TYPE)))
         {
            request.setContentType(input.getValueString(FIELD_LINK_TYPE));
         }
         if(input.getValue(FIELD_LINK_LENGTH) != null && StringUtils.hasContent(String.valueOf(input.getValue(FIELD_LINK_LENGTH))))
         {
            request.setContentLength(Long.valueOf(String.valueOf(input.getValue(FIELD_LINK_LENGTH))));
         }
         URL url = ActionSupport.storage(alias).presignPut(request);
         int minutes = (int) Math.min(ActionSupport.config(alias).getMaxPresignPutSeconds() / 60, 15);
         ProcessSummaryLine line = new ProcessSummaryLine(Status.OK, "Upload link for " + alias + "/" + request.getKey() + " (expires in about " + minutes + " minutes; shown once)");
         line.setBulletsOfText(new ArrayList<>(List.of(url.toString())));
         results.add(line);
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Result lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         lines.addAll(results);
      }
   }



   /*******************************************************************************
    ** The capability names a link needs (documented in the Review).
    *******************************************************************************/
   static boolean linksSupported(String alias)
   {
      return (!CapabilityStore.ruledOut(ActionSupport.storage(alias).capability(Capability.PRESIGNED_PUT)));
   }



   /*******************************************************************************
    ** A readable TTL.
    *******************************************************************************/
   static String describe(Duration ttl)
   {
      return (String.format(Locale.ROOT, "%d minutes", ttl.toMinutes()));
   }



   /*******************************************************************************
    ** A bulk-edit friendly id list of a page.
    *******************************************************************************/
   static List<Serializable> idsOf(List<QRecord> records)
   {
      List<Serializable> ids = new ArrayList<>();
      records.forEach(r -> ids.add(r.getValue("id")));
      return (ids);
   }
}
