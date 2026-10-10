# E — ข้อมูล rollout ที่ตรวจได้แล้วและข้อมูลที่ยังต้องยืนยัน

วันที่9ตุลาคม2026 อ้างอิง repo742a0fa ไม่ใช่การตรวจ target DB

## ตรวจจาก repo แล้ว

- code/pom.xml กำหนด Java17 พร้อม Maven Enforcer; มี scripts/mvn-java17.ps1
- docker-compose.yml และ CI ใช้ PostgreSQL17 Alpine; version ของฐานทีมยังไม่ทราบ ส่วน local rehearsal ใช้18.6
- application.properties ใช้ Hibernate ddl-auto=validate แต่ **spring.sql.init.mode=always** ต้องยืนยัน startup config ของ target ก่อน rollout ไม่ถือว่า Hibernate validate ทำให้ SQL initialization หยุดทำงาน
- ไม่พบ Flyway/Liquibase dependency ใน code/pom.xml; การมีไฟล์ชื่อ V7/V8 ไม่ยืนยันว่ามี migration runner/history อัตโนมัติใน target
- schema.sql ใช้สำหรับ schema initialization; ไม่ใช้แทน approved forward migrations กับฐานเดิม
- มี V7/V8/B1 draft, read-only inventory และ release manifest อยู่แล้ว ไม่จัดเลข version ใหม่โดยเดา
- local full-schema backup/migrate/rerun/restore/remigrate/app validation ผ่านตาม migration-rollout-test-report.md; ไม่แทน staging ที่ใช้ actual runner และข้อมูลจริง

## สิ่งที่ E ทำต่อเองได้

- เก็บ CI evidence ของ source ที่ส่งและเตรียม acceptance สำหรับงาน B/C
- ตรวจ diff ของ migration/schema ที่ได้รับใหม่และอัปเดต manifest เมื่อไฟล์ที่อนุมัติเปลี่ยน
- ตรวจความสอดคล้องของ runbook/checklist กับ source โดยไม่เชื่อมต่อฐานทีม
- เตรียมรายการคำถามด้านล่างให้ operator ตอบ; ไม่บันทึก passwords/tokens ในเอกสาร

## ข้อมูลที่ต้องรับจาก DB owner/operator

- [ ] Environment และตัวระบุ target/schema ที่ยืนยันได้ พร้อม PostgreSQL version
- [ ] Application SHA/config ที่กำลังใช้ และ SHA ที่ตั้งใจ deploy
- [ ] Runner ที่ใช้จริง, history/baseline, versions/checksums ที่เคยรัน และวิธีจัดการ populated DB
- [ ] Startup SQL initialization policy ที่อนุมัติสำหรับ target; schema.sql/data.sql ใดจะถูกรันและเมื่อใด
- [ ] Approved migration filenames/order/checksums และ transaction behavior ของ runner
- [ ] Read-only inventory ของ target: counts, lengths, constraints, indexes, URL conflicts/backfill และ oversized comments
- [ ] ตรวจ definition ของ existing V8 same-name constraint ไม่ถือว่าชื่อตรงแล้ว definition ถูก
- [ ] Backup reference/retention/access owner และหลักฐาน restore ใน staging
- [ ] Operator/reviewer, write pause/window, recovery decision owner และเงื่อนไข stop/go

หากไม่มีข้อมูลข้อใดให้รายงาน unknown อย่าใช้ค่า Compose/local ไปแทน target

## เงื่อนไขก่อนเริ่มฐานทีม

1. เติม target record ใน database-rollout-checklist.md ครบและผู้รับผิดชอบยืนยัน
2. ตรวจ data preflight และตกลงการแก้ข้อมูลขัดแย้ง ไม่ truncate/เลือกข้อมูลทิ้งเงียบ ๆ
3. ซ้อม approved runner และ backup/restore บน staging พร้อม startup validation
4. ยืนยัน schema/API compatibility รวม B/C guards ก่อนเปิดใช้ full B1 และหยุด old writers ตามแผน
5. ได้อนุมัติ operator/reviewer แล้วจึงปฏิบัติตาม migration-rollout-runbook.md และบันทึกผลจริง

## เอกสารที่ต้องใช้ร่วมกัน

- [Checklist](database-rollout-checklist.md)
- [Runbook](migration-rollout-runbook.md)
- [Release manifest](sql/migration-release-manifest.json)
- [Rehearsal evidence](migration-rollout-test-report.md)
- [Role handoff](team-role-handoff-2026-10-09.md)
