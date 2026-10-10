# B1 Tool Edit / Approval Revision Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task in this session. Steps use checkbox (`- [ ]`) syntax for tracking. Do not delegate unless the user requests it.

**Goal:** จำกัดการแก้ข้อมูล Tool ตามสถานะ และป้องกัน admin อนุมัติรอบส่งตรวจที่ล้าสมัย.

**Architecture:** ใช้ Tool row lock เดิมร่วมกันทุก write path และ refresh ก่อนตรวจสถานะ. เพิ่ม reviewRevision ที่เพิ่มทุก SUBMIT และบังคับ approve/reject ส่ง expectedReviewRevision. Metadata/tag/version ต้องเปลี่ยนได้เฉพาะ DRAFT ตามสิทธิ์ของแต่ละ service.

**Tech Stack:** Java 17, Spring Boot 4.1.1, JPA/Hibernate, PostgreSQL, Maven Surefire/Failsafe, Thymeleaf, MockMvc, GitHub Actions. ไม่เพิ่ม product dependency.

**Spec:** [B1 design](../specs/2026-10-08-b1-edit-approval-design.md).

**Status:** ผู้ใช้อนุมัติให้ทำส่วน E แล้ว: revision/decision/version implementation และเทสถูกเตรียมบน personal branch `thaninton_673380043-6_02`. Metadata guards ของ B และ tag guards ของ C ยังเป็นงานต่อ ไม่ถือว่า B1 รวมทีมเสร็จ. อ่าน [รายงานส่งมอบล่าสุด](../../../doc/role-e-delivery-handoff.md) สำหรับ commit และผลทดสอบบน branch ที่ส่งจริง.

## Global Constraints

- Metadata owner/admin แก้ได้เฉพาะ DRAFT; คนอื่น403ทุกสถานะ. No-op ในสถานะอื่นยัง409.
- Version owner-only/DRAFT-only; tool-tag associations owner/admin/DRAFT-only.
- `tools.review_revision BIGINT NOT NULL DEFAULT 0` พร้อม CHECK>=0; เพิ่มเฉพาะ SUBMIT ที่ commit สำเร็จ ไม่ reset หรือ reuse.
- Approve/reject ต้องส่ง expectedReviewRevision; tokenเก่า409 STALE_REVIEW_REVISION; ห้าม fallback เป็น approve by id.
- คง state machine, session/CSRF, review/deprecate policy, viewCount และ validation เดิม.
- Scope ของ candidate คือ Tool metadata, categoryId, tagIds และ ToolVersions; shared catalog labels ไม่ถูก freeze.
- เขียน failing tests ก่อนเปลี่ยน implementation และตรวจ persisted state บน PostgreSQL ไม่ใช้ H2 แทน concurrency.
- ไม่ merge develop, ไม่ส่งข้อความแทนผู้ใช้, ไม่ deploy และไม่ใช้ฐานทีม. Commit/push เป็นขั้นส่งมอบที่ต้องมีคำสั่งจากผู้ใช้.
- รักษาไฟล์ทดลอง UI และเอกสารข้อ2ที่ยังค้างใน working tree. อ้าง SHA ของ source/harness ที่รันจริงทุกครั้ง.

## Review Focus

1. Old form หลัง reject/edit/resubmit ต้องไม่อนุมัติ candidate ใหม่ — Task3/6.
2. Entity ที่ preload DRAFT ก่อน SUBMIT ต้องไม่หลบ status guard — Task2/5/6.
3. JSON fraction/string/boolean และ form token ที่ผิดต้องไม่ถูกแปลงเป็น revision ที่ใช้ได้ — Task3/4.
4. Tag delete แข่ง assign ต้องไม่ cascade association ที่เพิ่ง commit โดยเงียบ — Task5/6.
5. Revision overflow/rollback และ viewCount/reviews ต้องไม่ทำให้ token reuse หรือ conflict ผิดเรื่อง — Task1/3/6.

## ก่อนเริ่ม / source ที่จะใช้

