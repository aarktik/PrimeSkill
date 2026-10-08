# D+E CI verification — 8 October 2026

## Actual GitHub run

- Source commit: `3187098fac9d91c14d1fdfe40bb765797cde8cfd` on `role-de-review-pg-rollback`.
- [Build and test run 37801303699](https://github.com/aarktik/PrimeSkill/actions/runs/37801303699), manually dispatched against that branch after checking its tip SHA.
- Conclusion: **completed / success**, both `verify` and `postgres-integration` succeeded.
- Docker image build and container startup/database restart persistence steps succeeded.
- Workflow config uses PostgreSQL 17 Alpine. Downloaded XML reports confirm Java runtime **17.0.20.1**.

## Artifact-confirmed counts

The unit-test-reports artifact contains Surefire: **Tests 247 / Failures 0 / Errors 0 / Skipped 0**.

The postgres-test-reports artifact contains:

- Surefire: **Tests 247 / Failures 0 / Errors 0 / Skipped 0**.
- Failsafe: **Tests 107 / Failures 0 / Errors 0 / Skipped 0**.
- Combined in the PostgreSQL job: **354**, not 354 plus the separate job's duplicate Surefire execution.
- Native concurrent `ReviewPublishingRollbackPostgresIT`: 4/4 passed.
- Native sequential `ReviewServiceLockRollbackPostgresIT`: 4/4 passed.

Artifact ZIPs and counts.json are retained locally under `code/target/ci-runs/37801303699/` and ignored by Git. These counts came from downloaded CI XML, not inferred from local results. Credentials were used through Git's configured authentication, were not printed, and were not forwarded to redirected artifact-storage downloads.

## External fixture coverage: distinct evidence

The original workflow does not copy classes from test/ into Maven sources. Therefore the actual CI run above does **not** certify external RoleDPostgresIT or ReviewPublishingRacePostgresIT. Local expanded verification against the same production implementation passed 247 + 145 = 392; see [runner evidence](role-d-runner-update-report.md).

Prepared `.github/workflows/review-publishing.yml` for the expanded CI check. It:

1. Checks out the harness and a separately pinned combined source (default 3187098; manual input may select another ref).
2. Records both resolved SHAs, Java version and SHA256 hashes of copied fixtures.
3. Uses Java 17 / a disposable loopback PostgreSQL 17 service.
4. Runs the unmodified snapshot first and saves baseline reports separately.
5. Adds only external test fixtures, refusing to overwrite a different native fixture and checking that production/config remain unchanged.
6. Runs the expanded suite and checks required XML coverage: race 10, RoleDPostgresIT 28, concurrent rollback 4 and sequential rollback 4. Missing, skipped, failed or unexpectedly short suites fail the job.
7. Uploads baseline/overlay XML, summary and revision evidence even when verification fails.

This delivery includes the new workflow and its scripts. A successful expanded GitHub run remains pending verification after publication. It must not inherit the success label of run 37801303699.

## Local validation of CI additions

- Workflow YAML structure parsed successfully using a throwaway PyYAML installation under ignored code/target; no product dependency was added. Embedded preparation Python compiled successfully.
- `python scripts/test-review-ci-reports.py -v`: **8 tooling regression tests passed** (separate from Java/CI counts).
- Coverage checker accepts the existing XML from the 392-case local expanded run.
- Coverage checker rejects the 354-case baseline XML as missing required external race coverage, as intended.

## Next action

Publish the reviewed E delivery batch to the personal branch. Its push triggers the new expanded workflow; verify the resolved source/harness SHAs and baseline/overlay artifacts, then append that run URL/results here. No merge develop, PR approval or shared database migration has been performed by this CI task.
