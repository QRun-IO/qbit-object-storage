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
import java.util.List;
import com.kingsrook.qbits.objectstorage.api.CopyRequest;
import com.kingsrook.qbits.objectstorage.api.DeleteRequest;
import com.kingsrook.qbits.objectstorage.api.StorageCaller;
import com.kingsrook.qbits.objectstorage.api.StorageKeys;
import com.kingsrook.qbits.objectstorage.internal.Db;
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
 ** "Copy objects" and "Move or rename objects" (also the engine of the folder
 ** rename). A move is a copy followed by a delete, not an atomic operation, and
 ** the Review says so. Moves skip referenced, protected and locked objects;
 ** copies never overwrite unless asked.
 *******************************************************************************/
public final class CopyMoveProcesses
{
   public static final String COPY_NAME = "objectStorageCopyObjects";
   public static final String MOVE_NAME = "objectStorageMoveObjects";

   public static final String FIELD_DEST_BUCKET = "destinationBucket";
   public static final String FIELD_DEST_PREFIX = "destinationPrefix";
   public static final String FIELD_KEEP_PATH   = "keepPath";
   public static final String FIELD_OVERWRITE   = "overwrite";
   public static final String FIELD_NEW_NAME    = "newName";

   private static final long LARGE_OBJECT = 5L * 1024 * 1024 * 1024;



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private CopyMoveProcesses()
   {
   }



   /*******************************************************************************
    ** The processes.
    *******************************************************************************/
   public static List<QProcessMetaData> processes()
   {
      QFieldMetaData bucket = new QFieldMetaData(FIELD_DEST_BUCKET, QFieldType.INTEGER).withLabel("Destination bucket (leave blank for the same bucket)")
         .withPossibleValueSourceName(ObjectStorageTables.BUCKET);
      QFieldMetaData prefix = EtlSupport.stringField(FIELD_DEST_PREFIX, "Destination folder (for example media/2026/; leave blank for the bucket root)", false);
      QFieldMetaData keep   = EtlSupport.booleanField(FIELD_KEEP_PATH, "Keep each object's whole path under the destination (otherwise only its file name)", false);
      QFieldMetaData over   = EtlSupport.booleanField(FIELD_OVERWRITE, "Overwrite objects that already exist at the destination", false);
      QFieldMetaData name   = EtlSupport.stringField(FIELD_NEW_NAME, "New file name (only when moving one object)", false);

      return (List.of(
         EtlSupport.withInput(EtlSupport.etl(COPY_NAME, "Copy objects", "content_copy", ObjectStorageTables.OBJECT, CopyTransform.class, CopyLoad.class,
            "This is a preview of the objects that will be copied.", ObjectStoragePermissions.WRITE),
            "Copy objects", "Copies the selected objects to a bucket and folder. The originals stay where they are.", bucket, prefix, keep, over),
         EtlSupport.withInput(EtlSupport.etl(MOVE_NAME, "Move or rename objects", "drive_file_move", ObjectStorageTables.OBJECT, MoveTransform.class, MoveLoad.class,
            "This is a preview of the objects that will be moved.", ObjectStoragePermissions.WRITE),
            "Move or rename objects", "Moves the selected objects to a bucket and folder, or renames a single object. A move is a copy followed by a delete, not one atomic step.",
            bucket, prefix, keep, over, name)));
   }



   /*******************************************************************************
    ** Where an object goes: the destination folder plus the file name or the whole
    ** path; a single-object rename replaces the file name.
    *******************************************************************************/
   static String destinationKey(String sourceKey, String destPrefix, boolean keepPath, String newName)
   {
      String name   = sourceKey.substring(sourceKey.lastIndexOf('/') + 1);
      String folder = sourceKey.contains("/") ? sourceKey.substring(0, sourceKey.lastIndexOf('/') + 1) : "";
      String prefix = destPrefix == null ? "" : destPrefix.trim();
      if(!prefix.isEmpty() && !prefix.endsWith("/"))
      {
         prefix = prefix + "/";
      }
      boolean rename = StringUtils.hasContent(newName);
      if(rename && prefix.isEmpty())
      {
         // a rename with no destination folder stays in the object's own folder
         return (StorageKeys.normalize(folder + newName.trim()));
      }
      String leaf = rename ? newName.trim() : name;
      return (StorageKeys.normalize(prefix + (keepPath ? folder : "") + leaf));
   }



