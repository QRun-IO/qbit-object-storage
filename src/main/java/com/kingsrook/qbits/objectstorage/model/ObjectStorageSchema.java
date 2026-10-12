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

package com.kingsrook.qbits.objectstorage.model;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** The PostgreSQL schema of the qbit's tables, generated from the same metadata
 ** the app runs on, so an adopter's migration (Liquibase, Flyway) cannot drift
 ** from the tables. Column names are the snake_case of the field names (what the
 ** QQQ RDBMS backend uses), table names are the QQQ table names.
 **
 ** The statements are idempotent (CREATE ... IF NOT EXISTS) and include the
 ** unique constraints and the browse indexes the design calls for
 ** ((bucket, key) unique; bucket with folder, last modified, size, content type).
 *******************************************************************************/
public final class ObjectStorageSchema
{
   /** Unique constraints: table to column lists. */
   private static final Map<String, List<List<String>>> UNIQUES = Map.of(
      ObjectStorageTables.CONNECTION, List.of(List.of("name")),
      ObjectStorageTables.BUCKET, List.of(List.of("alias")),
      ObjectStorageTables.CONNECTION_CAPABILITY, List.of(List.of("connectionId", "capability")),
      ObjectStorageTables.BUCKET_CAPABILITY, List.of(List.of("bucketId", "capability")),
      ObjectStorageTables.FOLDER, List.of(List.of("bucketId", "path")),
      ObjectStorageTables.OBJECT, List.of(List.of("bucketId", "objectKey")),
      ObjectStorageTables.USAGE_DAILY, List.of(List.of("bucketId", "snapshotDate")));

   /** Browse and lookup indexes: table to column lists. */
   private static final Map<String, List<List<String>>> INDEXES = Map.of(
      ObjectStorageTables.OBJECT, List.of(List.of("bucketId", "folderId"), List.of("bucketId", "lastModified"), List.of("bucketId", "size"), List.of("bucketId", "contentType"),
         List.of("bucketId", "deleted", "referenceCount"), List.of("bucketId", "headersKnown", "deleted")),
      ObjectStorageTables.OBJECT_REFERENCE, List.of(List.of("bucketId", "objectId"), List.of("bucketId", "missing")),
      ObjectStorageTables.OPERATION, List.of(List.of("bucketId", "occurredAt"), List.of("objectKey", "occurredAt")),
      ObjectStorageTables.FOLDER, List.of(List.of("parentFolderId")),
      ObjectStorageTables.OBJECT_VERSION, List.of(List.of("bucketId", "objectKey")));



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ObjectStorageSchema()
   {
   }



   /*******************************************************************************
    ** The CREATE TABLE and CREATE INDEX statements for every qbit table, in
    ** dependency order, one statement per list entry.
    *******************************************************************************/
   public static List<String> postgresStatements(ObjectStorageQBitConfig config)
   {
      List<String> statements = new ArrayList<>();
      List<String> order = List.of(ObjectStorageTables.CONNECTION, ObjectStorageTables.CONNECTION_CAPABILITY, ObjectStorageTables.BUCKET, ObjectStorageTables.BUCKET_CAPABILITY,
         ObjectStorageTables.FOLDER, ObjectStorageTables.OBJECT, ObjectStorageTables.OBJECT_VERSION, ObjectStorageTables.OBJECT_REFERENCE, ObjectStorageTables.HEADER_RULE,
         ObjectStorageTables.PROTECTION_RULE, ObjectStorageTables.OPERATION, ObjectStorageTables.USAGE_DAILY);
      List<QTableMetaData> tables = ObjectStorageTables.tables(config);
      for(String name : order)
      {
         for(QTableMetaData table : tables)
         {
            if(table.getName().equals(name))
            {
               statements.add(createTable(table));
               int n = 0;
               for(List<String> columns : INDEXES.getOrDefault(name, List.of()))
               {
                  n++;
                  statements.add("CREATE INDEX IF NOT EXISTS ix_" + name + "_" + n + " ON " + name + " (" + String.join(", ", columns.stream().map(ObjectStorageSchema::column).toList()) + ")");
               }
            }
         }
      }
      return (statements);
   }



   /*******************************************************************************
    ** The statements as one script.
    *******************************************************************************/
   public static String postgresScript(ObjectStorageQBitConfig config)
   {
      return (String.join(";\n", postgresStatements(config)) + ";\n");
   }



   /*******************************************************************************
    ** One table.
    *******************************************************************************/
   private static String createTable(QTableMetaData table)
   {
      List<String> lines = new ArrayList<>();
      for(QFieldMetaData field : table.getFields().values())
      {
         if("id".equals(field.getName()))
         {
            lines.add("   id INTEGER GENERATED BY DEFAULT AS IDENTITY NOT NULL");
            continue;
         }
         lines.add("   " + column(field.getName()) + " " + sqlType(field) + (Boolean.TRUE.equals(field.getIsRequired()) ? " NOT NULL" : ""));
      }
      lines.add("   CONSTRAINT pk_" + table.getName() + " PRIMARY KEY (id)");
      int n = 0;
      for(List<String> columns : UNIQUES.getOrDefault(table.getName(), List.of()))
      {
         n++;
         lines.add("   CONSTRAINT uq_" + table.getName() + "_" + n + " UNIQUE (" + String.join(", ", columns.stream().map(ObjectStorageSchema::column).toList()) + ")");
      }
      return ("CREATE TABLE IF NOT EXISTS " + table.getName() + " (\n" + String.join(",\n", lines) + "\n)");
   }



   /*******************************************************************************
    ** The snake_case column of a field name.
    *******************************************************************************/
   static String column(String fieldName)
   {
      return (QInstanceEnricher.inferBackendName(fieldName));
   }



   /*******************************************************************************
    ** The PostgreSQL type of a field.
    *******************************************************************************/
   private static String sqlType(QFieldMetaData field)
   {
      return (switch(field.getType())
      {
         case INTEGER -> "INTEGER";
         case LONG -> "BIGINT";
         case BOOLEAN -> "BOOLEAN";
         case DATE -> "DATE";
         case DATE_TIME -> "TIMESTAMP WITHOUT TIME ZONE";
         case TEXT, HTML -> "TEXT";
         case DECIMAL -> "NUMERIC(18,4)";
         default -> "VARCHAR(" + (field.getMaxLength() == null ? 250 : field.getMaxLength()) + ")";
      });
   }
}
