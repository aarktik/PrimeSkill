# B metadata coverage gate — งาน E หลัง review ชุด 51d195e

## ที่มาและขอบเขต

ผู้ใช้แจ้งว่า A ไม่พบประเด็นที่ต้องแก้/blocker ในชุด `51d195e` ตามขอบเขตที่ A ตรวจ นี่เป็นผลที่ผู้ใช้ส่งต่อ ไม่ใช่ GitHub PR approval และไม่ใช่การรับรองชุดที่รวม C แล้ว

A ขอให้ E เพิ่ม suites ของ B เข้า coverage gate จึงปรับ checker ที่ CI `Build and test` เรียกอยู่แล้ว โดยไม่แก้ Java services, UI หรือ migration

## สิ่งที่ gate บังคับ

- `com.example.toolhub.ToolMetadataContractPostgresIT`: อย่างน้อย 38 tests
- `com.example.toolhub.ToolMetadataConcurrencyPostgresIT`: อย่างน้อย 11 tests
- ต้องอยู่ใน Failsafe reports; มีเฉพาะ Surefire reports ไม่นับว่าผ่าน PostgreSQL suite
- ใช้ base checker เดิมปฏิเสธ failures, errors, skips และ duplicate reports
- ยอมให้เพิ่ม tests เกินขั้นต่ำ แต่จำนวนที่ลดลงต้องมีการทบทวน gate พร้อมเหตุผล
- เก็บเงื่อนไข D/E เดิมครบ การตรวจนี้บังคับชื่อ suite/จำนวน execution ไม่ใช่ line/branch coverage และไม่รับรองว่าทุกพฤติกรรมถูกทดสอบแล้ว

## ลำดับการทำงาน

1. ตรวจพบว่า B fixtures ทั้งสองอยู่ใน Maven แล้ว แต่ checker ยังไม่บังคับให้มี
2. เพิ่ม regression tests ก่อน เห็น gate เดิมปล่อยผ่าน missing/reduced/wrong-folder suites จน assertions ล้มเหลว
3. เพิ่มข้อบังคับ B ใน `scripts/check-role-e-b1-reports.py`
4. ปรับชื่อขั้น CI ให้แสดง B metadata + E B1; คงการเรียก checker เดิม ไม่เปลี่ยน workflow baseline ของ D
5. รัน Python gate tests และ Java17/PostgreSQL verify พร้อมตรวจ reports จริง

## คำสั่งตรวจ

```powershell
python scripts/test-review-ci-reports.py -v
python scripts/test-role-e-b1-reports.py -v
./scripts/test-postgres.ps1 -Port 15444
python scripts/check-role-e-b1-reports.py code/target --output code/target/b-metadata-gate-evidence/summary.json
```

ตั้ง `JAVA17_HOME` ให้ชี้ JDK17 ก่อนรัน และลบ datasource/Supabase/MAVEN_ARGS overrides ใน child shell ที่ใช้ทดสอบ สคริปต์สร้างฐาน PostgreSQL disposable ของตัวเอง ไม่ใช้ฐานร่วม

## ผลตรวจ local

- Python gate tests: 17 ผ่าน (base checker 8 + B/E checker 9)
- Java17.0.20.1 / PostgreSQL18.6: `BUILD SUCCESS`, exit0
- Surefire294 + Failsafe234 = **528 Java tests**, failures/errors/skips0
- Gate ใหม่ตรวจ XML จริงผ่าน: B contract38 และ concurrency11 ไม่มี failures/errors/skips
- Log: `code/target/b-metadata-gate-verify.log`
- Summary: `code/target/b-metadata-gate-evidence/summary.json`
- ฐาน disposable หยุดแล้ว และไม่มี listener ที่ port15444 หลังจบ
- `git diff --check` ผ่าน
- เป็นผล local ของ working tree ต่อจาก `51d195e` ณ เวลารันทดสอบ ก่อนจัด commit; ยังไม่มีผล GitHub CI ของ gate ใหม่

## สถานะงานทีม

- A: ผู้ใช้แจ้งไม่มีประเด็นในชุด `51d195e`
- D: ยังรอผล review ของชุดนี้
- C: ยังรอ SHA tag guards และผล tests
- E: หลังรับ C ให้รวมและรัน tests/PostgreSQL/CI บน SHA เดียวกัน แล้วส่ง A/D ตรวจอีกครั้ง
- Migration ฐานร่วม: ยังต้อง DB owner อนุมัติแยก

การแก้ gate ในเอกสารนี้เป็นงาน local ต่อจาก `51d195e`; ผล CI ของ `51d195e` ไม่ใช่หลักฐานว่าทดสอบ gate ใหม่นี้แล้ว
