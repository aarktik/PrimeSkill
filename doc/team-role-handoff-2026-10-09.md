# แผนส่งต่องาน PrimeSkill แยกตาม Role A–E

วันที่จัดทำ: 9 ตุลาคม 2026

## 1. อ่านก่อนเริ่มงาน

เอกสารนี้ใช้ส่งต่อจาก Role E ให้แต่ละ role ลงมือทำและส่งผลกลับได้ โดยอ้างอิงชุดส่งมอบบน branch `thaninton_673380043-6_02` ที่ commit **`742a0fa40f35499abe452d81b2756eec198cddac`** ไม่ใช่การยืนยันว่า branch ของทุกคนหรือ develop มีงานนี้แล้ว

- Source ของชุดทดสอบรวมอยู่ที่ `b775771`; commit `742a0fa` เพิ่มเอกสารส่งต่อ
- โค้ด dependency C/D ที่นำมารวมมาจาก `3187098fac9d91c14d1fdfe40bb765797cde8cfd`
- ผล local: Java 17.0.20.1, PostgreSQL 18.6; Surefire 265 + Failsafe 185 = 450 tests ไม่มี failure/error/skip และ Python tests 12 ผ่าน
- ซ้อม backup/migration/restore/remigrate และ app schema validation ผ่านกับ fixture local ไม่ใช่ข้อมูลฐานทีม
- ผลข้างต้นเป็นหลักฐานของ SHA ที่ระบุ ไม่ใช่ reviewer approval หรือผลทดสอบ branch ที่เพื่อนแก้เพิ่ม
- CI ของ742a0faผ่านแล้วทั้งสอง workflows อ่าน [ขอบเขตหลักฐาน CI](role-e-ci-evidence-2026-10-09.md); E เตรียม [ชุดตรวจรับ B/C](b1-bc-integration-acceptance.md) และ [ข้อมูล rollout](role-e-rollout-preparation.md) ไว้แล้ว
- งาน UI ทดลองเดิมไม่ได้รวมอยู่ใน commit ส่งมอบนี้

**ประเด็นสำคัญ:** E ทำ revision และ publishing/version guards แล้ว แต่ metadata และ tag paths ยังต้องให้ B/C เพิ่ม guards จึงจะถือว่า B1 ป้องกันการเปลี่ยนเนื้อหาระหว่างรออนุมัติได้ครบ

การจัดเจ้าของงานด้านล่างเป็นแผนส่งต่อ ไม่ได้หมายความว่าเพื่อนแต่ละคนรับงานหรืออนุมัติแล้ว ต้องยืนยัน SHA ของงานที่ส่งกลับก่อนรวมจริง

## 2. ลำดับทำงานและสิ่งที่ต้องรอ

1. **เริ่มพร้อมกันได้:** A/D review ชุด E, B ทำ metadata guards, C ทำ tag guards และคะแนน browse, E/DB owner เตรียมข้อมูล rollout
2. **ก่อนแก้ส่วนที่ใช้ร่วมกัน:** B/C/E ตกลง shared Tool lock, transaction, error contract และ lock order ของ Tag ให้ตรงกัน
3. **เมื่อ B/C ส่งงาน:** E รวมบน branch สำหรับตรวจงาน แล้วรัน acceptance และ PostgreSQL races จาก source เดียวกัน A/D ตรวจ diff ชุดรวมอีกครั้ง
4. **ก่อนเข้า develop:** ต้องมี review ของ commit ที่จะรวมจริง ผล CI ของ SHA นั้น และสถานะงานค้างที่ชัดเจน ผู้รับผิดชอบ PR จึงดำเนินการตามกระบวนการทีม
5. **ก่อนรันฐานทีม:** DB owner ยืนยัน target/history/runner/version/backup/restore/window แล้วจึงอนุมัติการรัน migration และ deploy ที่เข้ากันได้

ไม่จำเป็นต้องรอ review เสร็จเพื่อเริ่มเขียนโค้ดบน branch ของตนเอง แต่ถ้า review เปลี่ยน shared contract ต้องปรับงานตามข้อสรุปก่อนรวม การรวม develop และการรันฐานทีมเป็นคนละขั้นตอน

## 3. Role A — Security และตรวจรับ contract

### จุดประสงค์

ตรวจให้แน่ใจว่าผู้ใช้ไม่มีสิทธิ์สั่งอนุมัติแทน admin และคำขอเก่าหรือ input ผิดรูปแบบไม่ทำให้ข้อมูลเปลี่ยน

### เริ่มตรวจจาก

