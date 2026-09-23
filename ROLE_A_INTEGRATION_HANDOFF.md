# Role A Integration Handoff — สิ่งที่ต้องทำเพื่อรองรับ B

> เอกสารนี้สรุปสิ่งที่ Role A ต้องจัดทำหลังจาก review งาน B  
> อ่านร่วมกับ `IMPLEMENTATION_PLAN.md`, `ROLE_B_TOOL_CATALOG.md` และ `LUNA_ROLE_B_IMPLEMENTATION.md`  
> ขอบเขต: User/Auth, security, current actor และ shared error contract เท่านั้น

## สรุปสถานะ

งาน B มี Entity/Repository/Service/DTO/Mapper/REST/Web foundation แล้ว แต่ยังเชื่อมกับระบบจริงไม่ได้เต็มที่เพราะ A contract ยังไม่มีใน source ปัจจุบัน

ปัญหาหลักที่ต้องแก้โดย A:

1. B controller ยังไม่สามารถอ่าน user id จาก authenticated principal ได้อย่างเป็นทางการ
2. `AccessDeniedException` และ conflict exceptions ยังถูก global handler แปลงเป็น 500 แทน 403/409
3. ไม่มี session security policy, route authorization, CSRF configuration และ method security ที่ใช้งานจริง
4. `@SpringBootTest` เชื่อม Supabase โดยตรงและไม่มี test profile/database แยก
5. schema/migration ของ users/profiles และ tools ต้องใช้ baseline เดียวกันก่อน `ddl-auto=validate`

ห้ามแก้โดยการให้ B รับ `ownerId` จาก request/header หรือปิด security เพื่อให้ test ผ่าน

## 1. Current-user contract ที่ B ต้อง consume

สร้าง abstraction กลางที่ทุก controller/service ใช้ร่วมกัน เช่น:

```java
public interface CurrentUserProvider {
    Long requiredUserId();
    Optional<Long> userId();
    boolean isAuthenticated();
    boolean isAdmin();
}
```

หรือใช้ `AuthenticatedUserPrincipal` ที่มี contract ชัดเจน เช่น `getId()` และ role/authorities แต่ต้องประกาศเป็น shared contract และให้ทุก roleใช้รูปแบบเดียวกัน

### Acceptance criteria

- B ไม่ต้องใช้ reflection เพื่อเรียก `getId()` จาก principal
- anonymous request ที่เรียก `requiredUserId()` ได้ 401/403 ตาม policy โดยไม่คืน 500
- authenticated USER ได้ id จริงจาก database user
- ADMIN ระบุผ่าน authority มาตรฐาน `ROLE_ADMIN`
- ห้ามเชื่อ `ownerId`, `userId` หรือ `role` จาก JSON, query parameter, form หรือ custom header
- Web controller และ REST controller ใช้ provider/principal contract เดียวกัน

### จุดที่ต้องเชื่อมกับ B

ไฟล์ B ที่ปัจจุบันรับ actor id/สะท้อน principal:

- `ToolService` / `ToolServiceImpl`
- `ToolRestController`
- `ToolWebController`
- category admin controller

A ควรเสนอ adapter ที่ทำให้ B เปลี่ยนจาก reflection เป็น dependency กลางได้ โดยไม่ย้าย ownership ของ Tool Catalog

## 2. Spring Security session baseline

ตั้ง security configuration ตามแผนกลาง:

- session-based authentication; ไม่เพิ่ม JWT flow ใหม่
- BCrypt password encoder และ UserDetailsService จาก `users`
- session fixation protection หลัง login
- `HttpOnly`, `SameSite=Lax`, `Secure=true` ใน production
- CSRF เปิดสำหรับ Thymeleaf/session mutations
- logout เป็น POST พร้อม CSRF; invalidate session
- CORS same-origin/restricted; ห้าม wildcard credentials
- method security เปิดใช้ที่ service boundaryเมื่อเหมาะสม

### Route matrix ที่ต้องทดสอบ

