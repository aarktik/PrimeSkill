# Role C: local acceptance of the new UI

Date: 10 October 2026, Asia/Bangkok. Prepared for Role E; no external message was sent.

The final Chrome and Edge runs passed the requested Role C flows on the **new local UI**. This is local verification, not deployment or remote CI approval. The browser animation observation is recorded below with its captured exception details.

## Source identity

- Develop baseline requested by E: `fc9421a086ecf7fabb9bd8ca7d246e843e64cff7`. The earlier clean-develop acceptance is documented in `doc/role-c-develop-smoke-2026-10-10.md`.
- Tested directory: `worktrees/primeskill-redesign`, including the new UI and the latest muted-focus changes.
- The user authorized commit/push to Role C branch `supakron_673380061-4_02` on 10 October 2026 after local review. The develop baseline does not identify this modified UI; use the Git commit supplied with the delivery links. This report is prepared before that commit is created, so it does not embed its own commit ID. Git metadata and the remote branch were checked during delivery preparation.
- Exact tested source is identified by the SHA256 file manifest in the accompanying `source-snapshot.json`. This hash is a source snapshot identifier, not a Git SHA. It covers `code/pom.xml`, main/test sources and verification scripts; raw file bytes and relative paths are hashed. Generated evidence and this report are excluded.
- Source snapshot SHA256: `2ff17cdbb681b525876e8207f630cd4ff61204f818d5cb6dc4b8150e8a138eaf`.

## Environment

| Component | Value |
|---|---|
| OS | Windows NT 10.0.26200.0; Windows PowerShell 5.1.26100.9444 |
| Java | Microsoft OpenJDK 17.0.20.1 |
| Maven / Node | Maven 3.9.16 / Node 24.19.0 |
| Browser | Chrome 154.0.8037.98 / Edge 155.0.4283.45, isolated headless profiles |
| UI and real HTTP requests | Explicit test profile, fresh in-memory H2 in PostgreSQL mode, `127.0.0.1:18088` |
| Database regression | Disposable PostgreSQL 18.6, runner port 15455 |
| Viewports | 320, 375 and 1440 CSS pixels, Light/Dark; mobile is emulated |

Both databases are disposable and isolated. The user's preview on port 18086 was not used for test mutations. No Supabase/shared database or migration was touched. The PostgreSQL cluster and port 18088 server were stopped after verification; the user preview remains available.

## Requested Role C checks

| Area | Evidence and result |
|---|---|
| Browse/search | Typed Home search reaches Browse; keyword results contain only published tools; missing keyword produces usable empty results and Clear filters |
| Category/tag filters | Actual category select and tag checkboxes work; tags match ANY; overlapping tag matches do not duplicate tools or inflate totals; single-tag narrowing and reset work |
| Ratings | Real stored reviews render 4.5 / two reviews; tools without reviews show No reviews yet; creating/editing/deleting a review through the UI changes subsequent Browse scores and ordering |
| Rating sort/pagination | High 5.0, Tied 5.0 ordered by ascending ID, Middle 4.5, Low 3.0, Unrated last; size 2 yields three stable pages; actual Next/Previous links preserve q/category/tags/sort/size and restore selected checkboxes |
| Tag mutations | Owner and admin assign/unassign through native UI forms on DRAFT; admin works on another owner's tool; rejected requests preserve committed tool/tag state |
| Non-DRAFT guards | PENDING/PUBLISHED/DEPRECATED display tags read-only; forged owner/admin assign/unassign requests return 409; non-owner mutations return 403 |
| Anonymous/CSRF/privacy | Management GET requires login; anonymous mutations with valid CSRF return 401; missing CSRF returns 403; outsiders cannot open management pages. Private DRAFT/PENDING/DEPRECATED reads return 404; published non-owner management reads return 403. This is the existing privacy contract |
| Responsive/focus | New Browse and the mobile filter drawer operate in both themes; no whole-page horizontal overflow in six checked layouts per browser; search has muted wrapper focus without an inner blue box |

The fresh PostgreSQL suites also cover full-precision average sorting before pagination, hidden-tool exclusion, literal wildcard search, bounded aggregate queries, DRAFT guards and tag concurrency. The E follow-up adds Thai blank-name presentation and fixes error-page menu rendering; domain/REST validation and authorization contracts are unchanged.

## Results

| Check | Result |
|---|---|
| Surefire | 377 tests, 0 failures/errors/skips |
| PostgreSQL Failsafe | 335 tests, 0 failures/errors/skips |
| Java total | **712**, BUILD SUCCESS |
| Python report validators | **25** passing tests (8 + 9 + 8) |
| Role C coverage gate | Passed actual XML reports and the parent B/D/E gate; includes PostgreSQL rating 9, tag guards 35 and tag concurrency 34 |
| Actual HTTP/session/CSRF | **22** check groups, PASS |
| Chrome new UI | **16** workflow groups and **6** Light/Dark layouts, PASS; no uncaught exceptions in final run |
| Edge new UI | **16** workflow groups and **6** Light/Dark layouts, PASS; no uncaught exceptions in final run |

