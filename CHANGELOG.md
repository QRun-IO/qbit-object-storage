# Changelog

All notable changes to this project are documented here. The format follows Keep a Changelog; versions follow semantic versioning.

## [0.1.0] - Unreleased

### Added
- **The service (P1).** `ObjectStorage.bucket(alias)` and `ObjectStorage.asSystem(caller, runId)`: put (multipart above the part size), get with ranges, head, copy (in parts for very large objects), header and metadata changes, delete (delete marker, private trash, or permanent), restore, list, presigned GET, PUT and POST, public URLs, tags. Key building and URL encoding, the header policy (`object_storage_header_rule`), `PublicUrlResolver`, `StorageChangeListener`, `StorageReferenceProvider`, `StorageAlertSink`.
- Configuration in code, upserted into visible `object_storage_*` tables; secret references with an allow-list; per-connection rate limits; per-bucket and per-connection record security locks.
- The capability model and the Test connection probe (read-only by default, write tests on request, clock skew, presign host), with seeded provider defaults.
- The `object_storage_operation` audit log (presigned URLs are logged without their signature) and protection rules (`READ_ONLY`, `NO_DELETE`, `WARN`, with allowed system callers).
- Backups follow-ups SF1 to SF5: `BucketRole.BACKUP`, object lock on put, system-caller permanent delete, probes that skip locked backup buckets, and the system-caller context.
- **The index (P2).** `object_storage_object` and `object_storage_folder` kept in step with each indexed bucket: resumable full scan with a mass-removal safety stop, HEAD backlog, write-through on every service write, soft-deleted rows, folder rollups. Orphaned and missing objects from `FieldReferenceProvider`, daily usage snapshots, quota and freshness alerts, the nightly job.
- **The Storage app (P3, P4).** Admin -> Storage with a responsive home; record screens with sections, child lists and a thumbnail preview; actions Rescan bucket, Check configuration, Refresh from bucket, Apply header policy (replaces app-specific backfills), Change headers and metadata, Edit tags, Copy, Move or rename, Delete, Restore, Create download link, Create upload link, folder rename and delete, Upload files, Test connection; every one a standard Review and Result with a bulk cap.
- `ObjectStorageSchema`: PostgreSQL DDL generated from the table metadata.
- Conformance suite against MinIO and Garage (Testcontainers), service, index and action tests, and the index and actions on PostgreSQL.

### Notes
- Until QQQ core's S3 module on AWS SDK v2 (qqq#1095, RC.5) is released, the qbit uses a small internal SDK v2 client factory.
- Deliberate differences from the design text: Test connection is two actions (read-only, and with write tests) so the write variant can require `object_storage.admin`; a folder record has no subfolder child list (core does not allow a self join as an exposed join); there is no `withTableNamePrefix` (the `object_storage_` prefix is fixed by the naming rule).
