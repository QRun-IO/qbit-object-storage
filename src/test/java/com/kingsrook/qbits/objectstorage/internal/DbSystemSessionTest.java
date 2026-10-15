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


import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.testsupport.StorageTestSupport;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Db.asSystem runs work as the core system user. Token-based authentication
 ** modules (OAuth2, Auth0) accept a QSystemUserSession without a token and
 ** reject any other tokenless session, so the class matters, not just the
 ** permissions.
 *******************************************************************************/
class DbSystemSessionTest
{
   /*******************************************************************************
    ** Reset the runtime and context after each test.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      StorageTestSupport.tearDown();
   }



   /*******************************************************************************
    ** The work sees a QSystemUserSession with every all-access key, and the
    ** caller's session is restored afterwards.
    *******************************************************************************/
   @Test
   void testAsSystemUsesCoreSystemUserSession() throws Exception
   {
      ObjectStorageQBitConfig config = new ObjectStorageQBitConfig().withBackendName(StorageTestSupport.MEMORY).withBucketScoping(false);
      StorageTestSupport.setUp(config, instance -> instance.addSecurityKeyType(new QSecurityKeyType().withName("tenant").withAllAccessKeyName("tenantAllAccess")));
      QSession caller = QContext.getQSession();

      QSession inside = Db.asSystem(QContext::getQSession);

      assertThat(inside).isInstanceOf(QSystemUserSession.class);
      assertThat(inside.hasPermission("anything")).isTrue();
      assertThat(inside.hasSecurityKeyValue("tenantAllAccess", true)).isTrue();
      assertThat(QContext.getQSession()).isSameAs(caller);
   }
}
