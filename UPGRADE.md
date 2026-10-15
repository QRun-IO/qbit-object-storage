# Upgrading

## Unreleased

- Nothing is required. Help and the Setup card appear when the qbit is produced; the schema is unchanged.
- Optional: include `db/liquibase/qbit-object-storage/object-storage-defaults.changelog.sql` after the changesets that create the tables, to get a disabled generic header rule (`default-mutable`). It is insert-only and changes no behavior until you enable the rule.
- Behavior change: the presign limits on an `object_storage_bucket` row (`maxPresignGetSeconds`, `maxPresignPutSeconds`) are now read, as a lower cap only. Any bucket row whose presign values are lower than your code config shortens links after this upgrade (the config sync copied the code values into the row on first start, so a row someone lowered earlier counts). Check the values on your bucket rows; 0 or empty means no row limit, and a value above the code limit has no effect.
- `object_storage_object_version` stays empty: it is reserved.
