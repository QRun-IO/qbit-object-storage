# Upgrading

## Unreleased

- Nothing is required. Help and the Setup card appear when the qbit is produced; the schema is unchanged.
- Optional: include `db/liquibase/qbit-object-storage/object-storage-defaults.changelog.sql` after the changesets that create the tables, to get a disabled generic header rule (`default-mutable`). It is insert-only and changes no behavior until you enable the rule.
- Behavior change: the presign limits edited on an `object_storage_bucket` row (`maxPresignGetSeconds`, `maxPresignPutSeconds`) now take effect. Rows are created from your code config and an edit is kept, so a row you edited earlier will start capping links now. Check the values on your bucket rows.
- `object_storage_object_version` stays empty: it is reserved.