- `code/src/main/java/com/example/toolhub/controller/api/PublishingRestController.java`
- `code/src/main/java/com/example/toolhub/controller/web/RoleEWebController.java`
- `code/src/main/java/com/example/toolhub/dto/request/ReviewDecisionRequest.java`
- `code/src/main/java/com/example/toolhub/dto/request/StrictReviewRevisionDeserializer.java`
- `code/src/main/resources/templates/admin/moderation.html`
- `code/src/test/java/com/example/toolhub/ReviewDecisionIntegrationTest.java`

### งานที่ต้องทำ

- [ ] ตรวจ actor มาจาก trusted session/security context และไม่รับ actor/admin identity จาก request body มาแทน
- [ ] ตรวจ anonymous/member/admin, session และ CSRF ของ API/web ตาม security contract ของระบบ
- [ ] approve/reject ต้องใช้ `expectedReviewRevision` ของข้อมูลที่ admin ตรวจจริง ไม่มี fallback อนุมัติด้วย id อย่างเดียว
- [ ] missing/null/negative/string/fraction/boolean/overflow ถูกปฏิเสธ 400; integer ที่ถูกต้องแต่เก่าได้ 409 `STALE_REVIEW_REVISION`
- [ ] current revision แต่สถานะผิดได้ 409 `INVALID_STATE_TRANSITION`; คำขอถูกปฏิเสธไม่เปลี่ยนสถานะหรือ revision
- [ ] ตรวจ form ที่เปิดค้างแล้วมี reject/resubmit ไม่สามารถอนุมัติรอบใหม่ด้วยข้อมูลรอบเก่า และไม่ retry ด้วย revision ใหม่อัตโนมัติ
- [ ] หลัง B/C ส่งงาน ตรวจ authorization/server guards ของ metadata/tag เพิ่ม พร้อมประเมิน diff ที่รวมจริง

### สิ่งที่ส่งกลับ / เกณฑ์เสร็จ

ส่ง reviewed SHA, ประเด็นที่พบพร้อมไฟล์/บรรทัด, ผล security tests และข้อสรุป approve/request changes บน PR หรือช่องทางทีมที่ตกลง ต้องระบุชัดว่าตรวจเฉพาะ E หรือชุดรวม B/C/E

**ต้องรอ:** ตรวจ E เริ่มได้ทันที; ตรวจรับ B1 ทั้งระบบต้องรอ B/C และ source ชุดรวม

## 4. Role B — Metadata update และฟอร์มแก้ Tool

### จุดประสงค์

ป้องกันแก้เนื้อหาของ Tool ขณะ PENDING/PUBLISHED/DEPRECATED เพื่อให้ admin อนุมัติเนื้อหาตรงกับรอบที่ส่งตรวจ

### เริ่มทำจาก

- `code/src/main/java/com/example/toolhub/service/impl/ToolServiceImpl.java` เมธอด `update`
- `code/src/main/java/com/example/toolhub/controller/web/ToolWebController.java` และฟอร์ม/dashboard ที่เรียก update
- `code/src/test/java/com/example/toolhub/service/ToolServiceImplTest.java`
- ตัวอย่าง PostgreSQL concurrency: `code/src/test/java/com/example/toolhub/ToolApprovalConcurrencyPostgresIT.java`

### งานที่ต้องทำ

- [ ] ใน transaction เดียว: shared Tool row lock → refresh entity → ตรวจ owner/admin → ตรวจ DRAFT → validate unique/FK → แก้ข้อมูล
- [ ] ครอบคลุม `name`, `slug`, `shortDescription`, `description`, `categoryId`, `repositoryUrl`
- [ ] ทดสอบ matrix 12 กรณี: 4 สถานะ × owner/admin/other user; DRAFT owner/admin แก้ได้, อีก 3 สถานะ owner/admin ได้ 409 `INVALID_STATE_TRANSITION`, other user ได้ 403 ทุกสถานะ
- [ ] non-DRAFT แม้ส่งค่าเดิมแบบ no-op ก็ต้องถูกปฏิเสธ; denied request ไม่เปลี่ยน metadata/status/revision/viewCount
- [ ] เปลี่ยน baseline tests ให้ยืนยัน policy ใหม่ และตรวจค่าที่บันทึกจริง ไม่ทดสอบเพียง mock response
- [ ] เพิ่ม PostgreSQL tests แข่ง update กับ SUBMIT/approval ในทั้งสอง commit orders รวม stale managed entity และ rollback
- [ ] UI เปิดแก้ไขตาม policy และแจ้ง conflict เมื่อเปิดฟอร์มก่อน SUBMIT; server ต้องบังคับ guard แม้ยิง request ตรง

