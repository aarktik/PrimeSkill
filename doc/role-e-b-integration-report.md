# E รับงาน B — รายงานรวมและทดสอบ 9 ตุลาคม 2026

## สถานะ

รวมงาน B กับ E และทดสอบ local แล้ว **528 Java tests ผ่าน** ไม่ใช่การรับรอง B1 ครบระบบ เพราะยังไม่ได้รวม tag guards ของ C และยังต้องให้ A/D ตรวจ source รวมใหม่

- E base: `742a0fa40f35499abe452d81b2756eec198cddac`
- B source: `ad58522bd43fd38148a10ea0c457371df54d09be`
- Branch สำหรับตรวจ: `codex/b1-b-e-integration`
- Worktree: `D:/PrimeSkill-worktrees/b1-b-e-integration`
- Implementation/test source tree ที่ใช้รัน: `1ef76df25ecc020745869615519444ef3e7f3a54` เป็น Git tree ไม่ใช่ commit SHA; เป็น staged source ก่อนเพิ่มรายงานนี้
- รายงานนี้จัดส่งพร้อม merge commit ที่มี parents เป็น E742a0fa และ B ad58522; ส่งเฉพาะ personal branch ไม่ merge develop ดู commit ปัจจุบันด้วย git log และผล GitHub CI ของ SHA ที่ส่งจริง

## ทำอะไรก่อน–หลัง

1. สำรอง tracked WIP patch และ untracked docs จาก primary checkout ไว้ที่ `D:/PrimeSkill-worktrees/evidence/b-e-integration/` โดยไม่ย้ายงาน UI ออกจากโฟลเดอร์เดิม
2. สร้าง worktree แยกจาก E742a0fa แล้ว merge B ad58522 แบบไม่ commit เพื่อรักษาประวัติทั้งสองฝ่าย
3. แก้ conflicts4ไฟล์ตามรายละเอียดด้านล่าง
4. นำ PostgreSQL fixtures ของ B เข้า Maven test source เพื่อให้ verify และ CI PostgreSQL job รันได้โดยไม่ต้องใช้ reviewer overlay
5. รัน Python report-gate tests และ Java17 PostgreSQL verify; ตรวจ XML, suite counts และ source tree หลังรัน
6. เก็บ logs, manifest, patch และรายงานนี้ให้ผู้ใช้ตรวจ ก่อนดำเนินการ commit/push ต่อ

## การแก้ conflicts

- `ToolWebController.java`: เก็บ review service/summary และหน้า detail ของ D พร้อมเพิ่ม B editableTool, DRAFT check และ HTML error handlers
- `GlobalExceptionHandler.java`: เก็บฉบับ E ที่มี state/revision/lock/error contracts อยู่แล้ว ไม่เพิ่ม handler ซ้ำของ B
- `ToolRepository.java`: เก็บฉบับ E ซึ่งมี shared findForUpdateById อยู่แล้ว พร้อม browse/tag queries เดิมของ C
- `tools/dashboard.html`: เก็บ E status badge, version/status link และ delete confirmation; เพิ่มเงื่อนไขแสดง edit link เฉพาะ DRAFT

Metadata service ใช้ B shared Tool lock → refresh → owner/admin → DRAFT guard → validation/mutation ใน transaction เดียว Non-DRAFT รวม no-op ถูกปฏิเสธ ไม่เพิ่ม revision/viewCount logic ซ้ำ

## Tests ที่นำเข้า Maven

ต้นฉบับ B ยังเก็บใน `test/role-b-b1/` และนำสำเนาที่ตรวจตรงกันเข้า:

- `code/src/test/java/com/example/toolhub/ToolMetadataContractPostgresIT.java`:38กรณี
- `code/src/test/java/com/example/toolhub/ToolMetadataConcurrencyPostgresIT.java`:11กรณี

ทั้ง49รวมอยู่ใน Failsafe234 ไม่บวกซ้ำเข้าผลรวม528 Existing report gate ของ E ผ่าน แต่ยังไม่บังคับชื่อ/count ของ B โดยเฉพาะ จึงตรวจ XML ของทั้งสอง suites เพิ่มในรอบนี้ เมื่อรวม C ให้ปรับ coverage gate ให้ครอบคลุม acceptance ที่ตกลงร่วมกัน

