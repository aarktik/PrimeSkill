# หลักฐาน CI ของชุดส่งมอบ E — 9 ตุลาคม 2026

ตรวจจาก GitHub Actions runs/jobs API วันที่ 9 ตุลาคม 2026
Workflow head: `742a0fa40f35499abe452d81b2756eec198cddac` บน `thaninton_673380043-6_02`

## Build and test — source HEAD ใหม่

[Run 37881904965](https://github.com/aarktik/PrimeSkill/actions/runs/37881904965): **completed / success**

- `verify`: Maven verify, Docker image build และ container startup/database restart persistence สำเร็จ
- `postgres-integration`: Maven PostgreSQL verification และ E B1 report gate สำเร็จ พร้อม upload reports
- Workflow `.github/workflows/build.yml` checkout source ของ run ใช้ Temurin Java17 และ PostgreSQL17 Alpine
- Container diagnostics ถูก skip เพราะเป็นขั้นตอน `if: failure()` ไม่ใช่ test ที่ถูกข้าม

นี่เป็นหลักฐาน CI ของ source HEAD ที่มี E B1 ไม่ใช่การอ้างเฉพาะ baseline เดิม

## Review and publishing integration — baseline/overlay

[Run 37881904804](https://github.com/aarktik/PrimeSkill/actions/runs/37881904804): **completed / success**

- `review-publishing` ผ่านทั้ง unmodified combined snapshot, expanded review/publishing suite และ coverage gate
- แม้ workflow head เป็น742a0fa แต่ workflow นี้ทดสอบ combined snapshot3187098 และ external fixture overlay ตาม config ของมัน
- ใช้ยืนยัน regression ของ baseline/overlay ไม่ใช้แทน B1 HEAD verification ข้างต้น

## ขอบเขตของตัวเลข tests

ผล local ที่บันทึกไว้ของ source `b775771` (742a0fa เปลี่ยนเอกสาร) คือ Surefire265 + Failsafe185 =450, failures/errors/skips0, Java17.0.20.1/PostgreSQL18.6 และ Python12ผ่าน

รอบอัปเดตเอกสารนี้ตรวจ conclusion ของ CI jobs/steps แต่ไม่ได้ดาวน์โหลด XML artifacts ใหม่ จึงไม่อ้าง450เป็นจำนวน tests ที่นับจาก CI artifacts และไม่ได้รัน Java tests ใหม่

## ยังไม่ใช่หลักฐานของสิ่งต่อไปนี้

- Reviewer approval จาก A/D หรือ acceptance ของ metadata/tag guards ที่ B/C ยังต้องส่ง
- ผลของ SHA ใหม่หลังรวมงาน B/C
- การผ่าน staging/shared DB migration หรือ production rollout
- ความครบถ้วนของ UI ทุกหน้า

ขั้นต่อไปของ E: ใช้ [ชุดตรวจรับ B/C](b1-bc-integration-acceptance.md) เมื่อได้รับ implementation และเก็บ run/report ของ source รวม SHA ใหม่
