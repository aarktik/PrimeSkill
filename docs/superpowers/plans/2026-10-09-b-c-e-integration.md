# B+C+E Integration Plan

> Execute inline with `superpowers:executing-plans`. Leave the candidate uncommitted for user review.

**Goal:** Integrate C `4b0fb0e1040c71db20c1bfe5fc20d061f8407614` with E `24ec2ac326cdc97f76c0a37872fe5e6c311edca2` and verify one combined working tree.

**Architecture:** Preserve the shared Tool lock and Tool → Tag association lock order. Compose C's acceptance checker with the existing B/D/E checker; adjust test expectations for the layer that actually rejects missing B suites.

**Tech stack:** Java17, Maven, PostgreSQL18.6 disposable loopback cluster, Python.

**Spec:** `doc/role-c-followup-handoff.md`, `doc/b-metadata-coverage-gate-2026-10-09.md`, `doc/b1-bc-integration-acceptance.md`.

## Constraints

- No develop merge, commit/push, shared DB migration, or messages to teammates in this step.
- Reuse clean isolated worktree; retain original source commits and source evidence.
- No old D rollback patch; no production changes unless inspection/tests expose a defect.
- CI on resulting committed SHA and A/D review remain delivery gates.

## Steps

- [x] Start candidate at E24ec2ac; merge exact C4b0fb0e with --no-commit --no-ff.
- [x] Inspect lock callers, permission/state order, rating aggregation/pagination, and workflow composition.
- [x] Run all Python gate tests; observe failure from missing B suite being rejected by parent gate.
- [x] Adjust C tests to assert the correct error layer without weakening rejection; cover below-minimum counts, wrong report folder, and additional tests.
- [x] Run Python gate tests again; full Java17/PostgreSQL verify; run B/E and C gates on fresh XML.
- [x] Save log, report counts, Java runtime, source hashes/tree and integration diff under target.
- [x] Write integration handoff identifying A/D review scope and pending CI; keep candidate uncommitted.

## Execution ledger

- Merge24ec2ac+C4b0fb0e: no conflicts; merge remains uncommitted.
- Red: C gate tests failed on missing B suite error prefix (2 subtests); parent gate correctly rejected the report.
- Ruling: fix tests to expect the actual rejecting gate and suite name, not change production checker ordering. Restore fixture in finally to avoid cross-subtest contamination.
- Green: Python24, Java638 (338+300), B/E and C XML gates passed; runtime Java17 confirmed in60 reports. No production change beyond C source; no blocker found in local inspection/tests.
- Review limitations and next A/D/CI steps: `doc/role-e-b-c-integration-handoff.md`.

## Review focus

- Every tag write path must respect Tool → Tag; deleteTag must never acquire Tool after Tag.
- Owner/admin checks precede state/association lookup; no-op on non-DRAFT is rejected.
- Ratings sort full precision in DB before pagination; unrated last; id ASC ties.
- Parent B checker rejecting missing B is valid behavior and must not be masked by the C test.
- Local combined evidence must not be presented as GitHub CI or team approval.
