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

package com.kingsrook.qbits.objectstorage.app;


import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** The Storage home: full-width sections, stacked, readable at every width with
 ** no inner horizontal scroll. It is the single place for storage data: every
 ** number comes from the bucket rows, the object index or the operation log,
 ** and every empty state says what is missing and why. Other apps link here;
 ** nothing is duplicated.
 *******************************************************************************/
public final class StorageHome
{
   private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

   private static final String WRAP   = "display:flex;flex-direction:column;gap:1.25rem;width:100%;";
   private static final String CARD   = "border:1px solid rgba(127,127,127,.3);border-radius:8px;padding:1rem;min-width:0;overflow-wrap:anywhere;";
   private static final String GRID   = "display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,19rem),1fr));gap:.75rem;";
   private static final String ITEM   = "border:1px solid rgba(127,127,127,.25);border-radius:6px;padding:.7rem .85rem;min-width:0;overflow-wrap:anywhere;";
   private static final String PAIRS  = "display:grid;grid-template-columns:minmax(6.5rem,auto) minmax(0,1fr);gap:.2rem .75rem;margin:.5rem 0 0;font-size:.88rem;";
   private static final String CHIP   = "display:inline-block;border:1px solid rgba(127,127,127,.45);border-radius:999px;padding:0 .5rem;margin:0 .25rem .25rem 0;font-size:.78rem;";
   private static final String MUTED  = "opacity:.75;";
   private static final String ROW    = "padding:.5rem 0;border-bottom:1px solid rgba(127,127,127,.2);min-width:0;overflow-wrap:anywhere;";
   private static final String HEAD   = "display:flex;justify-content:space-between;gap:.5rem;align-items:baseline;";

   /** Important capabilities whose absence or partial support is worth a chip. */
   private static final Map<String, String> CHIP_NAMES = Map.of("VERSIONING", "versioning", "LIFECYCLE", "lifecycle", "OBJECT_LOCK", "object lock",
      "OBJECT_TAGGING", "tagging", "PRESIGNED_POST", "presigned POST", "CONDITIONAL_WRITE", "conditional writes");



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private StorageHome()
   {
   }



   /*******************************************************************************
    ** Render the whole home, reading the current user's view of the tables (so
    ** per-bucket scoping applies). tablePathPrefix is the prefix of table and
    ** record links.
    *******************************************************************************/
   public static String render(String tablePathPrefix, Instant now) throws QException
   {
      List<QRecord> connections = query(ObjectStorageTables.CONNECTION, new QQueryFilter().withOrderBy(new com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy("name")));
      List<QRecord> buckets     = query(ObjectStorageTables.BUCKET, new QQueryFilter().withOrderBy(new com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy("alias")));
      List<QRecord> caps        = query(ObjectStorageTables.BUCKET_CAPABILITY, new QQueryFilter());
      List<QRecord> operations  = query(ObjectStorageTables.OPERATION, new QQueryFilter().withOrderBy(new com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy("occurredAt", false)).withLimit(20));
      List<QRecord> usage       = query(ObjectStorageTables.USAGE_DAILY, new QQueryFilter().withCriteria(new QFilterCriteria("snapshotDate", QCriteriaOperator.GREATER_THAN_OR_EQUALS,
         LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(30))).withOrderBy(new com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy("snapshotDate")));

      StringBuilder html = new StringBuilder("<div class=\"object-storage-home\" style=\"" + WRAP + "\">");
      bucketsSection(html, buckets, connections, caps, tablePathPrefix);
      healthSection(html, buckets, tablePathPrefix);
      recentSection(html, operations, tablePathPrefix);
      headersSection(html, buckets, tablePathPrefix);
      usageSection(html, buckets, usage);
      connectionsSection(html, connections, tablePathPrefix, now);
      return (html.append("</div>").toString());
   }



   /*******************************************************************************
    ** Query a table (empty list when the user cannot see it).
    *******************************************************************************/
   private static List<QRecord> query(String table, QQueryFilter filter) throws QException
   {
      return (new QueryAction().execute(new QueryInput(table).withFilter(filter)).getRecords());
   }