### สิ่งที่ส่งกลับ / เกณฑ์เสร็จ

ส่ง branch/SHA, diff, matrix ผล 12 กรณี, PostgreSQL race/rollback results และรายชื่อฟอร์ม/API ที่เปลี่ยน ถือว่าเสร็จเมื่อ E รวมแล้วรัน tests กับ revision contract เดียวกัน และ reviewer รับรอง

**ต้องรอ:** เริ่ม implement ได้จาก contract ปัจจุบัน; รวมจริงต้องตกลง contract/lock กับ E และผ่าน review ไม่ต้องรอ migration ฐานทีมเพื่อทดสอบ disposable DB

## 5. Role C — Tag guards และคะแนน browse

### งาน C1: Tag guards สำหรับ B1

จุดประสงค์คือป้องกันเปลี่ยนแท็กของ Tool ระหว่างรออนุมัติ และป้องกันลบ Tag แข่งกับการผูก Tag

เริ่มที่ `code/src/main/java/com/example/toolhub/service/impl/TagServiceImpl.java` เมธอด assign/unassign และ deleteTag ที่เกี่ยวข้อง

- [ ] assign/unassign ใช้ shared Tool lock + refresh และตรวจ owner/admin/DRAFT ใน transaction เดียว
- [ ] ตรวจ permission/status matrix ของทั้ง assign และ unassign; denied request ไม่เปลี่ยน association/status/revision
- [ ] ตกลง lock order ของ Tool/Tag กับ E ก่อนเพิ่ม Tag lock ให้ทุก path ที่แข่งกันใช้กติกาเดียวกัน
- [ ] serialize assign/deleteTag เพื่อคง in-use 409 และไม่ให้ check/delete race ลบ association ใหม่เงียบ ๆ
- [ ] เพิ่ม persisted tests และ PostgreSQL tag mutation/SUBMIT races ทั้งสอง orders, stale entity, rollback และ assign/deleteTag race
- [ ] ปรับหน้าจอเลือกแท็กให้สอดคล้องกับ server guard

อย่าขยาย policy ไปห้ามแก้ชื่อ Tag ส่วนกลางโดยอัตโนมัติ เพราะเป็นอีกขอบเขตหนึ่งที่ต้องตกลงแยก

### งาน C2: คะแนน browse และ `sort=rating` ร่วม D

งานนี้แยกจาก B1 และไม่ได้ implement แทน C ในชุด E

- [ ] ตรวจ D review summary contract กับ source ที่จะใช้จริง แล้วดึงคะแนนแบบ batch; หน้าเปล่าไม่ query และ unrated ใช้ `avg=null`, `count=0`
- [ ] sort ด้วยคะแนนจริงใน DB **ก่อน pagination**; unrated อยู่ท้าย และมีลำดับรองแน่นอนเพื่อไม่ให้ผลสลับข้ามหน้า
- [ ] create/update/delete review แล้ว request ถัดไปแสดงคะแนนใหม่
- [ ] ตรวจไม่มี N+1/duplicate tools เมื่อกรองหลาย tags และไม่เปิดเผย hidden tools
- [ ] หากต้องเพิ่ม index/schema ให้แจ้ง E/DB owner พร้อมเหตุผลและ SQL ที่เสนอ

### สิ่งที่ส่งกลับ / เกณฑ์เสร็จ

แยกผล C1/C2 ให้ชัด ส่ง SHA, contract/query ที่ใช้, persisted/PG tests และ migration/index ที่ต้องการ C1 ต้องผ่านการรวมทดสอบ B1; C2 ต้องมีผล sort/pagination/summary ที่ใช้ DB จริง

**ต้องรอ:** C1 เริ่มได้หลังตกลง lock order; C2 พัฒนาและทดสอบ branch ได้กับ contract D แต่ต้องตรวจ SHA/dependency ที่รวมจริงก่อนเข้า develop ไม่ต้องรอรัน V8 กับฐานทีม

## 6. Role D — Review contract และตรวจ review/publishing compatibility

### สิ่งที่มีแล้วใน source ส่งมอบ

โค้ด review/rating และการ lock Tool + refresh จาก dependency D รวมอยู่ในชุดนี้แล้ว พร้อม PostgreSQL review/deprecate และ rollback tests จึงไม่ต้องเขียนหรือ apply patch เดิมซ้ำเพียงเพราะได้รับเอกสารนี้

### งานที่ต้องทำต่อ

