# Database rollout checklist — V7/V8 drafts

Preparation only. No shared database has been inspected or migrated by this checklist. Role E / the database owner fills in the target facts and coordinates B/C/D review before any rollout.

## Target record (complete before execution)

- [ ] Environment, database, schema, PostgreSQL version: __________
- [ ] Responsible operator and reviewer: __________
- [ ] Current application commit(s) and intended application commit(s): __________
- [ ] Existing migration runner, baseline and last applied version/checksum: __________
- [ ] Approved migration filenames/order/checksums: __________
- [ ] Backup reference, access owner and retention location (no credentials): __________
- [ ] Restore rehearsal evidence and recovery duration: __________
- [ ] Maintenance window, write pause strategy and recovery decision owner: __________

## Read-only inventory

Run only through the approved connection to the identified target. Do not paste credentials or review text into reports. Confirm schema/search_path; the SQL drafts use unqualified table names.

```sql
SELECT current_database(), current_schema(), current_setting('server_version');
SHOW search_path;
SELECT to_regclass('flyway_schema_history') AS migration_history;
SELECT table_name, column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name IN ('tools','reviews','categories','tags','tool_versions')
ORDER BY table_name, ordinal_position;
```

- [ ] If migration history exists, inspect its applied versions/checksums using the actual runner; if absent, agree the populated-DB baseline procedure. Do not guess that V7/V8 are the next free versions.
- [ ] Record row counts for the relevant tables before/after staging migration. Confirm FK, unique, cascade and index definitions against B/C/D/E contracts.

After confirming the target reviews table exists in the intended schema:

```sql
SELECT count(*) AS oversized_review_count
FROM reviews WHERE comment IS NOT NULL AND char_length(comment) > 2000;
SELECT conname, convalidated, pg_get_constraintdef(oid) AS definition
FROM pg_constraint WHERE conrelid = 'reviews'::regclass
ORDER BY conname;
```

- [ ] Oversized comments: stop and obtain a data handling decision; never silently truncate. PostgreSQL char_length counts characters, whereas Java validation may count UTF-16 units.
- [ ] Existing `ck_reviews_comment_length`: verify its definition enforces the agreed <=2000 limit and is validated. V8 checks the name/table, not equivalence of its existing definition; a mismatch needs a reviewed forward migration.
- [ ] Inventory V7 lengths before narrowing: category.description 500, tool.name 150, slug 170, short_description 300, URL 500. If both website_url/repository_url exist, check conflicts before choosing a reconciliation rule.
- [ ] Confirm tags/version field names and lengths with B/C; include C's required tag index in the approved schema sequence.

## Staging execution gate

- [ ] Use an isolated copy with representative old schema/data and a separate empty database. Record exact SQL checksum and application SHA; do not use production for regression fixtures.
- [ ] Run `./scripts/test-postgres.ps1` in the E checkout for the existing V7/V8 regression fixtures. This is supporting evidence, not validation of the shared database or actual migration runner.
- [ ] Install only approved migrations through the agreed runner. Wrap each draft's complete file in one transaction; do not execute individual statements independently.
- [ ] Verify no dropped/truncated data, preserved counts/content, expected column lengths, validated comment constraint, unique/FK/cascade and Hibernate validation.
- [ ] Rerun the migration runner: no unintended schema/data changes. Restore the staging backup and confirm application startup and representative data.
- [ ] Attach evidence and receive operator/reviewer agreement to proceed within the recorded window.

## Rollout and recovery

- [ ] Confirm fresh backup and the agreed write pause; record the actual current schema/history again.
- [ ] Run approved migration; stop on any preflight or migration failure. Do not edit an already applied migration/checksum to force progress.
- [ ] Validate schema/history/counts/constraint definitions, then application login, browse, publishing and reviews with approved smoke accounts.
- [ ] Record start/end, migration and application versions, validation results and operator sign-off before resuming normal writes.
- [ ] If validation fails, use the rehearsed recovery procedure with the database owner. A committed migration is not undone by rolling back the application alone; account for writes since backup before restore.

References: [handoff plan](../docs/superpowers/plans/2026-10-07-postgres-race-handoff.md), [V7 report](role-e-migration-test-report.md), [V8 report](role-e-v8-test-report.md).