- [ ] ผู้ใช้ตรวจ plan+spec และยืนยันแนวทาง DRAFT-only + submission revision. หากเลือกแก้สถานะอื่นแล้วกลับDRAFTให้แก้เอกสารก่อนเขียนโค้ด.
- [ ] ตรวจ remote ล่าสุดและเลือก isolated worktree/checkout จาก source รวม `3187098fac9d91c14d1fdfe40bb765797cde8cfd` หรือ successor ที่ตรวจ diff แล้ว. ใช้ skill using-git-worktrees ตอนเริ่มจริง; ยังไม่สร้างในรอบเขียนแผนนี้.
- [ ] บันทึกว่า source รวมมี C tag code แต่ personal E ยังไม่มี; ทำ Task5 บน source ที่มี implementation จริง ไม่สร้าง tag service ซ้ำใน E.
- [ ] ตรวจ diff B `4208e4562aaf728caefde4093664a17af0b6411b`; นำ baseline test cases มาเปลี่ยนเป็น acceptance อย่างเลือกไฟล์ ไม่ merge branch ทั้งชุดโดยไม่ตรวจ.
- [ ] แบ่งไฟล์กับ B/C เพื่อไม่ทำซ้ำ. สามารถทำ candidate ใน checkout แยกเพื่อให้ทีม review โดยไม่รอ merge develop; การนำเข้าทีมต้องมี contract review จาก B/C/A.
- [ ] เตรียม env disposable DB: เก็บ/clear/restore SUPABASE_DB_* และ MAVEN_ARGS ไม่ให้แทรก config ของฐานจริง; ใช้ script guard เดิม. บันทึก Java/PG versions.

## ผลตรวจ remote ก่อนเริ่ม B1

ตรวจด้วย `git fetch origin '+refs/heads/*:refs/remotes/origin/*'` และ `git ls-remote --heads origin` หลังผู้ใช้ขอเทียบbranch. ทุกheadยังตรงกับsnapshotที่ใช้เขียนแผน ไม่มีcommitใหม่หลังsnapshotนั้น:

- A `9b03565`: แยกtestconfigจากSupabase; sourceรวม3187098รับการปรับนี้แล้ว. ห้ามcherry-pickซ้ำโดยไม่ดูdiff.
- B `4208e45`: baseline12และRESTtests/ข้อเสนอB1; productionmetadataยังแก้ได้ทุกสถานะ. เทียบToolServiceImplกับsourceรวมแล้วไม่มีdiff จึงใช้testsของBมาเปลี่ยนเป็นacceptance ไม่เขียนviewCountimplementationซ้ำ.
- C `281e3ac`: search/relevance/tags; tagassociationwritesยังไม่มีDRAFTguard/sharedToollock. ต้องทำTask5; Ccodeมีในsourceรวมแต่ไม่มีในpersonalE.
- D `e4df8c0`: reviewlock/refreshและPGrollbacktestsแล้ว. เป็นreview/deprecate ไม่ใช่metadata/approvalrevision; ห้ามapplycandidateเก่าซ้ำ.
- Combined `3187098`: มีpublishingToollockและversionDRAFTguard แต่ยังไม่มีreviewRevision/expectedReviewRevision; metadata/tagยังไม่guardDRAFT และpublishing/versionยังไม่refreshหลังlock.
- develop `d232282`: ยังไม่มีB1; personalE `59feba0` เป็นheadเอกสารผลCIเดิม.

ผลต่อแผน: Task1–7ยังจำเป็น ไม่มีงานB1productionส่วนใดที่พบว่าทีมทำแทนแล้ว. ก่อนเริ่มimplementationจริงให้fetchอีกครั้งถ้ามีเวลาผ่านไป และหากsourceเปลี่ยนต้องตรวจdiff/CIตามSHAใหม่.

## File Map

Existing pathsด้านล่างสัมพันธ์กับ repo root:

