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


import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.api.PutRequest;
import com.kingsrook.qbits.objectstorage.api.StorageCaller;
import com.kingsrook.qbits.objectstorage.api.StorageException;
import com.kingsrook.qbits.objectstorage.api.StorageKeys;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.internal.ProtectionRules;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.permissions.ObjectStoragePermissions;
import com.kingsrook.qqq.backend.core.actions.tables.StorageAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.storage.StorageInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.AdornmentType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractExtractStep;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** "Upload files": v1 uploads go through the app server (up to the bucket's
 ** upload limit, 100 MB per file by default) and are streamed to the store in
 ** parts. The file arrives the standard QQQ way (a file-upload form field, kept
 ** in the app's uploaded-file archive table), so the app needs its
 ** uploadedFileArchiveTableName set, as for any file upload. Direct browser
 ** uploads for larger files come later.
 **
 ** From a folder record the destination is that folder; from the table you pick
 ** the bucket and folder. Existing objects are never overwritten unless asked,
 ** and the header policy decides the new objects' headers.
 *******************************************************************************/
public final class UploadProcess
{
   public static final String UPLOAD_NAME    = "objectStorageUploadFiles";
   public static final String UPLOAD_TO_NAME = "objectStorageUploadToFolder";

   public static final String FIELD_FILE   = "theFile";
   public static final String FIELD_BUCKET = "uploadBucket";
   public static final String FIELD_PREFIX = "uploadPrefix";
   public static final String FIELD_OVER   = "uploadOverwrite";

   /** How uploaded files are opened; replaceable in tests (the default reads the app's archive table). */
   private static final Function<Serializable, InputStream> DEFAULT_OPENER = file ->
   {
      try
      {
         return (new StorageAction().getInputStream((StorageInput) file));
      }
      catch(QException e)
      {
         throw (new IllegalStateException("Could not open the uploaded file: " + e.getMessage(), e));
      }
   };

   private static volatile Function<Serializable, InputStream> opener = DEFAULT_OPENER;



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private UploadProcess()
   {
   }



   /*******************************************************************************
    ** Replace how uploaded files are opened (tests).
    *******************************************************************************/
   public static void setOpener(Function<Serializable, InputStream> newOpener)
   {
      opener = newOpener == null ? DEFAULT_OPENER : newOpener;
   }



   /*******************************************************************************
    ** The processes.
    *******************************************************************************/
   public static List<QProcessMetaData> processes()
   {
      QFieldMetaData file = new QFieldMetaData(FIELD_FILE, QFieldType.BLOB).withLabel("Files").withIsRequired(true)
         .withFieldAdornment(AdornmentType.FileUploadAdornment.newFieldAdornment().withValue(AdornmentType.FileUploadAdornment.formatDragAndDrop()).withValue(AdornmentType.FileUploadAdornment.widthFull()));
      QFieldMetaData bucket = new QFieldMetaData(FIELD_BUCKET, QFieldType.INTEGER).withLabel("Bucket").withPossibleValueSourceName(ObjectStorageTables.BUCKET);
      QFieldMetaData prefix = EtlSupport.stringField(FIELD_PREFIX, "Folder (for example media/2026/; leave blank for the bucket root)", false);
      QFieldMetaData over   = EtlSupport.booleanField(FIELD_OVER, "Overwrite objects that already exist", false);
      String help = "Uploads go through the app server and are limited per file (100 MB by default). The header policy decides the headers of the new objects.";

      QFieldMetaData requiredBucket = new QFieldMetaData(FIELD_BUCKET, QFieldType.INTEGER).withLabel("Bucket").withIsRequired(true).withPossibleValueSourceName(ObjectStorageTables.BUCKET);
      return (List.of(
         EtlSupport.withInput(EtlSupport.etl(UPLOAD_NAME, "Upload files", "upload_file", ObjectStorageTables.OBJECT, ObjectStorageTables.OBJECT, UploadExtractStep.class,
            UploadTransform.class, UploadLoad.class, "This is a preview of the files that will be uploaded.", ObjectStoragePermissions.WRITE), "Upload files", help, file, requiredBucket, prefix, over),
         EtlSupport.withInput(EtlSupport.etl(UPLOAD_TO_NAME, "Upload files to this folder", "upload_file", ObjectStorageTables.OBJECT, ObjectStorageTables.FOLDER, UploadExtractStep.class,
            UploadTransform.class, UploadLoad.class, "This is a preview of the files that will be uploaded to the folder.", ObjectStoragePermissions.WRITE),
            "Upload files to this folder", "The files go into the folder you started from (or the bucket and folder you fill in). " + help, file, bucket, prefix, over)));
   }



   /*******************************************************************************
    ** One record per uploaded file.
    *******************************************************************************/
   public static class UploadExtractStep extends AbstractExtractStep
   {
      /*******************************************************************************
       ** The uploaded files, as the file-upload field stored them.
       *******************************************************************************/
      @SuppressWarnings("unchecked")
      private static List<Serializable> files(RunBackendStepInput input)
      {
         Serializable value = input.getValue(FIELD_FILE);
         return (value instanceof List<?> list ? new ArrayList<>((List<Serializable>) list) : new ArrayList<>());
      }



      /*******************************************************************************
       ** How many files.
       *******************************************************************************/
      @Override
      public Integer doCount(RunBackendStepInput runBackendStepInput)
      {
         return (files(runBackendStepInput).size());
      }



      /*******************************************************************************
       ** Put one record per file in the pipe.
       *******************************************************************************/
      @Override
      public void run(RunBackendStepInput runBackendStepInput, RunBackendStepOutput runBackendStepOutput) throws QException
      {
         int index = 0;
         for(Serializable file : files(runBackendStepInput))
         {
            String reference = file instanceof StorageInput si && si.getReference() != null ? si.getReference() : String.valueOf(file);
            String name      = reference.replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1);
            getRecordPipe().addRecord(new QRecord().withValue("id", ++index).withValue("name", name).withValue("_file", file));
         }
      }
   }



   /*******************************************************************************
    ** Where the files go: the chosen bucket and folder, or the folder the action
    ** was started from.
    *******************************************************************************/
   record Destination(String alias, String prefix, String error)
   {
   }



   /*******************************************************************************
    ** Work out the destination from the inputs and, from a folder record, that folder.
    *******************************************************************************/
   static Destination destination(RunBackendStepInput input) throws QException
   {
      String alias  = CopyMoveProcesses.aliasOfBucketId(input.getValue(FIELD_BUCKET));
      String prefix = input.getValueString(FIELD_PREFIX);
      String folderIds = input.getValueString("recordIds");
      if(alias == null && StringUtils.hasContent(folderIds) && !folderIds.contains(","))
      {
         QRecord folder = Db.first(ObjectStorageTables.FOLDER, "id", Integer.valueOf(folderIds.trim()));
         if(folder != null)
         {
            alias = ActionSupport.aliasOf(folder);
            prefix = StringUtils.hasContent(prefix) ? folder.getValueString("path") + prefix : folder.getValueString("path");
         }
      }
      if(alias == null)
      {
         return (new Destination(null, null, "Choose a bucket."));
      }
      StorageBucketConfig config = ActionSupport.config(alias);
      if(config == null || config.getRole() == BucketRole.BACKUP)
      {
         return (new Destination(alias, prefix, "Uploads are not offered for backup buckets."));
      }
      String p = prefix == null ? "" : prefix.trim();
      if(!p.isEmpty() && !p.endsWith("/"))
      {
         p = p + "/";
      }
      return (new Destination(alias, p, null));
   }



   /*******************************************************************************
    ** Upload, review.
    *******************************************************************************/
   public static class UploadTransform extends BaseTransform
   {
      private final ProcessSummaryLine will = EtlSupport.line(Status.OK, "file will be uploaded", "files will be uploaded", "file was uploaded", "files were uploaded");
      private final ProcessSummaryLine overwrites = EtlSupport.line(Status.WARNING, "file will replace an object that already exists", "files will replace objects that already exist",
         "file replaced an object that already existed", "files replaced objects that already existed");
      private final ProcessSummaryLine exists = EtlSupport.line(Status.WARNING, "file is skipped: an object with that name exists (and overwrite is off)", "files are skipped: objects with those names exist (and overwrite is off)",
         "file was skipped: the object already existed", "files were skipped: the objects already existed");
      private final ProcessSummaryLine invalid = EtlSupport.line(Status.ERROR, "file is skipped: its name makes an invalid key", "files are skipped: their names make invalid keys",
         "file was skipped: invalid key", "files were skipped: invalid keys");
      private final ProcessSummaryLine protectedLine = EtlSupport.line(Status.WARNING, "file is skipped: the destination is protected", "files are skipped: the destination is protected",
         "file was skipped: protected", "files were skipped: protected");
      private Destination destination;
      private boolean     described = false;



      /*******************************************************************************
       ** Classify one file.
       *******************************************************************************/
      @Override
      protected void handle(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         if(destination == null)
         {
            destination = destination(input);
         }
         if(destination.error() != null)
         {
            return;
         }
         if(!ActionSupport.canSeeBucket(com.kingsrook.qbits.objectstorage.internal.ConfigSync.bucketId(destination.alias()), bucketAccess))
         {
            destination = new Destination(destination.alias(), destination.prefix(), "You may not use that bucket.");
            return;
         }
         Serializable id = record.getValue("id");
         String key;
         try
         {
            key = StorageKeys.normalize(destination.prefix() + StorageKeys.safeFilename(record.getValueString("name")));
         }
         catch(IllegalArgumentException e)
         {
            invalid.incrementCountAndAddPrimaryKey(id);
            return;
         }
         if(ProtectionRules.check(destination.alias(), key, ProtectionRules.Op.WRITE, StorageCaller.user()).blocked())
         {
            protectedLine.incrementCountAndAddPrimaryKey(id);
            return;
         }
         boolean exists = ActionSupport.storage(destination.alias()).head(key).isPresent();
         if(exists)
         {
            if(!Boolean.TRUE.equals(input.getValueBoolean(FIELD_OVER)))
            {
               this.exists.incrementCountAndAddPrimaryKey(id);
               return;
            }
            overwrites.incrementCountAndAddPrimaryKey(id);
         }
         will.incrementCountAndAddPrimaryKey(id);
         record.setValue("_alias", destination.alias());
         record.setValue("_key", key);
         output.getRecords().add(record);
         described = true;
      }



      /*******************************************************************************
       ** Review lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         if(destination != null && destination.error() != null)
         {
            lines.add(new ProcessSummaryLine(Status.ERROR, destination.error()));
            return;
         }
         will.addSelfToListIfAnyCount(lines);
         if(described && destination != null)
         {
            StorageBucketConfig config = ActionSupport.config(destination.alias());
            lines.add(new ProcessSummaryLine(Status.INFO, "Destination: " + destination.alias() + "/" + destination.prefix() + ". Each file may be up to " + ActionSupport.bytes(config.getMaxUploadBytes())
               + ". The header policy sets the new objects' headers."));
         }
         overwrites.addSelfToListIfAnyCount(lines);
         exists.addSelfToListIfAnyCount(lines);
         invalid.addSelfToListIfAnyCount(lines);
         protectedLine.addSelfToListIfAnyCount(lines);
      }



      /*******************************************************************************
       ** The skips, as the Result screen shows them.
       *******************************************************************************/
      @Override
      protected List<ProcessSummaryLine> skipLines()
      {
         return (List.of(exists, invalid, protectedLine));
      }
   }



   /*******************************************************************************
    ** A stream that fails once it passes a byte limit.
    *******************************************************************************/
   static class LimitedInputStream extends FilterInputStream
   {
      private final long limit;
      private long       read = 0;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      LimitedInputStream(InputStream in, long limit)
      {
         super(in);
         this.limit = limit;
      }



      /*******************************************************************************
       ** Count one byte.
       *******************************************************************************/
      @Override
      public int read() throws IOException
      {
         int b = super.read();
         if(b >= 0)
         {
            count(1);
         }
         return (b);
      }



      /*******************************************************************************
       ** Count a block.
       *******************************************************************************/
      @Override
      public int read(byte[] buffer, int off, int len) throws IOException
      {
         int n = super.read(buffer, off, len);
         if(n > 0)
         {
            count(n);
         }
         return (n);
      }



      /*******************************************************************************
       ** Fail past the limit.
       *******************************************************************************/
      private void count(int n) throws IOException
      {
         read += n;
         if(read > limit)
         {
            throw (new IOException("The file is larger than the upload limit of " + ActionSupport.bytes(limit)));
         }
      }
   }



   /*******************************************************************************
    ** Upload, execute: stream each file to the store.
    *******************************************************************************/
   public static class UploadLoad extends BaseLoad
   {
      private final ProcessSummaryLine uploaded = EtlSupport.line(Status.OK, "file will be uploaded", "files will be uploaded", "file was uploaded", "files were uploaded");
      private long bytes = 0;



      /*******************************************************************************
       ** Upload one file.
       *******************************************************************************/
      @Override
      protected void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         String alias = record.getValueString("_alias");
         String key   = record.getValueString("_key");
         StorageBucketConfig config = ActionSupport.config(alias);
         try(InputStream in = new LimitedInputStream(opener.apply(record.getValue("_file")), config.getMaxUploadBytes()))
         {
            long before = 0;
            var  result = ObjectStorage.bucket(alias).put(PutRequest.of(key, in));
            bytes += result.getBytes() == null ? before : result.getBytes();
         }
         catch(IOException e)
         {
            throw (new StorageException(StorageException.Reason.PRECONDITION_FAILED, e.getMessage()));
         }
         uploaded.incrementCountAndAddPrimaryKey(record.getValue("id"));
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Result lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         uploaded.addSelfToListIfAnyCount(lines);
         if(bytes > 0)
         {
            lines.add(new ProcessSummaryLine(Status.INFO, ActionSupport.bytes(bytes) + " uploaded"));
         }
         if(getTransformStep() instanceof UploadTransform transform)
         {
            transform.overwrites.addSelfToListIfAnyCount(lines);
         }
      }
   }



   /*******************************************************************************
    ** The configured upload limit (documentation helper for tests).
    *******************************************************************************/
   static long uploadLimit(String alias)
   {
      return (ObjectStorageRuntime.bucketConfig(alias).getMaxUploadBytes());
   }
}
