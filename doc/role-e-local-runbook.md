# Role E local runbook

## ส่วนต่าง ๆ ใช้ทำอะไร

- **Version CRUD:** ให้เจ้าของเพิ่ม/แก้/ลบรุ่นของเครื่องมือ พร้อม release notes ได้เฉพาะตอนเป็น DRAFT ป้องกันการเปลี่ยนข้อมูลระหว่างตรวจหรือหลังเผยแพร่
- **Publishing และ State pattern:** คุมลำดับ DRAFT → PENDING → PUBLISHED → DEPRECATED รวมถึง reject/restore เพื่อไม่ให้ข้ามขั้นตอนอนุมัติ
- **Session และสิทธิ์:** ใช้ผู้ใช้จากการ login จริง ตรวจเจ้าของเครื่องมือและ ADMIN ก่อนเปลี่ยนข้อมูล ไม่รับสิทธิ์ที่ผู้เรียกส่งมาเอง
- **CSRF:** คำขอ POST/PUT/DELETE ต้องส่ง token คู่กับ session เพื่อป้องกันเว็บอื่นสั่งเปลี่ยนข้อมูลแทนผู้ใช้ ฟอร์มของ Role E มีช่อง token แล้ว
- **หน้าสาธารณะ:** แสดงเวอร์ชันของเครื่องมือที่เผยแพร่แล้ว และโหลด `/css/role-e.css` ได้โดยไม่ต้อง login ส่วน draft ยังถูกซ่อนจากบุคคลทั่วไป
- **Health endpoint:** `GET /actuator/health` ใช้ตรวจว่าแอปและฐานข้อมูลพร้อมตอบสนอง โดยไม่เปิด endpoint จัดการอื่นเพิ่ม
- **Docker Compose:** เตรียมแอปและ PostgreSQL สำหรับเครื่อง local พร้อม volume เก็บข้อมูลข้ามการ restart ยังต้องรันทดสอบบนเครื่องที่มี Docker
- **CI:** รัน Maven verify แล้วจึง build Docker image เพื่อจับปัญหาโค้ดและการสร้าง container โดยยังไม่มีขั้นตอน push image หรือ deploy

## ทดสอบได้ทันทีโดยไม่ต้องมี Docker

ผลตรวจวันที่ 25 กันยายน 2026: `mvn -B -f code/pom.xml verify` ผ่าน 88 tests, 0 failures, 0 errors, 0 skipped และสร้าง executable JAR สำเร็จ ใช้ Java 26.0.1 ในเครื่อง โดย project กำหนด Java target 17; CI และ Docker กำหนด Java 17 แต่ยังไม่ได้รันในรอบนี้ รายงานละเอียดอยู่ที่ `code/target/surefire-reports/` และ log รอบล่าสุดอยู่ที่ `code/target/role-e-verify.log` (เป็นไฟล์ผล build ที่ Git ไม่ติดตาม)

รันจาก `D:\PrimeSkill`:

```powershell
mvn -B -f code/pom.xml verify
```

หากต้องการเฉพาะ flow รวมของ Role E:

```powershell
mvn -B -f code/pom.xml -Dtest=RoleEFlowIntegrationTest test
```

`RoleEFlowIntegrationTest` ใช้ controllers, security filters, services และ repositories จริง ผ่าน MockMvc สมัครและ login ผ่าน API พร้อมดึง CSRF token จาก endpoint จริง ไม่ mock ผู้ใช้หรือ service ข้อมูลอยู่ใน H2 แยกเฉพาะคลาสทดสอบ และแต่ละคำขอใช้ transaction ของ service จริง

ครอบคลุมการสร้าง/แก้/ลบ version, submit → reject → submit → approve, การดูข้อมูลสาธารณะ, deprecate → restore, สิทธิ์เจ้าของ/ADMIN, CSRF ที่ขาดหรือผิด, version ซ้ำ, versionId ผิด tool และ logout บัญชี ADMIN ถูกเตรียมเฉพาะในฐานข้อมูลทดสอบ; API สมัครสมาชิกยังสร้าง USER เสมอ

