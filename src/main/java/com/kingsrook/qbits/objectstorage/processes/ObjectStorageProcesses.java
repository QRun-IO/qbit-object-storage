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

package com.kingsrook.qbits.objectstorage.processes;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;


/*******************************************************************************
 ** All of the qbit's processes. Every action on records is a standard streamed
 ** ETL (Review is the dry run; Result has summary lines). None is in the nav.
 *******************************************************************************/
public final class ObjectStorageProcesses
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ObjectStorageProcesses()
   {
   }



   /*******************************************************************************
    ** All processes for a config (also records their permissions).
    *******************************************************************************/
   public static List<QProcessMetaData> processes(ObjectStorageQBitConfig config)
   {
      List<QProcessMetaData> processes = new ArrayList<>(TestConnectionProcess.processes());
      processes.addAll(BucketProcesses.processes());
      processes.addAll(ObjectWriteProcesses.processes());
      processes.addAll(CopyMoveProcesses.processes());
      processes.addAll(DeleteRestoreProcesses.processes());
      processes.addAll(LinkProcesses.processes());
      processes.addAll(FolderProcesses.processes());
      processes.addAll(UploadProcess.processes());
      processes.addAll(NightlyProcess.processes(config));
      return (processes);
   }
}
