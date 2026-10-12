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


import java.util.Optional;
import com.kingsrook.qbits.objectstorage.api.BucketKey;
import com.kingsrook.qbits.objectstorage.api.PublicUrlResolver;
import com.kingsrook.qbits.objectstorage.api.StorageKeys;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** publicBaseUrl + "/" + percent-encoded key, and the reverse.
 *******************************************************************************/
public class DefaultPublicUrlResolver implements PublicUrlResolver
{
   /*******************************************************************************
    ** The public URL of a key, or null when the bucket has no public base URL.
    *******************************************************************************/
   @Override
   public String resolve(StorageBucketConfig bucket, String key)
   {
      String base = SecretRefs.resolveSetting(bucket.getPublicBaseUrl());
      if(!StringUtils.hasContent(base))
      {
         return (null);
      }
      return (stripTrailingSlash(base) + "/" + StorageKeys.encodeForUrl(key));
   }



   /*******************************************************************************
    ** The bucket and key a public URL points at, when one configured base URL
    ** (longest match) is a prefix of it.
    *******************************************************************************/
   @Override
   public Optional<BucketKey> parse(String url)
   {
      if(!StringUtils.hasContent(url) || !ObjectStorageRuntime.isInstalled())
      {
         return (Optional.empty());
      }
      StorageBucketConfig best     = null;
      String              bestBase = "";
      for(StorageBucketConfig bucket : ObjectStorageRuntime.getConfig().getBuckets())
      {
         String base = stripTrailingSlash(SecretRefs.resolveSetting(bucket.getPublicBaseUrl()) == null ? "" : SecretRefs.resolveSetting(bucket.getPublicBaseUrl()));
         if(!base.isEmpty() && url.startsWith(base + "/") && base.length() > bestBase.length())
         {
            best = bucket;
            bestBase = base;
         }
      }
      if(best == null)
      {
         return (Optional.empty());
      }
      String rest = url.substring(bestBase.length() + 1);
      int    q    = rest.indexOf('?');
      if(q >= 0)
      {
         rest = rest.substring(0, q);
      }
      return (Optional.of(new BucketKey(best.getAlias(), StorageKeys.decodeFromUrl(rest))));
   }



   /*******************************************************************************
    ** Remove trailing slashes.
    *******************************************************************************/
   private static String stripTrailingSlash(String s)
   {
      String r = s;
      while(r.endsWith("/"))
      {
         r = r.substring(0, r.length() - 1);
      }
      return (r);
   }
}
