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


import java.util.List;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.index.StorageJobs;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.QScheduleMetaData;


/*******************************************************************************
 ** The nightly job: scan every indexed bucket, read the HEAD backlog, rescan
 ** references, snapshot usage, purge expired trash and raise the freshness
 ** alerts. Hidden (never in the nav); scheduled only when the config names a
 ** scheduler.
 *******************************************************************************/
public final class NightlyProcess
{
   public static final String NAME = "objectStorageNightly";

   private static final QLogger LOG = QLogger.getLogger(NightlyProcess.class);



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private NightlyProcess()
   {
   }



   /*******************************************************************************
    ** The process (empty when there is nothing to schedule).
    *******************************************************************************/
   public static List<QProcessMetaData> processes(ObjectStorageQBitConfig config)
   {
      QProcessMetaData process = new QProcessMetaData().withName(NAME).withLabel("Storage nightly job").withIcon(new QIcon("schedule")).withIsHidden(true)
         .withPermissionRules(ObjectStorageTables.permissionRules())
         .withStep(new QBackendStepMetaData().withName("run").withCode(new QCodeReference(NightlyStep.class)));
      if(config.getSchedulerName() != null)
      {
         process.withSchedule(new QScheduleMetaData().withSchedulerName(config.getSchedulerName()).withRepeatSeconds(config.getNightlyIntervalSeconds()).withInitialDelaySeconds(120));
      }
      EtlSupport.permission(NAME, com.kingsrook.qbits.objectstorage.permissions.ObjectStoragePermissions.ADMIN);
      return (List.of(process));
   }



   /*******************************************************************************
    ** The step.
    *******************************************************************************/
   public static class NightlyStep implements BackendStep
   {
      /*******************************************************************************
       ** Run the nightly work and record what happened.
       *******************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         List<String> log = StorageJobs.nightly();
         LOG.info("Storage nightly job finished", com.kingsrook.qqq.backend.core.logging.LogUtils.logPair("steps", log.size()));
         output.addValue("log", String.join("\n", log));
      }
   }
}
