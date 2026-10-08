# Review / publishing race handoff

Update 8 October: D implemented the candidate's lock/refresh behavior in d8c51e3. Use the combined snapshot with the updated runner, without applying the candidate again:

```powershell
./scripts/check-role-d-postgres.ps1 -RoleDRef 3187098fac9d91c14d1fdfe40bb765797cde8cfd -IncludePublishingRace
```

The commands pinned to 0a92dbc below remain historical red/green reproduction. Existing production code and A/PostgreSQL configuration on the combined snapshot are preserved by the updated runner.

This is an isolated D + E integration fixture, not a merge into develop. D is pinned to `0a92dbc3a80538917bc400d8862d8644c5dd4ec9`; E publishing source comes from the current personal checkout (publishing implementation unchanged from `76cdd65`).

## Approved policy

- Deprecate commits first: create/update review must reject without writing.
- Review commits first: keep the review; deprecate may then commit.
- Deleting hidden reviews remains available to their author or an admin.

## Reproduce

From the E repository root in PowerShell:

```powershell
# Original D review implementation: expected race regression failures.
./scripts/check-role-d-postgres.ps1 -RoleDRef 0a92dbc3a80538917bc400d8862d8644c5dd4ec9 -IncludePublishingRace
# Candidate implementation: expected passing regression tests.
./scripts/check-role-d-postgres.ps1 -RoleDRef 0a92dbc3a80538917bc400d8862d8644c5dd4ec9 -IncludePublishingRace -ApplyReviewLockCandidate
```

Requires Maven, Java and local PostgreSQL binaries (`-PostgresBin` and `-Port` configurable). Each run creates a detached local clone under ignored `code/target/role-d-checks`, records D's unmodified baseline, then uses a disposable loopback PostgreSQL cluster. It neither fetches nor commits nor migrates a shared database. Review baseline-verify.log separately: D's unconfigured contextLoads currently requires Supabase configuration.

Composition copies only E's publishing service/state machine/dependencies, a status mutation method and the shared ToolRepository row lock. It does not combine every controller/UI/schema change across branches. The test fixture runs real Spring services in separate READ_COMMITTED transactions; `pg_blocking_pids` verifies actual database blocking, with bounded barriers, SQL and transaction timeouts.

## Candidate patch for D / A / E review

`review-lock-candidate.patch` adds a pessimistic Tool row lock before create/update visibility checks, refreshes a potentially cached managed Tool, and adapts constructor unit-test setup. Publishing must use the same lock within its transaction. Deletes retain their existing permissions and status behavior. Writes for a given tool now serialize, so long transactions increase contention; keep remote calls outside these transactions.

The patch is based on the pinned D snapshot. On an isolated clean D checkout, inspect it and run `git apply --check <absolute-patch-path>` before applying. It includes ToolRepository's new method. If integrating after E already supplies that method, reconcile that hunk rather than adding a duplicate. The composed runner excludes the repository hunk for this reason. No candidate changes are applied to the E production tree by this harness.

The ten cases cover both create and update for deprecate-first, review-first (including author deletion after hiding), a stale managed Tool loaded before a separate deprecate commits, and rollback of either the review or deprecate transaction. Rollback cases flush actual SQL, assert the second transaction blocks, then verify it completes after rollback and only committed changes remain. Existing D PostgreSQL tests additionally cover HTTP permissions, CSRF, constraints, events and admin deletion. These tests do not replace browser/network tests or the full merged application. E's separate Java 17/PostgreSQL 17 and Docker CI passed at fd67569, but that workflow does not automatically run this composed harness.

See [the report](../../doc/review-publishing-race-report.md) for measured results.