- Entity/read contract: `code/src/main/java/com/example/toolhub/domain/entity/Tool.java`, `dto/response/ToolResponse.java`, `mapper/ToolMapper.java` (สอง path หลังอยู่ใต้ package com/example/toolhub เช่นกัน).
- Metadata: `code/src/main/java/com/example/toolhub/service/impl/ToolServiceImpl.java` และ `controller/web/ToolWebController.java`.
- Decisions: `code/src/main/java/com/example/toolhub/service/PublishingService.java`, `service/impl/PublishingServiceImpl.java`, `controller/api/PublishingRestController.java`, `controller/web/RoleEWebController.java`.
- API/errors: เพิ่ม `dto/request/ReviewDecisionRequest.java`, `exception/StaleReviewRevisionException.java`, `exception/ConcurrentOperationException.java`; แก้ `exception/GlobalExceptionHandler.java`, `controller/web/RoleEWebExceptionHandler.java`.
- Other writes: `service/impl/TagServiceImpl.java`, `repository/TagRepository.java`, `service/impl/ToolVersionServiceImpl.java` ใต้ packageเดิม.
- UI: `code/src/main/resources/templates/admin/moderation.html`, `templates/tools/dashboard.html`, `templates/tools/form.html`.
- Schema: `code/src/main/resources/schema.sql`; เพิ่ม draft `doc/sql/drafts/B1__add_tool_review_revision.sql` (ไม่กำหนด production V-numberล่วงหน้า).
- Tests ใหม่อยู่ใต้ `code/src/test/java/com/example/toolhub/` ตามชื่อในแต่ละ task; migration fixtureอ่าน draftไฟล์ตรงจากrepoโดยไม่ทำสำเนาที่แยก drift.

### Task 1 — Revision model, read contract และ schema (E)

**Files:** Tool.java, ToolResponse.java, ToolMapper.java, schema.sql, draft SQL ตาม File Map.
**Tests:** เพิ่ม `service/ToolReviewRevisionTest.java`, `ReviewRevisionMigrationPostgresIT.java`.
**Produces:** `Tool.getReviewRevision(): long`, `Tool.advanceReviewRevision(): void`; builder `reviewRevision(long)` และ response getter. advanceใช้ checked increment; overflowโยน CatalogConflictException409 RESOURCE_CONFLICT โดยไม่เปลี่ยนค่า.

- [ ] เขียน failing tests `newToolStartsAtZero`, `advanceNeverWraps`, `mapperIncludesRevision`; assertions 0→1, Long.MAX_VALUEยังเดิมเมื่อoverflow, mappedrevisionเท่ากับentity.
- [ ] เขียน migration tests: empty/populated table, pendingเดิมrevision0, rerun, NULL/negativeถูกปฏิเสธ, incompatibleexistingcolumn/constraintต้องfail. เทียบmetadata/status/countsก่อนหลังและHibernatevalidation.
- [ ] Run unit red: `mvn -B -f code/pom.xml "-Dtest=ToolReviewRevisionTest" test`; คาด fail เพราะ contract ยังไม่มี. PG red/greenใช้ `./scripts/test-postgres.ps1` ใน isolated checkout ไม่ชี้ฐานทีม.
- [ ] เพิ่ม field/schema/check/readmapping. Draft SQLเป็นtransactionเดียวและตรวจdefinitionก่อนยอมรับcolumn/checkที่มีแล้ว ไม่ตรวจแค่ชื่อ.
- [ ] ทดสอบ restore บน disposable database แยก: snapshot ฐานเก่าที่มีข้อมูลตัวแทนด้วย pg_dump, migrate/validate, restore ไปอีกฐานด้วย pg_restore แล้วตรวจcounts/contentและstartupที่schema/applicationversionเดิมตรงกัน. ใช้clientbinaryที่รองรับserverversion, credentialผ่านenvไม่ใส่argument/log; เก็บdumpใต้ignoredtargetและบันทึกผล. นี่เป็นrehearsal ไม่ใช่backupฐานทีม.
- [ ] Run green คำสั่งเดิม; เก็บXML/logและผลmigration. Revisionไม่ใช่ @Version และ viewCountไม่เรียกadvance.
- [ ] ตรวจ diff พร้อมเตรียม commit แยก `feat(role-e): add tool submission revision model` เมื่อผู้ใช้สั่งส่งมอบ.

### Task 2 — Metadata DRAFT-only และสถานะหลัง lock (B/E)

