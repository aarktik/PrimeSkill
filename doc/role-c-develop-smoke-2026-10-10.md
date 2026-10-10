# Role C — acceptance of merged develop

Date: 10 October 2026, Asia/Bangkok.

**Tested source:** `fc9421a086ecf7fabb9bd8ca7d246e843e64cff7` (`develop`, PR #6 merge).
The C personal branch was updated by fast-forward from `4b0fb0e` to this source.
The user confirmed that the requested team test environment is **local develop**.

**Conclusion:** no blocker found in the tested C flows. Browse, category/tag/keyword
filters, pagination, real review scores, rating order, and tag mutations passed on
the integrated source. No production code, schema, or migration was changed.

## Source and environment

- Clean isolated checkout: `worktrees/role-c-develop-acceptance`, detached at the tested SHA.
- Full verification: Microsoft JDK 17.0.20.1, Maven 3.9.16, disposable PostgreSQL 18.6.
- HTTP/browser: the same compiled develop source, with the repository's existing
  Sprint3 test fixtures, real login sessions and CSRF, and a fresh in-memory H2 database
  bound to `127.0.0.1:18085`.
- The local preview uses the existing test-only owner/admin accounts and synthetic
  reviewers/tools/tags. Browser profiles are isolated temporary profiles.
- The source files were checked against Git HEAD, allowing only CRLF/LF normalization.
  All 60 Java XML suites recorded a Java 17 runtime.

## Full regression results

| Run | Executions | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Surefire | 338 | 0 | 0 | 0 |
| Failsafe / real PostgreSQL | 312 | 0 | 0 | 0 |
| Total Java | **650** | **0** | **0** | **0** |
| Python report gates (8 + 9 + 8) | **25** | **0** | **0** | **0** |

`BUILD SUCCESS`. The current C gate and its parent B/D/E gate accepted the actual
XML reports. C rating9, tag guards35, and tag concurrency34 are included in the
312 PostgreSQL executions, including E's added REJECT/tag/rollback/stale-entity
cases. The B/D/E regression suites also ran; none were skipped.

## Actual HTTP/session smoke — 22 check groups

Requests reached a running HTTP server, not mocked authentication or test-level
transactions. Assertions read the result after each request completed.

- Owner/admin login, reviewer registration/login, and CSRF token rotation succeeded.
- Real Tool create -> assign tags -> SUBMIT -> ADMIN approval -> review creation
  produced five published test tools and one hidden tool.
- Keyword + category + multiple ANY tags produced five unique published tools,
  with accurate totals and three pages at size2; matching multiple tags did not
  multiply results. Hidden tools were excluded.
- Rating pages ordered High5.0, Tied5.0 (id tie-break), Middle4.5, Low3.0, Unrated;
  pagination remained stable and unrated was last.
- All four sort modes accepted missing/blank keyword with category/tag filters.
  Empty results and invalid page/size/sort contracts behaved correctly.
- Browse rendered the real 4.5 average / two reviews, and `ยังไม่มีรีวิว`.
- Creating, editing, and deleting a review updated subsequent rating search and
  Browse responses; deleting the last review restored unrated-last behavior.
- Anonymous/other users could not read hidden tag associations; owner/admin could.
- DRAFT tag assign/unassign succeeded for owner/admin. Anonymous, non-owner,
  and missing-CSRF mutation requests were denied and left Tool/tag state unchanged.
- PENDING/PUBLISHED/DEPRECATED tag POST/DELETE gave owner/admin409
  `INVALID_STATE_TRANSITION` and non-owner403 `ACCESS_DENIED`. Committed Tool
  metadata/status/revision/viewCount and associations remained unchanged.
- REJECT -> DRAFT tag edit -> resubmit advanced revision. Approval with the old
  revision returned409 `STALE_REVIEW_REVISION`.
- Deleting an in-use global Tag returned409 `RESOURCE_CONFLICT` and kept the tag.

The 22 HTTP groups are separate smoke checks, not additional JUnit executions.

## Actual browser smoke — 8 check groups

Headless Chrome exercised the existing Browse form and page controls:

- Typing keyword and submitting search, then selecting category, entering multiple
  tag slugs, and selecting rating sort produced the expected filtered card order.
- Clicking Next/Previous preserved q/category/tags/sort/size and showed the correct
  first and second rating pages.
- Real average/count and unrated labels displayed correctly; hidden tools did not appear.
- Desktop1440 and mobile390 viewports had scrollWidth equal to viewport width.
- Keyboard Tab focus was visible, the mobile menu opened/closed, and the empty
  search view displayed correctly. No unhandled page JavaScript exceptions were recorded.
- Desktop/mobile screenshots were visually inspected.

The eight browser groups are separate checks, not Java test counts.

## Reproduction and evidence

```powershell
./scripts/mvn-java17.ps1 -version
./scripts/test-postgres.ps1 -PostgresBin <local PostgreSQL bin> -Port 15454
python scripts/test-review-ci-reports.py -v
python scripts/test-role-e-b1-reports.py -v
python scripts/test-role-c-reports.py -v
python scripts/check-role-c-reports.py code/target --output code/target/develop-smoke/coverage.json
```

The runner executes `mvn -B -f code/pom.xml -Ppostgres-it verify`. Its cluster was
stopped after verification. The temporary HTTP preview was stopped after browser QA.
No Supabase/shared database or migration was used. GitHub CI was not rerun in this
local acceptance task; these results apply to the tested source SHA above.

- Portable summary: `role-c-develop-smoke-evidence-2026-10-10.json`.
- Full local log: `worktrees/role-c-develop-acceptance/code/target/role-c-develop-verify.log`.
- XML: that checkout's `code/target/surefire-reports` and `failsafe-reports`.
- Local delivery: `deliverables/role-c-develop-acceptance-2026-10-10/` includes
  HTTP/browser results, harnesses, and desktop/mobile screenshots.

## Message for E / team

> C อัปเดต branch จาก develop และตรวจ local develop ตามที่ตกลงแล้วครับ
> Tested SHA: fc9421a086ecf7fabb9bd8ca7d246e843e64cff7
> ไม่พบ blocker ใน Browse, filters, pagination, คะแนน/sort=rating และ tag mutations
> ผลใหม่ Java17/PostgreSQL: Surefire338 + Failsafe312 =650 ผ่าน, Python25 ผ่าน
> ไม่มี failure/error/skip รวม REJECT–tag concurrency34 ของ E แล้ว
> ตรวจ HTTP จริงด้วย login/session/CSRF อีก22กลุ่ม และ browser8กลุ่มผ่านทั้ง desktop/mobile
> review create/update/delete แล้วคะแนน Browse เปลี่ยนถูกต้อง; tag edits DRAFT-only,
> denied requests ไม่เปลี่ยน state, reject/edit/resubmit ปฏิเสธ approval token เก่า
> ไม่มี production/schema changes หรือ migration เพิ่ม รายงานอยู่ใน doc/role-c-develop-smoke-2026-10-10.md ครับ

This text is prepared for the user to send; no message/comment was sent to teammates.
