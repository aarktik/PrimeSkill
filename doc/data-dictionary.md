# Data Dictionary

Sprint 0 establishes the database ownership and constraints below. Each module owner should document its columns, validation rules, and business meaning here as implementation proceeds.

| Table | Owner | Purpose |
| --- | --- | --- |
| `users` | A | Authentication and account records |
| `user_profiles` | A | One-to-one public profile for a user |
| `categories` | B | Tool classification |
| `tools` | B | Published tool records |
| `tags` | C | Reusable tool tags: `id`, unique `name` (100), unique `slug` (120, lowercase-hyphen), timestamps |
| `tool_tags` | C | Tool-to-tag association: composite PK (`tool_id`, `tag_id`), FKs to `tools`/`tags` with cascade delete, indexes on both columns; deleting a link never deletes the tool |
| `reviews` | D | User ratings and feedback |
| `tool_versions` | E | Tool release/version history |