   /*******************************************************************************
    ** 1. Buckets: one card per bucket, linking to its record. Cards flow into as
    ** many columns as fit, so the section reads at any width without scrolling.
    *******************************************************************************/
   private static void bucketsSection(StringBuilder html, List<QRecord> buckets, List<QRecord> connections, List<QRecord> caps, String prefix)
   {
      open(html, "Buckets");
      if(buckets.isEmpty())
      {
         empty(html, "No buckets are configured. Declare them in the qbit config (withBucket) and restart.");
         close(html);
         return;
      }
      Map<Object, String> connectionNames = new LinkedHashMap<>();
      connections.forEach(c -> connectionNames.put(c.getValue("id"), c.getValueString("name")));
      html.append("<div style=\"").append(GRID).append("\">");
      for(QRecord bucket : buckets)
      {
         boolean indexed = Boolean.TRUE.equals(bucket.getValueBoolean("indexed"));
         html.append("<div style=\"").append(ITEM).append("\"><div style=\"").append(HEAD).append("\"><strong>")
            .append(link(prefix, ObjectStorageTables.BUCKET, bucket.getValue("id"), bucket.getValueString("alias"))).append("</strong><span style=\"").append(MUTED).append("font-size:.8rem;\">")
            .append(esc(bucket.getValueString("role"))).append("</span></div><dl style=\"").append(PAIRS).append("\">");
         pair(html, "Connection", esc(connectionNames.getOrDefault(bucket.getValue("connectionId"), "")));
         pair(html, "Objects", indexed ? number(bucket.getValueLong("objectCount")) : "<span style=\"" + MUTED + "\">not indexed</span>");
         pair(html, "Size", indexed ? bytes(bucket.getValueLong("totalBytes")) : "<span style=\"" + MUTED + "\">not indexed</span>");
         pair(html, "Last scan", bucket.getValueInstant("lastScanDate") == null ? "<span style=\"" + MUTED + "\">never</span>" : WHEN.format(bucket.getValueInstant("lastScanDate")));
         pair(html, "Versioning", esc(bucket.getValueString("versioningState") == null ? "not checked yet" : bucket.getValueString("versioningState")));
         html.append("</dl>");
         StringBuilder chips = new StringBuilder();
         for(QRecord cap : caps)
         {
            String  capability = cap.getValueString("capability");
            boolean relevant   = CHIP_NAMES.containsKey(capability) && (!"OBJECT_LOCK".equals(capability) || "BACKUP".equals(bucket.getValueString("role")));
            if(relevant && cap.getValueInteger("bucketId") != null && cap.getValueInteger("bucketId").equals(bucket.getValueInteger("id")))
            {
               String status = cap.getValueString("status");
               String name   = CHIP_NAMES.get(capability);
               if("UNSUPPORTED".equals(status))
               {
                  chips.append("<span style=\"").append(CHIP).append("\">no ").append(name).append("</span>");
               }
               else if("PARTIAL".equals(status))
               {
                  chips.append("<span style=\"").append(CHIP).append("\">").append(name).append(" partial</span>");
               }
               else if("NOT_PERMITTED".equals(status))
               {
                  chips.append("<span style=\"").append(CHIP).append("\">").append(name).append(": key not permitted</span>");
               }
            }
         }
         if(bucket.getValueString("configDriftNote") != null)
         {
            chips.append("<span style=\"").append(CHIP).append("\">config drift</span>");
         }
         if(chips.length() > 0)
         {
            html.append("<div style=\"margin-top:.55rem;\">").append(chips).append("</div>");
         }
         html.append("</div>");
      }
      html.append("</div>");
      close(html);
   }



