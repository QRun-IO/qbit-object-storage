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

package com.kingsrook.qbits.objectstorage;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.objectstorage.app.StorageWidgets;
import com.kingsrook.qbits.objectstorage.health.ObjectStorageConnectionChecks;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.DefaultObjectHeaderPolicy;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageHelp;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qbits.objectstorage.processes.ObjectStorageProcesses;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.health.HealthCheckSupplierMetaData;
import com.kingsrook.qqq.backend.core.health.HealthChecksMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.help.bundles.HelpBundles;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** Produces the Object Storage qbit: the object_storage_* tables and possible
 ** values, the security key types for per-bucket and per-connection scoping, the
 ** processes (table and record actions), and, when withAdminApp(true), the
 ** Admin -> Storage app with its home widget. Without the admin app a headless
 ** consumer (for example the CMS qbit) still gets the service and the tables.
 *******************************************************************************/
public class ObjectStorageQBitProducer implements QBitMetaDataProducer<ObjectStorageQBitConfig>
{
   public static final String GROUP_ID    = "com.kingsrook.qbits";
   public static final String ARTIFACT_ID = "qbit-object-storage";
   public static final String VERSION     = "0.1.0";

   private ObjectStorageQBitConfig qBitConfig;
   private String                  namespace;



   /*******************************************************************************
    ** Fluent setter for the config.
    *******************************************************************************/
   public ObjectStorageQBitProducer withQBitConfig(ObjectStorageQBitConfig config)
   {
      this.qBitConfig = config;
      return (this);
   }



   /*******************************************************************************
    ** Getter for the config.
    *******************************************************************************/
   @Override
   public ObjectStorageQBitConfig getQBitConfig()
   {
      return (qBitConfig);
   }



   /*******************************************************************************
    ** Fluent setter for the namespace.
    *******************************************************************************/
   public ObjectStorageQBitProducer withNamespace(String namespace)
   {
      this.namespace = namespace;
      return (this);
   }



   /*******************************************************************************
    ** Getter for the namespace.
    *******************************************************************************/
   @Override
   public String getNamespace()
   {
      return (namespace);
   }



   /*******************************************************************************
    ** The qbit's identity.
    *******************************************************************************/
   @Override
   public QBitMetaData getQBitMetaData()
   {
      return (new QBitMetaData().withGroupId(GROUP_ID).withArtifactId(ARTIFACT_ID).withVersion(VERSION).withNamespace(namespace).withConfig(qBitConfig));
   }



   /*******************************************************************************
    ** Producers found by package scanning have no config: skip them.
    *******************************************************************************/
   @Override
   public boolean isEnabled()
   {
      return (qBitConfig != null);
   }



   /*******************************************************************************
    ** Build everything and add it to the instance.
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      qBitConfig.validate(qInstance);
      QBitMetaData qBit = getQBitMetaData();
      qInstance.addQBit(qBit);
      ObjectStorageRuntime.install(qBitConfig);
      ConfigSync.reset();
      DefaultObjectHeaderPolicy.clearCache();

      MetaDataProducerMultiOutput output = new MetaDataProducerMultiOutput();
      for(QPossibleValueSource possibleValueSource : ObjectStorageTables.possibleValueSources())
      {
         output.add(possibleValueSource);
      }
      if(Boolean.TRUE.equals(qBitConfig.getBucketScoping()))
      {
         for(QSecurityKeyType keyType : ObjectStorageTables.securityKeyTypes())
         {
            output.add(keyType);
         }
      }
      for(QJoinMetaData join : ObjectStorageTables.joins())
      {
         output.add(join);
      }
      for(QWidgetMetaData widget : ObjectStorageTables.childListWidgets())
      {
         output.add(widget);
      }
      List<QTableMetaData> tables = new ArrayList<>();
      for(QTableMetaData table : ObjectStorageTables.tables(qBitConfig))
      {
         table.setSourceQBitName(qBit.getName());
         tables.add(table);
         output.add(table);
      }
      for(QProcessMetaData process : ObjectStorageProcesses.processes(qBitConfig))
      {
         process.setSourceQBitName(qBit.getName());
         output.add(process);
      }
      if(Boolean.TRUE.equals(qBitConfig.getAdminApp()))
      {
         for(QWidgetMetaData widget : StorageWidgets.widgets())
         {
            output.add(widget);
         }
         QAppMetaData app = StorageWidgets.app(qBitConfig, tables);
         if(qBitConfig.getParentAppName() != null)
         {
            app.setParentAppName(qBitConfig.getParentAppName());
         }
         output.add(app);
      }
      output.addSelfToInstance(qInstance);
      HealthChecksMetaData.ofOrWithNew(qInstance).withSupplier(new HealthCheckSupplierMetaData()
         .withNamePrefix("object_storage.connection").withComponent("object_storage")
         .withSupplier(new QCodeReference(ObjectStorageConnectionChecks.class)));
      if(Boolean.TRUE.equals(qBitConfig.getAdminApp()) && qBitConfig.getParentAppName() != null && qInstance.getApp(qBitConfig.getParentAppName()) != null)
      {
         ///////////////////////////////////////////////////////////////////////////////
         // Admin -> Storage: the parent app lists the Storage app in one of its sections //
         ///////////////////////////////////////////////////////////////////////////////
         qInstance.getApp(qBitConfig.getParentAppName()).withChild(qInstance.getApp(qBitConfig.getAppName()));
      }
      // Registration is metadata-only; core skips startup sync without a helpContent table.
      HelpBundles.register(qInstance, "help/qbit-object-storage.yaml");
      ObjectStorageHelp.apply(qInstance, qBitConfig);
      return (new MetaDataProducerMultiOutput());
   }
}
