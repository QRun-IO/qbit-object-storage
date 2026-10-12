package com.kingsrook.qbits.objectstorage.processes;


import java.io.Serializable;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.testsupport.ProcessRunner;


/*******************************************************************************
 ** Runs an action the way the UI does after the input form: the form's values
 ** are process values, then Review, then Result.
 *******************************************************************************/
final class ProcessRunnerWithInputs
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ProcessRunnerWithInputs()
   {
   }



   /*******************************************************************************
    ** Review and Result with input values.
    *******************************************************************************/
   static ProcessRunner.Run run(String process, String recordIds, Map<String, Serializable> inputs) throws Exception
   {
      return (ProcessRunner.run(process, recordIds, inputs));
   }



   /*******************************************************************************
    ** Review only, with input values.
    *******************************************************************************/
   static List<String> review(String process, String recordIds, Map<String, Serializable> inputs) throws Exception
   {
      return (ProcessRunner.review(process, recordIds, inputs));
   }
}
