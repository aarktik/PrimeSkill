# B1 — Tool edit policy และ approval ของข้อมูลที่ตรวจจริง

วันที่ 8 ตุลาคม 2026. **สถานะ: ข้อเสนอพร้อมตรวจ ยังไม่อนุมัติ ยังไม่ใช่ implementation.**

เจ้าของ requirement: ผู้ใช้/ทีม. ผู้ทำงาน: B (metadata), E (publishing/version/schema), C (tool-tag associations), A (security/review). การตอบรับข้อความของ Role อื่นไม่ถือว่าอนุมัติ spec นี้โดยอัตโนมัติ.

## 1. เป้าหมายและขอบเขต

ทำให้การอนุมัติผูกกับรอบส่งตรวจที่ admin อ่านจริง และไม่ให้ข้อมูลของรอบนั้นเปลี่ยนผ่าน metadata/tag/version writes ระหว่าง PENDING หรือหลัง PUBLISHED. รักษากติกา review/deprecate ที่ตกลงแล้ว ไม่เปลี่ยนสิทธิ์ลบรีวิว ไม่แก้ viewCount และไม่ merge develop หรือรัน migration ฐานทีมจากเอกสารนี้.

ข้อ 3 ใน handoff คือการตกลง policy/contract ก่อน implementation. เอกสารนี้เติมข้อเสนอครบ แต่ B1 ยังไม่ผ่าน acceptance จนกว่าทีมยืนยันและนำไปทำ/ทดสอบจริง. CI392 ของงานก่อนหน้าไม่ใช่หลักฐาน B1.

### หลักฐานที่อ่าน

- B proposal ที่ `4208e4562aaf728caefde4093664a17af0b6411b`: `doc/role-b-tool-edit-policy-proposal.md`. B baseline12ยังบันทึกพฤติกรรมเก่า ไม่ใช่ acceptance.
- Source รวม `3187098fac9d91c14d1fdfe40bb765797cde8cfd` มี D/E/A test config แต่ยังไม่มี B1.
- `ToolServiceImpl.update` ใช้ findById; owner/adminแก้ได้ทุกสถานะ, statusเดิมคงอยู่.
- `PublishingServiceImpl.transition` lock Tool row แต่ไม่ refresh/เทียบ revision. Admin approve/reject ส่งแค่ id.
- `ToolVersionServiceImpl` มี row lockและowner/DRAFT guard แต่ไม่มี refreshหลังlock.
- ในsourceรวม `TagServiceImpl.assignTag/unassignTag` ใช้findByIdและowner/admin guard ไม่มีDRAFT guard.
- `ToolWebController` และ moderation template ต้องปรับตามcontractใหม่ด้วย; branchEปัจจุบันไม่มีtag association codeของC จึงห้ามอ้างว่าการแก้เฉพาะbranchEจะปิดทุกช่องทางได้แล้ว.

## 2. แนวทางที่พิจารณา

1. **แนะนำ: แก้เฉพาะ DRAFT + revision ของแต่ละ submission.** Pending candidateคงที่; metadata/tag/versionใช้Tool row lockร่วมกัน. โค้ดและการทดสอบตรงไปตรงมา แต่เจ้าของต้องผ่านrejectหรือdeprecate/restoreก่อนแก้รายการที่ส่งแล้ว.
2. แก้สถานะอื่นแล้วกลับDRAFT + revision. ใช้งานคล่องขึ้นแต่ต้องยืนยันว่าการแก้จะถอนรายการpublicทันที และต้องทดสอบทุกwritepath/transitionเพิ่มเติม.
3. เก็บ published revision แยกจาก draft revision. Publicยังอยู่ระหว่างแก้ แต่ต้องมีข้อมูล/หน้าจอ/versioningแยกมากขึ้น เกินB1ปัจจุบัน.

รายละเอียดต่อจากนี้เป็นแนวทาง1ที่เสนอ ยังไม่ถือว่าผู้ใช้เลือกแล้ว. หากเลือกแนวทาง2หรือ3ต้องแก้specก่อนimplementation.

## 3. Policy ครบ 12 กรณี

