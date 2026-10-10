# UI navy/light — ชุดที่ผู้ใช้ตรวจรับ 9 ตุลาคม 2026

สถานะ: ผู้ใช้ตรวจหน้าตาและให้จัด commit/push บน `thaninton_673380043-6_02` หลังชุด B+E `571e140` ไม่ใช่การ merge develop หรือรับรอง B1 ทั้งระบบ

## ขอบเขต

- ใช้ navy/light ตาม `UI_DESIGN_GUIDE.md`: primary `#123B5D`, hover `#0B2942`, link/focus `#175CD3`, background `#F8FAFC`
- เปลี่ยนสีใน `code/src/main/resources/static/css/role-e.css` ให้เป็น source กลางของทุกหน้าที่เรียก CSS นี้ ไม่ต้องเพิ่ม navy override เฉพาะ admin/dashboard
- ปรับ shell, buttons, forms, tables, status badges และรายละเอียด Tool ที่ผู้ใช้เห็นใน preview พร้อม responsive styles
- หน้ารายละเอียด Tool มี breadcrumb, title/status, category, actions และ description panel; คง review section ของ D เดิม
- ใช้สี semantic ตามคู่มือ: error/delete แดง, pending เหลือง, published/success เขียว ไม่เปลี่ยนทุกสถานะเป็นน้ำเงิน
- ไม่มีการแก้ Java service, authorization, revision contract, PostgreSQL migration หรือโค้ด guards ของ B

## การตรวจ

- ผู้ใช้ตรวจหน้า UI และตอบรับแล้ว ก่อนสั่ง commit/push
- ตรวจ preview ใน browser: admin, dashboard, Tool detail และ versions; computed primary เป็น `#123b5d`, background เป็น `rgb(248,250,252)`
- ตรวจ source ไม่เหลือ palette KKU เดิมใน resources; D review section ใน detail ตรงกับฐาน571e140
- Impeccable CSS detector ไม่พบรายการเตือนใน role-e.css และ `git diff --check` ผ่าน
- Java17.0.20.1: รัน ToolWebControllerTest, ToolBrowseWebControllerTest, RoleEFlowIntegrationTest และ ReviewDecisionIntegrationTest รวม **96 tests**, failures/errors/skips0, BUILD SUCCESS
- คำสั่ง: `./scripts/mvn-java17.ps1 -B -f code/pom.xml '-Dtest=ToolWebControllerTest,ToolBrowseWebControllerTest,RoleEFlowIntegrationTest,ReviewDecisionIntegrationTest' test`
- Log local: `code/target/ui-approved-verify.log`; source hashes: `code/target/ui-approved-source.json` (generated ไม่รวม Git)

96เป็น targeted local verification รอบ UI ไม่ใช่ full528ซ้ำ GitHub CI ต้องอ่าน run ของ SHA UI ที่ push จริง; workflow Build and test ใช้ HEAD ส่วน Review and publishing integration ยังใช้ baseline3187098/overlay

## ส่งต่อให้แต่ละ role

- หน้าใหม่/หน้าแก้ไขให้ใช้ tokens จากคู่มือและ shared CSS แทนค่าสี KKU/ชุด override ซ้ำ
- รักษา status text, focus, session/CSRF และ error contracts; การซ่อนปุ่มไม่แทน server authorization
- ถ้าเปลี่ยน shared CSS ให้ตรวจ callers: auth, browse, detail, dashboard, forms, versions, moderation และ error pages
- ชุดนี้ให้ตรวจ source ด้วย `git log`/diff จาก571e140 และอ่าน CI run ที่อ้างอิง UI commit ใหม่
- B+E local528และ CI ของ571e140เป็นหลักฐานชุดก่อน UI; C tag guards และ A/D review ของชุดรวมยังเป็นงานค้างแยกจากการตรวจรับหน้าตา

เอกสารนี้พร้อมให้ผู้ใช้ส่งต่อ ไม่ได้ส่งข้อความหา role อื่นแทนผู้ใช้