**Files:** ToolServiceImpl.java; tests `service/ToolServiceImplTest.java`, เพิ่ม `ToolEditPolicyPostgresIT.java`.
**Consumes:** Task1 revision getter; ToolRepository.findForUpdateByIdเดิม.
**Produces:** signature `update(Long, UpdateToolRequest, Long, boolean)` เดิม พร้อม lock/refresh/permission/DRAFT guards.

- [ ] เปลี่ยน B baseline12เป็น `updateEnforcesDraftPolicy` acceptance. DRAFTowner/adminสำเร็จ200/statusDRAFT; อีก3statusowner/admin409 INVALID_STATE_TRANSITION; otheruser403ทั้ง4. ตรวจค่าที่ปฏิเสธไม่เปลี่ยนทุกfield.
- [ ] เพิ่ม `noOpRespectsState`, `conflictingSlugRollsBackAllFields`, `missingCategoryDoesNotPartiallyUpdate`, `forgedFieldsDoNotChangeOwnerStatusCounters` และ anonymous/missingTool checks.
- [ ] Run red: `mvn -B -f code/pom.xml "-Dtest=ToolServiceImplTest" test`; คาด behaviorเก่าผ่านกรณีที่ควร409. Persistedmatrixอยู่ใน PG script ไม่เอา mock มาแทนDB.
- [ ] เพิ่ม EntityManager injection; updateใช้sharedToollock → refresh → actor/ownership → DRAFT → validations → mutate. ไม่แก้deletepolicyหรือpublicupdate signature.
- [ ] Run green unitและ PG script; ตรวจresponseและrowใหม่ทุกกรณี. Owner/status/viewCount/revisionต้องไม่เปลี่ยนจากmetadataPUT.
- [ ] เตรียม diff/commit `fix(tool): restrict metadata edits to draft state` หลังผู้ใช้สั่งส่งมอบ.

### Task 3 — Revision-bound decision API และ publishing service (E/A)

**Files:** PublishingService/Impl, PublishingRestController, ReviewDecisionRequest, stale/concurrency exceptions, GlobalExceptionHandler.
**Tests:** `service/PublishingServiceImplTest.java`, `controller/api/PublishingRestControllerTest.java`; เพิ่ม `controller/api/ReviewDecisionSecurityTest.java`.
**Consumes:** Task1advance/getter.
**Produces:** `decide(Long toolId, PublishingAction action, long expectedReviewRevision, CurrentActor actor): ToolResponse`; transitionเดิมรับSUBMIT/DEPRECATE/RESTOREเท่านั้น.

- [ ] เขียน redtests: submitเพิ่มrevisionพร้อมstatus; rejectแล้วsubmitใหม่เพิ่มอีก; rollback/overflowไม่กินrevision; stale/current/replay decisionsตามspec. Generictransition(APPROVE/REJECT)→IllegalArgumentException400 VALIDATION_FAILEDโดยไม่มีwrite; decideกับactionอื่นเช่นกัน.
- [ ] เขียน HTTPredtests: bodyหาย/null/negative/fraction/string/boolean/overflowlong→400; token0ยอมรับสำหรับpendingเดิม; tokenเก่า409 STALE_REVIEW_REVISION; currentapprove/reject200; nonadmin403และanonymous401เมื่อมีCSRFถูกต้อง; missing/invalidCSRF403. ไม่คืนรายละเอียดcandidateให้ผู้ไม่มีสิทธิ์.
- [ ] Run red: `mvn -B -f code/pom.xml "-Dtest=PublishingServiceImplTest,PublishingRestControllerTest,ReviewDecisionSecurityTest" test`.
- [ ] เพิ่มDTOพร้อมstrictfield deserializationเฉพาะexpectedReviewRevision (ไม่เปลี่ยนcoercionทั้งระบบ): ใช้Jackson API/versionที่มีในpomจริง, ปฏิเสธtokenที่ไม่ใช่JSONinteger. Validate @NotNull/@PositiveOrZero.
- [ ] transition/decide lock+refreshToolก่อนread; requireauth/roleก่อนrevision → compare → state machine → mutation. SUBMITadvanceในtransactionเดียว. StaleReviewRevisionException→409 STALE_REVIEW_REVISION.
- [ ] แปลงเฉพาะSpring translated lock/deadlock exceptionsที่ตรวจได้ เช่น CannotAcquireLockException/PessimisticLockingFailureException เป็น ConcurrentOperationException→503 CONCURRENT_OPERATION_RETRY; ไม่แปลงconstraint/unknown DB errorsเป็น503. Transactionrollbackก่อนresponse.
- [ ] อัปเดต callsites/testfixturesที่เคยtransition(APPROVE/REJECT)ให้ส่งcandidate revisionอย่างตั้งใจ. ไม่มีoverloadที่แอบอ่านrevisionล่าสุดแทนclient.
- [ ] Run green คำสั่งเดิม; testsของvalidation/filterกับserviceแยกชัด. เตรียม commit `feat(publishing): require submission revision for review decisions` เมื่อผู้ใช้สั่ง.