ใช้กับ PUT metadataที่ผ่านการยืนยันตัวตนและvalidationแล้ว. Adminที่เป็นownerใช้สิทธิ์owner; adminอื่นไม่มีข้อยกเว้นด้านสถานะ.

- DRAFT / owner: 200; แก้ได้, next=DRAFT.
- DRAFT / admin: 200; แก้ได้, next=DRAFT.
- DRAFT / other user: 403 ACCESS_DENIED; ไม่มีข้อมูลเปลี่ยน.
- PENDING / owner: 409 INVALID_STATE_TRANSITION; next=PENDINGเดิม.
- PENDING / admin: 409 INVALID_STATE_TRANSITION; next=PENDINGเดิม.
- PENDING / other user: 403 ACCESS_DENIED; ไม่มีข้อมูลเปลี่ยน.
- PUBLISHED / owner: 409 INVALID_STATE_TRANSITION; next=PUBLISHEDเดิม.
- PUBLISHED / admin: 409 INVALID_STATE_TRANSITION; next=PUBLISHEDเดิม.
- PUBLISHED / other user: 403 ACCESS_DENIED; ไม่มีข้อมูลเปลี่ยน.
- DEPRECATED / owner: 409 INVALID_STATE_TRANSITION; next=DEPRECATEDเดิม.
- DEPRECATED / admin: 409 INVALID_STATE_TRANSITION; next=DEPRECATEDเดิม.
- DEPRECATED / other user: 403 ACCESS_DENIED; ไม่มีข้อมูลเปลี่ยน.

Anonymousใช้401เมื่อCSRFถูกต้อง; missing/invalidCSRFอาจถูกsecurityfilterปฏิเสธ403ก่อนcontroller. Toolไม่มีจริงใช้404 RESOURCE_NOT_FOUND. ไม่เปิดเผยสถานะในerrorของผู้ใช้ที่ไม่มีสิทธิ์.

### Fields และ no-op

- Metadata: name, slug, shortDescription, description, categoryId, repositoryUrl ใช้กติกาเดียวกันทุกfield. คงvalidation/unique/FKเดิม.
- ownerId, status, viewCount, createdAt และreviewRevisionไม่ใช่fieldที่clientเปลี่ยนได้ผ่านPUT. ไม่เพิ่มmass assignment.
- DRAFT PUTที่ค่าเหมือนเดิม: สำเร็จ200, status/reviewRevisionไม่เปลี่ยน; ไม่กำหนดupdatedAtเป็นapprovaltoken. ไม่เปลี่ยนnormalizationเดิมโดยแฝงมากับB1.
- No-opในPENDING/PUBLISHED/DEPRECATEDยังปฏิเสธ409เหมือนwriteอื่น เพื่อไม่เปิดช่องทางหลบguardและลดความกำกวมของการเปรียบเทียบค่า.
- slugแก้ได้เฉพาะDRAFT; slugเก่าไม่มีredirectaliasเพิ่มในB1. unique conflict409และไม่เปลี่ยนfieldอื่น.
- ส่งcategoryIdเดิมไม่เปลี่ยนสิทธิ์; categoryไม่มีจริง404, slugซ้ำ409. การปฏิเสธต้องrollbackทุกfield.

### Tags / versions / shared catalog

