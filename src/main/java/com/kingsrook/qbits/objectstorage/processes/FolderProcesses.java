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
import com.kingsrook.qbits.objectstorage.api.StorageKeys;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.permissions.ObjectStoragePermissions;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ExtractViaQueryStep;


/*******************************************************************************
 ** Folder record actions: "Move or rename folder" and "Delete folder contents".
 ** A folder is a prefix, so these act on the objects under the selected
 ** folders, with exactly the same Review lines, skip rules and Result as the
 ** object actions (referenced, protected and locked objects are skipped).
 *******************************************************************************/
public final class FolderProcesses
{
   public static final String RENAME_NAME = "objectStorageRenameFolder";
   public static final String DELETE_NAME = "objectStorageDeleteFolder";

   public static final String FIELD_NEW_PATH = "newPath";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private FolderProcesses()
   {
   }



   /*******************************************************************************
    ** The processes.
    *******************************************************************************/
   public static List<QProcessMetaData> processes()
   {
      return (List.of(
         EtlSupport.withInput(EtlSupport.etl(RENAME_NAME, "Move or rename folder", "drive_file_rename_outline", ObjectStorageTables.OBJECT, ObjectStorageTables.FOLDER, FolderObjectsExtractStep.class,
            FolderMoveTransform.class, CopyMoveProcesses.MoveLoad.class, "This is a preview of the objects under the folder that will be moved.", ObjectStoragePermissions.WRITE),
            "Move or rename folder", "Every object under the folder moves to the new folder path. A move is a copy followed by a delete, not one atomic step.",
            EtlSupport.stringField(FIELD_NEW_PATH, "New folder path (for example media/archive/2026/)", true),
            new QFieldMetaData(CopyMoveProcesses.FIELD_DEST_BUCKET, QFieldType.INTEGER).withLabel("Destination bucket (leave blank for the same bucket)").withPossibleValueSourceName(ObjectStorageTables.BUCKET),
            EtlSupport.booleanField(CopyMoveProcesses.FIELD_OVERWRITE, "Overwrite objects that already exist at the destination", false)),
         EtlSupport.etl(DELETE_NAME, "Delete folder contents", "folder_delete", ObjectStorageTables.OBJECT, ObjectStorageTables.FOLDER, FolderObjectsExtractStep.class,
            DeleteRestoreProcesses.DeleteTransform.class, DeleteRestoreProcesses.DeleteLoad.class, "This is a preview of the objects under the folder that will be deleted. Referenced, protected and locked objects are skipped.",
            ObjectStoragePermissions.DELETE)));
   }



   /*******************************************************************************
    ** Extracts the live objects under the selected folders (selected rows, the
    ** current filter, or all folders): the folder selection is read as the user
    ** (so per-bucket locks apply), then the object rows under those prefixes.
    *******************************************************************************/
   public static class FolderObjectsExtractStep extends ExtractViaQueryStep
   {
      /*******************************************************************************
       ** The folders the user selected.
       *******************************************************************************/
      public List<QRecord> selectedFolders(RunBackendStepInput input) throws QException
      {
         QQueryFilter folderFilter = super.getQueryFilter(input);
         return (new QueryAction().execute(new QueryInput(ObjectStorageTables.FOLDER).withFilter(folderFilter)).getRecords());
      }



      /*******************************************************************************
       ** The object filter: live objects under any selected folder (a folder inside
       ** another selected folder adds nothing).
       *******************************************************************************/
      @Override
      protected QQueryFilter getQueryFilter(RunBackendStepInput input) throws QException
      {
         List<QRecord> folders = selectedFolders(input);
         List<QRecord> top     = new ArrayList<>();
         for(QRecord folder : folders)
         {
            boolean nested = false;
            for(QRecord other : folders)
            {
               if(other != folder && other.getValueInteger("bucketId").equals(folder.getValueInteger("bucketId")) && folder.getValueString("path").startsWith(other.getValueString("path"))
                  && !folder.getValueString("path").equals(other.getValueString("path")))
               {
                  nested = true;
               }
            }
            if(!nested)
            {
               top.add(folder);
            }
         }
         if(top.isEmpty())
         {
            return (new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.IN, List.of(-1))));
         }
         QQueryFilter filter = new QQueryFilter().withBooleanOperator(QQueryFilter.BooleanOperator.OR);
         for(QRecord folder : top)
         {
            filter.withSubFilter(new QQueryFilter(new QFilterCriteria("bucketId", QCriteriaOperator.EQUALS, folder.getValueInteger("bucketId")),
               new QFilterCriteria("deleted", QCriteriaOperator.EQUALS, false), new QFilterCriteria("objectKey", QCriteriaOperator.STARTS_WITH, folder.getValueString("path"))));
         }
         return (filter);
      }
   }



   /*******************************************************************************
    ** Move or rename folder, review: the objects move under the new folder path.
    *******************************************************************************/
   public static class FolderMoveTransform extends CopyMoveProcesses.CopyMoveTransform
   {
      private List<QRecord> folders;
      private String        error;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      public FolderMoveTransform()
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



      /*******************************************************************************
       ** The object's new key: the new folder path plus what followed the old path.
       *******************************************************************************/
      @Override
      protected String destKeyFor(QRecord record, RunBackendStepInput input)
      {
         try
         {
            if(folders == null)
            {
               folders = new FolderObjectsExtractStep().selectedFolders(input);
            }
         }
         catch(QException e)
         {
            throw (new IllegalArgumentException("Could not read the selected folders"));
         }
         if(folders.size() != 1)
         {
            error = "Rename works on one folder at a time; " + folders.size() + " are selected.";
            throw (new IllegalArgumentException(error));
         }
         String oldPath = folders.get(0).getValueString("path");
         String newPath = StorageKeys.normalizePrefix(input.getValueString(FIELD_NEW_PATH));
         if(newPath.isEmpty())
         {
            error = "A new folder path is required.";
            throw (new IllegalArgumentException(error));
         }
         if(!newPath.endsWith("/"))
         {
            newPath = newPath + "/";
         }
         return (StorageKeys.normalize(newPath + record.getValueString("objectKey").substring(oldPath.length())));
      }



      /*******************************************************************************
       ** Review lines, with the input problem first.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         if(error != null)
         {
            lines.add(EtlSupport.text(Status.ERROR, error));
         }
         super.addLines(lines);
      }
   }



   /*******************************************************************************
    ** The ids of folders, for tests.
    *******************************************************************************/
   static List<Serializable> idsOf(List<QRecord> folders)
   {
      List<Serializable> ids = new ArrayList<>();
      folders.forEach(f -> ids.add(f.getValue("id")));
      return (ids);
   }
}