การทดสอบนี้ไม่ยืนยัน PostgreSQL locking/concurrency, การเก็บ cookie ของ browser, container restart หรือการ deploy จริง ต้องตรวจแยกบนสภาพแวดล้อมนั้น

## ใช้ API จาก client

1. เรียก `GET /api/v1/auth/csrf` และเก็บ session cookie พร้อมค่า `headerName`/`token` จาก response
2. สมัครและ login ด้วย JSON โดยส่ง cookie และ CSRF header คู่กัน
3. หลัง login เรียก CSRF endpoint อีกครั้ง ใช้ session ปัจจุบันกับการสร้าง version และ action เผยแพร่
4. POST/PUT/DELETE ที่ไม่มี token จะตอบ 403; USER เรียก action อนุมัติไม่ได้
5. GET version ของ PUBLISHED ทำได้โดยไม่ login; draft ของผู้อื่นตอบ 404

## Run locally

1. Copy `.env.example` to `.env` and change `POSTGRES_PASSWORD` to a local-only value.
2. Run `docker compose up --build` from the repository root.
3. Check `http://localhost:8080/actuator/health` after startup. This endpoint is allowed without authentication for health probes.
4. Run `mvn -B -f code/pom.xml verify` for the automated test suite. The context smoke test uses an isolated in-memory database and never reads Supabase credentials.

The Compose database is only for local development. The current application still initializes its schema through `schema.sql`; it does not yet have an approved Flyway baseline. Do not point this configuration at a shared or production database.

เมื่อมี Docker ให้สร้างข้อมูลตัวอย่างในฐานข้อมูล local แล้วรัน `docker compose stop` ตามด้วย `docker compose start` ตรวจ health และอ่านข้อมูลเดิมซ้ำเพื่อยืนยัน volume เก็บข้อมูล ห้ามใช้ `down -v` ในขั้นตอนนี้เพราะจะลบ volume ที่ต้องการทดสอบ

## Publishing and versioning contract

- Owners submit drafts and restore deprecated tools to draft. Admins approve or reject pending tools. Owners or admins deprecate published tools. Invalid transitions return `409 INVALID_STATE_TRANSITION`.
- Admins can read the pending queue at `GET /api/v1/admin/tools/pending`.
- Version list and CRUD live under `/api/v1/tools/{toolId}/versions`. Versions of unpublished tools are visible only to the owner or an admin. Only the owner can add, change or delete versions while the tool is a draft.
- The current database table stores `version` (100 characters), `release_notes` and optional `released_at`. The implementation follows that existing schema. The proposed manifest URL/type fields and `version_number` rename in `IMPLEMENTATION_PLAN.md` need a team-approved migration before code or production data is changed.

## Production checklist (pending team decisions)

- Agree on the hosting provider, owner of secrets, database URL and deployment permissions.
- Replace `schema.sql` startup initialization with approved forward-only Flyway migrations, tested on a copy of the existing schema. Keep `ddl-auto=validate`.
- Back up the database before migration; test the backup restore and smoke flow on staging.
- Keep only `/actuator/health` public for host probes and set `SESSION_COOKIE_SECURE=true` when serving the app over HTTPS behind the production proxy. Local Compose defaults this value to `false` for localhost.
- Deploy the immutable image, wait for health, then smoke test register, create draft, submit, approve and public browse. Roll back the image if the application fails; database rollback requires a separately reviewed recovery plan.

## ชุดทดสอบหาข้อผิดพลาดเพิ่มเติม (25 กันยายน 2026)

เพิ่มกรณีใน `RoleEFlowIntegrationTest` โดยใช้ session, CSRF, security filters, controller, service และฐานข้อมูล H2 จริงของเทส ไม่ mock ผลตอบกลับของ service และไม่ครอบทั้งเทสด้วย transaction เพื่อให้ตรวจข้อมูลที่ commit แล้วได้

สิ่งที่ตรวจเพิ่มเติม:

