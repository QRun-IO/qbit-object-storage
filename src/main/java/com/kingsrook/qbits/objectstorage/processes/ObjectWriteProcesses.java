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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.kingsrook.qbits.objectstorage.api.Capability;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaders;
import com.kingsrook.qbits.objectstorage.api.StorageBucket;
import com.kingsrook.qbits.objectstorage.api.StorageCaller;
import com.kingsrook.qbits.objectstorage.api.StoredObject;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.index.ObjectIndex;
import com.kingsrook.qbits.objectstorage.internal.CapabilityStore;
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
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Header and metadata actions on objects: Refresh from bucket, Apply header
 ** policy (which replaces app-specific backfills such as the CDN qbit's
 ** cdnBackfillObjectHeaders), Change headers and metadata, and Edit tags.
 ** Rewriting headers copies an object onto itself, which changes Last-Modified
 ** and can change the ETag; the Review says so.
 *******************************************************************************/
public final class ObjectWriteProcesses
{
   public static final String REFRESH_NAME = "objectStorageRefreshObjects";
   public static final String POLICY_NAME  = "objectStorageApplyHeaderPolicy";
   public static final String HEADERS_NAME = "objectStorageChangeHeaders";
   public static final String TAGS_NAME    = "objectStorageEditTags";

   public static final String FIELD_CACHE_CONTROL       = "cacheControl";
   public static final String FIELD_CONTENT_TYPE        = "contentType";
   public static final String FIELD_CONTENT_DISPOSITION = "contentDisposition";
   public static final String FIELD_USER_METADATA       = "userMetadata";
   public static final String FIELD_TAGS                = "tags";

   /** Typed into a header field to remove that header. */
   public static final String CLEAR = "-";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ObjectWriteProcesses()
   {
   }



   /*******************************************************************************
    ** The processes.
    *******************************************************************************/
   public static List<QProcessMetaData> processes()
   {
      return (List.of(
         EtlSupport.etl(REFRESH_NAME, "Refresh from bucket", "sync", ObjectStorageTables.OBJECT, RefreshTransform.class, RefreshLoad.class,
            "This is a preview of the objects that will be read from the bucket.", ObjectStoragePermissions.VIEW),
         EtlSupport.etl(POLICY_NAME, "Apply header policy", "rule", ObjectStorageTables.OBJECT, PolicyTransform.class, PolicyLoad.class,
            "This is a preview of the objects whose headers will be rewritten to match the header policy.", ObjectStoragePermissions.WRITE),
         EtlSupport.withInput(EtlSupport.etl(HEADERS_NAME, "Change headers and metadata", "edit_note", ObjectStorageTables.OBJECT, HeadersTransform.class, HeadersLoad.class,
            "This is a preview of the headers that will change.", ObjectStoragePermissions.WRITE),
            "Change headers and metadata", "Fill in only what you want to change; blank fields are left alone, and a single - removes that header. Metadata is one key=value per line; key= removes a key.",
            EtlSupport.stringField(FIELD_CACHE_CONTROL, "Cache-Control", false), EtlSupport.stringField(FIELD_CONTENT_TYPE, "Content-Type", false),
            EtlSupport.stringField(FIELD_CONTENT_DISPOSITION, "Content-Disposition", false), new QFieldMetaData(FIELD_USER_METADATA, QFieldType.TEXT).withLabel("Metadata (key=value per line)")),
         EtlSupport.withInput(EtlSupport.etl(TAGS_NAME, "Edit tags", "sell", ObjectStorageTables.OBJECT, TagsTransform.class, TagsLoad.class,
            "This is a preview of the objects whose tags will be replaced.", ObjectStoragePermissions.WRITE),
            "Edit tags", "Replaces the tags of the selected objects. One key=value per line; leave it empty to remove all tags. Providers without object tagging are skipped.",
            new QFieldMetaData(FIELD_TAGS, QFieldType.TEXT).withLabel("Tags (key=value per line)"))));
   }



   /*******************************************************************************
    ** Blank to null.
    *******************************************************************************/
   static String blankToNull(String s)
   {
      return (StringUtils.hasContent(s) ? s.trim() : null);
   }



