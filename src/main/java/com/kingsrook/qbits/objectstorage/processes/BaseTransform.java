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
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;


/*******************************************************************************
 ** The Review (dry run) half of an action. It is read-only: it classifies each
 ** record, produces the summary lines, and passes the records that will be acted
 ** on to the load step. It also applies the bulk cap (10,000 records per run
 ** unless the user has object_storage.admin) and the per-bucket record lock.
 *******************************************************************************/
public abstract class BaseTransform extends AbstractTransformStep implements ProcessSummaryProviderInterface
{
   protected final Map<Integer, Boolean> bucketAccess = new HashMap<>();

   protected final ProcessSummaryLine overCap = EtlSupport.line(Status.ERROR, "record is over the bulk cap and needs object_storage.admin (skipped)",
      "records are over the bulk cap and need object_storage.admin (skipped)", "record was over the bulk cap (skipped)", "records were over the bulk cap (skipped)");
   protected final ProcessSummaryLine noAccess = EtlSupport.line(Status.ERROR, "record is in a bucket you may not use (skipped)", "records are in buckets you may not use (skipped)",
      "record was in a bucket you may not use (skipped)", "records were in buckets you may not use (skipped)");

   private int processed = 0;



   /*******************************************************************************
    ** The bucket row id a record belongs to (the record's bucketId by default).
    *******************************************************************************/
   protected Integer bucketIdOf(QRecord record)
   {
      return (record.getValueInteger("bucketId"));
   }



   /*******************************************************************************
    ** Handle one record: classify it, count it, and add it to the output when it
    ** will be acted on.
    *******************************************************************************/
   protected abstract void handle(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException;



   /*******************************************************************************
    ** Add this action's own summary lines.
    *******************************************************************************/
   protected abstract void addLines(ArrayList<ProcessSummaryLineInterface> lines);



   /*******************************************************************************
    ** The lines about records that will be left alone, as the Result screen shows
    ** them (the load step appends these).
    *******************************************************************************/
   protected List<ProcessSummaryLine> skipLines()
   {
      return (List.of());
   }



   /*******************************************************************************
    ** Apply the cap and the bucket lock, then hand each record on.
    *******************************************************************************/
   @Override
   public final void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      for(QRecord record : input.getRecords())
      {
         processed++;
         if(processed > ObjectStorageRuntime.getConfig().getBulkCap() && !ActionSupport.isAdmin())
         {
            overCap.incrementCountAndAddPrimaryKey(record.getValue("id"));
            continue;
         }
         Integer bucketId = bucketIdOf(record);
         if(bucketId != null && !ActionSupport.canSeeBucket(bucketId, bucketAccess))
         {
            noAccess.incrementCountAndAddPrimaryKey(record.getValue("id"));
            continue;
         }
         handle(record, input, output);
      }
   }



   /*******************************************************************************
    ** Review lines.
    *******************************************************************************/
   @Override
   public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
   {
      ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();
      addLines(lines);
      overCap.addSelfToListIfAnyCount(lines);
      noAccess.addSelfToListIfAnyCount(lines);
      return (lines);
   }
}
