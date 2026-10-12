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

package com.kingsrook.qbits.objectstorage.internal;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.objectstorage.api.StorageCaller;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Evaluates object_storage_protection_rule: a protected bucket and prefix, with
 ** a mode (READ_ONLY blocks every write, NO_DELETE blocks delete and move, WARN
 ** only notes it) and optional allowedSystemCallers (for example "backups")
 ** whose system-context permanent delete may pass a NO_DELETE rule.
 *******************************************************************************/
public final class ProtectionRules
{
   private static final QLogger LOG = QLogger.getLogger(ProtectionRules.class);

   /*******************************************************************************
    ** What an operation is, for rule matching.
    *******************************************************************************/
   public enum Op
   {
      WRITE,
      DELETE
   }



   /*******************************************************************************
    ** The result of a check.
    *******************************************************************************/
   public record Result(boolean blocked, String reason, boolean warned)
   {
      /*******************************************************************************
       ** Not protected.
       *******************************************************************************/
      public static Result open()
      {
         return (new Result(false, null, false));
      }
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ProtectionRules()
   {
   }



   /*******************************************************************************
    ** Check a key for an operation. Never throws: when the table cannot be read,
    ** nothing is protected by rule (the bucket-level protectedBucket flag still
    ** applies in the caller).
    *******************************************************************************/
   public static Result check(String bucketAlias, String key, Op op, StorageCaller caller)
   {
      if(!Db.available(ObjectStorageTables.PROTECTION_RULE))
      {
         return (Result.open());
      }
      try
      {
         Integer       bucketId = ConfigSync.bucketId(bucketAlias);
         List<QRecord> rules    = Db.asSystem(() -> Db.query(ObjectStorageTables.PROTECTION_RULE, "enabled", true));
         List<String>  warnings = new ArrayList<>();
         for(QRecord rule : rules)
         {
            if(rule.getValueInteger("bucketId") != null && !rule.getValueInteger("bucketId").equals(bucketId))
            {
               continue;
            }
            if(!Globs.matches(rule.getValueString("prefixGlob"), key))
            {
               continue;
            }
            String mode   = rule.getValueString("mode");
            String reason = "Protected by a rule" + (StringUtils.hasContent(rule.getValueString("reason")) ? ": " + rule.getValueString("reason") : "");
            if("WARN".equals(mode))
            {
               warnings.add(reason);
            }
            else if("READ_ONLY".equals(mode) || ("NO_DELETE".equals(mode) && op == Op.DELETE))
            {
               if(op == Op.DELETE && caller.isSystem() && allowed(rule.getValueString("allowedSystemCallers"), caller.getCallerName()))
               {
                  continue;
               }
               return (new Result(true, reason, false));
            }
         }
         return (new Result(false, warnings.isEmpty() ? null : String.join("; ", warnings), !warnings.isEmpty()));
      }
      catch(Exception e)
      {
         LOG.warn("Could not evaluate protection rules", e);
         return (Result.open());
      }
   }



   /*******************************************************************************
    ** Whether a caller name is in a comma-separated allowed list.
    *******************************************************************************/
   private static boolean allowed(String allowedList, String callerName)
   {
      if(!StringUtils.hasContent(allowedList) || callerName == null)
      {
         return (false);
      }
      for(String name : allowedList.split(","))
      {
         if(name.trim().equals(callerName))
         {
            return (true);
         }
      }
      return (false);
   }
}
