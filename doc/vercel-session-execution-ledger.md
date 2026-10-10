# Vercel session execution ledger

Plan: docs/superpowers/plans/2026-10-10-vercel-session-deployment.md

User approved sequential implementation in this chat on 10 October 2026.

Ruling: work in the existing personal branch thaninton_673380043-6_02 — the approved plan explicitly preserves this branch and pending reports; no new worktree or shared-branch merge is needed. Product changes are reviewable before external rollout.

Task 1 started: serialization and profile isolation tests written before production changes. No new production session schema or Vercel service created.

Task 1 RED confirmed: password hash persisted for both roles; deployment config absent. Initial test fixture constructor corrected before RED.

Task 1 GREEN: 4/4. Task 2 RED: missing S1; harness errors corrected (HTML Accept header and stable instance-B datasource after restarting A). Task 3 config RED missing files, GREEN 2/2. Task 2 long-email regression running before width adjustment.

Ruling: widen S1 principal_name to varchar(255), matching users.email, rather than rejecting existing valid email identities. Exact managed schema otherwise retained. RED longEmailStillLogsIn: HTTP500 due varchar(100), 7 other tests passed. Cost if wrong: wider index key only; PostgreSQL supports it.

Ruling: non-owner member GET of another user's unpublished draft expects existing 404 visibility contract (ToolServiceImpl.findVisibleByIdOrSlug), not 403. Preserves current behavior. Added deployment cookie and owner/member/admin HTTP tests. Account inspected read-only: aarktik's projects Hobby; entitlement/OCI execution unproven; no project/secrets sent.

Additional ACL RED proved inherited TRUNCATE could survive direct revocation. S1 now checks all seven effective table privileges for both API roles and rolls back atomically if any remain.

Task 2 targeted GREEN: 14/14 actual HTTP/PostgreSQL tests, including deployment Secure cookie, distinct owner/member/admin permissions, long email, inherited privilege refusal/rollback. Backup restore initially hit default public-schema collision; rehearsal now drops only the empty default schema in the freshly created disposable DB and reconstructs V7/V8/B1 before S1. Full regression/rehearsal running on loopback15462. No live Supabase connection used.
Task 3 local configuration GREEN: 2/2; no Docker executable/daemon available. Added CI image build, not yet run remotely. Existing Dockerfile/Compose unchanged.
Final fresh reviewer read-only: no blocking bugs/security/contract regressions. Deferred coverage observations: no explicit missing-session-table deployment startup test; authenticated business mutation is not a dedicated cross-instance HTTP case (auth/login/logout/role checks are). OCI image execution and real Vercel proxy/cache acceptance remain rollout gates.
Python GREEN: existing validators8+9+8 plus Vercel config2 =27/27. Full Java/PostgreSQL results pending.

Task 4 GREEN: full Surefire381 + Failsafe349 =730 Java; no failures/errors/skips. Python27 and both B/E and C gates pass. Actual public-backup restore + reconstructed V7/V8/B1 + S1 passed; eight business counts unchanged; rerun refused and both session tables preserved. Cluster stopped. Minor EOF-only whitespace normalized in three files after full run; targeted checks repeated below. Source hashes will identify final candidate.
Ruling: preserve personal branch with uncommitted reviewable changes as explicitly required by plan; no integration menu, commit/push or develop merge. Task5 remains gated, and Docker/account/preview checks are explicitly unverified.

Final targeted GREEN after EOF normalization: principal/profile4 + actual HTTP/PostgreSQL14; backup restore/S1/rerun checks passed again. Both combined gates still730; all fourteen candidate files pass whitespace inspection. Final source snapshot516587e976c0c946fc7d5e498bf71be5c17a0fd51d196b19ec056cd7d5e66c01. Final S1 SHA256b11ab569e92b8bd7a5e6d40004888bbaf4363ce66d906c674a71b9ee2896c31d. Reports/runbook ready. Task5 not executed. No commit/push.

User authorized commit/push to personal branch on 2026-10-10. Rechecked all fourteen source hashes against verified snapshot: unchanged. Remote branch matches local baseline before commit. Archived source manifest in doc for portable review; no production action authorized by this publishing step.

Task 5 update (10 October 2026): user explicitly asked to continue Vercel rollout.
Fresh restricted Supabase public-schema backup verified (SHA-256
E8B892F1AB1191D5F7B15AB645E47A08A22D5A44A2647D42FE3AB0AB0F3981AE), restore
rehearsal including S1 and rerun refusal passed, then S1 was applied to project
jayichnxeyhvnmranutd (SQL SHA-256
B11AB569E92B8BD7A5E6D40004888BBAF4363CE66D906C674A71B9EE2896C31D). Read-only
postflight passed; see the production migration report for scope and residual
service_role structural grants. Dedicated runtime role creation was rejected by
automatic command review, so no app login/password was created or transmitted.
Vercel import is prepared for aarktik/PrimeSkill, but it selects `main`, not the
reviewed thaninton_673380043-6_02 candidate at 14dcefd6f5f2081b79e98c2ba96519e54f5c3b21.
No project was created and no deployment/secrets were submitted. Resolve the
role and branch before deploy.

## Deployment repair 2026-10-10
- Current develop124cfc2 lacks candidate14dcefd. Created PR8 to develop.
- Preview B3Xg14NJh8yaByJpDoqgLzsfzg2g from14dcefd also emitted empty output in304ms without Docker/Maven. Root directory empty, overrides off, Fluid enabled.
- Ruling: explicitly declare one container service and catch-all rewrite using documented services configuration; test on personal branch before merge. Cost if unsupported: failed preview, no database changes.
- New route-to-container configuration test failed because vercel.json was absent, then passed with explicit config; all3 configuration tests passed. Platform acceptance remains pending.
## Cold-start repair 2026-10-10
- ca21be5 CI: 8/8 checks passed. Actual container Preview built successfully.
- Operator manually created primeskill_runtime, set its password, granted scoped app/session table CRUD and owned ID-sequence USAGE, then entered username/password in Vercel. Password was not read by the agent.
- Redeploy 5ef35p5a1UL7TW64vN1pdoL2xyu8 on ca21be5 received current environment variables. Logs prove successful Hikari PostgreSQL 17.6 connection and Hibernate schema validation/EntityManagerFactory initialization.
- Runtime still failed: no TCP listener before Vercel startup timeout (~28.5s). Tomcat initialized for8080 correctly; port configuration is not the root cause.
- Scoped repair: Vercel-only lazy bean/repository initialization and JVM tier1 startup compilation; retain SQL init=never, Hibernate validate, session/CSRF/security, Java17 and non-root runtime. Bound each container's pool to4 connections with0 idle and10s acquire timeout.
- Acceptance must include real HTTP requests after the listener is bound; lazy initialization can defer failures until first use. Build/Ready alone is insufficient.
- Local gate: Java17 Surefire381 + PostgreSQL Failsafe349 =730 tests passed, no failures/errors/skips. Expanded first-use deployment-profile HTTP checks passed. Vercel configuration checks3/3 passed. Disposable PostgreSQL stopped cleanly.
