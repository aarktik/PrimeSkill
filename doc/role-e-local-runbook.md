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

### ข้อผิดพลาดที่เทสตรวจพบและยังไม่ได้แก้โค้ดแอป

1. `unknownModerationSortFieldIsAClientError`: admin เรียก `GET /api/v1/admin/tools/pending?sort=doesNotExist,asc` ได้ 500 แทน 400. Pageable รับ sort ที่ไม่มีใน entity แล้วเกิด PropertyReferenceException ซึ่งถูก generic exception handler แปลงเป็น INTERNAL_ERROR. ควรตรวจ/จำกัดฟิลด์ sort ที่ endpoint ก่อนส่งเข้า repository.
2. `unsupportedRequestMediaTypeReturns415WithoutWritingData`: owner ที่มี session/CSRF ถูกต้อง ส่ง `POST /api/v1/tools/{id}/versions` ด้วย `Content-Type: text/plain` ได้ 500 แทน 415. GlobalExceptionHandler จับ HttpMediaTypeNotSupportedException ด้วย handler ของ Exception ทั่วไป. ควรจัดการ HTTP exception ให้คงสถานะมาตรฐานที่ถูกต้อง.

เทสที่พบปัญหายังคง assertion ตามพฤติกรรมที่ถูกต้อง ไม่มี @Disabled และไม่ได้เปลี่ยนให้คาดหวัง 500 เพื่อให้ผ่าน เมื่อแก้แอปแล้วใช้เทสเดิมยืนยัน regression ได้ ดังนั้นชุดทดสอบปัจจุบันตั้งใจคงสถานะไม่ผ่านไว้ตามบั๊กที่ยังเปิดอยู่

ข้อจำกัด: การแข่งกันของคำขอทดสอบกับ H2 เท่านั้น และ barrier ไม่รับประกันทุก interleaving; ยังต้องยืนยัน locking/concurrency บน PostgreSQL จริง ไม่ใช่การรับรองว่าไม่มีบั๊กเหลือทั้งหมด

ผลรันล่าสุดของชุดเพิ่มเติม: 142 tests ทั้งโครงการ, ผ่าน 140, failures 2, errors 0, skipped 0; เฉพาะ RoleEFlowIntegrationTest มี 57 กรณี (เพิ่มจากเดิม 3 เป็น 57). Log: code/target/role-e-adversarial-final.log. Maven verify จบ BUILD FAILURE จากสองบั๊กข้างต้น จึงไม่ได้ยืนยันการ package ในรอบนี้.
