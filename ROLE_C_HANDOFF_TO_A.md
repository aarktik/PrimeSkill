# Role C → Role A: ขอ review Security/Auth ที่แตะจากฝั่ง C

> จาก branch `supakron_673380061-4_02` (Role C: Search/Browse/Tags/Strategy)
> สถานะ C: โค้ด + unit tests เสร็จ (`compile` ผ่าน, tests ใหม่ 27/27, regression B 24/24)
> สิ่งที่ไฟล์นี้ต้องการ: ให้ A ตรวจสอบและคงไว้ตอน merge security chain กลับ `develop`

## 1. C แก้อะไรในส่วนที่เกี่ยวกับ A

| ไฟล์ | สิ่งที่เปลี่ยน |
|---|---|
| `code/src/main/java/com/example/toolhub/config/SecurityConfig.java` | เพิ่ม public `GET /api/v1/tools` (exact path), `GET /api/v1/tags`, `GET /api/v1/tools/*/tags`; เพิ่ม `/api/v1/admin/tags/**` ให้ `ADMIN` เท่านั้น — `permitAll` คงได้เพราะบังคับ visibility ที่ service แล้ว |
| `code/src/main/java/com/example/toolhub/service/impl/TagServiceImpl.java` | แก้ `findTagsOfTool(toolId, actorId, isAdmin)` ตามรีวิว: `PUBLISHED` อ่านได้ทุกคน, non-`PUBLISHED` ได้เฉพาะ owner/admin ไม่งั้น 404 `RESOURCE_NOT_FOUND` (ลอก `ToolServiceImpl.getByIdOrSlug`) |
| `code/src/main/java/com/example/toolhub/exception/GlobalExceptionHandler.java` | เพิ่ม handler `IllegalArgumentException` → **400 `VALIDATION_FAILED`** (รองรับ sort/page/size ผิดของ search) — scope จำกัดแค่ `controller.api` เหมือนเดิม |

C ไม่ได้แตะ session/CSRF/login/logout/UserDetails/principal ใด ๆ และใช้ `CurrentActorProvider` + `ErrorResponse` เดิมทั้งหมด

## 2. ขอให้ A ทำ/ยืนยัน

1. **คง rules ใหม่ของ C ไว้** ตอน A merge security chain — ถ้า A เขียน `SecurityConfig` ทับ ให้คง 4 บรรทัดนี้ไว้ (listing เปิด public, admin tags ปิด ADMIN)
2. **REST 401 JSON** สำหรับ `POST/PUT/DELETE /api/v1/admin/tags/...` แบบ unauthenticated ต้องเป็น 401 `UNAUTHENTICATED` (ไม่ใช่ redirect login) — ใช้ `RestSecurityExceptionHandler` เดิม
3. **ตรวจ handler ใหม่ไม่ชนงาน A** — ถ้า A มีแผน map `IllegalArgumentException` เป็นอย่างอื่น ให้ตกลงกันก่อน merge (C ใช้ throw นี้สำหรับ `sort`/`page`/`size` ผิดเท่านั้น)
4. **CSRF** — mutation ของ C (`/api/v1/admin/tags`, `/api/v1/tools/{id}/tags/{tagId}`) ใช้ session/CSRF เดิม ไม่ได้ขอข้อยกเว้นใด ๆ ขอให้ security tests ของ A ครอบคลุม path ใหม่ด้วย
5. **Test DB profile** — เรื่องเดิมที่ค้างทั้งทีม (`${SUPABASE_DB_URL}` ยังชี้ production, `ToolHubApplicationTests.contextLoads` error) ทำให้ C รัน `mvn verify` เต็มและ filter-chain integration tests ไม่ได้ ขอให้ A/E จัด `application-test.yml`/Testcontainers ก่อน integration merge

## 3. เกณฑ์รับงาน (acceptance)

- [ ] Anonymous `GET /api/v1/tools`, `GET /api/v1/tags`, `GET /tools`, `GET /api/v1/tools/{id}/tags` (ของ `PUBLISHED`) ได้ 200
- [ ] Anonymous `GET /api/v1/tools/{id}/tags` ของ `DRAFT/PENDING/DEPRECATED` ได้ 404 `RESOURCE_NOT_FOUND` (owner/admin ได้ 200)
- [ ] `size=0` ได้ 400 `VALIDATION_FAILED` (แก้จากเดิมที่ default เงียบเป็น 20 ให้ตรงสเปก `1-100`), หน้า web `sort` ผิด fallback เป็น `newest` ไม่ 500
- [ ] Anonymous/USER `POST /api/v1/admin/tags` ได้ 401/403 (JSON), ADMIN + CSRF ได้ 201
- [ ] USER แตะ admin tags ไม่ได้ 403; mutation ไม่มี CSRF ถูกปฏิเสธ
- [ ] Error shape ทุก status ตรง contract เดิม (มี `code`/`fieldErrors`/`traceId` ตามที่กำหนด)

## 4. ถ้าไม่ตกลงกัน

- ห้ามแก้โดยปิด security หรือให้ client ส่ง `role`/`userId` มากับ request (ขัด architecture rules)
- ถ้า A จะเปลี่ยน error code ของ sort/page ผิด บอก C ด้วย — C มี tests ผูกกับ `VALIDATION_FAILED` อยู่ (`ToolSearchRestControllerTest`, `ToolSearchServiceImplTest`)