   /*******************************************************************************
    ** 2. Data health: orphaned and missing objects per bucket.
    *******************************************************************************/
   private static void healthSection(StringBuilder html, List<QRecord> buckets, String prefix)
   {
      open(html, "Data health");
      List<QRecord> tracked = buckets.stream().filter(b -> Boolean.TRUE.equals(b.getValueBoolean("referenceTracked"))).toList();
      if(tracked.isEmpty())
      {
         empty(html, "No bucket tracks references yet, so orphaned and missing objects cannot be counted. Register a reference provider (for example FieldReferenceProvider) and mark the bucket referenceTracked.");
         close(html);
         return;
      }
      html.append("<div style=\"").append(GRID).append("\">");
      for(QRecord bucket : tracked)
      {
         boolean scanned = bucket.getValueInstant("lastReferenceScanDate") != null;
         String  orphanFilter = filterLink(prefix, ObjectStorageTables.OBJECT, "[{\"fieldName\":\"bucketId\",\"operator\":\"EQUALS\",\"values\":[" + bucket.getValueInteger("id") + "]},"
            + "{\"fieldName\":\"referenceCount\",\"operator\":\"EQUALS\",\"values\":[0]}]");
         String  missingFilter = filterLink(prefix, ObjectStorageTables.OBJECT_REFERENCE, "[{\"fieldName\":\"bucketId\",\"operator\":\"EQUALS\",\"values\":[" + bucket.getValueInteger("id") + "]},"
            + "{\"fieldName\":\"missing\",\"operator\":\"EQUALS\",\"values\":[true]}]");
         html.append("<div style=\"").append(ITEM).append("\"><strong>").append(link(prefix, ObjectStorageTables.BUCKET, bucket.getValue("id"), bucket.getValueString("alias"))).append("</strong><dl style=\"")
            .append(PAIRS).append("\">");
         pair(html, "Orphaned", scanned ? "<a href=\"" + esc(orphanFilter) + "\">" + number(bucket.getValueLong("orphanedCount")) + " objects</a> (" + bytes(bucket.getValueLong("orphanedBytes")) + ")"
            : "<span style=\"" + MUTED + "\">not scanned yet</span>");
         pair(html, "Missing", scanned ? "<a href=\"" + esc(missingFilter) + "\">" + number(bucket.getValueLong("missingCount")) + " objects</a>" : "");
         pair(html, "Last scan", scanned ? WHEN.format(bucket.getValueInstant("lastReferenceScanDate")) : "never");
         html.append("</dl></div>");
      }
      html.append("</div>");
      close(html);
   }



   /*******************************************************************************
    ** 3. Recent changes: the latest 20 operations, linking to the full table.
    *******************************************************************************/
   private static void recentSection(StringBuilder html, List<QRecord> operations, String prefix)
   {
      open(html, "Recent changes");
      if(operations.isEmpty())
      {
         empty(html, "No storage writes have been recorded yet. Every write from the app, an action or a scheduled job lands here.");
         close(html);
         return;
      }
      for(QRecord op : operations)
      {
         html.append("<div style=\"").append(ROW).append("\"><div><strong>").append(link(prefix, ObjectStorageTables.OPERATION, op.getValue("id"), String.valueOf(op.getValueString("action"))))
            .append("</strong> ").append(esc(op.getValueString("bucketAlias") + "/" + (op.getValueString("objectKey") == null ? "" : op.getValueString("objectKey")))).append("</div><div style=\"")
            .append(MUTED).append("font-size:.85rem;\">").append(op.getValueInstant("occurredAt") == null ? "" : WHEN.format(op.getValueInstant("occurredAt"))).append(" &middot; ")
            .append(esc(op.getValueString("actor"))).append(" &middot; ").append(esc(op.getValueString("result")))
            .append(op.getValueString("errorMessage") == null ? "" : ": " + esc(op.getValueString("errorMessage"))).append("</div></div>");
      }
      html.append("<p style=\"margin:.75rem 0 0;\"><a href=\"").append(esc(prefix + "/" + ObjectStorageTables.OPERATION)).append("\">See every operation</a></p>");
      close(html);
   }



