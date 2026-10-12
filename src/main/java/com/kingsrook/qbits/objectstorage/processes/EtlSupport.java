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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QComponentType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendComponentMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractExtractStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ExtractViaQueryStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;


/*******************************************************************************
 ** Builds the qbit's processes as standard streamed ETLs: record input from the
 ** selected rows, the current filter or all rows; a transform step that produces
 ** the Review summary lines; a load step that does the work; and the standard
 ** Result. Processes are table or record actions, never in the nav.
 *******************************************************************************/
public final class EtlSupport
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private EtlSupport()
   {
   }



   /*******************************************************************************
    ** A standard streamed ETL process over a table, with full validation (the
    ** Review step is the dry run).
    *******************************************************************************/
   public static QProcessMetaData etl(String name, String label, String icon, String table, Class<? extends AbstractTransformStep> transform,
      Class<? extends AbstractLoadStep> load, String preview, String permission)
   {
      return (etl(name, label, icon, table, table, ExtractViaQueryStep.class, transform, load, preview, permission));
   }



   /*******************************************************************************
    ** A streamed ETL whose records come from a custom extract step (for example the
    ** objects under selected folders), shown as an action on another table.
    *******************************************************************************/
   public static QProcessMetaData etl(String name, String label, String icon, String recordTable, String actionTable, Class<? extends AbstractExtractStep> extract,
      Class<? extends AbstractTransformStep> transform, Class<? extends AbstractLoadStep> load, String preview, String permission)
   {
      Map<String, Serializable> values = new HashMap<>();
      values.put(StreamedETLWithFrontendProcess.FIELD_SOURCE_TABLE, recordTable);
      values.put(StreamedETLWithFrontendProcess.FIELD_DESTINATION_TABLE, recordTable);
      values.put(StreamedETLWithFrontendProcess.FIELD_SUPPORTS_FULL_VALIDATION, true);
      values.put(StreamedETLWithFrontendProcess.FIELD_DO_FULL_VALIDATION, true);
      values.put(StreamedETLWithFrontendProcess.FIELD_PREVIEW_MESSAGE, preview);
      QProcessMetaData process = StreamedETLWithFrontendProcess.defineProcessMetaData(extract, transform, load, values);
      process.setName(name);
      String table = actionTable;
      process.setLabel(label);
      process.setIcon(new QIcon(icon));
      process.setTableName(table);
      process.setPermissionRules(ObjectStorageTables.permissionRules());
      ObjectStorageRuntime.processPermissions().put(name, permission);
      return (process);
   }



   /*******************************************************************************
    ** Record the permission a process needs.
    *******************************************************************************/
   public static void permission(String processName, String permission)
   {
      ObjectStorageRuntime.processPermissions().put(processName, permission);
   }



   /*******************************************************************************
    ** Put an input form (help text plus fields) in front of an action, the standard
    ** way a QQQ process asks for values before its Review.
    *******************************************************************************/
   public static QProcessMetaData withInput(QProcessMetaData process, String label, String help, QFieldMetaData... fields)
   {
      QFrontendStepMetaData input = new QFrontendStepMetaData().withName("input").withLabel(label).withFormFields(List.of(fields))
         .withComponent(new QFrontendComponentMetaData().withType(QComponentType.HELP_TEXT).withValue("text", help))
         .withComponent(new QFrontendComponentMetaData().withType(QComponentType.EDIT_FORM));
      process.withStep(0, input);
      return (process);
   }



   /*******************************************************************************
    ** A string field for an input form.
    *******************************************************************************/
   public static QFieldMetaData stringField(String name, String label, boolean required)
   {
      return (new QFieldMetaData(name, QFieldType.STRING).withLabel(label).withIsRequired(required));
   }



   /*******************************************************************************
    ** A boolean field for an input form.
    *******************************************************************************/
   public static QFieldMetaData booleanField(String name, String label, boolean defaultValue)
   {
      return (new QFieldMetaData(name, QFieldType.BOOLEAN).withLabel(label).withDefaultValue(defaultValue));
   }



   /*******************************************************************************
    ** A summary line with future (Review) and past (Result) wording.
    *******************************************************************************/
   public static ProcessSummaryLine line(Status status, String singularFuture, String pluralFuture, String singularPast, String pluralPast)
   {
      ProcessSummaryLine line = new ProcessSummaryLine(status, 0, null);
      line.setSingularFutureMessage(singularFuture);
      line.setPluralFutureMessage(pluralFuture);
      line.setSingularPastMessage(singularPast);
      line.setPluralPastMessage(pluralPast);
      return (line);
   }
}
