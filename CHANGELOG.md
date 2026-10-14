# Changelog

All notable changes to this project are documented here. The format follows Keep a Changelog; versions follow semantic versioning.

## [0.1.0] - Unreleased

### Added
- Every table explains itself: a help bundle (`help/qbit-object-storage.yaml`, QQQ help-bundle format) says what each `object_storage_*` table is for and how its rows get created; it shows on the table screen and when the table is empty. Applied by `ObjectStorageHelp` (STAND-IN for QRun-IO/qqq#1198); a host's own help is kept.
- A Setup card on the Storage home (connections, buckets, header rules, protection rules) with the state and next step of each, resolved the way the runtime resolves them.
- A starter changelog `db/liquibase/qbit-object-storage/object-storage-defaults.changelog.sql`: a disabled `default-mutable` header rule. Insert-only, run through real Liquibase in `DefaultsChangelogTest`.
- `ObjectStorageTablesGuardrailTest` (each table is used at runtime, seeded or explained, and has help).
- The presign limits on the bucket row (`maxPresignGetSeconds`, `maxPresignPutSeconds`) are now read at runtime (`ConfigSync.effectiveMaxPresignGetSeconds` / `Put`) as a lower cap: the effective limit is the smaller of the code config and the row. It applies to presigned GET, PUT, POST, previews and the Create download link action. Previously the row value was never read.
- `object_storage_object_version` is documented as reserved and not used yet (the indexer lists current versions only).
- `StorageBucket.maxUploadBytes()` in the `api` package: the bucket's upload limit in bytes (null when none), so other qbits need not call `config()`, which returns an internal type.
- **The service (P1).** `ObjectStorage.bucket(alias)` and `ObjectStorage.asSystem(caller, runId)`: put (multipart above the part size), get with ranges, head, copy (in parts for very large objects), header and metadata changes, delete (delete marker, private trash, or permanent), restore, list, presigned GET, PUT and POST, public URLs, tags. Key building and URL encoding, the header policy (`object_storage_header_rule`), `PublicUrlResolver`, `StorageChangeListener`, `StorageReferenceProvider`, `StorageAlertSink`.
- Configuration in code, upserted into visible `object_storage_*` tables; secret references with an allow-list; per-connection rate limits; per-bucket and per-connection record security locks.
- The capability model and the Test connection probe (read-only by default, write tests on request, clock skew, presign host), with seeded provider defaults.
- The `object_storage_operation` audit log (presigned URLs are logged without their signature) and protection rules (`READ_ONLY`, `NO_DELETE`, `WARN`, with allowed system callers).
- Backups follow-ups SF1 to SF5: `BucketRole.BACKUP`, object lock on put, system-caller permanent delete, probes that skip locked backup buckets, and the system-caller context.
- **The index (P2).** `object_storage_object` and `object_storage_folder` kept in step with each indexed bucket: resumable full scan with a mass-removal safety stop, HEAD backlog, write-through on every service write, soft-deleted rows, folder rollups. Orphaned and missing objects from `FieldReferenceProvider`, daily usage snapshots, quota and freshness alerts, the nightly job.
- **The Storage app (P3, P4).** Admin -> Storage with a responsive home; record screens with sections, child lists and a thumbnail preview; actions Rescan bucket, Check configuration, Refresh from bucket, Apply header policy (replaces app-specific backfills), Change headers and metadata, Edit tags, Copy, Move or rename, Delete, Restore, Create download link, Create upload link, folder rename and delete, Upload files, Test connection; every one a standard Review and Result with a bulk cap.
- `ObjectStorageSchema`: PostgreSQL DDL generated from the table metadata.
- Conformance suite against MinIO and Garage (Testcontainers), service, index and action tests, and the index and actions on PostgreSQL.

### Changed
- Raised the QQQ BOM baseline to released `4.1.0-RC.5` and delegated S3 client construction to core `S3ClientFactory.buildClient`. Removed local HTTP client and trust-manager implementations; existing endpoint, region, addressing, checksum, TLS/CA, credentials and timeout settings remain supported.
- Clients remain owned by each connection runtime and are rebuilt on reset/reinstall. Retry behavior now follows core/SDK defaults instead of forcing `ADAPTIVE_V2`; hosts can select the SDK retry mode through `AWS_RETRY_MODE` or `aws.retryMode`.
- Retained local presigning (including a separate endpoint/key). A request-credentials bridge preserves static session tokens because RC.5 core metadata/factory has no session-token field or credentials-provider hook. This bridge and the existing `${file.PATH}` resolver are marked for removal by RC.7 under issue #10.
- Added the filesystem module with its unused SFTP/SSH stack excluded. Resolved dependencies otherwise only change QQQ RC.4 to RC.5 and jsoup 1.23.1 to 1.23.2; see [dependency details](docs/S3-FACTORY-MIGRATION.md).

### Notes
- Deliberate differences from the design text: Test connection is two actions (read-only, and with write tests) so the write variant can require `object_storage.admin`; a folder record has no subfolder child list (core does not allow a self join as an exposed join); there is no `withTableNamePrefix` (the `object_storage_` prefix is fixed by the naming rule).