### Task 4 — Web forms / UX รองรับกติกาใหม่ (B/E)

**Files:** ToolWebController, RoleEWebController, RoleEWebExceptionHandler, moderation/dashboard/form templates.
**Tests:** `controller/web/ToolWebControllerTest.java`, `RoleEFlowIntegrationTest.java`.
**Consumes:** Task2policy, Task3decide; response.reviewRevisionจากTask1.
**Produces:** API/Webใช้decisioncontractเดียวกัน ไม่มีwebbypass.

- [ ] Redtests `moderationFormCarriesReviewedRevision`, `staleModerationFormCannotPublishNewSubmission`, `editLinkOnlyShownForDraft`, `openEditFormBeforeSubmitCannotWriteAfterSubmit`. Missing/negative/nonintegerformrevision→400; stale→409หน้าข้อผิดพลาดพร้อมข้อความให้เปิดตรวจใหม่.
- [ ] Run red: `mvn -B -f code/pom.xml "-Dtest=ToolWebControllerTest,RoleEFlowIntegrationTest" test`.
- [ ] Formsเพิ่มhiddenexpectedReviewRevision+CSRFจากcandidateที่render; controllerรับค่าฟอร์มแล้วเรียกdecide. Handler staleแสดงversions/error409; ไม่PRG/autoretryด้วยtokenใหม่. แสดงmetadataeditเฉพาะDRAFTและserverguardทำงานกับforgedPOSTด้วย.
- [ ] คงค่าฟอร์ม/field errorsเมื่อvalidationผิด; roleที่ไม่มีสิทธิ์ไม่เห็นaction. ปรับทั้งGETและPOST ไม่พึ่งซ่อนปุ่มอย่างเดียว.
- [ ] Run greenและตรวจHTMLrenderจริงด้วยfullcontextMockMvc (sliceviewnameอย่างเดียวไม่เพียงพอ). เตรียม commit `feat(web): bind moderation forms to submission revisions` เมื่อผู้ใช้สั่ง.

### Task 5 — ปิด write paths ของ tags/versions (C/E)

**Files:** TagServiceImpl.java, TagRepository.java, ToolVersionServiceImpl.java.
**Tests:** `service/TagServiceImplTest.java`, `service/ToolVersionServiceImplTest.java`, `controller/api/ToolTagAssociationSecurityTest.java`; persisted/raceเพิ่มในTask6.
**Consumes:** ToolRepositorylockเดิม + Task2DRAFTpolicy; ไม่เพิ่มrevisionบนdraftwrites.
**Produces:** TagRepository.findForUpdateById(Long): Optional<Tag> ใช้PESSIMISTIC_WRITE; assignและdeleteใช้Taglockตัวเดียวกัน.

