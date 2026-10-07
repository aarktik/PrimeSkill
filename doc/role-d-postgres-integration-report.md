# Role D PostgreSQL integration — ตรวจแยกโดย Role E

วันที่ 7 ตุลาคม 2026. Source snapshot: `naphat_67338800363_02` commit `0a92dbc3a80538917bc400d8862d8644c5dd4ec9`.

## วิธีทดสอบและไฟล์ส่งต่อ

- [Test overlay](../test/role-d-postgres/RoleDPostgresIT.java) เก็บแยกจาก test source ของ E เพราะอ้าง service/event ของ D ที่ยังไม่มีใน E branch.
- [วิธีรัน](../test/role-d-postgres/README.md): `./scripts/check-role-d-postgres.ps1 -RoleDRef 0a92dbc3a80538917bc400d8862d8644c5dd4ec9`.
- สคริปต์สร้าง detached checkout ใหม่ใน ignored `code/target/role-d-checks/`, บันทึก baseline, แล้วเพิ่มเฉพาะ Maven/test config/guard และ integration test. ไม่ merge code เข้า E/develop และไม่แก้ production source ของ D.
- รันด้วย Java 26.0.1 / PostgreSQL 18.6. test config คง production defaults ของ D รวม `spring.jpa.open-in-view=false` แต่ใช้ฐาน disposable ของ E script. ไม่มี Supabase credentials/ฐานร่วม.
- MockMvc ใช้ controllers, security filters, sessions, CSRF, services และ repositories จริง. ไม่ mock actor/service; เป็น in-process HTTP dispatch ไม่ใช่ browser/network E2E.
- ผู้ใช้สมัคร/login ผ่าน API; ADMIN ถูกยกระดับเฉพาะ fixture ก่อน login. สถานะ Tool เตรียมด้วย SQL เพราะยังไม่ได้รวม E publishing implementation.

## Baseline ที่ต้องแจ้ง D/A

Unmodified snapshot รัน `mvn -B -f code/pom.xml verify` โดยไม่มี DB environment ได้ **131 tests, failures 0, errors 1**. Error คือ `DemoApplicationTests.contextLoads` (อยู่ไฟล์ `ToolHubApplicationTests.java`) โหลด `${SUPABASE_DB_URL}` ที่ยังไม่ถูกกำหนด และ PostgreSQL driver ปฏิเสธ URL นี้.

นี่เป็นข้อจำกัดการเตรียม test environment ไม่ใช่หลักฐานว่า review business logic ผิด. เมื่อเพิ่ม test-only datasource overlay ให้ context ใช้ฐาน PostgreSQL แยก จึงตรวจ integration ต่อได้. ผล overlay ที่ผ่านไม่เปลี่ยนข้อเท็จจริงว่า unmodified baseline ที่ไม่มี config ยังไม่ผ่าน.

ข้อเสนอให้ D/A: เพิ่ม isolated test datasource/profile สำหรับ contextLoads หรือกำหนดฐาน CI เฉพาะเทสให้ชัดเจน โดยไม่ใช้ credentials ของฐานร่วม. Snapshot เดิมยังไม่มีการแก้ไขนี้ใน branch D จากงานของ E.

## Coverage ของชุดใหม่ 28 กรณี

- API create/update/delete commit ข้อมูลจริง, trim/blank→null, optional comment และขอบเขต 2,000 ตัวอักษร.
- Duplicate ผ่าน API เป็น conflict และ database unique(user_id,tool_id) บังคับจริง.
- เจ้าของ Tool รีวิวตัวเองไม่ได้; author เท่านั้นแก้ได้; other-user ลบไม่ได้; author/admin ลบได้หลังซ่อน Tool.
- DRAFT/PENDING/DEPRECATED visibility, create policy, hidden edit, missing tool และ review ID ที่ไม่ตรง tool path.
- Session/anonymous/logout, CSRF ขาด/ผิดสำหรับ create/update/delete; actor/tool fields ที่ผู้เรียกปลอมใน JSON ต้องไม่เปลี่ยนตัวตนจาก session/path.
- Rating 1/5 ผ่าน, 0/6/null ไม่ผ่าน; comment เกิน 2,000 ไม่ผ่านทั้ง API และ DB.
- Public list pagination, stable id tie-break, invalid pagination และ response ไม่เปิด email/password.
- Batch summary ใช้ 1 prepared query, empty input 0 query; avg/count/unrated ถูกต้อง และเปลี่ยนตาม CRUD.
- Foreign keys ของ user/tool, tool-delete cascade และ reviewer-delete cascade โดยไม่ลบ Tool/User ผิดตัว.
- Production ReviewAuditListener ทำงานหลัง commit จริงและ log เฉพาะ IDs ไม่ใช่ comment; เมื่อ transaction rollback ไม่มี row และไม่มี AFTER_COMMIT audit.

เทสเดิมของ D ที่กำหนด H2 ใน annotation ยังใช้ H2. แยกจำนวน Surefire เดิมออกจาก `RoleDPostgresIT` ซึ่ง assert database product PostgreSQL โดยตรง.

## ผลรันยืนยัน

- ทดสอบผ่าน runner ตั้งแต่ clone snapshot ใหม่: Surefire เดิม 131 passed + PostgreSQL IT 28 passed = 159 การรัน; failures/errors/skipped = 0 และ BUILD SUCCESS สำหรับ overlay.
- Unmodified baseline ที่ไม่มี datasource config ยัง exit 1 ตามข้อจำกัดข้างต้น; runner บันทึกแยก ไม่ซ่อนว่า baseline fail.
- Checkout ที่ใช้ยืนยัน: `code/target/role-d-checks/role-d-0a92dbc-b7c77377/`.
- ตรวจว่า hash ของ Java harness ที่เก็บบน E ตรงกับไฟล์ที่รันจริง, `git diff -- code/src/main` ของ D ว่าง และ port 15432 ไม่มี listener หลังจบ.
- ค่า config ที่ใช้คง `open-in-view=false` ของ D; ไม่ได้เปิด lazy loading ระหว่าง render เพื่อให้เทสผ่าน.
- จำนวน 159 ของ checkout D นี้แยกจาก 269 ของ E/V7/V8 ก่อนหน้า ไม่ใช่ผลรัน D+E ที่ merge รวมกัน.

## งานที่ยังเปิด

- Review/publishing concurrency race และ integration D+E ที่มี publishing service จริงยังไม่ทดสอบในชุดนี้.
- ไม่ใช่การยืนยัน Java17/PostgreSQL17 บน CI, Docker, browser/session over TCP หรือ deployment.
- ไม่เปลี่ยน migration history หรือรัน V8 กับฐานร่วม; ผล V8 แยกอยู่ [รายงาน V8](role-e-v8-test-report.md).
- ไม่ได้ทดสอบให้ logger/Observer ล้มเหลวหลัง commit; ชุดนี้ยืนยันช่วงเวลา AFTER_COMMIT, no-event-on-rollback และ privacy ของ audit ที่รันจริง.
- Source snapshot ใหม่ของ D ต้องรันซ้ำ; ผลนี้ไม่ครอบคลุม commit ในอนาคตและไม่ใช่การ approve/merge PR.

## ตำแหน่งผลรัน

Log ของ runner: `code/target/role-d-check-runner.log` (ignored). ใน checkout ที่สคริปต์พิมพ์ออกมา มี `baseline-verify.log`, `postgres-verify.log`, `code/target/surefire-reports/` และ `code/target/failsafe-reports/`.

ไฟล์ harness/script/รายงานในรอบนี้ยังเป็น local changes บน branch E; ไม่มี commit/push/merge จากงานนี้.
