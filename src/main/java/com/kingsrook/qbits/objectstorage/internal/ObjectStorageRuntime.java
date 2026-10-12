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


import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaderPolicy;
import com.kingsrook.qbits.objectstorage.api.PublicUrlResolver;
import com.kingsrook.qbits.objectstorage.api.StorageAlertSink;
import com.kingsrook.qbits.objectstorage.api.StorageChangeListener;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;


/*******************************************************************************
 ** Process-wide state of the qbit: the installed config, one ConnectionRuntime
 ** per connection (clients are cached and rebuilt when settings change), the
 ** listeners, the header policy, the URL resolver, the alert sink and a clock
 ** (replaceable in tests).
 *******************************************************************************/
public final class ObjectStorageRuntime
{
   private static final Map<String, ConnectionRuntime>   CONNECTIONS = new ConcurrentHashMap<>();
   private static final List<StorageChangeListener>      LISTENERS   = new CopyOnWriteArrayList<>();
   private static final Map<String, String>              PROCESS_PERMISSIONS = new ConcurrentHashMap<>();

   private static volatile ObjectStorageQBitConfig config;
   private static volatile ObjectHeaderPolicy      headerPolicy;
   private static volatile PublicUrlResolver       publicUrlResolver;
   private static volatile StorageAlertSink        alertSink = StorageAlertSink.noop();
   private static volatile Clock                   clock     = Clock.systemUTC();
   private static volatile IndexHook               indexHook;



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ObjectStorageRuntime()
   {
   }



   /*******************************************************************************
    ** Install the config (called by the producer). Connections are rebuilt.
    *******************************************************************************/
   public static synchronized void install(ObjectStorageQBitConfig newConfig)
   {
      closeConnections();
      config = newConfig;
      headerPolicy = newConfig.getHeaderPolicy() != null ? newConfig.getHeaderPolicy() : new DefaultObjectHeaderPolicy();
      publicUrlResolver = newConfig.getPublicUrlResolver() != null ? newConfig.getPublicUrlResolver() : new DefaultPublicUrlResolver();
      alertSink = newConfig.getAlertSink() != null ? newConfig.getAlertSink() : StorageAlertSink.noop();
      CapabilityStore.clearCache();
   }



   /*******************************************************************************
    ** Forget everything (tests).
    *******************************************************************************/
   public static synchronized void reset()
   {
      closeConnections();
      LISTENERS.clear();
      PROCESS_PERMISSIONS.clear();
      config = null;
      headerPolicy = null;
      publicUrlResolver = null;
      alertSink = StorageAlertSink.noop();
      clock = Clock.systemUTC();
      indexHook = null;
      CapabilityStore.clearCache();
   }



   /*******************************************************************************
    ** Close and drop every cached client.
    *******************************************************************************/
   private static void closeConnections()
   {
      for(ConnectionRuntime connection : CONNECTIONS.values())
      {
         connection.close();
      }
      CONNECTIONS.clear();
   }



   /*******************************************************************************
    ** The installed config; fails clearly when the qbit is not installed.
    *******************************************************************************/
   public static ObjectStorageQBitConfig getConfig()
   {
      if(config == null)
      {
         throw (new IllegalStateException("The Object Storage qbit is not installed"));
      }
      return (config);
   }



   /*******************************************************************************
    ** Whether the qbit is installed.
    *******************************************************************************/
   public static boolean isInstalled()
   {
      return (config != null);
   }



   /*******************************************************************************
    ** The (cached) runtime of a connection.
    *******************************************************************************/
   public static ConnectionRuntime connection(String name)
   {
      StorageConnectionConfig connectionConfig = getConfig().findConnection(name);
      if(connectionConfig == null)
      {
         throw (new IllegalArgumentException("Unknown storage connection: " + name));
      }
      return (CONNECTIONS.computeIfAbsent(name, n -> new ConnectionRuntime(ConfigSync.withOverrides(connectionConfig))));
   }



   /*******************************************************************************
    ** The configured bucket of an alias; fails clearly when unknown.
    *******************************************************************************/
   public static StorageBucketConfig bucketConfig(String alias)
   {
      StorageBucketConfig bucket = getConfig().findBucket(alias);
      if(bucket == null)
      {
         throw (new IllegalArgumentException("Unknown storage bucket alias: " + alias));
      }
      return (bucket);
   }



   /*******************************************************************************
    ** Register a change listener.
    *******************************************************************************/
   public static void addChangeListener(StorageChangeListener listener)
   {
      LISTENERS.add(listener);
   }



   /*******************************************************************************
    ** Remove a change listener.
    *******************************************************************************/
   public static void removeChangeListener(StorageChangeListener listener)
   {
      LISTENERS.remove(listener);
   }



   /*******************************************************************************
    ** The registered listeners.
    *******************************************************************************/
   public static List<StorageChangeListener> listeners()
   {
      return (LISTENERS);
   }



   /*******************************************************************************
    ** The header policy in force.
    *******************************************************************************/
   public static ObjectHeaderPolicy headerPolicy()
   {
      return (headerPolicy == null ? new DefaultObjectHeaderPolicy() : headerPolicy);
   }



   /*******************************************************************************
    ** The public URL resolver in force.
    *******************************************************************************/
   public static PublicUrlResolver publicUrlResolver()
   {
      return (publicUrlResolver == null ? new DefaultPublicUrlResolver() : publicUrlResolver);
   }



   /*******************************************************************************
    ** Replace the public URL resolver (the CDN qbit does this).
    *******************************************************************************/
   public static void setPublicUrlResolver(PublicUrlResolver resolver)
   {
      publicUrlResolver = resolver;
   }



   /*******************************************************************************
    ** The alert sink.
    *******************************************************************************/
   public static StorageAlertSink alertSink()
   {
      return (alertSink);
   }



   /*******************************************************************************
    ** Replace the alert sink (an Alerts qbit adapter does this).
    *******************************************************************************/
   public static void setAlertSink(StorageAlertSink sink)
   {
      alertSink = sink == null ? StorageAlertSink.noop() : sink;
   }



   /*******************************************************************************
    ** The clock.
    *******************************************************************************/
   public static Clock clock()
   {
      return (clock);
   }



   /*******************************************************************************
    ** Replace the clock (tests).
    *******************************************************************************/
   public static void setClock(Clock newClock)
   {
      clock = newClock;
   }



   /*******************************************************************************
    ** The write-through index hook, or null when the index is not active.
    *******************************************************************************/
   public static IndexHook indexHook()
   {
      return (indexHook);
   }



   /*******************************************************************************
    ** Set the write-through index hook.
    *******************************************************************************/
   public static void setIndexHook(IndexHook hook)
   {
      indexHook = hook;
   }



   /*******************************************************************************
    ** The permission each process needs (default object_storage.admin).
    *******************************************************************************/
   public static Map<String, String> processPermissions()
   {
      return (PROCESS_PERMISSIONS);
   }
}
