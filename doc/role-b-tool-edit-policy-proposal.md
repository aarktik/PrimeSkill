# Task B1 — Role B metadata contract and handoff

Updated: 9 October 2026. Owner: B + E; C owns tool-tag guards and A reviews security.

Status: B metadata candidate follows the DRAFT-only contract supplied in A's
9 October review and E's B1 handoff at `742a0fa`. Full B1 acceptance still
requires C guards, a common integration SHA, and A/D review. No merge/deploy
or team-database migration is approved by this document.

## Metadata contract

| Current status | Owner | Admin | Other user |
| --- | --- | --- | --- |
| DRAFT | 200, remains DRAFT | 200, remains DRAFT | 403 ACCESS_DENIED |
| PENDING | 409 INVALID_STATE_TRANSITION | 409 INVALID_STATE_TRANSITION | 403 ACCESS_DENIED |
| PUBLISHED | 409 INVALID_STATE_TRANSITION | 409 INVALID_STATE_TRANSITION | 403 ACCESS_DENIED |
| DEPRECATED | 409 INVALID_STATE_TRANSITION | 409 INVALID_STATE_TRANSITION | 403 ACCESS_DENIED |

The rule covers `name`, `slug`, `shortDescription`, `description`, `categoryId`
and `repositoryUrl`. A no-op outside DRAFT is still rejected; no automatic
return to DRAFT occurs. Existing E reject/deprecate/restore transitions govern
how an owner returns to an editable draft. Owner/status/viewCount/reviewRevision
are not client-writable metadata fields. Existing validation/unique/FK rules
remain in force after actor and status checks.

Anonymous with valid CSRF receives 401. Missing/invalid CSRF can produce 403
before authorization. Missing Tool returns the existing 404 contract. Other
users are denied before status checks so a mutation error does not reveal a
hidden status. Spring pessimistic locking failures map to 503
CONCURRENT_OPERATION_RETRY; unrelated integrity errors retain their mapping.

## Coordination with E

B update takes E's shared Tool `PESSIMISTIC_WRITE` lock, refreshes the managed
entity, checks owner/admin then DRAFT, validates and mutates in one transaction.
E owns `reviewRevision`, increments it on successful SUBMIT, and requires
`expectedReviewRevision` for approve/reject. B does not create another state
machine or increment/reset that submission token on metadata edits.

With DRAFT-only metadata guards, a submitted candidate cannot be edited by B.
With E's submission revision, an old decision after reject → edit → resubmit
cannot approve the new candidate. Both protections are necessary; C's tag
guards must still be integrated to protect the complete candidate.

Editor GET, invalid-form redisplay, and valid POST apply the same permission/
state rules. Valid POST always rechecks through the locked service even if the
form was opened before submit. Dashboard exposes edit links only for DRAFT.
HTML error pages preserve the web flow without changing the REST error shape.

## Preparation and review history

On 8 October, preparation commit `4208e4562aaf728caefde4093664a17af0b6411b`
added 12 **baseline** status/actor cases and five REST update checks without
changing behavior. B's focused regression passed 46/46. The commit was pushed
to `natchapol_6733802674_02`.

The user reported A's independent review: regression 46/46, actor forwarding
and errors 401/403/400 matched the existing contract. A correctly identified
the 12 cases as preparation, not acceptance. That report did not approve a
policy implementation by itself.

On 9 October the user supplied A's review of E at
`742a0fa40f35499abe452d81b2756eec198cddac`, explicitly assigning B shared
lock + refresh + owner/admin + DRAFT guard, including non-DRAFT no-op rejection.
B replaced baseline expectations with the supplied acceptance contract and
added PostgreSQL session/persistence/race fixtures.

A's reported E-only evidence (450 Java tests, 28 reviewer-only additions in a
478-test overlay, Python12 and matching Build and test CI) is separate from
B's new runs. It does not certify B metadata/C tags or approve merge/deploy.

## Evidence and remaining work

Read [B's actual test report](role-b-b1-test-report.md) and
[composition instructions](../test/role-b-b1/README.md) for runtime, source
hashes, red/green counts and limits. Existing viewCount code is unchanged.

E needs the B candidate and C tag guards on a common source, then must rerun
acceptance/races/CI and send the new SHA to A/D. A must inspect actual metadata/
tag server guards, real sessions/CSRF and persisted denied-state invariants on
that SHA. C coordinates Tool/Tag lock ordering and assign/delete races with E.
Any shared database baseline/migration/backup/restore is a separate rollout.

## Message for E/team (draft, not sent)

B ทำ metadata DRAFT-only guard ตาม handoff E และรายงาน A แล้ว: shared Tool
lock + refresh → owner/admin → DRAFT → validation/mutation. Non-DRAFT รวม no-op
คืน 409; คนอื่น 403. Editor/dashboard ใช้กติกาเดียวกันและ stale form หลบ service
guard ไม่ได้ มี session/CSRF และ PostgreSQL persisted/race fixtures ให้ E รวม
ตาม README. ดู SHA/ผลรันทดสอบจากข้อความส่งมอบล่าสุดและ test report ก่อนตรวจ.
ยังไม่รับรอง B1 ทั้งทีม ต้องรวม C tag guards และส่ง integration SHA ใหม่ให้ A/D
ตรวจอีกครั้ง; ไม่ได้ merge/deploy/รันฐานทีม.