   /*******************************************************************************
    ** Parse "key=value" lines. A key with an empty value is kept with the empty
    ** value (the caller decides what that means).
    *******************************************************************************/
   public static Map<String, String> parseKeyValues(String text)
   {
      Map<String, String> map = new LinkedHashMap<>();
      if(text == null)
      {
         return (map);
      }
      for(String line : text.split("\\r?\\n"))
      {
         String trimmed = line.trim();
         if(trimmed.isEmpty())
         {
            continue;
         }
         int eq = trimmed.indexOf('=');
         if(eq <= 0)
         {
            throw (new IllegalArgumentException("Not key=value: " + trimmed));
         }
         map.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
      }
      return (map);
   }



   /*******************************************************************************
    ** The current headers of an object row: from the index when known, else HEAD.
    ** Empty when the object cannot be read.
    *******************************************************************************/
   static Optional<ObjectHeaders> currentHeaders(QRecord row, StorageBucket storage)
   {
      if(Boolean.TRUE.equals(row.getValueBoolean("headersKnown")))
      {
         ObjectHeaders h = new ObjectHeaders().withCacheControl(row.getValueString("cacheControl")).withContentType(row.getValueString("contentType"))
            .withContentDisposition(row.getValueString("contentDisposition"));
         try
         {
            h.setUserMetadata(StringUtils.hasContent(row.getValueString("userMetadata")) ? com.kingsrook.qqq.backend.core.utils.JsonUtils.toObject(row.getValueString("userMetadata"), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, String>>()
            {
            }) : new LinkedHashMap<>());
         }
         catch(java.io.IOException e)
         {
            h.setUserMetadata(new LinkedHashMap<>());
         }
         return (Optional.of(h));
      }
      try
      {
         Optional<StoredObject> head = storage.head(row.getValueString("objectKey"));
         return (head.map(StoredObject::getHeaders));
      }
      catch(RuntimeException e)
      {
         return (Optional.empty());
      }
   }



