# Primeskill — Shared UI Design Guide

> คู่มือหน้าตาเว็บร่วมสำหรับ A–E · v1.0  
> ใช้ร่วมกับ [Implementation Plan](IMPLEMENTATION_PLAN.md); เอกสารนี้กำหนดวิธีแสดงผล ไม่เปลี่ยน feature/authorization contract

## Direction and tokens

โทน light, สะอาด อ่านง่าย มี whitespace; reference คือบรรยากาศ Calendly ใน Land-book ไม่ใช่การคัดลอก. ใช้ภาษาไทยเป็นหลัก, Noto Sans Thai + `system-ui, sans-serif`; primary button น้ำเงินเข้มเหมือนกันทุกหน้า, gradient เฉพาะ hero/banner, ไม่มี animation ซับซ้อน.

| Token | Value |
|---|---|
| `--ps-bg`, `--ps-surface`, `--ps-text`, `--ps-muted` | `#F8FAFC`, `#FFF`, `#102A43`, `#526477` |
| `--ps-primary`, `--ps-primary-hover`, `--ps-link`, `--ps-soft` | `#123B5D`, `#0B2942`, `#175CD3`, `#EAF2FF` |
| `--ps-border`, `--ps-control-border` | `#D8E1EA`, `#7C8B9A` |
| `--ps-success`, `--ps-warning`, `--ps-danger` | `#166534`, `#92400E`, `#B42318` |

Badge backgrounds: success `#DCFCE7`, warning `#FEF3C7`, danger `#FEE4E2`, neutral `#EEF2F6`. Hero: `linear-gradient(135deg,#E2EFF2 0%,#D9E9FF 55%,#E9E4FA 100%)`. Status always has text, never color-only.

Type: hero 48/32px 700, H1 32/26px 700, H2 24/22px 600, H3 18px 600, body/input 16px, button/label 14px 600, caption/badge 13px. Thai line-height 1.6; headings 1.3. Spacing scale 4/8/12/16/24/32/48/64px; controls radius 8px, cards 16px, badges pill; card shadow `0 8px 24px rgba(16,42,67,.08)`.

## Layout and navigation

Public: sticky white 72px navbar, page header/hero, centered max-width 1200px content, normal footer. Gutters: 24px desktop, 16px mobile. Dashboard: navbar + 240px white sidebar below it and main background `--ps-bg`; main padding 32px desktop/16px mobile. Auth: logo plus max 440px form card, no sidebar.

Navbar: logo `/`, explore `/tools`; anonymous gets login ghost + register primary; authenticated gets add-tool primary + avatar menu (my tools/profile/admin only for ADMIN/logout POST with CSRF). At <768px use one accessible menu/drawer; `aria-expanded`, Escape and focus return apply. Sidebar active uses soft background, primary 600 text, `aria-current="page"`; hide admin items for USER but enforce backend authorization.

Page header order: breadcrumb → one H1 plus primary action → short description. On mobile action moves below and may be full width.

## Components

Buttons use `.ps-btn`: min height 44px, padding 12px 20px, gap 8px. Variants: `--primary` save/register/submit/add, `--secondary` cancel/back, `--ghost` navigation/edit/menu, `--danger` destructive confirmation. Use `<a>` for navigation and `<button>` for mutation; non-submit buttons state `type="button"`; icon-only buttons need accessible names. Focus visible uses 3px link outline/3px offset; disabled uses `.5` opacity and explains why when relevant; loading locks resubmission and says “กำลังบันทึก…”.

Forms: labels always above controls (placeholder only example), input/select min 44px, textarea min 120px, white/control border/padding 12px. Label gap 8px, field gap 24px. Errors use danger border/text plus `aria-invalid`/`aria-describedby`. Proper input types and autocomplete; preserve safe values after validation but never password. Inline post-redirect alerts: success `role=status`, important error `role=alert`, never leak server internals.

Tool card order: 48px icon/initial + linked name; category/short description max 3 lines; max 3 tags + `+N`; real rating or “ยังไม่มีรีวิว”; publisher/date. White bordered 16px card, subtle hover, equal grid height; do not nest controls inside card link. Status mapping: DRAFT=แบบร่าง neutral, PENDING=รออนุมัติ warning, PUBLISHED=เผยแพร่แล้ว success, DEPRECATED=เลิกเผยแพร่ neutral. User changes status only through allowed actions.

Search is GET: full-row labeled search, category/tag filters, result count and allowed sort labels (ใหม่ล่าสุด/ยอดนิยม/คะแนนสูงสุด/เกี่ยวข้องที่สุด). Desktop filter 240px left; mobile collapsible filter with apply/reset. Changing filter/sort resets page; pagination preserves query and disabled endpoints are not clickable.

