# PrimeSkill shared UI design guide

Version 2 — local redesign for user review, 10 October 2026.

## Direction

Bright, professional SaaS directory with generous spacing, clear hierarchy, geometric SVG icons and restrained motion. UI copy is English; user content can remain Thai. Self-hosted Plus Jakarta Sans is the primary font, with Noto Sans Thai as the Thai fallback. Font licenses are included alongside seven WOFF2 subsets in `code/src/main/resources/static/fonts`.

Light is the default. The theme toggle persists a preference under `primeskill-theme`; `theme-init.js` applies it before styles load. Dark mode has its own semantic colors and native control color scheme.

| Token | Light | Dark |
|---|---|---|
| Background | `#F8FAFC` | `#0B1220` |
| Surface | `#FFFFFF` | `#111C2D` |
| Text | `#102A43` | `#E8EEF7` |
| Muted text | `#526477` | `#A8B4C4` |
| Primary | `#2563EB` | `#60A5FA` |
| On primary | `#FFFFFF` | `#0B1220` |
| Divider | `#D8E1EA` | `#2A3A50` |
| Form boundary | `#7C8B9A` | `#60728C` |

Primary button text has 5.17:1 contrast in light mode; muted text on the light soft surface has 5.41:1. Muted dark text on the dark surface has 8.14:1. Form boundaries have 3.49:1 against their respective surfaces. These are token checks, not a formal accessibility certification of every possible state.

## Structure

The shared Thymeleaf fragments in `templates/fragments/layout.html` own the head, brand, navigation, workspace sidebar, footer, alerts, status badge and tool card. `primeskill.css` is the shared stylesheet; `role-e.css` remains a compatibility import. There are no page-local stylesheets or inline page CSS blocks.

Public content uses a 1,280px maximum width. The workspace uses a 1,440px maximum width, a 224px sidebar, and a flexible main column. Gutters shrink on phones. The sidebar becomes a horizontal navigation row; public navigation becomes an expandable menu. Without JavaScript, the mobile navigation and Browse filters remain visible.

| Page | Main layout |
|---|---|
| `/` | Search hero, real directory preview, categories, popular/recent cards, contribution steps |
| `/tools` | Search, category/tag rail or mobile drawer, sort, cards and pagination |
| Tool detail | Tool identity, actions, overview/reviews/release navigation, review forms |
| Login/register | Brand story and a compact labeled form |
| My tools | Workspace header, status table with mobile rows, safe empty state |
| Tool editor | Essentials, description and discovery/source groups |
| My reviews | Review cards without hidden tool metadata |
| Releases | Publishing panel and release-note cards |
| Moderation | Submission cards with inspection and revision-safe decisions |
| Profile | Editable display name, bio and avatar; read-only account identity and role |
| Categories/tags | Admin lists, create/edit forms and protected deletion |
| Tool tags | Owner/admin assignment and removal in DRAFT; read-only in other states |
| Errors | Shared signed-in shell, safe message and contextual recovery links |

The UI now exposes the existing profile, category/tag administration, tool tag assignment and admin tool actions. These screens reuse existing services, permissions and DTO limits; they do not introduce new REST or database contracts.

## Interaction and motion

Vanilla JavaScript progressively enhances native controls. Browse tag checkboxes serialize to the existing comma-separated `tags` parameter and preserve ANY-tag matching. GET forms and pagination retain the search contract. The mobile filter drawer handles Escape, keyboard focus cycling, focus return and inert background content. Enhanced navigation collapses at 1,100px; the Browse drawer is used below 768px. Enhancement classes are applied only after handlers initialize, so missing scripts leave navigation usable.

Native mutation forms keep CSRF and server validation. The shared confirmation dialog runs before duplicate-submit locking; Cancel/Escape restores focus without locking the form. Approval keeps the submitted `expectedReviewRevision`. Password visibility controls do not change validation rules. Login/register preserve a validated same-origin return destination. Missing auth scripts leave the submit button disabled with an explanation; failed requests restore the button and its icon.

Search inputs do not show an inner blue focus rectangle. Focus uses a muted border and subtle outer shadow on the complete search bar, and other controls use muted keyboard outlines. Text fields, textareas and selects use the same subtle focus border/shadow across all forms; invalid fields retain their red boundary. Forms use linked error summaries plus messages at each invalid field, preserving entered values. Profile fields normalize whitespace before validation. Your own review editor remains available independently of the community review page.

Names, category labels, chips, breadcrumbs and release headings wrap within their containers, including maximum-length unbroken content. Mobile inputs use 16px text. Broken avatar images fall back to initials.

Control feedback uses 150ms transitions; drawers/dialogs use 220ms; public entrances use 400ms and the first six cards reveal at 360ms with 35ms stagger. Shared route transitions are an optional browser enhancement in `public-motion.css`. `prefers-reduced-motion` disables animations, transitions and smooth scrolling. Content is visible without observers or JavaScript.

## Data and ownership contracts

The homepage requests two six-item published-tool pages and shares one deduplicated review summary batch across both sections. Scores, counts and views come from the existing services. Browse rating sorting still occurs in the database before pagination, with unrated items last and the existing tie-break.

Authentication, REST DTOs, error codes, tool locks, DRAFT mutation guards and approval revisions keep their existing contracts. Presentation-only validation messages live in `UiText`. Static assets alone receive additional anonymous GET/HEAD access. Hidden tool metadata stays private in My reviews.

## Review workflow

This redesign is kept in the local worktree `worktrees/primeskill-redesign`. Do not push or merge it until the user has inspected and accepted the local preview. Verification details and preview accounts are in `doc/redesign-local-review.md`.


E follow-up: blank tool names use Thai presentation-only validation with form retention. Error handlers restore the shared session menu model; error actions expose sign-in/workspace/moderation according to the current actor. Verification and the credential-free handoff bundle are recorded in `doc/role-c-new-ui-local-2026-10-10.md`.
