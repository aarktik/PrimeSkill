# E รวม B+C — candidate สำหรับตรวจ

## Source และสถานะ

- E/B/UI base: `24ec2ac326cdc97f76c0a37872fe5e6c311edca2`
- C delivery: `4b0fb0e1040c71db20c1bfe5fc20d061f8407614`
- งาน B ที่อยู่ใน base: `ad58522bd43fd38148a10ea0c457371df54d09be`
- Worktree: `D:/PrimeSkill-worktrees/b1-b-e-integration`
- Candidate branch: `codex/b1-b-c-e-integration`
- รวมด้วย `git merge --no-commit --no-ff` ไม่มี merge conflicts; ยังไม่เกิด resulting commit SHA
- Branch หลัก `thaninton_673380043-6_02` ยังอยู่ `24ec2ac` และ preview เดิมยังไม่ใช่ source candidate นี้

## สิ่งที่รวมและ E ตรวจ

### C1: tag mutations

assign/unassign ใช้ shared Tool row lock → refresh → trusted owner/admin → DRAFT guard → Tag row lock → refresh → association write ใน transaction เดียว ลำดับสิทธิ์มาก่อนสถานะ/association รวม no-op นอก DRAFT

deleteTag ตรวจ admin แล้วล็อก Tag ก่อนนับการใช้งาน/ลบ ไม่ล็อก Tool ขณะถือ Tag จากการค้น callers ใน production พบ association writers อยู่ที่ TagServiceImpl เท่านั้น ลำดับ Tool → Tag จึงสอดคล้องกับ publishing/version/review ที่ล็อก Tool ไม่มี path Tag → Tool ใน writers ที่ตรวจ

assign commit ก่อน → delete in-use409; delete commit ก่อน → assign404; คง foreign key และไม่เปลี่ยน metadata/status/reviewRevision/viewCount

### C2: rating browse

query รวม AVG ต่อ Tool, เรียงค่าเต็มความละเอียด DESC NULLS LAST และ id ASC ก่อน pagination; category/keyword/ANY-tags ใช้ count ที่ไม่คูณผลจาก tags Browse โหลด summaries ของหน้าปัจจุบันผ่าน D batch service ไม่มีการเรียก summary เมื่อหน้าเปล่า ใช้ CAST keyword ตาม C เพื่อให้ PostgreSQL รับ null keyword ได้

### งาน E เพิ่มเฉพาะการรวม

- เก็บ gate B จาก24ec2ac และ gate C ที่เรียกต่อจาก B/D/E
- แก้ `scripts/test-role-c-reports.py` เพราะ parent B checker ปฏิเสธ missing B suites ก่อน C checker ข้อความ error จึงเป็น B metadata coverage
- Assertions ยังบังคับ prefix ของ gate ที่ถูกต้องพร้อมชื่อ suite ไม่เปลี่ยนเป็นรับ ValueError ใดก็ได้
- Restore fixture ใน finally เพื่อไม่ให้กรณีล้มเหลวหนึ่งกระทบ subtest ถัดไป
- เพิ่ม gate tests สำหรับจำนวนต่ำกว่าเกณฑ์, report อยู่ผิด folder, และจำนวนมากกว่าขั้นต่ำ
- Production C1/C2 และ Java tests ของ C ไม่ถูกแก้จาก source ที่ส่งมา

## หลักฐานและข้อจำกัด

ผลรันทดสอบ local ของ candidate ที่ E รันใหม่:

- Java17.0.20.1 / PostgreSQL18.6 / Maven3.9.16
- Surefire338 + Failsafe300 = **638 Java tests**, failures/errors/skips0
- `BUILD SUCCESS`, runner exit0; XML60 suites ระบุ Java17.0.20.1 ทั้งหมด
- Python24 ผ่าน: base8 + B/E9 + C7
- B/E gate และ C gate ตรวจ XML จริงผ่านทั้งคู่
- B contract38/concurrency11; C rating9H2+9PG, tag guards35H2+35PG, tag concurrency22 รวมอยู่ในยอด638แล้ว ไม่บวกซ้ำ
- Test port15445 ไม่มี listener หลัง runner หยุดฐาน disposable
- ตรวจ production/test source/POM เทียบ C4b0fb0e ไม่มี diff; ตรวจ manifest C19ไฟล์ที่ไม่ตั้งใจเปลี่ยนตรงกันเมื่อ normalize line endings
- `git diff --check` และ staged diff check ผ่าน
- Log: `code/target/b-c-e-integration-verify.log`; gate summaries: `code/target/b-c-e-integration-evidence/b-e-summary.json` และ `combined-summary.json`
- Python red log: `code/target/c-gate-integration-red.log`; source/tree evidence ใน `code/target/b-c-e-integration-evidence/`

เป็นผลของ working tree ชุดรวมในรอบ E จริง ไม่ใช้ผล638ของ C หรือ CI24ec2ac แทนผลชุดรวม ยังไม่มี resulting commit SHA หรือ GitHub CI ของ candidate

รายงาน A ที่ผู้ใช้ส่งมาไม่มี blocker เฉพาะ51d195e ไม่ใช่ approval ของ candidate นี้ Reviewer-only28ของ A ไม่ได้ import หรือรวมเข้าจำนวน tests ในรอบ E

ชุด C ตรวจ matrix ของ service และ API บางกรณีด้วย mock principal/CSRF ส่วน real login/session, wrong/cross-session CSRF, forged payload และ denied-state invariants ของชุดรวมยังให้ A ตรวจเพิ่มตามหน้าที่ ไม่อ้างผลทดสอบ metadata ของ B ว่าแทน tag endpoints ได้

TagConcurrency ของ C มี22กรณี (submit/approve/stale/rollback/assign-delete/unassign-delete); ไม่อ้างว่าเป็น concurrent REJECT–tag race โดยเฉพาะ ให้ A/D พิจารณาร่วมกับ decision revision regression และ acceptance checklist ก่อนรับรองครบระบบ

ไม่ได้ตรวจ desktop/mobile ซ้ำในรอบ E; ผล browser ใน handoff C เป็นของ C source ส่วน combined tests ตรวจ rendered Browse/API

## ส่งต่อหลังผู้ใช้สั่ง commit/push

1. Commit candidate โดยรักษา merge parents E24ec2ac และ C4b0fb0e แล้วนำ commit ที่ตรวจแล้วเข้า personal branch ของผู้ใช้
2. Push เฉพาะ personal branch แล้วตรวจ CI `Build and test` ของ resulting full SHA; workflow baseline3187098 ไม่ใช้รับรอง HEAD
3. ส่ง SHA เดียวกันพร้อมผลรวมและ CI ให้ A/D
4. A: ตรวจ tag authorization/state/error contracts, session/CSRF และ invariants หลัง denied พร้อม regression B/E
5. D: ตรวจ batch ReviewSummary contract, full-precision rating ordering/unrated/ties และ review–publish/deprecate/rollback regression
6. C: ช่วยทบทวน lock order/rating query เมื่อมี feedback และยืนยัน test gaps ที่ต้องเพิ่ม
7. E: แก้ feedback แล้วรัน/ส่งหลักฐานของ SHA ใหม่ก่อนเสนอ PR/develop ตามข้อตกลงทีม

ไม่มีการส่งข้อความให้ทีม, approve PR, merge develop หรือใช้ฐานร่วม Migration ต้อง DB owner อนุมัติแยก