- การassign/unassigntagของTool: owner/adminเฉพาะDRAFT, ใช้lock+refreshToolก่อนเขียนassociation. กรณีtagซ้ำ/associationไม่มีคงerrorเดิมหลังผ่านสิทธิ์/สถานะ.
- Versions: คงowner-only/DRAFT-onlyเดิม; adminที่ไม่ใช่ownerยัง403. เพิ่มrefreshหลังlockเพื่อไม่ใช้DRAFTที่cacheค้างอยู่.
- ขอบเขตcandidate: metadataของTool, categoryId, ชุดtagIdและข้อมูลToolVersions. Reviews, rating aggregatesและviewCountเปลี่ยนได้โดยไม่ทำให้approvalเก่าเสียเพราะไม่ใช่เนื้อหาที่ownerส่งตรวจ.
- ชื่อ/คำอธิบายCategoryและTagเป็นshared catalogที่adminดูแลแยก; ข้อเสนอนี้รับรองreference IDs ไม่freezeฉลากshared catalog. หากทีมต้องการfreezeฉลากด้วย ต้องขยายขอบเขตก่อนเริ่ม.
- TagService.deleteปัจจุบันตรวจin-useแล้วคืน409 RESOURCE_CONFLICT; ต้องรักษาcontractนี้ และเพิ่มการserializeกับassignเพื่อป้องกันcheck/delete raceที่DBcascadeอาจทำให้associationใหม่หาย. ลบassociationเฉพาะDRAFTก่อนลบtagได้; tagที่ผูกกับpending/publishedต้องรอworkflowที่อนุญาต. ให้Cตรวจlockstrategyร่วมกับassign; ไม่อ้างว่าin-use guardปัจจุบันหายไป.
- ตรวจทุกwritepathรวมadminendpoint/scripts; การแก้DBตรงหรือwriterจากappเวอร์ชันเก่าไม่ถูกป้องกันโดยserviceguard. ต้องหยุดwritersเก่าระหว่างrollout. หากมีwriterภายนอกต้องออกแบบDBenforcementก่อนอ้างว่าคุ้มครองทุกwriter.

## 4. Submission revision

เพิ่ม `tools.review_revision BIGINT NOT NULL DEFAULT 0` พร้อมCHECK>=0; JPAfield `long reviewRevision` และgetter. ToolResponse/ToolMapperเพิ่มfield `reviewRevision` แบบJSONinteger. ไม่ใช้updatedAtหรือ@Versionเป็นreviewtokenเพราะviewCountและauditingไม่ควรทำให้candidateเสีย.

- Createเริ่ม0; SUBMITที่สำเร็จเพิ่ม1ภายในtransactionเดียวกับDRAFT→PENDING.
- รอบใหม่ต้องเพิ่มแม้ไม่แก้metadata: REJECT→DRAFT→SUBMITทำให้tokenเก่าใช้ไม่ได้. ห้ามresetค่าหรือreusetoken.
- APPROVE/REJECTคงrevisionเดิม; DEPRECATE/RESTOREคงเดิม; ครั้งถัดไปSUBMITเพิ่มอีก.
- Metadata/tag/versionwritesในDRAFTไม่เพิ่มreviewRevision: tokenเป็นรอบส่งตรวจ ไม่ใช่เลขการแก้ทุกครั้ง. การปิดwritesในPENDINGเป็นเงื่อนไขจำเป็นของวิธีนี้.
- SUBMITต้องตรวจoverflowและrollbackถ้าบวกไม่ได้ ห้ามwrapกลับ0.
- Pendingเดิมตอนmigrationrevision0: adminต้องreloadcandidateจากแอปใหม่ก่อนapproveด้วยexpected0; oldclientที่ส่งidอย่างเดียวถูกปฏิเสธ. รอบSUBMITใหม่เริ่ม1.

## 5. API และ service contract ที่เสนอ

### Read

ToolResponseรวม `reviewRevision` ในdetail/listPending/transitionresponses. Adminต้องส่งค่าที่มาจากcandidateที่ตรวจ อย่าทำGETอัตโนมัติเพื่อเติมtokenใหม่ก่อนapproveโดยไม่ให้ผู้ใช้ตรวจเนื้อหาใหม่.

### Write

เส้นทางเดิม:

```http
POST /api/v1/admin/tools/42/approve
Content-Type: application/json
X-CSRF-TOKEN: <token ของ session>

{"expectedReviewRevision":7}
```

Rejectใช้bodyเดียวกันที่ `/api/v1/admin/tools/42/reject`. `ReviewDecisionRequest(@NotNull @PositiveOrZero Long expectedReviewRevision)`; bodyหาย/null/negative/ชนิดผิด→400ตามvalidation/malformedcontractเดิม. JSONrevisionต้องเป็นintegerจริง ไม่ยอมรับfractional/boolean/coercionที่ตัดทศนิยม; ต้องมีtestยืนยันdecoderconfigของโปรเจค. ห้ามมีfallbackapproveById.

