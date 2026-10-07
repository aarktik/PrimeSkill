# Review–publishing race: local integration report

Date: 7 October 2026. Owner: Role E; review implementation belongs to Role D.

## Scope and approved behavior

D snapshot: `0a92dbc3a80538917bc400d8862d8644c5dd4ec9`. E publishing service/state machine: current personal checkout, unchanged from `76cdd65`. The harness composes these in an isolated detached clone; it does not merge branches or change E production review code.

The user approved: deprecate commits first → reject review create/update; review commits first → retain that review and allow deprecate; author/admin can delete hidden reviews.

## Reproduced defect and candidate

Original D create/update checks visibility/status without acquiring the publishing Tool row lock. That check and review write are separate from the competing transition, allowing a review after the tool is hidden. An already managed Tool can also retain PUBLISHED in JPA's first-level cache after another transaction commits DEPRECATED.

Candidate: acquire `PESSIMISTIC_WRITE` on the Tool before visibility/status checks, then refresh that managed entity while holding the lock. E's transition uses the same row lock. Both operations hold it through transaction commit/rollback. Delete behavior is unchanged. The candidate includes the repository method, ReviewServiceImpl and constructor unit-test adjustments only.

## Red / green evidence

- Unfixed D + E composition: six race cases failed, zero test errors/skips. Deprecate-first accepted a write, review-first did not block deprecate, and stale-cache cases accepted a write.
- Targeted candidate run: ReviewServiceImplTest 7 passed and race IT 6 passed, failures/errors/skips = 0.
- Full fresh candidate run: existing D tests 131 + PostgreSQL IT 28 + race IT 6 = **165 passed**, failures/errors/skips = 0; Maven BUILD SUCCESS, runner exit 0. Separate unmodified baseline exit 1 (contextLoads configuration error) remains recorded.

Each operation (create/update) is checked for both commit orders and a previously loaded stale Tool. Race tests run real Spring services, separate threads/connections and READ_COMMITTED transactions. They assert blocking through PostgreSQL `pg_blocking_pids`, release the first transaction, then assert outcomes and persisted content. Review-first tests also delete the retained hidden review as its author. Admin hidden-review deletion and HTTP authorization are covered separately by RoleDPostgresIT.

Logs retained locally:

- Red six-case run: `code/target/role-d-checks/role-d-0a92dbc-483d6214/race-six-red.log`.
- Targeted green: same checkout, `race-six-green.log`.
- Full runner: `code/target/review-race-green-runner.log`; fresh checkout `code/target/role-d-checks/role-d-0a92dbc-038bca51/`, including baseline-verify.log, postgres-verify.log and Surefire/Failsafe XML reports.
- Copied race fixture matches the repository fixture byte-for-byte (SHA256 `5bda8fb2afffa2f43b931161b538f25b03793f309ddafb673774db86d8a3edba`).

The complete patch passed `git apply --cached --check` against pinned D using a temporary index; it did not alter the real index. The composed runner skips only the ToolRepository patch hunk because composition already added that lock method.

## Handoff / limits

Publication update: tests, the candidate patch and [the implementation handoff plan](../docs/superpowers/plans/2026-10-07-postgres-race-handoff.md) are delivered on E's personal branch. Statements below about no commit/push describe the original test run; production integration/PR approval/shared migration remain pending.

D/A/E should review [the candidate patch](../test/review-publishing-race/review-lock-candidate.patch) and [reproduction instructions](../test/review-publishing-race/README.md), then integrate equivalent changes on their agreed branch. Once D/E production branches are combined, rerun this suite against the actual merged implementation and normal CI. No commit, push, PR approval or shared DB migration was performed for this race task.

This is a minimal composed service integration, not full application/UI merge verification. Java 26.0.1 and local PostgreSQL 18.6 were used. Docker and Java 17 CI remain unverified. Existing D repository tests still explicitly use H2; only PostgreSQL IT results should be described as PostgreSQL coverage. The unmodified D contextLoads still needs Supabase test configuration; the disposable-DB overlay solves test execution locally without claiming that baseline is fixed.

Per-tool create/update writes now serialize with publishing and each other; keep transaction work short. This verifies the agreed deprecate policy, not every possible catalog edit/delete/publishing race. E's earlier 269 checks and D standalone 159 checks are separate runs, not a combined passing total.
