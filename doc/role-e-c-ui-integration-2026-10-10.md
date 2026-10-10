# E — รวม UI ของ C เพื่อผู้ใช้ตรวจ

## Source

- Branch: thaninton_673380043-6_02
- HEAD ก่อนรวม: d41f26b1dd64cd32f40c2921b0a40aa70e09e3e5
- develop ฐาน: fc9421a086ecf7fabb9bd8ca7d246e843e64cff7
- C source: f7ecd1d66c5a2f5d23b6c2284d79bd3894974769
- รวมด้วย git merge --no-ff --no-commit ไม่มี conflict; ยังไม่มี merge commit/SHA ใหม่ และยังไม่ push
- Product tree ใน index ตรงกับ C source; เอกสาร local acceptance เดิมเก็บไว้และสำรองนอก Git ก่อนรวม

## ขอบเขตที่ต้องตรวจ

UI ใหม่ Light/Dark, ภาษาอังกฤษ, shared layout/fonts/icons และ controller ใหม่สำหรับ home/profile/reference/tag management รวมทั้ง return target หลัง login

สามจุดที่ส่งต่อ: My reviews (มี/ไม่มีรายการและมือถือ), ชื่อว่าง/whitespace ภาษาไทยพร้อมรักษาฟอร์ม, error menu ตาม anonymous/USER/ADMIN

## วิธีตรวจ

Java 17.0.20.1 / PostgreSQL 18.6 disposable loopback port 15456:

- scripts/test-postgres.ps1 -Port 15456
- Python test-review-ci-reports.py, test-role-e-b1-reports.py, test-role-c-reports.py
- check-role-c-reports.py สำหรับ coverage gate หลัง Maven จบ
- ตรวจจริงด้วย in-app browser ที่พอร์ต 18081 หลังติดตั้ง JAR ชุดรวม

ไม่ใช้ Supabase ไม่รัน migration ฐานร่วม เก็บข้อมูล local เดิม; ผล GitHub CI ต้องรอ SHA ที่ commit/push จริง ไม่อ้างว่า local checks คือ GitHub CI

## ข้อสังเกต

- Generic animation cancellation ยังเป็นข้อสังเกตที่ C แนบไว้ ไม่อ้างว่าปิดแล้วทุก browser
- Diff check พบ trailing whitespace ใน license fonts และ blank line EOF ใน templates เดิมจาก C ไม่มีการแก้ license เพื่อให้ product tree ตรงกับ source C
- A ต้องตรวจ auth/error navigation และ route ใหม่; B ตรวจ editor; D ตรวจ reviews/summary; E ตรวจ releases/moderation และ flow รวมบน candidate ที่จะส่งจริง
- ผู้ใช้ตรวจและยืนยัน UI หลักก่อน commit/push/PR develop

## ผลรัน

- Maven -Ppostgres-it verify: BUILD SUCCESS, Surefire 377 + PostgreSQL Failsafe 335 = 712 tests; failures/errors/skips = 0 (10 ตุลาคม 2026 เวลา 13:32:57)
- PostgreSQL disposable port 15456 หยุดเรียบร้อยหลังรัน
- Python validators: 8 + 9 + 8 = 25 ผ่าน
- check-role-c-reports.py: ผ่าน gate B/D/E/C จาก XML จริง (377/335)
- Python launcher บน PATH เคยชี้ Windows Store stub ทำให้ gate ครั้งแรก exit 9009; รันใหม่ด้วย Python314/python.exe จริงผ่าน exit 0 ไม่ใช่ product failure
- Browser บน packaged JAR + PostgreSQL local ที่ 18081: member review populated, owner empty reviews, whitespace name ภาษาไทยและรักษาค่าฟอร์ม, ยกเลิกแล้วชื่อเดิมยังอยู่, member hidden 404 ยังคง Workspace/Sign out, anonymous error แสดง Sign in/Get started, admin forbidden แสดง Moderation recovery
- ตรวจ owner versions และ confirmation Submit: กด Cancel แล้ว focus กลับปุ่มเดิม สถานะ Draft คงเดิม; admin queue โหลดได้ (queue ว่าง) ไม่ได้ publish/delete ข้อมูล fixture เดิม
- Profile load และ login/logout ผ่าน browser ทั้งสามบัญชี
- Browse 375×812 และ 1280×900, Light/Dark, navigation/filter drawer ใช้งานได้ ไม่มี whole-page horizontal overflow ในขนาดที่ตรวจ
- Browser console warn/error ที่เก็บจากแท็บตรวจใหม่: ว่าง ไม่ได้แปลว่า animation cancellation ปิดแล้วทุก environment
- รูป/coverage: D:/PrimeSkill-local-team/evidence/c-ui-integration-2026-10-10/
- Log Maven: D:/PrimeSkill-local-team/logs/c-ui-integration-verify.log
- Runtime 18081: primeskill-c-ui-preview.jar, source_uncommitted=true, integration_source_sha=f7ecd1d; JAR hash, patch hash และ source tree อยู่ runtime/manifest.json ไม่อ้างว่ามี SHA รวมที่ commit แล้ว
- สำรอง DB local ก่อน restart: D:/PrimeSkill-local-team/backups/local-team-25691010-133111.dump; ไม่แตะ Supabase
- Docker executable ไม่พบใน PATH รอบนี้ จึงยังไม่ได้รัน Docker/container checks ในเครื่องนี้
- ยังไม่ commit/push/PR develop; GitHub CI ชุดรวมยังไม่รัน


## สถานะส่งมอบล่าสุด

ผู้ใช้สั่งสร้าง merge commit บน thaninton_673380043-6_02 รวม C f7ecd1d และเอกสารผลตรวจของ E แล้ว ผลด้านบนเป็นการรันก่อน commit บน product tree เดียวกัน (ไม่แก้ product code หลังทดสอบ) ยังไม่ push/เปิด PR ใหม่/merge develop และยังไม่มี GitHub CI ของ merge commit นี้ ข้อความ no-commit ก่อนหน้าเป็นสถานะระหว่างตรวจ

งานต่อ: push เมื่อผู้ใช้สั่ง, รับ CI ของ SHA รวม, ให้ A ตรวจ security routes/error navigation และให้ B/D ตรวจ flow UI ในส่วนรับผิดชอบ; animation cancellation เป็นข้อสังเกตต่อเนื่อง, Docker/Supabase deployment ยังไม่ได้ยืนยัน
