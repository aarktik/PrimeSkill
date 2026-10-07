# PostgreSQL and Review–Publishing Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ให้ A/D/E รับช่วงตรวจและนำแพตช์ race เข้าโค้ดจริง ให้ C ต่อคะแนน และให้ผู้ดูแลฐานข้อมูลรัน migration ได้ตามขั้นตอนที่ตรวจสอบได้.

**Architecture:** ใช้ ReviewService ของ D กับ PublishingService ของ E และ Tool row lock เดียวกัน. ชุดทดสอบใน branch E ประกอบบริการทั้งสองใน detached checkout เท่านั้น; แพตช์ยังไม่ได้ติดตั้งใน production branch D. Migration SQL เป็นร่างและ test fixture แยกจาก production migration runner.

**Tech Stack:** Java 17 target, Spring Boot 4.1.1, JPA, Spring Security session/CSRF, PostgreSQL, Maven Failsafe, PowerShell, GitHub Actions, Docker Compose.

**Spec:** [Race policy and evidence](../../../doc/review-publishing-race-report.md), [D PostgreSQL report](../../../doc/role-d-postgres-integration-report.md), [V8 report](../../../doc/role-e-v8-test-report.md), [Migration readiness](../../../doc/role-e-migration-readiness.md), [Cross-role plan](2026-10-07-team-handoff.md).

## สถานะและวิธีเริ่ม

- ส่งงานจาก branch `thaninton_673380043-6_02` เท่านั้น. เอกสารนี้ไม่ได้อนุมัติ merge develop หรือเปลี่ยนฐานข้อมูลร่วม.
- D snapshot ที่ทดสอบ: `0a92dbc3a80538917bc400d8862d8644c5dd4ec9`. E publishing implementation ไม่เปลี่ยนจาก `76cdd65`. ตรวจ remote/PR ใหม่ก่อนลงมือ; ข้อความจาก D ระบุ PR #5 แต่เอกสารนี้ไม่ยืนยันว่าสถานะปัจจุบัน approve/merge แล้ว.
- E suite เคยผ่าน 170 Surefire + 99 PostgreSQL IT = 269; D standalone overlay 131 + 28 = 159; D+E candidate overlay 131 + 28 + 6 = 165. เป็นคนละ run ห้ามบวกเป็น suite เดียว. ผล local ใช้ Java 26.0.1/PostgreSQL 18.6.
- Unmodified D baseline มี `DemoApplicationTests.contextLoads` error เมื่อไม่มี Supabase config. ผล overlay ผ่านไม่ได้แก้ test configuration ใน branch D ให้โดยอัตโนมัติ.
- Race เดิมไม่ผ่าน 6 กรณี; candidate ผ่านทั้ง 6. แพตช์พร้อม review แต่ยังไม่ได้ใช้ในโค้ดจริง. V7 tests 13 และ V8 tests 16 ผ่านใน disposable fixtures; ยังไม่รัน SQL กับฐานทีม.
- เริ่มด้วย `git status --short` และ `git fetch origin '+refs/heads/*:refs/remotes/origin/*'`. จด SHA ของ branch ที่จะตรวจ; อย่า reset งานที่มีอยู่. อ่านไฟล์ของ Role อื่นด้วย `git show <sha>:<path>` ได้โดยไม่ merge.
- Logs/reports ใต้ `code/target` ถูก ignore และไม่ส่งไป Git. ผู้รับงานต้องรันเองหรือใช้ artifacts จาก CI; รายงานใน doc ระบุผลและขอบเขตที่เคยรัน.

## Global Constraints

- User/Tool/session/CSRF ใช้ของเดิม ไม่สร้าง model/login ซ้ำ.
- กติกาที่ผู้ใช้ยืนยัน: deprecate commit ก่อน → ปฏิเสธ create/update review; review commit ก่อน → เก็บรีวิวแล้ว deprecate ต่อได้; author/admin ยังลบ hidden review ได้.
- ทั้ง review write และ publishing transition ต้องล็อก Tool แถวเดียวกันภายใน transaction จน commit/rollback. ตรวจสถานะล่าสุดหลังได้ล็อก; ห้ามเชื่อ managed entity เก่าที่ค้างใน persistence context.
- SQL V7/V8 ยังเป็นเลขร่าง. ห้าม auto-baseline, truncate ข้อมูล หรือรันกับฐานร่วมก่อนระบุ target/history/backup/ผู้รัน.
- รัน destructive integration fixtures เฉพาะฐาน disposable ที่ guard อนุญาต (`127.0.0.1`/`localhost`, explicit port, `primeskill_test_*`).
- ธีม KKU ยังเป็นงานทดลอง local; ใช้ UI guide ของทีมจนกว่าจะตกลงเปลี่ยนร่วมกัน.

