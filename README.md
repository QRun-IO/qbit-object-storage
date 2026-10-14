# QBit Object Storage

[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/java-21+-blue.svg)](https://adoptium.net/)

> QQQ QBit: S3-compatible object storage service and Admin -> Storage app (browse, upload, presigned links, permission-checked and logged writes).

S3-compatible storage as a capability for any QQQ app, and an admin app to run it. Design: [QRun-IO/qqq#1089](https://github.com/QRun-IO/qqq/pull/1089).

1. **The service.** One Java API (`ObjectStorage.bucket("files")`) for put, get, head, copy, delete, restore, list, header and metadata changes, presigned upload and download URLs, public URLs and key building, over configured connections and buckets. Works against AWS S3, MinIO, Cloudflare R2, Backblaze B2, Wasabi, DigitalOcean Spaces, Ceph RGW and Garage. A capability model, filled in by a **Test connection** probe, says what each provider can do.
2. **The Storage app** (`Admin -> Storage`, optional). Buckets, objects, folders, connections, operations, header rules and protection rules as standard QQQ tables, an index of every indexed bucket, a home with real numbers, and standard Review/Result actions for every write.

**Status:** P1 (service), P2 (index, references, orphaned and missing objects, audit log) and P3/P4 (the app, read and write) are implemented. Not yet released: see [CHANGELOG.md](CHANGELOG.md).

## Requirements

- Java 21.
- QQQ `4.1.0-RC.5` (released BOM), including `qqq-backend-module-filesystem`. `ConnectionRuntime` maps connection settings to core's `S3BackendMetaData` and uses `S3ClientFactory.buildClient`; core owns HTTP, TLS and checksum setup. The unused SFTP/SSH dependency is excluded. See the [dependency diff](docs/S3-FACTORY-MIGRATION.md).
- A QQQ backend for the qbit's tables (PostgreSQL is the intended store). `ObjectStorageSchema.postgresScript(config)` generates the DDL from the same metadata the app runs on, for your Liquibase or Flyway migration.

## Install

```java
new ObjectStorageQBitProducer()
   .withQBitConfig(new ObjectStorageQBitConfig()
      .withBackendName("rdbms")
      .withConnection(new StorageConnectionConfig("primary")
         .withProviderType(ProviderType.MINIO)
         .withEndpoint("${env.S3_ENDPOINT}")
         .withPresignEndpoint("${env.S3_PRESIGN_ENDPOINT}")      // optional: the host clients reach the store by
         .withRegion("${env.S3_REGION}")
         .withAddressingStyle(AddressingStyle.PATH)
         .withAccessKeyRef("${env.S3_ACCESS_KEY}")
         .withSecretKeyRef("${env.S3_SECRET_KEY}")
         .withPresignAccessKeyRef("${env.S3_PRESIGN_ACCESS_KEY}") // a least-privilege key for presigned URLs
         .withPresignSecretKeyRef("${env.S3_PRESIGN_SECRET_KEY}"))
      .withBucket(new StorageBucketConfig("files").withConnection("primary").withBucketName("${env.S3_BUCKET_NAME}")
         .withRole(BucketRole.PUBLIC).withPublicBaseUrl("${env.PUBLIC_BASE_URL}").withIndexed(true).withReferenceTracked(true))
      .withBucket(new StorageBucketConfig("incoming").withConnection("primary").withBucketName("${env.INCOMING_BUCKET}")
         .withRole(BucketRole.INCOMING).withIndexed(false))
      .withReferenceProvider(FieldReferenceProvider.keyPrefix("files", "media", "storageKey"))
      .withAdminApp(true).withParentAppName("admin"))
   .produce(qInstance);
```

Connections and buckets come from this code only. Secrets are **references** (`${env.X}`, `${prop.X}`, `${file.PATH}`) that must match `allowedSecretRefPatterns` (default `env.S3_*`, `env.STORAGE_*`, `file./var/run/secrets/storage/*`); no table stores a secret value. At startup the qbit upserts one `object_storage_connection` and one `object_storage_bucket` row per entry, so the tables always show the real configuration.

Client settings take effect when the qbit configuration is reinstalled or the runtime is reset. Presigning keeps its separate public endpoint and credentials. Retry behavior now follows core/SDK defaults; hosts requiring adaptive retries can set `AWS_RETRY_MODE=adaptive` or `aws.retryMode=adaptive`. Static session tokens remain supported through a small local bridge pending core support.

## Use

```java
StorageBucket files = ObjectStorage.bucket("files");
files.put(PutRequest.of("media/42/hero.jpg", bytes).withContentType("image/jpeg"));   // headers come from the header policy
URL link = files.presignGet(PresignGet.of("media/42/hero.jpg").ttl(Duration.ofMinutes(5)));
String publicUrl = files.publicUrl("media/42/hero.jpg");
files.delete(DeleteRequest.of("media/42/hero.jpg"));                                  // soft where the bucket allows it

// a calling qbit that checked its own permission (for example backups):
ObjectStorage.asSystem("backups", runId).bucket("backups").put(PutRequest.of(key, stream).withObjectLock(LockMode.COMPLIANCE, until));
```

Every call is synchronous, rate limited per connection, throws `StorageException` with a stable reason (`NOT_FOUND`, `ACCESS_DENIED`, `UNSUPPORTED`, `PRECONDITION_FAILED`, `THROTTLED`, `PROVIDER_ERROR`) and the provider's request id, and every write lands in `object_storage_operation`. Other qbits plug in with `ObjectHeaderPolicy`, `PublicUrlResolver`, `StorageChangeListener`, `StorageReferenceProvider` and `StorageAlertSink` (a no-op unless the Alerts qbit is installed).

## What is in the app

| Where | What |
|---|---|
| Home | Buckets, data health (orphaned and missing objects), recent changes, cache-header coverage, usage trend, connections. Each section says what is missing when it is empty. |
| Tables | `object_storage_bucket`, `_object`, `_folder`, `_connection`, `_operation`, `_header_rule`, `_protection_rule` in the nav; capabilities, versions, references and daily usage appear as child lists on their parents. |
| Bucket actions | Rescan bucket, Check configuration, Apply header policy, Create upload link |
| Object actions | Refresh from bucket, Apply header policy, Change headers and metadata, Edit tags, Copy objects, Move or rename objects, Delete objects, Restore objects, Create download link |
| Folder actions | Move or rename folder, Delete folder contents, Upload files to this folder |
| Table actions | Upload files |
| Connection actions | Test connection, Test connection with write tests |

Every write is a standard streamed ETL: **Review** (the dry run, with summary lines) then **Result**. Deletes are soft wherever the bucket has versioning or a private trash (Restore undoes them); referenced, protected and object-locked objects are skipped and the Review says why; bulk runs are capped at 10,000 objects unless the user has `object_storage.admin`.

Permissions: `object_storage.view`, `.write`, `.delete`, `.admin` (admin implies the rest). Per-bucket scoping uses record security locks on the `object_storage_bucket` and `object_storage_connection` security key types (switch off with `withBucketScoping(false)` for a single-administrator app).

## Setup and starter defaults

The Storage home has a **Setup** card: connections and buckets (declared in code, synced into their tables), header rules, and protection rules, each with its state and next step. Every table has help (`help/qbit-object-storage.yaml`) saying what it is for and how its rows get created, which also shows when the table is empty.

The jar ships one optional seed changelog. After the changesets that create the tables (the script from `ObjectStorageSchema.postgresScript`), include it from your master changelog:

```yaml
- include: { file: db/liquibase/qbit-object-storage/object-storage-defaults.changelog.sql }
```

It is insert-only and inserts a **disabled** generic header rule, `default-mutable` (every key, `public, max-age=3600`, priority 1000), so nothing changes on deploy: enable and edit it when you want it. Edits survive re-runs. Protection rules are not seeded (a rule needs a bucket). Presign limits on the bucket row are read at runtime: editing `maxPresignGetSeconds` or `maxPresignPutSeconds` caps every new presigned link. `object_storage_object_version` is reserved and not used yet.

## Deployment notes

- **Uploads** go through the app server (100 MB per file by default, per bucket `maxUploadBytes`), using QQQ's standard file-upload field, so the app needs `uploadedFileArchiveTableName` set in its Javalin metadata, as for any file upload.
- **Previews** are short-lived (5 minute) presigned GETs, so the Next dashboard's `img-src` must allow the storage host (it allows `https:` by default). Video, audio and PDF previews need same-origin media (the dashboard's `media-src` and `frame-src` are `'self'`); a same-origin download route is the planned follow-up. Until then those files show the file card with download and open links.
- **Scheduler.** Set `withSchedulerName(...)` to run the nightly scan, HEAD backlog, reference scan, usage snapshot and trash purge. Without it, run Rescan bucket by hand.
- **Alerts.** With the Alerts qbit installed, bind a `StorageAlertSink` adapter (`ObjectStorageRuntime.setAlertSink`); without it every condition is still visible on the home and in the tables.

## Conformance

`ObjectStorageConformanceSuite` runs the service against a store through Testcontainers on every PR: **MinIO** (the digest-pinned Chainguard build the QQQ tests use, since Docker Hub's `minio/minio` is gone) and **Garage** (digest-pinned). The index and the actions also run on **PostgreSQL**. AWS S3, R2 and B2 run from an owner-run workflow with scratch buckets; Wasabi, Spaces and Ceph results are community reported. See the design for the provider matrix.

## Demo server

`src/test/java/.../devserver/StorageDevServer` starts MinIO and an in-memory QQQ instance with generated images, header rules, references, an index and some history, and serves the Next dashboard, for looking at the app in a browser.

## Naming

Tables are `object_storage_<name>` (lowercase snake_case, backend name equal to the table name). Permissions are `object_storage.<action>`.

## Build

```bash
./mvnw verify
```

Always use the Maven wrapper (Maven 3.9.11); Maven 3.10 cannot read the parent POM. The tests need Docker (Testcontainers); in CI a missing Docker fails the build instead of skipping.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
