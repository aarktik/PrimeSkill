# Vercel real browser acceptance — 10 October 2026

## Environment and evidence boundaries

Preview https://primeskill-3u6vbsb2f-aarktiks-projects.vercel.app serves
3913c92498c5e3826f1bb363ebe256352bdd82c5 against the approved Supabase project
jayichnxeyhvnmranutd using a restricted runtime database role and shared JDBC
sessions. Production rollout is a separate gate. Preview has Vercel SSO protection.
No account passwords, hashes, cookies or session identifiers are in this report.
The operator created the three acceptance accounts and explicitly approved only
admin account id=1 becoming ADMIN. Other accounts remain USER. A USER becomes an
owner by creating a Tool; owner is not an additional authority.

## Verified through the real browser

- Owner USER: authenticated Profile, My tools and My reviews; admin access denied.
  Profile update persisted, survived navigation/new tab, and original bio was
  restored. Logout followed by workspace access redirected to login.
- Admin: created category/tag, Tool id=1 and version 0.0.1; edited draft details
  and release notes; assigned/removed/reassigned a tag. Submitted to PENDING,
  returned to DRAFT, edited/resubmitted, then approved to PUBLISHED. Pending and
  Published tags/releases had no mutation controls. Published Tool appeared in
  Browse with category=1, tags=acceptance-test and sort=rating. Creator was
  prevented from reviewing their own Tool. Logout succeeded.
- Member USER id=2: Profile identity confirmed. Access to admin and another
  owner's tag management denied. Created a 5-star review on published Tool1,
  edited it to 4 stars; Tool detail and Browse both displayed 4.0/5 with one
  review. My reviews displayed the updated content.
- Same USER as owner of Tool2: created/edited DRAFT, assigned/removed a tag,
  created/edited version 0.0.1, submitted to PENDING. Versions and tags became
  read-only. The previous edit URL was rejected because only DRAFT is editable.
- Independent read-only PostgreSQL queries confirmed the profile restoration,
  roles, Tool owners/states/details, versions, tag assignments and member review.

Reference fixtures remain: Tool1 PUBLISHED owned by admin, Tool2 PENDING owned by
member. Permanent review/version/Tool deletion was not performed. Review deletion
requires specific operator confirmation. This report does not certify every
negative HTTP mutation, concurrent race or browser/device combination in production.
The Java/PostgreSQL suites cover additional security and concurrency contracts.

## Animation follow-up

Browser console observed InvalidStateError cancellations with reasons
`ViewTransition opt-in disabled` and `Page already revealed`; operations still
completed. Existing ready rejection handling covered the first reason only.
The follow-up patch narrowly handles both observed cancellation reasons while
preserving unrelated errors. Node regression tests first failed for the second
reason on both pageswap/pagereveal, then passed 4/4 after the patch. CI runs these
tests. A new deployment/browser navigation check is still required before claiming
the live animation observation is resolved; older Preview evidence is not reused
as proof of the patch.

## Test evidence

3913c92: local Java17 Surefire381 + PostgreSQL Failsafe349 =730 tests, no
failures/errors/skips; Python deployment configuration3 passed; GitHub8 checks
passed. Animation patch: Node4 passed locally; final-SHA CI must be checked.

Restricted screenshots and detailed operator ledger are under the operator's
PrimeSkill-local-team deliverables directory. Portable screenshots include
admin-published-browse.jpg, admin-published-readonly.jpg,
member-review-browse-four-stars.jpg and member-owner-pending-readonly.jpg.

## Rollout and remaining checks

### Production rollout on 10 October 2026

PR8 merged as `812e07ad2492d9eecaf2d3ab2bbe47a4a48367a5` after all eight
head checks passed. Vercel Production deployment
`91NwhhvxgAW4ZJ1p21HKcdDaAssA` became Ready and serves
https://primeskill-zeta.vercel.app with the real Supabase database.
The merged tree equals the tested PR head. Merge-SHA verify and PostgreSQL
integration checks passed.

Production HTTP acceptance passed 16/16 checks, including public pages/API,
health, unauthenticated workspace redirects/admin API denial, disabled API docs,
Secure session cookie, persisted CSRF retrieval, missing-CSRF rejection and
invalid-credential rejection. Health requires a JSON Accept header.

Operator login and browser checks verified admin approval of USER-owned Tool2,
deprecation and logout. The USER owner restored Tool2 to DRAFT. Member review
creation (5), edit (4), detail/home/Browse summary (4.0), and permanent deletion
of test review id=2 passed; the operator explicitly approved that deletion.
My reviews returned to empty. USER admin access and cross-owner tag access were
denied. Tool1 remains PUBLISHED; Tool2 is DRAFT; test reference data remains.

Production still logged browser ViewTransition cancellations despite the narrow
JavaScript handler. The follow-up disables optional cross-document transitions;
normal navigation and existing in-page motion remain. A regression first failed
against `navigation:auto` and passes against `navigation:none`. Live confirmation
on the follow-up deployment remains required. Prior console logs are preserved.

1. Verify follow-up SHA CI and Preview navigation without cross-document transitions.
2. Integrate the follow-up and verify its Production source SHA, public routes and
   navigation console. Role workflows above are from Production 812e07a.
3. Keep S1 session tables and existing business data. S1 was already applied:
   do not rerun it. Roll application back to a compatible verified deployment if
   rollout fails; do not drop session/business tables as automatic rollback.

See vercel-deployment-runbook.md for deployment, secrets, rollback and session
lifecycle. Do not share credentials through Git or PR comments.