| Route/group | Anonymous | USER | ADMIN |
|---|---:|---:|---:|
| static/home/public category/public published tool | allow | allow | allow |
| public browse/detail | allow published only | allow | allow |
| create/update/delete own tool | deny | own only | allowedตาม policy |
| profile | deny | own only | own profile |
| review create | deny | allow | allow |
| `/api/v1/admin/categories/**` | deny | deny | allow |
| approve/reject/publishing admin actions | deny | deny | allow |

Backend ต้องตรวจ ownership/admin ซ้ำ แม้ controller จะซ่อนปุ่มหรือ route แล้วก็ตาม

## 3. Shared ErrorResponse contract

ปัจจุบัน `ErrorResponse` มีเพียง `status`, `message`, `timestamp`, `path` และ handler จับ exception กว้างเกินไป. ปรับ shared contract ให้ตรง baseline:

```json
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "code": "ACCESS_DENIED",
  "message": "You do not have permission to perform this action",
  "path": "/api/v1/tools/1",
  "fieldErrors": [],
  "traceId": "optional-correlation-id"
}
```

`fieldErrors` ต้องมี field/message แยกทุก validation error ไม่ใช่คืนเฉพาะ error แรก

### Exception mapping ที่ต้องมี

| Exception | HTTP | Code |
|---|---:|---|
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` |
| malformed JSON/type mismatch | 400 | `MALFORMED_REQUEST` |
| bad credentials | 401 | `INVALID_CREDENTIALS` |
| unauthenticated authentication entry point | 401 | `UNAUTHENTICATED` |
| `AccessDeniedException` | 403 | `ACCESS_DENIED` |
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| `CatalogConflictException`/duplicate resource | 409 | `RESOURCE_CONFLICT` |
| invalid state transition | 409 | `INVALID_STATE_TRANSITION` |
| `DataIntegrityViolationException` ที่เป็น duplicate/FK race | 409 | `RESOURCE_CONFLICT` |
| unexpected exception | 500 | `INTERNAL_ERROR` |

ห้ามคืน stack trace, SQL, password, session id หรือ database URL

### แยก REST กับ Web error handling

ปัจจุบัน `@RestControllerAdvice` ครอบคลุมทั้ง application และอาจทำให้ Thymeleaf error กลายเป็น JSON. ให้แยกอย่างใดอย่างหนึ่ง:

- REST advice จำกัดกับ API controller/package
- Security REST handlers คืน JSON ส่วน web ใช้ redirect/error view
- validation ของ Thymeleaf ใช้ `BindingResult` และ error page/flash ที่เหมาะสม

## 4. Authentication and authorization tests ที่ A ต้องเพิ่ม

- register creates user/profile atomically และ password เป็น BCrypt
- duplicate normalized email → 409
- invalid login → generic 401; ไม่เปิดเผยว่า email มีอยู่หรือไม่
- successful login creates session; `/users/me` อ่าน user ปัจจุบันได้
- session fixation protection ทำงาน
- logout invalidates previous session
- anonymous protected endpoint → 401
- authenticated USER accessing another user's tool/profile → 403 หรือ 404 ตาม disclosure policy
- USER accessing admin category/approval route → 403
- ADMIN accessing admin route → allowed
- CSRF missing/invalid on browser mutation → rejected
- request ไม่สามารถ mass-assign role/email/password hash/owner id/status ได้
- error response ทุก status ใช้ shape เดียวกัน

เพิ่ม integration tests ด้วย MockMvc และ isolated database; อย่าใช้ Supabase project จริงใน test suite

## 5. Database and migration coordination

### User-owned schema alignment

ตรวจให้ Entity A ตรงกับ schema ที่ทีมใช้:

- `users.email` lowercase unique, password hash, role, timestamps
- `user_profiles.user_id` unique FK (1:1)
- profile field naming ต้องตกลง `full_name` หรือ `display_name` ให้ตรงทั้ง entity/DTO/schema
- URL/bio length ต้องตรง documentation

### B schema dependency

B เปลี่ยน clean schema ของ `tools` ให้มี `short_description`, `repository_url`, `view_count`, status length และ indexes. Database เดิมที่มี `website_url` จะไม่เปลี่ยนจาก `CREATE TABLE IF NOT EXISTS`.

A/ทีมต้องช่วยตัดสินและทำ migration strategy:

- ถ้าใช้ Flyway: เพิ่ม forward migration สำหรับ rename/add columns และ baseline/version order
- ถ้ายังใช้ `schema.sql`: reset/prepare development DB ใหม่อย่างชัดเจน และห้ามใช้ production/Supabase เดิมโดยไม่ migrate
- ก่อน `ddl-auto=validate` ต้องมี columns/index/FK ตรงกับ entities ทุก role

ห้ามแก้หรือ reset database ของทีมแบบ destructive โดยไม่มีการอนุมัติและ backup

## 6. Test profile and credentials

ปัจจุบัน test configuration ใช้ datasource จาก environment ที่อาจชี้ Supabase. จัดทำอย่างใดอย่างหนึ่ง:

- `application-test.yml` + local PostgreSQL/Testcontainers
- dynamic container properties สำหรับ PostgreSQL
- test profile ที่ไม่มี secret จริงและไม่ยิง managed database

Acceptance:

- `mvn -f code/pom.xml test` รันได้จาก clean checkout โดยไม่ต้องใช้ Supabase password
- test schema สร้างจาก migration/schema baseline เดียวกัน
- credentials ไม่อยู่ใน source, logs, screenshot หรือ test report

## 7. JWT/session conflict

POM ปัจจุบันมี JJWT dependencies แต่ plan กลางเลือก session-based authentication. A ต้อง:

- ตรวจว่ามี JWT code/requirement ที่ merge แล้วหรือไม่
- ถ้าไม่มี ให้เสนอ ADR/PR ลบ dependencies ที่ไม่ใช้ หรือยืนยันเหตุผลการเก็บไว้
- ห้ามสร้าง JWT filter ควบคู่ session โดยไม่มี decision เพราะจะทำให้ authentication lifecycle และ CSRF policy สับสน

## 8. Definition of done สำหรับ A integration

- [ ] มี `CurrentUserProvider` หรือ principal contract ที่ B consume ได้โดยไม่ reflection
- [ ] Session login/logout/BCrypt/CSRF/fixation policy ทำงาน
- [ ] USER/ADMIN/owner/non-owner matrix มี tests
- [ ] `ErrorResponse` มี code/error/fieldErrors/traceIdตาม contract
- [ ] 400/401/403/404/409/500 mapping มี tests
- [ ] REST advice ไม่ intercept Thymeleaf เป็น JSON
- [ ] duplicate/FK race ไม่กลายเป็น 500 โดยไม่ตั้งใจ
- [ ] test profile ไม่ต่อ Supabase จริง
- [ ] user/profile schema และ entity naming สอดคล้องกับ baseline
- [ ] B สามารถสร้าง/แก้/ลบ tool ผ่าน trusted actor ได้จริงหลัง A merge
- [ ] ไม่มี request field ใดสามารถยกระดับ role/owner/status ได้

## 9. Updated handoff after B remediation

B now provides these shared seams:

- `com.example.toolhub.security.AuthenticatedUserPrincipal` with `Long getId()`.
- `CurrentActorProvider`, which reads only Spring Security's trusted principal and authorities.
- API-only `GlobalExceptionHandler`, including validation, malformed-body, 403, 404, 409, and safe-500 responses.

Role A must make its authenticated `UserDetails`/principal implement `AuthenticatedUserPrincipal` and expose the persisted user ID. It must grant admins `ROLE_ADMIN`. Do not put `ownerId`, `userId`, or `role` in requests as a substitute.

Role A remains responsible for the security filter chain, authentication entry point (401 JSON for REST), access-denied handler, session/CSRF policy, and security integration tests. The current B handler deliberately excludes Thymeleaf controllers so web errors can use a web error view or redirect.

Database coordination is still required: `doc/sql/V7__align_existing_tool_catalog.sql` is a non-executing, guarded migration draft for the existing `website_url` schema. A/E must agree the Flyway baseline, back up the target database, test the migration on staging, and then install it as an approved forward migration. It must not be run ad hoc against production.