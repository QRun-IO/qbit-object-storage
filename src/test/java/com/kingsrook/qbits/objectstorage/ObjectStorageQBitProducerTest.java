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


import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfigValidationException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Unit test for ObjectStorageQBitProducer
 *******************************************************************************/
class ObjectStorageQBitProducerTest
{

   /*******************************************************************************
    ** A configured qbit registers itself in the instance.
    *******************************************************************************/
   @Test
   void testProduce() throws Exception
   {
      QInstance           qInstance = new QInstance();
      ObjectStorageQBitConfig config    = new ObjectStorageQBitConfig().withBackendName("rdbms");
      ObjectStorageQBitProducer producer  = new ObjectStorageQBitProducer().withQBitConfig(config).withNamespace("object_storage");

      String artifactVersion = System.getProperty("qbit.artifactVersion");
      assertThat(artifactVersion).isNotBlank();
      assertThat(ObjectStorageQBitProducer.VERSION).isEqualTo(artifactVersion);
      assertThat(producer.getQBitMetaData().getVersion()).isEqualTo(artifactVersion);
      assertThat(producer.isEnabled()).isTrue();
      assertThat(producer.getQBitConfig()).isSameAs(config);
      assertThat(producer.getNamespace()).isEqualTo("object_storage");
      assertThat(config.getDefaultBackendNameForTables()).isEqualTo("rdbms");

      producer.produce(qInstance);
      assertThat(qInstance.getQBits()).hasSize(1);
      assertThat(qInstance.getQBits().values().iterator().next().getArtifactId()).isEqualTo("qbit-object-storage");
   }



   /*******************************************************************************
    ** Without a config the producer is disabled; without a backend it fails.
    *******************************************************************************/
   @Test
   void testDisabledAndInvalid()
   {
      assertThat(new ObjectStorageQBitProducer().isEnabled()).isFalse();

      ObjectStorageQBitConfig config = new ObjectStorageQBitConfig();
      config.setBackendName(null);
      assertThat(config.getBackendName()).isNull();
      assertThatThrownBy(() -> new ObjectStorageQBitProducer().withQBitConfig(config).produce(new QInstance()))
         .isInstanceOf(QBitConfigValidationException.class);
   }



   /*******************************************************************************
    ** Names follow the owner rule: lowercase snake_case with the full qbit name.
    *******************************************************************************/
   @Test
   void testNaming()
   {
      assertThat(ObjectStorageQBitConfig.TABLE_NAME_PREFIX).isEqualTo("object_storage_").matches("^[a-z][a-z0-9_]*_$");
      assertThat(ObjectStorageQBitConfig.PERMISSION_BASE_NAME).isEqualTo("object_storage");
   }

}
