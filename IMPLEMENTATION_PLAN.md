# Primeskill — Implementation Plan

> โครงการรายวิชา CP353002 — Principles of Software Design and Development  
> เอกสารกลางของทีมสำหรับการพัฒนา รีวิว PR เอกสาร และส่งต่อบริบทให้ AI assistant  
> เวอร์ชัน 0.2 — Maven, Supabase PostgreSQL และ Thymeleaf ยืนยันแล้ว; deployment/CI และรายชื่อ Role A–E ยังรอยืนยัน

## Product vision

**Primeskill** คือเว็บ registry และ marketplace สำหรับเครื่องมือ/ปลั๊กอินที่ใช้กับ AI agents: นักพัฒนาลงทะเบียน เผยแพร่ metadata/version และส่งให้ admin อนุมัติ; ผู้ใช้ค้นหา กรอง จัดเรียง ให้คะแนน และเขียนรีวิวได้. คำว่า ToolHub ใน draft เก่าเป็นเพียงชนิดระบบ ไม่ใช่ชื่อผลิตภัณฑ์—code, DB, diagram และงานนำเสนอต้องใช้ **Primeskill** ให้ตรงกัน. งานต้องออกแบบใหม่ ห้ามคัดลอก source/UI/wording/branding จาก registry อื่น.

### เป้าหมายและเกณฑ์สำเร็จ

- ครบ flow สมัคร → publish → discover และ 4 core use cases แบบ end-to-end
- แสดง SOLID, Layered Architecture และ GoF Behavioral patterns ได้จริง
- REST API มี validation/security/error format/OpenAPI ที่สม่ำเสมอ
- SQL จริง พร้อม Flyway, constraints, indexes; test, Docker และ public deployment
- CRUD อย่างน้อย `tools`, `reviews` (เพิ่ม profile update และ version management)
- มี 8 application tables พร้อม 1:1, 1:N, N:M; Swagger ที่ `/swagger-ui.html`
- list endpoint มี pagination/sorting/search/filter; anonymous/USER/ADMIN ถูกจำกัดสิทธิ์ถูกต้อง
- ก่อน merge ต้องผ่าน `mvn -f code/pom.xml verify`; หลัง deploy ต้องผ่าน smoke test

## Decisions and scope

| เรื่อง | Baseline |
|---|---|
| Backend | Spring Boot 3.x, Java 17, Maven |
| Database | PostgreSQL บน Supabase; local PostgreSQL ผ่าน Docker |
| Frontend | Thymeleaf + Bootstrap หรือ CSS ทีม (single service) |
| Auth | Spring Security แบบ session-based; ไม่ใช้ JWT เว้นแต่มี ADR |
| Migration/API docs/Mapping | Flyway / springdoc-openapi / mapper class เขียนเอง |
| Official GoF set | Behavioral: Strategy + Observer + State |
| Deployment | ต้องเลือกระหว่าง Render/Railway/Fly.io ก่อนจบ Sprint 0 |

### In scope (MVP)

Registration/login/logout + BCrypt, profile, USER/ADMIN, owner tool CRUD/admin approval, category/tag association, tool versions, public browse/search/filter/sort/pagination, review 1–5 หนึ่งรายการต่อ user/tool, rating aggregate, Swagger, standard errors, Bean Validation, Flyway + dev/demo seed, Thymeleaf core pages, automated tests, Docker/local Compose/public deployment, UML/architecture/data dictionary.

### Out of scope

OAuth/email verification/forgot password, payments, binary upload, real-time chat/ML/semantic search, multiple admin roles, native mobile, และ webhook/email จริง (Observer ใช้ in-app/log/audit listener).

### Decision gate ก่อนเริ่ม

ยืนยัน Java package (เช่น `com.primeskill.platform`), deployment provider/owner/secrets, จะทำ CI/CD bonus หรือไม่, ชื่อจริง/รหัส/section ของ A–E และ branch, UI style guide, policy edit/delete review, policy version ใหม่ของ tool ที่ published, GitHub/reviewer rotation/branch protection. การเปลี่ยน baseline ต้องทำ ADR ใน `doc/adr/` และแก้เอกสารนี้ใน PR เดียวกัน.

