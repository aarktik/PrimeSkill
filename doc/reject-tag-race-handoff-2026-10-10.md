# E — เติม concurrent REJECT–tag coverage

## ที่มาและ source

ต่อจากชุดรวม `0cc966ffe062abebe43bccd0d2c4431e85242de5` ที่ A/D แจ้งไม่พบ blocker และ CI ผ่าน ผู้ใช้เลือกให้เพิ่ม tests ก่อนส่งต่อ เพื่อปิดช่องว่าง REJECT–tag โดยไม่ถือว่าผล review เดิมรับรอง source ใหม่อัตโนมัติ

ทำงานใน isolated worktree `D:/PrimeSkill-worktrees/b1-b-e-integration` branch `codex/reject-tag-race` ระหว่างเตรียมชุดนี้ ไม่ใช้ฐานทีม

## Tests ที่เพิ่ม

เพิ่ม12 executions ใน `TagMutationConcurrencyPostgresIT` จาก22เป็น34:

1. **4:** assign/unassign × ทั้งสองลำดับ: mutation ล็อก PENDING ก่อนต้องถูกปฏิเสธ แล้ว REJECT สำเร็จ; REJECT commit ก่อน mutation ที่รอ lock ต้องอ่าน DRAFT ใหม่และแก้ได้
2. **2:** REJECT rollback ก่อน writer ที่รอ lock → writer ยังเห็น PENDING และถูกปฏิเสธ; Tool snapshot และ association คงเดิม
3. **2:** REJECT + tag mutation ใน transaction แรก rollback → REJECT อีก transaction ที่รอทำงานต่อได้ โดย association ที่ rollback ไม่หลุดออกมา
4. **2:** หลัง REJECT commit, tag mutation rollback ขณะ SUBMIT รอ → submission ใหม่ไม่รับ tag changes ที่ rollback และ revisionเพิ่มเป็น2
5. **2:** preload managed PENDING แล้วอีก connection REJECT commit → mutation ต้อง refresh เป็น DRAFT และ persist association ได้

ใช้ race harness เดิม: สอง connections, latches คุม transaction, `pg_blocking_pids` ยืนยันการ block จริง และ timeout ที่จำกัด ส่วน stale-managed tests ใช้ worker transaction แยกยืนยัน cached state ก่อน refresh

ตรวจ associations, status, revision, owner/viewCount และ metadata snapshot หลังจบ transaction ในกรณีทั้งสองลำดับยังตรวจ resubmit → revision2 → token1 ของทั้ง APPROVE/REJECT ถูกปฏิเสธและไม่เปลี่ยน snapshot

## Gate และขอบเขตการแก้

- `scripts/check-role-c-reports.py`: ขั้นต่ำ TagMutationConcurrencyPostgresIT34
- `scripts/test-role-c-reports.py`: เพิ่ม regression ว่ารายงานเก่า22ต้องไม่ผ่าน และตรวจขอบ33
- เห็น test gate ใหม่ล้มเหลวกับ checker เดิมก่อนเพิ่มขั้นต่ำ; ไม่ใช่หลักฐานว่ามี production bug
- ไม่มีการแก้ production code, POM, schema, security config หรือ migration
- gate บังคับ execution count ของ suite ไม่ใช่ line/branch coverage

## การส่งต่อ

- A: ตรวจ delta ของ tests/gate และยืนยันขอบเขต security บน resulting SHA ใหม่ ไม่ใช้รายงาน0cc966fแทนโดยอัตโนมัติ
- D: ตรวจ expected behavior ของ REJECT/tag, commit/rollback orders, stale-managed state และ stale review token พร้อม delta จาก0cc966f
- E: รัน full Java17/PostgreSQL/Python + report gates แล้วจัดส่ง source SHA และ CI run ของชุดใหม่
- Tests ใหม่เป็น service/PostgreSQL races ไม่แทน reviewer real-session104ของ A ซึ่งรันแยกบน0cc966f
- PR/develop และ shared DB migration ยังแยกขั้นตอน ไม่ใช่ผลจากการเพิ่ม tests นี้

## คำสั่งและหลักฐาน local

```powershell
./scripts/test-postgres.ps1 -Port 15446
python scripts/test-review-ci-reports.py -v
python scripts/test-role-e-b1-reports.py -v
python scripts/test-role-c-reports.py -v
python scripts/check-role-c-reports.py code/target --output code/target/reject-tag-evidence/summary.json
```

ตั้ง JAVA17_HOME เป็น JDK17 และลบ datasource/Supabase/MAVEN_ARGS overrides ใน child shell ทดสอบ runner สร้าง/หยุด PostgreSQL disposable loopback ของตัวเอง

Log: `code/target/reject-tag-verify.log`; gate red: `code/target/reject-tag-gate-red.log`; source/report evidence อยู่ `code/target/reject-tag-evidence/` (generated ไม่รวม Git)

## ผลที่ E รันใหม่

- Java17.0.20.1 / PostgreSQL18.6 / Maven3.9.16
- Surefire338 + Failsafe312 = **650 Java tests**, failures/errors/skips0
- TagMutationConcurrencyPostgresIT34 ผ่าน รวม12 executions ใหม่อยู่ใน Failsafe312แล้ว
- Python25 ผ่าน (base8 + B/E9 + C8); C gateตรวจ XMLจริงผ่านพร้อม parent B/D/E gate
- `BUILD SUCCESS`, runner exit0; หยุดฐาน disposable แล้ว
- Production/POM ไม่มี diffจาก0cc966f และ `git diff --check` ผ่าน
- เป็นผล local ก่อนจัด commit/push ชุดใหม่; CIต้องอ้างอิง resulting SHA ของชุดนี้ ไม่ใช้ผล CI0cc966fแทน

## ข้อความสำหรับผู้ใช้ส่ง A/D หลังมี SHA/CI

> E เพิ่ม coverage ของ REJECT–tag ต่อจาก0cc966fแล้วครับ ไม่มี production/POM/schema/security changes เพิ่ม12 PostgreSQL executions ครอบคลุมทั้งสองลำดับ, rollback, stale managed PENDING และ old decision tokenหลังresubmit พร้อมเพิ่ม gateขั้นต่ำtag concurrencyเป็น34 ผล local Java17/PostgreSQL650และPython25ผ่าน ไม่มีfailure/error/skip ฝาก A/D ตรวจ deltaและCIของSHAใหม่ โดย Aยืนยันขอบเขตsecurity และ DยืนยันexpectedbehaviorของREJECT/tag/rollbackครับ ผลreviewเดิมของ0cc966fเก็บเป็นฐานเปรียบเทียบ ไม่อ้างเป็นapprovalอัตโนมัติของSHAใหม่
