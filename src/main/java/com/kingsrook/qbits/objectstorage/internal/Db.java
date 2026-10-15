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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.CapturedContext;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;


/*******************************************************************************
 ** Small helpers for reading and writing the qbit's own tables as the system
 ** user (the service writes its audit log and index regardless of the calling
 ** user's per-bucket scope; the calling user's permissions were checked first).
 *******************************************************************************/
public final class Db
{
   /*******************************************************************************
    ** Something that may throw.
    *******************************************************************************/
   @FunctionalInterface
   public interface Work<T>
   {
      /*******************************************************************************
       ** Do the work.
       *******************************************************************************/
      T run() throws Exception;
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private Db()
   {
   }



   /*******************************************************************************
    ** Whether the instance and the table exist in the current context.
    *******************************************************************************/
   public static boolean available(String table)
   {
      return (QContext.getQInstance() != null && QContext.getQInstance().getTable(table) != null);
   }



   /*******************************************************************************
    ** Run work in the system-user context, restoring the caller's afterwards.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public static <T> T asSystem(Work<T> work) throws QException
   {
      if(QContext.getQInstance() == null)
      {
         throw (new QException("No QQQ context is active"));
      }
      CapturedContext original = QContext.capture();
      Object[]        holder   = new Object[1];
      Exception[]     error    = new Exception[1];
      try
      {
         QContext.init(new CapturedContext(original.qInstance(), systemSession(original.qInstance())));
         try
         {
            holder[0] = work.run();
         }
         catch(Exception e)
         {
            error[0] = e;
         }
      }
      finally
      {
         QContext.init(original);
      }
      if(error[0] != null)
      {
         if(error[0] instanceof QException qe)
         {
            throw (qe);
         }
         if(error[0] instanceof RuntimeException re)
         {
            throw (re);
         }
         throw (new QException("Storage table access failed", error[0]));
      }
      return ((T) holder[0]);
   }



   /*******************************************************************************
    ** The core system-user session (every permission and every all-access
    ** security key of the given instance, read per instance). It must be a
    ** QSystemUserSession: token-based authentication modules (OAuth2, Auth0)
    ** accept that class without a token and reject any other tokenless session,
    ** which made every system read and write fail on such hosts.
    *******************************************************************************/
   private static QSession systemSession(QInstance instance)
   {
      return (new QSystemUserSession(instance));
   }



   /*******************************************************************************
    ** Query a table with equals criteria (name, value pairs).
    *******************************************************************************/
   public static List<QRecord> query(String table, Object... nameValuePairs) throws QException
   {
      QQueryFilter filter = new QQueryFilter();
      for(int i = 0; i + 1 < nameValuePairs.length; i += 2)
      {
         filter.withCriteria(new QFilterCriteria((String) nameValuePairs[i], QCriteriaOperator.EQUALS, (Serializable) nameValuePairs[i + 1]));
      }
      return (query(table, filter));
   }



   /*******************************************************************************
    ** Query a table with a filter.
    *******************************************************************************/
   public static List<QRecord> query(String table, QQueryFilter filter) throws QException
   {
      return (new QueryAction().execute(new QueryInput(table).withFilter(filter)).getRecords());
   }



   /*******************************************************************************
    ** Run a query page by page (ordered by id), handing each page to the consumer.
    ** The filter's own skip and limit are ignored.
    *******************************************************************************/
   public static void forEachPage(String table, QQueryFilter filter, int pageSize, java.util.function.Consumer<List<QRecord>> consumer) throws QException
   {
      int skip = 0;
      while(true)
      {
         QQueryFilter page = filter.clone();
         page.setOrderBys(new ArrayList<>(List.of(new com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy("id"))));
         page.setSkip(skip);
         page.setLimit(pageSize);
         QueryInput input = new QueryInput(table).withFilter(page);
         input.setShouldGenerateDisplayValues(false);
         input.setShouldTranslatePossibleValues(false);
         List<QRecord> records = new QueryAction().execute(input).getRecords();
         if(records.isEmpty())
         {
            return;
         }
         consumer.accept(records);
         if(records.size() < pageSize)
         {
            return;
         }
         skip += pageSize;
      }
   }



   /*******************************************************************************
    ** Count the records matching a filter.
    *******************************************************************************/
   public static int count(String table, QQueryFilter filter) throws QException
   {
      return (new com.kingsrook.qqq.backend.core.actions.tables.CountAction().execute(new com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput(table).withFilter(filter)).getCount());
   }



   /*******************************************************************************
    ** An equals criteria.
    *******************************************************************************/
   public static QFilterCriteria eq(String field, Serializable value)
   {
      return (new QFilterCriteria(field, QCriteriaOperator.EQUALS, value));
   }



   /*******************************************************************************
    ** The first record of an equals query, or null.
    *******************************************************************************/
   public static QRecord first(String table, Object... nameValuePairs) throws QException
   {
      List<QRecord> records = query(table, nameValuePairs);
      return (records.isEmpty() ? null : records.get(0));
   }



   /*******************************************************************************
    ** Insert records, returning them with their ids.
    *******************************************************************************/
   public static List<QRecord> insert(String table, List<QRecord> records) throws QException
   {
      return (new InsertAction().execute(new InsertInput(table).withRecords(records)).getRecords());
   }



   /*******************************************************************************
    ** Insert one record.
    *******************************************************************************/
   public static QRecord insert(String table, QRecord record) throws QException
   {
      List<QRecord> list = new ArrayList<>();
      list.add(record);
      return (insert(table, list).get(0));
   }



   /*******************************************************************************
    ** Update records (each must carry its primary key).
    *******************************************************************************/
   public static List<QRecord> update(String table, List<QRecord> records) throws QException
   {
      return (new UpdateAction().execute(new UpdateInput(table).withRecords(records)).getRecords());
   }



   /*******************************************************************************
    ** Delete records by primary key.
    *******************************************************************************/
   public static void delete(String table, List<Serializable> ids) throws QException
   {
      if(!ids.isEmpty())
      {
         new DeleteAction().execute(new DeleteInput(table).withPrimaryKeys(ids));
      }
   }



   /*******************************************************************************
    ** A string value of a record field, or null.
    *******************************************************************************/
   public static String str(QRecord record, String field)
   {
      return (record == null ? null : record.getValueString(field));
   }
}
