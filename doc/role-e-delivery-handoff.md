# Role E delivery — เริ่มอ่านไฟล์นี้ก่อน

วันที่ 9 ตุลาคม 2026. Branch ส่งมอบ: `thaninton_673380043-6_02`. ผู้ใช้ให้จัดงานและ push เฉพาะ branch นี้; ไม่มีการ merge develop, เปิด PR, ส่งข้อความแทนทีม หรือรัน migration ฐานทีม.

## ทำอะไรก่อน–หลัง

1. เก็บ WIP และ UI ทดลองเดิมไว้ก่อน แล้วรวมประวัติ dependency จาก `3187098fac9d91c14d1fdfe40bb765797cde8cfd` เข้าสู่ personal branch. เป็นโค้ด C/D เดิมและชุดรวม D/E ไม่ใช่งานใหม่ที่ E อ้างเป็นของตัวเอง. Baseline บน branch ที่รวมแล้วผ่าน Java17: Surefire247 + PostgreSQL107 =354, failures/errors/skips0.
2. ตั้ง JDK17 เป็น runtime หลักของ build/test: Maven Enforcer `[17,18)`, Windows Maven wrapper, default PostgreSQL runner และคู่มือ. ไม่ฝัง path JDK เฉพาะเครื่องลง source ที่ทีมต้องใช้.
3. เพิ่ม B1 เฉพาะ E: `reviewRevision` เพิ่มทุก SUBMIT, approve/reject ต้องส่ง `expectedReviewRevision`, stale409, invalid input400, lock failure503; refresh Tool หลัง lock ก่อนอ่านสถานะ/revision และก่อนแก้ version. เพิ่ม REST/web/security/stale entity/concurrency/rollback/timeout tests และปรับ CI coverage gate.
4. เตรียม migration: read-only inventory ที่ไม่ fallback ข้าม schema, draft B1 SQL, full-schema fixture backup/migrate/rerun/restore/remigrate/app validation และ checksum/runbook/checklist. V7/V8 ยังเป็นชื่อเสนอ ไม่ใช่เลขเวอร์ชันที่อนุมัติสำหรับฐานทีม.
5. ตรวจโค้ดและผลทดสอบจาก personal branch ที่จะส่งจริง แยก commit แล้วส่งชุดนี้ให้ทีมตรวจต่อผ่าน GitHub. UI ทดลองเดิมไม่รวมใน commit ส่งมอบ.

## สิ่งที่ B ต้องทำต่อ — metadata guards

อ่าน [B1 contract](b1-role-e-handoff.md) และ [design](../docs/superpowers/specs/2026-10-08-b1-edit-approval-design.md) ก่อนแก้ `ToolServiceImpl.update`.

1. ใช้ shared Tool row lock + refresh; ตรวจ owner/admin แล้วอนุญาตแก้เฉพาะ DRAFT. Non-DRAFT รวม no-op ต้อง409 INVALID_STATE_TRANSITION; other user403 ทุกสถานะ.
2. ครอบคลุม name/slug/shortDescription/description/categoryId/repositoryUrl ใน transaction เดียว; denied writes ไม่เปลี่ยน metadata/status/revision/viewCount.
3. เปลี่ยน baseline12 cases เป็น persisted acceptance; เพิ่ม session/CSRF และ PostgreSQL ทั้งสอง commit orders/stale managed entity/rollback โดยใช้รูปแบบจาก `ToolApprovalConcurrencyPostgresIT`.
4. ปรับ dashboard/form ให้สอดคล้องกับ server guard; ส่ง SHA และผลทดสอบให้ E/A. อย่า cherry-pick feature commit โดยข้าม dependency/revision contract.

## สิ่งที่ C ต้องทำต่อ — tag guards

1. `TagServiceImpl.assignTag/unassignTag`: Tool lock + refresh, owner/admin, DRAFT-only.
2. Serialize assign/deleteTag ด้วย Tag lock ตาม lock order ที่ตกลง; รักษา in-use409 ไม่ให้ check/delete race ลบ association ใหม่เงียบ ๆ.
3. เพิ่ม persisted permission/status matrix และ PG mutation/SUBMIT races ทั้งสอง orders/stale entity/rollback; ส่ง SHA/ผลให้ E/A.
4. คะแนน browse/`sort=rating` คือข้อ4ของ C ร่วม D แยกจาก B1; ไม่ได้ implement ในชุด E นี้.

## สิ่งที่ A/D ต้องตรวจ

- A: trusted actor, session/CSRF, strict JSON/form revision, ไม่มี approve-by-id fallback, stale/replay/error behavior.
- A/D: lock/refresh/transaction boundaries, commit orders, rollback, compatibility ของ review กับ publishing และ version writes.
- คง review/deprecate rule เดิม: deprecate ชนะก่อนให้ reject create/update; review ชนะก่อนให้เก็บแล้ว deprecate ต่อได้; author/admin ยังลบตามสิทธิ์.
- ผล local/CI ไม่เท่ากับ reviewer approval. ต้องทดสอบโค้ด B/C ที่นำมารวมจริงอีกครั้งก่อนถือว่า B1 ครบ.

**ข้อจำกัดสำคัญ:** metadata/tag paths เดิมยังแก้ PENDING ได้โดยไม่เปลี่ยน revision จนกว่า B/C จะเพิ่ม guards. ชุด E นี้พร้อมให้ review/integrate ไม่ใช่ full B1 content safety หรือ production rollout approval.