## Ownership and integration

| Role | Tables/domain | Deliverable และ shared contract |
|---|---|---|
| A — User & Auth | `users`, `user_profiles` | register/login/logout/profile, BCrypt/session security/USER-ADMIN; `ErrorResponse`, exception handler, current-user pattern |
| B — Tool Catalog | `tools`, `categories` | tool CRUD/category management/ownership; OpenAPI config/tool DTO |
| C — Search & Browse | `tags`, `tool_tags` | public search/filter/tag/sort/pagination; Strategy and paged response |
| D — Review & Rating | `reviews` | review CRUD/aggregate; Observer and demo seed |
| E — Publishing/Versioning/Deploy | `tool_versions` | version CRUD/moderation/Docker/deploy/CI; State and runbook |

Integration: A merges User/auth/error foundation first; B merges Category/Tool; C/D/E then integrate against merged contracts; combine browse + rating; publishing approval + UI; hardening/docs/deploy. Do not copy unmerged entities across branches. C may prepare Tag/search contracts/tests while waiting for Tool, but must not create a duplicate Tool or take Category ownership.

## Architecture rules

```text
Browser/API → Presentation (REST + Thymeleaf MVC) → Application/Service
           → Repository (Spring Data JPA) → Domain (entities/enums) → PostgreSQL
Cross-cutting: config, security, DTO/mapper, exception, events, pagination
```

Package root: `config`, `controller/api`, `controller/web`, `service/impl`, `repository`, `domain/entity`, `domain/enums`, `dto/request`, `dto/response`, `mapper`, `security`, `event`, `exception`, `common` under `code/src/main/java/com/primeskill/platform/`.

Resources: `db/migration`, `templates`, `static`, `application.yml`, `application-dev.yml`, `application-prod.yml`. JUnit stays in Maven conventions (`code/src/test/...`); root `test/` is for plan/report/manual evidence only.

- Controller only validates/maps HTTP; service owns business rules, authorization and transactions; repository only persistence/query.
- Never expose entity directly; request/response DTOs separate. Mapper has no repository/business decision.
- Constructor injection only; interface by real use case; LAZY collections by default; use projection/entity graph/join fetch to prevent N+1.
- SOLID: separated concerns (S), extensible sort strategies (O), valid same-contract implementations (L), small service interfaces (I), controllers/high-level services depend on interfaces (D).

## Official GoF patterns (all Behavioral)

1. **Strategy:** `ToolSortStrategy` with `NewestToolSortStrategy`, `PopularityToolSortStrategy`, `RatingToolSortStrategy`, `RelevanceToolSortStrategy`; `ToolSearchService` selects through `ToolSortOption`. Never sort all records in memory.
2. **Observer:** `ReviewService` publishes `ReviewCreatedEvent` after successful save; audit/notification listener uses `@TransactionalEventListener(phase = AFTER_COMMIT)` and cannot break the main transaction.
3. **State:** `PublishingState`/handler registry controls `DRAFT → PENDING → PUBLISHED → DEPRECATED` actions; controller never writes status directly.

| Current | Action/actor | Next | invalid |
|---|---|---|---|
| DRAFT | submit owner | PENDING | 409 |
| PENDING | approve ADMIN | PUBLISHED | 409 |
| PENDING | reject ADMIN | DRAFT | 409 |
| PUBLISHED | deprecate owner/ADMIN | DEPRECATED | 409 |
| DEPRECATED | restore owner | DRAFT | 409 |

Builder/Factory/Facade may help but are not part of the official three.

## Data model (8 tables)

```text
USERS 1—1 USER_PROFILES
USERS 1—N TOOLS; CATEGORIES 1—N TOOLS; TOOLS 1—N TOOL_VERSIONS
USERS 1—N REVIEWS; TOOLS 1—N REVIEWS
TOOLS 1—N TOOL_TAGS N—1 TAGS
```

