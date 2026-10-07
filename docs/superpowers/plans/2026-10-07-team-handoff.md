# PrimeSkill Cross-role Handoff Implementation Plan

> **อัปเดตรอบส่งงาน:** อ่าน [PostgreSQL/race handoff](2026-10-07-postgres-race-handoff.md) ก่อนสำหรับสถานะและงานต่อ A/D/E/C ล่าสุด. ข้อความด้านล่างที่ระบุ D `e39a880`, local-only PostgreSQL/CI และ race ที่ยังไม่พิสูจน์เป็น snapshot ก่อนหน้า; D ที่ทดสอบล่าสุดคือ `0a92dbc`, E suite ผ่าน 269 และ D+E candidate overlay ผ่าน 165. เทส/แพตช์/CI ถูกจัดส่งใน branch E แล้ว แต่ race production integration, shared migration และผล CI ยังต้องตรวจต่อ. งาน C/B และข้อตกลง UI ด้านล่างยังเป็นรายการส่งต่อที่ต้องยืนยันกับเจ้าของ Role.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ให้ผู้รับช่วงงาน A–E ทำงานใน branch ของตนต่อได้ และมีเกณฑ์ตรวจรับก่อนเสนอรวมโค้ด โดยเอกสารนี้ไม่ได้สั่ง merge เข้า develop

**Architecture:** ใช้ User/Tool/session/CSRF และฐานข้อมูลชุดเดิม แยกเจ้าของงานตาม Role. C ใช้ batch summary ของ D สำหรับแสดงคะแนน และใช้ aggregate query ก่อน pagination สำหรับเรียงคะแนน. E ประสาน publishing concurrency, การทดสอบ PostgreSQL, migration และ CI.

**Tech Stack:** Java 17 target, Spring Boot 4.1.1, Spring Data JPA, Spring Security, Thymeleaf, PostgreSQL, Maven, Docker Compose, GitHub Actions.

