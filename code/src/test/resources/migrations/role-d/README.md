# Role D V8 test fixture

Source: [V8 SQL at commit 0a92dbc](https://github.com/aarktik/PrimeSkill/blob/0a92dbc3a80538917bc400d8862d8644c5dd4ec9/doc/sql/V8__limit_review_comment_length.sql).

- Branch inspected: `naphat_67338800363_02`, 7 October 2026.
- Commit: `0a92dbc3a80538917bc400d8862d8644c5dd4ec9`.
- Source path: `doc/sql/V8__limit_review_comment_length.sql`.
- Git blob ID: `3a612cbbd02c6cee9aac62090f64a625a39d030f` (verified against `git hash-object` on this copy).

This is an unchanged, pinned test-only snapshot. It is not under application SQL initialization or Flyway migration paths and is not installed on a shared database. `ReviewCommentMigrationPostgresIT` explicitly loads it into a guarded disposable database and a private schema.

When D changes the SQL, review the diff, update this snapshot and provenance together, and rerun the PostgreSQL suite. Do not infer that results for this snapshot cover a future revision.
