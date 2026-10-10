# PrimeSkill redesign ? local UI acceptance

Date: 10 October 2026. Base recorded by the earlier local review: `0d1cc6b8c3f8a947aa3de03279e98577f8a8eba3`.

All changes remain in `worktrees/primeskill-redesign` for user inspection. No Git commands, commits, pushes, PRs, merges or deployment were performed during this audit. No shared database or Supabase migration was run.

## Preview

Open <http://127.0.0.1:18086/> on this computer. The refreshed preview uses real application services with an isolated in-memory H2 database and seven published sample tools. Data disappears when the process stops.

All sample accounts use `ReviewOnly123!`:

| Account | Pages to inspect |
|---|---|
| `owner@sprint3.test` | Tool editor, tags, releases, submit/withdraw/restore |
| `admin@sprint3.test` | Moderation, categories, tags and admin tool actions |
| `reviewer@sprint3.test` | My reviews, review editing and Profile |
| `member@sprint3.test` | Browse, review creation and Profile |

## Findings corrected

| Finding | Result |
|---|---|
| Blue focus rectangle inside search input | Home and Explore focus the complete search bar with a muted border and subtle shadow; keyboard focus remains visible |
| Maximum-length names/category labels overflow | Headings, flex children, chips, breadcrumbs and category spans wrap at 320px and at 400% browser zoom |
| Preview tool icon compresses beside a long title | Icon retains its size while the title wraps; focused recheck on both browsers |
| Small form labels and mobile input text | Form labels are 14px; supporting text is at least 12px; mobile inputs are 16px |
| Profile, reference administration and tool-tag UI absent | Added screens that call existing services and enforce their existing authorization/state rules |
| Tool/tag mutation states unclear | Tool tag assignment/removal is available only to owner/admin in DRAFT, with a read-only view for other states |
| Error messages use wrong DTO limits | Messages use the actual constraint bounds; invalid slug and duplicate slug errors retain field values |
| Failed review creation discards validation binding | Entered comment and linked summary/field errors survive the response; covered on H2 and PostgreSQL |
| Own review editor can disappear beyond the first community page | Dedicated Your review section, independent of community pagination; deletion keeps tool context |
| Missing review becomes an unhandled error | Safe 404 web response and recovery link |
| Login loses the original destination | Validated same-origin return target retained across login/register; unsafe targets fall back to the workspace |
| Auth failure loses button arrow or leaves controls stuck | Button markup restored after failure; offline feedback verified |
| Partial script loading can hide mobile navigation | Navigation collapses only after handlers are ready; missing auth scripts show an explanation and disabled submit |
| Filter drawer leaves background accessible | Focus containment, Escape/return and inert background verified through browser accessibility tree |
| Security error page loses the authenticated shell | Shared model also applies to the framework error controller; authenticated CSRF failure retains signed-in navigation |
| Broken profile avatar has no fallback | Initials remain visible after image failure |

Native forms retain CSRF and server-side validation. Existing REST DTOs, database schema, shared tool locks, DRAFT guards, revision-safe approval and review visibility policies are preserved. Tag reads fetch their associated tag in one query instead of creating an N+1 query pattern.

## Verification

- Full disposable PostgreSQL verify: **372 Surefire + 330 PostgreSQL Failsafe = 702 Java tests**, zero failures/errors/skips, BUILD SUCCESS. Log: `code/target/ui-audit-final-full.log`.
- Report validators: **25 Python tests**, all passing (8 Role C, 9 Role E B1, 8 review CI).
- Chrome: **17 workflow groups and 188 layout checks**, PASS, no uncaught JavaScript exceptions. Report: `code/target/ui-acceptance/mv1q5zvf-chrome/report.json`.
- Edge: **17 workflow groups and 188 layout checks**, PASS, no uncaught JavaScript exceptions. Report: `code/target/ui-acceptance/mv1qa0kb-edge/report.json`. Together Chrome/Edge covered **34 workflow groups and 376 layout checks**.
- Browser coverage: Home, Browse, detail, Profile, dashboard, tool editor, tag assignment, release list/form, public releases, My reviews, moderation and category/tag list/forms; Light/Dark at 320/375/768/1024/1440px, landscape, real browser zoom 200/400%, reduced motion, no JS/partial JS, validation, duplicate retention, authentication and return links, role denials, confirmation cancellation, CRUD flows, stale approval revision, CSRF/session expiry and offline auth failure.
- Meaningful new backend checks cover actual profile normalization and privilege immutability, DTO limits and form retention, roles/CSRF, referenced deletion guards, all four tag mutation states, review pagination/error recovery and bounded tag query count. Fourteen return-target cases test redirect safety. Existing assertions remain in place.
- A final focused Chrome/Edge recheck after the icon-size fix passed **12 Light/Dark layouts** at 320/375/1440px, including search focus on Home and Explore. Report: `code/target/search-final-report.json`.
- UI JavaScript and preview launcher syntax checks pass; no trailing whitespace findings in inspected implementation files. The launcher's Maven compile/dependency-classpath command was verified.
- Screenshots inspected: search focus, profile/avatar fallback, catalog validation, maximum-length mobile detail, mobile Home in both themes and native zoom. Generated evidence is under `code/target/ui-acceptance`.

An earlier Edge run exposed a 100-character category overflow; the category span wrapping fix is included in the final recheck. Browser test mutations use port 18088 and a required disposable-server marker. They never run against the user's port 18086 preview or a shared database.

## Reproduce browser checks

Use Java 17, Maven and Node with Chrome/Edge installed. Start a separate disposable server:

```powershell
./scripts/preview-ui-acceptance.ps1 -Port 18088
```

Pass `-MavenCommand`/`-JavaCommand` executable paths if they are not on PATH. In a second terminal:

```powershell
node ./scripts/test-ui-browser.cjs chrome http://127.0.0.1:18088
node ./scripts/test-ui-browser.cjs edge http://127.0.0.1:18088
```

The script refuses non-loopback URLs and responses lacking the disposable marker. Its browser profiles are isolated from the user's browser. Failed test data can be discarded by restarting only that test server; successful runs clean up their created catalog/tool records.

For full Java/PostgreSQL verification, use `scripts/test-postgres.ps1` with a disposable PostgreSQL installation. The preview itself does not require PostgreSQL.

## Practical limits

Physical Android, Safari and deployed Supabase behavior are not certified by these local checks. Mobile viewport/touch settings are emulated. Production performance and deployment remain separate verification steps. Authentication still requires JavaScript; other native forms and discovery work without it. The user has not yet accepted the design for GitHub or deployment.

## Follow-up: consistent focus across the site

The user's login screenshot identified another blue field outline from the shared `:focus-visible` rule. The shared focus token now uses the muted theme color. Text inputs, passwords, textareas and selects have a subtle muted border/shadow instead of the blue outline; Home/Explore keep focus on the complete search wrapper. Invalid fields retain a red boundary. Buttons, links and tag choices retain a visible muted keyboard outline.

A focused read-only Chrome/Edge audit passed **308 field-focus checks across 112 page/theme/viewport combinations**: 14 routes, both themes, 375/1440px and two browsers. It includes keyboard Tab/Shift+Tab on the password visibility control, incorrect-login feedback and invalid-field styling. No uncaught JavaScript exceptions or horizontal page overflow were found. Report: `code/target/focus-audit-report.json`; screenshots: `code/target/focus-audit-*`.

Only CSS and documentation changed in this follow-up. The preview CSS is refreshed on port 18086; no saved catalog/profile/review records were modified during these checks, and no Git commands were used.