## Review Focus

- สถานะใน persistence context เก่า → Task 1 ต้องปฏิเสธ create/update หลัง deprecate.
- ลำดับ commit สองแบบและ rollback → Task 1 ต้องยืนยัน lock wait จริงและไม่ทิ้ง lock หลัง rollback.
- Supabase/config หลุดเข้า default tests → Task 2 ต้องรันโดยไม่มี production credentials ได้.
- Comment เกินขนาด/constraint ชื่อเดิมแต่ definition ต่าง → Task 3 ต้องหยุดก่อนเปลี่ยนข้อมูล.
- คะแนน, pagination และหลาย tag → Task 5 ต้องเรียงคะแนนก่อนแบ่งหน้าและไม่เกิด duplicate/N+1.

## Task 1 — D ร่วม A/E: นำแพตช์ race เข้า implementation จริง (ลำดับแรก)

**ใช้ทำอะไร:** ป้องกันรีวิวถูกสร้าง/แก้หลังเครื่องมือถูก deprecate ระหว่างคำขอพร้อมกัน.

**Files:** `test/review-publishing-race/review-lock-candidate.patch`, `ReviewPublishingRacePostgresIT.java`, `README.md` ใน directory เดียวกัน; production D `code/src/main/java/com/example/toolhub/service/impl/ReviewServiceImpl.java`, `repository/ToolRepository.java`; unit test `code/src/test/java/com/example/toolhub/service/ReviewServiceImplTest.java`; E `service/impl/PublishingServiceImpl.java`.

**Interfaces:** `Optional<Tool> findForUpdateById(Long id)` ใช้ `PESSIMISTIC_WRITE`; `ReviewService.create/update` ได้ล็อกก่อน visibility/status checks แล้ว `EntityManager.refresh(tool)`. `PublishingService.transition` ใช้ล็อกเดียวกัน. Delete ไม่บังคับ PUBLISHED.

- [ ] A/D อ่านแพตช์และรายงาน; D/E เลือกเจ้าของ commit แพตช์เพียงคนเดียวเพื่อไม่แก้ซ้ำ.
- [ ] ใน branch E รัน `./scripts/check-role-d-postgres.ps1 -RoleDRef 0a92dbc3a80538917bc400d8862d8644c5dd4ec9 -IncludePublishingRace` เพื่อเห็น red 6 cases และอ่าน baseline log แยก.
- [ ] รันคำสั่งเดิมเพิ่ม `-ApplyReviewLockCandidate`; expected overlay 165 ผ่าน ไม่มี failure/error/skip. ถ้าเปลี่ยน snapshot ให้ตรวจ diff/จำนวนเทสใหม่ ไม่บังคับเลขเดิม.
- [ ] ใน isolated D checkout ตรวจ `git apply --check <absolute-path-to-patch>` ก่อน apply. ถ้ามี `findForUpdateById` จาก E แล้วให้รวม hunk นั้นด้วยมือ ห้ามเพิ่ม method ซ้ำ. Runner ปัจจุบัน skip repository hunk เพราะ compose method ไว้แล้ว.
- [ ] นำ race fixture เข้า test source ของ checkout integration ที่มี D+E จริง; ให้ PostgreSQL profile เลือก fixture. อย่า copy harness ของ D ที่ไม่มี PublishingService แล้วคาดว่าจะ compile ได้.
- [ ] เพิ่ม regression: transaction รีวิว rollback หลังถือ Tool lock → deprecate ทำต่อได้และไม่มีรีวิวค้าง; transaction deprecate rollback → review ทำต่อได้ในสถานะ PUBLISHED. ใช้ transaction แยก/barrier และ timeout, ยืนยัน DB outcome ไม่ใช้ sleep เป็นหลักฐาน lock.
- [ ] ตรวจ create/update/delete ผ่าน session/CSRF จริงด้วย `RoleDPostgresIT`; hidden author/admin delete ผ่าน, บุคคลอื่นถูกปฏิเสธ. รัน `mvn -B -f code/pom.xml -Ppostgres-it verify` กับฐาน disposable ของ integration checkout.
- [ ] บันทึก SHA ทั้ง D/E และผล suite ของ checkout ที่รวมจริง, ตรวจ diff แล้ว commit/เสนอ PR ตามขั้นตอนทีม. การ approve/merge ให้ reviewer เป็นผู้ดำเนินการ.