## สิ่งที่ DB owner ต้องยืนยัน

อ่าน [runbook](migration-rollout-runbook.md), [checklist](database-rollout-checklist.md) และ [release manifest](sql/migration-release-manifest.json).

- Target/schema/PostgreSQL version, actual migration runner/history/baseline/checksums และ official filenames/order.
- Review oversized data/URL conflicts/missing backfill source, actual V8 same-name constraint definition และ C tag index; หยุดเพื่อแก้ข้อมูลอย่างมีข้อตกลง ไม่ truncate เงียบ.
- Operator/reviewer, backup/retention, staging restore ด้วยข้อมูลจริง, maintenance/write pause และ recovery decision owner.
- V7/V8 ต้อง transaction ทั้งไฟล์; B1 มี BEGIN/COMMIT ของตัวเอง. หยุด old writers และ deploy schema/API พร้อมกันเมื่อพร้อม. ไม่มี shared DB connection/migration ในชุดนี้.

## วิธีให้เพื่อนเริ่มทำงาน

1. `git fetch origin` แล้วตรวจ branch/SHA จากรายการ commit ด้านล่าง. ใช้ source ที่รวม dependency ครบ; อย่า merge develop แทนการ review โดยอัตโนมัติ.
2. Windows: ตั้ง JAVA17_HOME หรือ JAVA_HOME ให้ชี้ JDK17 แล้วรัน `./scripts/mvn-java17.ps1 -version` และ `./scripts/test-postgres.ps1`. Linux/CI ใช้ JDK17 และ `mvn -B -f code/pom.xml -Ppostgres-it verify` กับ disposable DB ที่ guard ยอมรับ.
3. ผลรอบส่งมอบต้องแยก Surefire/Failsafe และ failures/errors/skips พร้อม source SHA. อ่าน [Java17 setup](java17-development.md), [B1 evidence](b1-implementation-test-report.md), [migration evidence](migration-rollout-test-report.md).
4. Workflow **Build and test** ทดสอบ HEAD ใหม่. Workflow **Review and publishing integration** เดิมยังทดสอบ baseline3187098 + overlay; อย่าใช้ผล354/392เก่ารับรอง B1 ใหม่.

## Commit และผลรอบส่งมอบ

- `0d8cb1f`: รวม dependency C/D จาก 3187098; baseline 354 tests ผ่าน.
- `48c8944`: Java17 build/test wrapper และคู่มือ.
- `a67e8b7`: B1 ส่วน E, revision contract, concurrency tests และ CI coverage gate.
- `b775771`: migration inventory, rehearsal, restore และ release manifest. เป็น source SHA ของผลทดสอบรวมด้านล่าง.
- Commit เอกสารส่งต่ออยู่ถัดจากสี่ commit นี้; ตรวจ SHA ล่าสุดด้วย `git log -5 --oneline`.

ผลบน personal branch วันที่ 9 ตุลาคม 2026:

- JDK17.0.20.1; XML reports ทั้ง53 suites ระบุ runtime17.
- Surefire265 + Failsafe PostgreSQL185 = **450 tests**; failures/errors/skips ทั้งหมด0. PostgreSQL local18.6.
- Python coverage-checker tests12 ผ่าน; workflow YAML parse และ `git diff --check` ผ่าน.
- Full-schema migration/backup/restore/remigrate/app startup validation **PASS**; restore/remigrate23.82วินาที บน fixture local.
- หลักฐาน local: `D:/PrimeSkill-worktrees/evidence/delivery/final-verify.log`, `final-coverage.json`; rehearsal `code/target/migration-rollout/bb5284002be24edda20d967dad343caa/summary.json` (ไฟล์ generated ไม่รวม Git).
- GitHub CI ของ742a0fa ตรวจแล้ว completed/success ทั้ง Build and test และ Review and publishing integration; อ่าน [หลักฐาน CI และขอบเขต source](role-e-ci-evidence-2026-10-09.md). ตัวเลข450ข้างต้นเป็นผล local ไม่ใช่การนับ artifacts CI ใหม่.
- ไม่มีการรัน migration กับฐานทีม และไม่มีการ merge develop. B/C guards และ A/D review ยังรอทีม.

## เอกสารพร้อมใช้ระหว่างรอทีม

- [หน้าที่ A–E และข้อความข้อมูลส่งกลับ](team-role-handoff-2026-10-09.md)
- [ชุดตรวจรับ B/C เมื่อส่ง implementation](b1-bc-integration-acceptance.md)
- [ข้อมูล rollout ที่ตรวจจาก repo แล้วและข้อมูลที่ต้องขอ operator](role-e-rollout-preparation.md)

## รับงาน B เพิ่มหลัง742a0fa

รวม B ad58522 และนำ PostgreSQL fixtures49กรณีเข้า Maven แล้ว Local Java17/PostgreSQL18.6 ผ่าน Surefire294 + Failsafe234 =528 ไม่มี failure/error/skip; Python12ผ่าน อ่าน [รายงานรวม B+E](role-e-b-integration-report.md) งาน C tag guards และการ review ชุดรวมใหม่โดย A/D ยังรออยู่ ผล450/CI742a0faข้างบนเป็นหลักฐานชุด E เดิม ไม่ใช่ผลของชุด B+E ใหม่
