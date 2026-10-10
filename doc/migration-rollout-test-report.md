# ข้อ 5 — local migration preparation evidence

Delivery update: [รายงานส่งมอบบน personal branch](role-e-delivery-handoff.md) เป็นสถานะล่าสุด. ข้อมูล worktree/commit/push และผลทดสอบด้านล่างที่ระบุรอบก่อนเป็นประวัติการเตรียมงาน.

ผล Java17 ล่าสุดอ่าน [รายงานนี้](java17-test-report.md): 450 tests และ migration rehearsal ผ่านบน JDK17 จริง. ผล Java26 และ log paths ด้านล่างเป็นประวัติรอบก่อน; archived evidence อยู่ที่ `D:\PrimeSkill-worktrees\evidence\before-java17`.

วันที่ 9 ตุลาคม 2026 (Asia/Bangkok). Candidate อยู่ใน `D:\PrimeSkill-worktrees\b1-role-e`, branch `codex/b1-role-e`, base `3187098fac9d91c14d1fdfe40bb765797cde8cfd` พร้อม uncommitted B1 diff. ไม่มี commit/push/merge และไม่ได้ตรวจหรือแก้ฐานทีม.

## สิ่งที่เพิ่ม

- Read-only inventory สำหรับ schema, lengths, counts, constraints/indexes และ oversized data; ตรวจตารางที่ขาดหรือถูก search_path บังแล้วหยุด.
- Rehearsal helper ใช้ฐาน GUID ที่สร้างเองบน disposable cluster เท่านั้น; สำรอง schema ครบชุดพร้อมข้อมูลตัวแทนและทดสอบ migration/rerun/restore.
- Test-only application launcher ตรวจ Hibernate ด้วย SQL initialization ปิดอยู่; เปิดแอปเฉพาะ loopback port ชั่วคราวแล้วปิด.
- [Runbook](migration-rollout-runbook.md) และ [checklist](database-rollout-checklist.md) แยก local evidence จากสิ่งที่ operator ต้องยืนยันก่อนฐานทีม.

## ผลระหว่างตรวจและแก้

### Final rehearsal — PASS

`final-verify.log` จบ exit0 พร้อม `Migration rollout rehearsal PASS`; disposable cluster หยุดแล้วและไม่มี listener15432. Coverage checker ผ่าน; `git diff --check` ผ่าน.

หลักฐาน: `code/target/migration-rollout/c72be31cd5ea4d2384a4d2c3ba407659/` ภายใน worktree (ignored).

- `empty-startup.log`: initialized empty schema แล้วเปิด actual app ด้วย Hibernate validate/SQL init=never สำเร็จ. เป็น schema.sql baseline fixture ไม่ใช่ production migration-baseline approval.
- `legacy-inventory.txt` / `migrated-inventory.txt`: read-only inventory รันผ่านก่อน/หลัง migration.
- Whole-file V7 → V8 → B1 ผ่าน; original JSON contents/associations และ FK/unique/primary keys ตรงกัน. Full description350ตัวอักษรยังอยู่; new short description300, view_count0, review_revision0 และ URL rename ถูกต้อง.
- Rerun ทั้งชุดผ่านและ complete tool row ไม่เปลี่ยน; `migrated-startup.log` เปิดแอปโดยไม่ให้ SQL init เติมตารางสำเร็จ.
- `legacy.dump` restore เข้าอีกฐานผ่าน; ตรวจ original contents และไม่มี review_revision ตาม old schema; re-migrate แล้ว `restored-remigrated-startup.log` ผ่าน.
- `summary.json`: PASS, restore+re-migrate+app validation **17.63วินาที** สำหรับ fixture เล็กนี้เท่านั้น ไม่ใช่ RTO ของฐานทีม. Backup SHA256 `20C396B56A599B25CA819D2191C300C1706C40094EF2417DEDA101754FD34952`.
- Draft/source hashes อยู่ใน [manifest](sql/migration-draft-manifest.json); V7/V8/B1 SQL เดิมไม่ได้แก้ในงานข้อ5.