- สำเร็จ200 ToolResponse; APPROVE=PUBLISHED, REJECT=DRAFT.
- Tokenไม่ตรง→409 `STALE_REVIEW_REVISION`; ข้อความบอกให้อ่านcandidateใหม่, ไม่เปลี่ยนข้อมูล. ใช้ErrorResponseรูปแบบเดิมและเพิ่มexceptionhandlerเฉพาะ.
- Actorไม่ใช่admin→403ก่อนตรวจrevision/status เพื่อไม่รั่วcandidatefacts.
- เช็คrevisionหลังlock+refreshและauthorization แล้วจึงเช็คstate machine. หากtokenเก่าและstateผิดพร้อมกันให้STALE_REVIEW_REVISION; tokenตรงแต่ไม่PENDINGให้INVALID_STATE_TRANSITION409.
- Double approve/rejectของtokenเดียวหลังตัดสินใจแล้ว:409 INVALID_STATE_TRANSITION; ไม่ทำซ้ำและไม่เพิ่มrevision.
- submit/deprecate/restoreไม่ต้องรับbodyrevisionในB1. Existinggenerictransitionต้องไม่ใช้เป็นช่องทางapprove/rejectแบบไม่มีtoken.

Service interfacesที่เสนอ:

```java
ToolResponse transition(Long toolId, PublishingAction action, CurrentActor actor);
ToolResponse decide(Long toolId, PublishingAction action,
                    long expectedReviewRevision, CurrentActor actor);
```

`transition`รับเฉพาะSUBMIT/DEPRECATE/RESTORE; เรียกAPPROVE/REJECTโดยไม่มีtokenต้องถูกปฏิเสธชัดเจนและไม่มีwrite. `decide`รับเฉพาะAPPROVE/REJECT. ทั้งสองใช้PublishingStateMachineเดิม ไม่ทำstate machineซ้ำในB.

### Web

- Moderationformsapprove/rejectมีhidden `expectedReviewRevision` จากToolResponseที่render พร้อมCSRF. ไม่เติมค่าปัจจุบันฝั่งserverแทนค่าฟอร์มเก่า.
- staleformแสดงข้อความให้เปิดตรวจใหม่; ห้ามauto-retryapproveด้วยtokenใหม่. PRGกลับคิวพร้อมerrorได้ตามwebpatternเดิม โดยไม่บอกว่าสำเร็จ.
- Metadataedit formและdashboardแสดงแก้ไขเฉพาะDRAFT. Serverguardยังต้องทำงานแม้ปลอมrequestหรือเปิดฟอร์มทิ้งไว้ก่อนsubmit.
- B update API/service signatureเดิมคงไว้; anonymous identityยังมาจากCurrentActorProvider. ReviewDecisionRequestไม่มีactorId/adminfield.

## 6. Transaction / lock / freshness

ทุกmetadata/tag association/version write, submit และdecisionเริ่มtransaction → lockToolForUpdate → refreshTool → authentication/ownership/role check → status/revision check → validateuniques/FK → mutate → flush/commit. Refreshก่อนmutation ห้ามrefreshหลังแก้แล้วจนทิ้งdirtychangesโดยไม่ตั้งใจ.

Lockorder: Toolก่อนToolTag/ToolVersion rows; multi-Tool operationถ้าจำเป็นให้เรียงToolidก่อน. Tagdeleteต้องใช้Taglockร่วมกับassignเพื่อป้องกันassociationเพิ่มระหว่างตรวจin-use; assignใช้Tool→Tag, deleteใช้Tagและตรวจin-useโดยไม่กลับไปlockTool (หลีกเลี่ยงวงจร). CategorydeleteยังอาศัยRESTRICT/FKและcontractเดิม.

