# Role E design and handoff

This describes the publishing and versioning code in this branch. It does not authorize changes to shared or production databases.

## Modules and responsibilities

- `PublishingState` and `PublishingStateMachine` implement the GoF **State** pattern. Each state handler owns its allowed actions. Unsupported actions throw `InvalidStateTransitionException`, mapped to HTTP 409 with `INVALID_STATE_TRANSITION`.
- `PublishingServiceImpl` loads the tool, checks the actor (owner or admin according to the action), asks the state machine for the next status, and updates the managed entity in a transaction. The controller does not set status.
- `ToolVersionServiceImpl` handles nested version reads and draft-only owner writes. It checks for duplicate version numbers before writing; the database unique constraint is the final guard against concurrent duplicates.
- Publishing transitions and version writes take a pessimistic lock on the same Tool row. This serializes a submit action with a concurrent draft version edit.
- `PublishingRestController` and `ToolVersionRestController` expose REST endpoints. `RoleEWebController` exposes the Thymeleaf pages and post/redirect/get mutations. All three use the same services.
- `RoleEWebExceptionHandler` gives scoped, safe web responses for 403, 404 and 409. The shared REST exception handler retains its JSON contract.

## Publishing sequence

```mermaid
sequenceDiagram
    actor Owner
    participant API as Publishing controller
    participant Service as Publishing service
    participant State as State handler
    participant DB as Tool repository
    actor Admin
    Owner->>API: POST /tools/{id}/submit
    API->>Service: transition(SUBMIT, owner)
    Service->>DB: load Tool
    Service->>Service: verify owner
    Service->>State: DRAFT + SUBMIT
    State-->>Service: PENDING
    Service-->>API: updated ToolResponse
    Admin->>API: POST /admin/tools/{id}/approve
    API->>Service: transition(APPROVE, admin)
    Service->>DB: load Tool
    Service->>Service: verify ADMIN
    Service->>State: PENDING + APPROVE
    State-->>Service: PUBLISHED
    Service-->>API: updated ToolResponse
```

## State transitions

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> PENDING: submit / owner
    PENDING --> PUBLISHED: approve / ADMIN
    PENDING --> DRAFT: reject / ADMIN
    PUBLISHED --> DEPRECATED: deprecate / owner or ADMIN
    DEPRECATED --> DRAFT: restore / owner
```

## Role E class relationships

```mermaid
classDiagram
    class PublishingState {
      <<State>>
      +status() ToolStatus
      +transition(action) ToolStatus
    }
    class PublishingStateMachine {
      +transition(current, action) ToolStatus
    }
    class PublishingServiceImpl {
      +transition(toolId, action, actor) ToolResponse
      +listPending(pageable, actor) Page
    }
    class ToolVersionServiceImpl {
      +list(toolId, actor) List
      +get(toolId, versionId, actor) ToolVersionResponse
      +create(toolId, request, actor) ToolVersionResponse
      +update(toolId, versionId, request, actor) ToolVersionResponse
      +delete(toolId, versionId, actor) void
    }
    PublishingStateMachine o-- PublishingState
    PublishingServiceImpl --> PublishingStateMachine
    PublishingServiceImpl --> ToolRepository
    ToolVersionServiceImpl --> ToolVersionRepository
    ToolVersionRepository --> ToolVersion
    ToolVersion --> Tool
```

## Local deployment design

This diagram describes the prepared Compose configuration. Container startup and restart persistence have not been verified on this workstation because Docker is unavailable.

```mermaid
flowchart LR
    Client[Browser or API client] -->|HTTP localhost:8080; session and CSRF| App[Spring Boot app container]
    App -->|JDBC; internal Compose network| DB[PostgreSQL 17 container]
    DB --> Volume[(postgres_data volume)]
    Probe[Health probe] -->|GET /actuator/health| App
    CI[GitHub Actions: Maven verify] --> Build[Docker image build]
```

Production HTTPS, host, secrets and migration rollout remain team decisions. The workflow builds an image but does not publish or deploy it.

## Remaining integration points

1. The current `develop` branch provides session authentication and a `UserPrincipal` that implements `AuthenticatedUserPrincipal#getId()`, which Role E consumes through `CurrentActorProvider`. GET access to published version history is public; version mutations and publishing actions require authentication, admin actions require `ADMIN`, and state-changing web/API requests must carry CSRF tokens. `RoleEFlowIntegrationTest` verifies the real login-to-publish API flow with security filters and H2. Browser cookie behavior and PostgreSQL deployment verification remain pending.
2. The committed `schema.sql` has a `tool_versions.version VARCHAR(100)` and `release_notes`. The plan proposes `version_number VARCHAR(50)` plus optional manifest URL/type. A forward migration and data policy must be agreed before changing shared database schema. The `doc/sql/V7__align_existing_tool_catalog.sql` draft is not an approved migration.
3. The team must agree whether versions may be added to a published tool. This branch conservatively allows owner writes only while status is `DRAFT`.
4. The final shared UI owner should connect the pages to the common navbar/sidebar/alerts fragments when those assets exist. Role E pages use scoped `role-e.css` and direct links in the meantime.
5. Docker Compose and production deployment require runtime verification. Docker is not installed on this workstation. CI is configured to run Maven verify and build the container image; that updated workflow has not yet run on GitHub. PostgreSQL integration tests and the full login-to-review smoke flow remain pending.