- [ ] Redtests tagsactor/status12, versionownerDRAFTallowed/adminnonowner403, tagdeletein-use409 (รักษาเดิม); staleDRAFTwriterต้องถูกปฏิเสธหลังSUBMIT.
- [ ] Run red: `mvn -B -f code/pom.xml "-Dtest=TagServiceImplTest,ToolVersionServiceImplTest,ToolTagAssociationSecurityTest" test`.
- [ ] assign/unassign lock+refreshToolแล้วตรวจสิทธิ์/DRAFTก่อนเขียนassociation. assign lockTagหลังTool; delete lockTagแล้วตรวจin-useโดยไม่กลับไปlockTool. Versionwritesเพิ่มrefreshก่อนrequireDraftOwner.
- [ ] คงunique/FK/notfound errorsและguardของtagdeleteเดิม; ไม่freezeชื่อsharedcatalogและไม่เปลี่ยนadminversionสิทธิ์.
- [ ] Run green; ระบุผลส่วนCแยกหากยังไม่รวมsource. ห้ามmarkB1ครบถ้าTask5ยังไม่มีบนsourceที่จะรันCI.
- [ ] เตรียม commit `fix(catalog): serialize draft tag and version mutations` เมื่อผู้ใช้สั่ง.

### Task 6 — PostgreSQL concurrency และ rollback acceptance (B/C/E)

**Files:** เพิ่ม `ToolApprovalRevisionPostgresIT.java`, `ToolApprovalConcurrencyPostgresIT.java`; Task1/2 PG tests; ปรับ existingtestsที่approveแบบid-only.
**Consumes:** Task1–5ทั้งหมด; DBguardและpostgres-itprofileเดิม.
**Produces:** หลักฐานpersistedpolicy/revision/racesบนsourceเดียวกัน.

- [ ] เขียนtestsโดยใช้latches/แยกtransactions/connections + pg_blocking_pids, workersมีtimeout15sและtransactiontimeout20s. ปิดworkerในfinallyและcleanupDBเฉพาะdisposable.
- [ ] `updateAndSubmitSerialize` parameterizedทั้ง2orders; `updateAndApprovePreserveCandidate`ทั้ง2orders; `preloadedDraftCannotBypassGuard`; `oldApprovalAfterRejectEditResubmitIsRejected` (approve/reject).
- [ ] `unauthorizedWriterCannotRaceApproval`, `tagMutationAndSubmitSerialize` (assign/unassign×2orders), `versionMutationAndSubmitSerialize` (create/update/delete×2orders), `tagDeleteCannotCascadeConcurrentAssignment` ทั้ง2orders.
- [ ] `rollbackRestoresRevisionAndContent` ครอบsubmit/decision/metadata writer, flushก่อนdeliberaterollback; ตรวจwaiterเคยblockแล้วสำเร็จ. `lockTimeoutLeavesNoPartialWrite` คืน503และpoolใช้ต่อได้; ใช้controlledSQLlock/timeoutให้เกิดexceptionจริง ไม่mockสำหรับPGcase.
- [ ] `viewsAndReviewsDoNotInvalidateCandidate`, overflow, duplicateconcurrentdecisions, staleentitystatus/revision และno-opcases. เทียบfields/status/owner/viewCount/revision/associationsในtransactionใหม่หลังจบ.
- [ ] ตรวจว่าnewtestsFAILจริงบนbaselineหรือเมื่อถอดguard/revisioncheckที่ตรงกัน แล้วrestoreimplementation; ไม่ทำmutationกับฐานทีม. บันทึกredหลักฐานแยกจากgreen.
- [ ] Run `./scripts/test-postgres.ps1`; expected exit0, Surefire/Failsafeทุกsuiteไม่มีfail/error/skip. บันทึกจำนวนจริง ไม่กำหนดให้เท่ากับ392เพราะมีtestsใหม่.
- [ ] เตรียม commit `test(b1): cover approval revision and concurrent catalog writes` เมื่อผู้ใช้สั่ง.

### Task 7 — CI / migration handoff / review gate (E/A)

**Files:** `.github/workflows/review-publishing.yml`, `scripts/check-review-ci-reports.py`, `scripts/test-review-ci-reports.py`, `test/role-d-postgres/RoleDPostgresIT.java`, `test/review-publishing-race/ReviewPublishingRacePostgresIT.java`; เพิ่ม `doc/b1-implementation-test-report.md`; อัปเดตteam-action-handoffและdatabase-rollout-checklist.
**Consumes:** testedB1sourceจากTask6; revisionbodyใหม่จากTask3.

