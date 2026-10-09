# Role C → Role E: ประสาน migration, test DB และ published fixture

> จาก branch `supakron_673380061-4_02` (Role C: Search/Browse/Tags/Strategy)
> สถานะ C: โค้ด + unit tests เสร็จ (`compile` ผ่าน, tests ใหม่ 27/27, regression B 24/24)
> สถานะ E (24 ก.ย. 2026): **ยังไม่เริ่มลงมือ** — ไฟล์นี้จึงเป็นรายการฝากไว้ล่วงหน้า **ไม่บล็อกการ merge งาน C** เพราะตอนนี้ยังไม่มี Flyway/State machine ให้ชนกัน
> สิ่งที่ไฟล์นี้ต้องการ: เมื่อ E เริ่มทำ ให้ E อ่านส่วนที่ 2 แล้วตัดสินใจก่อนตั้ง baseline (C ไม่แตะ State/deploy/CI เอง)

## 1. สิ่งที่ C เปลี่ยนเกี่ยวกับ DB (มีแค่นี้)

| เปลี่ยน | รายละเอียด |
|---|---|
| `code/src/main/resources/schema.sql` | เพิ่มบรรทัดเดียว: `CREATE INDEX IF NOT EXISTS idx_tool_tags_tag_id ON tool_tags(tag_id);` (ของเดิมมีแค่ index `tool_id`) |
| ไม่สร้างตารางใหม่ | `tags`/`tool_tags` มีใน `schema.sql` อยู่แล้ว — entities ของ C map กับตารางเดิมตรง ๆ |
| ไม่แตะตารางอื่น | C ไม่แก้ `tools`, `reviews`, `tool_versions`, ไม่แตะ State `DRAFT→PENDING→PUBLISHED→DEPRECATED` |

## 2. เรื่องค้างที่ขอให้ E ตัดสิน (ห้าม C ตัดสินใจฝ่ายเดียว)

1. **ความยาวคอลัมน์ `tags`:** `schema.sql` ปัจจุบันใช้ `name 100 / slug 120` แต่ Implementation Plan เขียน `80/90` — **C เลือกตาม `schema.sql` (100/120)** เพื่อให้ `ddl-auto=validate` ไม่พัง ขอให้ E/ทีมประกาศค่าทางการก่อนทำ Flyway baseline (ถ้าจะลดความยาวต้องตรวจข้อมูลเดิมก่อน)
2. **เลข migration:** แผนเสนอ V3 สำหรับ tags แต่มี draft `doc/sql/V7__align_existing_tool_catalog.sql` ที่ยังไม่รัน — ขอ E confirm เลขจริงและรวม index ใหม่ของ C เข้า baseline ไปด้วย
3. **`CREATE TABLE IF NOT EXISTS` ไม่แก้ตารางเดิม** — index ใหม่ต้องอยู่ใน forward migration ที่ตกลงร่วมกันสำหรับ DB ที่มีข้อมูลแล้ว อย่ารัน ad hoc กับ production
4. **Test DB profile** (เรื่องเดียวกับที่ขอ A): `mvn verify` เต็มและ repository/integration tests ของ C (search/count ไม่ซ้ำเมื่อ tool มีหลายแท็ก, ไม่หลุด non-PUBLISHED) ต้องใช้ PostgreSQL สำหรับ test (local/Testcontainers) ห้ามชี้ Supabase production ห้าม commit secret
5. **Published fixture:** ขอ flow/seed ที่เปลี่ยน tool เป็น `PUBLISHED` สำหรับเทส browse ด้วยตา (C ไม่เขียนข้าม State ของ E เอง)

## 3. เกณฑ์รับงานร่วม (ทำตอน E เริ่มแล้ว ไม่ใช่ตอนนี้)

- [ ] Flyway baseline/เลข migration ตกลงแล้ว รวม index `idx_tool_tags_tag_id`
- [ ] Migration รันจาก DB ว่างได้ และรันกับ DB เก่าที่มีข้อมูลได้โดยไม่เสียข้อมูล (มี backup ก่อน migrate shared/production DB)
- [ ] `ddl-auto=validate` ผ่านกับ entities ทุก role รวม `Tag`/`ToolTag` ของ C
- [ ] มีวิธีรัน integration tests ของ C กับ test DB (ไม่ใช้ Supabase จริง) และผ่านก่อนประกาศ integration เสร็จ
- [ ] มี published tool จริงสำหรับ smoke test หน้า `/tools` + `GET /api/v1/tools`

> ข้อควรรู้ตอนนี้: search ของ C แสดงเฉพาะ `PUBLISHED` แต่ flow ที่เปลี่ยนสถานะเป็น `PUBLISHED` (submit/approve) เป็นของ E ที่ยังไม่เริ่ม — ดังนั้นช่วงนี้ browse จะว่างเปล่าจนกว่า E จะทำ moderation เสร็จ (หรือตั้ง status ตรงใน DB ตอนเทสด้วยมือ) นี่คือพฤติกรรมตามแผน ไม่ใช่บั๊กของ C
