# B1 — ส่งต่อ implementation ส่วน E

สถานะส่งมอบ: implementation เฉพาะ E ถูกจัดเข้า `thaninton_673380043-6_02` จาก source รวม `3187098fac9d91c14d1fdfe40bb765797cde8cfd`. อ่าน [ลำดับ commit และผลทดสอบล่าสุด](role-e-delivery-handoff.md). ยังไม่ merge develop/deploy/รันฐานทีม และยังต้องมี metadata/tag guards ของ B/C.

## E ทำอะไรแล้ว

- เพิ่ม `tools.review_revision` / `Tool.reviewRevision` / response mapping; createเริ่ม0 และ SUBMITเพิ่ม1พร้อมสถานะใน transactionเดียว. Reject/deprecate/restoreไม่reset; resubmitเพิ่มอีก. Overflowปฏิเสธโดยไม่เปลี่ยนstatus.
- เพิ่ม `PublishingService.decide(id, action, expectedReviewRevision, actor)` สำหรับAPPROVE/REJECT. Generictransitionเดิมไม่อนุญาตapprove/rejectโดยไม่มีtoken.
- Publishingและversionwriteใช้Tool row lockเดิมพร้อมrefreshก่อนอ่านstate/revision เพื่อไม่ใช้entityที่cacheค้าง.
- APIapprove/rejectต้องมีbodyrevisionที่เป็นintegerจริง; missing/null/negative/string/fraction/boolean/overflowถูกปฏิเสธ400. Tokenเก่า409 STALE_REVIEW_REVISION; currenttokenแต่stateผิด409 INVALID_STATE_TRANSITION.
- Webmoderationส่งhiddenrevisionจากcandidateที่renderพร้อมCSRF. Formเก่าหลังresubmitถูกปฏิเสธ ไม่มีauto-retryด้วยrevisionใหม่.
- Locktimeoutที่Springแปลงเป็นPessimisticLockingFailureExceptionคืน503 CONCURRENT_OPERATION_RETRY; ไม่เปลี่ยนconstraint/unknownDBerrorsเป็น503.
- เพิ่มmigrationdraftตรวจcolumn/default/checkdefinition/rerunและPGacceptance. เตรียมcoveragegateในbuildworkflowที่checkoutsourceของrunจริง ไม่อ้างCIเก่า.

## Contract ที่ผู้ใช้ API / service ต้องปรับ

```http
POST /api/v1/admin/tools/42/approve
Content-Type: application/json
X-CSRF-TOKEN: <session token>

{"expectedReviewRevision":7}
```

Rejectใช้bodyเดียวกัน. อ่านreviewRevisionจากcandidateที่adminตรวจจริง; ห้ามGETค่าปัจจุบันมาแทนค่าที่เคยตรวจแล้วส่งapproveเงียบ ๆ. Webใช้formfieldชื่อexpectedReviewRevision. Java callersเดิมต้องย้ายจากtransition(APPROVE/REJECT)ไปdecide(..., reviewedRevision, actor). Submit/deprecate/restoreคงcontractเดิม.

## งาน B ต้องทำต่อ

1. ToolServiceImpl.update: sharedToollock+refresh → actor/owner/admin → DRAFT guard → unique/FKvalidation → mutationในtransactionเดียว.
2. Matrix12: DRAFTowner/admin200, อีก3statusowner/admin409 INVALID_STATE_TRANSITION, otheruser403ทุกstatus. Denied/no-opนอกDRAFTห้ามเปลี่ยนmetadata/status/revision/viewCount.
3. ToolWebController/dashboard/formให้แก้เฉพาะDRAFT และตรวจserverguardแม้เปิดformก่อนsubmit. Eไม่ได้แก้ไฟล์เหล่านี้แทนB.
4. เปลี่ยนbaseline12เป็นacceptance; เพิ่มpersistedmatrixและmetadata update/SUBMIT/APPROVEทั้งสองorders/staleentityบนPG.

## งาน C ต้องทำต่อ

1. assign/unassignToolTagใช้Toollock+refreshและowner/admin/DRAFTguard.
2. Serializeassign/deleteTagด้วยTaglockร่วมกัน; รักษาin-use409เดิมไม่ให้cascadeลบassociationใหม่ในcheck/delete race.
3. เพิ่มPGtagmutation/SUBMITraceและauthorization/statusmatrix. อย่าเปลี่ยนsharedcataloglabelsเป็นimmutableโดยไม่มีrequirement.

## งาน A / integration reviewer

- ตรวจAPI/Web/session/CSRF/role/errorcontractใหม่ โดยเฉพาะไม่มีfallbackapproveByIdและstrictJSONrevisionไม่ถูกcoerce.
- ตรวจmigration/lockorder/refresh/rollbackและerror503เฉพาะlockfailure. อ่าน [ผลทดสอบ E](b1-implementation-test-report.md).
- รวม B/C แล้วทดสอบบนsourceเดียวกันและบันทึกSHAใหม่; ต้องมีmetadata/tagtestsเพิ่มก่อนอ้างB1ครบ.

## ข้อจำกัดที่ต้องรักษาให้ชัด

**E candidateนี้ยังไม่ทำให้approvalของเนื้อหาทั้งชุดปลอดภัยครบ:** metadataและtagwritesเดิมยังเปลี่ยนPENDINGได้โดยไม่เปลี่ยนreviewRevision. ต้องรวมguardsของB/Cก่อนเปิดใช้B1เป็นfeatureที่เสร็จแล้ว. Etestsพิสูจน์revision/token/versionpaths ไม่ใช่การแทนacceptanceของB/C.

Migrationdraft: `doc/sql/drafts/B1__add_tool_review_revision.sql`. ยังไม่ตั้งV-number, ไม่เชื่อมต่อฐานทีม. DBownerต้องตรวจhistory/target/schema/backupและหยุดoldwritersก่อนdeployAPI/schemaร่วมกัน. LegacyPENDINGrevision0ต้องเปิดcandidateใหม่แล้วส่งexpected0; id-onlyclientเก่าจะได้400.

## การตรวจงานในเครื่อง

- Worktree: `D:\PrimeSkill-worktrees\b1-role-e`.
- Worktree ข้างต้นเป็นต้นฉบับ candidate; ชุดส่งมอบอยู่บน personal branch ตามรายงานล่าสุด. UI ทดลองเดิมไม่รวมในชุดส่งนี้.
- ทดสอบ: `./scripts/test-postgres.ps1` ในworktree โดยclear/restore SUPABASE_DB_* และ MAVEN_ARGS ตามrunnerwrapper. ใช้ฐานdisposableที่guardอนุญาตเท่านั้น.
- Coverage: `python scripts/test-review-ci-reports.py -v`, `python scripts/test-role-e-b1-reports.py -v`, `python scripts/check-role-e-b1-reports.py code/target --output code/target/b1-evidence/summary.json`.
- Nativeexternalfixturesถูกนำเข้าMaventestsourceในcandidateนี้เพื่อให้verifyรันrace10และD28จริง; อย่านำสำเนาที่ต่างกันมาทับโดยไม่ดูdiff.
- ไม่ได้ส่งข้อความถึงเพื่อนแทนผู้ใช้; ผู้ใช้ส่งไฟล์นี้ให้B/C/Aได้เมื่อพร้อม.