- ทุกคู่สถานะกับ action 20 กรณี พร้อมตรวจสถานะในฐานข้อมูลหลังคำขอสำเร็จ/ถูกปฏิเสธ
- การมองเห็นของ anonymous, owner, ผู้ใช้อื่น และ admin ในทั้ง 4 สถานะ; ตรวจสิทธิ์สร้าง/แก้/ลบเวอร์ชัน
- payload ไม่ถูกต้อง 12 แบบ ทดสอบทั้ง create/update: ค่าว่าง, null, whitespace, JSON เสีย, ชนิดข้อมูลผิด, ความยาวเกินกำหนด พร้อมยืนยันข้อมูลเดิมไม่เปลี่ยน
- ขอบเขต version 100 ตัวอักษร/release notes 2000 ตัวอักษร, trim, เวอร์ชันซ้ำ, rollback, ลำดับรายการ และเวอร์ชันชื่อเดียวกันคนละเครื่องมือ
- การอ้าง versionId ข้ามเครื่องมือ, ID ไม่มีอยู่/ผิดรูปแบบ, คิวอนุมัติและ pagination, cascade delete
- ฟอร์มเว็บจริง: validation, create/edit, submit/approve, escaping ของข้อความ script และป้องกันลบเวอร์ชันที่เผยแพร่แล้ว
- CSRF ต่าง session, anonymous writes, CSRF ของทุก publishing action ทั้ง API/เว็บ
- คำขอพร้อมกัน: สร้างเวอร์ชันซ้ำ, submit ซ้ำ, edit แข่งกับ submit; มี barrier และ timeout พร้อมปิด executor ทุกครั้ง
- HTTP method และ media type ที่ไม่รองรับ และ sort field ที่ไม่มีอยู่

รันชุดนี้: `mvn -B -f code/pom.xml -Dtest=RoleEFlowIntegrationTest test`
รันทั้งโครงการ: `mvn -B -f code/pom.xml verify`

### ประวัติข้อผิดพลาดที่เทสตรวจพบ (ผลวันที่ 25 กันยายน 2026)

1. `unknownModerationSortFieldIsAClientError`: admin เรียก `GET /api/v1/admin/tools/pending?sort=doesNotExist,asc` ได้ 500 แทน 400. Pageable รับ sort ที่ไม่มีใน entity แล้วเกิด PropertyReferenceException ซึ่งถูก generic exception handler แปลงเป็น INTERNAL_ERROR. ควรตรวจ/จำกัดฟิลด์ sort ที่ endpoint ก่อนส่งเข้า repository.
2. `unsupportedRequestMediaTypeReturns415WithoutWritingData`: owner ที่มี session/CSRF ถูกต้อง ส่ง `POST /api/v1/tools/{id}/versions` ด้วย `Content-Type: text/plain` ได้ 500 แทน 415. GlobalExceptionHandler จับ HttpMediaTypeNotSupportedException ด้วย handler ของ Exception ทั่วไป. ควรจัดการ HTTP exception ให้คงสถานะมาตรฐานที่ถูกต้อง.

เทสที่พบปัญหายังคง assertion ตามพฤติกรรมที่ถูกต้อง ไม่มี @Disabled และไม่ได้เปลี่ยนให้คาดหวัง 500 เพื่อให้ผ่าน เมื่อแก้แอปแล้วใช้เทสเดิมยืนยัน regression ได้ ผลไม่ผ่านในหัวข้อนี้เป็นประวัติก่อนแก้ไข ดูสถานะล่าสุดในหัวข้อวันที่ 27 กันยายน 2026 ด้านล่าง

ข้อจำกัด: การแข่งกันของคำขอทดสอบกับ H2 เท่านั้น และ barrier ไม่รับประกันทุก interleaving; ยังต้องยืนยัน locking/concurrency บน PostgreSQL จริง ไม่ใช่การรับรองว่าไม่มีบั๊กเหลือทั้งหมด

