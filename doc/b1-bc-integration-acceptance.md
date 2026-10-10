# B1 — ชุดตรวจรับงาน B/C สำหรับ E

สถานะ: **ข้อกำหนดตรวจรับที่เตรียมไว้ ยังไม่ใช่ผลทดสอบผ่านของ implementation B/C**

อ้างอิง contract ส่งมอบ E ที่742a0fa อ่าน [contract](b1-role-e-handoff.md) และ [หน้าที่แต่ละ role](team-role-handoff-2026-10-09.md) ก่อนเริ่ม

## 1. ข้อมูลที่ E ต้องได้รับก่อนรวม

- [ ] Full SHA และ branch ของ B/C พร้อม base/dependencies
- [ ] รายชื่อ service/controller/template/schema ที่แก้ และ API/error contract ที่เปลี่ยน
- [ ] Suites/results ของ permission/status matrix, concurrency และ rollback
- [ ] ข้อสรุป shared Tool lock + refresh และ Tool/Tag lock order ที่ทุก path ใช้ร่วมกัน
- [ ] รายการงานที่ยังไม่เสร็จหรือยังไม่ได้ทดสอบ

E ต้องตรวจ diff/dependency ก่อนเลือกวิธีรวม ห้ามนำผล tests ของคนละ SHA มารวมเป็นคำรับรอง source ชุดเดียว

## 2. Matrix ที่ต้องตรวจจาก persisted state

ใช้กับ metadata update และแยกตรวจ tag assign/unassign แต่ละ operation:

- DRAFT: owner/admin สำเร็จ; other user403
- PENDING/PUBLISHED/DEPRECATED: owner/admin409 INVALID_STATE_TRANSITION; other user403
- รวม12กรณีต่อ operation; authentication/session/CSRF ให้มี cases เพิ่มตาม route
- no-op นอก DRAFT ต้องปฏิเสธเหมือน mutation ไม่เปิดทาง bypass policy
- อ่านค่าใหม่หลังจบ transaction เพื่อยืนยัน denied request ไม่เปลี่ยน metadata, associations, status, revision หรือ viewCount

Metadata fields ต้องครอบคลุม name/slug/shortDescription/description/categoryId/repositoryUrl รวม unique/FK failure ที่ไม่ทำให้เกิด partial writes

## 3. PostgreSQL commit orders

### Metadata หรือ tag mutation กับ SUBMIT

- [ ] mutation commit ก่อน: SUBMIT ตรวจข้อมูลหลัง mutation แล้วเพิ่ม revision เพียงครั้งเดียว
- [ ] SUBMIT commit ก่อน: mutation ที่รอ lock อ่านสถานะใหม่และถูกปฏิเสธ ไม่เปลี่ยนข้อมูลที่ส่งตรวจ
- [ ] preload managed Tool ก่อนอีก transaction SUBMIT: refresh หลัง lock ต้องไม่ใช้ DRAFT จาก cache เก่า
- [ ] mutation rollback: waiter ดำเนินต่อได้โดยไม่มี metadata/tag changes ที่ rollback หลุดออกมา
- [ ] SUBMIT rollback: mutation ประเมินสถานะ committed จริงและดำเนินตาม DRAFT policy

### Mutation กับ approval/rejection

- [ ] PENDING ไม่เปิดรับ metadata/tag mutation แม้ mutation request เริ่มก่อน decision
- [ ] APPROVE สำเร็จแล้ว mutation ต้องเห็น PUBLISHED และถูกปฏิเสธ
- [ ] REJECT กลับ DRAFT แล้ว mutation อาจสำเร็จได้ตาม owner/admin policy; ต้องไม่ทำให้ revision ย้อนกลับ
- [ ] reject → edit → resubmit: decision ด้วย token ของรอบก่อนต้อง409 STALE_REVIEW_REVISION
- [ ] การ rollback ไม่ทิ้ง state/revision/event ที่ไม่ commit

### Tag assign กับ deleteTag

- [ ] assign commit ก่อน: deleteTag เห็นว่าใช้งานอยู่และได้ in-use409
- [ ] deleteTag commit ก่อน: assign ต้องไม่สร้าง dangling association และไม่รายงานสำเร็จโดยไม่มีข้อมูล; ยืนยัน error contract กับ C
- [ ] rollback ของแต่ละฝ่ายปล่อย lock และไม่ทำให้ association ที่ commit แล้วสูญหาย
- [ ] lock order ตรงกันในทุก path ไม่มี inversion ที่เพิ่ม deadlock โดยไม่จำเป็น

ใช้ PostgreSQL จริงพร้อมตรวจการ block ใน DB เช่น pg_blocking_pids และ timeout ที่จำกัด ไม่ใช้ sleep เพียงอย่างเดียวเป็นหลักฐาน race อ่านตัวอย่าง ToolApprovalConcurrencyPostgresIT และ ReviewPublishingRacePostgresIT

## 4. Regression ของ E/D ที่ต้องคงไว้

- [ ] SUBMIT เพิ่ม revision; reject/deprecate/restore ไม่ reset; overflow ไม่เปลี่ยนข้อมูล
- [ ] strict revision input400, stale409, invalid state409, lock timeout503 เฉพาะ lock failure
- [ ] API/web/session/CSRF ไม่มี approve-by-id fallback หรือ retry token ใหม่เงียบ ๆ
- [ ] version writes ใช้ shared lock/refresh และ policy เดิม
- [ ] review/deprecate ทั้งสอง orders, stale entity และ rollback ยังคงผ่าน
- [ ] UI stale form ได้ error ที่เหมาะสม; server guard ทำงานแม้ส่ง request โดยตรง

## 5. ลำดับตรวจรับโดย E

1. บันทึก SHA เริ่มต้นและ SHA ของ B/C แยกกัน เก็บ WIP/UI เดิมก่อนรวม
2. รวม source บน branch สำหรับตรวจ บันทึก resulting SHA
3. ตรวจ service transaction/locking/refresh และ acceptance test assertions จาก DB
4. รัน Java17 PostgreSQL suite และ coverage gate ตามคู่มือ จาก source ที่รวมจริง
5. ตรวจว่ากรณีใหม่ของ B/C รันจริง รายงาน tests/failures/errors/skips แยก Surefire/Failsafe; gate E ปัจจุบันไม่ครอบคลุม B/C โดยอัตโนมัติ
6. ส่ง resulting SHA, CI run, suite names และข้อจำกัดให้ A/D review
7. ปิดข้อทักท้วงแล้วจึงเสนอรวม develop ตามกระบวนการทีม

จำนวน450เป็น baseline local ของ E ไม่ใช่เป้าจำนวน tests สูงสุด ชุดรวมควรมี cases เพิ่มและต้องระบุ coverage ตามพฤติกรรม

## 6. แบบบันทึกผล — กรอกเมื่อทดสอบจริง

```text
B SHA:
C SHA:
E base SHA:
Integrated SHA:
Java / PostgreSQL:
Metadata matrix / no-op / unique-FK:
Tag assign matrix / unassign matrix:
Mutation-SUBMIT orders / stale entity / rollback:
Decision races / old revision:
Assign-deleteTag orders / rollback / lock order:
E/D regression:
Surefire Tests / Failures / Errors / Skipped:
Failsafe Tests / Failures / Errors / Skipped:
CI URL / artifacts:
Reviewer / reviewed SHA:
Remaining gaps / decision:
```
