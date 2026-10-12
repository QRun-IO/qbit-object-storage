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


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.api.DeleteMode;
import com.kingsrook.qbits.objectstorage.api.DeleteRequest;
import com.kingsrook.qbits.objectstorage.api.DeleteResult;
import com.kingsrook.qbits.objectstorage.api.StorageBucket;
import com.kingsrook.qbits.objectstorage.config.ProviderType;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
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
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;


/*******************************************************************************
 ** "Delete objects" and "Restore objects". A delete is soft wherever the bucket
 ** allows it (a delete marker on a versioned bucket, a move to the private
 ** trash otherwise) and permanent only with object_storage.admin. Referenced
 ** objects, protected objects and objects under object lock are skipped, and the
 ** Review says why.
 *******************************************************************************/
public final class DeleteRestoreProcesses
{
   public static final String DELETE_NAME  = "objectStorageDeleteObjects";
   public static final String RESTORE_NAME = "objectStorageRestoreObjects";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private DeleteRestoreProcesses()
   {
   }



   /*******************************************************************************
    ** The processes.
    *******************************************************************************/
   public static List<QProcessMetaData> processes()
   {
      return (List.of(
         EtlSupport.etl(DELETE_NAME, "Delete objects", "delete", ObjectStorageTables.OBJECT, DeleteTransform.class, DeleteLoad.class,
            "This is a preview of the objects that will be deleted. Referenced, protected and locked objects are skipped.", ObjectStoragePermissions.DELETE),
         EtlSupport.etl(RESTORE_NAME, "Restore objects", "restore_from_trash", ObjectStorageTables.OBJECT, RestoreTransform.class, RestoreLoad.class,
            "This is a preview of the deleted objects that will be restored.", ObjectStoragePermissions.WRITE)));
   }



   /*******************************************************************************
    ** Delete, review.
    *******************************************************************************/
   public static class DeleteTransform extends BaseTransform
   {
      private final ProcessSummaryLine marker = EtlSupport.line(Status.OK, "object will be deleted (a delete marker is added; Restore undoes it)",
         "objects will be deleted (a delete marker is added; Restore undoes it)", "object was deleted (a delete marker was added; Restore undoes it)",
         "objects were deleted (delete markers were added; Restore undoes it)");
      private final ProcessSummaryLine trash = EtlSupport.line(Status.OK, "object will be moved to the trash (Restore undoes it)", "objects will be moved to the trash (Restore undoes it)",
         "object was moved to the trash (Restore undoes it)", "objects were moved to the trash (Restore undoes it)");
      private final ProcessSummaryLine permanent = EtlSupport.line(Status.WARNING, "object will be deleted permanently (this cannot be undone)",
         "objects will be deleted permanently (this cannot be undone)", "object was deleted permanently", "objects were deleted permanently");
      private final ProcessSummaryLine needsAdmin = EtlSupport.line(Status.WARNING, "object is skipped: this bucket has no versioning or trash, so the delete would be permanent and needs object_storage.admin",
         "objects are skipped: these buckets have no versioning or trash, so the delete would be permanent and needs object_storage.admin",
         "object was skipped: a permanent delete needs object_storage.admin", "objects were skipped: a permanent delete needs object_storage.admin");
      private final ProcessSummaryLine referenced = EtlSupport.line(Status.WARNING, "object is skipped: it is referenced", "objects are skipped: they are referenced",
         "object was skipped: it is referenced", "objects were skipped: they are referenced");
      private final ProcessSummaryLine protectedLine = EtlSupport.line(Status.WARNING, "object is skipped: it is protected", "objects are skipped: they are protected",
         "object was skipped: it is protected", "objects were skipped: they are protected");
      private final ProcessSummaryLine locked = EtlSupport.line(Status.WARNING, "object is skipped: it is under object lock", "objects are skipped: they are under object lock",
         "object was skipped: it is under object lock", "objects were skipped: they are under object lock");
      private final ProcessSummaryLine backup = EtlSupport.line(Status.INFO, "object is skipped: it is in a backup bucket", "objects are skipped: they are in backup buckets",
         "object was skipped: it is in a backup bucket", "objects were skipped: they are in backup buckets");
      private final ProcessSummaryLine already = EtlSupport.line(Status.INFO, "object is already deleted (no change)", "objects are already deleted (no change)",
         "object was already deleted (no change)", "objects were already deleted (no change)");

      private final Map<String, DeleteMode> modes = new HashMap<>();
      private boolean wasabi = false;



