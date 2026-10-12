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

package com.kingsrook.qbits.objectstorage.testsupport;


import java.io.Serializable;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;


/*******************************************************************************
 ** Runs a standard streamed ETL the way the UI does: Review (the dry run), then
 ** Result. Returns the lines of both screens as text.
 *******************************************************************************/
public final class ProcessRunner
{
   /*******************************************************************************
    ** The two screens of one run.
    *******************************************************************************/
   public record Run(List<String> review, List<String> result, RunProcessOutput resultOutput)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ProcessRunner()
   {
   }



   /*******************************************************************************
    ** Run review, then execute, for record ids.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public static Run run(String process, String recordIds, Map<String, Serializable> extraValues) throws Exception
   {
      RunProcessOutput reviewOutput = toReview(process, recordIds, extraValues);
      List<ProcessSummaryLineInterface> reviewLines = (List<ProcessSummaryLineInterface>) reviewOutput.getValues().get(StreamedETLWithFrontendProcess.FIELD_VALIDATION_SUMMARY);

      RunProcessInput go = new RunProcessInput();
      go.setProcessName(process);
      go.setProcessUUID(reviewOutput.getProcessUUID());
      go.setStartAfterStep(StreamedETLWithFrontendProcess.STEP_NAME_REVIEW);
      go.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
      RunProcessOutput result = new RunProcessAction().execute(go);
      List<ProcessSummaryLineInterface> resultLines = (List<ProcessSummaryLineInterface>) result.getValues().get(StreamedETLWithFrontendProcess.FIELD_PROCESS_SUMMARY);
      return (new Run(text(reviewLines, false), text(resultLines, true), result));
   }



   /*******************************************************************************
    ** Start the process and stop at the Review screen. When the action has an input
    ** form, the first screen is that form: submit it with the values, as the UI does.
    *******************************************************************************/
   private static RunProcessOutput toReview(String process, String recordIds, Map<String, Serializable> extraValues) throws Exception
   {
      RunProcessInput input = new RunProcessInput();
      input.setProcessName(process);
      input.addValue("recordsParam", "recordIds");
      input.addValue("recordIds", recordIds);
      if(extraValues != null)
      {
         extraValues.forEach(input::addValue);
      }
      input.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.BREAK);
      RunProcessOutput output = new RunProcessAction().execute(input);
      if(QContext.getQInstance().getProcess(process).getFrontendStep("input") != null)
      {
         RunProcessInput submit = new RunProcessInput();
         submit.setProcessName(process);
         submit.setProcessUUID(output.getProcessUUID());
         submit.setStartAfterStep("input");
         if(extraValues != null)
         {
            extraValues.forEach(submit::addValue);
         }
         submit.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.BREAK);
         output = new RunProcessAction().execute(submit);
      }
      return (output);
   }



   /*******************************************************************************
    ** Only the review (the dry run): nothing is changed.
    *******************************************************************************/
   public static List<String> review(String process, String recordIds) throws Exception
   {
      return (review(process, recordIds, null));
   }



   /*******************************************************************************
    ** Only the review, with the input form's values.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public static List<String> review(String process, String recordIds, Map<String, Serializable> extraValues) throws Exception
   {
      RunProcessOutput review = toReview(process, recordIds, extraValues);
      return (text((List<ProcessSummaryLineInterface>) review.getValues().get(StreamedETLWithFrontendProcess.FIELD_VALIDATION_SUMMARY), false));
   }



   /*******************************************************************************
    ** Lines as shown (count and message).
    *******************************************************************************/
   private static List<String> text(List<ProcessSummaryLineInterface> lines, boolean past)
   {
      return (lines == null ? List.of() : lines.stream().map(line ->
      {
         ProcessSummaryLine summary = (ProcessSummaryLine) line;
         summary.pickMessage(past);
         String bullets = summary.getBulletsOfText() == null || summary.getBulletsOfText().isEmpty() ? "" : " [" + String.join(" | ", summary.getBulletsOfText()) + "]";
         return ((summary.getCount() == null || summary.getCount() == 0 ? "" : summary.getCount() + " ") + summary.getMessage() + bullets);
      }).toList());
   }
}
