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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;


/*******************************************************************************
 ** The Setup card on the Storage home: each configuration table, its state and
 ** the next step. States use the same resolution the runtime uses: connections
 ** and buckets come from code (so "set up" means declared and synced), and the
 ** header policy is the host's own when the config names one, otherwise the
 ** enabled rows of object_storage_header_rule.
 *******************************************************************************/
public final class StorageSetupStatus
{
   public static final String SET_UP         = "Set up";
   public static final String NEEDS_SETUP    = "Needs setup";
   public static final String USING_DEFAULTS = "Using defaults";
   public static final String OPTIONAL       = "Optional";



   /*******************************************************************************
    ** One line of the card.
    *******************************************************************************/
   public record Step(String label, String state, String next, String table)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private StorageSetupStatus()
   {
   }



   /*******************************************************************************
    ** Read the current state, as the viewer.
    *******************************************************************************/
   public static List<Step> load() throws QException
   {
      return (steps(count(ObjectStorageTables.CONNECTION, new QQueryFilter()), count(ObjectStorageTables.BUCKET, new QQueryFilter()),
         count(ObjectStorageTables.HEADER_RULE, new QQueryFilter(new QFilterCriteria("enabled", QCriteriaOperator.EQUALS, true))),
         ObjectStorageRuntime.getConfig().getHeaderPolicy() != null,
         count(ObjectStorageTables.PROTECTION_RULE, new QQueryFilter(new QFilterCriteria("enabled", QCriteriaOperator.EQUALS, true)))));
   }



   /*******************************************************************************
    ** The steps, from the numbers (pure, for tests).
    *******************************************************************************/
   static List<Step> steps(int connections, int buckets, int enabledHeaderRules, boolean hostHeaderPolicy, int enabledProtectionRules)
   {
      List<Step> steps = new ArrayList<>();
      steps.add(connections > 0
         ? new Step("Connections", SET_UP, connections + " declared in code and synced here; set limits and timeouts on the row", ObjectStorageTables.CONNECTION)
         : new Step("Connections", NEEDS_SETUP, "Declare a connection in the qbit config (withConnection) and restart: endpoint, region and the names of the key variables", ObjectStorageTables.CONNECTION));
      steps.add(buckets > 0
         ? new Step("Buckets", SET_UP, buckets + " declared in code and synced here; set the presign limits on the row", ObjectStorageTables.BUCKET)
         : new Step("Buckets", NEEDS_SETUP, "Declare a bucket in the qbit config (withBucket) and restart", ObjectStorageTables.BUCKET));
      if(hostHeaderPolicy)
      {
         steps.add(new Step("Header rules", SET_UP, "The host's own header policy (code) decides; these rows are not read", ObjectStorageTables.HEADER_RULE));
      }
      else if(enabledHeaderRules > 0)
      {
         steps.add(new Step("Header rules", SET_UP, enabledHeaderRules + " enabled; the first match by priority sets Cache-Control", ObjectStorageTables.HEADER_RULE));
      }
      else
      {
         steps.add(new Step("Header rules", USING_DEFAULTS, "No rule is enabled, so objects keep the headers they were written with. Enable the starter rule or add one", ObjectStorageTables.HEADER_RULE));
      }
      steps.add(enabledProtectionRules > 0
         ? new Step("Protection rules", SET_UP, enabledProtectionRules + " enabled", ObjectStorageTables.PROTECTION_RULE)
         : new Step("Protection rules", OPTIONAL, "Add a rule to make a bucket prefix read-only or delete-proof", ObjectStorageTables.PROTECTION_RULE));
      return (steps);
   }



   /*******************************************************************************
    ** Count as the viewer.
    *******************************************************************************/
   private static int count(String table, QQueryFilter filter) throws QException
   {
      Integer count = new CountAction().execute(new CountInput(table).withFilter(filter)).getCount();
      return (count == null ? 0 : count);
   }

}
