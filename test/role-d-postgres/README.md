# Role D PostgreSQL integration overlay

## Runner update — 8 October 2026

For the combined branch with D's implemented lock and A's test configuration, run:

```powershell
./scripts/check-role-d-postgres.ps1 -RoleDRef 3187098fac9d91c14d1fdfe40bb765797cde8cfd -IncludePublishingRace
```

The runner preserves existing publishing files, Tool lock/status methods, test config/guard/script and a compatible postgres-it profile. It adds only missing support and the external E fixtures to an isolated clone. If an existing profile is incompatible, it stops for review rather than replacing it. Existing unrelated Maven profiles remain when a missing postgres-it profile is added.

Do not request the old candidate for an already fixed D implementation. If `-ApplyReviewLockCandidate` is accidentally passed, the runner detects the existing review lock and skips the patch. That switch remains available for the pinned pre-fix D snapshot `0a92dbc`.

`-IncludePublishingRace` copies E's current race fixture into Maven test sources so the commit-order/stale-cache cases and local rollback follow-up run explicitly. On an already combined checkout this is a test overlay; its results must be distinguished from the unmodified 3187098 baseline. Existing native PostgreSQL rollback classes continue to run via the preserved profile.

Preparation regression command: `./scripts/test-role-d-runner.ps1`. This uses a Maven stub to test missing/existing support and source preservation; it still creates/stops disposable PostgreSQL clusters on port 15433. Its check count is **not** a Java test-suite count. Run the actual runner command above for application evidence. Both scripts clear and restore inherited MAVEN_ARGS/Supabase process values during execution.

The older instructions and baseline limitations below describe the initial D snapshot; A's test config is already present on 3187098.

ชุดนี้เก็บบน branch E เพื่อทดสอบ D แยก ไม่ใช่โค้ด review service ที่ merge เข้ามาใน E.

## รันซ้ำ

จาก repository root ใน PowerShell:

```powershell
git fetch origin '+refs/heads/*:refs/remotes/origin/*'
./scripts/check-role-d-postgres.ps1
# ระบุ snapshot เดิมเพื่อเทียบผล:
./scripts/check-role-d-postgres.ps1 -RoleDRef 0a92dbc3a80538917bc400d8862d8644c5dd4ec9
```

ต้องมี Git/Maven/JDK/PostgreSQL binaries และไฟล์ test guard/profile/script ของ E ใน checkout นี้ก่อน. ปรับ `-PostgresBin`, `-Port`, `-MavenCommand` ได้เหมือน PostgreSQL script ของ E. ไม่ต้องใช้ Supabase credentials หรือ Docker.

สคริปต์ pin ref เป็น SHA แล้ว clone จาก local object store แบบ detached checkout ใหม่ใต้ `code/target/role-d-checks/`. ไม่ fetch อัตโนมัติและไม่ checkout/merge branch หลัก. เก็บ baseline ก่อนเพิ่ม test overlay โดยล้าง Supabase environment variables ชั่วคราวเพื่อไม่ให้ context test ใช้ฐานร่วม แล้วคืนค่าเมื่อจบ.

หลัง baseline สคริปต์เพิ่มเฉพาะ Maven test profile, test datasource config, guard และ `RoleDPostgresIT.java`. test application.properties คง production defaults ของ D รวม `open-in-view=false` และ override เฉพาะ database/test initialization values; integration test assert database product PostgreSQL ก่อนทำ fixture. ไม่แก้ `src/main` ของ D.

ฐาน PostgreSQL ใหม่มีชื่อเฉพาะเทส, loopback, port ที่ว่าง และ random password; guard ทำงานก่อน initialize SQL ของ integration context. ก่อนจบ script หยุด cluster ของตัวเอง. โฟลเดอร์ checkout/log/cluster เก็บไว้ใน ignored target เพื่อวิเคราะห์ ไม่ลบ recursive อัตโนมัติ.

อ่านผล **baseline และ overlay แยกกัน**: D snapshot ที่ตรวจมี contextLoads ต้องการ DB environment จึง baseline ที่ไม่มี config ได้ 1 error. สคริปต์บันทึก error นั้นแล้วเดินต่อเพื่อตรวจ PostgreSQL; ผล overlay ผ่านไม่ได้แปลว่า unmodified baseline ผ่าน.

## ขอบเขต

- Controller/security filters/session/CSRF/service/repository จริงผ่าน MockMvc; ไม่ mock authentication หรือ review service. ไม่ใช่ HTTP over TCP หรือ browser E2E.
- สมัคร/login ผ่าน API จริง; ADMIN ยกระดับเฉพาะ fixture ในฐานเทสก่อน login. Tool states เตรียมด้วย SQL เพราะไม่ได้รวม E publishing implementation.
- API CRUD, normalization, duplicate, owner/author/admin permissions, hidden tool visibility, delete after hidden, wrong nested IDs, validation, paging/privacy.
- SQL constraints/FK/cascade, batch summary query budget, production Observer AFTER_COMMIT และ rollback.
- ไม่รวม review/publishing race, D+E integration, Flyway rollout, migration บนฐานทีม, Docker หรือ Java17/PostgreSQL17 CI.

เทสเดิมของ D ที่ pin H2 ใน annotation ยังใช้ H2. อย่าอ้างว่า Surefire ทุกกรณีเป็น PostgreSQL; `RoleDPostgresIT` คือชุด PostgreSQL ที่ยืนยันชนิดฐานโดยตรง.
