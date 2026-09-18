# Role B Cross-Team Integration Handoff

> ใช้ร่วมกับ `IMPLEMENTATION_PLAN.md`, `ROLE_B_TOOL_CATALOG.md`, `UI_DESIGN_GUIDE.md` และ `ROLE_A_INTEGRATION_HANDOFF.md`  
> เอกสารนี้ระบุ contract ที่ Role B ทำเสร็จแล้วและงานเชื่อมต่อที่ A/C/E ต้องยืนยันก่อน merge/release

## 1. สิ่งที่ Role B มีให้แล้ว

- Tool/Category entity, repository, DTO, mapper, service, REST controller และ Thymeleaf foundation
- ownership check ของ Tool ที่ service boundary
- admin check ของ Category mutation ที่ service boundary
- `AuthenticatedUserPrincipal#getId()` เป็น principal contract
- `CurrentActorProvider` อ่าน user id/ADMIN จาก Spring Security context โดยไม่ใช้ reflection
- `AuthenticationRequiredException` extends Spring Security `AuthenticationException`
- API error shape เดียวกัน พร้อม validation errors และ trace ID สำหรับ server/database failures
- database integrity mapping:
  - PostgreSQL `23505` unique violation → 409 `RESOURCE_CONFLICT`
  - PostgreSQL `23503` foreign-key violation → 409 `RESOURCE_CONFLICT`
  - integrity failure อื่น → safe 500 `INTERNAL_ERROR`
- dashboard pagination backend + UI controls
- migration draft ที่ `doc/sql/V7__align_existing_tool_catalog.sql` ซึ่งยังไม่ถูก execute

## 2. Handoff ให้ Role A — Authentication/Security/Error

A ต้องทำให้ authenticated principal implement:

```java
com.example.toolhub.security.AuthenticatedUserPrincipal
```

โดย `getId()` ต้องคืน persisted `users.id` และ ADMIN ต้องมี authority มาตรฐาน `ROLE_ADMIN` ห้ามอ่าน `userId`, `ownerId` หรือ `role` จาก request

A ต้องเพิ่ม/ยืนยัน:

- session-based `SecurityFilterChain`
- public GET สำหรับ home, published tools, categories และ Swagger ตาม demo policy
- authenticated routes สำหรับ dashboard/tool mutation/profile/review
- ADMIN routes สำหรับ `/api/v1/admin/**`
- REST unauthenticated entry point → JSON 401 `UNAUTHENTICATED`
- REST access denied → JSON 403 `ACCESS_DENIED`
- Web unauthenticated → login redirect หรือ web error flow ที่ทีมตกลง
- CSRF เปิดสำหรับ Thymeleaf/session mutation
- method security และ owner/non-owner tests
- isolated test database/profile; test suite ห้ามต่อ Supabase จริง

`GlobalExceptionHandler` จำกัดเฉพาะ `controller.api` แล้ว A ไม่ควรทำ advice อีกตัวที่คืน JSON ให้ Thymeleaf โดยไม่ตั้งใจ ก่อนแก้ `ErrorResponse` หรือ constraint mapping ให้ review ผลกระทบกับ B/D/E

## 3. Handoff ให้ Role C — Public Browse/Search

ปัจจุบัน `ToolWebController.GET /tools` เป็น placeholder และส่งรายการว่าง เพื่อไม่สร้าง search logic แทน C ส่วน REST B มีเฉพาะ detail/create/update/delete

ก่อน C merge ต้องตกลง ownership เพื่อไม่ให้เกิด duplicate/ambiguous mappings:

- C เป็นเจ้าของ `GET /tools` และ `GET /api/v1/tools` สำหรับ published search/filter/sort/page
- หาก C สร้าง controller ใหม่ ให้ถอด placeholder mapping ของ B ใน PR integration เดียวกัน
- C ใช้ `ToolResponse`/projection contract ที่ตกลง ห้ามส่ง Entity ตรง
- public result ต้องกรองเฉพาะ `PUBLISHED`
- ห้ามแก้ Tool CRUD ownership/status mutation ของ B/E ผ่าน search request
- pagination ต้องรักษา query parameters และใช้ stable secondary sort ด้วย id

## 4. Handoff ให้ Role E — Flyway/Deployment

`doc/sql/V7__align_existing_tool_catalog.sql` เป็น guarded draft เท่านั้น ไม่อยู่ใน Flyway location และยังไม่เคยรันกับ Supabase

E/ทีมต้อง:

1. ยืนยันว่า migration version ถัดไปคือ V7 จริง; ถ้าไม่ใช่ให้ rename ก่อนติดตั้ง
2. ทดสอบ draft กับสำเนา schema เก่าที่มี `website_url`
3. ทดสอบกับ schema ใหม่ที่มี `repository_url`
4. ทดสอบกรณีมีทั้งสองคอลัมน์ ซึ่ง migration ต้องหยุดให้ reconcile data
5. backup/export ก่อน migrate shared/production database
6. ย้ายไฟล์ที่อนุมัติแล้วไป `code/src/main/resources/db/migration/`
7. เพิ่ม Flyway dependency/config และใช้ `ddl-auto=validate`
8. รัน smoke test หลัง migration โดยไม่ reset production data

ห้ามรัน draft นี้กับ production แบบ ad hoc และห้ามใช้ `CREATE TABLE IF NOT EXISTS` เป็นตัวแทน forward migration สำหรับฐานข้อมูลเดิม

## 5. Shared UI Owner

หน้า B ใช้ `ps-` classes แล้ว แต่ยังต้องรอ shared fragments/assets ตาม `UI_DESIGN_GUIDE.md`:

- `head`, navbar, sidebar, alerts, pagination และ status badge fragments
- tokens/components/pages CSS
- status label ภาษาไทยจาก enum กลาง
- responsive/accessibility check ที่ 375px, 768px และ 1280px

ให้แก้ component กลางที่ต้นทาง ไม่ copy navbar/CSS เข้าแต่ละหน้า

## 6. Verification ก่อน Integration Merge

Role B focused verification:

```text
mvn -f code/pom.xml -DskipTests test-compile
mvn -f code/pom.xml -Dtest=ToolServiceImplTest,CategoryServiceImplTest,ToolRestControllerTest,CategoryRestControllerTest,CurrentActorProviderTest,ToolWebControllerTest test
```

ก่อน release ต้องเพิ่มและผ่าน:

```text
mvn -f code/pom.xml verify
```

พร้อม security matrix, migration integration test และ browser smoke flow โดยไม่เชื่อม test suite เข้าฐานข้อมูล production