ผลรันล่าสุดของชุดเพิ่มเติม: 142 tests ทั้งโครงการ, ผ่าน 140, failures 2, errors 0, skipped 0; เฉพาะ RoleEFlowIntegrationTest มี 57 กรณี (เพิ่มจากเดิม 3 เป็น 57). Log: code/target/role-e-adversarial-final.log. Maven verify จบ BUILD FAILURE จากสองบั๊กข้างต้น จึงไม่ได้ยืนยันการ package ในรอบนี้.

## งานต่อเนื่องวันที่ 27 กันยายน 2026

### แก้ HTTP errors และคิวอนุมัติ

- คิวอนุมัติเป็นรายการให้ admin เลือกตรวจเครื่องมือที่อยู่ใน PENDING. รองรับ sort เฉพาะ `id`, `name`, `createdAt`, `updatedAt` ทั้ง asc/desc; ฟิลด์อื่นรวมถึง nested properties ตอบ `400 MALFORMED_REQUEST` ก่อนเรียก repository. ใช้ exception สำหรับพารามิเตอร์ผิดโดยเฉพาะ เพื่อไม่ซ่อน programming errors เป็น 400.
- เพิ่ม id เป็นลำดับรองเมื่อผู้เรียกไม่ได้ระบุ เพื่อให้รายการชื่อ/เวลาเท่ากันมีลำดับแน่นอนเวลาแบ่งหน้า. ไม่ใช่ snapshot pagination: ถ้ามีรายการเพิ่ม/ลบระหว่างเปิดหน้า ผลยังเปลี่ยนได้.
- Content-Type บอกชนิดข้อมูลที่ client ส่ง. ชนิดที่ API อ่านไม่ได้ตอบ `415 UNSUPPORTED_MEDIA_TYPE` พร้อม Accept header ระบุชนิดที่รองรับ แทน 500.
- Accept บอกชนิดผลลัพธ์ที่ client รับได้. ถ้าไม่มีชนิดที่ตรงกันตอบ 406 แบบไม่มี body เพื่อไม่พยายามส่ง JSON ที่ client ปฏิเสธซ้ำอีก.
- Regression tests ตรวจทั้ง sort ผิด, sort ที่อนุญาต, หลาย sort ที่มีค่าผิดปน, การแบ่งหน้าเมื่อชื่อเท่ากัน, error code และ header. เก็บเทสเดิมที่พบปัญหาไว้.

### งานลำดับต่อไปและประโยชน์

1. PostgreSQL และ Docker: ยืนยัน schema, unique constraints, transaction/locking และ volume persistence บนระบบจริง โดยเฉพาะ create เวอร์ชันซ้ำและ edit แข่งกับ submit. เครื่องนี้ยังเรียก docker ไม่ได้ จึงยังไม่ถือว่าผ่าน runtime verification.
2. นโยบายแก้รายละเอียดเครื่องมือ: ToolServiceImpl.update ปัจจุบันตรวจ owner/admin แต่ไม่จำกัดสถานะ ขณะที่การแก้เวอร์ชันจำกัด DRAFT. ต้องตกลงร่วมกับเจ้าของ catalog ว่าการแก้ข้อมูลระหว่าง PENDING/PUBLISHED ต้องถูกปฏิเสธหรือกลับไป DRAFT เพื่อให้การอนุมัติตรงกับข้อมูลที่ตรวจ. ยังไม่เปลี่ยนนโยบายของโมดูลอื่นโดยเดา requirement.
3. Session และเบราว์เซอร์: ตรวจ login/logout, cookie หลังหมดอายุ, การเปลี่ยนสิทธิ์/ปิดบัญชี และ CSRF ผ่านฟอร์มจริง เพื่อยืนยันพฤติกรรมที่ MockMvc ไม่ครอบคลุม.
4. CI/ส่งงาน: หลังตรวจ diff ให้ commit การแก้ไขและ push ไป branch ส่วนตัว แล้วตรวจ workflow ที่รันจริงก่อนรวม develop. อย่าใช้ผล H2 อ้างว่า Docker/PostgreSQL ผ่านแล้ว.

