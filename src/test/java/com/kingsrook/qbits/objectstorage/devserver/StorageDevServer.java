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

package com.kingsrook.qbits.objectstorage.devserver;


import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitProducer;
import com.kingsrook.qbits.objectstorage.api.CopyRequest;
import com.kingsrook.qbits.objectstorage.api.DeleteRequest;
import com.kingsrook.qbits.objectstorage.api.FieldReferenceProvider;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaders;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.api.PresignGet;
import com.kingsrook.qbits.objectstorage.api.PutRequest;
import com.kingsrook.qbits.objectstorage.config.AddressingStyle;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.ProviderType;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qbits.objectstorage.index.ObjectIndex;
import com.kingsrook.qbits.objectstorage.index.ReferenceScan;
import com.kingsrook.qbits.objectstorage.index.StorageJobs;
import com.kingsrook.qbits.objectstorage.internal.CapabilityProbe;
import com.kingsrook.qbits.objectstorage.internal.ConfigSync;
import com.kingsrook.qbits.objectstorage.internal.Db;
import com.kingsrook.qbits.objectstorage.internal.ObjectStorageRuntime;
import com.kingsrook.qbits.objectstorage.model.ObjectStorageTables;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;


/*******************************************************************************
 ** A demo server for the Storage app, used to look at it in a browser (and for
 ** the screenshots): an in-memory QQQ instance with the qbit installed against a
 ** throwaway MinIO (Testcontainers), a few buckets, generated images, header
 ** rules, references, an index and some history. Not shipped; lives in src/test.
 **
 ** Run (from the repository root, after building the test classes):
 **   java -Dqqq.javalin.frontend=next -Dos.dev.port=8099 -cp "target/test-classes;target/classes;DEPS;EXPORT" ...StorageDevServer
 ** where EXPORT is a classpath folder that holds a Next export as next-dashboard/.
 *******************************************************************************/
public class StorageDevServer extends AbstractQQQApplication
{
   private static QInstance instance;

   private static String minioEndpoint;



   /*******************************************************************************
    ** The demo instance (built once in main).
    *******************************************************************************/
   @Override
   public QInstance defineQInstance()
   {
      return (instance);
   }



