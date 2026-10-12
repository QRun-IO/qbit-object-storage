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


import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.objectstorage.api.PresignGet;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
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
 ** "Create download link": a presigned GET for the selected objects. The Result
 ** shows each link once (the operation log keeps the key, the expiry and who
 ** asked, never the signature, because a presigned URL is a bearer token). The
 ** link lasts five minutes, or the bucket's maximum if that is shorter.
 *******************************************************************************/
public final class LinkProcesses
{
   public static final String DOWNLOAD_NAME = "objectStorageCreateDownloadLink";

   private static final int MAX_LINKS_PER_RUN = 25;



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private LinkProcesses()
   {
   }



   /*******************************************************************************
    ** The processes.
    *******************************************************************************/
   public static List<QProcessMetaData> processes()
   {
      return (List.of(EtlSupport.etl(DOWNLOAD_NAME, "Create download link", "link", ObjectStorageTables.OBJECT, DownloadTransform.class, DownloadLoad.class,
         "This is a preview of the presigned download links that will be created.", ObjectStoragePermissions.VIEW)));
   }



   /*******************************************************************************
    ** Create download link, review.
    *******************************************************************************/
   public static class DownloadTransform extends BaseTransform
   {
      private final ProcessSummaryLine will = EtlSupport.line(Status.OK, "download link will be created", "download links will be created", "download link was created", "download links were created");
      private final ProcessSummaryLine deleted = EtlSupport.line(Status.INFO, "object is deleted (skipped)", "objects are deleted (skipped)", "object was deleted (skipped)", "objects were deleted (skipped)");
      private final ProcessSummaryLine tooMany = EtlSupport.line(Status.WARNING, "object is skipped: at most " + MAX_LINKS_PER_RUN + " links are made per run", "objects are skipped: at most " + MAX_LINKS_PER_RUN + " links are made per run",
         "object was skipped: link limit", "objects were skipped: link limit");



      /*******************************************************************************
       ** Count.
       *******************************************************************************/
      @Override
      protected void handle(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         if(Boolean.TRUE.equals(record.getValueBoolean("deleted")))
         {
            deleted.incrementCountAndAddPrimaryKey(record.getValue("id"));
            return;
         }
         if(will.getCount() != null && will.getCount() >= MAX_LINKS_PER_RUN)
         {
            tooMany.incrementCountAndAddPrimaryKey(record.getValue("id"));
            return;
         }
         will.incrementCountAndAddPrimaryKey(record.getValue("id"));
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Review lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         will.addSelfToListIfAnyCount(lines);
         deleted.addSelfToListIfAnyCount(lines);
         tooMany.addSelfToListIfAnyCount(lines);
         lines.add(new ProcessSummaryLine(Status.INFO, "Each link works for about five minutes and is shown once on the next screen. Anyone who has a link can download that object until it expires."));
      }



      /*******************************************************************************
       ** The skips, as the Result screen shows them.
       *******************************************************************************/
      @Override
      protected List<ProcessSummaryLine> skipLines()
      {
         return (List.of(deleted, tooMany));
      }
   }



   /*******************************************************************************
    ** Create download link, execute.
    *******************************************************************************/
   public static class DownloadLoad extends BaseLoad
   {
      private final ArrayList<ProcessSummaryLineInterface> links = new ArrayList<>();



      /*******************************************************************************
       ** Create one link.
       *******************************************************************************/
      @Override
      protected void apply(QRecord record, RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         String              alias  = ActionSupport.aliasOf(record);
         StorageBucketConfig config = ActionSupport.config(alias);
         Duration            ttl    = Duration.ofSeconds(Math.min(300, config.getMaxPresignGetSeconds()));
         URL url = ActionSupport.storage(alias).presignGet(PresignGet.of(record.getValueString("objectKey")).ttl(ttl).downloadAs(record.getValueString("name")));
         ProcessSummaryLine line = new ProcessSummaryLine(Status.OK, "Download link for " + alias + "/" + record.getValueString("objectKey") + " (expires in " + ttl.toMinutes() + " minutes; shown once)");
         line.setBulletsOfText(new ArrayList<>(List.of(url.toString())));
         links.add(line);
         output.getRecords().add(record);
      }



      /*******************************************************************************
       ** Result lines.
       *******************************************************************************/
      @Override
      protected void addLines(ArrayList<ProcessSummaryLineInterface> lines)
      {
         lines.addAll(links);
      }
   }
}