Tables have 56px rows, background header, action last; use empty state; mobile horizontal scroll in table boundary or stacked cards. Detail tabs: รายละเอียด/เวอร์ชัน/รีวิว. Dropdown and modal must support Escape/outside close/focus management; destructive confirmation starts focus on cancel. Modal max 480px, 24px padding, 40% overlay and names actual effect.

Empty states have simple icon, title, one-line explanation and relevant action. Required copy: no search result “ไม่พบเครื่องมือที่ตรงกับการค้นหา”; none of own tools “เริ่มเผยแพร่เครื่องมือแรกของคุณ”; no reviews “ยังไม่มีรีวิวสำหรับเครื่องมือนี้”; 404/403/server error use short safe messages + return/retry action.

## Pages and responsive rules

| Owner/page | Layout/components/action |
|---|---|
| B+C home | public hero/search/cards; explore |
| A auth/profile | auth form or dashboard form/alert; register-login/save |
| C browse | public header/search/filter/grid/pagination; search |
| B+D+E detail | tool header/tags/rating/tabs; valid external website action |
| B my tools/editor | dashboard table/form/status; add/save draft |
| D reviews | detail section; accessible 1–5 radio rating + textarea/list; submit/save |
| E versions/moderation | dashboard list/form/badges or admin queue/confirm; add/approve |

At <768px: one column, drawer, collapsible filter, full-width forms. 768–1023: two-card grid and dashboard drawer. ≥1024: 240px sidebar and three cards only if card content area remains ≥240px. Test 375, 768, 1280; never allow whole-page horizontal overflow.

## Thymeleaf implementation agreement

```text
templates/fragments/{head,navbar,sidebar,footer,page-header,alerts,tool-card,pagination,status-badge}.html
templates/{auth,profile,tools,reviews,versions,admin}/
static/css/{tokens,components,pages}.css
static/js/ui.js
```

Use `th:replace`/`th:insert`, never copied navigation. Controllers provide `pageTitle`, `activeNav` (`explore|my-tools|profile|moderation|categories|tags`), necessary user data and common flash shape. Prefix CSS `ps-`; scope page styles (e.g. `.ps-profile`), load tokens → components → pages. Shared baseline must be one CSS system; if Bootstrap is chosen, choose one version and map it to tokens. Web controllers use the same services as REST; mutations contain CSRF and redirect after success.

```css
:root { --ps-bg:#f8fafc; --ps-surface:#fff; --ps-text:#102a43; --ps-muted:#526477; --ps-primary:#123b5d; --ps-primary-hover:#0b2942; --ps-link:#175cd3; --ps-soft:#eaf2ff; --ps-border:#d8e1ea; --ps-control-border:#7c8b9a; --ps-success:#166534; --ps-warning:#92400e; --ps-danger:#b42318; --ps-radius-control:8px; --ps-radius-card:16px; --ps-shadow:0 8px 24px rgba(16,42,67,.08); --ps-nav-height:72px; --ps-sidebar-width:240px; --ps-content-width:1200px; }
* { box-sizing:border-box; } body { margin:0; background:var(--ps-bg); color:var(--ps-text); font-family:"Noto Sans Thai",system-ui,sans-serif; font-size:16px; line-height:1.6; }
.ps-btn { display:inline-flex; align-items:center; justify-content:center; gap:8px; min-height:44px; padding:12px 20px; border:1px solid transparent; border-radius:var(--ps-radius-control); font:inherit; font-size:14px; font-weight:600; text-decoration:none; cursor:pointer; transition:background-color 150ms,border-color 150ms; }
.ps-btn--primary { background:var(--ps-primary); color:#fff; }.ps-btn--primary:hover:not(:disabled){background:var(--ps-primary-hover)}.ps-btn--secondary{background:var(--ps-surface);border-color:var(--ps-control-border);color:var(--ps-text)}.ps-btn--ghost{background:transparent;color:var(--ps-text)}.ps-btn--secondary:hover:not(:disabled),.ps-btn--ghost:hover:not(:disabled){background:var(--ps-soft)}.ps-btn--danger{background:var(--ps-danger);color:#fff}.ps-btn--danger:hover:not(:disabled){background:#912018}.ps-btn:disabled{opacity:.5;cursor:not-allowed}:focus-visible{outline:3px solid var(--ps-link);outline-offset:3px}@media (prefers-reduced-motion:reduce){.ps-btn{transition:none}}
```

Before splitting pages: appoint shared UI owner; implement tokens/buttons/inputs/layout fragments; add a development component showcase; all A–E consume it; change shared components only at source via PR and review all callers. Pre-merge check: tokens/spacings/labels consistent, common nav active, state handling, keyboard/focus/labels, 375px works, UI hides unauthorized options but server rechecks, and page CSS does not leak globally.
