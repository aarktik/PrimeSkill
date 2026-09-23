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

## Database

`code/src/main/resources/schema.sql` is the initial PostgreSQL schema. Hibernate validates this schema and does not generate DDL automatically. The data dictionary belongs in [doc/data-dictionary.md](doc/data-dictionary.md).

## Development workflow

Create feature branches from `develop`, rebase before opening a pull request, and keep each module within its assigned package.