**Done:** review โค้ดแล้ว, candidate อยู่ใน production implementation ที่ตกลงกัน, ทั้งสอง commit orders/stale cache/rollback/permissions ผ่านบน PostgreSQL ของโค้ดที่รวมจริง.

## Task 2 — A/D: ทำ default tests ให้ไม่ต้องใช้ Supabase

**ใช้ทำอะไร:** ให้ผู้ clone และ CI รัน baseline D ได้โดยไม่พึ่ง credentials ของฐานจริง.

**Files:** D `code/src/test/java/com/example/toolhub/DemoApplicationTests.java`, test resource configuration; อ้างอิง `scripts/check-role-d-postgres.ps1` สำหรับ test-only overlay และ `code/src/test/resources/application-postgres-test.properties` ของ E.

- [ ] รัน D baseline `mvn -B -f code/pom.xml verify` โดยไม่มี `SUPABASE_DB_URL/USERNAME/PASSWORD`; เก็บ error ของ contextLoads ก่อนแก้.
- [ ] กำหนด isolated test profile/datasource ใน test resources ตามแนวทางทีม. อย่าเปลี่ยน production datasource หรือ commit secrets; อย่าเปิด open-in-view เพื่อกลบ lazy loading errors.
- [ ] รัน baseline ใหม่โดยไม่มี Supabase env; ต้องผ่าน. จากนั้นรัน PostgreSQL integration แยกกับฐาน disposable เพื่อไม่ใช้ H2 แทนหลักฐาน PostgreSQL.
- [ ] ส่ง SHA/คำสั่ง/ผลให้ A/D reviewer. Task นี้เริ่มได้พร้อม Task 1.

**Done:** default verify ผ่านโดยไม่ใช้ฐานจริงและ PostgreSQL profile ยังทำงาน.

## Task 3 — E/ผู้ดูแล DB ร่วม D/B/C: migration ไปฐานเดิม

**ใช้ทำอะไร:** จำกัด comment และจัด schema ให้ตรง model โดยไม่ทำข้อมูลเดิมหาย.

**Files:** `doc/sql/V7__align_existing_tool_catalog.sql`, V8 ต้นทาง D `doc/sql/V8__limit_review_comment_length.sql`; สำเนาเพื่อทดสอบ `code/src/test/resources/migrations/role-d/V8__limit_review_comment_length.sql`; `MigrationPreflightPostgresIT.java`, `ReviewCommentMigrationPostgresIT.java`, `doc/role-e-migration-readiness.md`.

- [ ] ระบุผู้รัน/ฐาน/schema/ช่วงเวลาและ backup/restore; ตรวจ migration history กับ schema จริงก่อนเลือก version. ตรวจว่า migration runner ครอบทั้งไฟล์ใน transaction.
- [ ] นับ comment เกินโดยไม่พิมพ์เนื้อหา: `SELECT count(*) FROM reviews WHERE comment IS NOT NULL AND char_length(comment)>2000;`. ถ้ามีให้หยุดและให้ผู้ดูแลตัดสินใจ ไม่ truncate อัตโนมัติ.
- [ ] ตรวจ definition และ validation ของ constraint ชื่อ `ck_reviews_comment_length` จาก SQL snapshot. หากชื่อเดียวกันแต่เงื่อนไขไม่ตรง <=2000 ให้ D/E เตรียม forward migration; ห้ามสรุปว่าชื่อตรงแล้วปลอดภัย.
- [ ] ตรวจ URL columns ขัดกัน, short_description >300 และ lengths อื่นตาม V7; ตกลง schema tags/version/index กับ B/C. V7 draft ที่ส่งมามี preflight และ ALTER TYPE สำหรับ short_description เดิมแล้ว.
- [ ] รัน `./scripts/test-postgres.ps1` และทดสอบ approved migration runner บน empty DB กับสำเนาข้อมูลตัวแทน: row counts/ค่าคงเดิม, constraints/FK/cascade, rerun และ restore. เทส SQL fixtures เดิมไม่แทน runner นี้.
- [ ] เมื่อ staging ผ่านและผู้ดูแลยืนยัน rollout จึงรันกับฐานเป้าหมาย; เก็บ history/schema validation/ผล count ก่อนหลัง. ไม่เปิด Flyway อัตโนมัติจากแผนนี้.

