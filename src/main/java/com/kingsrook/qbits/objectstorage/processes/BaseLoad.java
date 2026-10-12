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
import java.util.Map;
import com.kingsrook.qbits.objectstorage.api.StorageException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;


/*******************************************************************************
 ** The execute half of an action. Before each provider call it re-checks the
 ** per-bucket lock for the record (defense in depth: the review already filtered,
 ** but the state may have changed). A failure on one record is counted and listed
 ** with the error; it never stops the others. Every write lands in
 ** object_storage_operation through the service.
 *******************************************************************************/
public abstract class BaseLoad extends AbstractLoadStep implements ProcessSummaryProviderInterface
{
   protected final Map<Integer, Boolean> bucketAccess = new HashMap<>();

   protected final ProcessSummaryLine failed = EtlSupport.line(Status.ERROR, "record failed (see Operations for the error)", "records failed (see Operations for the errors)",
      "record failed (see Operations for the error)", "records failed (see Operations for the errors)");

   private final ArrayList<String> failures = new ArrayList<>();



   /*******************************************************************************
    ** The bucket row id a record belongs to (the record's bucketId by default).
    *******************************************************************************/
   protected Integer bucketIdOf(QRecord record)
   {
      return (record.getValueInteger("bucketId"));
   }



   /*******************************************************************************
    ** Do the work for one record.
    *******************************************************************************/
   protected abstract void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException;



   /*******************************************************************************
    ** Add this action's own result lines.
    *******************************************************************************/
   protected abstract void addLines(ArrayList<ProcessSummaryLineInterface> lines);



   /*******************************************************************************
    ** Re-check the bucket lock, run the work, count failures.
    *******************************************************************************/
   @Override
   public final void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      for(QRecord record : input.getRecords())
      {
         Integer bucketId = bucketIdOf(record);
         if(bucketId != null && !ActionSupport.canSeeBucket(bucketId, bucketAccess))
         {
            failed.incrementCountAndAddPrimaryKey(record.getValue("id"));
            failures.add("no access to the bucket");
            continue;
         }
         try
         {
            apply(record, input, output);
         }
         catch(StorageException e)
         {
            failed.incrementCountAndAddPrimaryKey(record.getValue("id"));
            if(failures.size() < 5)
            {
               failures.add(String.valueOf(record.getValueString("objectKey")) + ": " + e.getMessage());
            }
         }
         catch(RuntimeException e)
         {
            failed.incrementCountAndAddPrimaryKey(record.getValue("id"));
            if(failures.size() < 5)
            {
               failures.add(String.valueOf(record.getValueString("objectKey")) + ": " + e.getMessage());
            }
         }
      }
   }



   /*******************************************************************************
    ** Result lines: this action's, then the records that were left alone, then
    ** failures.
    *******************************************************************************/
   @Override
   public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
   {
      ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();
      addLines(lines);
      if(getTransformStep() instanceof BaseTransform transform)
      {
         for(ProcessSummaryLine line : transform.skipLines())
         {
            line.addSelfToListIfAnyCount(lines);
         }
         transform.overCap.addSelfToListIfAnyCount(lines);
         transform.noAccess.addSelfToListIfAnyCount(lines);
      }
      if(failed.getCount() != null && failed.getCount() > 0)
      {
         failed.setBulletsOfText(new ArrayList<>(failures));
         failed.addSelfToListIfAnyCount(lines);
      }
      return (lines);
   }
}
