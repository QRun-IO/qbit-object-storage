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


import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaderPolicy;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaders;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The default header policy: the first enabled object_storage_header_rule (by
 ** ascending priority) whose bucket, key glob or regex, and content-type pattern
 ** match decides the headers. BACKUP buckets are never touched. Rules are
 ** ordinary audited data, cached briefly.
 *******************************************************************************/
public class DefaultObjectHeaderPolicy implements ObjectHeaderPolicy
{
   private static final QLogger LOG = QLogger.getLogger(DefaultObjectHeaderPolicy.class);

   private static final long CACHE_MILLIS = 15_000L;

   private static volatile List<QRecord> cachedRules;
   private static volatile long          cachedAt;



   /*******************************************************************************
    ** Drop cached rules (after the rules change).
    *******************************************************************************/
   public static void clearCache()
   {
      cachedRules = null;
   }



   /*******************************************************************************
    ** The headers the policy gives an object (empty when no rule matches).
    *******************************************************************************/
   @Override
   public ObjectHeaders headersFor(StorageBucketConfig bucket, String key, String contentType)
   {
      if(bucket.getRole() == BucketRole.BACKUP)
      {
         return (new ObjectHeaders());
      }
      Integer bucketId = ConfigSync.bucketId(bucket.getAlias());
      for(QRecord rule : rules())
      {
         Integer ruleBucket = rule.getValueInteger("bucketId");
         if(ruleBucket != null && !ruleBucket.equals(bucketId))
         {
            continue;
         }
         if(matches(rule, key, contentType))
         {
            return (new ObjectHeaders().withCacheControl(rule.getValueString("cacheControl")).withContentDisposition(rule.getValueString("contentDisposition")));
         }
      }
      return (new ObjectHeaders());
   }



   /*******************************************************************************
    ** Whether a rule matches the key and content type.
    *******************************************************************************/
   static boolean matches(QRecord rule, String key, String contentType)
   {
      String glob  = rule.getValueString("keyGlob");
      String regex = rule.getValueString("keyRegex");
      String ct    = rule.getValueString("contentTypePattern");
      if(StringUtils.hasContent(glob) && !Globs.matches(glob, key))
      {
         return (false);
      }
      if(StringUtils.hasContent(regex) && !Pattern.compile(regex).matcher(key).matches())
      {
         return (false);
      }
      if(StringUtils.hasContent(ct) && (contentType == null || !Globs.matches(ct, contentType)))
      {
         return (false);
      }
      return (true);
   }



   /*******************************************************************************
    ** The enabled rules by ascending priority (empty when the table is not there).
    *******************************************************************************/
   private static List<QRecord> rules()
   {
      List<QRecord> rules = cachedRules;
      if(rules != null && System.currentTimeMillis() - cachedAt < CACHE_MILLIS)
      {
         return (rules);
      }
      if(!Db.available(ObjectStorageTables.HEADER_RULE))
      {
         return (List.of());
      }
      try
      {
         rules = Db.asSystem(() -> Db.query(ObjectStorageTables.HEADER_RULE, "enabled", true));
         rules.sort(Comparator.comparing((QRecord r) -> r.getValueInteger("priority") == null ? 100 : r.getValueInteger("priority"))
            .thenComparing(r -> r.getValueInteger("id") == null ? 0 : r.getValueInteger("id")));
         cachedRules = rules;
         cachedAt = System.currentTimeMillis();
         return (rules);
      }
      catch(Exception e)
      {
         LOG.warn("Could not read storage header rules", e);
         return (List.of());
      }
   }
}
