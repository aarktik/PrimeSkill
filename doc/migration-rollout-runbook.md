# ข้อ 5 — E migration preparation

Delivery update: [รายงานส่งมอบบน personal branch](role-e-delivery-handoff.md) เป็นสถานะล่าสุด. ข้อมูล worktree/commit/push และผลทดสอบด้านล่างที่ระบุรอบก่อนเป็นประวัติการเตรียมงาน.

Preparation candidate only. No shared database inspected/migrated. Worktree `D:\PrimeSkill-worktrees\b1-role-e`, base `3187098`; B1 changes remain uncommitted. No Flyway runtime/baseline installed.

## Files and responsibility

- [Draft manifest](sql/migration-draft-manifest.json): SHA256 ของ draft SQL, inventory, schema และ test helper/launcher ที่ตรวจในเครื่อง; ต้องยืนยัน checksum อีกครั้งจาก release ที่อนุมัติจริง.

- `doc/sql/preflight/rollout-inventory.sql`: read-only target/schema/columns/counts/oversized values/FK/unique/index inventory. Does not include credentials, passwords or review text. Run only after operator confirms the intended schema/search_path. Output is evidence for human review, not automatic permission to migrate.
- `doc/sql/V7__align_existing_tool_catalog.sql`: B's proposed catalog alignment. Entire file must run in one transaction. Preserves full description; derives a new short description using its first 300 characters. Existing short descriptions are not silently truncated. Confirm this derivation with B.
- `doc/sql/V8__limit_review_comment_length.sql`: D's unchanged draft, caller transaction required. Existing >2000 character comments fail. Existing same-name constraint is not checked for equivalent definition: operator must inspect definition and validation before approval. Do not change an applied migration checksum.
- `doc/sql/drafts/B1__add_tool_review_revision.sql`: E candidate with its own BEGIN/COMMIT. Requires approved B1 code deployment and B/C guards before claiming full content protection. Do not nest it inside another caller transaction without reviewing transaction boundaries.
- `scripts/rehearse-migration-rollout.ps1`: tests only; refuses non-loopback/non-runner database URL. Creates and removes only its GUID-named disposable databases. Produces ignored backup/startup/summary evidence under `code/target/migration-rollout/`.
- `MigrationSchemaValidationApplication`: test-only launcher, opens and closes actual application context; rehearsal supplies SQL init=never and Hibernate validate. Does not expose production endpoints or credentials.

## Local rehearsal (Windows / PostgreSQL 18)

Run from this worktree. Requires Maven, Java, pwsh and PostgreSQL binaries. The wrapper executes within the disposable cluster's lifetime; it does not connect to Supabase. Clear/restore any local SUPABASE_DB_* and MAVEN_ARGS overrides as in the test report.

Create an ignored `code/target/rollout-progress/rollout-mvn.cmd`:

```bat
@echo off
call mvn %* dependency:build-classpath -Dmdep.outputFile=target/rollout-classpath.txt
if errorlevel 1 exit /b %errorlevel%
pwsh -NoProfile -File scripts\rehearse-migration-rollout.ps1
exit /b %errorlevel%
```

Then run `./scripts/test-postgres.ps1 -MavenCommand ./code/target/rollout-progress/rollout-mvn.cmd`.
Java17 update: this custom CMD wrapper uses `mvn` and launches `java` separately, so set the current session's JAVA_HOME and PATH to JDK17 for its complete lifetime. See [Java17 setup](java17-development.md). The default PostgreSQL runner selects JDK17 automatically; overriding MavenCommand transfers that responsibility to the custom caller.
The helper's PostgreSQL binary default is PG18 on Windows; edit the wrapper to pass `-PostgresBin` if installed elsewhere. Java compilation/classpath must complete before invoking it directly.

Rehearsal checks: current empty schema initialization + app validation; full representative legacy schema/data; whole-file V7 → V8 → B1; unchanged original contents and associations; expected derived fields; rerun; app validation without SQL init; custom-format backup restore into a second database; old schema and contents preserved; re-migrate and app validation. Separate Java suites exercise oversized fields/comments, URL conflicts, migration rollback and incompatible B1 columns. This is not the actual production migration runner or a restoration of the team's full data.

## Before shared rollout — required input

Fill `doc/database-rollout-checklist.md`: target/schema/PG version, operator/reviewer, current and intended app SHA, actual runner/history/checksums, official migration versions/order, backup location/retention and measured full restore duration, maintenance/write-pause window and recovery decision owner. These facts cannot be inferred from local fixtures.

1. Inventory the approved target read-only; inspect history using its actual runner. If there is no history, agree populated baseline separately from empty DB initialization.
2. Review all lengths, URL columns, comment constraint definition, FK/unique/cascades and `idx_tool_tags_tag_id`. V7 does not create that tag index: include C's approved forward change if absent. Stop for oversized data, conflicting URL columns, missing backfill sources or incompatible revision schema; no silent truncation.
3. Agree official filenames/order from actual history. V7/V8 filenames are proposals; B1 has no assigned version. The fixture order is a dependency rehearsal, not an official version allocation.
4. Freeze old writers, capture fresh backup and rehearse with actual runner on a staging copy. Deploy schema/API together; old id-only approve/reject clients must be updated. Keep B1 disabled until B/C guards are integrated and tested.
5. Apply only approved files; stop on error. Check history/checksums, counts/content, constraints/indexes and app startup. Perform approved login/browse/publishing/review smoke checks before resuming writes.
6. Recovery after committed DDL requires the agreed database procedure. Application rollback alone does not remove schema changes; restore must account for writes after backup. Retain evidence and operator sign-off.

## User review queue

1. B1 E candidate: revision policy, API/web changes, version concurrency, draft SQL and E test report. Not committed/pushed.
2. This migration preparation: inventory SQL, rehearsal helper/launcher, result report and updated checklist. Confirm that this is the package to send B/C/D/A; sending is not performed automatically.
3. Decide commit packaging/push after reviewing both candidates. Shared DB rollout remains a later operator approval, not implied by reviewing these files.
