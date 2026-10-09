# Role E — ตรวจร่าง V8 ของ Role D

> อัปเดตรอบส่งงาน: tests/fixture/รายงานนี้จัดส่งพร้อม [แผนส่งต่อ](../docs/superpowers/plans/2026-10-07-postgres-race-handoff.md) ใน branch E แล้ว. ข้อความ local-only และงาน integration ที่ยังเปิดด้านล่างเป็นสถานะ ณ รอบ V8; ดูรายงาน D integration/race และแผนล่าสุดสำหรับงานที่ทำต่อแล้ว. ยังไม่ได้รัน V8 กับฐานทีม.

วันที่ 7 ตุลาคม 2026. ขอบเขต: SQL comment-length migration บนฐาน PostgreSQL แยก ไม่ใช่การ merge โค้ด D หรือเปลี่ยนฐานข้อมูลร่วม.

## SQL ที่ตรวจ

- D branch: `naphat_67338800363_02`, commit `0a92dbc3a80538917bc400d8862d8644c5dd4ec9`.
- [SQL ต้นทาง](https://github.com/aarktik/PrimeSkill/blob/0a92dbc3a80538917bc400d8862d8644c5dd4ec9/doc/sql/V8__limit_review_comment_length.sql).
- Git blob ต้นทางและสำเนาเทสตรงกัน: `3a612cbbd02c6cee9aac62090f64a625a39d030f`.
- สำเนาอยู่ `code/src/test/resources/migrations/role-d/` พร้อม README ระบุที่มา; ไม่อยู่ใน production migration directory และไม่ถูก auto-run ตอนเปิดแอป.
- Snapshot develop ที่ตรวจยังเป็น `d232282`. การมี remote branch ของ D ไม่ใช่หลักฐานว่า PR ได้รับ approve หรือ merge แล้ว; รอบนี้ไม่ได้ตรวจสถานะ approval ผ่าน GitHub API.

## วิธีและกรณีทดสอบ

`ReviewCommentMigrationPostgresIT` สร้าง schema ชื่อสุ่มใหม่ทุกกรณี ใช้ fixture reviews ขนาดเล็ก มี rating check เพื่อทวนว่า migration ไม่ทำลาย constraint อื่น. ใช้ guard เดิมบังคับ loopback และชื่อฐานทดสอบ. หลังจบ rollback งานค้าง, reset search_path และลบเฉพาะ schema ที่สร้างเอง.

16 กรณี:

- ข้อมูลเดิมที่รับได้ 7 แบบ: null, empty, whitespace, ASCII 1,999/2,000, ไทย 2,000 และ emoji 2,000. ตรวจว่า comment ไม่ถูก trim/ตัด/เขียนทับ และ constraint ถูก validate.
- ข้อมูลเดิมเกิน 2,000 จำนวน 3 แบบ: ASCII, ไทย, emoji. ต้องหยุด migration พร้อมข้อความ oversized data, เก็บ comment เดิม และไม่ติดตั้ง constraint ค้างหลัง rollback.
- Insert/update หลัง migration จำนวน 3 แบบ: ASCII, ไทย, emoji. 2,000 ผ่าน; 2,001 ถูกปฏิเสธด้วย SQLSTATE `23514` และค่าเดิมยังอยู่.
- ตารางว่างและรันซ้ำ: ไม่เกิด constraint ซ้ำ; rating check เดิมยังบังคับใช้.
- มี constraint แบบ NOT VALID อยู่ก่อน: ตรวจข้อมูลเดิมและเปลี่ยนเป็น validated.
- Caller rollback หลังเพิ่ม constraint: ยกเลิก DDL ได้และข้อมูลเก่ายังคงอยู่.

รันจาก repository root: `./scripts/test-postgres.ps1`. Log รอบ V8: `code/target/role-e-v8-verify.log`; XML/text reports อยู่ `code/target/failsafe-reports/` และ `code/target/surefire-reports/`. ผลรันเป็นไฟล์ ignored ไม่ได้ push ไป Git.

## ผลรันจริง

- V8 tests: 16 passed, failures/errors/skipped = 0.
- Full regression: Surefire 170 + PostgreSQL Failsafe 99 = 269 การรัน, failures/errors/skipped = 0, Maven BUILD SUCCESS.
- Local environment: Java 26.0.1 / PostgreSQL 18.6. สคริปต์ปิด cluster แล้วและตรวจ port 15432 ไม่เหลือ listener.
- รอบแรกแก้ assertion ของ test harness ที่คาด boolean เป็น true/false แต่ JDBC คืน t/f; ใช้ `convalidated::text` เพื่ออ่านค่าอย่างชัดเจน แล้วรันทั้งชุดซ้ำ. ไม่ได้แก้ SQL V8 ของ D. Log รอบแรกเก็บ `code/target/role-e-v8-first-run.log`.
- ไม่พบข้อผิดพลาดของ SQL V8 ใน 16 กรณีที่ครอบคลุมนี้. ยังไม่ใช่การรับรอง schema/ข้อมูลจริงของทีม.
- งาน V8 tests/fixture/รายงานชุดนี้ยังเป็น local changes ไม่ได้ commit/push/merge.

## ก่อนใช้ฐานร่วมยังต้องตรวจ

- ยืนยันฐานเป้าหมาย/ผู้รัน/ช่วงเวลา, backup และ restore, migration history/เลข V8 ที่ใช้จริง.
- นับ oversized rows โดยไม่ดึง comment ออกมา: `SELECT count(*) FROM reviews WHERE comment IS NOT NULL AND char_length(comment)>2000;`. ถ้ามี ต้องให้ผู้ดูแลตัดสินใจจัดการ ไม่ truncate อัตโนมัติ.
- ตรวจ constraint เดิมด้วย `pg_get_constraintdef` และ `convalidated`: SQL นี้ตรวจชื่อและตารางก่อนข้าม ADD แต่ไม่ได้ยืนยันว่า constraint ชื่อเดียวกันมี definition ตรงกับ <=2000. หากเจอ definition ต่างกัน ให้หยุดและตกลง migration แก้ไขก่อน; นี่เป็นเงื่อนไขที่ต้องตรวจ ไม่ใช่การยืนยันว่าฐานทีมมีปัญหานี้.
- ใช้ transaction ครอบทั้งไฟล์; SQL นี้ไม่ได้มี BEGIN/COMMIT ของตัวเอง. Schema/table ต้องชัดเจนเพราะ SQL อาศัย search_path.
- PostgreSQL `char_length` นับอักขระ ไม่ใช่ UTF-8 bytes หรือ Java UTF-16 code units. Emoji boundary ในเทสนี้ยืนยันกติกา DB เท่านั้น ไม่ใช่ API/form validation ของ D.

## งานที่ยังเปิดร่วม D/E

ผลชุดนี้ไม่ปิดงาน PostgreSQL review service/API integration, user/tool FK และ unique contracts ของ D, Observer, session/CSRF, หรือ review/publishing race. ชุดนี้ใช้ minimal fixture และ SQL snapshot; ไม่ใช่สำเนาฐานจริงหรือ migration runner/Flyway test. Java17/PostgreSQL17 บน CI และ Docker ยังต้องมีผลรันจริงแยกต่างหาก.
