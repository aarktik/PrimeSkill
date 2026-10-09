# ข้อ 2 — รายงานตรวจรับโค้ดรวมจากฝั่ง E

วันที่ 8 ตุลาคม 2026. ตรวจ source/diff หลัง fetch remote ในรอบนี้ และใช้หลักฐาน CI ที่รันไว้แล้วด้านล่าง ไม่ได้รัน Java suite ใหม่ในรอบตรวจเอกสารนี้. รายงานนี้ใช้เตรียมการตรวจของ A/D ไม่ใช่ approval ของผู้รีวิว.

## Snapshot ที่ต้องใช้ให้ตรงกัน

- Source รวม D/E/A test config: `3187098fac9d91c14d1fdfe40bb765797cde8cfd` บน `role-de-review-pg-rollback`.
- Harness ที่รัน expanded CI: `3c2af5a2ab51751547a905ba7fffa198777c1ff7` บน `thaninton_673380043-6_02`.
- เอกสารผล CI ถูก push เพิ่มใน `59feba0`; เป็นการแก้เอกสาร ไม่เปลี่ยน implementation/harness ที่ CI ตรวจ.
- [PR #5](https://github.com/aarktik/PrimeSkill/pull/5) ยัง open/not merged; head `e4df8c067ba1e1c249eb8571b0c3a34c25ce8dcb` จาก D เข้า develop ณ เวลาตรวจ.
- GitHub reviews API พบ COMMENTED บน `0a92dbc` เท่านั้น ณ เวลาตรวจ ไม่ถือว่าข้อความตอบรับของทีมเท่ากับ GitHub approval ของ head ล่าสุด.

**ผล CI 392 รับรอง source รวม3187098พร้อมfixtureจาก3c2af5a ไม่ใช่ผลรันตรงบน PR #5 head.**

## หลักฐานที่ตรวจแล้ว

- [Expanded CI 37802871848](https://github.com/aarktik/PrimeSkill/actions/runs/37802871848): baseline Surefire247/Failsafe107 และ overlay Surefire247/Failsafe145; failures/errors/skips=0 ทั้งสองชุด. อย่ารวม baseline กับ overlay เป็นจำนวนเทสที่ไม่ซ้ำกัน.
- Required coverage: external race10, external RoleD HTTP/service28, native concurrent rollback4, native sequential rollback4.
- [Build and test 37802871648](https://github.com/aarktik/PrimeSkill/actions/runs/37802871648): success รวม PostgreSQL job และ Docker startup/restart persistence.
- รายละเอียด runtime/artifacts: [CI report](role-de-ci-test-report.md). Java17.0.20.1/PostgreSQL17; เป็นฐาน disposable ไม่ใช่ฐานทีม.

## 1. Row lock / stale entity / commit order

[ReviewServiceImpl ที่ตรวจ](https://github.com/aarktik/PrimeSkill/blob/3187098fac9d91c14d1fdfe40bb765797cde8cfd/code/src/main/java/com/example/toolhub/service/impl/ReviewServiceImpl.java#L132): create/update อยู่ใน transaction และเรียก `lockToolForReview` ก่อนอ่าน visibility/status. ใช้ `findForUpdateById` แล้ว `entityManager.refresh(tool)` ป้องกันสถานะ PUBLISHED ที่ค้างอยู่ใน persistence context.

[PublishingServiceImpl ที่ตรวจ](https://github.com/aarktik/PrimeSkill/blob/3187098fac9d91c14d1fdfe40bb765797cde8cfd/code/src/main/java/com/example/toolhub/service/impl/PublishingServiceImpl.java#L35): transition อยู่ใน transaction ใช้ lock ของ Tool row เดียวกัน ก่อนตรวจสิทธิ์และเปลี่ยนสถานะ.

- `deprecateFirstRejectsReviewAfterWaitingForToolLock`: create/update ต้องรอ lock และถูกปฏิเสธหลัง deprecate commit.
- `reviewFirstCommitsBeforeDeprecateAndReviewIsPreserved`: create/update commit ก่อน แล้ว deprecate ต่อได้และรีวิวยังอยู่.
- `previouslyManagedToolMustNotReuseStatusFromBeforeDeprecate`: preload Tool ให้ stale จริงก่อนอีก transaction deprecate แล้วตรวจ create/update ถูกปฏิเสธ.
- เทสใช้ `pg_blocking_pids` ตรวจการรอใน DB พร้อม timeout ไม่อาศัยเวลานอนอย่างเดียว.

สามกลุ่มข้างต้นคือ 6 cases ใน external fixture. ตรวจ diff แล้ว ReviewServiceImpl และ ToolRepository ใน source รวมตรงกับ D e4df8c0; PublishingServiceImpl ตรงกับ implementation บน E ปัจจุบัน. ไม่ต้อง apply candidate patch ซ้ำ.

## 2. Rollback และการลบหลังซ่อน Tool

- External fixture เพิ่ม `rollbackReleasesLockAndPreservesOnlyCommittedChanges` 4 cases: review/deprecate rollback × create/update. มี flush ก่อน rollback เพื่อให้ SQL เกิดจริง, ตรวจ waiter เคย block, waiter สำเร็จ และอ่านสถานะ/rating/comment/จำนวนแถวจาก DB หลังจบ.
- Native concurrent4 และ native sequential4 เป็นคนละ suite; ไม่ใช้ผล suite ใดแทนอีก suite.
- `RoleDPostgresIT.authorAndAdminCanDeleteAfterToolHidden` ตรวจ API ลบด้วย author/admin หลัง DEPRECATED ได้204 และไม่มีแถวเหลือ. Update ของ author บน hidden toolได้404.
- `ReviewServiceImpl.delete` ตรวจ review ภายใต้ toolId และสิทธิ์ author/admin โดยไม่บังคับ published. จึงรองรับกติกาที่ผู้ใช้ยืนยันไว้.
- เจ้าของ Tool, ผู้ใช้อื่น และ admin ที่ไม่ได้เป็น author แก้รีวิวคนอื่นไม่ได้; admin มีสิทธิ์ลบ ไม่ได้มีสิทธิ์แก้ข้อความแทนผู้เขียน.

## 3. Session / CSRF / error / templates

[SecurityConfig ที่ตรวจ](https://github.com/aarktik/PrimeSkill/blob/3187098fac9d91c14d1fdfe40bb765797cde8cfd/code/src/main/java/com/example/toolhub/config/SecurityConfig.java#L32) เทียบ D e4df8c0:

- คง session IF_REQUIRED และไม่ได้ disable CSRF.
- เพิ่ม public static/login/register/error; read routes ของ versions เป็น GET เท่านั้น. Admin tools routes ใช้ ADMIN; routes อื่นยังต้อง authenticated.
- Public GET ยังต้องผ่าน visibility ใน service; permitAll ของ route ไม่ได้ให้สิทธิ์อ่าน hidden Tool.
- RestSecurityExceptionHandler เพิ่ม redirect เฉพาะ browser GET ที่ขอ text/html และ path อยู่นอก /api/. API ยังคง error response.
- CurrentActorProvider อ่าน id/role จาก SecurityContext/principal. Review controller ใช้ requireActor สำหรับ write; ไม่รับ actor id/admin จาก body.
- Template detail เพิ่ม layout/assets/status badge/version links และรวม alerts. ส่วนฟอร์ม review/error ยังมีอยู่; `invalidWebUpdate_rendersDetailAndPreservesSubmittedValues` ตรวจคงค่าฟอร์มเมื่อ validation ผิด. เทสนี้ใช้ controller slice จึงไม่อ้างว่าแทน full browser rendering.

หลักฐานเทสที่มีในชุด CI:

- `RoleDPostgresIT.sessionAndCsrfProtectCreateUpdateAndDelete`: anonymous+CSRFถูกต้องได้401; writeที่ไม่มี/ผิดCSRFได้403; logoutแล้วไม่มีสิทธิ์; แถวเดิมไม่ถูกแก้.
- `forgedActorFieldsCannotOverrideSessionIdentity`: sessionเป็นตัวกำหนด identity.
- `hiddenToolVisibilityAndCreatePolicyAreEnforced`: hiddenToolอ่านโดยanonymous/ผู้ใช้ทั่วไปได้404; owner/adminอ่านได้และสร้างรีวิวไม่ได้409.
- `ownerCannotSelfReviewAndOnlyAuthorCanEdit`, wrong nested id และ validation tests ตรวจสิทธิ์และไม่มีการเปลี่ยนข้อมูลที่ไม่ถูกต้อง.
- A `AuthCsrfSecurityTest` ตรวจ CSRF endpoint/register/logout/profile/admin category; D `ReviewRouteSecurityTest` ตรวจ API/web review routes และคงค่าฟอร์ม.
- E `RoleEFlowIntegrationTest` ตรวจ publishing CSRF, browser redirect/API401 และ admin/owner routes ด้วย sessionจริงในMockMvc. นี่ไม่ใช่การทดสอบเครือข่ายผ่าน browser.

Error contract ที่ต้องรักษา: authentication401, access403, hidden/missing404, published-state conflict409, validation400. หลัง deprecate ผู้ใช้ทั่วไปอาจได้404เพื่อไม่เปิดเผย Tool; owner/adminที่ยังเห็นToolได้409. Race testยอมรับทั้ง visibility/state rejection อย่างตั้งใจ.

## 4. เส้นทาง PR ที่เสนอให้ A/D ตัดสินใจ

1. ให้ A/D ตรวจ source3187098และหลักฐานข้างบนก่อน เพราะ PR#5ยังไม่ใช่sourceที่ใช้CI392.
2. ทางเลือกที่แนะนำ: D/Eเตรียม PR สำหรับ sourceรวม โดยระบุ3187098และความสัมพันธ์กับPR#5ให้ชัด; ถ้าทีมเลือกupdatePR#5ให้รวมE ต้องรันCIบนheadใหม่อีกครั้ง. รายงานนี้ไม่ได้แก้headหรือเปิดPRแทนทีม.
3. เมื่อเลือกbranchที่จะเข้าdevelopแล้ว ให้นำexternalfixturesทั้งสองเข้าMaven test sourceหรือพกworkflow/runnerไปด้วยอย่างตั้งใจ. ปัจจุบันworkflowอยู่branchEและcheckoutsourceแยก; mergeเฉพาะsourceรวมไม่ได้ทำให้external38casesรันเอง.
4. ตรวจdiffของbranchปลายทางและรันCIกับSHAจริงที่จะmerge; pinned3187098ที่ผ่านแล้วไม่รับรองการแก้ไขหลังจากนั้น.
5. Reviewerกดapprove/mergeเมื่อพอใจ. ยังไม่ทำmergedevelopหรือรันV7/V8ฐานทีม.

## งานที่ยังเปิดอยู่

- A: ตรวจโค้ด/ผล/ข้อจำกัดแล้วให้คำตัดสินกับSHAที่ต้องการmerge.
- D/E: เลือกเส้นทางPRและที่เก็บfixtures/CI. ข้อนี้ต้องตกลงกับทีม ไม่สามารถยืนยันแทนDได้.
- B/E: metadata edit policy / expected revision ป้องกันstale approval (B1) ยังไม่ถูกแก้ด้วยreview/deprecate lock. Publishingไม่ได้refreshหรือรับexpectedrevisionในโค้ดที่ตรวจ; ไม่ควรอ้างว่าชุดreviewraceพิสูจน์approval-content raceแล้ว.
- C: real rating browse/sortก่อนpagination.
- DB owner/E: target/history/backupและอนุมัติmigrationฐานทีมตามchecklist.

สรุป: ฝั่ง E ตรวจsource/diffและเชื่อมหลักฐานข้อ2แล้ว ไม่พบข้อที่ต้องแก้implementationเพิ่มภายในขอบเขตreview/deprecateจากการตรวจนี้. ผล392เป็นหลักฐานของกรณีที่ทดสอบ ไม่รับประกันว่าไม่มีบัคอื่น. ยังเหลือการตรวจรับของA/DและการเลือกPRปลายทาง.