| Table | Essential fields/constraints |
|---|---|
| `users` | `id`, lowercase unique `email`, `password_hash` BCrypt, `role` USER/ADMIN, created/updated timestamps |
| `user_profiles` | `id`, unique FK `user_id`, `full_name` (120), optional `avatar_url` (500), `bio` (1000) |
| `categories` | `id`, unique `name` (100), unique `slug` (120), optional `description` (500) |
| `tools` | `id`, indexed FKs owner/category, `name` 150, unique `slug` 170, descriptions, optional `repository_url`, indexed `status`, `view_count` default 0, audit timestamps |
| `tags` | `id`, unique `name` (80), unique `slug` (90) |
| `tool_tags` | composite PK (`tool_id`,`tag_id`), FKs; tag FK indexed |
| `reviews` | `id`, indexed `user_id/tool_id`, `rating` CHECK 1..5, `comment` 2000, audit timestamps, UNIQUE (`user_id`,`tool_id`) |
| `tool_versions` | `id`, indexed FK tool, `version_number` 50, notes, optional manifest URL, optional HOSTED/LOCAL type, UNIQUE (`tool_id`,`version_number`) |

User-profile and tool-version lifecycle use appropriate cascade/orphan removal. Do not broadly cascade user/review relations. Restrict deleting referenced categories. Consider `@Version` on mutable aggregates. Flyway sequence: V1 users/profiles, V2 categories/tools, V3 tags/tool_tags, V4 reviews, V5 versions, V6 indexes/constraints. Production uses `ddl-auto=validate`; demo seed is dev/test only and contains no real password.

## REST contract

Base `/api/v1`, camelCase JSON, UTC ISO-8601 offsets.

- Public auth: `POST /auth/register` (201), `POST /auth/login` (200 + session); authenticated `POST /auth/logout` (204), `GET /users/me`, `PUT /users/me/profile`.
- Tools: public `GET /tools` (published only), `GET /tools/{idOrSlug}` (owner/admin may see non-public); authenticated/owner CRUD and `POST /tools/{id}/submit`, deprecate; admin approve/reject at `/admin/tools/{id}/...`; version list/CRUD nested under tool.
- Reviews: public GET `/tools/{toolId}/reviews`; authenticated POST; author/admin-policy PUT/DELETE.
- Reference data: public GET categories/tags; admin mutations at `/admin/categories/...`, `/admin/tags/...`.
- Search: `GET /tools?q=&category=&tags=&sort=newest&page=0&size=20`; q searches name/short/full description with escaped wildcards; tags are comma-separated and baseline is ANY; max 100; allowed sort `newest|popular|rating|relevance`; stable secondary `id`; relevance with no q falls back to newest.