Browser and HTTP groups are separate acceptance checks; they are not additional JUnit test counts. Screenshots for rating page two, mobile filtering, admin DRAFT editing and non-DRAFT read-only tags were inspected.

## Observation and limits

The generic promise event was reproduced with details: `InvalidStateError: Transition was aborted because of invalid state. ViewTransition opt-in disabled`. Every requested functional workflow still completed. Navigation styling now uses the same opt-in stylesheet across shared pages, and the early script handles this specific expected cancellation when a ViewTransition object is available. Unexpected promise errors remain visible. A residual event was seen during headless navigation, so it remains an integration observation rather than a claim of complete resolution. Sanitized details are included under `observations/view-transition.json`.

Cross-document transitions require both documents to opt in according to [Chrome's documentation](https://developer.chrome.com/docs/web-platform/view-transitions/cross-document). Error dispatch can lose the security holder according to [Spring Security's persistence documentation](https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html). The UI reads the existing trusted session for error rendering only; application authorization does not use this fallback.
No functional blocker was reproduced in the final Role C runs. Physical Android, Safari and the deployed Supabase environment remain unverified. E's deployment verification remains a separate team step. These results cover the exact local source manifest below. E should rerun integration on the combined UI commit before merging or deploying; a future merged SHA is not automatically certified by this local run.

## Reproduce

From `worktrees/primeskill-redesign`, with Java 17/Maven/Node and installed Chrome/Edge:

```powershell
./scripts/preview-ui-acceptance.ps1 -Port 18088
```

In another terminal, sequentially:

```powershell
node ./scripts/check-role-c-ui-http.cjs http://127.0.0.1:18088
node ./scripts/check-role-c-ui-browser.cjs chrome http://127.0.0.1:18088
node ./scripts/check-role-c-ui-browser.cjs edge http://127.0.0.1:18088
```

The HTTP check seeds isolated fixtures and writes `code/target/role-c-new-ui/http-check.json`, which the browser checks consume. Both refuse non-loopback servers and require the disposable-server marker. Do not run the scripts against the user's port 18086 preview. Stop the test server to discard all test data. To retry a browser run interrupted mid-workflow, run the HTTP fixture check again first.

```powershell
./scripts/test-postgres.ps1 -PostgresBin <disposable PostgreSQL bin> -Port 15455
python ./scripts/check-role-c-reports.py ./code/target --output ./code/target/role-c-new-ui/coverage.json
python ./scripts/test-role-c-reports.py
python ./scripts/test-role-e-b1-reports.py
python ./scripts/test-review-ci-reports.py
```

Full Java log: `code/target/role-c-e-approved-source-verify.log`. Portable evidence, source manifest, browser results and screenshots: `deliverables/role-c-new-ui-for-e-2026-10-10/`. `message-to-e.txt` is prepared for the user to send; it has not been posted to GitHub or sent to any teammate.

## E follow-up: three required confirmations

| Item | Status / owner | Current evidence |
|---|---|---|
| `/my/reviews` shared layout/theme, empty state and mobile | COMPLETE ? Role C UI | Empty and populated states in Light/Dark at 375/1440px; native review actions remain reachable |
| Empty/whitespace tool name uses Thai and preserves the form | COMPLETE ? Role C UI presentation | `กรุณาระบุชื่อเครื่องมือ` on create/edit for empty and three-space names; slug, descriptions, category and repository URL retain input; invalid requests do not create/update stored tools. REST DTO messages remain unchanged |
| Error menu follows anonymous/USER/ADMIN; forbidden pages share layout | COMPLETE ? Role C UI | Both controller-handled 404 and framework 404, USER denied admin page, ADMIN denied owner-only page; canonical navigation and shared CSS/header/footer match the session. Anonymous gets sign-in recovery; signed-in users retain workspace/sign-out; ADMIN gets Moderation recovery |

Exception handlers explicitly populate a fresh layout model because the original model can be discarded during exception resolution. Framework error rendering also handles an existing session when the holder has been cleared. Error buttons are a shared fragment and forbidden recovery leads to an accessible workspace.

E follow-up browser checks: **9 groups / 20 layouts per browser**, Chrome and Edge PASS, no unhandled exceptions in those final runs. The added Java regression covers blank create/edit preservation and menus on handled 404; the shared UI suite is now 21 tests on each database profile.

The final portable bundle is curated: reports, SHA256 manifest, coverage summaries, inspected UI screenshots and sanitized animation details. It excludes harness source, browser profiles, raw HTML, cookies, auth/CSRF values, fixture passwords and raw Maven/server logs. The historical local harness folder is not the package to send E. The original ZIP link is replaced by this sanitized package.

The latest review preview is <http://127.0.0.1:18089/>. The user has explicitly authorized delivery to the Role C branch. Delivery commit: `feat(ui): redesign PrimeSkill and complete Role C handoff checks`. The final handoff supplies the resulting Git SHA and remote links after push verification. No external teammate message was sent.