ผลตรวจล่าสุด 27 กันยายน 2026: mvn -B -f code/pom.xml verify ผ่าน 150 tests (failures 0, errors 0, skipped 0) และสร้าง executable JAR สำเร็จ. เพิ่ม 8 กรณีจากเดิม 142; บั๊ก 400/415 เดิมผ่านแล้ว. ใช้ H2 กับ Java 26.0.1; ไม่ใช่ผลตรวจ Docker/PostgreSQL หรือ CI Java 17. Log: code/target/role-e-http-fixes.log.

## Sprint 3 browser review demo

Start from the repository root in PowerShell: `./scripts/preview-sprint3.ps1`. This compiles test-only preview fixtures and starts the app at `http://127.0.0.1:18080`; stop with Ctrl+C. It binds to loopback and uses a fresh, in-memory H2 database. Nothing is saved to a shared DB. Sample logins are `owner@sprint3.test` and `admin@sprint3.test`, both with password `ReviewOnly123!`. They and sample tools are created only by the test-source class `Sprint3PreviewApplication`; never use those credentials outside this disposable local preview.

Review the owner dashboard's draft, pending, and published sample tools; open the draft to add/edit/delete a version, test duplicate validation, and submit it. Log in as admin to approve/reject a pending tool. Open the published tool's version history while logged out. Confirm the success notice after each transition. Narrow the browser to 375px and 768px and use the keyboard to open/close the menu (Escape returns focus to the menu button). The pending list's table scrolls inside its own region on narrow screens.

This is a review aid, not a production login bootstrap. Registration and login browser forms call Role A's existing JSON endpoints with the session cookie and the current CSRF token. E2E review of owner and moderator flows uses only test accounts.

ผล Sprint 3 ที่ทำในรอบนี้อยู่ใน branch `thaninton_673380043-6_02` และยังไม่ commit: มีหน้า login/register ที่เชื่อมกับ Role A ผ่าน session+CSRF, shared nav/alerts/footer, mobile menu, form validation และ lock ปุ่มระหว่างส่ง, flash messages, safe error pages, version detail link เฉพาะกรณีมี URL, idempotency/query guard สำหรับคิวอนุมัติ และ preview fixtures แยกใน H2.

Browser smoke ที่ตรวจ: สมัคร/login จริง → draft → เพิ่ม version → validation → duplicate version พร้อมรักษาค่าที่กรอก → submit → สลับเป็น admin → เห็น pending queue → approve → ดู public version ได้หลัง logout; error login แสดงข้อความทั่วไปและล้าง password. ตรวจ viewport 375px (document scrollWidth=375; ตาราง scroll ภายใน region), 768px และ 1280px และ Escape ปิดเมนู/คืน focus. ตรวจหน้า status/flash และ devtools ไม่มี console error ในเส้นทางที่ลอง. Automated `mvn -B -f code/pom.xml verify` ผ่าน 153 tests; `git diff --check` ผ่าน.

ขอบเขตสถานะ: ส่วน browser/session/CSRF/navigation/form ของ Role E พร้อมให้ review; ยังไม่ถือว่า Sprint 3 ของทีม A–E ปิดทั้ง sprint เพราะหน้า browse/search/review ร่วมและ sign-off/integration ระหว่างโมดูลยังขึ้นกับเจ้าของ Role อื่น. PostgreSQL จริงและ integration บน CI ยังไม่รันในงานนี้.

ผลหลังปิดงาน Sprint 3 ของ Role E (30 กันยายน 2026): mvn -B -f code/pom.xml verify ผ่าน 154 tests, failures 0, errors 0, skipped 0; สร้าง executable JAR สำเร็จ. Regression ของ sort/Content-Type, CSRF/cascade-delete form และ moderation query budget ผ่านแล้ว. มีคำเตือนยืนยันการลบเครื่องมือ+เวอร์ชันและป้ายสถานะภาษาไทย. ภาพ browser review อยู่ที่ sprint3-review-dashboard.png ใน workspace; preview loopback H2 ยังเปิดให้ตรวจ. งานทั้งหมดของรอบนี้ยังไม่ commit/push.