## ผลทดสอบที่ E รันเอง

- Runtime: Temurin Java17.0.20.1+1, PostgreSQL18.6, Maven3.9.16
- `./scripts/test-postgres.ps1 -Port 15443`: BUILD SUCCESS / exit0
- Surefire:294, failures0/errors0/skipped0
- Failsafe/PostgreSQL:234, failures0/errors0/skipped0
- รวม528 Java tests; XML55 suites ทั้งหมดระบุ java.version17.0.20.1
- Python: test-review-ci-reports8 + test-role-e-b1-reports4 =12ผ่าน
- E B1 coverage checker ผ่าน และตรวจ B contract38/concurrency11 จาก XML ว่าครบไม่มี skip
- `git diff --cached --check` ผ่าน; ไม่มีไฟล์ unmerged
- Runner หยุด cluster ของตัวเองแล้ว และไม่พบ listener ที่15443หลังจบ
- ไม่มีการใช้ Supabase/ฐานทีม และไม่ได้รัน shared migration หรือ Docker/UI smoke ใหม่ในรอบนี้

ผลนี้เป็น local ของ candidate ที่ระบุ ไม่ใช่ผล GitHub CI ของ SHA ใหม่ และไม่รวม reviewer-only28tests ของ A ซึ่งไม่ได้ import ในรอบนี้

## ปัญหาที่พบรอบแรกและวิธีแก้

รอบแรก Surefire294มี1failureที่ ToolHubApplicationTests.contextLoads: spring.datasource.url กลายเป็นค่าว่าง ไม่ถึง Failsafe

สาเหตุจากคำสั่งเตรียม environment ของ E ใน PowerShell รุ่นนี้: `[Environment]::SetEnvironmentVariable(name,$null,'Process')` สร้างตัวแปรค่าว่างแทนลบ จึงทับ H2 test-profile URL ตรวจยืนยันด้วย Test-Path Env: แล้ว

เปลี่ยนเฉพาะคำสั่งเรียก tests เป็น `Remove-Item -LiteralPath Env:<name>` ใน child shell เพื่อลบ override จริง ไม่แก้ production code/test assertions แล้วรัน full verify ใหม่จนผ่าน

ล้างเฉพาะ database/Spring/MAVEN_ARGS overrides ใน child process ตั้ง JAVA17_HOME และให้ disposable runner จัดการ PRIMESKILL_TEST_DB_* ไม่บันทึกค่า secret ในรายงานนี้

## หลักฐาน local

โฟลเดอร์ `D:/PrimeSkill-worktrees/evidence/b-e-integration/`:

- `primary-wip.patch`, `primary-untracked/`: สำรองงานใน primary checkout
- `resolved-integration.patch`, `candidate-manifest.json`, `source-tree.txt`: exact source ที่รวมก่อนทดสอบ
- `first-run-environment-failure.log`, `first-run-context-failure.xml`: รอบแรกที่ environment ผิด
- `final-verify.log`, `final-coverage.json`: รอบสุดท้ายที่ผ่าน

XMLต้นฉบับอยู่ worktree `code/target/surefire-reports/` และ `code/target/failsafe-reports/` ไม่รวม generated logs/XML เข้า Git

## งานต่อไป

1. จัด commit/push ชุด B+E และเอกสารตามคำสั่งผู้ใช้; ตรวจ GitHub CI ของ SHA ใหม่ก่อนส่งต่อ
2. รับ C tag guards พร้อม SHA/ผลทดสอบ ตกลง lock order และรวมบน source เดียวกัน
3. รัน metadata/tag acceptance, PostgreSQL races และ regression ใหม่ พร้อมเก็บ resulting commit SHA และ CI ของ SHA นั้น
4. ส่ง SHA รวมและ diff จาก742a0faให้ A/D ตรวจ; A รับรอง Eเดิมแล้วไม่ได้หมายความว่ารับรองชุด B/C ใหม่โดยอัตโนมัติ
5. Migration/backup/restore ของฐานทีมยังต้อง DB owner approval แยก