   /*******************************************************************************
    ** 4. Cache headers: how many indexed objects match the header policy.
    *******************************************************************************/
   private static void headersSection(StringBuilder html, List<QRecord> buckets, String prefix)
   {
      open(html, "Cache headers");
      List<QRecord> indexed = buckets.stream().filter(b -> Boolean.TRUE.equals(b.getValueBoolean("indexed")) && !"BACKUP".equals(b.getValueString("role"))).toList();
      if(indexed.isEmpty())
      {
         empty(html, "No indexed bucket, so header coverage cannot be measured.");
         close(html);
         return;
      }
      for(QRecord bucket : indexed)
      {
         long total = bucket.getValueLong("objectCount") == null ? 0 : bucket.getValueLong("objectCount");
         long known = bucket.getValueLong("headersKnownCount") == null ? 0 : bucket.getValueLong("headersKnownCount");
         long match = bucket.getValueLong("headersMatchingPolicyCount") == null ? 0 : bucket.getValueLong("headersMatchingPolicyCount");
         html.append("<div style=\"").append(ROW).append("\"><strong>").append(link(prefix, ObjectStorageTables.BUCKET, bucket.getValue("id"), bucket.getValueString("alias"))).append("</strong>: ");
         if(bucket.getValueInstant("lastScanDate") == null)
         {
            html.append("<span style=\"").append(MUTED).append("\">not scanned yet</span>");
         }
         else
         {
            html.append(number(match)).append(" of ").append(number(total)).append(" objects match the header policy; headers known for ")
               .append(total == 0 ? 100 : Math.round(known * 100.0 / total)).append("%");
         }
         html.append("</div>");
      }
      html.append("<p style=\"margin:.75rem 0 0;\">To fix mismatches, run <strong>Apply header policy</strong> on the objects or a bucket. Edge caching lives in the CDN app.</p>");
      close(html);
   }



   /*******************************************************************************
    ** 5. Usage trend: bytes and object count per bucket over 30 days (snapshots).
    *******************************************************************************/
   private static void usageSection(StringBuilder html, List<QRecord> buckets, List<QRecord> usage)
   {
      open(html, "Usage trend");
      Map<Object, List<QRecord>> byBucket = new LinkedHashMap<>();
      for(QRecord row : usage)
      {
         byBucket.computeIfAbsent(row.getValue("bucketId"), k -> new ArrayList<>()).add(row);
      }
      StringBuilder trends     = new StringBuilder();
      StringBuilder collecting = new StringBuilder();
      for(QRecord bucket : buckets)
      {
         List<QRecord> rows = byBucket.getOrDefault(bucket.getValue("id"), List.of());
         if(rows.size() < 2)
         {
            if(!rows.isEmpty())
            {
               collecting.append("<div style=\"").append(ROW).append("\"><strong>").append(esc(bucket.getValueString("alias"))).append("</strong>: collecting since ")
                  .append(rows.get(0).getValueString("snapshotDate")).append("</div>");
            }
            continue;
         }
         QRecord first = rows.get(0);
         QRecord last  = rows.get(rows.size() - 1);
         long delta = (last.getValueLong("totalBytes") == null ? 0 : last.getValueLong("totalBytes")) - (first.getValueLong("totalBytes") == null ? 0 : first.getValueLong("totalBytes"));
         long objs  = (last.getValueLong("objectCount") == null ? 0 : last.getValueLong("objectCount")) - (first.getValueLong("objectCount") == null ? 0 : first.getValueLong("objectCount"));
         trends.append("<div style=\"").append(ITEM).append("\"><strong>").append(esc(bucket.getValueString("alias"))).append("</strong><dl style=\"").append(PAIRS).append("\">");
         pair(trends, "Now", number(last.getValueLong("objectCount")) + " objects, " + bytes(last.getValueLong("totalBytes")));
         pair(trends, "Since " + first.getValueString("snapshotDate"), number(first.getValueLong("objectCount")) + " objects, " + bytes(first.getValueLong("totalBytes")));
         pair(trends, "Change", (objs >= 0 ? "+" : "") + objs + " objects, " + (delta >= 0 ? "+" : "-") + bytes(Math.abs(delta)));
         trends.append("</dl></div>");
      }
      if(trends.length() > 0)
      {
         html.append("<div style=\"").append(GRID).append("\">").append(trends).append("</div>");
      }
      html.append(collecting);
      if(trends.length() == 0 && collecting.length() == 0)
      {
         empty(html, "No usage snapshots yet. The nightly snapshot records bucket size and object count from the index; a trend appears after two days.");
      }
      close(html);
   }



