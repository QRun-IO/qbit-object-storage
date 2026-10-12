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

package com.kingsrook.qbits.objectstorage.api;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** A reference provider that needs no code: it reads a table field that holds an
 ** object key, a key prefix (for example a set of renditions), or a public URL
 ** (parsed back to bucket and key with the PublicUrlResolver).
 **
 ** <pre>
 **   .withReferenceProvider(FieldReferenceProvider.keyPrefix("files", "media", "storageKey"))
 ** </pre>
 *******************************************************************************/
public class FieldReferenceProvider implements StorageReferenceProvider
{
   /*******************************************************************************
    ** What the field holds.
    *******************************************************************************/
   private enum Holds
   {
      KEY,
      KEY_PREFIX,
      URL
   }

   private final String bucketAlias;
   private final String tableName;
   private final String fieldName;
   private final Holds  holds;
   private String       labelField;



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   private FieldReferenceProvider(String bucketAlias, String tableName, String fieldName, Holds holds)
   {
      this.bucketAlias = bucketAlias;
      this.tableName = tableName;
      this.fieldName = fieldName;
      this.holds = holds;
   }



   /*******************************************************************************
    ** The field holds one object key.
    *******************************************************************************/
   public static FieldReferenceProvider key(String bucketAlias, String tableName, String fieldName)
   {
      return (new FieldReferenceProvider(bucketAlias, tableName, fieldName, Holds.KEY));
   }



   /*******************************************************************************
    ** The field holds a key prefix: every object under it is referenced.
    *******************************************************************************/
   public static FieldReferenceProvider keyPrefix(String bucketAlias, String tableName, String fieldName)
   {
      return (new FieldReferenceProvider(bucketAlias, tableName, fieldName, Holds.KEY_PREFIX));
   }



   /*******************************************************************************
    ** The field holds a public URL.
    *******************************************************************************/
   public static FieldReferenceProvider url(String bucketAlias, String tableName, String fieldName)
   {
      return (new FieldReferenceProvider(bucketAlias, tableName, fieldName, Holds.URL));
   }



   /*******************************************************************************
    ** Use another field as the label of the referencing record (default: the table
    ** name and id).
    *******************************************************************************/
   public FieldReferenceProvider withLabelField(String labelField)
   {
      this.labelField = labelField;
      return (this);
   }



   /*******************************************************************************
    ** Every reference this provider knows of in a bucket.
    *******************************************************************************/
   @Override
   public Stream<StorageReference> references(String alias) throws QException
   {
      if(!bucketAlias.equals(alias) && holds != Holds.URL)
      {
         return (Stream.empty());
      }
      List<StorageReference> references = new ArrayList<>();
      QQueryFilter filter = new QQueryFilter(new QFilterCriteria(fieldName, QCriteriaOperator.IS_NOT_BLANK));
      Db.forEachPage(tableName, filter, 2000, rows ->
      {
         for(QRecord row : rows)
         {
            String value = row.getValueString(fieldName);
            if(!StringUtils.hasContent(value))
            {
               continue;
            }
            String label = labelField != null && row.getValueString(labelField) != null ? row.getValueString(labelField) : tableName + " #" + row.getValue("id");
            if(holds == Holds.URL)
            {
               Optional<BucketKey> parsed = ObjectStorageRuntime.publicUrlResolver().parse(value);
               if(parsed.isPresent() && parsed.get().getBucketAlias().equals(alias))
               {
                  references.add(new StorageReference().withBucketAlias(alias).withKeyOrPrefix(parsed.get().getKey()).withMatchMode(MatchMode.KEY).withTableName(tableName)
                     .withRecordId(String.valueOf(row.getValue("id"))).withFieldName(fieldName).withLabel(label));
               }
            }
            else
            {
               references.add(new StorageReference().withBucketAlias(alias).withKeyOrPrefix(StorageKeys.normalizePrefix(value)).withMatchMode(holds == Holds.KEY_PREFIX ? MatchMode.PREFIX : MatchMode.KEY)
                  .withTableName(tableName).withRecordId(String.valueOf(row.getValue("id"))).withFieldName(fieldName).withLabel(label));
            }
         }
      });
      return (references.stream());
   }
}
