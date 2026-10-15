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


import java.util.Set;
import java.util.stream.Collectors;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.help.bundles.HelpBundles;
import com.kingsrook.qqq.backend.core.testing.HelpBundleAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Coverage of every object owned by the storage qbit, including its optional app.
 *******************************************************************************/
class ObjectStorageHelpBundleTest
{
   /*******************************************************************************
    ** Release the installed runtime after each test.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      StorageTestSupport.tearDown();
   }



   /*******************************************************************************
    ** Register safely without help tables and cover all owned metadata.
    *******************************************************************************/
   @Test
   void bundleIsCompleteWithoutHelpTables() throws Exception
   {
      QInstance instance = StorageTestSupport.setUp(new ObjectStorageQBitConfig()
         .withBackendName(StorageTestSupport.MEMORY).withBucketScoping(false).withOpenWhenAnonymous(true).withAdminApp(true));

      assertThat(instance.getTable("helpContent")).isNull();
      assertThat(HelpBundles.of(instance).getBundles()).singleElement()
         .satisfies(bundle -> assertThat(bundle.getId()).isEqualTo("qbit-object-storage"));
      HelpBundles.syncOnStartup(instance);
      Set<String> keys = HelpBundles.of(instance).getBundles().get(0).getEntries().stream()
         .map(entry -> entry.getKey()).collect(Collectors.toSet());
      // RC.7 validates these entries but only requires app/table/process/field coverage.
      instance.getWidgets().keySet().stream().filter(name -> name.startsWith("object_storage_") || name.startsWith("objectStorage"))
         .forEach(name -> assertThat(keys).contains("widget:" + name + ";slot:label"));
      instance.getPossibleValueSources().keySet().stream().filter(name -> name.startsWith("object_storage_"))
         .forEach(name -> assertThat(keys).contains("possibleValueSource:" + name + ";slot:help"));
      HelpBundleAssertions.forInstance(instance)
         .bundles("help/qbit-object-storage.yaml")
         .owning(target -> target.name().startsWith("object_storage_") || target.name().startsWith("objectStorage"))
         .assertComplete();
   }
}