Paged response: `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.

Status: 200 read/update/action; 201 create (+Location if possible); 204 delete/logout; 400 invalid input; 401 unauthenticated/bad credentials; 403 unauthorized actor; 404 absent/not revealed; 409 duplicate or invalid transition; 500 safe generic response.

## Error, validation and security

REST error shape: `timestamp`, `status`, `error`, `code`, `message`, `path`, `fieldErrors[{field,message}]`, optional `traceId`. Map invalid validation → `VALIDATION_FAILED`; malformed JSON → `MALFORMED_REQUEST`; bad login → 401 `INVALID_CREDENTIALS`; denied → 403; not found → 404; duplicate → 409 `RESOURCE_CONFLICT`; state → 409 `INVALID_STATE_TRANSITION`; fallback → 500 `INTERNAL_ERROR`. REST advice must not turn Thymeleaf errors into JSON.

Validate email (blank/email/max255; trim/lowercase), password 8–72, full name 2–120, allowed http/https URLs, schema-aligned lengths, rating 1–5 at Bean+DB level, pagination page ≥0/size 1–100, enum sort allowlist. Escape Thymeleaf output; sanitize any future rich text.

Spring Security: BCrypt reasonable cost, session `HttpOnly`, production `Secure`, `SameSite=Lax`, fixation protection, CSRF enabled for browser mutations, same-origin restricted CORS, method security at service boundary, no actor/owner/role supplied by request, generic login failure/no password or session log. Public static/home/auth/public GET/reference data/Swagger per demo policy; authenticated profile/tool/version/submit/review; admin moderation/reference mutations. Admin bootstrap credentials come only from environment/dev docs.

## UI, testing and deliverables

Pages: `/`, `/tools`, `/tools/{slug}`, `/register`, `/login`, `/profile`, `/dashboard/tools`, tool editor/version pages, `/admin/tools`, and common `/error`. Shared fragments are navbar/footer/flash/tool-card/pagination/form error/rating. Responsive, keyboard usable, labels/focus clear, never color-only status.

Testing uses JUnit 5, Mockito, Spring Boot Test, MockMvc and preferably Testcontainers PostgreSQL (not H2 alone): unit service/mapper/pattern; repository filters/constraints/aggregates; MVC validation/error/status; security anonymous/USER/owner/non-owner/ADMIN + CSRF; full smoke `register → login → create → submit → approve → browse → review`. Critical positive, negative and authorization paths are mandatory.

Documentation source is Mermaid/PlantUML under `doc/`, with exported artifacts as needed. Required scenarios: registration/login, publish/approve, browse/review. Class diagram marks `<<Strategy>>`, `<<Observer>>`, `<<State>>` and reflects real code. README includes identity/team/stack/architecture/ER/run/migrate/seed/test/Swagger/deploy/demo/project links. `solid-analysis.md` and `design-patterns.md` cite actual files/classes.

## Sprints, Git and operation

- **Sprint 0:** decide gates, bootstrap `code/test/doc/img`, profiles/Flyway/Docker Compose, contracts/ADR/diagram skeleton, E deploys health skeleton. Exit: app/DB/empty CI work for everyone.
- **Sprint 1:** A auth foundation; B categories/tools; E version/deploy skeleton. Exit: authenticated draft creation and merged contracts.
- **Sprint 2:** C search/tags/Strategy, D reviews/Observer, E state/moderation, B OpenAPI. Exit: core REST + Swagger flows.
- **Sprint 3:** Thymeleaf integration, CSRF/validation/flash/navigation, E2E, performance/accessibility. Exit: browser demo works.
- **Sprint 4:** docs, production secrets/Docker/health/deploy, CI, rehearsal/regression. Exit: public URL/runbook/artifacts.

Branches must exactly follow teacher format `name_studentid_section`; personal → PR → `develop` → release PR → `main`. No commit/push for a teammate. Every merge needs a reviewer; PR documents approach, schema/API/tests/screenshots/migration and `mvn -f code/pom.xml verify` must pass. Target at least 15 meaningful vertical commits per person—not artificial commits. Never force-push others' branches.

Required files: multistage non-root `Dockerfile`, `docker-compose.yml`, `.dockerignore`, `.env.example`, `/actuator/health`, GitHub Actions build/test (optional deploy). Production Flyway before traffic, `ddl-auto=validate`, proxy headers, safe logs, health/restart/resources/backup plan. Env includes profile, database credentials/URL, controlled admin bootstrap, secure cookie flag; never commit Supabase credentials/URLs/passwords.

## Role A detailed plan

A is critical path. A1: Role/User/Profile entities, migration/repositories, atomic normalized unique email/profile creation, error DTO contract. A2: registration DTO/mapper/service/BCrypt and 201/400/409 tests. A3: UserDetails adapter/security chain/session fixation/CSRF + login/logout MockMvc tests. A4: `CurrentUserProvider`, ProfileService and own profile GET/PUT, preventing mass assignment. A5: immutable errors, typed exceptions, REST/security handlers, safe trace logging with tests for 400/401/403/404/409/500. A6: cross-team route/method security/IDOR integration review and security matrix. Required: User may publish/review but not approve; admin approves; non-owner cannot mutate others.

## Definition of done and next actions

Feature done means contract behavior, validation/auth/error paths, forward-only Flyway from empty DB, DTO secrecy, appropriate passing tests, docs/Swagger/UI states updated, no field injection/controller business logic/known N+1/secret, approved PR, and owner can explain/demo it.

Immediate: review decision gate; create GitHub/real branches; A/B/E open contract-first foundation issues/PRs; create Spring Initializr with Web/Thymeleaf/Validation/Security/JPA/PostgreSQL/Flyway/Actuator/springdoc/Test; configure local and Supabase/CI; create owned sprint issues; weekly integration demo. AI work must read this plan, preserve contracts/ownership, use small diffs/tests, explain decisions, and never commit/push for students.
