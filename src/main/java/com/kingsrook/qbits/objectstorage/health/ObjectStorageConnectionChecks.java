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

package com.kingsrook.qbits.objectstorage.health;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.kingsrook.qbits.objectstorage.internal.CapabilityProbe;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.health.HealthCategory;
import com.kingsrook.qqq.backend.core.health.HealthCheckInput;
import com.kingsrook.qqq.backend.core.health.HealthCheckKind;
import com.kingsrook.qqq.backend.core.health.HealthCheckMetaData;
import com.kingsrook.qqq.backend.core.health.HealthCheckOutcome;
import com.kingsrook.qqq.backend.core.health.HealthCheckSupplier;
import com.kingsrook.qqq.backend.core.health.HealthDetails;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;


/*******************************************************************************
 ** Dynamic, read-only checks for configured connection rows. Diagnostic text and
 ** resolved settings never cross the health boundary.
 *******************************************************************************/
public class ObjectStorageConnectionChecks implements HealthCheckSupplier
{
   /*******************************************************************************
    ** Resolve current rows on every refresh; removed configuration is not probed.
    *******************************************************************************/
   @Override
   public List<HealthCheckMetaData> supply(QInstance qInstance) throws QException
   {
      ConfigSync.ensureSynced();
      List<HealthCheckMetaData> checks = new ArrayList<>();
      for(QRecord row : Db.asSystem(() -> Db.query(ObjectStorageTables.CONNECTION)))
      {
         if(ObjectStorageRuntime.getConfig().findConnection(row.getValueString("name")) == null)
         {
            continue;
         }
         Integer id = row.getValueInteger("id");
         String name = "object_storage.connection." + id.toString().toLowerCase(Locale.ROOT);
         checks.add(new HealthCheckMetaData().withName(name).withLabel("Storage connection " + id)
            .withComponent("object_storage").withCategory(HealthCategory.STORAGE).withKind(HealthCheckKind.DEPENDENCY)
            .withIntervalSeconds(300).withTimeoutMs(20000).withSeverity(AlertSeverity.ERROR)
            .withCheckInstance(input -> run(id, input)));
      }
      return (checks);
   }



   /*******************************************************************************
    ** Check the current row, reuse the bounded capability probe, and expose only
    ** numeric facts and a fixed problem vocabulary (never exception messages).
    *******************************************************************************/
   private static HealthCheckOutcome run(Integer id, HealthCheckInput input) throws QException
   {
      if(!Instant.now().isBefore(input.getDeadline()) || Thread.currentThread().isInterrupted())
      {
         return (HealthCheckOutcome.down("Storage probe timed out").withDetails(new HealthDetails().withValue("problemKind", "timeout")));
      }
      QRecord row = Db.asSystem(() -> Db.first(ObjectStorageTables.CONNECTION, "id", id));
      if(row == null || ObjectStorageRuntime.getConfig().findConnection(row.getValueString("name")) == null)
      {
         return (HealthCheckOutcome.unknown("Storage connection is disabled or no longer configured"));
      }
      try
      {
         CapabilityProbe.Report report = CapabilityProbe.probeConnection(row.getValueString("name"), input.getDeadline());
         HealthDetails details = new HealthDetails().withValue("bucketsProbed", report.getByBucket().size());
         if(report.getClockSkewSeconds() != null)
         {
            details.withValue("clockSkewSeconds", report.getClockSkewSeconds());
         }
         if(report.getProblemKind() != null)
         {
            return (HealthCheckOutcome.down("Storage connection probe failed").withDetails(details.withValue("problemKind", report.getProblemKind())));
         }
         if(report.getClockSkewSeconds() != null && report.getClockSkewSeconds() > CapabilityProbe.CLOCK_SKEW_THRESHOLD_SECONDS)
         {
            return (HealthCheckOutcome.degraded("Storage clock skew exceeds the signing threshold").withDetails(details));
         }
         if(report.getByBucket().isEmpty())
         {
            return (HealthCheckOutcome.unknown("No storage buckets configured to probe").withDetails(details));
         }
         return (HealthCheckOutcome.up("Storage connection is reachable").withDetails(details));
      }
      catch(RuntimeException e)
      {
         return (HealthCheckOutcome.down("Storage connection probe failed")
            .withDetails(new HealthDetails().withValue("problemKind", CapabilityProbe.problemKind(e))));
      }
   }
}
