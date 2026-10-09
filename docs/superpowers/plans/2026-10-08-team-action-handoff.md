# A/B/C/D/E — เริ่มอ่านไฟล์นี้ก่อน

วันที่ 8 ตุลาคม 2026. เอกสารนี้รวมสถานะหลังอ่านข้อความส่งต่อของแต่ละ Role และตรวจ source/ผลทดสอบจริง. ชุดส่งมอบ E เผยแพร่บน personal branch แล้วที่ 3c2af5a พร้อม runner/tests/CI/เอกสาร; เอกสารนี้ไม่ใช่การอนุมัติ merge หรือรันฐานร่วม.

## สิ่งที่ทำแล้วและไม่ต้องทำซ้ำ

- **Java17 verification (9 ต.ค.):** ติดตั้ง JDK17 ควบคู่กับ26, เพิ่ม Maven Enforcer/Java17 wrapper และ default PostgreSQL runner. E candidate ที่ `D:\PrimeSkill-worktrees\b1-role-e` ผ่าน clean verify บน Java17 จริง450 tests (265+185, failures/errors/skips0) และ migration rehearsal/app validation ผ่าน; ดู `doc/java17-test-report.md` ใน worktree. ยังไม่ commit/push; local PG18.6 ไม่ใช่ CI/PG17 รอบใหม่.

- **B1 รอบทำส่วน E ก่อน:** candidate แยกอยู่ที่ `D:\PrimeSkill-worktrees\b1-role-e` บน `codex/b1-role-e` จาก3187098 ยังไม่ commit/push. อ่าน `doc/b1-role-e-handoff.md` และ `doc/b1-implementation-test-report.md` ใน worktree. E ทำ revision/decision/version paths; B ต้องทำ metadata DRAFT guard และ C ต้องทำ tag DRAFT guard ก่อนถือว่า B1 รวมทีมเสร็จ. ผล CI392 ด้านล่างเป็นงานก่อน B1.

- D นำ Tool row lock/refresh เข้า ReviewService แล้วใน d8c51e3 และ branch D ล่าสุดที่ตรวจคือ e4df8c0. ไม่ apply candidate patch ของ E ซ้ำ.
- Branch `role-de-review-pg-rollback` ที่ `3187098fac9d91c14d1fdfe40bb765797cde8cfd` รวม D+E และการแก้ test config ของ A แล้ว. ไม่ cherry-pick 9b03565 ซ้ำโดยไม่ดู diff.
- E รัน source 3187098 เดิมโดยไม่เติมเทส: Surefire 247 + PostgreSQL 107 = 354, failures/errors/skips = 0. [รายงาน](../../../doc/role-de-3187098-postgres-test-report.md).
- E ปรับ runner ให้รักษา source/config/profile เดิม แล้วตรวจ expanded overlay: Surefire 247 + PostgreSQL 145 = 392, failures/errors/skips = 0. รวม external race 10, D HTTP/service 28 และ native rollback 4+4. [รายงาน](../../../doc/role-d-runner-update-report.md).
- CI ของ 3187098 รันจริงผ่าน 354 และ Docker health/restart persistence. Java runtime17.0.20.1/PostgreSQL17; ตรวจ XML artifacts แล้ว. [รายงานและ run URL](../../../doc/role-de-ci-test-report.md).
- PR #5 เปิดแล้วที่ D e4df8c0 เข้า develop; ยังไม่ merge. Branch รวม3187098เป็นอีก branch ไม่ใช่ head ของ PR #5 ณ เวลาตรวจ.

## ลำดับที่ 1 — E: ส่งชุดเพิ่มเติมและยืนยัน expanded CI

- [x] ตรวจ diff ของ runner, race fixture, CI additions และเอกสาร; แยกไฟล์ทดลองธีม KKU ออกจากชุดนี้.
- [x] Commit/push ลง `thaninton_673380043-6_02`. ระบุ SHA ใหม่ให้ทีมทราบว่าเป็นเทส/runner/CI/เอกสารเพิ่มเติม; implementation race เดิมของ D ไม่ถูกแก้ทับ.
- [x] ดู workflow `Review and publishing integration` ที่ push เรียก. ต้องได้ source SHA ที่ตั้งใจ, baseline/overlay artifacts และ required race coverage ผ่าน. ตรวจ run 37802871848 และ artifacts แล้ว: baseline354/expanded392 ผ่าน, failures/errors/skips=0.
- [x] แนบ run URL, SHA harness/source, Surefire/Failsafe และผล race/rollback ให้ A ตรวจใน [รายงาน CI](../../../doc/role-de-ci-test-report.md); ผู้ใช้ส่งลิงก์นี้ต่อให้ทีมได้. จำนวนจริงอาจเปลี่ยนหาก snapshot เปลี่ยน ให้ใช้ XML ใหม่.

## ลำดับที่ 2 — A ร่วม D/E: ตรวจรับโค้ดรวม

- [x] E ตรวจ source/diff, เชื่อมหลักฐาน lock/rollback/security และเตรียมเส้นทาง PR แล้วใน [รายงานตรวจรับฝั่ง E](../../../doc/role-de-review-readiness.md). ยังไม่ถือว่า A/D approve.