   /*******************************************************************************
    ** The alias of a bucket row id (the destination bucket field), or null.
    *******************************************************************************/
   static String aliasOfBucketId(Serializable bucketId)
   {
      if(bucketId == null || !StringUtils.hasContent(String.valueOf(bucketId)))
      {
         return (null);
      }
      try
      {
         QRecord bucket = Db.asSystem(() -> Db.first(ObjectStorageTables.BUCKET, "id", Integer.valueOf(String.valueOf(bucketId))));
         return (bucket == null ? null : bucket.getValueString("alias"));
      }
      catch(QException | NumberFormatException e)
      {
         return (null);
      }
   }



   /*******************************************************************************
    ** The shared review of copy and move.
    *******************************************************************************/
   public abstract static class CopyMoveTransform extends BaseTransform
   {
      protected final ProcessSummaryLine will;
      protected final ProcessSummaryLine overwrites = EtlSupport.line(Status.WARNING, "object will replace one that already exists at the destination", "objects will replace ones that already exist at the destination",
         "object replaced one that already existed at the destination", "objects replaced ones that already existed at the destination");
      protected final ProcessSummaryLine exists = EtlSupport.line(Status.WARNING, "object is skipped: the destination already exists (and overwrite is off)",
         "objects are skipped: the destinations already exist (and overwrite is off)", "object was skipped: the destination already existed", "objects were skipped: the destinations already existed");
      protected final ProcessSummaryLine same = EtlSupport.line(Status.INFO, "object is skipped: it is already at the destination", "objects are skipped: they are already at the destination",
         "object was skipped: it is already at the destination", "objects were skipped: they are already at the destination");
      protected final ProcessSummaryLine invalid = EtlSupport.line(Status.ERROR, "object is skipped: its destination key is not valid", "objects are skipped: their destination keys are not valid",
         "object was skipped: its destination key was not valid", "objects were skipped: their destination keys were not valid");
      protected final ProcessSummaryLine referenced = EtlSupport.line(Status.WARNING, "object is skipped: it is referenced", "objects are skipped: they are referenced",
         "object was skipped: it is referenced", "objects were skipped: they are referenced");
      protected final ProcessSummaryLine protectedLine = EtlSupport.line(Status.WARNING, "object is skipped: it (or its destination) is protected", "objects are skipped: they (or their destinations) are protected",
         "object was skipped: it (or its destination) is protected", "objects were skipped: they (or their destinations) are protected");
      protected final ProcessSummaryLine locked = EtlSupport.line(Status.WARNING, "object is skipped: it is under object lock", "objects are skipped: they are under object lock",
         "object was skipped: it is under object lock", "objects were skipped: they are under object lock");
      protected final ProcessSummaryLine backup = EtlSupport.line(Status.INFO, "object is skipped: it is in a backup bucket", "objects are skipped: they are in backup buckets",
         "object was skipped: it is in a backup bucket", "objects were skipped: they are in backup buckets");
      protected final ProcessSummaryLine deletedRows = EtlSupport.line(Status.INFO, "object is skipped: it is deleted", "objects are skipped: they are deleted", "object was skipped: it is deleted",
         "objects were skipped: they are deleted");
      protected final ProcessSummaryLine large = EtlSupport.line(Status.INFO, "object is over 5 GB and is copied in parts", "objects are over 5 GB and are copied in parts",
         "object was over 5 GB and was copied in parts", "objects were over 5 GB and were copied in parts");

      private long totalBytes = 0;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      protected CopyMoveTransform(ProcessSummaryLine will)
      {
         this.will = will;
      }



      /*******************************************************************************
       ** Whether this is a move (the source is deleted afterwards).
       *******************************************************************************/
      protected abstract boolean isMove();



