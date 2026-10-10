# PrimeSkill Vercel deployment runbook

## Candidate scope

Java17 Spring Boot/Thymeleaf remains one application. `Dockerfile.vercel` is the
deployment entrypoint; the ordinary Dockerfile and Compose stay unchanged.
Root `vercel.json` explicitly declares the container service and routes all paths
to it, following https://vercel.com/docs/services. On 2026-10-10 the connected
project silently produced an empty output even with Dockerfile.vercel present
(preview B3Xg14NJh8yaByJpDoqgLzsfzg2g, source14dcefd). Automatic detection alone
is therefore insufficient evidence for this project. Require container build logs
and an actual HTTP health response; a Ready badge alone is not acceptance.
The `vercel` profile explicitly enables JDBC sessions. Default local and test
profiles continue using servlet sessions. Do not run preview fixtures against Supabase.

The signed-in team is **aarktik's projects**, **Hobby**, checked on 2026-10-10.
PrimeSkill has not been created there. Container entitlement, effective resource
limits, startup time and actual OCI build remain to be confirmed on this account.
Do not upgrade a plan or accept paid commitments without the owner.

Official platform reference: [Running Docker on Vercel](https://vercel.com/kb/guide/docker).
Root `Dockerfile.vercel` routes the application through an OCI Function. Platform
default HTTP port is 80; explicitly set the project `PORT=8080` to match this image.

## Runtime variables

Supply these privately in the intended Vercel environment, after approval to
transfer the database credentials. Never use a Docker build argument for secrets.

- `PORT=8080`
- `SPRING_PROFILES_ACTIVE=vercel` (also in the image)
- `SUPABASE_DB_URL`: PostgreSQL JDBC URL for the confirmed session pooler, with TLS.
- `SUPABASE_DB_USERNAME`: approved runtime database role.
- `SUPABASE_DB_PASSWORD`: that role's secret, entered privately.

Use the session pooler on port5432, not the transaction pooler. The Spring JDBC
connection is distinct from Supabase REST/API keys. The URL must not contain the
password. An application role needs its existing business-table/sequence grants,
schema USAGE, and SELECT/INSERT/UPDATE/DELETE on the two session tables. Choose
and authorize that role before deployment; the migration administrator password
is not an implicit runtime-role decision. Preview should use an approved isolated
database; do not point untrusted PR previews at the shared production database.

SQL initialization is disabled and Hibernate validates business mappings.
The application does not auto-create session tables. Missing session schema must
be fixed through the approved migration process, not by enabling init.

## S1 review and migration

Draft: `doc/sql/drafts/S1__add_shared_http_sessions.sql`.
Source is Spring Session JDBC4.1.1 PostgreSQL schema, with principal_name widened
to255 to match users.email. All other expected columns/indexes/FK cascade are kept.
The script runs in one transaction, fixes search_path, sets lock/statement timeouts,
serializes installation with an advisory lock, and refuses any existing session
tables. A rerun is an inspection event, not a silently accepted installation.

Only the two new tables' PUBLIC/anon/authenticated grants are revoked. Any inherited
effective API table privilege makes the transaction fail. Existing business-table
grants are unchanged. Runtime-role grants are a separate operator-approved step.

Before production execution:

1. Obtain specific approval for S1 and runtime-role privileges.
2. Take a fresh restricted public-schema/data backup and verify restoration into
   a disposable PostgreSQL database. Record its checksum and scope limitations.
3. Run the complete file using `psql -v ON_ERROR_STOP=1`; inspect the transaction
   result, columns/indexes/FK and effective privileges before launching the app.
4. Record script checksum, time, operator and exact candidate. Do not invent
   Flyway history. The existing V7/V8/B1 approval does not authorize S1.

## Preview acceptance and release

1. Obtain approval for creating/importing PrimeSkill and transmitting the named
   secrets to the signed-in team. Import repository root and the tested immutable
   candidate; verify Dockerfile detection and account support before spending.
2. Deploy preview. Verify platform image build, Java17 startup, `/actuator/health`,
   HTML/CSS/JS, redirects, forwarded HTTPS origin and cold-start behavior.
3. Through real HTTPS inspect JSESSIONID: Secure, HttpOnly, SameSite=Lax, path `/`,
   no Domain. Confirm login rotates its ID, role menus/permissions are correct,
   CSRF rejects missing/wrong/cross-session tokens, and logout invalidates the
   login across requests/instances. Authenticated responses must not be cached.
4. Verify owner/member/admin on approved accounts. Ask for the initial admin
   identity and promotion authorization; do not install a fixture admin/password.
5. Record URL, source SHA, image/build identifier, database target, acceptance
   evidence and remaining limitations. Promote only after preview acceptance.

## Session lifecycle and rollback

### Cold-start budget

The observed container startup budget is approximately 28.5 seconds. The Vercel
profile uses lazy bean/repository initialization so HTTP can bind before
request-only components initialize. Hibernate schema validation remains enabled;
acceptance must exercise database-backed routes after startup, not only inspect
the provider's Ready badge. Lazy initialization can move errors to the first
request. The container uses `-XX:TieredStopAtLevel=1` to favor startup latency over
peak JIT throughput; local/default JVM invocations are unchanged.

Each container has a maximum of4 JDBC connections,0 minimum idle connections and
a10-second acquisition timeout. Total connections still scale with the number of
containers: check Supabase pooler capacity before increasing traffic. A runtime
timeout is not evidence that the configured port is wrong; inspect the Java
startup milestones and database connection result first.

Idle timeout is1800 seconds. Expired sessions are refused on access even if the
scheduled cleanup has not run. Spring Session cleanup runs every minute while
an instance is running; cleanup can wait while all Functions are scaled to zero.
Do not promise database row deletion exactly30 minutes after inactivity.

The Java principal has a stable serialVersionUID and transient passwordHash;
fresh authentication still verifies the password. Existing privileges remain a
session snapshot under the app's existing contract; disabling/changing an account
does not introduce a new automatic session-revocation guarantee.

Rollback the application to a compatible tested deployment. Keep additive session
tables, and do not drop them or restore business data as an automatic rollback.
Changes to serialized classes may require explicitly invalidating old sessions.
The image uses shell `exec` so Java receives SIGTERM; graceful container shutdown
and provider limits must still be checked on preview.

## Local reproducibility

- `scripts/mvn-java17.ps1 -f code/pom.xml test`
- `scripts/test-postgres.ps1 -Port <free-loopback-port>`
- `python scripts/test_vercel_configuration.py -v`
- Run existing Python report-validator tests and B/C coverage gates from CI.
- Within the disposable runner environment only, use
  `scripts/rehearse-shared-session-schema.ps1 -Backup <restricted-public.dump>`
  to restore a production schema copy and confirm eight business-table counts
  remain unchanged after S1. This never connects to live Supabase.

Actual local results and unresolved rollout checks belong in
`doc/vercel-session-verification-report.md`.