- [ ] เปลี่ยนexternalfixturesที่approve/rejectให้ส่งreviewedrevision. คงrace10/D28/native4+4coverageเดิม ถ้าต้องเปลี่ยนจำนวนให้มีเหตุผล/requiredcase mappingและปรับcheckertestsพร้อมกัน.
- [ ] เพิ่มcoveragegateสำหรับB1 PGclassesทั้ง4โดยเช็คว่ามีtests>0และfail/error/skip=0; เพิ่มnegativecheckertestmissing/skippedB1. ไม่เดาจำนวนสุดท้ายก่อนXMLจริง.
- [ ] WorkflowรับB1sourceSHAที่reviewแล้วแทนpin3187098ในการรันใหม่. บันทึกsource/harness/fixturehashesและแยกbaseline/overlayartifactsเหมือนเดิม. ถ้ายังไม่commitให้รันlocalก่อน; CIต้องใช้sourceที่เข้าถึงได้บนremoteหลังผู้ใช้สั่งpush.
- [ ] Run `python scripts/test-review-ci-reports.py -v`, `./scripts/test-postgres.ps1`, `git diff --check` ในcheckoutที่ถูกต้อง. CIใช้Java17/PG17และตรวจDocker/startupregressionตามworkflowเดิม; localใช้PG18ที่มีและบันทึกversion.
- [ ] รายงานไฟล์/commands/SHA/Tests-Failures-Errors-SkippedแยกSurefire/Failsafe, XML/runURL, ข้อจำกัดและreviewer. ห้ามใช้CI392เก่าตีตราB1ผ่าน.
- [ ] แนบdraftSQLchecksumและผลempty/populated/rerun/restore/schema-validationให้DBowner. กำหนดproductionversionจากhistoryจริงภายหลัง; หยุดoldwritersตอนrollout. ไม่มีmigrationฐานทีมในงานimplementationนี้.
- [ ] ให้AตรวจsecurityและB/Cตรวจcontract/diff; ผู้ใช้ตรวจงานก่อนcommit/push/PR/mergeตามคำสั่งรอบส่งมอบ. เตรียม commit `ci(b1): verify integrated approval revision coverage`.

## ลำดับส่งมอบ / สิ่งที่รอ

1. **สถานะล่าสุด:** ผู้ใช้อนุมัติให้ทำส่วน E ก่อนแล้ว; candidate อยู่ใน worktree `D:\PrimeSkill-worktrees\b1-role-e` บน `codex/b1-role-e` จาก source รวม `3187098`. ยังไม่ commit/push/merge และยังไม่รัน migration ฐานทีม.
2. **ส่วน E:** revision/schema draft, decision contract, web token, version lock/refresh, E PostgreSQL tests และ coverage gate. อ่าน `doc/b1-role-e-handoff.md` และ `doc/b1-implementation-test-report.md` ใน worktree สำหรับผลจริง. Task2 metadata ของ B และ tag guards ของ C ยังไม่ทำ; checklist เดิมเป็นเกณฑ์สำหรับ B1 รวมทีม จึงยังไม่ถือว่าครบทุก task.
3. **ก่อนเข้าทีม:** B/C/Aตรวจcontract/implementationและตกลงPR. ระบุว่าbranchที่ทดสอบรวมtagcodeแล้วหรือยัง.
4. **ก่อนฐานทีม:** DBownerยืนยันtarget/history/backup/restore/windowแล้วเท่านั้น. การอนุมัติแผนนี้ไม่เท่ากับอนุมัติrollout.

## Plan self-review

- [x] Policy12/fields/no-opอยู่Task2; revision/schema/overflowอยู่Task1/3.
- [x] REST/Webtoken, replay, strictinput, session/CSRFอยู่Task3/4.
- [x] Tag/versionทุกwritepathและsharedcatalogขอบเขตอยู่Task5/6.
- [x] Commitorders/staleentity/rollback/timeout/viewCountอยู่Task6.
- [x] CI sourcepin, externalfixtures, artifacts และmigrationgateอยู่Task7.
- [x] งานที่ยังต้องยืนยันไม่ถูกmarkว่าimplemented/tested/approvedแล้ว.
