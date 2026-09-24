# Role C → Role B: ยืนยัน ownership และ contract ที่ C ใช้ต่อ

> จาก branch `supakron_673380061-4_02` (Role C: Search/Browse/Tags/Strategy)
> สถานะ C: โค้ด + unit tests เสร็จ (`compile` ผ่าน, tests ใหม่ 27/27, regression B 24/24)
> สิ่งที่ไฟล์นี้ต้องการ: ให้ B ยืนยัน ownership และ provisional contract ก่อน merge กลับ `develop`

## 1. Ownership หลังงาน C (กัน route ชน)

| เส้นทาง | เจ้าของ | ไฟล์ |
|---|---|---|
| `GET /tools` (หน้า browse) | **C** | `controller/web/ToolBrowseWebController.java` (ใหม่) — placeholder `list()` ใน `ToolWebController.java` ถูกลบออกในชุดเดียวกันแล้ว |
| `GET /api/v1/tools` (search) | **C** | `controller/api/ToolSearchRestController.java` (ใหม่) — ไม่ชน `GET /api/v1/tools/{idOrSlug}` ของ B |
| `GET /api/v1/tools/{idOrSlug}`, `POST/PUT/DELETE /api/v1/tools/...` | **B เหมือนเดิม** | C ไม่ได้แตะ `ToolRestController` / `ToolService` / ownership logic เลย |

**ขอ B:** อย่าเพิ่ม placeholder `GET /tools` กลับมา — ถ้า B มีงานค้างบน `ToolWebController` ให้ rebase แล้วคงเฉพาะ detail/dashboard/editor ไว้

## 2. ของ B ที่ C ใช้ต่อ (ไม่ได้แก้ behavior)

- `Tool` entity, `ToolResponse` + `ToolMapper` (ใช้ตามเดิม ไม่ได้เพิ่มฟิลด์)
- `CategoryService.findAll()` (dropdown หมวดในหน้า browse)
- `CurrentActorProvider`, `ErrorResponse`/`GlobalExceptionHandler`, `CatalogConflictException`/`ResourceNotFoundException`

**C เพิ่มใน `ToolRepository.java`:** `searchPublished(...)` และ `searchPublishedWithTags(...)` (2 `@Query` + `countQuery`, `EntityGraph(category)`) — ขอให้ B review ว่าไม่ชน query ที่ B จะเพิ่มในอนาคต

## 3. Provisional contract ที่ขอให้ B ยืนยัน (C ใช้ค่านี้ไปก่อน)

| เรื่อง | ค่าที่ C ใช้ | ถ้า B จะเปลี่ยน |
|---|---|---|
| `category` filter | **ID** (`Long`) | บอก C ก่อน merge — C แก้จุดเดียวใน `ToolSearchServiceImpl` + `ToolBrowseWebController` ได้ |
| `tags` filter | **slug** คั่น comma, logic **ANY** | ถ้าจะเอา ID ต้องแก้ `searchPublishedWithTags` + หน้า web |
| `ToolResponse` จะใส่ tags/rating | **ยังไม่ใส่** (คง shape เดิมเพื่อไม่พัง callers ของ B) | ถ้าจะขยาย ให้ตกลง projection/DTO ร่วมกันก่อน อย่าแก้ `ToolMapper.toResponse` ฝ่ายเดียว |
| ผูก tag กับ tool | C สร้าง endpoint แยก `POST/DELETE /api/v1/tools/{toolId}/tags/{tagId}` (ตรวจ owner/admin ใน `TagService`) | ถ้า B อยากให้ผ่าน Tool CRUD เดิมแทน บอก C — C จะย้าย logic ไปโดยคง service authorization ไว้ |

## 4. เกณฑ์รับงาน (acceptance)

- [ ] ไม่มี duplicate/ambiguous mapping (`GET /tools` มีที่เดียว, `GET /api/v1/tools` มีที่เดียว)
- [ ] `ToolWebControllerTest` และ tests ของ B ทั้งหมดยังผ่าน (C รันแล้ว 24/24)
- [ ] ผล search แสดงเฉพาะ `PUBLISHED` เสมอ แม้ query ตรงกับ DRAFT/PENDING/DEPRECATED
- [ ] Pagination มี stable secondary sort ด้วย `id` (อยู่ในทุก Strategy แล้ว)
