# ผลตรวจ local runtime ของทีม — 2026-10-10

## ชุดโค้ดและความหมายของผล

- SHA ที่ทุก role ใช้อ้างอิง: `fc9421a086ecf7fabb9bd8ca7d246e843e64cff7` (develop หลังรวม PR #6)
- ผลแต่ละเครื่องเป็น local acceptance แยกกัน ไม่ใช่การรับรอง deployment หรือฐานข้อมูลร่วม
- E ตรวจผ่าน Codex in-app browser ที่ `http://127.0.0.1:18081/login`
- Runtime E: Java 17.0.20.1, PostgreSQL 18.6 local, ฐานแยกจาก Supabase
- เอกสารนี้อยู่บน branch ส่วนตัว HEAD d41f26b ขณะเขียน; SHA ของแอปที่ตรวจเป็น fc9421a ตาม manifest ของ local runtime ไม่ใช่ HEAD ของเอกสาร

## B — ผลที่ได้รับจากผู้ใช้

B รายงาน local จาก fc9421a ด้วยฐานแยก: ผ่าน 96/96 ครอบคลุม HTTP/session/CSRF, HTML forms และ persisted-state checks ไม่พบปัญหาในขอบเขตที่ตรวจ ไม่มีแพตช์เดิมต้องส่งซ้ำ

E บันทึกตามรายงานที่ส่งมา ยังไม่ได้รับ raw logs หรือรายละเอียด runtime ของ B ในข้อความนี้ ไม่ได้นำ 96 มารวมเป็นจำนวนกรณีที่ E รันเอง และผล B ไม่ครอบคลุม browser visual/JavaScript หรือระบบ 18081 ของ E

## E — ขั้นตอนที่ลงมือตรวจผ่าน browser รอบนี้

1. Login owner: เข้าหน้าเครื่องมือของฉันสำเร็จ เห็นรายการของ owner
2. เปิดแก้ไขแบบร่างจาก dashboard: ค่าเดิมปรากฏในฟอร์ม
3. ล้างชื่อแล้วส่งฟอร์ม: แสดง `must not be blank` และเก็บ slug/คำอธิบาย/หมวดหมู่เดิมไว้ ไม่มีการบันทึกชื่อว่าง; ยกเลิกกลับ dashboard เห็นชื่อเดิม
4. เปิดเวอร์ชันของแบบร่าง: เห็น 1.0.0, ลิงก์เพิ่ม/แก้ไข และปุ่มส่งให้ตรวจ (ยังไม่ได้กดเปลี่ยนสถานะ)
5. กดเมนู: JavaScript เปลี่ยน collapsed เป็น expanded และแสดงลิงก์นำทาง
6. เปิด Browse ผ่านเมนู: รายการเผยแพร่ปรากฏ
7. เลือกคะแนนสูงสุดแล้วใช้ตัวกรอง: URL เป็น sort=rating; รายการคะแนน 4.5/2 รีวิวอยู่ก่อนรายการไม่มีรีวิว
8. เปิดรายละเอียด published: คะแนน 4.5/2 รีวิวตรงกับ Browse และ owner เห็นข้อความห้ามรีวิวของตัวเอง
9. Logout owner แล้ว login member: dashboard แสดง Local member draft ของ member ไม่แสดงรายการของ owner
10. Logout member แล้ว login admin: เมนูแสดงคิวอนุมัติและเปิดหน้าได้; ปัจจุบันคิวว่าง จึงยังไม่ได้ตรวจ approve/reject ผ่าน browser
11. ค้นหา e-no-match-20261010: แสดง 0 รายการพร้อมข้อความไม่พบ; กดล้างตัวกรองแล้วกลับมาเห็น 2 รายการ
12. เปิดรีวิวของฉันด้วย admin: เห็นรีวิว 4/5 ของ admin และลิงก์กลับ Browse ใช้งานได้; ไม่ได้กดลบ
13. Logout admin: กลับหน้าล็อกอิน ทิ้ง browser ไว้ในสถานะออกจากระบบ

Console ที่อ่านระหว่างตรวจหลังเปิด admin queue ไม่พบ warn/error ที่เครื่องมือเก็บได้ ไม่ใช่การรับรองว่า JavaScript ทุกเส้นทางไม่มีข้อผิดพลาด

## ภาพหลักฐาน E (local-only)

เก็บใน `D:/PrimeSkill-local-team/evidence/browser-2026-10-10/` ไม่ใส่บัญชีหรือรหัสผ่านใน Git:

- 01-owner-dashboard.png
- 02-form-validation.png
- 03-versions.png
- 04-rating-browse.png
- 05-owner-detail.png
- 06-member-dashboard.png
- 07-admin-queue.png
- 08-my-reviews.png

ภาพ 02/04/07/08 ได้เปิดดูประกอบการตรวจ ไม่ใช่อ้างอิงเฉพาะ DOM

## ข้อสังเกตและงานต่อ

- หน้า /my/reviews เป็นหน้าเดี่ยว ไม่มี header/menu/footer ร่วม และปุ่มใช้รูปแบบพื้นฐานต่างจากหน้าอื่น ทางกลับ Browse ยังใช้งานได้ ให้ D/E ประสานปรับ template ตามธีมที่ทีมอนุมัติ
- Validation ชื่อว่างแสดงภาษาอังกฤษ `must not be blank` บนหน้าภาษาไทย ให้ B/A ประสานข้อความภาษาไทยและการผูก error กับช่องกรอก
- Fixture slug local-team-pending ปัจจุบันแสดงสถานะเผยแพร่แล้ว และคิว admin ว่าง อย่าอนุมานสถานะจากชื่อ fixture; หากจะตรวจ approve/reject ให้สร้างรายการทดสอบใหม่แล้วส่งตรวจอย่างตั้งใจ
- รอบนี้ไม่มีการสร้าง/ลบเครื่องมือ รีวิว หรือเปลี่ยนสถานะ; การเปิดรายละเอียดอาจมีผลกับ viewCount ตามพฤติกรรมแอป

## ผู้รับงานต่อ

- A: ส่งผล local login/session/CSRF และ authorization ของ SHA นี้ พร้อม environment และขั้นตอนทำซ้ำเมื่อพบปัญหา
- B: ส่วนรายงาน 96/96 รับทราบแล้ว; ขอ runtime/logs หรือ checklist แนบเมื่อพร้อม และพิจารณาข้อความ validation ภาษาไทย ไม่ต้องส่งแพตช์เดิมซ้ำ
- C: ส่งผล local browse/search/filter/rating/tag guards ของ SHA นี้
- D: ส่งผล local create/edit/delete review และกติกาสถานะ; ประสานหน้ารีวิวของฉันกับ E
- E: รวบรวมผล A/C/D และติดตามข้อสังเกต UI; ตรวจ publishing ผ่าน browser ด้วยรายการทดสอบเฉพาะเพิ่มเติมก่อนอ้างว่าครบ flow

## ข้อจำกัดที่ยังต้องตรวจ

ไม่ใช่ exhaustive end-to-end: ยังไม่ได้ตรวจ browser ทุกหน้า ทุกขนาดจอ ทุกสิทธิ์แบบ URL ตรง, keyboard/screen reader, การสร้าง/แก้ไขสำเร็จ, การลบ, approve/reject/deprecate, concurrent requests หรือ security suite รอบใหม่ ผล CI/review เดิมแยกจากผล browser นี้

Docker และ migration ฐานร่วมไม่ได้ทำในรอบนี้ Migration ฐานร่วมต้องได้รับอนุมัติจาก DB owner; ไม่จำเป็นต่อ local acceptance ที่ใช้ฐานแยก

เอกสารยังไม่ได้ commit/push และไม่ได้ส่งข้อความถึงเพื่อนโดยตรง

## รอบเพิ่มเติม E — ตรวจต่อโดยไม่รอ A/C/D

ใช้ SHA/runtime เดิม สำรองฐาน local ก่อนเริ่ม และสร้าง disposable tool ใหม่เฉพาะรอบ (`id=8`, slug `e-runtime-20261010-887921`) ไม่ใช้เครื่องมือที่ผู้ใช้สร้างไว้เพื่อเปลี่ยนสถานะหรือลบ

### Browser flow ที่ตรวจเพิ่ม

1. รหัสผ่านผิด: แสดงข้อความไทยว่าอีเมลหรือรหัสผ่านไม่ถูกต้อง จากนั้น login ด้วยรหัสที่ถูกต้องได้
2. Owner สร้างเครื่องมือใหม่ผ่าน HTML form: แสดง DRAFT และข้อความสร้างสำเร็จ
3. Owner เพิ่มเวอร์ชัน 0.1.0 แล้วแก้ release notes: ค่าที่แก้ปรากฏหลังบันทึก
4. ส่งให้ตรวจ: เป็น PENDING และซ่อนปุ่มเพิ่ม/แก้/ลบเวอร์ชัน
5. Admin เห็นรายการใหม่ในคิวและกดส่งกลับ: owner กลับมาเห็น DRAFT
6. Owner ส่งตรวจซ้ำ และ admin อนุมัติ: รายการปรากฏบน Browse ของ member
7. Member สร้างรีวิว 5 คะแนนผ่านฟอร์ม: summary เป็น 5.0/1 รีวิว; ข้อความ `<script>alert(1)</script>` แสดงเป็นข้อความ ไม่เกิด dialog ในกรณีนี้ (ไม่ใช่ exhaustive XSS test)
8. Member แก้รีวิวเป็น 3 คะแนน: summary เป็น 3.0/1 รีวิวและข้อความแก้ไขปรากฏ
9. Owner เลิกเผยแพร่ผ่านปุ่ม: เป็น DEPRECATED และซ่อนการแก้เวอร์ชัน
10. Member เปิด URL รายละเอียดเดิม: แสดงไม่พบรายการ ส่วน Browse ไม่แสดงเครื่องมือที่ deprecate แล้ว
11. Member เปิดรีวิวของฉัน: ยังเห็นรีวิวของตัวเองสำหรับ tool ที่ถูกซ่อน และลบ disposable review ได้; รีวิวเดิม tool #3 ยังอยู่
12. Owner กู้คืนเป็น DRAFT: ปุ่มจัดการเวอร์ชันกลับมาและลบเวอร์ชันทดสอบได้
13. ลอง submit เมื่อไม่มีเวอร์ชัน: ระบบอนุญาตให้เป็น PENDING ปัจจุบัน service ไม่มีกฎว่าต้องมีเวอร์ชันก่อน submit จึงบันทึกเป็นพฤติกรรมที่พบ ไม่ถือเป็น bug หรือเพิ่มกติกาเอง
14. Owner ลบเฉพาะ tool ทดสอบรอบนี้ แล้ว logout สำเร็จ

บาง click ได้ timeout จากเครื่องมือ browser แต่เมื่ออ่านสถานะใหม่พบว่าคำขอสำเร็จแล้ว จึงไม่กดซ้ำ ภาพและผลฐานข้อมูลใช้สถานะหลังคำขอจริง

### HTTP/session/CSRF/guards ที่ E รันเอง

ผ่าน **45/45 checks** ด้วย Python standard library ส่ง HTTP จริงไป local runtime:

- health และ anonymous denial
- CSRF หาย/ผิดบน login ถูกปฏิเสธ, รหัสผ่านผิดถูกปฏิเสธ
- login ทั้ง 3 บัญชี, role USER/ADMIN, session ID เปลี่ยนหลัง login และ profile อ่านได้
- owner/member เข้า admin API ไม่ได้; admin เข้าได้
- DRAFT ซ่อนจาก anonymous/non-owner แต่ owner/admin อ่านได้
- metadata ไม่มี CSRF และ non-owner edit ถูกปฏิเสธ
- non-owner assign/unassign tag ถูกปฏิเสธ; non-DRAFT tag mutations ได้ 409 รวม no-op
- ตรวจ name/slug/status/reviewRevision ของ DRAFT เดิมหลังคำขอที่ถูกปฏิเสธ
- เจ้าของรีวิวเครื่องมือตัวเองไม่ได้, member สร้างรีวิวซ้ำไม่ได้
- rating นอกช่วง, comment 2,001 ตัว, page ติดลบ และ size 101 ถูกปฏิเสธ
- logout และอ่าน profile หลัง logout ถูกปฏิเสธทั้ง 3 บัญชี

Runner อยู่ local-only ที่ `D:/PrimeSkill-local-team/check-runtime-api.py` อ้าง fixture ของเครื่อง E โดยเฉพาะ ไม่ใช่ portable runner สำหรับเครื่องเพื่อน ผลที่ไม่มี credentials อยู่ `D:/PrimeSkill-local-team/evidence/browser-flow-2026-10-10/api-results.json`

จำนวน 45 เป็นจำนวน assertions/checks ของ runner ไม่ใช่ JUnit 45 tests และไม่รวม B 96/96 ไม่มีการรัน full Java/CI/concurrency suite ใหม่ในรอบนี้

### หลักฐานและข้อมูลหลังตรวจ

ภาพ 01–12 และ browser-run.json อยู่ `D:/PrimeSkill-local-team/evidence/browser-flow-2026-10-10/`

ตรวจ SQL แบบอ่านอย่างเดียวหลัง cleanup: tool #8, versions และ reviews ของ tool #8 เหลือ 0 แถว; fixture #1–5 ยังอยู่ตามสถานะก่อนรอบนี้ และ review tool #3 ยังเป็น 5/4 คะแนนพร้อมข้อความเดิม รายละเอียดที่เปิดอ่านอาจกระทบ viewCount ตามพฤติกรรมแอป

### พบเพิ่มเติม: navbar บนหน้า error แสดงสถานะ login ผิด

- วิธีทำซ้ำ: member login → เปิด URL tool ที่ DEPRECATED ของ owner → เปิดเมนู
- พบลิงก์เข้าสู่ระบบ/สมัครสมาชิกแทนเมนูบัญชี ทั้งที่เปิด Browse และรีวิวของฉันต่อได้โดยไม่ login ใหม่
- ขอบเขตที่พบเป็นการแสดงเมนูผิด ไม่ใช่หลักฐานว่า session หลุดหรือ bypass สิทธิ์ได้
- แนวตรวจสาเหตุ: WebLayoutAdvice ใส่ signedIn/isAdmin/viewerId ให้ model ของ controller ปกติ แต่ exception handler ที่คืน versions/error ต้องตรวจการเติม model อีกครั้ง; อย่าแก้ด้วยการแสดงเมนูสมาชิกให้ทุกคน
- ให้ E/A เพิ่ม regression ของหน้า error สำหรับ anonymous/member/admin ก่อนแก้

### แพตช์ UI/Docker ที่เสนอ ยังไม่ลงมือแก้

- reviews/mine.html: ใช้ shared navbar/footer, role-e.css/role-e.js และ class ของธีมเดิม โดยคง CSRF, pagination, ข้อจำกัดไม่เปิดรายละเอียด tool ที่ซ่อน และสิทธิ์ลบ
- ข้อความชื่อเครื่องมือว่าง: เปลี่ยนเฉพาะหน้าเว็บเป็นไทย โดยไม่เปลี่ยน API error contract
- Docker มี Dockerfile Java17 และ Compose PostgreSQL อยู่แล้ว ไม่ต้องสร้างซ้ำ; ปรับ Compose local ให้ bind localhost เพิ่ม app readiness และคู่มือบัญชีทดสอบ/volume/backup พร้อมยืนยัน source SHA
- ต้องทดสอบ Docker จริงก่อนอ้างว่าใช้งานได้ ใน session นี้ยังไม่พบคำสั่ง docker บน PATH และไม่ได้ติดตั้ง Docker หรือเปลี่ยน network exposure
- แบบของแพตช์ส่งให้ผู้ใช้ยืนยันผ่านคำถามแล้วตาม skill brainstorming; ระหว่างรอได้ทำ runtime checks ด้านบนครบแล้ว

ผลตรวจเพิ่มเติมทั้งหมดเป็นของ E ไม่เปลี่ยนสถานะเป็น A/C/D approved อัตโนมัติ และยังไม่ใช่การรับรองระบบ deploy หรือ migration ฐานร่วม

## Follow-up หลังผู้ใช้สั่งแก้ UI

ผู้ใช้ยืนยันให้แก้ UI 3 จุดแล้ว รายละเอียดแพตช์และผล QA ใหม่อยู่ รายงาน ui-three-fixes-2026-10-10.md (ภายหลังย้ายไป backup local ตามหัวข้อย้อน UI ด้านล่าง) ระบบ 18081 เปลี่ยนเป็น preview ที่มี uncommitted UI changes แล้ว; ผล 45/45 และ browser flow ด้านบนยังอ้างฐาน fc9421a รอบก่อนแพตช์ ไม่ใช่ผลรับรองซ้ำของ source ใหม่ Docker ยังไม่ได้ปรับในคำขอ UI รอบนี้

## ย้อน UI ตามคำขอผู้ใช้ — 10 ตุลาคม 2026

ผู้ใช้ให้เพื่อนรับงาน UI ต่อ จึงย้อนแพตช์ UI 3 จุดล่าสุดทั้งหมด (7 product files) กลับ HEAD d41f26b ซึ่งมี source tree ตรงกับ develop fc9421a ไม่มีการย้อน feature/backend ที่ commit ไว้ก่อนหน้า รายงาน UI และแพตช์เก็บสำรอง local-only ใน D:/PrimeSkill-local-team/runtime/ui-reverted-2026-10-10/ ข้อความ UI follow-up ก่อนหน้านี้เป็นประวัติ ไม่ใช่สถานะ runtime ปัจจุบัน ทั้งสามจุดกลับเป็นงานที่ต้องส่งต่อให้ผู้ทำ UI

## รับ UI ใหม่จาก C เพื่อ integration — 10 ตุลาคม 2026

สถานะล่าสุดแทนข้อความย้อน UI ก่อนหน้า: รับ C f7ecd1d เข้า branch E ด้วย merge --no-commit แล้ว เปิด JAR ชุดรวมที่ 18081 พร้อมข้อมูล local เดิม ผล Java712/Python25 และ coverage gate ผ่าน พร้อม manual browser smoke; รายละเอียดอยู่ role-e-c-ui-integration-2026-10-10.md ยังไม่มี SHA รวมใหม่/commit/push/CI และยังไม่ deploy

