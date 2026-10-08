# Task B1 — Tool metadata edit policy and test proposal

Date: 8 October 2026. Owner: B + E; requirements owner confirms the policy.

Status: **preparation only; policy NOT confirmed; production behavior unchanged**.

Source snapshots: B `04a9023d7b393b04eaf00835256b98cf11395b74`,
develop `d23228267a569867867fff876440a46a9529da27`, E `fd67569`.
Reference: Task B1 in E's `docs/superpowers/plans/2026-10-07-team-handoff.md`.
E publishing code is not integrated in this B checkout.

## Current behavior, not the approved future policy

`ToolService.update(id, request, actorUserId, actorIsAdmin)` allows an owner or
admin to edit metadata in every status and preserves that status. Other users
are denied. It reads using `findById`; there is no shared publishing lock or
revision check. E transitions use `findForUpdateById` with `PESSIMISTIC_WRITE`.
E's version CRUD being DRAFT-only does not establish metadata edit policy.

The new `b1-baseline` parameterized tests record this existing behavior in all
4 statuses × owner/admin/other-user (12 cases). They check metadata, category,
response mapping, denied-write invariants, and preservation of owner/status/
viewCount. They use real Tool objects with mocked persistence and do **not**
prove database persistence, concurrency, or approval correctness. Reflection
sets status only in test fixtures; no production setter is added.

Replace the owner/admin baseline expectations with the confirmed contract when
implementing B1. Passing these baseline tests is not B1 acceptance.

The additional REST tests cover trusted actor forwarding, validation, and
401/403 error mapping. Standalone MockMvc has no security filter chain; CSRF
and real session authorization must be verified after B/E integration.

## Decisions needed from B/E/team

For every allowed edit, specify both permission and resulting status. For
every rejected edit, specify the shared exception and HTTP status/error code.

| Current status | Owner edit → next status/error | Admin edit → next status/error | Other user |
| --- | --- | --- | --- |
| DRAFT | Pending team decision | Pending team decision | Denied; existing 403 ACCESS_DENIED |
| PENDING | Pending team decision | Pending team decision | Denied; existing 403 ACCESS_DENIED |
| PUBLISHED | Pending team decision | Pending team decision | Denied; existing 403 ACCESS_DENIED |
| DEPRECATED | Pending team decision | Pending team decision | Denied; existing 403 ACCESS_DENIED |

Decide whether each status rejects edits, allows them without transition, or
allows them with a return to DRAFT requiring submit/approval again. Consider
restricting PENDING edits to prevent changing a review candidate; do not treat
this suggestion as an approved requirement.

Metadata currently means `name`, `slug`, `shortDescription`, `description`,
`categoryId`, and `repositoryUrl`. Confirm whether all use the same rule,
whether no-op PUT requests count as edits, and whether admin has an exception.
Agree implications of mutable slugs and whether tags/version changes can also
invalidate approval; those operations belong to C/E and are not changed here.

## Approval race proposal for E review

A shared row lock serializes transactions but does not by itself identify the
content an admin previously reviewed. Review content A → edit to B commits →
approve by ID may publish B even if the approval transaction reads a fresh row.
If PENDING edits are allowed, invalidate the candidate (for example, return to
DRAFT) and/or validate an expected review revision at approval. B/E must choose
the contract, including stale approval errors and any API/schema changes.
An expected revision must also reject an old approval after edit + re-submit.

After integration, use an isolated PostgreSQL DB with real B/E services and
separate transactions/connections. Use barriers/latches, bounded waits and
database lock evidence, not timing sleeps. Compare every metadata field,
status, owner, and count in a new transaction after both workers finish.

| Scenario | Acceptance expectation to finalize after policy decision |
| --- | --- |
| Update obtains lock/commits before approve | Rejected edit leaves candidate unchanged, or changed candidate requires fresh review; old approval cannot publish changed content |
| Approve obtains lock/commits before update | Update rechecks PUBLISHED policy; reject without mutation, or apply agreed transition; no unreviewed changed content remains PUBLISHED |
| B preloads PENDING, then E approves | B must not use stale entity state to bypass PUBLISHED policy |
| E preloads candidate A, then B commits edit B | Refresh/compare revision; never approve stale/unreviewed candidate |
| Admin reviews A, edit B commits, B is re-submitted, old approval arrives | Reject stale approval or ensure edits were impossible while review candidate was pending |
| Other user races with approve | Edit is denied without mutation in both commit orders |
| Rollback/conflict/timeout | No partially changed metadata/status; use agreed safe error contract |

Keep a consistent lock order with E and, if chosen, refresh already managed
entities after locking. Preserve the public update signature unless B/E agree
otherwise. Do not introduce a second publishing state machine in B.

## Acceptance work after team confirmation

- [ ] Record all 12 policy outcomes, covered fields, error contracts and approvers.
- [ ] Replace baseline owner/admin expectations; run new policy tests FAIL before implementation.
- [ ] Add persisted-row assertions for all 12 state/actor cases.
- [ ] Add real security-filter HTTP coverage including session and CSRF.
- [ ] Add both transaction orders and stale candidate/entity tests on PostgreSQL.
- [ ] Implement agreed B/E policy and concurrency strategy; confirm PASS.
- [ ] Run B regressions, integrated `verify`, and existing viewCount regressions.
- [ ] Send branch, commit SHA, commands/results and limits to E/team for review.

## Preparation verification

Command:

```text
mvn -B -f code/pom.xml "-Dtest=ToolServiceImplTest,CategoryServiceImplTest,ToolRestControllerTest,CategoryRestControllerTest,CurrentActorProviderTest,ToolWebControllerTest" test
git diff --check
```

Result on 8 October 2026: **46 tests passed; failures/errors/skips = 0**;
Maven BUILD SUCCESS on Java 17. This includes 12 new baseline matrix cases,
5 new REST update checks, and the existing B regressions. `git diff --check`
passed. The local log is `code/target/b1-preparation-tests.log` (ignored build
output). Full `verify` and PostgreSQL approval-race tests are not claimed by
this preparation. Existing viewCount implementation is unchanged.

## Message for E/team (draft, not sent)

ฝั่ง B เตรียม tests B1 แล้ว: 4 สถานะ × owner/admin/คนอื่น รวม 12 กรณี
บันทึก behavior ปัจจุบัน และเพิ่ม tests API update; regression รวมผ่าน 46/46
ยังไม่เปลี่ยน production behavior และไม่ได้ทำ viewCount ซ้ำ

ขอ E/ทีมยืนยันแต่ละสถานะ DRAFT/PENDING/PUBLISHED/DEPRECATED ว่า owner/admin
แก้ metadata ได้ไหม ถ้าแก้แล้วคงสถานะหรือกลับ DRAFT และ error ที่ต้องคืนเมื่อ
แก้ไม่ได้ รวมถึงขอบเขต fields และกรณีส่งค่าเดิมโดยไม่มีการเปลี่ยนแปลง

ขอเลือกวิธีป้องกัน approve ข้อมูลคนละชุดกับที่ admin ตรวจด้วยครับ: row lock
อย่างเดียวไม่ป้องกันกรณี admin ตรวจ A → มีการแก้เป็น B → กด approve ของ A
โดยเฉพาะถ้าแก้แล้ว submit ใหม่ก่อน approval เก่าจะมาถึง เมื่อ policy ชัด B
จะเปลี่ยน baseline tests เป็น acceptance tests แล้วทำร่วมกับ E และทดสอบ
ทั้งสองลำดับ transaction บน PostgreSQL ก่อนเสนอรวมงาน