      /*******************************************************************************
       ** Classify one object.
       *******************************************************************************/
      @Override
      protected void handle(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         java.io.Serializable id = record.getValue("id");
         if(Boolean.TRUE.equals(record.getValueBoolean("deleted")))
         {
            already.incrementCountAndAddPrimaryKey(id);
            return;
         }
         ActionSupport.Classification c = ActionSupport.classify(record, ProtectionRules.Op.DELETE, true, true);
         switch(c.skip())
         {
            case REFERENCED -> skip(referenced, id, record, c);
            case PROTECTED -> skip(protectedLine, id, record, c);
            case LOCKED -> skip(locked, id, record, c);
            case BACKUP_BUCKET -> backup.incrementCountAndAddPrimaryKey(id);
            case NO_BUCKET_ACCESS -> noAccess.incrementCountAndAddPrimaryKey(id);
            default ->
            {
               String alias = ActionSupport.aliasOf(record);
               DeleteMode mode = modes.computeIfAbsent(alias, a -> ActionSupport.storage(a).plannedDeleteMode());
               StorageBucketConfig config = ActionSupport.config(alias);
               wasabi = wasabi || ObjectStorageRuntime.getConfig().findConnection(config.getConnection()).getProviderType() == ProviderType.WASABI;
               if(mode == DeleteMode.PERMANENT && !ActionSupport.isAdmin())
               {
                  needsAdmin.incrementCountAndAddPrimaryKey(id);
                  return;
               }
               (mode == DeleteMode.SOFT_DELETE_MARKER ? marker : mode == DeleteMode.SOFT_TRASH ? trash : permanent).incrementCountAndAddPrimaryKey(id);
               record.setValue("_mode", mode.name());
               output.getRecords().add(record);
            }
         }
      }



      /*******************************************************************************
       ** Count a skip and keep the first few reasons as bullets.
       *******************************************************************************/
      private void skip(ProcessSummaryLine line, java.io.Serializable id, QRecord record, ActionSupport.Classification c)
      {
         line.incrementCountAndAddPrimaryKey(id);
         if(line.getBulletsOfText() == null)
         {
            line.setBulletsOfText(new ArrayList<>());
         }
         if(line.getBulletsOfText().size() < 5)
         {
            line.getBulletsOfText().add(record.getValueString("objectKey") + ": " + c.detail());
         }
      }



      /*******************************************************************************
       ** The lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         marker.addSelfToListIfAnyCount(lines);
         trash.addSelfToListIfAnyCount(lines);
         permanent.addSelfToListIfAnyCount(lines);
         needsAdmin.addSelfToListIfAnyCount(lines);
         referenced.addSelfToListIfAnyCount(lines);
         protectedLine.addSelfToListIfAnyCount(lines);
         locked.addSelfToListIfAnyCount(lines);
         backup.addSelfToListIfAnyCount(lines);
         already.addSelfToListIfAnyCount(lines);
         if(wasabi)
         {
            lines.add(EtlSupport.text(Status.INFO, "Wasabi bills deleted objects for their minimum storage duration, so deleting does not reduce the bill at once."));
         }
      }



      /*******************************************************************************
       ** The skips, as the Result screen shows them.
       *******************************************************************************/
      @Override
      protected List<ProcessSummaryLine> skipLines()
      {
         return (List.of(needsAdmin, referenced, protectedLine, locked, backup, already));
      }
   }



   /*******************************************************************************
    ** Delete, execute.
    *******************************************************************************/
   public static class DeleteLoad extends BaseLoad
   {
      private final ProcessSummaryLine marker = EtlSupport.line(Status.OK, "object will be deleted (a delete marker is added; Restore undoes it)",
         "objects will be deleted (a delete marker is added; Restore undoes it)", "object was deleted (a delete marker was added; Restore undoes it)",
         "objects were deleted (delete markers were added; Restore undoes it)");
      private final ProcessSummaryLine trash = EtlSupport.line(Status.OK, "object will be moved to the trash", "objects will be moved to the trash", "object was moved to the trash (Restore undoes it)",
         "objects were moved to the trash (Restore undoes it)");
      private final ProcessSummaryLine permanent = EtlSupport.line(Status.WARNING, "object will be deleted permanently", "objects will be deleted permanently", "object was deleted permanently",
         "objects were deleted permanently");
      private final ProcessSummaryLine lockedNow = EtlSupport.line(Status.WARNING, "object was skipped: object lock", "objects were skipped: object lock", "object was skipped: object lock",
         "objects were skipped: object lock");
      private final ProcessSummaryLine gone = EtlSupport.line(Status.INFO, "object was already gone", "objects were already gone", "object was already gone", "objects were already gone");



