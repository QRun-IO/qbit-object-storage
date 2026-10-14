-- liquibase formatted sql

-- qbit-object-storage starter defaults.
-- Include it from the host's master changelog AFTER the changesets that create
-- the object_storage_* tables (the script from ObjectStorageSchema.postgresScript):
--
--   - include: { file: db/liquibase/qbit-object-storage/object-storage-defaults.changelog.sql }
--
-- Rules for this file:
--   * insert-only: a row is inserted only when its natural key is absent, so an
--     administrator's edits always survive and nothing is ever updated;
--   * a seed never changes what an existing host does on deploy: the rows below
--     are inserted DISABLED, so they are visible examples until someone turns
--     them on;
--   * rollback is empty: a seed row may have been edited or adopted by the host, so
--     rolling back never deletes data;
--   * no secrets, no host-specific values;
--   * portable SQL (PostgreSQL and H2): INSERT ... SELECT ... WHERE NOT EXISTS;
--   * every changeset has a precondition, so a host that has not created the
--     table yet simply skips it (MARK_RAN).
-- DefaultsChangelogTest runs this file through Liquibase, twice, on H2.
--
-- Not seeded: object_storage_protection_rule (a rule needs a bucket, which is
-- host-specific), and the connection and bucket rows (written from code by the
-- qbit's config sync).

-- changeset qbit-object-storage:defaults-1
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM object_storage_header_rule WHERE name = 'default-mutable'
-- comment: A generic header rule for mutable objects, disabled. Enable it to give every object (any bucket) Cache-Control public, max-age=3600; lower the priority number to rank it ahead of other rules.
INSERT INTO object_storage_header_rule (name, key_glob, cache_control, priority, enabled, create_date, modify_date)
SELECT 'default-mutable', '**', 'public, max-age=3600', 1000, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM object_storage_header_rule WHERE name = 'default-mutable');
-- rollback empty