      /*******************************************************************************
       ** Where the object goes, for this run's inputs (the folder rename overrides).
       *******************************************************************************/
      protected String destKeyFor(QRecord record, RunBackendStepInput input)
      {
         Integer total   = input.getValueInteger("recordCount");
         String  newName = isMove() && total != null && total == 1 ? input.getValueString(FIELD_NEW_NAME) : null;
         return (destinationKey(record.getValueString("objectKey"), input.getValueString(FIELD_DEST_PREFIX), Boolean.TRUE.equals(input.getValueBoolean(FIELD_KEEP_PATH)), newName));
      }



      /*******************************************************************************
       ** Classify one object.
       *******************************************************************************/
      @Override
      protected void handle(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         Serializable id = record.getValue("id");
         if(Boolean.TRUE.equals(record.getValueBoolean("deleted")))
         {
            deletedRows.incrementCountAndAddPrimaryKey(id);
            return;
         }
         ActionSupport.Classification c = ActionSupport.classify(record, isMove() ? ProtectionRules.Op.DELETE : ProtectionRules.Op.WRITE, isMove(), isMove());
         switch(c.skip())
         {
            case REFERENCED:
               referenced.incrementCountAndAddPrimaryKey(id);
               return;
            case PROTECTED:
               protectedLine.incrementCountAndAddPrimaryKey(id);
               return;
            case LOCKED:
               locked.incrementCountAndAddPrimaryKey(id);
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

         String srcAlias  = ActionSupport.aliasOf(record);
         String destAlias = aliasOfBucketId(input.getValue(FIELD_DEST_BUCKET));
         destAlias = destAlias == null ? srcAlias : destAlias;
         if(ActionSupport.config(destAlias) != null && !ActionSupport.canSeeBucket(com.kingsrook.qbits.objectstorage.internal.ConfigSync.bucketId(destAlias), bucketAccess))
         {
            noAccess.incrementCountAndAddPrimaryKey(id);
            return;
         }
         String destKey;
         try
         {
            destKey = destKeyFor(record, input);
         }
         catch(IllegalArgumentException e)
         {
            invalid.incrementCountAndAddPrimaryKey(id);
            return;
         }
         if(destAlias.equals(srcAlias) && destKey.equals(record.getValueString("objectKey")))
         {
            same.incrementCountAndAddPrimaryKey(id);
            return;
         }
         if(ActionSupport.config(destAlias) == null || ActionSupport.config(destAlias).getRole() == com.kingsrook.qbits.objectstorage.config.BucketRole.BACKUP
            || ProtectionRules.check(destAlias, destKey, ProtectionRules.Op.WRITE, StorageCaller.user()).blocked())
         {
            protectedLine.incrementCountAndAddPrimaryKey(id);
            return;
         }
         boolean destExists = false;
         try
         {
            destExists = ActionSupport.storage(destAlias).head(destKey).isPresent();
         }
         catch(RuntimeException e)
         {
            destExists = false;
         }
         if(destExists)
         {
            if(!Boolean.TRUE.equals(input.getValueBoolean(FIELD_OVERWRITE)))
            {
               exists.incrementCountAndAddPrimaryKey(id);
               return;
            }
            overwrites.incrementCountAndAddPrimaryKey(id);
         }
         Long size = record.getValueLong("size");
         totalBytes += size == null ? 0 : size;
         if(size != null && size > LARGE_OBJECT)
         {
            large.incrementCountAndAddPrimaryKey(id);
         }
         will.incrementCountAndAddPrimaryKey(id);
         record.setValue("_destAlias", destAlias);
         record.setValue("_destKey", destKey);
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
            lines.add(EtlSupport.text(Status.INFO, ActionSupport.bytes(totalBytes) + " in total"));
            if(isMove())
            {
               lines.add(EtlSupport.text(Status.INFO, "A move is a copy followed by a delete, not one atomic step. If the delete fails, the object exists in both places."));
            }
         }
         overwrites.addSelfToListIfAnyCount(lines);
         large.addSelfToListIfAnyCount(lines);
         for(ProcessSummaryLine line : skipLines())
         {
            line.addSelfToListIfAnyCount(lines);
         }
      }



