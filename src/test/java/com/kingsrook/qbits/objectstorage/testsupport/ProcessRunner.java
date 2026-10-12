package com.kingsrook.qbits.objectstorage.testsupport;


import java.io.Serializable;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
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
      RunProcessInput input = new RunProcessInput();
      input.setProcessName(process);
      input.addValue("recordsParam", "recordIds");
      input.addValue("recordIds", recordIds);
      if(extraValues != null)
      {
         extraValues.forEach(input::addValue);
      }
      input.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.BREAK);
      RunProcessOutput review = new RunProcessAction().execute(input);
      List<ProcessSummaryLineInterface> reviewLines = (List<ProcessSummaryLineInterface>) review.getValues().get(StreamedETLWithFrontendProcess.FIELD_VALIDATION_SUMMARY);

      RunProcessInput go = new RunProcessInput();
      go.setProcessName(process);
      go.setProcessUUID(review.getProcessUUID());
      go.setStartAfterStep(StreamedETLWithFrontendProcess.STEP_NAME_REVIEW);
      go.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
      RunProcessOutput result = new RunProcessAction().execute(go);
      List<ProcessSummaryLineInterface> resultLines = (List<ProcessSummaryLineInterface>) result.getValues().get(StreamedETLWithFrontendProcess.FIELD_PROCESS_SUMMARY);
      return (new Run(text(reviewLines, false), text(resultLines, true), result));
   }



   /*******************************************************************************
    ** Only the review (the dry run): nothing is changed.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public static List<String> review(String process, String recordIds) throws Exception
   {
      RunProcessInput input = new RunProcessInput();
      input.setProcessName(process);
      input.addValue("recordsParam", "recordIds");
      input.addValue("recordIds", recordIds);
      input.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.BREAK);
      RunProcessOutput review = new RunProcessAction().execute(input);
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
         return ((summary.getCount() == null || summary.getCount() == 0 ? "" : summary.getCount() + " ") + summary.getMessage());
      }).toList());
   }
}