   /*******************************************************************************
    ** 6. Connections: last test and result, with a nudge when never tested or old.
    *******************************************************************************/
   private static void connectionsSection(StringBuilder html, List<QRecord> connections, String prefix, Instant now)
   {
      open(html, "Connections");
      if(connections.isEmpty())
      {
         empty(html, "No connections are configured.");
         close(html);
         return;
      }
      html.append("<div style=\"").append(GRID).append("\">");
      for(QRecord c : connections)
      {
         Instant tested = c.getValueInstant("lastTestedAt");
         String  nudge  = tested == null ? "Never tested: run Test connection on the record."
            : ChronoUnit.DAYS.between(tested, now) > 30 ? "Last tested more than 30 days ago." : "";
         if(c.getValueString("presignWarning") != null)
         {
            nudge = (nudge.isEmpty() ? "" : nudge + " ") + c.getValueString("presignWarning");
         }
         html.append("<div style=\"").append(ITEM).append("\"><div style=\"").append(HEAD).append("\"><strong>")
            .append(link(prefix, ObjectStorageTables.CONNECTION, c.getValue("id"), c.getValueString("name"))).append("</strong><span style=\"").append(MUTED).append("font-size:.8rem;\">")
            .append(esc(c.getValueString("providerType"))).append("</span></div><dl style=\"").append(PAIRS).append("\">");
         pair(html, "Last test", tested == null ? "never" : WHEN.format(tested));
         pair(html, "Result", esc(c.getValueString("lastTestStatus") == null ? "" : c.getValueString("lastTestStatus")));
         if(!nudge.isEmpty())
         {
            pair(html, "Note", esc(nudge));
         }
         html.append("</dl></div>");
      }
      html.append("</div>");
      close(html);
   }



   /*******************************************************************************
    ** One label/value pair in a card.
    *******************************************************************************/
   private static void pair(StringBuilder html, String label, String valueHtml)
   {
      html.append("<dt style=\"").append(MUTED).append("\">").append(esc(label)).append("</dt><dd style=\"margin:0;min-width:0;\">").append(valueHtml).append("</dd>");
   }



   /*******************************************************************************
    ** Open a card.
    *******************************************************************************/
   private static void open(StringBuilder html, String title)
   {
      html.append("<section style=\"").append(CARD).append("\"><h3 style=\"margin:0 0 .75rem;font-size:1.05rem;\">").append(esc(title)).append("</h3>");
   }



   /*******************************************************************************
    ** Close a card.
    *******************************************************************************/
   private static void close(StringBuilder html)
   {
      html.append("</section>");
   }



   /*******************************************************************************
    ** An honest empty state.
    *******************************************************************************/
   private static void empty(StringBuilder html, String text)
   {
      html.append("<p style=\"margin:0;").append(MUTED).append("\">").append(esc(text)).append("</p>");
   }



   /*******************************************************************************
    ** A link to a record.
    *******************************************************************************/
   private static String link(String prefix, String table, Object id, String text)
   {
      return ("<a href=\"" + esc(prefix + "/" + table + "/" + id) + "\">" + esc(text) + "</a>");
   }



   /*******************************************************************************
    ** A link to a table filtered by criteria JSON.
    *******************************************************************************/
   private static String filterLink(String prefix, String table, String criteriaJson)
   {
      String filter = "{\"criteria\":" + criteriaJson + ",\"booleanOperator\":\"AND\"}";
      return (prefix + "/" + table + "?filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8));
   }



   /*******************************************************************************
    ** A count with thousands separators.
    *******************************************************************************/
   private static String number(Long value)
   {
      return (value == null ? "0" : String.format("%,d", value));
   }



   /*******************************************************************************
    ** Bytes as KB/MB/GB.
    *******************************************************************************/
   static String bytes(Long value)
   {
      if(value == null)
      {
         return ("0 B");
      }
      String[]   units = { "B", "KB", "MB", "GB", "TB" };
      BigDecimal v     = new BigDecimal(value);
      int        unit  = 0;
      while(v.compareTo(BigDecimal.valueOf(1024)) >= 0 && unit < units.length - 1)
      {
         v = v.divide(BigDecimal.valueOf(1024), 4, java.math.RoundingMode.HALF_UP);
         unit++;
      }
      return (unit == 0 ? value + " B" : v.setScale(1, java.math.RoundingMode.HALF_UP) + " " + units[unit]);
   }



   /*******************************************************************************
    ** HTML-escape.
    *******************************************************************************/
   private static String esc(String s)
   {
      return (s == null ? "" : ValueUtils.getValueAsString(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"));
   }
}