- Updateแข่งSUBMITบนDRAFT: updateก่อน→candidateมีค่าที่commitแล้ว; submitก่อน→update409 ไม่มีค่าใหม่หลุดเข้าPENDING.
- Updateแข่งAPPROVEบนPENDING: updateต้อง409ในทั้งสองลำดับ; approvalpublishเฉพาะcandidateเดิม.
- E preloadcandidateเก่า→reject/edit/resubmitอีกtransaction→olddecision: refreshแล้วrevisionไม่ตรง409.
- B/C/E preloadDRAFT→อีกtransactionsubmit→oldwriter: refreshแล้วPENDINGทำให้write409.
- Rollbackก่อนcommitต้องคืนทั้งmetadata/status/revision/associations; waiterทำงานต่อจากstateที่commitจริง. ไม่คืน200ก่อนflushที่อาจล้มเหลว.
- Timeout/deadlockต้องไม่เหลือpartialwrites; ไม่ถือว่าtimeout=stale revision. เสนอ503 `CONCURRENT_OPERATION_RETRY` สำหรับlock/deadlockexceptionที่จำแนกได้จริง; clientreloadและให้ผู้ใช้ยืนยันใหม่ ไม่retrydecisionเงียบ. ห้ามแปลงdatabaseexceptionทุกชนิดเป็น503.

## 7. Acceptance catalog ที่ B/E/C ต้องนำไปเขียนเทส

รายการนี้เป็นspecของเทส ยังไม่มีการรันหรืออ้างผลB1ผ่าน.

### Persisted policy / fields

- Matrix12ตามข้อ3: status/HTTP/errorตรง; เมื่อdenyอ่านDBใหม่ยืนยันทุกmetadatafield/category/owner/status/viewCount/reviewRevisionไม่เปลี่ยน. Allowedตรวจครบทุกfieldและmapping.
- Anonymous; missingTool/category; slugduplicate; invalidURL/length/ratingไม่เกี่ยวอย่าแก้reviewvalidationในB1.
- DRAFTno-opสำเร็จและtokenเดิม; no-opอีก3status409; bodyปลอมowner/status/viewCount/revisionไม่เปลี่ยนfieldเหล่านี้.
- AdminmetadataDRAFTallowed; adminversionnon-owner403; adminotherstatusmetadata409.

### Revision / HTTP / browser

- create0→submit1→reject1→submit2; deprecate/restoreไม่reset; overflowrollback.
- Missing/null/negative/malformedtoken400, wrongtoken409, currenttokenapprove/reject200, replay409; authorization/CSRFก่อนrevision.
- Sessionจริง+CSRF: anonymous401เมื่อCSRFถูกต้อง, missing/invalidCSRF403, nonadmin403, forgedactorไม่เพิ่มสิทธิ์.
- PendingJSONและmoderationformมีtokenเดียวกับcandidate; oldformถูกปฏิเสธ, field/errorข้อความแสดงและไม่มีautoresubmit.
- Regression: reviews/deprecate10, DHTTP28, nativeRollback4+4, existingpublishing/version/viewCountยังผ่าน.

### PostgreSQL concurrent scenarios

ทุกกรณีใช้realservices/proxies, connections/transactionsแยก, latch/barrier, pg_blocking_pidsพร้อมtimeout และอ่านDBในtransactionใหม่หลังworkersจบ. เทสstaleentityต้องยืนยันว่าentitycacheเก่าจริงก่อนinvoke.

1. DRAFTmetadataupdateก่อนSUBMIT และSUBMITก่อนupdate.
2. PENDINGupdateก่อนAPPROVE และAPPROVEก่อนupdate; editถูกปฏิเสธทั้งสองและไม่มีpublishedข้อมูลใหม่.
3. BpreloadDRAFTแล้วSUBMITอีกtransaction; Epreloadcandidateเก่าแล้วREJECT/edit/RESUBMITอีกtransaction.
4. Adminอ่านrevisionr; REJECT→edit→SUBMITr+1; approve/rejectr409แม้statusกลับPENDING.
5. OtheruserraceSUBMIT/APPROVEทั้งลำดับ ไม่มีmutation.
6. Tagassign/unassignกับSUBMITทั้งลำดับ; versioncreate/update/deleteกับSUBMITทั้งลำดับ.
7. Tagdelete/assignrace: ไม่มีcandidateassociationหายโดยเงียบ; in-use409หรือassignไปtagที่ลบแล้ว404/FKconflictตามลำดับ ไม่มีpartialstate.
8. Firstwriterrollbackแล้วwaiterสำเร็จ; SUBMITrollbackไม่กินrevision; decisionrollbackไม่publish/rejectบางส่วน; metadata/category/slugconflictrollbackครบ.
9. Locktimeout/deadlock: boundedcompletion, errorcontractถูกต้อง, DBไม่เปลี่ยน, connectionคืนpoolและrequestถัดไปทำงานได้.
10. viewCountเพิ่มระหว่างadminอ่านกับapproveไม่ทำให้tokenเสีย; reviewscreate/update/deleteไม่เปลี่ยนreviewRevision.