   /*******************************************************************************
    ** Start MinIO, build and seed the instance, start the server.
    *******************************************************************************/
   public static void main(String[] args) throws Exception
   {
      int port = Integer.parseInt(System.getProperty("os.dev.port", "8099"));

      String endpoint = System.getProperty("os.dev.minio");
      if(endpoint == null)
      {
         GenericContainer<?> minio = new GenericContainer<>(System.getProperty("qqq.test.minioImage", "cgr.dev/chainguard/minio@sha256:dc455cdd4db6e390b1b26514f15ff37681a777bf8dab41aac445a0259f751e56"))
            .withEnv("MINIO_ROOT_USER", "devaccess").withEnv("MINIO_ROOT_PASSWORD", "devsecretkey").withCommand("server", "/tmp/minio-data").withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).withStartupTimeout(Duration.ofMinutes(2)));
         minio.start();
         endpoint = "http://localhost:" + minio.getMappedPort(9000);
      }
      minioEndpoint = endpoint;
      createBuckets();

      System.setProperty("os.dev.endpoint", endpoint);
      System.setProperty("os.dev.ak", "devaccess");
      System.setProperty("os.dev.sk", "devsecretkey");
      instance = buildInstance();

      QContext.init(instance, new QSession());
      ConfigSync.sync();
      seed();

      String imageSource = endpoint;
      QApplicationJavalinServer server = new QApplicationJavalinServer(new StorageDevServer()).withPort(port).withServeFrontendNext(true)
         .withNextDashboardSecurityHeadersCustomizer(headers -> headers.withSources("img-src", imageSource));
      server.start();
      System.out.println("STORAGE DEV SERVER READY http://localhost:" + port + "/  (MinIO at " + endpoint + ")");
      Thread.currentThread().join();
   }



   /*******************************************************************************
    ** The QQQ instance: an in-memory backend, a small "media" table that points at
    ** objects, and the Storage qbit.
    *******************************************************************************/
   static QInstance buildInstance() throws Exception
   {
      QInstance qInstance = new QInstance();
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      qInstance.addBackend(new QBackendMetaData().withName("memory").withBackendType(MemoryBackendModule.class));
      qInstance.addTable(new QTableMetaData().withName("media").withLabel("Media").withBackendName("memory").withPrimaryKeyField("id").withRecordLabelFormat("%s").withRecordLabelFields("title")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER)).withField(new QFieldMetaData("title", QFieldType.STRING)).withField(new QFieldMetaData("storageKey", QFieldType.STRING)).withField(new QFieldMetaData("thumbKey", QFieldType.STRING)));
      qInstance.addApp(new QAppMetaData().withName("admin").withLabel("Admin")
         .withSection(new QAppSection().withName("data").withLabel("Data").withTables(List.of("media"))));

      ObjectStorageQBitConfig config = new ObjectStorageQBitConfig()
         .withBackendName("memory").withBucketScoping(false).withOpenWhenAnonymous(true).withAdminApp(true).withParentAppName("admin")
         .withAllowedSecretRefPatterns(List.of("prop.os.dev.*"))
         .withConnection(new StorageConnectionConfig("primary").withProviderType(ProviderType.MINIO).withEndpoint("${prop.os.dev.endpoint}").withRegion("us-east-1")
            .withAddressingStyle(AddressingStyle.PATH).withAccessKeyRef("${prop.os.dev.ak}").withSecretKeyRef("${prop.os.dev.sk}")
            .withPresignEndpoint(minioEndpoint).withMaxOpsPerSecond(500).withMaxConcurrency(8))
         .withBucket(new StorageBucketConfig("media").withConnection("primary").withBucketName("dev-media").withRole(BucketRole.PUBLIC).withPublicBaseUrl(minioEndpoint + "/dev-media")
            .withIndexed(true).withReferenceTracked(true).withExpectVersioning(true).withExemptPrefixes(new ArrayList<>(List.of("tools/"))))
         .withBucket(new StorageBucketConfig("incoming").withConnection("primary").withBucketName("dev-incoming").withRole(BucketRole.INCOMING).withIndexed(false))
         .withBucket(new StorageBucketConfig("documents").withConnection("primary").withBucketName("dev-documents").withRole(BucketRole.PRIVATE).withIndexed(true)
            .withTrashEnabled(true).withTrashBucket("trash"))
         .withBucket(new StorageBucketConfig("trash").withConnection("primary").withBucketName("dev-trash").withRole(BucketRole.PRIVATE).withIndexed(false))
         .withBucket(new StorageBucketConfig("backups").withConnection("primary").withBucketName("dev-backups").withRole(BucketRole.BACKUP).withIndexed(false))
         .withReferenceProvider(FieldReferenceProvider.key("media", "media", "storageKey")).withReferenceProvider(FieldReferenceProvider.key("media", "media", "thumbKey"));
      new ObjectStorageQBitProducer().withQBitConfig(config).produce(qInstance);
      return (qInstance);
   }



   /*******************************************************************************
    ** Create the buckets with the raw client (the app never creates buckets).
    *******************************************************************************/
   private static void createBuckets()
   {
      try(S3Client s3 = S3Client.builder().endpointOverride(java.net.URI.create(minioEndpoint)).region(Region.US_EAST_1).forcePathStyle(true)
         .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("devaccess", "devsecretkey")))
         .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED).responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED).build())
      {
         for(String bucket : new String[] { "dev-media", "dev-incoming", "dev-documents", "dev-trash" })
         {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
         }
         s3.putBucketVersioning(b -> b.bucket("dev-media").versioningConfiguration(VersioningConfiguration.builder().status(BucketVersioningStatus.ENABLED).build()));
         s3.createBucket(CreateBucketRequest.builder().bucket("dev-backups").objectLockEnabledForBucket(true).build());
      }
   }



   /*******************************************************************************
    ** A generated picture (so the thumbnails are real images).
    *******************************************************************************/
   private static byte[] picture(int w, int h, Color a, Color b, String label, String format) throws Exception
   {
      BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics2D    g     = image.createGraphics();
      g.setPaint(new GradientPaint(0, 0, a, w, h, b));
      g.fillRect(0, 0, w, h);
      g.setColor(new Color(255, 255, 255, 220));
      g.setFont(new Font("SansSerif", Font.BOLD, Math.max(14, w / 12)));
      g.drawString(label, w / 12, h / 2);
      g.dispose();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, format, out);
      return (out.toByteArray());
   }



   /*******************************************************************************
    ** Demo data: media, documents, header rules, references, history, an index.
    *******************************************************************************/
   private static void seed() throws Exception
   {
      QContext.setQSession(new QSession().withUser(new com.kingsrook.qqq.backend.core.model.session.QUser().withIdReference("james@kof22.com")));

      Db.insert(ObjectStorageTables.HEADER_RULE, new QRecord().withValue("name", "Content-addressed media").withValue("keyRegex", ".*/[0-9a-f]{8}-.*")
         .withValue("cacheControl", "public, max-age=31536000, immutable").withValue("priority", 10).withValue("enabled", true));
      Db.insert(ObjectStorageTables.HEADER_RULE, new QRecord().withValue("name", "Images").withValue("contentTypePattern", "image/*").withValue("cacheControl", "public, max-age=86400").withValue("priority", 50).withValue("enabled", true));
      Db.insert(ObjectStorageTables.PROTECTION_RULE, new QRecord().withValue("bucketId", ConfigSync.bucketId("media")).withValue("prefixGlob", "legal/*").withValue("mode", "NO_DELETE")
         .withValue("reason", "Retained for legal review").withValue("enabled", true));

      // objects written outside QQQ first (so the scan has something to find), then through the service
      try(S3Client raw = S3Client.builder().endpointOverride(java.net.URI.create(minioEndpoint)).region(Region.US_EAST_1).forcePathStyle(true)
         .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("devaccess", "devsecretkey")))
         .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED).responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED).build())
      {
         Color[][] palette = { { new Color(0x2563eb), new Color(0x7c3aed) }, { new Color(0x059669), new Color(0x84cc16) }, { new Color(0xea580c), new Color(0xdb2777) },
            { new Color(0x0891b2), new Color(0x1d4ed8) }, { new Color(0x4f46e5), new Color(0x06b6d4) }, { new Color(0xb91c1c), new Color(0xf59e0b) } };
         String[] names = { "harbor-sunrise", "forest-trail", "city-lights", "alpine-lake", "desert-dunes", "coast-storm" };
         for(int idx = 0; idx < names.length; idx++)
         {
            final int i = idx;
            String hash = String.format("%08x", names[i].hashCode());
            byte[] original = picture(1600, 900, palette[i][0], palette[i][1], names[i], "jpg");
            byte[] small    = picture(480, 270, palette[i][0], palette[i][1], names[i], "png");
            raw.putObject(b -> b.bucket("dev-media").key("photos/2026/" + hash + "-" + names[i] + ".jpg").contentType("image/jpeg"), software.amazon.awssdk.core.sync.RequestBody.fromBytes(original));
            raw.putObject(b -> b.bucket("dev-media").key("photos/2026/thumbs/" + hash + "-" + names[i] + ".png").contentType("image/png"), software.amazon.awssdk.core.sync.RequestBody.fromBytes(small));
         }
         raw.putObject(b -> b.bucket("dev-media").key("photos/legacy/IMG_0042.jpg").contentType("image/jpeg"), software.amazon.awssdk.core.sync.RequestBody.fromBytes(picture(1200, 800, Color.DARK_GRAY, Color.GRAY, "legacy", "jpg")));
         raw.putObject(b -> b.bucket("dev-media").key("tools/import-notes.txt").contentType("text/plain"), software.amazon.awssdk.core.sync.RequestBody.fromString("Internal import notes\n"));
         raw.putObject(b -> b.bucket("dev-media").key("legal/consent-template.txt").contentType("text/plain"), software.amazon.awssdk.core.sync.RequestBody.fromString("Consent text\n"));
         raw.putObject(b -> b.bucket("dev-documents").key("reports/2026-09-summary.txt").contentType("text/plain"), software.amazon.awssdk.core.sync.RequestBody.fromString("September summary\n".repeat(40)));
         raw.putObject(b -> b.bucket("dev-documents").key("reports/2026-10-summary.txt").contentType("text/plain"), software.amazon.awssdk.core.sync.RequestBody.fromString("October summary\n".repeat(60)));
         raw.putObject(b -> b.bucket("dev-documents").key("contracts/vendor-agreement.txt").contentType("text/plain"), software.amazon.awssdk.core.sync.RequestBody.fromString("Agreement\n".repeat(200)));
      }

      // media records point at the photos and their thumbnails; one record points at an object that is not there
      String[] titles = { "Harbor at sunrise", "Forest trail", "City lights", "Alpine lake", "Desert dunes", "Coast storm" };
      String[] slugs  = { "harbor-sunrise", "forest-trail", "city-lights", "alpine-lake", "desert-dunes", "coast-storm" };
      List<QRecord> mediaRows = new ArrayList<>();
      for(int i = 0; i < slugs.length; i++)
      {
         String hash = String.format("%08x", slugs[i].hashCode());
         mediaRows.add(new QRecord().withValue("title", titles[i]).withValue("storageKey", "photos/2026/" + hash + "-" + slugs[i] + ".jpg")
            .withValue("thumbKey", "photos/2026/thumbs/" + hash + "-" + slugs[i] + ".png"));
      }
      mediaRows.add(new QRecord().withValue("title", "Old launch banner").withValue("storageKey", "photos/2025/launch-banner.jpg"));
      Db.insert("media", mediaRows);

      // yesterday's usage, so the trend has two points
      ObjectIndex.scan("media");
      ObjectIndex.scan("documents");
      Db.update(ObjectStorageTables.BUCKET, List.of(new QRecord().withValue("id", ConfigSync.bucketId("media")).withValue("orphanCountAlertThreshold", 2)));

      // some history through the service: uploads, a copy, a soft delete, a presign
      ObjectStorage.bucket("media").put(PutRequest.of("photos/2026/" + String.format("%08x", "new-banner".hashCode()) + "-new-banner.jpg", picture(1600, 900, new Color(0x0f766e), new Color(0x1e3a8a), "new banner", "jpg"))
         .withContentType("image/jpeg"));
      ObjectStorage.bucket("media").copy(CopyRequest.of("photos/legacy/IMG_0042.jpg", "photos/legacy/IMG_0042-copy.jpg"));
      ObjectStorage.bucket("media").updateHeaders("photos/legacy/IMG_0042.jpg", ObjectHeaders.cacheControl("public, max-age=60"));
      ObjectStorage.bucket("documents").put(PutRequest.of("reports/draft-notes.txt", "Draft\n".getBytes(StandardCharsets.UTF_8)));
      ObjectStorage.bucket("documents").delete(DeleteRequest.of("reports/draft-notes.txt"));
      ObjectStorage.bucket("media").presignGet(PresignGet.of("photos/legacy/IMG_0042.jpg"));
      ObjectStorage.asSystem("backups", "run 41").bucket("backups").put(PutRequest.of("2026-10-11/db.dump", new byte[2048]));

      // the index: headers, references, usage (two days), connection test
      ObjectIndex.headBacklog("media", 1000);
      ObjectIndex.headBacklog("documents", 1000);
      ReferenceScan.run("media");
      StorageJobs.usageSnapshot("media");
      StorageJobs.usageSnapshot("documents");
      for(String alias : new String[] { "media", "documents" })
      {
         QRecord today = Db.first(ObjectStorageTables.USAGE_DAILY, "bucketId", ConfigSync.bucketId(alias));
         Db.insert(ObjectStorageTables.USAGE_DAILY, new QRecord().withValue("bucketId", ConfigSync.bucketId(alias)).withValue("snapshotDate", LocalDate.now(ZoneOffset.UTC).minusDays(29))
            .withValue("objectCount", Math.max(0, today.getValueLong("objectCount") - 5)).withValue("totalBytes", Math.max(0, today.getValueLong("totalBytes") - 600_000)).withValue("byPrefix", "{}"));
      }
      CapabilityProbe.Report report = CapabilityProbe.probeConnection("primary", true);
      QRecord connection = Db.first(ObjectStorageTables.CONNECTION, "name", "primary");
      Db.update(ObjectStorageTables.CONNECTION, List.of(new QRecord().withValue("id", connection.getValue("id")).withValue("lastTestedAt", Instant.now()).withValue("lastTestStatus", "OK")
         .withValue("lastTestSummary", report.getByBucket().keySet().stream().map(a -> a + ": " + report.summaryFor(a)).reduce((x, y) -> x + "; " + y).orElse(""))
         .withValue("clockSkewSeconds", report.getClockSkewSeconds() == null ? null : report.getClockSkewSeconds().intValue())));
      ObjectStorageRuntime.setClock(Clock.systemUTC());
      QContext.setQSession(new QSession());
      System.out.println("Seeded: " + Db.query(ObjectStorageTables.OBJECT).size() + " objects, " + Db.query(ObjectStorageTables.OPERATION).size() + " operations; " + Map.of("ok", true));
   }
}
