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

package com.kingsrook.qbits.objectstorage.api;


import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Who is making a storage call: the current session's user, or a system caller
 ** (a calling qbit and its run, for example "backups" run 1234). System callers
 ** are not subject to the user's object_storage.* permissions; the calling qbit
 ** must have checked its own permission first. Every write is logged with this.
 *******************************************************************************/
public final class StorageCaller
{
   private final boolean system;
   private final String  callerName;
   private final String  runId;
   private final String  startedBy;



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   private StorageCaller(boolean system, String callerName, String runId, String startedBy)
   {
      this.system = system;
      this.callerName = callerName;
      this.runId = runId;
      this.startedBy = startedBy;
   }



   /*******************************************************************************
    ** The current session's user as the caller.
    *******************************************************************************/
   public static StorageCaller user()
   {
      return (new StorageCaller(false, null, null, currentUserName()));
   }



   /*******************************************************************************
    ** A system caller: the calling qbit's name and its run id.
    *******************************************************************************/
   public static StorageCaller system(String callerName, String runId)
   {
      return (new StorageCaller(true, callerName, runId, currentUserName()));
   }



   /*******************************************************************************
    ** The name of the current user, or null.
    *******************************************************************************/
   private static String currentUserName()
   {
      QSession session = QContext.getQSession();
      if(session != null && session.getUser() != null)
      {
         String id = session.getUser().getIdReference();
         return (StringUtils.hasContent(id) ? id : session.getUser().getFullName());
      }
      return (null);
   }



   /*******************************************************************************
    ** Whether this is a system caller.
    *******************************************************************************/
   public boolean isSystem()
   {
      return (system);
   }



   /*******************************************************************************
    ** Getter for callerName.
    *******************************************************************************/
   public String getCallerName()
   {
      return (callerName);
   }



   /*******************************************************************************
    ** Getter for runId.
    *******************************************************************************/
   public String getRunId()
   {
      return (runId);
   }



   /*******************************************************************************
    ** The text recorded in the operation log.
    *******************************************************************************/
   public String describe()
   {
      if(system)
      {
         return ("system: " + callerName + (runId == null ? "" : " run " + runId) + (startedBy == null ? "" : " (started by user " + startedBy + ")"));
      }
      return (startedBy == null ? "system (no user)" : startedBy);
   }
}
