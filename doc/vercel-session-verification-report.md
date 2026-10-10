# Vercel shared-session candidate — verification, 2026-10-10

## Status and identity

This report records local verification of tasks1–4 before commit. The user
subsequently authorized commit/push to the personal branch; the containing commit
identifies the candidate. Remote CI results must be checked separately.
No production session migration, credential transfer or Vercel deployment was made.
The existing Supabase rollout reports and UI were preserved.

- Branch: `thaninton_673380043-6_02`.
- Baseline HEAD: `2c6aff803c63b36dd72b6595cdb19589d38924bd`.
- Baseline Git tree: `f63b4a05677b5b176693fa2885b60a4afc3f4fae`.
- At verification the candidate was uncommitted, not that baseline SHA. Runtime/build/test source
  snapshot SHA256: `516587e976c0c946fc7d5e498bf71be5c17a0fd51d196b19ec056cd7d5e66c01`.
- Snapshot manifest: `doc/vercel-session-source-snapshot.json` (pre-commit measurement).
- S1 SQL SHA256: `b11ab569e92b8bd7a5e6d40004888bbaf4363ce66d906c674a71b9ee2896c31d`.

The snapshot hashes the fourteen listed runtime/build/test/migration files;
it is not a Git commit/tree, deployment identifier or hash of the documentation.
Use the containing immutable commit and its fresh CI evidence before rollout.

## What changed and why

1. **Shared sessions:** managed Spring Session JDBC4.1.1 dependency and explicitly
   profiled configuration (`vercel`, `jdbc-session-test`). Default local/test/Compose
   sessions remain servlet sessions. Filter executes before security, including
   error/async dispatch. JSESSIONID remains host-only, HttpOnly, SameSite=Lax,
   Secure on deployment; inactivity timeout1800 seconds.
2. **Safe principal:** passwordHash is transient and serialVersionUID is explicit.
   Fresh login still receives the hash for password verification; persisted
   principals retain id/email/role/enabled/authorities without that hash.
3. **S1 draft:** additive two-table schema derived from the exact managed library.
   principal_name widened from100 to255 to match the existing email contract.
   Strict rerun refusal, transaction/timeouts/advisory lock, FK cascade and indexes.
   New-table API grants are revoked and all seven effective table privileges
   checked, including inherited TRUNCATE. No business-table grants are changed.
4. **Deployment files:** separate Java17 non-root Dockerfile, PORT alignment,
   exec-based signal delivery, SQL-init off, Hibernate validation, secure cookies,
   proxy headers and disabled API documentation. Ordinary Docker/Compose and UI
   files were not modified. CI now includes config checks and a Vercel image build.
5. **Evidence and handoff:** actual two-server HTTP tests, backup/schema rehearsal
   helper, configuration tests, execution ledger and operator runbook.

## Results

Runtime: Java17.0.20.1, disposable PostgreSQL18.6 bound to loopback15462.
Test DB names are guarded `primeskill_test_*`; no SUPABASE_DB_* endpoint is used.
The disposable cluster was stopped by the runner after completion.

- Surefire: **381 passed**, failures0/errors0/skips0.
- PostgreSQL Failsafe: **349 passed**, failures0/errors0/skips0.
- Total Java: **730 passed**.
- Included new principal/profile unit tests:4; JDBC HTTP/PostgreSQL tests:14.
- Existing Python validator tests:8+9+8; Vercel config tests:2; **27 passed**.
- B metadata/E B1 and combined C coverage gates: both passed on these reports.
- `git diff --check`: passed for tracked changes; added-file whitespace inspected.
- Final independent read-only whole-change review: no blocking bug, security
  issue or contract regression found. Full tests completed after that review.

After the full run, only redundant trailing blank lines in three files were
removed. The principal/profile4 and JDBC/PostgreSQL14 targeted tests and backup/S1
rehearsal were rerun successfully on the final source snapshot; both combined
gates still report730. Log: `D:\PrimeSkill-local-team\logs\vercel-session-final-targeted.log`.