      /*******************************************************************************
       ** Delete one object through the service (which logs it, updates the index and
       ** tells the listeners).
       *******************************************************************************/
      @Override
      protected void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         String alias = ActionSupport.aliasOf(record);
         if("PERMANENT".equals(record.getValueString("_mode")) && !ActionSupport.isAdmin())
         {
            throw (new com.kingsrook.qbits.objectstorage.api.StorageException(com.kingsrook.qbits.objectstorage.api.StorageException.Reason.ACCESS_DENIED, "a permanent delete needs object_storage.admin"));
         }
         DeleteResult result = ActionSupport.storage(alias).delete(DeleteRequest.of(record.getValueString("objectKey")));
         switch(result.getMode())
         {
            case SOFT_DELETE_MARKER -> marker.incrementCountAndAddPrimaryKey(record.getValue("id"));
            case SOFT_TRASH -> trash.incrementCountAndAddPrimaryKey(record.getValue("id"));
            case PERMANENT -> permanent.incrementCountAndAddPrimaryKey(record.getValue("id"));
            case SKIPPED_OBJECT_LOCK -> lockedNow.incrementCountAndAddPrimaryKey(record.getValue("id"));
            default -> gone.incrementCountAndAddPrimaryKey(record.getValue("id"));
         }
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Result lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         marker.addSelfToListIfAnyCount(lines);
         trash.addSelfToListIfAnyCount(lines);
         permanent.addSelfToListIfAnyCount(lines);
         lockedNow.addSelfToListIfAnyCount(lines);
         gone.addSelfToListIfAnyCount(lines);
      }
   }



   /*******************************************************************************
    ** Restore, review.
    *******************************************************************************/
   public static class RestoreTransform extends BaseTransform
   {
      private final ProcessSummaryLine unmark = EtlSupport.line(Status.OK, "delete marker will be removed", "delete markers will be removed", "delete marker was removed", "delete markers were removed");
      private final ProcessSummaryLine fromTrash = EtlSupport.line(Status.OK, "object will be copied back from the trash", "objects will be copied back from the trash",
         "object was copied back from the trash", "objects were copied back from the trash");
      private final ProcessSummaryLine notDeleted = EtlSupport.line(Status.INFO, "object is not deleted (no change)", "objects are not deleted (no change)",
         "object was not deleted (no change)", "objects were not deleted (no change)");
      private final ProcessSummaryLine cannot = EtlSupport.line(Status.WARNING, "object cannot be restored: its bucket has no versioning or trash", "objects cannot be restored: their buckets have no versioning or trash",
         "object could not be restored: its bucket has no versioning or trash", "objects could not be restored: their buckets have no versioning or trash");
      private final ProcessSummaryLine protectedLine = EtlSupport.line(Status.WARNING, "object is skipped: it is protected", "objects are skipped: they are protected",
         "object was skipped: it is protected", "objects were skipped: they are protected");
      private final Map<String, DeleteMode> modes = new HashMap<>();



      /*******************************************************************************
       ** Classify one object.
       *******************************************************************************/
      @Override
      protected void handle(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         java.io.Serializable id = record.getValue("id");
         if(!Boolean.TRUE.equals(record.getValueBoolean("deleted")))
         {
            notDeleted.incrementCountAndAddPrimaryKey(id);
            return;
         }
         String alias = ActionSupport.aliasOf(record);
         if(ProtectionRules.check(alias, record.getValueString("objectKey"), ProtectionRules.Op.WRITE, com.kingsrook.qbits.objectstorage.api.StorageCaller.user()).blocked())
         {
            protectedLine.incrementCountAndAddPrimaryKey(id);
            return;
         }
         DeleteMode mode = modes.computeIfAbsent(alias, a -> ActionSupport.storage(a).plannedDeleteMode());
         if(mode == DeleteMode.SOFT_DELETE_MARKER)
         {
            unmark.incrementCountAndAddPrimaryKey(id);
         }
         else if(mode == DeleteMode.SOFT_TRASH)
         {
            fromTrash.incrementCountAndAddPrimaryKey(id);
         }
         else
         {
            cannot.incrementCountAndAddPrimaryKey(id);
            return;
         }
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** The lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         unmark.addSelfToListIfAnyCount(lines);
         fromTrash.addSelfToListIfAnyCount(lines);
         notDeleted.addSelfToListIfAnyCount(lines);
         cannot.addSelfToListIfAnyCount(lines);
         protectedLine.addSelfToListIfAnyCount(lines);
      }



      /*******************************************************************************
       ** The skips, as the Result screen shows them.
       *******************************************************************************/
      @Override
      protected List<ProcessSummaryLine> skipLines()
      {
         return (List.of(notDeleted, cannot, protectedLine));
      }
   }



   /*******************************************************************************
    ** Restore, execute.
    *******************************************************************************/
   public static class RestoreLoad extends BaseLoad
   {
      private final ProcessSummaryLine restored = EtlSupport.line(Status.OK, "object will be restored", "objects will be restored", "object was restored", "objects were restored");
      private final ProcessSummaryLine nothing = EtlSupport.line(Status.INFO, "object had nothing to restore (it may have been purged)", "objects had nothing to restore (they may have been purged)",
         "object had nothing to restore (it may have been purged)", "objects had nothing to restore (they may have been purged)");



      /*******************************************************************************
       ** Restore one object.
       *******************************************************************************/
      @Override
      protected void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         StorageBucket storage = ActionSupport.storage(ActionSupport.aliasOf(record));
         if(storage.restore(record.getValueString("objectKey")))
         {
            restored.incrementCountAndAddPrimaryKey(record.getValue("id"));
            output.getRecords().add(record);
         }
         else
         {
            nothing.incrementCountAndAddPrimaryKey(record.getValue("id"));
         }
      }



      /*******************************************************************************
       ** Result lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         restored.addSelfToListIfAnyCount(lines);
         nothing.addSelfToListIfAnyCount(lines);
      }
   }
}