**Spec:** [Role E design](../../../doc/role-e-design.md), [Implementation plan](../../../IMPLEMENTATION_PLAN.md), [UI guide](../../../UI_DESIGN_GUIDE.md), [D review contract ที่ตรวจ](https://github.com/aarktik/PrimeSkill/blob/e39a880405af717062bbb9def311ae331dd9b1b3/doc/role-d-review-contract.md), [Migration readiness](../../../doc/role-e-migration-readiness.md).

## อ่านก่อนเริ่ม

- เอกสารนี้เป็นแผนส่งต่อ ไม่ใช่รายงานว่างานด้านล่างทำเสร็จแล้ว. Checkbox ที่ยังว่างหมายถึงยังต้องทำ/ตรวจรับ.
- ตรวจ remote ใหม่วันที่ 7 ตุลาคม 2026: E `76cdd65`, develop `d232282`, A `7c2a1ce`, B `04a9023`, C `281e3ac`, D `e39a880`.
- A อยู่ใน E แล้ว; B/C อยู่ใน develop แต่ยังไม่อยู่ใน E; D อิง develop และยังไม่รวมเข้า develop ณ snapshot นี้.
- อ่านโค้ดของเจ้าของ Role ผ่าน `git show <ref>:<path>` ได้โดยไม่ checkout/merge. ชื่อไฟล์ C/D ที่ระบุด้านล่างอาจยังไม่มีใน E branch ซึ่งเป็นเรื่องปกติ.
- งาน PostgreSQL/CI รอบล่าสุดยังเป็น **local changes ที่ไม่ได้ส่งมาพร้อมแผนนี้**: local รัน Surefire 170 + PostgreSQL 70 กรณีผ่านบน Java 26.0.1/PostgreSQL 18.6. ผู้ clone branch นี้ยังใช้ผลนี้แทนการรันทดสอบโค้ดของตนไม่ได้.
- UI ธีม KKU ยังเป็น local experiment ไม่ใช่ธีมที่ทีมตกลง. เอกสารกลางที่ตรวจยังเป็น navy/light.

## Global Constraints

- ไม่สร้าง User/Tool model หรือระบบ login/session ซ้ำ; actor มาจาก session และคำขอเปลี่ยนข้อมูลต้องผ่าน CSRF.
- Public browse/search แสดงเฉพาะ PUBLISHED. การอ่าน draft/pending/deprecated ต้องใช้ visibility contract เดิม.
- D contract: rating เป็นจำนวนเต็ม 1–5, รีวิวหนึ่งรายการต่อ user/tool, owner ห้ามรีวิวตัวเอง, create/edit เฉพาะ PUBLISHED, delete โดย author/admin ได้แม้ tool ถูกซ่อน.
- Empty summary: `avgRating = null`, `reviewCount = 0`; rating sort เป็นค่าเฉลี่ยเต็มความละเอียด DESC, unrated last, `tool.id ASC` เมื่อคะแนนเท่ากัน.
- ใช้ database constraint เป็นตัวคุมความซ้ำจริง และทดสอบ race บน PostgreSQL ด้วย transaction แยกกัน.
- ยังไม่เปิด Flyway/รัน V7/V8 บนฐานร่วม จนกว่าจะยืนยัน baseline, ลำดับ migration, preflight และ backup/restore.
- ทำงาน/เสนอ commit ใน branch ของเจ้าของงาน. การ merge/push develop และการเปลี่ยนธีมกลางต้องตกลงแยกต่างหาก.

## Review Focus

- Pagination และหลาย tag ต้องไม่ทำให้ tool ซ้ำหรือ totalElements ผิด — Task C2.
- การเพิ่ม/แก้/ลบรีวิวต้องสะท้อนคะแนนใน request ถัดไป และไม่เกิด N+1 — Task C1.
- รีวิวแข่งกับ deprecate ต้องมีผลตามลำดับ transaction ที่ตกลง — Task D1.
- การแก้ข้อมูลหลัง submit ต้องไม่ทำให้แอดมินอนุมัติข้อมูลคนละชุดกับที่ตรวจ — Task B1.
- ข้อมูลเก่าเกินความยาวหรือ URL สองคอลัมน์ขัดกันต้องหยุดก่อนเปลี่ยนข้อมูล — Task E2.

## วิธีเริ่มและลำดับส่งต่อ

1. ตรวจ `git status --short` ก่อนทำงาน; เก็บงานเดิมให้เรียบร้อยโดยไม่ reset/discard งานคนอื่น.
2. อัปเดตข้อมูล remote ด้วย `git fetch origin '+refs/heads/*:refs/remotes/origin/*'`; อ่าน snapshot ใหม่ก่อนยึดข้อสรุปในแผนนี้.
3. อ่าน task ที่ตนเป็นเจ้าของและ spec ที่เกี่ยวข้อง. ทำ failing test → ยืนยัน FAIL → implement → ยืนยัน PASS → ตรวจ diff → commit เฉพาะไฟล์ของ task ที่ตรวจแล้ว.
4. C เริ่ม C2 ได้จาก branch C/develop; C1 รอให้ interface ของ D พร้อมใน checkout ที่ใช้ทำงาน หรือเริ่ม controller test ด้วย mock contract ได้ก่อน. D เริ่มทดสอบความเสี่ยงใน branch D ได้ แต่ทดสอบ lock ร่วมกับ E หลังมีทั้งสอง implementation เท่านั้น.
5. B เริ่มตกลง policy/เขียน test ได้ทันที; E เตรียม CI/migration ได้ทันที; UI ให้ตกลงธีมก่อนเปลี่ยน shared styles.
6. เมื่อส่งงาน ให้ระบุ branch, commit SHA, ไฟล์ที่แก้, คำสั่งและผลทดสอบ, dependency และข้อจำกัด. ผู้อ่านต้องแยกผลที่รันจริงออกจากสิ่งที่ยังรอ.

## Task C1 — แสดงคะแนนและจำนวนรีวิวบน Browse

**Owner:** C; D ยืนยัน summary contract. **ใช้ทำอะไร:** ผู้ใช้เห็นข้อมูลรีวิวบนการ์ดโดยไม่ query รีวิวแยกทีละ tool.

**Files (จาก branch C/D):**
- Modify: `code/src/main/java/com/example/toolhub/controller/web/ToolBrowseWebController.java`
- Modify: `code/src/main/resources/templates/tools/list.html`
- Test: `code/src/test/java/com/example/toolhub/controller/web/ToolBrowseWebControllerTest.java`
- Reference: `code/src/main/java/com/example/toolhub/service/ReviewSummaryService.java` และ `dto/response/ReviewSummary.java` ของ D.

**Interfaces:** ใช้ `Map<Long, ReviewSummary> summarizeByToolIds(Collection<Long> toolIds)`; record คือ `ReviewSummary(Long toolId, Double avgRating, long reviewCount)`. ส่ง model attribute ใหม่ `reviewSummaries` ให้ template โดยเรียก service ครั้งเดียวสำหรับ IDs ในหน้าปัจจุบัน; empty page ไม่เรียก service. ไม่ขยาย REST DTO โดยไม่มีข้อตกลง.

- [ ] เพิ่ม `browseShowsBatchedReviewSummaries`: สองการ์ดได้คะแนน/count ถูก ID; verify service ถูกเรียกหนึ่งครั้งด้วย IDs ทั้งหน้า.
- [ ] เพิ่ม `emptyBrowseSkipsSummaryQuery`, `unratedCardShowsNoReviews` และ `browseDoesNotRenderHiddenToolSummary`: หน้าเปล่าไม่ query, count=0 ไม่แสดงคะแนน 0 เป็นคะแนนจริง, ไม่ render tool ที่ถูกซ่อน.
- [ ] รัน `mvn -B -f code/pom.xml "-Dtest=ToolBrowseWebControllerTest" test` ยืนยันว่า assertion ใหม่ FAIL ก่อนแก้.
- [ ] Inject summary service ใน controller; template แสดงทศนิยมเพื่ออ่านเท่านั้น ไม่ใช้ค่าปัดเศษเรียงลำดับ.
- [ ] ใน integration test ที่มี C/D จริง สร้าง → แก้ → ลบรีวิว และเปิด browse ใหม่แต่ละครั้ง; assert avg/count เปลี่ยนทันที. เพิ่ม query-budget assertion ว่าจำนวน aggregate query ไม่โตตามจำนวนการ์ด.
- [ ] รัน test ที่เพิ่มและ `mvn -B -f code/pom.xml verify`; ตรวจ empty/loading/error states ตาม UI guide แล้ว commit task นี้ใน branch C.

**Done:** ผลคะแนนถูกต้อง, ไม่มี N+1, ไม่ทำลาย filter/pagination เดิม และมีผลทดสอบแนบ.

## Task C2 — เรียงคะแนนจริงก่อนแบ่งหน้า

**Owner:** C. **ใช้ทำอะไร:** ผล `sort=rating` เรียงตามรีวิวจริง ไม่ตกกลับไป newest.

**Files:**
- Modify: `code/src/main/java/com/example/toolhub/repository/ToolRepository.java`
- Modify: `code/src/main/java/com/example/toolhub/service/impl/ToolSearchServiceImpl.java`
- Modify: `code/src/main/java/com/example/toolhub/service/search/RatingToolSortStrategy.java`
- Test: `code/src/test/java/com/example/toolhub/service/ToolSearchServiceImplTest.java`
- Create (proposed): `code/src/test/java/com/example/toolhub/RatingSearchIntegrationTest.java`

**Interfaces:** คง `Page<ToolResponse> search(String keyword, Long categoryId, List<String> tagSlugs, String sort, int page, int size)`. แยกเส้นทาง RATING ใน service เช่นเดียวกับแนวทาง RELEVANCE เดิม; ใช้ grouped review aggregate + filtered tools ที่ DB และ count query ที่ให้ total tools จริง. อย่าใช้ batch display service มา sort หลัง pagination.

- [ ] เพิ่ม fixture PUBLISHED 4 tools: A ratings 5/4 (4.5), B rating 5, C unrated, D rating 5; กำหนด ID และ createdAt ให้ newest ให้ลำดับต่างจาก rating.
- [ ] Test `ratingSortOrdersBeforePagination`: B/D เรียง ID ASC ก่อน A แล้ว C; size=2 สองหน้ารวมครบ 4 IDs ไม่มีซ้ำและ totalElements=4.
- [ ] Test `ratingSortPreservesFiltersAndDistinctCount`: ใช้ keyword/category/หลาย tags รวมกันแล้วได้เฉพาะ tools ที่ตรง contract tag เดิม; tool ที่ match หลาย join ไม่ซ้ำ; DRAFT/PENDING/DEPRECATED ไม่ปรากฏ.
- [ ] Test `ratingSortUsesFullPrecision`: ค่าเฉลี่ยต่างกันแต่แสดงทศนิยมเท่ากันยังเรียงด้วยค่าจริง; empty result คืน page/count ถูกต้อง.
- [ ] รัน `mvn -B -f code/pom.xml "-Dtest=RatingSearchIntegrationTest,ToolSearchServiceImplTest" test` ยืนยัน FAIL แล้วแก้ query/service/strategy ตาม contract.
- [ ] ยืนยัน PASS บน H2 และ PostgreSQL ใน test setup ที่พร้อม; ตรวจ SQL/count query และ query plan ของข้อมูลตัวแทน แล้ว commit ใน branch C.

**Done:** ไม่มี fallback newest สำหรับ rating; filter, stable tie-break, null handling และ pagination ถูกต้องบน PostgreSQL.

## Task D1 — รีวิวแข่งกับการเลิกเผยแพร่

**Owner:** D + E. **ใช้ทำอะไร:** ป้องกันการบันทึก/แก้รีวิวจากสถานะที่ถูกอ่านก่อน transaction อื่นเปลี่ยน tool.

**Files:**
- Modify: `code/src/main/java/com/example/toolhub/service/impl/ReviewServiceImpl.java` (D)
- Coordinate: `code/src/main/java/com/example/toolhub/repository/ToolRepository.java`, `service/impl/PublishingServiceImpl.java` (E)
- Test: `code/src/test/java/com/example/toolhub/service/ReviewServiceImplTest.java`
- Create (proposed): `code/src/test/java/com/example/toolhub/ReviewPublishingPostgresIT.java`
- Modify: `doc/role-d-review-contract.md` ใน branch D เพื่อบันทึก policy ที่ตกลง.

**Interfaces:** E มี `Optional<Tool> findForUpdateById(Long id)` ใช้ PESSIMISTIC_WRITE. เสนอให้ review create/update และ deprecate ใช้ tool-row lock ร่วมและตรวจสถานะอีกครั้งภายใน transaction เดียวกัน; D/E ต้องยืนยันกติกาก่อนเปลี่ยน behavior. ความเสี่ยงนี้ยังไม่ใช่บั๊กที่พิสูจน์แล้ว.

- [ ] ตกลงกติกา: ถ้า deprecate ได้ lock/commit ก่อน review ต้องไม่ถูกสร้าง/แก้; ถ้า review ได้ lock/commit ก่อน อนุญาตรีวิวนั้นแล้ว deprecate ต่อได้. ระบุ API error ตาม shared visibility/error contract ที่ใช้จริง ไม่เดา status ใหม่.
- [ ] สร้าง test ด้วย transaction/connection แยกและ latch/barrier บน PostgreSQL; ใช้ timeout ทุก wait ไม่ใช้ sleep เป็นหลักฐาน.
- [ ] Test ทั้ง create และ update สองลำดับ; assert ผลตอบกลับและ persisted row ไม่เปลี่ยนเมื่อถูกปฏิเสธ. ทวน owner/self-review, duplicate, wrong tool ID และ author/admin delete หลังซ่อน tool.
- [ ] รัน failing reproduction ก่อนแก้; ถ้าไม่พบ failure ให้บันทึกหลักฐานและตรวจ isolation/lock จริงก่อนเพิ่ม lock.
- [ ] หากต้องแก้ ให้ lock tool ก่อน review row อย่างสม่ำเสมอ เพื่อลด deadlock; ตรวจสถานะจาก tool ที่ lock แล้วและรักษา authorization/visibility เดิม.
- [ ] เชื่อม IT ใหม่เข้ากับ runner ที่ใช้งานจริง: ชื่อ `ReviewPublishingPostgresIT` ตรง include `*PostgresIT` ของ E profile ที่ยังรอส่ง. รัน `mvn -B -f code/pom.xml -Ppostgres-it verify` เมื่อมี profile/ฐานทดสอบพร้อม แล้วส่ง SHA/ผลให้ E.

**Done:** กติกาที่ตกลงมีผล deterministic จาก test PostgreSQL; ไม่อ้าง mock/H2 อย่างเดียวว่า concurrency ถูกต้อง.

## Task B1 — Policy การแก้ Tool ระหว่างตรวจ/หลังเผยแพร่

**Owner:** B + E; ผู้รับผิดชอบ requirement ยืนยัน policy. **ใช้ทำอะไร:** ให้ข้อมูลที่ admin อนุมัติเป็นชุดเดียวกับข้อมูลที่จะเผยแพร่.

**Files:**
- Modify after decision: `code/src/main/java/com/example/toolhub/service/impl/ToolServiceImpl.java`
- Coordinate: `code/src/main/java/com/example/toolhub/service/impl/PublishingServiceImpl.java`
- Test: `code/src/test/java/com/example/toolhub/service/ToolServiceImplTest.java`
- Test after integration: `code/src/test/java/com/example/toolhub/RoleEFlowIntegrationTest.java`
- Record: `doc/role-e-design.md` และ contract ของ B.

**Interfaces:** คง public signature ของ `ToolService.update` เดิม. ตอนนี้ version CRUD ของ E จำกัด DRAFT; อย่านำ policy นั้นไปบังคับ tool metadata โดยไม่มี requirement.

- [ ] B/E/เจ้าของ requirement เลือกและบันทึก policy สำหรับ DRAFT/PENDING/PUBLISHED/DEPRECATED: ปฏิเสธการแก้ หรืออนุญาตพร้อมกลับไป DRAFT และขออนุมัติใหม่. ระบุช่องข้อมูลที่ครอบคลุมและ actor ที่ทำได้.
- [ ] เพิ่ม parameterized tests ครบ 4 สถานะ × owner/admin/other-user สำหรับ policy ที่ยืนยัน; ตรวจค่าจริงใน DB และ HTTP error contract.
- [ ] เพิ่ม race `update` กับ `approve`: ไม่มีทาง approve ข้อมูลชุดที่ไม่ผ่านกระบวนการที่ตกลง. ใช้ row lock/version strategy ร่วมกับ E ภายใน transaction ไม่แยก read-check-save.
- [ ] รัน tests ให้ FAIL → implement → PASS; ทวน validation และ atomic published non-owner view counter ของ B ว่ายังทำงาน แล้วส่ง commit ใน branch B.

**Done:** มี policy เป็นลายลักษณ์อักษรและ state/authorization/concurrency tests รองรับ. ถ้า policy ยังไม่ตกลง ส่ง test proposal ได้ แต่ยังไม่เปลี่ยน production behavior.

## Task UI1 — ยืนยันธีมกลางและแบ่งเจ้าของหน้า

**Owner:** ทั้งทีมเลือกผู้ดูแล shared UI หนึ่งคน; แต่ละ Role ดูแลหน้าของตน. **ใช้ทำอะไร:** ป้องกันสี/navbar/form/error state ต่างกันเมื่อรวมหน้า.

**Files:** `UI_DESIGN_GUIDE.md`, `code/src/main/resources/templates/fragments/`, `code/src/main/resources/static/css/role-e.css` และ templates ของแต่ละ Role ที่ตกลงขอบเขตแล้ว.

- [ ] ยืนยัน navy/light เดิมหรือ KKU; ถ้าเลือก KKU ให้บันทึก palette, semantic colors, contrast, typography, spacing, components และ screenshot ตัวอย่างใน UI guide ก่อนปรับหลาย branch.
- [ ] ระบุเจ้าของ shared nav/footer/alerts/styles เพียงคนเดียว; A auth, B catalog/detail, C browse/search, D reviews, E versions/moderation. ถ้าไฟล์ detail ใช้ร่วม B/D ให้กำหนดจุดประกอบ component และผู้แก้ไฟล์หลัก.
- [ ] แต่ละ Role ใช้ guide/version เดียวกันและ shared fragments; ตรวจ 375/768/1280px, keyboard/focus, validation, empty/error และข้อความภาษาไทย.
- [ ] ตรวจ summary/review/version ในหน้า detail ร่วมกัน ไม่แทนที่ส่วนของ Role อื่น; แนบภาพและผล browser review ก่อนส่ง commit.

**Done:** ธีมและ component ownership ยืนยันแล้ว; ทุกหน้าที่เปลี่ยนมีหลักฐาน review. ยังไม่ถือว่า KKU ได้รับอนุมัติจากการมีภาพทดลอง.

## Task E1 — ส่งและตรวจ PostgreSQL/CI ที่เตรียมไว้

**Owner:** E. **ใช้ทำอะไร:** ตรวจ regression บนฐานจริงและจับปัญหาสร้าง/เริ่ม container โดยไม่ต้องรวม develop ก่อน.

**Files ที่ยังเป็น local changes ณ วันที่เขียนแผน:** `.github/workflows/build.yml`, `code/pom.xml`, `scripts/test-postgres.ps1`, `code/src/test/resources/application-postgres-test.properties`, `code/src/test/java/com/example/toolhub/RoleEPostgresIT.java`, `ModerationPostgresIT.java`, `support/PostgresTestDatabaseGuard.java`, `support/PostgresTestDatabaseGuardTest.java`, comment ใน `RoleEFlowIntegrationTest.java`, `README.md`, `doc/role-e-local-runbook.md`.

- [ ] ให้ผู้ใช้ตรวจ diff ของชุดนี้ก่อน commit/push แยกจาก UI. ผู้อ่าน remote ต้องรอ SHA ชุดนี้ก่อนใช้คำสั่ง profile/script; อย่าสร้างไฟล์ทับ local implementation ที่มีอยู่.
- [ ] หลังพร้อม รัน `./scripts/test-postgres.ps1`; ต้องรายงาน Surefire/Failsafe failures=0/errors=0 และยืนยัน cluster ของ script หยุดแล้ว. ถ้าโค้ดเปลี่ยน ห้ามใช้จำนวน 170/70 เดิมแทนผลใหม่.
- [ ] ส่งชุดที่ตรวจแล้วเข้า branch E และตรวจ GitHub Actions run จริง: Java 17/PostgreSQL 17, reports ทั้งสอง job และ Compose startup/restart persistence. ตอนนี้ยืนยันได้เพียง local Java26/PG18.6 และ syntax ของ workflow.
- [ ] บนเครื่องที่มี Docker ตรวจ `docker compose config`, build, health UP, ใส่ข้อมูลทดสอบ, stop/start แล้วอ่านข้อมูลเดิม. ใช้ volume เฉพาะเทส; ห้ามลบ volume ที่มีงานจริง.
- [ ] บันทึก run URL/SHA/ผลและข้อจำกัดใน runbook. ไม่ deploy หรือ push image จาก task นี้.

**Done:** มีผล workflow จริงผูกกับ SHA; Docker health/persistence ผ่าน; secrets ไม่อยู่ใน Git. งานนี้เริ่มได้โดยไม่ merge develop.

## Task E2 — เตรียม Migration และ Integration หลังทีมพร้อม

**Owner:** E ร่วม B/C/D. **ใช้ทำอะไร:** รวม schema/behavior โดยเก็บข้อมูลเดิม และยืนยัน flow ข้าม Role.

**Files:** [migration readiness](../../../doc/role-e-migration-readiness.md), `doc/sql/V7__align_existing_tool_catalog.sql` (draft E), `doc/sql/V8__limit_review_comment_length.sql` (draft D), `code/src/main/resources/schema.sql`, approved migration directory/runner หลังทีมตัดสินใจเท่านั้น.

- [ ] ตรวจ target schema/history และเลือกลำดับ migration ที่ไม่ชน; V7/V8 ยังเป็นเลขร่าง. ยืนยันชื่อ/ความยาว tags/version, URL columns และ manifest scope ก่อนเขียน migration จริง.
- [ ] เพิ่ม preflight cases: website_url อย่างเดียว, repository_url อย่างเดียว, ทั้งสองคอลัมน์ข้อมูลต่างกัน, รีวิวเกิน 2,000 ตัวอักษร และค่าที่เกินความยาวเป้าหมาย. ข้อมูลขัดกันต้องได้รายงาน/หยุด ไม่มี silent truncate/drop.
- [ ] หลังอนุมัติ sequence ให้ทดสอบ empty DB + copy schema/data เก่า + rerun runner, Hibernate validate, FK/unique/cascade, C tag index และ D comment check; เปรียบเทียบ row counts/ข้อมูลก่อนหลังและทดสอบ restore.
- [ ] ก่อนเสนอ integration อ่าน diff/conflicts ล่าสุด. Snapshot นี้ merge simulation E/develop พบ `SecurityConfig.java`, `ToolRepository.java`, `templates/tools/list.html`; กับ D เพิ่ม `templates/tools/detail.html`. รักษา E lock/publishing, C browse/filter และ D review UI ครบ ไม่เลือก ours/theirs ทั้งไฟล์.
- [ ] หลังได้รับอนุญาตให้รวมใน checkout สำหรับ integration จึงทดสอบ approve → public browse/search/version/reviews; deprecate → ซ่อน public แต่ author/admin ยังลบรีวิวได้; restore → draft และต้อง submit ใหม่.
- [ ] ทดสอบ detail counter เฉพาะ published non-owner และไม่เพิ่มจาก moderation/version reads. ทวน session/CSRF ของ A; ตอนนี้ไม่มีงานแก้ A ที่พิสูจน์ว่าจำเป็น หากพบ regression ให้แนบ reproduction ส่ง A.
- [ ] ส่งรายงาน integration พร้อม SHA ทุก Role และรายการ fail/pending. การ merge develop/deploy เป็นการตัดสินใจขั้นถัดไปของทีม.

**Done:** approved migration ผ่านข้อมูลตัวแทนและ restore; integration มีหลักฐานจากโค้ดที่รวมจริง. ก่อน dependency พร้อมให้ทำเอกสาร/preflight fixtures ได้ แต่ไม่อ้าง integration ผ่าน.

## รูปแบบส่งมอบแต่ละ Task

- Owner / Task ID / branch / commit SHA
- พฤติกรรมที่เพิ่มหรือเปลี่ยน และไฟล์ที่เกี่ยวข้อง
- คำสั่งทดสอบ + สภาพแวดล้อม + จำนวน tests/failures/errors + report/run URL
- Screenshot สำหรับ UI และ DB assertions สำหรับ state/migration/concurrency
- Dependency ที่ยังรอ, policy ที่ต้องยืนยัน และงานที่ยังไม่ได้ตรวจ
- สถานะ ready for review; ผู้ดูแล integration เป็นคนประเมินการรวมเข้า develop ต่อ
