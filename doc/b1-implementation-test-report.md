# B1 E implementation — local verification

Delivery update: [รายงานส่งมอบบน personal branch](role-e-delivery-handoff.md) เป็นสถานะล่าสุด. ข้อมูล worktree/commit/push และผลทดสอบด้านล่างที่ระบุรอบก่อนเป็นประวัติการเตรียมงาน.

Java17 update (9 October 2026): see [actual Java17 verification](java17-test-report.md). Candidate moved to `D:\PrimeSkill-worktrees\b1-role-e`. Historical Java26 logs referenced below were archived to `D:\PrimeSkill-worktrees\evidence\before-java17\b1-progress`; clean builds no longer keep them under target.

Base: `3187098fac9d91c14d1fdfe40bb765797cde8cfd`. Branch `codex/b1-role-e`, uncommitted candidate. Logsอยู่ใต้ `code/target/b1-progress/` ในworktreeและไม่เข้Git. ใช้Java26.0.1/PostgreSQL18.6ในเครื่อง; CIใหม่Java17/PG17ยังไม่รันเพราะยังไม่ได้push.

## Red → green

- Baseline: Surefire247ผ่านก่อนแก้ (`baseline.log`).
- `ReviewDecisionIntegrationTest` บนโค้ดเดิม: failures13/errors1 จาก15cases (`decision-red.log`) รวมapproveไม่มีtoken/staleapprovalและcolumnที่ยังไม่มี. หลังแก้decision+serviceผ่าน22cases; ไม่อ้างerrorsจากmissingcolumnเป็นconcurrencyproof.
- Versionfreshness: PGcreate/update/deleteที่preloadDRAFTก่อนSUBMITไม่ถูกปฏิเสธบนโค้ดเดิมทั้ง3cases (`version-red-pg.log`). หลังrefreshผ่านครบ. รอบredมีอีก1fixtureerrorจากouterrollback-only; ปรับfixtureให้rollbackอย่างตั้งใจแล้วแยกจากproductionfailure.
- Migrationdraft: redเมื่อยังไม่มีไฟล์SQL (`migration-red-pg.log`); green6casesตรวจschema/data/rerun/incompatiblecolumn/checkจริงบนPG. นี่เป็นschemafixture ไม่ใช่ฐานทีม.
- Coveragegateใหม่: missing/emptyB1suiteไม่ถูกปฏิเสธในcheckerเก่า; เพิ่มguardแล้วtoolingtestsใหม่4ผ่าน พร้อมเดิม8ผ่าน.

## Full green run

**ผลล่าสุดหลังแก้ preview:** `final-green.log` / `final-coverage-summary.json`: Surefire **265** + PostgreSQL **184** = **449**, failures/errors/skipped **0** ทั้งสองชุด; Maven BUILD SUCCESS. Preview regression 1 ผ่านรวมอยู่ใน265. Coverage checker ผ่าน และ disposable PostgreSQL หยุดแล้ว (ไม่มี listener ที่15432). Tooling12, YAML parse และ `git diff --check` ผ่าน. ผล448ด้านล่างเป็นรอบก่อนเพิ่ม preview regression.

Command: `./scripts/test-postgres.ps1` ผ่านwrapperที่เก็บ/clear/restore SUPABASE_DB_* และ MAVEN_ARGS. Log `full-green.log`; XML snapshot `full-run-reports/`; JSON `full-counts.json` และ `coverage-summary.json`.

- Surefire **264**, Failures0 / Errors0 / Skipped0.
- Failsafe PostgreSQL **184**, Failures0 / Errors0 / Skipped0.
- รวม **448** ในverifyรอบเดียว; Pythontooling12แยกจากJava counts.
- Requiredexistingcoverage: externalrace10, externalRoleD28, nativeconcurrentrollback4, nativesequentialrollback4 ผ่าน.
- E B1: HTTP/filter/webcontract17บนH2และ18บนPG (PGเพิ่มreal locktimeout503), concurrentversion/decision/rollback15, migration6 ผ่าน.
- Concurrencyใช้separateconnections/transactions/latchesและpg_blocking_pids ตรวจwaitจริง. Staleentitycaseยืนยันcachedstateก่อนอีกtransactioncommit. ไม่ใช้H2แทนPGconcurrency.
- 55P03 locktimeoutในlogเป็นcaseที่ตั้งใจทดสอบ; waiter/transactionrollbackและrequestถัดไปสำเร็จ. มีframework/JDK26warningsเดิม ไม่อ้างว่าlogปราศจากwarning.

## ขอบเขตผล

448เป็นผลlocalของEcandidateรวมregressionเดิม ไม่ใช่GitHubCIใหม่และไม่ใช่fullB1acceptance. ยังไม่มีmetadata/tagguardsและapproval-contentraceของB/C; อ่าน [handoff](b1-role-e-handoff.md) ก่อนนำไปใช้งาน. ไม่มีcommit/push/merge/ฐานทีมmigrationในรอบนี้.

## Backup/restore rehearsal

`restore-rehearsal.log`: migration tests 6 ผ่าน ตามด้วย pg_dump/pg_restore บน PostgreSQL disposable. สำรอง fixture tools เดิม 2 แถว (DRAFT/PENDING), apply draft, restore backup ลงฐานจำลองอีกฐาน แล้วตรวจชื่อ/สถานะและ schema เดิมที่ไม่มี review_revision; ผ่านและ cleanup แล้ว. ผลนี้ครอบคลุม representative tools fixture เท่านั้น ไม่ใช่ backup ฐานทีมทั้งฐานหรือการเปิด legacy application.

Draft SQL SHA256: `DEAE24A3782BD730001B370F5E126CCFE5BB26E434A03FCA9960A3E0EEA86552`.

## Fresh code review

Reviewer `/root/review_b1_e` ตรวจ diff แบบ read-only. พบ Important 1 จุด: preview runner ใช้ response revision 0 หลัง SUBMIT ซึ่งเป็น revision 1 แล้ว. เพิ่ม `Sprint3PreviewFixtureTest` ยืนยัน RED ด้วย StaleReviewRevisionException (`preview-red.log`) แล้วแก้ให้รับ response จาก SUBMIT. ไม่พบ Critical/Important เพิ่มในส่วน E ที่ตรวจ. Review นี้ไม่แทน review ของ A/B/C หรือการอนุมัติของทีม.

Follow-up review ยืนยันว่าจุด preview ถูกแก้แล้ว และไม่เหลือ Critical/Important ที่พบในขอบเขต E.

ใช้ผล CI392 เก่ารับรองงานนี้ไม่ได้; GitHub CI, Docker และ Java17/PG17 ยังไม่รันในรอบนี้.
