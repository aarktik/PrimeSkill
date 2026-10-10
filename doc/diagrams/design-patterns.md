# PrimeSkill — Class diagrams

อ้างอิง develop SHA `4678e0047184de7100579041c9cdfacfe228c688` แสดงคลาสที่เกี่ยวข้องกับ Patterns โดยละสมาชิกที่ไม่เกี่ยวข้องเพื่อให้อ่านง่าย Mermaid source ในไฟล์นี้ render ได้บน GitHub

## Layered MVC Repository Service DTO and DI

```mermaid
classDiagram
    class ToolService {<<interface>>}
    class ToolRepository {<<interface>>}
    ToolRestController --> ToolService : constructor injection
    ToolWebController --> ToolService : constructor injection
    ToolWebController ..> ThymeleafTemplates : view name and model
    ToolServiceImpl ..|> ToolService
    ToolServiceImpl --> ToolRepository : constructor injection
    ToolServiceImpl --> ToolMapper : constructor injection
    ToolRepository ..> Tool : persists and queries
    ToolMapper ..> Tool : reads
    ToolMapper ..> ToolResponse : creates DTO
    ToolRestController ..> CreateToolRequest : accepts validated DTO
    ToolRestController ..> ToolResponse : returns DTO
```

## Strategy

```mermaid
classDiagram
    class ToolSortStrategy {
        <<interface>>
        option() ToolSortOption
        toSort() Sort
    }
    ToolSearchServiceImpl --> ToolSortStrategy : injected list, resolveSort
    NewestToolSortStrategy ..|> ToolSortStrategy
    PopularityToolSortStrategy ..|> ToolSortStrategy
    RatingToolSortStrategy ..|> ToolSortStrategy
    RelevanceToolSortStrategy ..|> ToolSortStrategy
    ToolSearchServiceImpl --> ToolRepository : executes search queries
```

Rating/Relevance aggregate query ordering remains in Service/Repository; this diagram does not imply those queries are delegated to strategies.

## State

```mermaid
classDiagram
    class PublishingState {
        <<interface>>
        status() ToolStatus
        transition(PublishingAction) ToolStatus
    }
    PublishingServiceImpl --> PublishingStateMachine : transition after authorization and locking
    PublishingStateMachine o-- PublishingState : enum map
    BaseState ..|> PublishingState
    DraftState --|> BaseState
    PendingState --|> BaseState
    PublishedState --|> BaseState
    DeprecatedState --|> BaseState
```

BaseState and concrete states are private nested classes of PublishingStateMachine. Valid transitions: DRAFT→PENDING (submit), PENDING→PUBLISHED (approve), PENDING→DRAFT (reject), PUBLISHED→DEPRECATED (deprecate), DEPRECATED→DRAFT (restore).

## Observer

```mermaid
classDiagram
    class ApplicationEventPublisher {<<interface>>}
    class ReviewCreatedEvent {
        reviewId
        toolId
        userId
    }
    ReviewServiceImpl --> ApplicationEventPublisher : constructor injection
    ReviewServiceImpl ..> ReviewCreatedEvent : publishes after save
    ApplicationEventPublisher ..> ReviewAuditListener : Spring dispatch AFTER_COMMIT
    ReviewAuditListener ..> ReviewCreatedEvent : observes
```

Spring dispatch is shown as an explanatory dependency; ReviewServiceImpl never calls ReviewAuditListener directly.
