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


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.internal.CapabilityProbe;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.ConnectionRuntime;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
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
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;


/*******************************************************************************
 ** "Test connection" and "Test connection with write tests": record actions on
 ** object_storage_connection. Review lists what will be probed; Result writes
 ** the capability rows, sets lastTestedAt and lastTestStatus, and shows one
 ** summary line per bucket ("8 supported, 3 unsupported, 1 not permitted"),
 ** plus clock skew and whether the presign host answers.
 *******************************************************************************/
public final class TestConnectionProcess
{
   public static final String READ_NAME  = "objectStorageTestConnection";
   public static final String WRITE_NAME = "objectStorageTestConnectionWithWriteTests";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private TestConnectionProcess()
   {
   }



   /*******************************************************************************
    ** The two processes.
    *******************************************************************************/
   public static List<QProcessMetaData> processes()
   {
      return (List.of(
         EtlSupport.etl(READ_NAME, "Test connection", "network_check", ObjectStorageTables.CONNECTION, ReadTransform.class, ReadLoad.class,
            "This is a preview of what will be probed. Only read-only calls are made.", ObjectStoragePermissions.VIEW),
         EtlSupport.etl(WRITE_NAME, "Test connection with write tests", "rule", ObjectStorageTables.CONNECTION, WriteTransform.class, WriteLoad.class,
            "This is a preview of what will be probed. Write tests put, copy and delete a probe object under .qqq-probe/ in each unprotected bucket.", ObjectStoragePermissions.ADMIN)));
   }



   /*******************************************************************************
    ** Review: what will be probed.
    *******************************************************************************/
   public static class ReadTransform extends AbstractTransformStep implements ProcessSummaryProviderInterface
   {
      private final ProcessSummaryLine connections = EtlSupport.line(Status.OK, "connection will be tested", "connections will be tested", "connection was tested", "connections were tested");
      private final ProcessSummaryLine readBuckets = EtlSupport.line(Status.INFO, "bucket will get read-only probes", "buckets will get read-only probes", "bucket got read-only probes", "buckets got read-only probes");
      private final ProcessSummaryLine writeBuckets = EtlSupport.line(Status.WARNING, "bucket will get write tests", "buckets will get write tests", "bucket got write tests", "buckets got write tests");
      private final ProcessSummaryLine skipped = EtlSupport.line(Status.INFO, "bucket will not get write tests (protected, or object lock on a backup bucket)",
         "buckets will not get write tests (protected, or object lock on a backup bucket)", "bucket did not get write tests", "buckets did not get write tests");



      /*******************************************************************************
       ** Whether this variant includes write tests.
       *******************************************************************************/
      protected boolean includeWrite()
      {
         return (false);
      }



      /*******************************************************************************
       ** Count what will be probed.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         for(QRecord record : input.getRecords())
         {
            String name = record.getValueString("name");
            if(ObjectStorageRuntime.getConfig().findConnection(name) == null)
            {
               continue;
            }
            connections.incrementCountAndAddPrimaryKey(record.getValue("id"));
            ConnectionRuntime runtime = ObjectStorageRuntime.connection(name);
            for(StorageBucketConfig bucket : ObjectStorageRuntime.getConfig().getBuckets())
            {
               if(name.equals(bucket.getConnection()))
               {
                  readBuckets.incrementCount();
                  if(includeWrite())
                  {
                     if(CapabilityProbe.writeProbeSkipReason(bucket, runtime) == null)
                     {
                        writeBuckets.incrementCount();
                     }
                     else
                     {
                        skipped.incrementCount();
                     }
                  }
               }
            }
            output.getRecords().add(record);
         }
      }



      /*******************************************************************************
       ** Review lines.
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();
         connections.addSelfToListIfAnyCount(lines);
         readBuckets.addSelfToListIfAnyCount(lines);
         writeBuckets.addSelfToListIfAnyCount(lines);
         skipped.addSelfToListIfAnyCount(lines);
         return (lines);
      }
   }



   /*******************************************************************************
    ** Review for the variant with write tests.
    *******************************************************************************/
   public static class WriteTransform extends ReadTransform
   {
      /*******************************************************************************
       ** Includes write tests.
       *******************************************************************************/
      @Override
      protected boolean includeWrite()
      {
         return (true);
      }
   }