**Done:** sequence อนุมัติ, staging/restore ผ่าน, ผู้ดูแลรัน migration และยืนยัน schema/data หลังรัน.

## Task 4 — E/ผู้ดูแล CI: ยืนยัน Java 17, PostgreSQL 17 และ Docker

**Files:** `.github/workflows/build.yml`, `code/pom.xml`, `docker-compose.yml`, `Dockerfile`, `doc/role-e-local-runbook.md`.

- [ ] เปิด Actions run ของ SHA ที่ push branch E; ดูทั้ง `verify` และ `postgres-integration` และดาวน์โหลด reports. Workflow นี้ไม่รัน harness D+E ที่อยู่ใต้ test/ โดยอัตโนมัติ.
- [ ] ตรวจ Java17/PostgreSQL17 suite, Compose health และ restart persistence. หาก fail ให้แยก config/runtime จาก application regression และแนบ log reproduction ก่อนแก้.
- [ ] บันทึก run URL/SHA/จำนวน tests/failure/error/skip ใน runbook. local Java26/PG18.6 ไม่ใช่ผล CI.
- [ ] หลัง Task 1 รวมจริง ให้เพิ่ม race suite ใน CI ของ integration branch และยืนยันผลอีกครั้ง.

**Done:** workflow ของ SHA ที่ตรวจผ่านพร้อม reports; race ของโค้ดรวมจริงมี CI evidence แยก.

## Task 5 — C ร่วม D: คะแนน Browse และ sort=rating

**ใช้ทำอะไร:** ให้คะแนนบนหน้า browse เป็นคะแนนจริง และเรียงผลทั้งชุดก่อน pagination.

**Files/Interfaces:** ใช้ Task C1/C2 ใน [แผนข้าม Role](2026-10-07-team-handoff.md), `ReviewSummaryService.summarizeByToolIds(Collection<Long>)`, `ReviewSummary(Long toolId, Double avgRating, long reviewCount)`; controller/template browse และ ToolRepository ของ C.

- [ ] หลัง interface D พร้อมใน checkout C ใช้ batch summary หนึ่งครั้งต่อหน้า; empty page ไม่ query, unrated แสดง count=0/avg=null.
- [ ] sort ด้วย aggregate DB ก่อน pagination: avg DESC เต็มความละเอียด, unrated last, tool.id ASC เมื่อเสมอ; ห้าม sort เฉพาะ cards หลังแบ่งหน้า.
- [ ] เพิ่ม tests create/update/delete review แล้ว browse request ถัดไปคะแนนเปลี่ยน, query budget ไม่โตตาม cards, หลาย tag ไม่ซ้ำและ totalElements ถูก, hidden tools ไม่เผยข้อมูล.
- [ ] รัน C tests และ PostgreSQL integration ของโค้ดรวม; ส่ง SHA และผลให้ reviewer. ไม่ต้องรอรัน V8 บนฐานจริงเพื่อเริ่มเขียนเทสกับฐาน disposable.

**Done:** คะแนน/ลำดับ/pagination/query budget ผ่านตาม contract; ไม่ตกกลับ newest โดยเงียบ.

## ลำดับงานและรูปแบบส่งต่อ

1. A/D/E ตรวจ Task 1 ก่อน; A/D เริ่ม Task 2 และ E ตรวจ CI Task 4 พร้อมกันได้.
2. DB owner เริ่ม inventory/preflight ของ Task 3 ได้ แต่รันฐานร่วมหลัง sequence/staging/backup พร้อมเท่านั้น.
3. C ทำ mock/batch/query tests ได้ก่อน; integration ของคะแนนรอ D interface ใน checkout ที่ใช้งาน.
4. ทุกงานส่ง owner, branch/SHA, ไฟล์, คำสั่งและสภาพแวดล้อมทดสอบ, reports/run URL, ข้อจำกัด และ dependency ที่ยังรอ. ผู้รีวิวประเมิน PR ก่อน merge develop.
5. งานส่งเอกสารลง branch E ไม่ใช่การส่งข้อความหาเพื่อนหรือ approve PR แทนผู้ใช้.