- [ ] ตรวจ diff ของชุด E เทียบกับ D ล่าสุด ยืนยันไม่มีการทำลาย review API/summary/error contract
- [ ] ตรวจ shared lock/refresh/transaction ระหว่าง review, publishing และ version writes รวม error 503 เฉพาะ lock failure
- [ ] ยืนยันกติกาที่ผู้ใช้ตกลง: deprecate commit ก่อน → reject create/update review; review commit ก่อน → เก็บ review แล้ว deprecate ต่อได้; author/admin ยังลบตามสิทธิ์
- [ ] ตรวจ PostgreSQL tests ทั้งสอง orders, stale managed entity, rollback และไม่ปล่อย event จาก transaction ที่ rollback
- [ ] ให้ C อ้างอิง review summary contract/ตัวอย่าง batch ที่ตรง SHA และช่วยตรวจคะแนน browse/`sort=rating`
- [ ] ตรวจ PR ของ D และ head ปัจจุบันก่อนดำเนินการต่อ หากต้อง rebase/แก้ conflict ให้ส่ง SHA ใหม่ ไม่ใช้ผล tests ของ head เก่ารับรอง
- [ ] หลังรวม B/C/E ตรวจ regression ของ review/deprecate อีกครั้ง

### สิ่งที่ส่งกลับ / เกณฑ์เสร็จ

ส่ง reviewed SHA, contract ที่ C ต้องใช้, regression results และข้อสรุป review ความรับผิดชอบเปิด PR ของ D กับการ approve/merge ต้องเป็นไปตามทีม ไม่ถือว่า E ได้ merge PR ให้แล้ว

**ต้องรอ:** review ชุด E และช่วย C เริ่มได้ทันที; การรับรองชุดรวมต้องรอ source รวมและผลทดสอบใหม่

## 7. Role E — รวมงาน ทดสอบ และประสานฐานข้อมูล

### ทำแล้ว

- revision เพิ่มทุก SUBMIT และ approve/reject ต้องส่ง revision ที่ตรวจจริง
- publishing/version ใช้ Tool lock + refresh พร้อม stale/input/timeout/rollback tests
- Java17 wrapper/Enforcer, CI coverage gate และเอกสารผลทดสอบ
- inventory SQL, migration draft/manifest/runbook/checklist และ local backup/restore rehearsal
- จัด dependency และ push ชุดส่งมอบเฉพาะ personal branch แล้ว

### งานต่อไป

- [ ] รับข้อแก้จาก A/D แล้วแก้เฉพาะประเด็นที่ตรวจยืนยัน พร้อมบันทึก SHA ใหม่
- [ ] รับ SHA ของ B/C และตรวจ contract/lock order/dependency ก่อนรวมบน branch สำหรับตรวจงาน
- [ ] รัน tests ชุดรวมใหม่ รวม metadata/tag acceptance และ PostgreSQL races; จำนวน tests ต้องสะท้อน suites ใหม่ ไม่ยึด 450 เป็นเพดาน
- [ ] ตรวจ GitHub CI ของ source SHA ใหม่ แยกจาก workflow baseline/overlay เดิม
- [ ] ส่งชุดรวมให้ A/D ตรวจ แล้วเตรียม PR/การรวม develop เมื่อได้รับมอบหมาย ไม่ถือว่าการ push personal branch เป็นการ merge develop
- [ ] ร่วม DB owner เติม rollout checklist และซ้อม approved runner ด้วย staging/ข้อมูลตัวแทนที่ได้รับอนุญาต
- [ ] บันทึก release manifest/checksums และผล migration หลังได้รับอนุมัติให้รันจริง

**ต้องรอ:** full B1 รอ B/C และ reviewer; shared DB rollout รอ DB owner ส่วนแก้ review feedback/เตรียม runner/docs ทำต่อได้ทันที

## 8. DB owner / ผู้ปฏิบัติการฐานข้อมูล — อาจเป็น E ตามที่ทีมมอบหมาย

เริ่มจาก [runbook](migration-rollout-runbook.md), [checklist](database-rollout-checklist.md), [release manifest](sql/migration-release-manifest.json)