   /*******************************************************************************
    ** Refresh from bucket, review.
    *******************************************************************************/
   public static class RefreshTransform extends BaseTransform
   {
      private final ProcessSummaryLine will = EtlSupport.line(Status.OK, "object will be read from the bucket", "objects will be read from the bucket", "object was refreshed", "objects were refreshed");



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
      }
   }



   /*******************************************************************************
    ** Refresh from bucket, execute.
    *******************************************************************************/
   public static class RefreshLoad extends BaseLoad
   {
      private final ProcessSummaryLine refreshed = EtlSupport.line(Status.OK, "object will be refreshed", "objects will be refreshed", "object was refreshed from the bucket", "objects were refreshed from the bucket");
      private final ProcessSummaryLine gone = EtlSupport.line(Status.WARNING, "object no longer exists and will be marked deleted", "objects no longer exist and will be marked deleted",
         "object no longer exists and was marked deleted", "objects no longer exist and were marked deleted");
      private final ProcessSummaryLine unreadable = EtlSupport.line(Status.ERROR, "object could not be read", "objects could not be read", "object could not be read", "objects could not be read");



      /*******************************************************************************
       ** Refresh one object (a page at a time would be faster; HEAD is per object anyway).
       *******************************************************************************/
      @Override
      protected void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         int[] counts = ObjectIndex.refresh(ActionSupport.aliasOf(record), List.of(record));
         if(counts[2] > 0)
         {
            unreadable.incrementCountAndAddPrimaryKey(record.getValue("id"));
         }
         else if(counts[1] > 0)
         {
            gone.incrementCountAndAddPrimaryKey(record.getValue("id"));
         }
         else
         {
            refreshed.incrementCountAndAddPrimaryKey(record.getValue("id"));
         }
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Result lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         refreshed.addSelfToListIfAnyCount(lines);
         gone.addSelfToListIfAnyCount(lines);
         unreadable.addSelfToListIfAnyCount(lines);
      }
   }



   /*******************************************************************************
    ** Apply header policy, review: already correct / will change / failed to read.
    *******************************************************************************/
   public static class PolicyTransform extends BaseTransform
   {
      private final ProcessSummaryLine correct = EtlSupport.line(Status.INFO, "object already matches the policy (or no rule applies)", "objects already match the policy (or no rule applies)",
         "object already matched the policy (no change)", "objects already matched the policy (no change)");
      private final ProcessSummaryLine change = EtlSupport.line(Status.OK, "object will change to match the policy", "objects will change to match the policy",
         "object was changed to match the policy", "objects were changed to match the policy");
      private final ProcessSummaryLine unreadable = EtlSupport.line(Status.ERROR, "object could not be read, so it is skipped", "objects could not be read, so they are skipped",
         "object could not be read, so it was skipped", "objects could not be read, so they were skipped");
      private final ProcessSummaryLine protectedLine = EtlSupport.line(Status.WARNING, "object is skipped: it is protected", "objects are skipped: they are protected",
         "object was skipped: it is protected", "objects were skipped: they are protected");
      private final ProcessSummaryLine backup = EtlSupport.line(Status.INFO, "object is skipped: it is in a backup bucket (the policy never applies there)",
         "objects are skipped: they are in backup buckets (the policy never applies there)", "object was skipped: backup bucket", "objects were skipped: backup buckets");
      private final ProcessSummaryLine deleted = EtlSupport.line(Status.INFO, "object is skipped: it is deleted", "objects are skipped: they are deleted", "object was skipped: it is deleted",
         "objects were skipped: they are deleted");



      /*******************************************************************************
       ** Compare one object with the policy.
       *******************************************************************************/
      @Override
      protected void handle(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         Serializable id = record.getValue("id");
         if(Boolean.TRUE.equals(record.getValueBoolean("deleted")))
         {
            deleted.incrementCountAndAddPrimaryKey(id);
            return;
         }
         ActionSupport.Classification c = ActionSupport.classify(record, ProtectionRules.Op.WRITE, false, false);
         switch(c.skip())
         {
            case PROTECTED:
               protectedLine.incrementCountAndAddPrimaryKey(id);
               return;
            case BACKUP_BUCKET:
               backup.incrementCountAndAddPrimaryKey(id);
               return;
            case NO_BUCKET_ACCESS:
               noAccess.incrementCountAndAddPrimaryKey(id);
               return;
            default:
               break;
         }
         String              alias  = ActionSupport.aliasOf(record);
         StorageBucketConfig config = ActionSupport.config(alias);
         Optional<ObjectHeaders> current = currentHeaders(record, ActionSupport.storage(alias));
         if(current.isEmpty())
         {
            unreadable.incrementCountAndAddPrimaryKey(id);
            return;
         }
         String        key    = record.getValueString("objectKey");
         ObjectHeaders wanted = ObjectStorageRuntime.headerPolicy().headersFor(config, key, current.get().getContentType());
         if(ObjectIndex.matchesPolicy(config, key, current.get()))
         {
            correct.incrementCountAndAddPrimaryKey(id);
            return;
         }
         change.incrementCountAndAddPrimaryKey(id);
         if(change.getBulletsOfText() == null)
         {
            change.setBulletsOfText(new ArrayList<>());
         }
         if(change.getBulletsOfText().size() < 5)
         {
            change.getBulletsOfText().add(key + ": Cache-Control " + (current.get().getCacheControl() == null ? "(none)" : current.get().getCacheControl()) + " -> "
               + (wanted.getCacheControl() == null ? "(unchanged)" : wanted.getCacheControl()));
         }
         record.setValue("_wantCache", wanted.getCacheControl());
         record.setValue("_wantDisposition", wanted.getContentDisposition());
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Review lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         change.addSelfToListIfAnyCount(lines);
         if(change.getCount() != null && change.getCount() > 0)
         {
            lines.add(EtlSupport.text(Status.INFO, "Rewriting headers copies each object onto itself, which changes Last-Modified and can change the ETag."));
         }
         correct.addSelfToListIfAnyCount(lines);
         unreadable.addSelfToListIfAnyCount(lines);
         protectedLine.addSelfToListIfAnyCount(lines);
         backup.addSelfToListIfAnyCount(lines);
         deleted.addSelfToListIfAnyCount(lines);
      }



      /*******************************************************************************
       ** The skips, as the Result screen shows them.
       *******************************************************************************/
      @Override
      protected List<ProcessSummaryLine> skipLines()
      {
         return (List.of(correct, unreadable, protectedLine, backup, deleted));
      }
   }



   /*******************************************************************************
    ** Apply header policy, execute.
    *******************************************************************************/
   public static class PolicyLoad extends BaseLoad
   {
      private final ProcessSummaryLine changed = EtlSupport.line(Status.OK, "object will change to match the policy", "objects will change to match the policy",
         "object was changed to match the policy", "objects were changed to match the policy");



      /*******************************************************************************
       ** Rewrite one object's headers.
       *******************************************************************************/
      @Override
      protected void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         ActionSupport.storage(ActionSupport.aliasOf(record)).updateHeaders(record.getValueString("objectKey"),
            new ObjectHeaders().withCacheControl(record.getValueString("_wantCache")).withContentDisposition(record.getValueString("_wantDisposition")));
         changed.incrementCountAndAddPrimaryKey(record.getValue("id"));
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Result lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         changed.addSelfToListIfAnyCount(lines);
      }
   }



   /*******************************************************************************
    ** Change headers and metadata, review: before -> after.
    *******************************************************************************/
   public static class HeadersTransform extends BaseTransform
   {
      private final ProcessSummaryLine change = EtlSupport.line(Status.OK, "object will change", "objects will change", "object was changed", "objects were changed");
      private final ProcessSummaryLine same = EtlSupport.line(Status.INFO, "object already has these values (no change)", "objects already have these values (no change)",
         "object already had these values (no change)", "objects already had these values (no change)");
      private final ProcessSummaryLine unreadable = EtlSupport.line(Status.ERROR, "object could not be read, so it is skipped", "objects could not be read, so they are skipped",
         "object could not be read, so it was skipped", "objects could not be read, so they were skipped");
      private final ProcessSummaryLine protectedLine = EtlSupport.line(Status.WARNING, "object is skipped: it is protected", "objects are skipped: they are protected",
         "object was skipped: it is protected", "objects were skipped: they are protected");
      private final ProcessSummaryLine backup = EtlSupport.line(Status.INFO, "object is skipped: it is in a backup bucket", "objects are skipped: they are in backup buckets",
         "object was skipped: backup bucket", "objects were skipped: backup buckets");
      private boolean nothing = false;
      private String  inputError;



      /*******************************************************************************
       ** Compare before and after for one object.
       *******************************************************************************/
      @Override
      protected void handle(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         Serializable id = record.getValue("id");
         ObjectHeaders wanted = wanted(input);
         if(inputError != null)
         {
            return;
         }
         if(wanted.isEmpty())
         {
            nothing = true;
            return;
         }
         ActionSupport.Classification c = ActionSupport.classify(record, ProtectionRules.Op.WRITE, false, false);
         switch(c.skip())
         {
            case PROTECTED:
               protectedLine.incrementCountAndAddPrimaryKey(id);
               return;
            case BACKUP_BUCKET:
               backup.incrementCountAndAddPrimaryKey(id);
               return;
            case NO_BUCKET_ACCESS:
               noAccess.incrementCountAndAddPrimaryKey(id);
               return;
            default:
               break;
         }
         Optional<ObjectHeaders> current = currentHeaders(record, ActionSupport.storage(ActionSupport.aliasOf(record)));
         if(current.isEmpty())
         {
            unreadable.incrementCountAndAddPrimaryKey(id);
            return;
         }
         ObjectHeaders now = current.get();
         List<String> diffs = new ArrayList<>();
         diff(diffs, "Cache-Control", now.getCacheControl(), wanted.getCacheControl());
         diff(diffs, "Content-Type", now.getContentType(), wanted.getContentType());
         diff(diffs, "Content-Disposition", now.getContentDisposition(), wanted.getContentDisposition());
         if(wanted.getUserMetadata() != null)
         {
            for(Map.Entry<String, String> e : wanted.getUserMetadata().entrySet())
            {
               String before = now.getUserMetadata() == null ? null : now.getUserMetadata().get(e.getKey());
               diff(diffs, "metadata " + e.getKey(), before, e.getValue().isEmpty() ? CLEAR : e.getValue());
            }
         }
         if(diffs.isEmpty())
         {
            same.incrementCountAndAddPrimaryKey(id);
            return;
         }
         change.incrementCountAndAddPrimaryKey(id);
         if(change.getBulletsOfText() == null)
         {
            change.setBulletsOfText(new ArrayList<>());
         }
         if(change.getBulletsOfText().size() < 5)
         {
            change.getBulletsOfText().add(record.getValueString("objectKey") + ": " + String.join("; ", diffs));
         }
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** One before -> after entry when the wanted value differs.
       *******************************************************************************/
      private static void diff(List<String> diffs, String what, String before, String wanted)
      {
         if(wanted == null)
         {
            return;
         }
         String after = CLEAR.equals(wanted) ? null : wanted;
         if(!java.util.Objects.equals(before, after))
         {
            diffs.add(what + " " + (before == null ? "(none)" : before) + " -> " + (after == null ? "(removed)" : after));
         }
      }



      /*******************************************************************************
       ** What the user asked for, from the input form.
       *******************************************************************************/
      private ObjectHeaders wanted(RunBackendStepInput input)
      {
         ObjectHeaders h = new ObjectHeaders().withCacheControl(ObjectWriteProcesses.blankToNull(input.getValueString(FIELD_CACHE_CONTROL))).withContentType(ObjectWriteProcesses.blankToNull(input.getValueString(FIELD_CONTENT_TYPE)))
            .withContentDisposition(ObjectWriteProcesses.blankToNull(input.getValueString(FIELD_CONTENT_DISPOSITION)));
         try
         {
            Map<String, String> metadata = parseKeyValues(input.getValueString(FIELD_USER_METADATA));
            h.setUserMetadata(metadata.isEmpty() ? null : metadata);
         }
         catch(IllegalArgumentException e)
         {
            inputError = e.getMessage();
         }
         return (h);
      }



      /*******************************************************************************
       ** Review lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         if(inputError != null)
         {
            lines.add(EtlSupport.text(Status.ERROR, "The metadata is not valid: " + inputError));
            return;
         }
         if(nothing)
         {
            lines.add(EtlSupport.text(Status.ERROR, "Nothing to change: fill in at least one header or metadata value."));
            return;
         }
         change.addSelfToListIfAnyCount(lines);
         if(change.getCount() != null && change.getCount() > 0)
         {
            lines.add(EtlSupport.text(Status.INFO, "Changing headers copies each object onto itself, which changes Last-Modified and can change the ETag."));
         }
         same.addSelfToListIfAnyCount(lines);
         unreadable.addSelfToListIfAnyCount(lines);
         protectedLine.addSelfToListIfAnyCount(lines);
         backup.addSelfToListIfAnyCount(lines);
      }



      /*******************************************************************************
       ** The skips, as the Result screen shows them.
       *******************************************************************************/
      @Override
      protected List<ProcessSummaryLine> skipLines()
      {
         return (List.of(same, unreadable, protectedLine, backup));
      }
   }



   /*******************************************************************************
    ** Change headers and metadata, execute.
    *******************************************************************************/
   public static class HeadersLoad extends BaseLoad
   {
      private final ProcessSummaryLine changed = EtlSupport.line(Status.OK, "object will change", "objects will change", "object was changed", "objects were changed");



      /*******************************************************************************
       ** Apply the requested headers to one object, merging the metadata.
       *******************************************************************************/
      @Override
      protected void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         StorageBucket storage = ActionSupport.storage(ActionSupport.aliasOf(record));
         String        key     = record.getValueString("objectKey");
         StoredObject  now     = storage.head(key).orElseThrow(() -> new com.kingsrook.qbits.objectstorage.api.StorageException(com.kingsrook.qbits.objectstorage.api.StorageException.Reason.NOT_FOUND, "No such object"));
         ObjectHeaders change  = new ObjectHeaders();
         change.setCacheControl(resolve(input.getValueString(FIELD_CACHE_CONTROL), now.getHeaders().getCacheControl()));
         change.setContentType(resolve(input.getValueString(FIELD_CONTENT_TYPE), now.getHeaders().getContentType()));
         change.setContentDisposition(resolve(input.getValueString(FIELD_CONTENT_DISPOSITION), now.getHeaders().getContentDisposition()));
         Map<String, String> metadata = new LinkedHashMap<>(now.getHeaders().getUserMetadata() == null ? Map.of() : now.getHeaders().getUserMetadata());
         for(Map.Entry<String, String> e : parseKeyValues(input.getValueString(FIELD_USER_METADATA)).entrySet())
         {
            if(e.getValue().isEmpty())
            {
               metadata.remove(e.getKey());
            }
            else
            {
               metadata.put(e.getKey(), e.getValue());
            }
         }
         change.setUserMetadata(metadata);
         storage.updateHeaders(key, change);
         changed.incrementCountAndAddPrimaryKey(record.getValue("id"));
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** The new value of a header: blank keeps the current one, "-" removes it.
       *******************************************************************************/
      private static String resolve(String requested, String current)
      {
         if(!StringUtils.hasContent(requested))
         {
            return (current);
         }
         return (CLEAR.equals(requested.trim()) ? "" : requested.trim());
      }



      /*******************************************************************************
       ** Result lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         changed.addSelfToListIfAnyCount(lines);
      }
   }



   /*******************************************************************************
    ** Edit tags, review: only where tagging is supported.
    *******************************************************************************/
   public static class TagsTransform extends BaseTransform
   {
      private final ProcessSummaryLine will = EtlSupport.line(Status.OK, "object will get these tags", "objects will get these tags", "object got these tags", "objects got these tags");
      private final ProcessSummaryLine unsupported = EtlSupport.line(Status.WARNING, "object is skipped: its provider has no object tagging (or it is untested)",
         "objects are skipped: their providers have no object tagging (or it is untested)", "object was skipped: no object tagging", "objects were skipped: no object tagging");
      private final ProcessSummaryLine protectedLine = EtlSupport.line(Status.WARNING, "object is skipped: it is protected", "objects are skipped: they are protected",
         "object was skipped: it is protected", "objects were skipped: they are protected");
      private String inputError;



      /*******************************************************************************
       ** Classify one object.
       *******************************************************************************/
      @Override
      protected void handle(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         Serializable id = record.getValue("id");
         try
         {
            parseKeyValues(input.getValueString(FIELD_TAGS));
         }
         catch(IllegalArgumentException e)
         {
            inputError = e.getMessage();
            return;
         }
         String alias = ActionSupport.aliasOf(record);
         if(!CapabilityStore.usable(ActionSupport.storage(alias).capability(Capability.OBJECT_TAGGING)))
         {
            unsupported.incrementCountAndAddPrimaryKey(id);
            return;
         }
         if(ProtectionRules.check(alias, record.getValueString("objectKey"), ProtectionRules.Op.WRITE, StorageCaller.user()).blocked())
         {
            protectedLine.incrementCountAndAddPrimaryKey(id);
            return;
         }
         will.incrementCountAndAddPrimaryKey(id);
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Review lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         if(inputError != null)
         {
            lines.add(EtlSupport.text(Status.ERROR, "The tags are not valid: " + inputError));
            return;
         }
         will.addSelfToListIfAnyCount(lines);
         unsupported.addSelfToListIfAnyCount(lines);
         protectedLine.addSelfToListIfAnyCount(lines);
      }



      /*******************************************************************************
       ** The skips, as the Result screen shows them.
       *******************************************************************************/
      @Override
      protected List<ProcessSummaryLine> skipLines()
      {
         return (List.of(unsupported, protectedLine));
      }
   }



   /*******************************************************************************
    ** Edit tags, execute.
    *******************************************************************************/
   public static class TagsLoad extends BaseLoad
   {
      private final ProcessSummaryLine done = EtlSupport.line(Status.OK, "object will get these tags", "objects will get these tags", "object got these tags", "objects got these tags");



      /*******************************************************************************
       ** Replace one object's tags.
       *******************************************************************************/
      @Override
      protected void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         ActionSupport.storage(ActionSupport.aliasOf(record)).setTags(record.getValueString("objectKey"), parseKeyValues(input.getValueString(FIELD_TAGS)));
         done.incrementCountAndAddPrimaryKey(record.getValue("id"));
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Result lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         done.addSelfToListIfAnyCount(lines);
      }
   }
}