      /*******************************************************************************
       ** The skips, as the Result screen shows them.
       *******************************************************************************/
      @Override
      protected List<ProcessSummaryLine> skipLines()
      {
         return (List.of(exists, same, invalid, referenced, protectedLine, locked, backup, deletedRows));
      }
   }



   /*******************************************************************************
    ** Copy, review.
    *******************************************************************************/
   public static class CopyTransform extends CopyMoveTransform
   {
      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      public CopyTransform()
      {
         super(EtlSupport.line(Status.OK, "object will be copied", "objects will be copied", "object was copied", "objects were copied"));
      }



      /*******************************************************************************
       ** Not a move.
       *******************************************************************************/
      @Override
      protected boolean isMove()
      {
         return (false);
      }
   }



   /*******************************************************************************
    ** Move, review.
    *******************************************************************************/
   public static class MoveTransform extends CopyMoveTransform
   {
      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      public MoveTransform()
      {
         super(EtlSupport.line(Status.OK, "object will be moved", "objects will be moved", "object was moved", "objects were moved"));
      }



      /*******************************************************************************
       ** A move.
       *******************************************************************************/
      @Override
      protected boolean isMove()
      {
         return (true);
      }
   }



   /*******************************************************************************
    ** Copy and move, execute.
    *******************************************************************************/
   public abstract static class CopyMoveLoad extends BaseLoad
   {
      private final ProcessSummaryLine done;
      private final ProcessSummaryLine copiedNotDeleted = EtlSupport.line(Status.ERROR, "object was copied but the original could not be deleted (it exists in both places)",
         "objects were copied but the originals could not be deleted (they exist in both places)", "object was copied but the original could not be deleted (it exists in both places)",
         "objects were copied but the originals could not be deleted (they exist in both places)");



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      protected CopyMoveLoad(ProcessSummaryLine done)
      {
         this.done = done;
      }



      /*******************************************************************************
       ** Whether this is a move.
       *******************************************************************************/
      protected abstract boolean isMove();



      /*******************************************************************************
       ** Copy one object, and for a move delete the original.
       *******************************************************************************/
      @Override
      protected void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         String srcAlias  = ActionSupport.aliasOf(record);
         String srcKey    = record.getValueString("objectKey");
         String destAlias = record.getValueString("_destAlias");
         String destKey   = record.getValueString("_destKey");
         if(isMove() && !ActionSupport.has(ObjectStoragePermissions.DELETE))
         {
            throw (new com.kingsrook.qbits.objectstorage.api.StorageException(com.kingsrook.qbits.objectstorage.api.StorageException.Reason.ACCESS_DENIED, "a move needs object_storage.delete too"));
         }
         ActionSupport.storage(srcAlias).copy(CopyRequest.of(srcKey, destKey).withDestinationBucket(destAlias.equals(srcAlias) ? null : destAlias));
         if(isMove())
         {
            try
            {
               ActionSupport.storage(srcAlias).delete(DeleteRequest.of(srcKey));
            }
            catch(RuntimeException e)
            {
               copiedNotDeleted.incrementCountAndAddPrimaryKey(record.getValue("id"));
               return;
            }
         }
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
         copiedNotDeleted.addSelfToListIfAnyCount(lines);
      }
   }



   /*******************************************************************************
    ** Copy, execute.
    *******************************************************************************/
   public static class CopyLoad extends CopyMoveLoad
   {
      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      public CopyLoad()
      {
         super(EtlSupport.line(Status.OK, "object will be copied", "objects will be copied", "object was copied", "objects were copied"));
      }



      /*******************************************************************************
       ** Not a move.
       *******************************************************************************/
      @Override
      protected boolean isMove()
      {
         return (false);
      }
   }



   /*******************************************************************************
    ** Move, execute.
    *******************************************************************************/
   public static class MoveLoad extends CopyMoveLoad
   {
      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      public MoveLoad()
      {
         super(EtlSupport.line(Status.OK, "object will be moved", "objects will be moved", "object was moved", "objects were moved"));
      }



      /*******************************************************************************
       ** A move.
       *******************************************************************************/
      @Override
      protected boolean isMove()
      {
         return (true);
      }
   }
}
