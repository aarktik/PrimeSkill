# Vercel-only deployment with shared PostgreSQL sessions

## Intent and current state

The user selected Vercel as the only application hosting provider and has signed in to team `aarktik's projects`. Supabase project jayichnxeyhvnmranutd remains the database. Preserve current Spring Boot4.1.1/Java17/Thymeleaf UI and session/CSRF contracts; no frontend rewrite or Render service.

Base product release: develop124cfc27, product equivalent to fe1029d. Personal branch has documentation commit2c6aff8 and uncommitted database rollout reports; preserve those files.

V7/V8/B1 were applied with approval, with backup and restore rehearsal. That approval does not silently authorize new session tables or transferring DB credentials to another provider. No PrimeSkill Vercel project exists yet in the inspected dashboard.

## Architecture decision

Run the complete application as a Vercel OCI container Function using a root Dockerfile.vercel. All HTML, assets and APIs remain in the same application/origin. Supabase is accessed server-side through an SSL PostgreSQL session-pooler connection.

Use Spring Session JDBC backed by two PostgreSQL tables, SPRING_SESSION and SPRING_SESSION_ATTRIBUTES (actual PostgreSQL lower-case unquoted names). Reuse the existing database rather than introducing Redis. Memory-only sessions are not sufficient across Function instances or scale-down. Stateless token authentication would require changing existing login/CSRF semantics and is outside this scope.

## Session behavior and isolation

- Use the managed Spring Boot session JDBC dependency compatible with Boot4.1.1.
- Enable JDBC session storage only for an explicit Vercel deployment profile. Local/Compose/regression profiles retain their current memory session behavior. Verify the exact Boot4 auto-configuration mechanism before implementation; adding the dependency must not implicitly activate JDBC sessions in all existing tests.
- Preserve the existing session cookie name where tests/contracts depend on it; ensure Secure, HttpOnly, SameSite=Lax and host-only cookie behavior over HTTPS.
- Maintain session ID rotation on successful authentication and global invalidation on logout.
- Persist CSRF and security context so another instance can process the same browser session.
- Serialize only the principal information required by authentication/authorization; do not persist its passwordHash in session payloads. Inspect the actual serialization result in tests.
- Use 30-minute inactive expiration, verified across instances. Expired sessions must be rejected even when no cleanup worker was running while Functions were scaled down. Cleanup must not assume an always-running scheduler.
- Deserialize session data only from the restricted server-managed database. Review DB role/table grants; never expose session tables through browser Data API access.

## Container and platform configuration

- Retain Java17 build and non-root runtime conventions of existing Dockerfile; create Vercel-specific file without changing local Docker flow.
- Bind 0.0.0.0 and align Spring server.port with Vercel PORT (current documentation says default80; existing Dockerfile exposes8080). Do not rely on automatic Spring mapping of PORT.
- Disable SQL initialization and automatic session schema creation in deployed runtime. Hibernate remains validate. Apply session DDL as a separate operator migration.
- Set forwarded-header handling for the trusted hosting proxy and Secure cookies. Inspect real cookie/redirect behavior instead of assuming local success proves proxy correctness.
- Disable API-docs/Swagger endpoints for public deployment unless the team explicitly requests them.
- Store database credentials only in Vercel server-side secrets. No public frontend environment variables contain the DB password.
- Confirm OCI Function availability/limits for this actual account before committing to external deployment; documentation support is not proof that the team's plan accepts this service.

## Database rollout proposal

New session schema is additive. Prepare explicit SQL from the compatible Spring Session PostgreSQL schema, use a single transaction with timeout/preflight guards, and record its checksum/release. Do not enable baseline-on-migrate or invent Flyway history.

Rehearse against a disposable copy, verify session persistence/expiration, then obtain explicit approval to apply these two tables to the real Supabase project. Back up again immediately before production session DDL. Keep existing eight business tables and their contents unchanged. Verify no anon/authenticated privileges expose session data; final application role needs only the verified runtime privileges.

## Required verification (after user authorizes implementation testing)

1. Java17 existing Java/PostgreSQL suites and Python gates remain green.
2. Two real application instances sharing a disposable PostgreSQL DB: obtain CSRF/register fixture/login on A, access authenticated endpoint and mutation with correct CSRF on B; reverse roles.
3. Old session ID after login rotation rejected; wrong/missing/other-session CSRF rejected.
4. Logout on B invalidates subsequent A request. Restart A without losing valid shared session. Expired sessions rejected by another instance.
5. Stored session attributes do not contain password hash; permission/admin state round-trips correctly; error pages preserve role navigation.
6. Local default profile and existing Compose workflow do not require session tables; deployed profile fails clearly if required tables are missing.
7. Container config/build verification and Vercel preview: cold start, callback/redirect origin, assets, health, cookie properties, session continuity and no caching of personalized/auth endpoints.
8. Production acceptance uses explicitly designated accounts/records; never point disposable fixture harnesses at the real database.

## External actions and approval boundaries

This is an architectural change to authentication/session storage. Read-only exploration and this design are complete. Review the design before producing the implementation plan. Implementation plan then needs the user's execution choice under the brainstorming workflow.

Creating external projects, granting GitHub access, transferring the DB password to Vercel and applying new production DDL require clear authorization for the specific actions. The user handles billing/legal signup interactions. No deployment is claimed until actual URL/SHA and behavior are verified.

## Sources

- https://vercel.com/kb/guide/docker — root Dockerfile.vercel, OCI Functions, port resolution and scale-down behavior (checked10October2026).
- Local code: AuthController stores SecurityContext in HttpSession; SecurityConfig uses IF_REQUIRED; UserPrincipal currently holds passwordHash; Boot dependency management includes starter-session-jdbc.

## Completion criteria

Vercel is the only application host; Supabase stores business data and shared sessions; existing UI/role contracts remain intact; cross-instance/restart/CSRF/logout checks pass; exact deploy SHA and URL plus limitations recorded. No claim of completion from a successful build alone.
