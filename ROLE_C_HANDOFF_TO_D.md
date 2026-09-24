# Role C → Role D: ขอ contract คะแนนรีวิวสำหรับ sort=rating

> จาก branch `supakron_673380061-4_02` (Role C: Search/Browse/Tags/Strategy)
> สถานะ C: โค้ด + unit tests เสร็จ (`compile` ผ่าน, tests ใหม่ 27/27, regression B 24/24)
> สถานะ D (24 ก.ย. 2026): **ยังไม่เริ่มลงมือ** — ไฟล์นี้จึงเป็นรายการฝากไว้ล่วงหน้า **ไม่บล็อกการ merge งาน C** C รวมเข้า `develop` ได้เลยโดยไม่รอ D
> สิ่งที่ไฟล์นี้ต้องการ: เมื่อ D เริ่มทำ ให้ D อ่านส่วนที่ 2 แล้วส่ง contract aggregate มา C จะต่อของจริงให้ใน PR follow-up (C ไม่ทำ review CRUD เอง)

## 1. สถานะปัจจุบัน (ซื่อสัตย์ ไม่ mock คะแนน)

- `sort=rating` เป็นค่าที่ valid (ไม่คืน 400) แต่ `RatingToolSortStrategy.toSort()` **fallback เป็น newest-first ชั่วคราว** เพราะ entity/service รีวิวยังไม่ merge — มี TODO ในโค้ดระบุไว้ชัดเจน
- หน้า `/tools` แสดงข้อความ **“ยังไม่มีรีวิว”** ทุก card ไปก่อน (ตาม UI guide กรณีไม่มีรีวิว)
- C **ไม่**สร้าง review entity, ไม่คำนวณคะแนนจำลอง, ไม่แตะตาราง `reviews` ใน `schema.sql`

## 2. ขอให้ D ส่งมา (เมื่อ D merge แล้ว)

1. **วิธีอ่าน aggregate ต่อ tool:** ชื่อ service method / DTO ที่ให้ `avgRating + reviewCount` (เช่น `ReviewSummary(toolId, avgRating, reviewCount)`) — C จะเรียกต่อ ไม่ query ตาราง `reviews` ตรง
2. **Query สำหรับ sort ที่ DB level:** C ห้ามดึงทั้งหมดมา sort ใน Java — ขอวิธีเรียงตามคะแนนที่ database (เช่น join subquery/projection ใน `ToolRepository` หรือ method ที่ D เตรียมไว้) โดยไม่เกิด N+1
3. **กรณีไม่มีรีวิว:** ยืนยันว่า aggregate คืน `null`/0 และตกลงว่า tool ไม่มีรีวิวควรอยู่หัวหรือท้ายเมื่อ `sort=rating`
4. **Performance:** ถ้า D มี index/aggregate แนะนำบน `reviews(tool_id)` บอกมาด้วย

## 3. สิ่งที่ C จะทำให้ต่อเมื่อได้ contract (PR follow-up หลัง D merge)

- [ ] แก้ `RatingToolSortStrategy` ให้ sort ด้วยคะแนนจริงที่ DB + คง `id` tie-break
- [ ] โชว์คะแนน/จำนวนรีวิวจริงบน tool cards หน้า `/tools` (แทน “ยังไม่มีรีวิว”)
- [ ] เพิ่ม tests: sort ตามคะแนน, tool ไม่มีรีวิว, คะแนนเท่ากันแล้วลำดับคงที่
- [ ] อัพเดต Swagger example ของ `sort=rating`

> หมายเหตุ: จนกว่า D จะ merge ข้อ 3 ทั้งหมดถือว่า parked — `sort=rating` ยัง valid แต่ให้ผลเท่า newest ตามที่ tests ล็อคไว้ (`ToolSearchServiceImplTest`, `ToolSortOptionTest`)

## 4. เกณฑ์รับงานร่วม (acceptance)

- [ ] `GET /api/v1/tools?sort=rating` เรียงตามคะแนนจริงที่ DB (ไม่ sort ใน Java)
- [ ] Tool ไม่มีรีวิวแสดง “ยังไม่มีรีวิว” และมีตำแหน่งที่ตกลงกัน
- [ ] ไม่มี N+1 เมื่อ browse หน้าที่มีหลาย tool (ตรวจด้วย `EntityGraph`/join fetch/projection)
- [ ] C ไม่ต้องแก้ตาราง `reviews` และ D ไม่ต้องแก้โค้ด search ของ C ฝ่ายเดียว