HTTP session tests use two independent servlet contexts/repositories sharing one
disposable DB, with actual HTTP requests/cookies. Covered cross-instance CSRF and
login, missing/wrong/other-session tokens, ID rotation and old-cookie rejection,
USER/ADMIN identity, owner/member/admin access, global logout, restart persistence,
expiry without cleanup, hash absence in stored bytes, timeout, secure deployment
cookie, parallel requests, stale save after logout and API-role privileges.

## RED evidence and fixes

- Principal payload contained the synthetic password hash for both roles: fixed
  by transient persistence; serialization/profile tests passed4/4 afterward.
- Managed schema's varchar(100) caused HTTP500 for a valid long email: widened
  principal_name to255; long-email login now passes.
- Direct revocation alone missed inherited TRUNCATE: a failing regression proved
  the gap; all effective privileges now checked and installation rolls back.
- Missing Vercel files failed configuration tests; new files pass2/2.
- Harness corrections: public User constructor, HTML Accept header, retaining
  instance-B datasource when restarting A, public Session interface for library's
  non-public concrete session class, and existing draft-visibility404 contract.
  These were test corrections, not production business behavior changes.

## Production-schema copy rehearsal

Restored the restricted retained public backup into a new disposable DB,
reconstructed already-applied V7/V8/B1, then applied S1. This was a local restore,
not a new production connection. The empty default public schema is removed
without CASCADE before restoring the dump's CREATE SCHEMA statement.

All eight business table counts remained0 before/after S1 and after the rejected
rerun: users, user_profiles, categories, tags, tools, tool_versions, tool_tags,
reviews. Both session tables survived the refused rerun. An inherited-privilege
failure also rolled back both tables in the dedicated acceptance case.

Backup scope remains public schema/data only; this does not prove restore of
Supabase auth/storage or constitute the fresh backup required before production.

## Commands and local evidence

Primary full command:

```powershell
scripts/test-postgres.ps1 -Port 15462 -MavenCommand D:\PrimeSkill\code\target\session-full.ps1
```

That wrapper runs Java17 Maven `-B -f code/pom.xml -Ppostgres-it verify`, then
`rehearse-shared-session-schema.ps1` against the restricted retained public backup.
Old Surefire/Failsafe reports were archived before the full run, so the final
coverage gates did not consume stale test reports. The active test cluster was
never subjected to Maven clean.

Python commands:

```text
python scripts/test-review-ci-reports.py -v
python scripts/test-role-e-b1-reports.py -v
python scripts/test-role-c-reports.py -v
python scripts/test_vercel_configuration.py -v
python scripts/check-role-e-b1-reports.py code/target --output code/target/vercel-session-evidence/b1-summary.json
python scripts/check-role-c-reports.py code/target --output code/target/vercel-session-evidence/role-c-summary.json
```

Local logs: `D:\PrimeSkill-local-team\logs\vercel-session-full-verify.log`,
`vercel-session-task1-red.log`, `vercel-session-long-email-red.log`,
`vercel-session-acl-red.log`, `vercel-session-task2-complete.log`, and
`vercel-python-{review,b1,role-c,config}.log`.
Final XML reports and gate summaries are under `code/target/`.

## Limits and external rollout gates

- Docker executable/daemon is unavailable here; the image was **not built or
  executed locally**. The new CI build step has not run remotely for this candidate.
- Signed-in Vercel team is aarktik's projects, Hobby. OCI entitlement, actual image
  build, resource/startup limits, HTTPS/proxy/caching and cold starts are unproven.
- No explicit missing-session-table deployment startup test or dedicated successful
  authenticated business-mutation test across instances was added. Existing full
  domain regressions and cross-instance auth/CSRF/role tests are separate evidence.
- Late saves of deleted sessions can be rejected by the FK; the stale-save test
  asserts no authentication resurrection, not universal request success in races.
- Cleanup runs while instances run; expired access is denied even when cleanup
  waits during scale-to-zero. Account changes retain existing session-snapshot
  behavior; no new automatic revocation guarantee is introduced.
- Before task5: approve S1 and runtime-role privileges, take/verify a fresh backup,
  authorize named database credentials going to the intended Vercel project,
  confirm immutable source/CI, then perform preview acceptance before promotion.

Follow `doc/vercel-deployment-runbook.md`; no initial admin account was created.
