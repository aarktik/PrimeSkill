# Vercel Shared Session Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task in this chat. Steps use checkbox syntax for tracking. Do not delegate unless the user requests it.

**Goal:** Deploy the existing PrimeSkill Java application on Vercel alone, with shared sessions in the existing Supabase database and unchanged UI/session/CSRF contracts.

**Architecture:** The entire Spring Boot/Thymeleaf application remains one OCI Function. An explicit deployment profile enables Spring Session JDBC; the default local session mechanism stays unchanged. Two additive session tables are installed only after disposable verification, fresh backup and specific production approval.

**Tech Stack:** Java17, Spring Boot4.1.1, managed Spring Session JDBC, PostgreSQL17.6 production / disposable PostgreSQL18.6 tests, Docker/OCI Vercel Functions.

**Spec:** [Vercel session design](../specs/2026-10-10-vercel-session-deployment-design.md)

## Global constraints

- Vercel is the only application hosting provider; no Render deployment.
- Preserve current UI, login/session/CSRF and business role contracts.
- JDBC sessions only on explicit deployment/test profiles; default/Compose behavior stays compatible.
- Expire inactive sessions after 30 minutes. Preserve session ID rotation and global logout.
- No passwordHash in persisted principal/session payloads.
- Production SQL init and session schema auto-init are disabled; no invented Flyway history.
- All implementation and tests use Java17. No production fixture harness execution.
- No credential values in Git, reports, screenshots or command output.
- Preserve uncommitted Supabase reports and previous rollout evidence.
- Do not apply new session DDL, expose a new service or transfer credentials to Vercel until the corresponding external action is authorized.

## Review focus

- Simultaneous responses can race to update a session: authentication, CSRF refresh and logout must not resurrect an invalidated login.
- Password/principal changes while a session exists must respect current application contract; do not accidentally widen privileges through serialization.
- Functions scaled down during expiration/cleanup: expired sessions must be refused on access; database cleanup may wait for a running instance.
- HTTPS proxy headers, cookie scope and redirect origin can differ from local tests: verify against the actual Vercel preview.
- Platform support/build limits for this account are not proven by the docs; check the actual deployment before claiming Vercel compatibility.

## File map

Modify:
- code/pom.xml — managed JDBC session dependency with explicit profile activation rather than global auto-activation.
- code/src/main/java/com/example/toolhub/security/UserPrincipal.java — safe serialization, stable serialVersionUID and exclusion of passwordHash.

Create:
- code/src/main/java/com/example/toolhub/config/JdbcSessionConfiguration.java — explicit vercel/jdbc-session-test configuration, timeout and cookie settings.
- code/src/main/resources/application-vercel.properties — SQL init off, validation, trusted-proxy handling, Secure cookies and API-docs policy.
- code/src/test/resources/application-jdbc-session-test.properties — disposable test configuration, no production endpoint.
- code/src/test/java/com/example/toolhub/security/UserPrincipalSerializationTest.java — password-free principal roundtrip.
- code/src/test/java/com/example/toolhub/SessionProfileIsolationTest.java — defaults do not require JDBC sessions.
- code/src/test/java/com/example/toolhub/JdbcSessionPostgresIT.java — real cross-instance HTTP/session/CSRF tests.
- code/src/test/java/com/example/toolhub/support/JdbcSessionTestInstances.java — two loopback application instances guarded by existing disposable-DB checks.
- doc/sql/drafts/S1__add_shared_http_sessions.sql — explicit additive PostgreSQL session schema, preflight/transaction/privileges guidance.
- Dockerfile.vercel — Java17 multi-stage OCI runtime with provider port alignment.
- doc/vercel-deployment-runbook.md — secret names, precise release procedure, rollback and account provisioning.
- doc/vercel-session-verification-report.md — actual results, SHA and unresolved limitations.

Checkpoint 2026-10-10: local implementation and regression are complete; see doc/vercel-session-verification-report.md. Actual OCI/account entitlement and task5 rollout remain pending.

## Task 1 — Principal persistence and explicit JDBC session activation

- [x] Inspect managed Spring Session4.1.1 APIs and auto-configuration contents before choosing imports. Prefer the managed `org.springframework.session:spring-session-jdbc` dependency plus explicit `@EnableJdbcHttpSession` configuration to avoid global starter activation. Verify with the profile-isolation test; do not assume classpath behavior.
- [x] Add serialization test with a synthetic UserPrincipal: roundtrip id/email/role/enabled/authorities, and assert decoded payload does not contain the synthetic password hash.
- [x] Add profile tests: default test profile has no SessionRepositoryFilter; jdbc-session-test has one and uses a JDBC SessionRepository.
- [x] Run the new tests with Java17 and confirm their intended failures before implementation.
- [x] Add the managed dependency, explicit profiled configuration and serialization changes. A transient passwordHash is an initial minimal implementation: verify no post-login code requires it from a deserialized principal. Do not alter authentication password verification for fresh login.
- [x] Preserve JSESSIONID through DefaultCookieSerializer if required by existing tests. Set cookie Secure through configuration (true on deployment, false only in loopback tests), HttpOnly=true and SameSite=Lax. Verify actual Set-Cookie, not just bean values.
- [x] Configure timeout1800 seconds and access-time expiration. Confirm the supported cleanup mechanism; do not assume a scheduler runs while Function instances are absent. Document cleanup limitations and test expired-row access.
- [x] Re-run new tests and existing authentication/security tests; inspect failures before changing contracts.

Expected deliverable: isolated session configuration and safe principal serialization without changing UI routes.

