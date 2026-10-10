# D+E PostgreSQL verification — 8 October 2026

## Source and execution

- Branch inspected: `origin/role-de-review-pg-rollback`.
- Exact tested commit: `3187098fac9d91c14d1fdfe40bb765797cde8cfd`.
- Detached isolated clone: `D:\PrimeSkill\code\target\integration-checks\role-de-3187098-638dc481`.
- No production/test source, Maven configuration or SQL was changed in this clone. No candidate patch or additional test fixture was applied.
- Ran the script included at that commit from the clone root: `./scripts/test-postgres.ps1`.
- Process `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD` and `MAVEN_ARGS` were cleared for the command, then restored. This prevents a real datasource or inherited test selector from affecting the baseline.
- Environment: Java 26.0.1 (compiler release 17), Maven, PostgreSQL 18.6. This is a local run, not Java 17 runtime / PostgreSQL 17 CI evidence.
- Script/Maven exit: **0 / BUILD SUCCESS**, completed 8 October 2026, 22:15:23 Asia/Bangkok; Maven elapsed 2:39.

## XML-confirmed results

- **Surefire: Tests 247, Failures 0, Errors 0, Skipped 0.**
- **Failsafe: Tests 107, Failures 0, Errors 0, Skipped 0.**
- Total in this exact run: **354**, with no failures/errors/skips.
- `ToolHubApplicationTests`: 1/1 passed, including A's H2 datasource, validation and open-in-view assertions without Supabase credentials.

Failsafe classes:

- `MigrationPreflightPostgresIT`: 13 passed.
- `ModerationPostgresIT`: 1 passed.
- `ReviewCommentMigrationPostgresIT`: 16 passed.
- `RoleEPostgresIT`: 69 passed.
- `ReviewPublishingRollbackPostgresIT`: **4 passed**, failures/errors/skips 0.
- `ReviewServiceLockRollbackPostgresIT`: **4 passed**, failures/errors/skips 0.

The two rollback classes are distinct. The first calls real ReviewService and PublishingService in separate concurrent transactions, asserts PostgreSQL blocking PID evidence, then checks state after rollback. The second retains D's sequential service/SQL regressions. Its four passes alone would not prove a concurrent service race; both classes ran here.

## Evidence retained locally

Under the detached clone above:

- `code/target/integration-postgres-verify.log`
- `code/target/surefire-reports/TEST-*.xml`
- `code/target/failsafe-reports/TEST-*.xml`
- `code/target/failsafe-reports/failsafe-summary.xml`

Results were counted from the XML files in this new clone, not copied from older 165/169/171 runs. The generated log was moved into ignored code/target after completion; `git status --short` in the clone is clean. The test cluster stopped successfully and port 15432 had no listener afterwards. No shared database was used or migrated.

## Scope still pending

- `test/review-publishing-race/ReviewPublishingRacePostgresIT.java` is outside the Maven test source tree and was **not executed** by this baseline command. Commit orders and stale managed Tool cases from that harness must still be run in a separate, clearly identified follow-up.
- `test/role-d-postgres/RoleDPostgresIT.java` is likewise outside Maven test sources and was not added to this baseline.
- The E local ten-case race fixture and rollback follow-up were not copied into this run. This report certifies the unmodified commit only.
- No claim of CI success for 3187098, PR approval, readiness to merge develop, shared V7/V8 rollout or B1 metadata edit/approval policy is made by this result.
- The existing overlay runner still needs adaptation before targeting D with an existing lock/profile; this run used the integration branch's PostgreSQL script directly.

## ข้อความพร้อมส่งให้ A/D (ยังไม่ได้ส่ง)

E รัน `./scripts/test-postgres.ps1` จาก checkout แยกที่ SHA `3187098fac9d91c14d1fdfe40bb765797cde8cfd` แล้ว โดยไม่แก้ source/config หรือ apply candidate ซ้ำครับ ผล Surefire: Tests 247 / Failures 0 / Errors 0 / Skipped 0; Failsafe: Tests 107 / Failures 0 / Errors 0 / Skipped 0 รวม 354 กรณี ผ่านทั้งหมด. `ReviewPublishingRollbackPostgresIT` 4/4 และ `ReviewServiceLockRollbackPostgresIT` 4/4 ผ่าน. ใช้ Java26.0.1/PostgreSQL18.6, script exit0/BUILD SUCCESS และปิด test cluster แล้ว. ชุด race เดิมใต้ test/ ยังไม่ถูกรันในคำสั่งนี้ จะตรวจแยกเป็นขั้นต่อไปครับ ยังไม่สรุปพร้อม merge.