- Final Java verify (`code/target/rollout-progress/final-verify.log`): **Surefire265 + PostgreSQL185 =450**, failures/errors/skips0, Maven BUILD SUCCESS. Migration classes: V7/inventory14 + V8 comments16 + B1 revision6 =36 cases (เป็นส่วนหนึ่งของ450 ไม่ใช่นับเพิ่ม). จำนวน185เพิ่มจาก184เพราะ search_path regression.
- Helper URL guard ตรวจจริง3กรณี: remote host, wrong local database, URL with query string ถูกปฏิเสธก่อนทำ database operations.

- `code/target/rollout-progress/verify.log`: Java265+184=449 ผ่าน; rehearsal เปิดแอปกับ empty schema สำเร็จ แต่ seed ภาษาไทยผ่าน Windows native command argument ทำให้ psql UTF8 error. แก้ใช้ไฟล์ UTF8 และกำหนด/คืนค่า PGCLIENTENCODING. ไม่ถือว่ารอบนี้ซ้อม restore ผ่าน.
- Fresh reviewer `/root/review_b1_e` พบ Important 1: metadata อ่าน current_schema แต่ unqualified tables อาจ fallback ไป public. Regression `rolloutInventoryRejectsSearchPathFallbackToPublicTables` บน SQL เดิมล้มเพราะไม่ throw (`inventory-red.log`:14 tests/1 failure/0 errors/0 skips). แก้ตรวจ required tables และ resolved OID ก่อน inventory.
- ไม่มี Critical/Important อื่นที่ผู้ตรวจพบใน helper/launcher/SQL/runbook. การแก้ได้รับการตรวจด้วย regression และชุดรวม ไม่ถือว่าแทน review จากทีม.

## ข้อจำกัด / สิ่งที่ยังต้องยืนยัน

- ใช้ PostgreSQL18.6/Java26.0.1 ในเครื่อง; ไม่ใช่ GitHub CI Java17/PG17 หรือ actual production runner.
- Fixture เป็น schema ตัวแทนครบตารางจาก schema.sql ปัจจุบัน แล้วสร้าง legacy shape เพื่อทดสอบ V7/V8/B1; ไม่ใช่สำเนาฐานทีม และไม่ได้ตรวจ previous application กับ restored legacy schema.
- pg_restore ใช้ --no-owner บน local role; production ownership/permissions/extensions และ full recovery duration ต้องซ้อมกับผู้ดูแลอีกครั้ง.
- ชื่อ V7/V8 ยังเป็น proposal; B1 ไม่มี official version. ไม่ได้ติดตั้ง Flyway หรือเปลี่ยน applied checksum.
- V8 เดิมไม่ยืนยัน equivalent definition ของ existing same-name constraint; ต้องตรวจจริงก่อน rollout. tag index ต้องรวม forward change ของ C หากฐานเดิมขาด. B/C guards ยังเป็น dependency ของ B1.
- Target/history/operator/backup/window และ approval ของทีมยังไม่ได้รับ จึงยังไม่รันฐานทีม.

## งานที่รอผู้ใช้ตรวจ

1. **ข้อ 3 ส่วน E:** revision/decision contract, version lock+refresh, migration draft และ [รายงาน B1](b1-implementation-test-report.md).
2. **ข้อ 5 ส่วนเตรียม:** inventory SQL, rehearsal helper/launcher, runbook, checklist และหลักฐาน local รอบล่าสุด.
3. **ชุดส่งต่อ:** B รับ metadata guards, C รับ tag guards/คะแนน browse, A/D ตรวจ contract/integration; DB owner ยืนยัน target/history/runner/window. ผู้ใช้ส่งเอกสารให้ทีมเองได้; ยังไม่มีการส่งข้อความแทน.
4. หลังตรวจ ค่อยเลือกชุด commit/push ลง personal branch และเส้นทาง integration. การตรวจเอกสารนี้ไม่เท่ากับอนุมัติรันฐานทีม.