- [ ] A อ่าน baseline354, local expanded392 และ expanded CI เมื่อมีผล โดยแยกหลักฐานตาม source/harness SHA.
- [ ] ตรวจ lock/refresh, ทั้งสอง commit orders, stale managed entity, rollback และ author/admin delete hidden review; อย่าให้ sequential rollback4แทน concurrent4/race10.
- [ ] ตรวจ session/CSRF/error behavior และ diff จากการรวม SecurityConfig/ToolRepository/templates ของแต่ละ Role.
- [ ] D/E ตกลงเส้นทาง PR สำหรับ source รวมและเทสที่จะเก็บในโค้ดทีม. External fixtureใต้ test/ไม่รันเองเมื่อใช้Mavenธรรมดา; ต้องเก็บไว้ในCI/runnerหรือย้ายเข้าtest sourceอย่างตั้งใจ.
- [ ] Reviewer เป็นผู้ตัดสินใจ approve/merge หลังผลครบ. เอกสารนี้ไม่สั่ง merge develop.

## ลำดับที่ 3 — B/E และคนยืนยัน requirement: ตัดสินใจ B1

- [x] เตรียม [implementation plan B1](2026-10-08-b1-edit-approval-implementation.md) ตามคำขอผู้ใช้ เพื่อให้ตรวจก่อนทำ; ยังไม่อนุมัติ policy หรือเริ่ม implementation.

- [x] E ตรวจ behavior ของ B/E/C และเตรียม [ข้อเสนอ policy/API/revision/acceptance ครบชุด](../specs/2026-10-08-b1-edit-approval-design.md). สถานะยังเป็นข้อเสนอรอยืนยัน ไม่ใช่ implementation หรือ acceptance ที่ผ่านแล้ว.

B `4208e45` push แล้ว แต่เป็น baseline tests/ข้อเสนอ ยังไม่เปลี่ยน behavior. Read: [B policy proposal](https://github.com/aarktik/PrimeSkill/blob/4208e4562aaf728caefde4093664a17af0b6411b/doc/role-b-tool-edit-policy-proposal.md).

- [ ] กำหนด owner/admin/other-user outcomes ใน DRAFT/PENDING/PUBLISHED/DEPRECATED รวม 12 กรณี: แก้ได้ไหม, next status, error HTTP/code.
- [ ] ยืนยันขอบเขต name/slug/shortDescription/description/categoryId/repositoryUrl, no-op update และผลจาก tags/version changes ต่อการอนุมัติ.
- [ ] เลือกวิธีผูก approval กับข้อมูลที่adminตรวจ เช่น expected revision ที่ป้องกัน stale approvalหลังแก้+submitใหม่. Row lockอย่างเดียวไม่ยืนยันเนื้อหาที่ผู้ใช้เคยอ่าน.
- [ ] B/E ส่ง API/error contract ให้ A ตรวจ ก่อนเปลี่ยน implementation. B เปลี่ยน baseline testsเป็นacceptance testsและทดสอบกับEบนPostgreSQL.

เรื่อง B1 เป็น metadata-update/approval race คนละเรื่องกับ review/deprecate race ที่ D แก้แล้ว.

## ลำดับที่ 4 — C ร่วม D: คะแนน browse และ sort=rating

- [ ] ใช้ D review summary contract ทำ batch summary; หน้าเปล่าไม่ query และ unrated ใช้ avg=null/count=0.
- [ ] เรียงคะแนนจริงใน DB ก่อน pagination; unrated อยู่ท้ายและมีลำดับรองแน่นอน.
- [ ] ตรวจ create/update/delete reviewแล้วrequestถัดไปคะแนนเปลี่ยน, ไม่มี N+1/duplicate tools เมื่อ filter หลาย tags และไม่เปิดเผย hidden tools.
- [ ] ส่งSHA/ผลทดสอบให้reviewer; ไม่ต้องรอรันV8กับฐานทีมเพื่อเริ่มทำกับฐานdisposable.

## ลำดับที่ 5 — E/DB owner ร่วม B/C/D: migration

- [x] E เตรียม local candidate เพิ่ม (9 ต.ค.): inventory SQL ตรวจ schema fallback, checksum manifest, full-schema fixture backup/migrate/rerun/restore/re-migrate และ actual app validate แบบ SQL init=never. Java450ผ่าน (265+185; failures/errors/skips0), rehearsalผ่าน. ไฟล์อยู่ใน worktree `D:\PrimeSkill-worktrees\b1-role-e`: `doc/migration-rollout-runbook.md`, `doc/migration-rollout-test-report.md`, `doc/database-rollout-checklist.md`. ยังไม่ commit/push และไม่ใช่การอนุมัติฐานทีม; target/history/runner/operator/backup/window ด้านล่างยังต้องยืนยัน.

- [ ] กรอก [database rollout checklist](../../../doc/database-rollout-checklist.md): target/schema/operator/history/version sequence/backup/restore/window.
- [ ] ตรวจoversized commentsและconstraintdefinitionจริง, URL conflictsและcolumn lengthsก่อนรัน. ห้ามtruncateเงียบหรือเดาเลขV7/V8.
- [ ] ทดสอบapproved runnerกับempty DBและสำเนาข้อมูลตัวแทน พร้อมrerun/restore/schema validation.
- [ ] ผู้ดูแลยืนยันrolloutแล้วจึงรันฐานทีม; บันทึกhistory/counts/schemaก่อนหลัง. ตอนนี้ยังไม่ได้เชื่อมต่อหรือรันmigrationกับฐานทีม.

## รูปแบบตอบกลับ

ส่ง owner/task, branch/SHA, สิ่งที่แก้, คำสั่ง/สภาพแวดล้อม, Tests/Failures/Errors/Skipped แยก Surefire/Failsafe, report/run URL และdependency ที่เหลือ. ข้อความในไฟล์นี้เป็นhandoffพร้อมส่ง ไม่ได้ส่งข้อความถึงเพื่อนแทนผู้ใช้.
