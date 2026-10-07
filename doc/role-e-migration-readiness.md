# Role E migration readiness — 7 October 2026

This is a preparation checklist, not an installed migration or permission to change a shared database. The current E branch uses `schema.sql` with `ddl-auto=validate`; no Flyway runtime has been enabled.

## Source snapshots inspected

- E: `76cdd65` (`thaninton_673380043-6_02`).
- develop: `d232282` (includes B catalog/view counting and C browse/tags).
- D: `e39a880` (`naphat_67338800363_02`, review implementation dated 6 October).

These snapshots were read through Git without merging branches. The PostgreSQL test profile validates E's current schema only; it is not a migration or C/D integration result.

Publication note: this checklist is delivered with the [team handoff plan](../docs/superpowers/plans/2026-10-07-team-handoff.md). The PostgreSQL profile/script and CI implementation mentioned here remain local changes awaiting review; they are not included in this documentation commit. Remote readers must wait for their implementation commit before using them.

## Decisions needed before installing Flyway

1. Inventory the target schema and any existing `flyway_schema_history`. Do not assume the next version is V7 because a draft has that filename. A new empty database and a populated database need separate, explicit baseline procedures. Do not enable automatic baseline-on-migrate against an unidentified database.
2. Agree official column lengths. Current SQL uses `tags.name=100`, `tags.slug=120`, `tool_versions.version=100`; the implementation plan proposes 80/90 and `version_number=50`. Check existing values before renaming or reducing lengths. Optional manifest URL/type from the plan is not implemented in E and requires a separate agreed change.
3. Review `doc/sql/V7__align_existing_tool_catalog.sql`: test old `website_url`, new `repository_url`, and both columns present. The last case requires a deliberate reconciliation of data. Preserve all populated fields.
4. Include C's `idx_tool_tags_tag_id` in the approved baseline or a forward migration as appropriate. It exists in develop's `schema.sql`, not in E's current schema.
5. Review D's proposed comment constraint from [commit e39a880](https://github.com/aarktik/PrimeSkill/blob/e39a880405af717062bbb9def311ae331dd9b1b3/doc/sql/V8__limit_review_comment_length.sql). It rejects existing review comments over 2,000 characters and adds/validates a check constraint. Decide how to handle oversized rows; do not silently truncate them. Its V8 filename is provisional until the sequence is agreed.
6. Retain current FK/unique/delete contracts, especially unique tool version and user/tool review, category restriction, and tool-to-version/review cascade. A migration must not introduce a second tool/user model.

## Acceptance checks after the team approves the sequence

- [ ] Empty isolated PostgreSQL database: migrate, start application, and pass Hibernate validation.
- [ ] Copy of the old schema with representative data: preflight, migrate, verify row counts and preserved values.
- [ ] Oversized review comments and conflicting URL columns cause a clear preflight failure without partial destructive changes.
- [ ] Rerunning the migration runner makes no unintended schema/data changes.
- [ ] Existing E PostgreSQL regressions still pass; after C/D integration, add browse/tag/review schema and query checks.
- [ ] Shared deployment has a tested backup/restore procedure, a migration owner, and an approved maintenance/rollout window.

## Integration tests that need the other roles' code

- Approve -> public browse/search includes the tool; deprecate -> browse and public detail no longer reveal it.
- Detail view counting excludes the owner and stays atomic; internal version/moderation reads do not inflate popularity.
- Review create/edit racing with deprecate follows an agreed transactional rule. D currently checks `PUBLISHED` without using E's tool-row lock; this is a risk to investigate, not a verified concurrency failure.
- Real rating sort and browse aggregates are a C follow-up using D's summary contract. The inspected branches still fall back to newest for `sort=rating`.

UI integration and migration rollout remain separate team decisions. The shared UI guide specifies the navy/light theme; E's KKU palette is an uncommitted local design experiment.