- [ ] ระบุ target, schema, PostgreSQL version, migration runner, history/baseline และ checksum ปัจจุบัน
- [ ] ยืนยันเลขและลำดับ migration จริง; V7/V8 และ B1 draft ใน repo ไม่ใช่เลขที่อนุมัติให้รันกับทุกฐานโดยอัตโนมัติ
- [ ] ตรวจ oversized comments, URL conflicts, missing backfill source, column lengths, constraint definitions และ index ที่ C เสนอ
- [ ] ตรวจ V8 constraint ที่ใช้ชื่อเดิมว่า definition ตรงจริง ไม่อาศัยแค่ตรวจว่ามีชื่ออยู่แล้ว
- [ ] ระบุ operator/reviewer, backup/retention, staging restore, write pause/window และผู้ตัดสินใจกู้คืน
- [ ] V7/V8 ต้อง transaction ทั้งไฟล์; B1 draft มี BEGIN/COMMIT ของตัวเอง ตรวจวิธี runner ให้ตรง runbook
- [ ] วางแผนหยุด old writers และ deploy schema/API ที่เข้ากันได้; id-only approve client เก่าจะถูกปฏิเสธหลังเปลี่ยน contract
- [ ] เมื่อได้รับอนุมัติแล้วจึงรันจริง เก็บ history/schema/counts/checksums ก่อน–หลัง และผล app validation

**ต้องรอ:** ข้อมูล target/runner/history และ approval ของผู้รับผิดชอบก่อนฐานทีม; local fixture rehearsal ผ่านแล้วแต่ไม่แทน staging restore ด้วยข้อมูลจริง

## 9. วิธีตรวจงานและส่งผลกลับ

ใช้ JDK17 ตาม [คู่มือ](java17-development.md) และ disposable DB ตาม runner guards รันจาก repository root:

```powershell
git rev-parse HEAD
./scripts/mvn-java17.ps1 -version
./scripts/test-postgres.ps1
python scripts/test-review-ci-reports.py -v
python scripts/test-role-e-b1-reports.py -v
python scripts/check-role-e-b1-reports.py code/target --output code/target/b1-evidence/summary.json
```

คำสั่ง gate ปัจจุบันตรวจ suites E/D ที่กำหนดไว้ ไม่ได้พิสูจน์ว่า B/C acceptance ครบโดยอัตโนมัติ B/C ต้องแนบผล suites ใหม่และ E ต้องตรวจ coverage เพิ่มเมื่อรวมงาน

ใช้แบบฟอร์มนี้ส่งกลับใน PR/ไฟล์หรือช่องทางทีมที่ตกลง โดยไม่ใส่ secret/connection password:

```text
Role / ผู้รับผิดชอบ:
งาน: A review / B metadata / C1 tags / C2 rating / D review / E integration / DB rollout
Branch และ full commit SHA:
สิ่งที่เปลี่ยน / contract ที่เปลี่ยน:
ไฟล์หลัก:
Java / PostgreSQL version:
คำสั่งที่รัน:
Surefire: Tests / Failures / Errors / Skipped
Failsafe: Tests / Failures / Errors / Skipped
Suites ใหม่และกรณี race ที่ตรวจ:
CI run / report / artifact:
สิ่งที่ยังไม่ทดสอบ / dependency ที่รอ:
ต้องการให้ใครตรวจอะไร:
ข้อสรุป review และ reviewed SHA (ถ้ามี):
```

## 10. เงื่อนไขก่อนประกาศ B1 เสร็จ

- [ ] E revision/decision guards + B metadata guards + C tag guards อยู่ใน source ชุดเดียวกัน
- [ ] persisted permission/status matrix และ PostgreSQL races ของทุก mutation path ผ่าน
- [ ] A/D review commit ที่จะรวมจริง และแก้ข้อทักท้วงแล้ว
- [ ] GitHub CI ของ SHA นั้นผ่าน ไม่มีการใช้ผล baseline เก่ามารับรองงานใหม่
- [ ] บันทึกงานค้าง/ข้อจำกัดและ migration dependency ชัดเจน

การเสร็จของโค้ด B1 กับการ rollout ฐานทีมต้องรายงานแยกกัน เพราะมีเงื่อนไขอนุมัติและหลักฐานคนละชุด

## เอกสารประกอบ

- [ภาพรวม commit และผลส่งมอบ E](role-e-delivery-handoff.md)
- [B1 API/service contract](b1-role-e-handoff.md)
- [B1 design](../docs/superpowers/specs/2026-10-08-b1-edit-approval-design.md)
- [B1 implementation plan](../docs/superpowers/plans/2026-10-08-b1-edit-approval-implementation.md)
- [B1 test evidence](b1-implementation-test-report.md)
- [Java17 test evidence](java17-test-report.md)
- [Migration test evidence](migration-rollout-test-report.md)

เอกสารนี้จัดไว้สำหรับให้ผู้ใช้ส่งต่อ ไม่ได้ส่งข้อความหา role อื่นแทนผู้ใช้ และไม่ได้ตรวจ fetch branch ทุกคนใหม่ในรอบเขียนเอกสารนี้