   /*******************************************************************************
    ** Execute: run the probe, record the results.
    *******************************************************************************/
   public static class ReadLoad extends AbstractLoadStep implements ProcessSummaryProviderInterface
   {
      private final ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();



      /*******************************************************************************
       ** Whether this variant includes write tests.
       *******************************************************************************/
      protected boolean includeWrite()
      {
         return (false);
      }



      /*******************************************************************************
       ** Probe each connection and update its row.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         for(QRecord record : input.getRecords())
         {
            String name = record.getValueString("name");
            if(ObjectStorageRuntime.getConfig().findConnection(name) == null)
            {
               continue;
            }
            CapabilityProbe.Report report = CapabilityProbe.probeConnection(name, includeWrite());
            boolean ok = report.getProblems().isEmpty();
            for(String alias : report.getByBucket().keySet())
            {
               StringBuilder text = new StringBuilder(alias + ": " + report.summaryFor(alias));
               for(String note : report.getNotes().getOrDefault(alias, List.of()))
               {
                  text.append(" (").append(note).append(")");
               }
               lines.add(EtlSupport.text(Status.INFO, text.toString()));
            }
            for(String problem : report.getProblems())
            {
               lines.add(EtlSupport.text(Status.ERROR, problem));
            }
            if(report.getClockSkewSeconds() != null)
            {
               boolean skewed = report.getClockSkewSeconds() > 900;
               ok = ok && !skewed;
               lines.add(new ProcessSummaryLine(skewed ? Status.ERROR : Status.INFO, "Clock skew against the store: " + report.getClockSkewSeconds() + " seconds"
                  + (skewed ? " (SigV4 fails beyond 15 minutes)" : "")));
            }
            if(report.getPresignHostReachable() != null)
            {
               lines.add(new ProcessSummaryLine(report.getPresignHostReachable() ? Status.INFO : Status.ERROR, report.getPresignHostReachable()
                  ? "The presign host answers" : "The presign host could not be reached"));
            }

            String status = ok ? "OK" : "PROBLEMS";
            String summary = String.join("; ", report.getByBucket().keySet().stream().map(a -> a + ": " + report.summaryFor(a)).toList());
            Db.update(ObjectStorageTables.CONNECTION, List.of(new QRecord().withValue("id", record.getValue("id"))
               .withValue("lastTestedAt", Instant.now()).withValue("lastTestStatus", status).withValue("lastTestSummary", summary)
               .withValue("clockSkewSeconds", report.getClockSkewSeconds() == null ? null : report.getClockSkewSeconds().intValue())));
            if(!ok)
            {
               ObjectStorageRuntime.alertSink().raise("storage.connection.failed", "connection:" + name, "error", "Connection " + name + " failed its test: " + String.join("; ", report.getProblems()), java.util.Map.of());
            }
            else
            {
               ObjectStorageRuntime.alertSink().resolve("storage.connection.failed", "connection:" + name);
            }
            ConfigSync.ensureSynced();
            output.getRecords().add(record);
         }
      }



      /*******************************************************************************
       ** Result lines.
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         ArrayList<ProcessSummaryLineInterface> result = new ArrayList<>(lines);
         if(getTransformStep() instanceof ReadTransform transform)
         {
            transform.connections.addSelfToListIfAnyCount(result);
         }
         return (result);
      }
   }



   /*******************************************************************************
    ** Execute for the variant with write tests.
    *******************************************************************************/
   public static class WriteLoad extends ReadLoad
   {
      /*******************************************************************************
       ** Includes write tests.
       *******************************************************************************/
      @Override
      protected boolean includeWrite()
      {
         return (true);
      }
   }
}
