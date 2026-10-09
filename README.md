# ToolHub / PrimeSkill

ToolHub is a platform for discovering, publishing, versioning, and reviewing software tools.

## Team

| Name | Student ID | Section | Branch | Responsibility |
| --- | --- | --- | --- | --- |
| | | | | |

## Technology

- Java 17, Spring Boot, Spring Data JPA, Spring Security
- PostgreSQL (Supabase)
- Session-based authentication and OpenAPI/Swagger UI

## Project structure

```text
code/   Spring Boot application and automated tests
test/   Test reports and supporting documentation
doc/    Documentation, diagrams, and slides
img/    Project images
```

## Local setup

1. Set `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, and `SUPABASE_DB_PASSWORD` in your environment.
2. Run `mvn spring-boot:run` from `code/` (use `mvn` if the Maven Wrapper is unavailable on your machine).
3. Open Swagger UI at `/swagger-ui.html` once API endpoints are available.

For an isolated local PostgreSQL stack, copy `.env.example` to `.env`, change its local password, then run `docker compose up --build` from the repository root. See [Role E local runbook](doc/role-e-local-runbook.md) for the current limitations and production checklist.

Run `mvn -f code/pom.xml verify` for the automated suite. The context test uses an isolated test database and does not require Supabase credentials.

To also replay Role E's integration and query-budget tests against a disposable local PostgreSQL database on Windows, run `./scripts/test-postgres.ps1` from the repository root. PostgreSQL binaries and Maven must be installed; use `-PostgresBin` and `-MavenCommand` if their locations differ. The script creates its own cluster under `code/target`, binds to loopback port 15432, runs `mvn -B -f code/pom.xml -Ppostgres-it verify`, and stops that cluster afterwards. It does not use Supabase credentials. See the [PostgreSQL test instructions](doc/role-e-local-runbook.md#postgresql-regression-tests) and [migration readiness checklist](doc/role-e-migration-readiness.md).

## Publishing and versions

For cross-role follow-up, start with the [PostgreSQL, review race and migration handoff plan](docs/superpowers/plans/2026-10-07-postgres-race-handoff.md). It assigns A/D/E/C tasks, reproduction commands, dependencies and acceptance checks; the race fix remains a reviewable patch until integrated into the agreed production branch.

Owners manage draft versions at `/dashboard/tools/{id}/versions` and submit tools for approval. Admins review pending tools at `/admin/tools`. Public version history is available at `/tools/{slug}/versions` for published tools. The REST endpoints are under `/api/v1/tools/{id}` and `/api/v1/admin/tools/{id}`. See [Role E design](doc/role-e-design.md) for transitions, ownership, and integration points.

The [Role E runbook](doc/role-e-local-runbook.md) explains each module in Thai, session/CSRF API usage, integration test commands, and the remaining Docker/PostgreSQL checks.

## Database

`code/src/main/resources/schema.sql` is the initial PostgreSQL schema. Hibernate validates this schema and does not generate DDL automatically. The data dictionary belongs in [doc/data-dictionary.md](doc/data-dictionary.md).

## Development workflow

Create feature branches from `develop`, rebase before opening a pull request, and keep each module within its assigned package.
