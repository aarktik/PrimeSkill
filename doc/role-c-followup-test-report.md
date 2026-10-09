# Role C follow-up — local verification

Verified on 9 October 2026, Asia/Bangkok. Full Maven run finished at **19:13:19 +07:00**.

The run tested C changes on E base `51d195ea0dbf03bbd942b2eec5f272cf9c71a452`
in `worktrees/role-c-followup`. The implementation was then committed as
`75f58444be25d76a77f6a8592ddde1aadb18928f`; all 21 implementation/verification files were checked against the
successfully tested source, allowing only Git CRLF/LF normalization.
The committed manifest is `role-c-followup-source.json`; the original local
manifest is `code/target/role-c-evidence/source.json`.
These are local results; GitHub CI must be checked for the pushed HEAD separately.

## Results

| Verification | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Surefire | 338 | 0 | 0 | 0 |
| Failsafe / real PostgreSQL | 300 | 0 | 0 | 0 |
| Total Java | **638** | **0** | **0** | **0** |
| Python report gates | 16 | 0 | 0 | 0 |

Runtime: Microsoft JDK **17.0.20.1**, Maven **3.9.16**, PostgreSQL **18.6**.
All 60 Java XML suites record a Java 17 runtime. `BUILD SUCCESS`;
`scripts/check-role-c-reports.py` accepted the complete reports.
The local PostgreSQL runner stopped its own temporary cluster after verification;
no listener remained on test port 15453. No shared/Supabase database was used.

New feature scenarios are run unchanged against H2 and PostgreSQL:

- RatingBrowseIntegrationTest / RatingBrowsePostgresIT: **9 each**. Average ordering,
  same-display/different-precision scores, stable tie-breaks across pages, unrated last,
  ANY-tag/category/keyword/count handling, hidden tools, escaped wildcards,
  absent keyword for all four sorts, review CRUD freshness, three-query maximum,
  and actual rendered Browse/API responses.
- TagGuardIntegrationTest / TagGuardPostgresIT: **35 each**. Assign/unassign matrix
  across four states and owner/admin/other users, persisted association/Tool snapshots,
  non-DRAFT no-ops, existing 404/409 behavior, referenced deletion, API state errors,
  trusted principals, anonymous/non-owner rejection, and CSRF.
- TagMutationConcurrencyPostgresIT: **22**. Both commit orders for tag changes vs
  SUBMIT/approval, stale managed entities after concurrent SUBMIT/approval,
  rollback of mutations/submissions, assign/deleteTag races and rollback,
  and unassign/deleteTag ordering. The tests inspect `pg_blocking_pids` to prove
  actual waiting on the first transaction's row lock, with bounded timeouts.

Existing B metadata, D review/publishing/rollback, E version/state/revision/security,
and migration tests remain in the successful full run. The C gate additionally
requires the B metadata contract (38) and concurrency (11) suites.

## Browser checks

Headless Chrome used an isolated temporary profile and local H2 fixture at
`127.0.0.1:18083`, not the user's normal profile or real project data.
The browser connector failed to initialize twice, so QA used the local browser harness.

- Desktop viewport 1440px and mobile viewport 390px: PASS.
- `document.documentElement.scrollWidth` equals viewport width in both sizes;
  scores and descriptions remain inside the cards.
- Score display and card order: 5.0, 4.5, 3.0, then `ยังไม่มีรีวิว`.
- Pagination preserves `sort=rating`, `page=1`, and `size=2`: PASS.
- Desktop/mobile screenshots were visually inspected. Results are in
  `code/target/ui-preview/browser-check.json`, `browse-desktop.png`, and `browse-mobile.png`.

## Commands and evidence

The local Maven wrapper directory was added to PATH for this process only,
and MAVEN_OPTS pointed to the existing local Maven dependency cache.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/test-postgres.ps1 `
  -PostgresBin (Resolve-Path ./code/target/tooling/pgsql/bin).Path -Port 15453

python scripts/test-review-ci-reports.py -v
python scripts/test-role-e-b1-reports.py -v
python scripts/test-role-c-reports.py -v
python scripts/check-role-c-reports.py code/target --output code/target/role-c-evidence/coverage.json
git diff --check
```

The runner invokes `mvn -B -f code/pom.xml -Ppostgres-it verify`.
Full log: `code/target/role-c-verify.log`; XML: `code/target/surefire-reports/` and
`code/target/failsafe-reports/`. Browser harness/fixtures are generated under target
and excluded from the production JAR and source delivery.
Workflow additions were inspected in the existing YAML run block; a YAML parser
was not available locally. GitHub workflow execution is pending publication.

## Review still required

Implementation and local verification are complete. E/A/D still need to review
the resulting integrated source and proposed Tool->Tag lock order, run CI on its
actual commit SHA, and follow the team's PR/merge process. The user subsequently authorized committing and pushing the C personal branch.
Remote PR review, shared migration, and deployment are separate steps.
