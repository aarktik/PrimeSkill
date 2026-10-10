# B1 metadata contract and PostgreSQL integration fixtures

These fixtures require E revision/decision/security dependencies. They live
outside B's Maven test source so B's older standalone branch still compiles.
They test real services, sessions, CSRF and PostgreSQL persistence/locks;
they do not implement or certify C tag guards.

Reviewed E source: `742a0fa40f35499abe452d81b2756eec198cddac`.
B preparation source: `4208e4562aaf728caefde4093664a17af0b6411b`.

## Reproduce B + E composition

From B's checkout, with the metadata candidate present in committed or working
files, create a fresh detached checkout and compose selected B changes:

```powershell
git worktree add --detach C:/WORK/primeskill/b1-role-b-review 742a0fa40f35499abe452d81b2756eec198cddac
python test/role-b-b1/compose-review.py C:/WORK/primeskill/b1-role-b-review
```

The helper checks pinned sources and refuses to overwrite changed candidate
files. Three-way application preserves C/D/E web/controller/layout additions.
E already provides the identical shared Tool lock method, exception and API
error handlers, so the helper retains E's versions. It emits file SHA256s in
`b1-source-manifest.json`. If Git reports a merge conflict, resolve it with
the owning Role before running tests; do not discard E changes. At this pinned
snapshot, resolve the two expected conflicts by keeping both Review and
ToolStatus/InvalidState imports, and keeping E's status badge/version link/
delete confirmation while adding B's DRAFT condition to the edit link. Stage
only those resolved candidate files, then rerun the helper with `--resolved-web`.

In the candidate checkout, clear process-level overrides for `SUPABASE_DB_*`,
`SPRING_DATASOURCE_*`, `SPRING_APPLICATION_JSON`, `SPRING_PROFILES_ACTIVE`,
`SPRING_SQL_INIT_MODE`, and `MAVEN_ARGS` before invoking the runner. Run in a
child PowerShell process so the caller's environment is preserved. Never put
credentials into logs or commit them. Point `JAVA17_HOME` to JDK 17.

```powershell
./scripts/test-postgres.ps1 -Port 15443
```

The reviewed runner creates a fresh loopback-only PostgreSQL cluster and
guarded database, runs `-Ppostgres-it verify`, and stops its own cluster.
Do not use Supabase or an existing team database.

On Windows PowerShell 5.1, `Start-Process` may expose a null `ExitCode` even
after PostgreSQL successfully starts. The local review used a runner-only
adaptation: reject non-null nonzero exit codes, then require successful
`pg_ctl -D <created cluster> status` before setting `$started = $true`.
This adaptation is recorded separately from the product candidate; it is
not an E production commit. The review also lets Maven emit native stderr
warnings non-terminatingly in `mvn-java17.ps1` and uses Maven's exit code;
PowerShell 5.1 otherwise treats the JVM class-sharing warning as terminating.
If startup fails, inspect the created cluster's
logs and stop only that cluster if it started; never stop another server.

## What the fixtures verify

- Full 12-case state/actor matrix through REST and web with real login/session
  and persisted denied-row invariants (including auditing timestamps).
- DRAFT and non-DRAFT no-op rules; trusted actor and actual CSRF failures,
  anonymous/logout, original validation/unique/FK errors.
- Editor opened before submit, editor/dashboard visibility, and old approval
  after reject → metadata edit → resubmit.
- Metadata update versus submit/approve in both orders; stale managed DRAFT;
  owner and non-owner; rollbacks and classified lock timeout.
- Race workers use separate real transactions/connections with bounded waits
  and `pg_blocking_pids` evidence, then read persisted data after completion.

See `doc/role-b-b1-test-report.md` for actual results and limitations. Unit,
reviewer-overlay and E baseline totals must not be added together as unique
tests or substituted for a future B+C+E integration run.
