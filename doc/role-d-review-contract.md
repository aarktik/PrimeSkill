# Role D Review & Rating Contract

Status: confirmed by the project owner on 6 October 2026. Implementation is against develop commit `d23228267a569867867fff876440a46a9529da27`.

## Scope and ownership

Role D owns review persistence, CRUD, review summaries, the review-created observer event, and review UI on tool detail. Role C owns browse-card summaries and the database-level rating sort. Role D does not create User or Tool models and does not change C's search implementation.

## Confirmed behavior

| Rule | Contract |
| --- | --- |
| Rating | Integer from 1 through 5 |
| Reviews per user/tool | One; database unique constraint is authoritative |
| Create | Authenticated user, published tool only; tool owner cannot review their own tool |
| Edit | Review author only; only while tool is published |
| Delete | Review author or ADMIN, including after a tool is no longer published |
| Read visibility | Public for published tools; tool owner and ADMIN for other statuses, using ToolService visibility |
| Comment | Optional, trim before save, blank becomes null, maximum 2,000 characters |
| Empty aggregate | `avgRating = null`, `reviewCount = 0` |
| Rating sort | Average descending, unrated tools last, `id` ascending as tie-break; keep full precision for sorting |
| Deletion policy | Keep existing SQL user/tool `ON DELETE CASCADE`; coordinate any future change with A/B/E |

The `/my/reviews` page lists only the current user's review content and tool IDs. It does not load hidden tool details and keeps the author's delete action reachable after a tool is hidden. Tool detail uses the existing B route/template.

## Review API

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/v1/tools/{toolId}/reviews?page=0&size=20` | 200 and a `PagedResponse<ReviewResponse>` |
| POST | `/api/v1/tools/{toolId}/reviews` | 201 and the created review |
| PUT | `/api/v1/tools/{toolId}/reviews/{reviewId}` | 200 and the updated review |
| DELETE | `/api/v1/tools/{toolId}/reviews/{reviewId}` | 204 |

Requests contain only rating and comment. The authenticated actor and tool ID come from trusted context and the path, not the request body. Responses contain review ID, tool ID, author ID/display name, rating, comment, and timestamps; they do not expose an entity, email, password hash, or session data. Review IDs outside the tool path return 404. The shared API error response is retained. Lists default to page 0, size 20, allow sizes 1–100, and sort by `createdAt DESC, id ASC`.

## Role C summary and sort contract

The implemented summary API is:

```java
public record ReviewSummary(Long toolId, Double avgRating, long reviewCount) {}

public interface ReviewSummaryService {
    Map<Long, ReviewSummary> summarizeByToolIds(Collection<Long> toolIds);
}
```

`summarizeByToolIds` runs one grouped repository query for the requested IDs. Empty input returns an empty map without a query. Requested IDs without reviews are included with `avgRating = null` and `reviewCount = 0`. Create, update, and delete are reflected on the next call. Consumers check `reviewCount` before displaying an average.

For browse sorting, C should join a grouped aggregate (`tool_id`, `AVG(rating)`, `COUNT(*)`) to the existing filtered Tool query, then order by `avg_rating DESC NULLS LAST, tool.id ASC` before pagination. This keeps sorting at database level and avoids both loading all tools into Java and per-card review queries. D's summary service is for batched display aggregates; C owns integration with browse filters and pagination.

## Database and events

- `schema.sql` already defines the reviews table, rating check, unique (`user_id`, `tool_id`), foreign keys with cascade, and `idx_reviews_tool_id`.
- New databases also get the comment length check. Existing databases need `doc/sql/V8__limit_review_comment_length.sql`; it is deliberately not auto-run because the project has no Flyway runtime and Role E owns migration rollout.
- Review maps to existing tables with lazy User/Tool references and no ORM cascade to User or Tool.
- `ReviewCreatedEvent` carries only review, tool, and user IDs. Its listener runs after commit and logs no review text or other PII; failures are caught so a committed create is not reported as failed.
- Synthetic sample ratings are seeded only by the explicit `dev` profile, from disabled `.invalid` demo accounts; default and production profiles do not load them. They exist only to preview the UI. Real review aggregates come from persisted user reviews.

## Integration boundaries

- A: actor/session, CSRF, shared error mapping, and public GET security matcher.
- B: tool visibility and existing detail route/template.
- C: browse-card summaries and database-level rating sort.
- E: migration baseline, PostgreSQL integration setup, publishing-state concurrency, and any dev/test fixture profile.

The confirmed contract should be sent to C so C can implement the search follow-up against the batch summary API and database-sort query shape above.