## Task 2 — Additive session SQL and real multi-instance acceptance

- [x] Obtain the PostgreSQL schema from the exact managed session JDBC library; copy with provenance/version and preserve expected column types/indexes/FK cascade.
- [x] Prepare S1 SQL using a transaction and explicit public schema/search_path. Require no incompatible existing session tables; compatible rerun must have a deliberate policy and never silently overwrite data. Apply constraints/index checks, lock/statement timeouts and rollback-on-error semantics.
- [x] Check default table grants and existing Supabase default privileges; session payloads must not be readable/mutable by anon/authenticated. Document the necessary revocations/application grants, scoped strictly to the new session objects. Do not alter existing business table grants incidentally.
- [x] Implement disposable test setup using PostgresTestDatabaseGuard before initialization. Do not supply real SUPABASE_DB_* values to the test runner.
- [x] Launch two independent servlet contexts/processes on random loopback ports sharing that disposable DB, with auto schema initialization disabled after fixture setup. Use actual HTTP cookie jars rather than MockHttpSession for persistence acceptance.
- [x] Write and run initially failing tests for: CSRF obtained on A usable on B; login on A visible on B; owner/member/admin context preservation; wrong/missing/other-session CSRF denied; session ID rotation; logout on B invalidates A; A restart preserves session; expired sessions denied by B; serialized password hash absent; inaccessible session table privileges.
- [x] Include concurrent login/logout or save races and ensure no authenticated session resurrection. Verify both role/CSRF payload persistence and database committed state.
- [x] Apply only minimal session/auth adjustments needed to pass. Never add production endpoint exceptions just for tests.
- [x] Rehearse backup restoration plus S1 schema on the actual production schema copy in a disposable database; compare the eight business tables/counts before/after.

Expected deliverable: reproducible PostgreSQL evidence for cross-instance authentication and safe additive schema, not production DDL.

## Task 3 — Vercel Docker and deployment profile

- [x] Add failing configuration/build assertions for Java17, non-root runtime, port alignment, no build-time secrets, SQL-init off and profile activation. Do not introduce Node/React frontend work.
- [x] Create Dockerfile.vercel following the existing multi-stage Maven/Temurin build. Map `${PORT:-8080}` explicitly into Spring `server.port` at launch; choose correct shell exec form so Java receives SIGTERM. Keep the ordinary Dockerfile/Compose unchanged.
- [x] Activate vercel profile explicitly in the platform runtime. Set server.address=0.0.0.0, SQL init=never, Hibernate validate, secure cookie and forwarded-header strategy for the trusted Vercel proxy. Disable Swagger/OpenAPI by environment/profile unless specifically requested.
- [x] Do not add the previous probe's read-only connection option to the writable deployment. Do not repeat the observed Hikari connection-init-sql binding issue.
- [x] Validate the image when a Docker builder is available. If local Docker remains unavailable, distinguish configuration review from image execution and require CI/platform image build before claiming success.
- [ ] Check Vercel account OCI availability, size/startup/duration constraints and selected plan. Do not upgrade/pay automatically.

Expected deliverable: deployment-specific image/profile with existing local workflow intact.

## Task 4 — Full regression and candidate handoff

- [x] Run Java17 targeted tests after each preceding task, then `scripts/test-postgres.ps1` on a free disposable loopback port for full verification.
- [x] Ensure JdbcSessionPostgresIT is included in the Failsafe `*PostgresIT` convention. Existing gates must still include B/C/D/E suites; add a session-specific gate only if the project workflow needs it, never replace existing coverage.
- [x] Run Python report-validator suites and combined coverage gate; report actual new totals, failures/errors/skips. Do not reuse old712 counts as proof of new code.
- [x] Record baseline/patched product trees, exact commands/logs and limitations. Run `git diff --check` and inspect only intended changes.
- [x] Present candidate changes/results. Commit/push only under user instructions; do not merge develop as an incidental part of deployment work.

Expected deliverable: reviewed, tested candidate and operator-readable report.

## Task 5 — External rollout after specific approvals

- [ ] Review and obtain approval for S1 migration and credential transfer to Vercel. Prior V7/V8/B1 approval does not cover either action.
- [ ] Take a new restricted public-schema/data backup, verify restore, apply complete S1 with approved privilege changes, then inspect columns/indexes/grants. Record checksum and release without fictitious Flyway history.
- [ ] Create/import PrimeSkill in the signed-in Vercel team after confirming GitHub permissions and service exposure. User handles legal/billing steps.
- [ ] Supply secrets privately; select immutable tested candidate; initially deploy preview. Do not screenshot environment values.
- [ ] Check health, assets/HTML, cold start, redirects, Secure/HttpOnly/SameSite cookie, actual session/CSRF/logout behavior and no caching of authenticated pages on preview.
- [ ] Obtain intended initial admin identity and authorization before account creation/promotion. No local fixture passwords or hidden admin bypass.
- [ ] Promote the verified deployment only after acceptance, then record URL/SHA/image and check production again. Rollback code separately from forward additive DB migration; do not drop live session/business tables automatically.

Expected deliverable: verified Vercel deployment and clear account/DB ownership, with no unreported production mutations.

## Execution order and current checkpoint

Tasks1–4 can be implemented/tested locally without changing real Supabase again. Task5 is deliberately gated by concrete database/service/credential approvals.

Executed sequentially in this chat, with one final read-only reviewer. Personal branch and previous reports preserved. Product changes remain uncommitted; no new production session tables or Vercel service created. Local Java730 and Python27 passed; task5 needs the stated external approvals.
