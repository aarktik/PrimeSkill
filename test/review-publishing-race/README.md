# Review / publishing race handoff

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

The six cases cover both create and update for deprecate-first, review-first (including author deletion after hiding), and a stale managed Tool loaded before a separate deprecate commits. Existing D PostgreSQL tests additionally cover HTTP permissions, CSRF, constraints, events and admin deletion. These tests do not replace browser/network tests, the full merged application, Java 17 CI or Docker verification.

See [the report](../../doc/review-publishing-race-report.md) for measured results.
