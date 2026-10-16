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

package com.kingsrook.qbits.objectstorage.app;


import java.time.Instant;
import java.util.List;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.RawHTML;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.WidgetType;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppSection;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** The Admin -> Storage app: a home widget (the single owner of storage data)
 ** and the nav of the main tables. Sub-tables (capabilities, folders, versions,
 ** references, usage) appear as relations and child lists on their parents, not
 ** in the nav. Processes are never added to the nav.
 *******************************************************************************/
public final class StorageWidgets
{
   public static final String HOME_WIDGET = "objectStorageHome";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private StorageWidgets()
   {
   }



   /*******************************************************************************
    ** The widgets.
    *******************************************************************************/
   public static List<QWidgetMetaData> widgets()
   {
      return (List.of(new QWidgetMetaData().withName(HOME_WIDGET).withLabel("Overview").withType(WidgetType.HTML.getType())
         .withCodeReference(new QCodeReference(HomeRenderer.class)).withGridColumns(12).withIsCard(false).withPermissionRules(ObjectStorageTables.permissionRules())));
   }



   /*******************************************************************************
    ** The app. Nav entries are the home and the main tables.
    *******************************************************************************/
   public static QAppMetaData app(ObjectStorageQBitConfig config, List<QTableMetaData> tables)
   {
      QAppMetaData app = new QAppMetaData()
         .withName(config.getAppName())
         .withLabel(config.getAppLabel())
         .withIcon(new QIcon("inventory_2"))
         .withPermissionRules(ObjectStorageTables.permissionRules())
         .withWidgets(List.of(HOME_WIDGET))
         .withSection(new QAppSection().withName("storage").withLabel("Storage").withIcon(new QIcon("inventory_2"))
            .withTables(List.of(ObjectStorageTables.BUCKET, ObjectStorageTables.OBJECT, ObjectStorageTables.FOLDER, ObjectStorageTables.CONNECTION)))
         .withSection(new QAppSection().withName("activity").withLabel("Activity").withIcon(new QIcon("history"))
            .withTables(List.of(ObjectStorageTables.OPERATION)))
         .withSection(new QAppSection().withName("rules").withLabel("Rules").withIcon(new QIcon("rule"))
            .withTables(List.of(ObjectStorageTables.HEADER_RULE, ObjectStorageTables.PROTECTION_RULE)));
      for(String navTable : ObjectStorageTables.NAV_TABLES)
      {
         for(QTableMetaData table : tables)
         {
            if(table.getName().equals(navTable))
            {
               app.withChild(table);
            }
         }
      }
      return (app);
   }



   /*******************************************************************************
    ** The prefix of table and record links: /app, so a link is /app/<tableName>
    ** (the frontend serves every table, in any app, at that one flat route).
    *******************************************************************************/
   public static String tablePathPrefix()
   {
      return (ObjectStorageRuntime.isInstalled() ? ObjectStorageRuntime.getConfig().getTablePathPrefix() : "/app");
   }



   /*******************************************************************************
    ** Storage home renderer.
    *******************************************************************************/
   public static class HomeRenderer extends AbstractWidgetRenderer
   {
      /*******************************************************************************
       ** Render.
       *******************************************************************************/
      @Override
      public RenderWidgetOutput render(RenderWidgetInput input) throws QException
      {
         ConfigSync.ensureSynced();
         return (new RenderWidgetOutput(new RawHTML(null, StorageHome.render(tablePathPrefix(), Instant.now()))));
      }
   }
}