### Schema / integration

- EmptyDBสร้างcolumn/check; populatedDBคงข้อมูล/countsและpendingเดิมrevision0; Hibernatevalidateผ่าน.
- Migrationrerunตามrunnerจริงไม่มีsideeffect; negative/nullrevisionถูกDBปฏิเสธ; oldschemaต้องfailชัดก่อนรับtraffic.
- รายงานแยกSurefire/Failsafe, failures/errors/skippedและSHA. ไม่เอาH2แทนconcurrencyPG และไม่รวมduplicateSurefireจากหลายjob.
- Bbaseline12เปลี่ยนเป็นacceptance ไม่เก็บชื่อbaselineแล้วอ้างpolicyผ่าน; ทำFAILก่อนimplementationแล้วPASSหลังแก้.
- Expandedworkflowปัจจุบันpin3187098; ทดสอบB1ต้องเลือกsourceใหม่ที่รวมB1และบันทึกSHAจริง.

## 8. File ownership และการส่งต่อ

- B: ToolServiceImpl.update, ToolWebController, metadataform/dashboard, Bacceptancetests; reuseToolRepositorylockเดิม.
- E: Tool.reviewRevision, schema.sql, ToolResponse/ToolMapper, ReviewDecisionRequest, PublishingService/Impl, PublishingRestController, RoleEWebController, RoleEWebExceptionHandler, moderationtemplate, stale/lockexceptionmapping, PostgreSQLrevision/racetests.
- C: TagServiceImpl.assignTag/unassignTag/deleteและtests; ต้องทำบนsourceที่มีCcodeจริง ไม่สร้างimplementationซ้ำในEbranchที่ยังไม่มี.
- E: ToolVersionServiceImplrefreshหลังlockและversion/SUBMITtests.
- A: session/CSRF/error/rolecontract reviewและตรวจไม่มีfallbackapproveById.
- SharedfilesTool/ToolResponse/Mapper/GlobalExceptionHandlerให้Eรวมdiffร่วมกับBก่อนintegration เพื่อลดconstructor/mockconflicts.

ไม่กำหนดmigrationเป็นV9ล่วงหน้า: DBownerต้องตรวจhistory/versioncollisionก่อนตั้งชื่อ. ExistingDBต้องforwardmigrationเพิ่มcolumn/check, ไม่พึ่งCREATE TABLE IF NOT EXISTSเพื่อแก้tableเดิม. MigrationSQL/runnerต้องreviewก่อนใช้ฐานร่วม; จำกัดwritewindowและdeploywritersใหม่พร้อมกัน.

## 9. Definition of done / gate

- [ ] ผู้ใช้ยืนยันpolicyและreviewwritten specนี้ รวมtagdeleteและversionexception.
- [ ] B/C/Aยืนยันcontract/ownership; ระบุreviewerและsourceที่จะรวม.
- [ ] เขียนimplementationplanตามspecที่ยืนยันและให้ผู้ใช้ตรวจ ก่อนเริ่มproductcode.
- [ ] Implementationครบทุกwritepath, APIs/webไม่มีช่องทางdecisionไม่ใช้token.
- [ ] Acceptanceใหม่ผ่านบนPGและregressionเดิมผ่านบนsourceSHAเดียวกัน พร้อมartifacts.
- [ ] Review/PRรับโค้ดจริงและDBownerอนุมัติrolloutแยกกัน.

จนกว่าจะครบgates ห้ามรายงานข้อ3ว่าเสร็จทั้งimplementation หรืออ้างว่าการapproveเดิมปลอดภัยแล้ว.
