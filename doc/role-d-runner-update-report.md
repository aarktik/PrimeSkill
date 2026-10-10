# Role D/E runner update — 8 October 2026

## Purpose

Adapt `scripts/check-role-d-postgres.ps1` to D's implemented lock and the combined D/E checkout with A's test config. Continue supporting the pinned pre-fix snapshot without modifying the user's working branch or replacing existing combined implementation/config.

## Changes

- Existing publishing source, Tool lock and changeStatus are preserved; missing parts are supplied only for legacy composition. Duplicate lock/status definitions stop preparation instead of adding another.
- Existing test guard, PostgreSQL script/profile and A's application-test configuration are preserved. Legacy D gets fallback test defaults only when neither A's profile nor existing default test config is present.
- Existing compatible Maven postgres-it profile is validated and left byte-for-byte unchanged. If absent, only that profile is imported; unrelated profiles are retained. Duplicate/incompatible PostgreSQL profiles require manual review.
- An explicitly requested candidate patch is skipped when ReviewService already contains a Tool lock. It can still be applied to the pre-fix snapshot. Normal combined-branch use does not need the candidate flag.
- Supabase process variables and inherited MAVEN_ARGS are cleared/restored, preventing an inherited test selector from silently shortening the verification.
- Baseline and overlay results remain separate. External RoleDPostgresIT and, when requested, ReviewPublishingRacePostgresIT are copied into isolated test sources; no merge, commit, push or shared migration is performed.

## Reproduced problem

Before changing the runner, the command targeting 3187098 with IncludePublishingRace stopped with `D now defines profiles; review the overlay instead of overwriting them.` The old preparation also inserted lock/status methods unconditionally. Reproduction log: `code/target/runner-existing-profile-red.log`. That reproduction selected only the application context baseline to reach preparation quickly; it is not a full suite result.

## Preparation regressions

Run `./scripts/test-role-d-runner.ps1` from the E repository root. Requires local PostgreSQL binaries; uses a separate disposable cluster on configurable port 15433.

The script checks legacy `0a92dbc` and combined `3187098`: exactly one Tool lock, one status mutator, one PostgreSQL profile, freshness protection, copied fixtures, legacy candidate/defaults, and preservation of combined production/config/profile. **21 checks passed.** Maven is deliberately stubbed for these preparation tests; these 21 checks are not Java/application test cases.

Latest preparation log: `code/target/runner-preparation-regressions.log`; individual fixture logs: `code/target/runner-preparation-tests/9e916105bbad414ca1185fcc2715dbb1/`.

## Full verification command

```powershell
./scripts/check-role-d-postgres.ps1 -RoleDRef 3187098fac9d91c14d1fdfe40bb765797cde8cfd -IncludePublishingRace -ApplyReviewLockCandidate
```

The last flag is intentionally included here to verify that an already implemented candidate is skipped. For normal use omit it.

Pinned checkout: `code/target/role-d-checks/role-d-3187098-d143cecc`. Runner log: `code/target/runner-3187098-green.log`.

## Full results

- Unmodified Maven baseline: exit 0. Its log is baseline-verify.log in the pinned checkout.
- Expanded overlay: **Surefire Tests 247 / Failures 0 / Errors 0 / Skipped 0**; **Failsafe Tests 145 / Failures 0 / Errors 0 / Skipped 0**, total **392**. XML counts were verified in the fresh checkout; Maven BUILD SUCCESS and runner exit 0.
- Includes `RoleDPostgresIT` 28, `ReviewPublishingRacePostgresIT` 10, native concurrent `ReviewPublishingRollbackPostgresIT` 4 and sequential `ReviewServiceLockRollbackPostgresIT` 4. All passed, with no skips/errors/failures.
- The original unmodified PostgreSQL run was 247 + 107 = 354. This overlay adds D HTTP/service fixture 28 and E race fixture 10; do not present 392 as the count of the unmodified commit.
- Verified tracked production source, Maven pom, test resources, A's application test, existing guard and PostgreSQL script have no diff against 3187098 in the overlay checkout. Runtime log confirms candidate skipped and existing profile preserved.
- Local environment: Java 26.0.1/PostgreSQL 18.6. Script stopped the cluster; neither test port 15432 nor preparation port 15433 had a listener after completion.

The race fixture includes the original six commit-order/stale-cache cases and four E rollback cases. Their 10/10 passing result also supplies the previously missing external race-harness evidence against the combined production implementation; no candidate was applied for that result.

## Limits

This runner validates supported existing profile structure rather than rewriting arbitrary future Maven/Java configurations. If the target changes these interfaces, inspect the failure and update the harness deliberately. Older 0a92dbc application results and the unmodified 3187098 baseline must remain distinct from the expanded test overlay. CI changes and Java 17 runtime verification of this updated runner remain separate work.
