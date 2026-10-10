# Role B — DRAFT-only metadata guard and integration evidence

Date: 9 October 2026 (Asia/Bangkok).

Scope: B metadata candidate using the contract supplied in A's review of E
`742a0fa40f35499abe452d81b2756eec198cddac` and E's B1 handoff. This is not
approval to merge/deploy, run a team migration, or certify C tag guards.

## Implementation

- `ToolService.update` uses the same `findForUpdateById` Tool row lock as E,
  refreshes the managed entity, checks owner/admin before the status, then
  requires DRAFT before unique/FK validation and mutation in one transaction.
- DRAFT owner/admin may update all existing metadata fields. Other users are
  denied in every status (403 ACCESS_DENIED). Non-DRAFT owner/admin requests,
  including no-op, return 409 INVALID_STATE_TRANSITION with no mutation.
- Owner/status/viewCount/reviewRevision remain outside the request DTO. No new
  revision logic or publishing state machine is added in B; E owns those.
- Editor GET and invalid-form redisplay verify ownership/status. Valid POST
  always reaches the transactional service guard, including forms opened
  before submit. Dashboard edit links appear only for DRAFT.
- REST and web classify Spring pessimistic locking failures as 503, while
  original validation/unique/FK and unknown integrity errors retain their
  contracts. Web exceptions produce an HTML error page, not REST JSON.
- Existing viewCount implementation is unchanged and its tests remain in the
  focused regression. Tool deletion and shared catalog-label policy are not
  expanded by this metadata change.

## Source and runtime

- Primary B base: `4208e4562aaf728caefde4093664a17af0b6411b`.
- E source for isolated composition: `742a0fa40f35499abe452d81b2756eec198cddac`.
- Candidate checkout: `code/target/b1-integration-742a0fa` (detached, ignored).
- B overlay was uncommitted at test execution; exact composed file SHA256s
  are recorded in `b1-source-manifest.json` in that checkout.
- Actual runtime: Amazon Corretto **17.0.19+10**, PostgreSQL **18.4**, Maven
  **3.9.16**, Windows PowerShell **5.1**. These are not A's JDK17.0.20.1/PG18.6.
- PostgreSQL runner created a new loopback-only guarded DB on port 15443,
  cleared inherited database/Spring/MAVEN_ARGS overrides in its child process,
  and stopped its own cluster. No Supabase/team DB was used.
- Two runner-only PS5 adaptations are disclosed in the fixture README:
  nullable Process.ExitCode verified with pg_ctl status, and non-terminating
  native JVM stderr warnings while retaining Maven exit status. They are not
  changes to E's committed source or B's production dependencies.
- Composition preserves C browse routes, D review wiring and E layout/version
  links. Two web composition conflicts were resolved accordingly. E's shared
  lock/exception/API handlers are retained, not overwritten with older B files.

## Tests actually run

| Run | Tests | Failures | Errors | Skipped | Outcome |
| --- | ---: | ---: | ---: | ---: | --- |
| New B unit policy cases before guard | 25 | 9 | 0 | 0 | Expected red |
| B focused regression after guard | 58 | 0 | 0 | 0 | BUILD SUCCESS |
| New PostgreSQL fixtures on original E metadata path | 49 | 26 | 0 | 0 | Expected red: contract19 + races7 |
| Full B + E composition Surefire | 294 | 0 | 0 | 0 | BUILD SUCCESS |
| Full B + E composition Failsafe/PostgreSQL | 234 | 0 | 0 | 0 | BUILD SUCCESS |
| E Python report-gate tests | 12 | 0 | 0 | 0 | PASS (8 + 4) |

The full composed Java run passed **528 tests** across 55 XML suites (42
Surefire + 13 Failsafe). Runner exit code was 0 and its PostgreSQL cluster was
stopped. New B PostgreSQL tests passed 38 + 11 = **49**, included in 234, not
added again to 528. This is E's 450-test source plus 29 added B unit/MVC cases
and 49 B PostgreSQL cases. The separate B-focused 58 are not an extra unique
total. `git diff --check` passed.

Focused command:

```text
mvn -B -f code/pom.xml "-Dtest=ToolServiceImplTest,CategoryServiceImplTest,ToolRestControllerTest,CategoryRestControllerTest,CurrentActorProviderTest,ToolWebControllerTest" test
```

Full composition command, executed in the detached checkout with JDK17:

```text
./scripts/test-postgres.ps1 -Port 15443
python scripts/test-review-ci-reports.py -v
python scripts/test-role-e-b1-reports.py -v
python scripts/check-role-e-b1-reports.py code/target --output code/target/b1-gate-summary.json
```

New PostgreSQL fixtures: `ToolMetadataContractPostgresIT` (38 cases) and
`ToolMetadataConcurrencyPostgresIT` (11 cases). Contract tests use actual
registration/login/session/CSRF through the real filters and compare fresh
persisted rows. Race tests use real service proxies with separate connections/
transactions, bounded latches and pg_blocking_pids evidence. They cover both
submit/approve orders, stale managed DRAFT, unauthorized writers, rollback and
classified lock timeout. Old approval after reject → metadata edit → resubmit
is tested through the real decision API.

## Evidence locations

- `code/target/b1-policy-red.log` and `b1-policy-green.log`.
- `code/target/b1-evidence/red/`: saved red PostgreSQL XML (49 cases).
- `code/target/b1-integration-742a0fa/b1-integration-full-green.log`.
- Candidate `code/target/surefire-reports/` and `failsafe-reports/`.
- Candidate `b1-source-manifest.json`.
- Saved full green XML/log/manifest: `code/target/b1-evidence/green/`.
- Candidate `b1-gate-summary.json` confirms 294/234 with zero fail/error/skip.
- Reproduction/fixtures: `test/role-b-b1/README.md` and `compose-review.py`.

These are local runs. A's earlier 450 and reviewer-overlay 478 totals are
separate evidence and are not added to this run as new distinct tests. No
GitHub CI result is claimed for this B candidate.

## Remaining team work

E must receive B's candidate and C's tag guards and run the final shared source
and CI on a new SHA. A then reviews metadata/tag session/security guards and
denied-state invariants on that SHA; D reviews integration as appropriate.
Migration, backup/restore and rollout of the team database require separate
coordination. B metadata evidence does not mean B1 is complete for all writers.
