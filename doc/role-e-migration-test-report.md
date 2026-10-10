# Role E — ทดสอบร่าง migration V7 บน PostgreSQL

> อัปเดตรอบส่งงาน: ร่าง SQL/tests/รายงานนี้จัดส่งใน branch E พร้อม [แผนส่งต่อ](../docs/superpowers/plans/2026-10-07-postgres-race-handoff.md). ข้อความ local-only และขอบเขตงานที่ยังไม่ทดสอบด้านล่างเป็นสถานะของรอบ V7; V8 และ D integration/race มีรายงานแยกแล้ว. ยังไม่ได้รัน V7 กับฐานทีม.

วันที่: 7 ตุลาคม 2026. งานนี้เป็น local changes บน branch E ยังไม่ commit/push หรือ merge develop.

## วัตถุประสงค์และวิธีรัน

ตรวจ `doc/sql/V7__align_existing_tool_catalog.sql` จากไฟล์จริงด้วย `MigrationPreflightPostgresIT` ผ่าน Maven profile `postgres-it` ที่ E เตรียมไว้. รันจาก repository root ด้วย `./scripts/test-postgres.ps1`.

ใช้ PostgreSQL 18.6 / Java 26.0.1 ในเครื่อง. แต่ละกรณีสร้าง schema ชื่อสุ่มในฐาน disposable ที่ผ่าน guard, สร้าง fixture legacy ขนาดเล็ก, แล้วลบเฉพาะ schema นั้นพร้อม reset search_path ก่อนคืน connection เข้า pool. ไม่มีการรัน SQL นี้กับฐานร่วมและไม่มีการเปิด Flyway.

## กรณีทดสอบ 13 กรณี

- URL 3 แบบ: มี website_url เดิม, มี repository_url อยู่แล้ว, ไม่มีทั้งสองคอลัมน์ — ยืนยัน URL/ชื่อ/หมวด/description เดิมไม่หาย, short_description ยาว 300 ตัวอักษร, view_count เริ่ม 0 และ index ถูกสร้าง.
- รัน SQL ซ้ำ — ข้อความสรุปที่ผู้ใช้แก้และ view_count เดิมไม่ถูกเขียนทับ.
- มี URL ทั้งสองคอลัมน์พร้อมข้อมูลต่างกัน — ปฏิเสธและเก็บค่าทั้งสองเดิมไว้.
- ข้อมูลยาวเกิน 5 แบบ: category description >500, tool name >150, slug >170, website_url/repository_url >500 — ปฏิเสธก่อนเปลี่ยน schema และไม่ตัดข้อมูล.
- description เป็น null ทำให้ขั้นตอน NOT NULL ล้ม — transaction rollback คืน schema/data ก่อนรัน. กรณีนี้ยืนยันว่าผู้รันต้องมี transaction ครอบ migration ไม่ได้อ้างว่าทุกข้อผิดพลาดตรวจพบใน preflight แล้ว.
- มี short_description เป็น TEXT อยู่ก่อน — ต้องปรับเป็น VARCHAR(300) และเก็บค่าที่ผู้ใช้มีอยู่.
- short_description เดิมยาว 301 — ต้องปฏิเสธและเก็บข้อมูลเดิม.

## ข้อผิดพลาดที่ยืนยันและแก้ในร่าง SQL

`ADD COLUMN IF NOT EXISTS short_description VARCHAR(300)` ไม่เปลี่ยนชนิดคอลัมน์ที่มีอยู่แล้ว. ร่างเดิมจึงปล่อย short_description ที่เป็น TEXT หรือมีข้อมูลเกิน 300 ผ่านไป โดย schema ยังไม่ตรงเป้าหมาย.

หลักฐานก่อนแก้: targeted run มี 13 tests, failures 2, errors 0. กรณีแรกคาด character_maximum_length=300 แต่ได้ null (TEXT); อีกกรณีคาด SQL exception สำหรับ 301 ตัวอักษรแต่ไม่เกิด. Log: `code/target/role-e-migration-red.log`.

แก้ร่าง V7 โดยตรวจคอลัมน์เดิมและค่าที่ยาวเกินก่อน ALTER แล้วเพิ่ม `ALTER COLUMN short_description TYPE VARCHAR(300)`. ไม่ truncate ค่าที่เกินและไม่เปลี่ยน production application code.

## ขอบเขตที่ยังไม่ยืนยัน

- นี่เป็น fixture legacy ขนาดเล็ก ไม่ใช่สำเนาฐานจริงทั้งระบบ; ยังไม่ยืนยัน Flyway baseline/version history, migration runner, backup/restore หรือ deploy.
- ยังไม่ได้ทดสอบการรวม schema ของ C/D, V8 comment constraint, rating sort หรือ review/publishing race.
- ต้องตกลง migration sequence กับทีมก่อนใช้ V7; ไฟล์ยังอยู่ `doc/sql` และไม่ถูก auto-run ตอนเปิดแอป.
- Docker และ GitHub CI Java17/PostgreSQL17 ยังต้องมีผลรันจริงแยกต่างหาก.

ผลหลังแก้: migration tests 13/13 ผ่าน. Full regression ผ่าน Surefire 170 และ Failsafe PostgreSQL 83 (รวม 253 การรัน), failures/errors/skipped = 0; Maven BUILD SUCCESS. สคริปต์ปิด PostgreSQL ทดสอบหลังจบแล้ว.

Log รวม: `code/target/role-e-migration-verify.log`; รายงาน XML/text: `code/target/surefire-reports/` และ `code/target/failsafe-reports/` (ไฟล์ผลรันไม่ติดตามใน Git).
