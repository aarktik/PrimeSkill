# Java17 local verification — 9 October 2026

Delivery update: [รายงานส่งมอบบน personal branch](role-e-delivery-handoff.md) เป็นสถานะล่าสุด. ข้อมูล worktree/commit/push และผลทดสอบด้านล่างที่ระบุรอบก่อนเป็นประวัติการเตรียมงาน.

## Configuration

- Installed Temurin17.0.20.1+1 side by side with existing25/26 under `C:\Users\ACER\.jdks\jdk-17.0.20.1+1`.
- Official Adoptium Windows x64 JDK archive SHA256 verified before extraction: `e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0`.
- `JAVA17_HOME` persisted for this user; system/global JAVA_HOME and PATH unchanged.
- POM Enforcer3.6.3 requires `[17,18)` at lifecycle validation. Existing compilation target remains17. Applied to personal checkout and E candidate without importing D's extra dependencies into personal checkout.
- Added `.java-version`, Windows Maven wrapper and local ignored VS Code runtime/terminal settings. Default PostgreSQL runner selects the wrapper automatically. See [usage](java17-development.md).

## Verification evidence

**Final default-runner result:** `default-runner-verify.log` exit0 / BUILD SUCCESS in3m18s: **Surefire265 + PostgreSQL185 =450**, failures/errors/skips0. `default-shell-runtime.txt` reports26.0.1, while all53 XML suites report17.0.20.1 (`final-runtime-proof.json`); this proves the default test runner chooses17 automatically. Fresh clean ran before the cluster started (`final-clean.log`). Coverage gate passed (`final-coverage-summary.json`); cluster stopped and no listener15432 remains. All3 migration rehearsal startup logs independently confirm Java17 and the validation PASS marker.

Evidence root: `D:\PrimeSkill-worktrees\evidence\java17`.

- Before Enforcer, Java26 `validate` succeeded (`java26-before-guard.log`); after configuration it fails early with the JDK17 message (`java26-rejected.log`). Java17 validate passes in both candidate and personal checkout.
- Wrapper rejects JAVA17_HOME pointing to26 and restores caller JAVA_HOME/PATH after success. Original advanced-script parameter binding misread Maven `-o` as ambiguous PowerShell common parameter (`wrapper-args-red.log`). Switched to simple-script `$args`; `-o -v` and `-e -o ... validate` pass (`wrapper-args-green.log`, `wrapper-validate-green.log`). Checks require both java.exe and javac.exe.
- Clean build completed before starting disposable PostgreSQL (`clean.log`). First full verify on candidate: **Surefire265 + PostgreSQL185 =450**, failures/errors/skips0 (`full-verify.log`, `coverage-summary.json`). XML properties for all53 suites report `java.version=17.0.20.1`; production class major61 (`runtime-proof.json`). This is actual Java17 execution, not just release17 compilation on26.
- Java17 migration rehearsal PASS: empty initialized schema startup; legacy full-schema fixture backup/migration/rerun; original contents/relationships preserved; restore original schema then remigrate and actual app validation with SQL init disabled. Archived evidence: `first-rehearsal/83d3d8ab5c224a73a29d92ad089089c0/summary.json`. Restore+remigrate+validation13.1s applies only to this small fixture.
- First Maven run reports a9h32 wall-clock gap; do not use it as a performance benchmark. A separate default-runner clean verification tests automatic JDK17 selection from an unchanged Java26 shell.

## Worktree and evidence safety

Git worktree move was denied by Windows. Copied tracked/untracked candidate, archived prior evidence, then `git worktree repair` registered `D:\PrimeSkill-worktrees\b1-role-e` on the same `codex/b1-role-e` branch/base3187098. Old directory retains an inactive `.git.archived` snapshot. No uncommitted code was discarded. This keeps the active candidate outside the personal checkout's Maven target directory.

Prior Java26 evidence is archived under `D:\PrimeSkill-worktrees\evidence\before-java17`. Current reports should cite Java17 evidence above rather than claim the old results ran on17.

## Limits

- Full450 verify concerns the uncommitted integrated E candidate, not a new commit or a full verification of personal branch59feba0. Personal checkout's Java17 configuration was validated separately.
- PostgreSQL18.6 locally; new GitHub CI/PG17/Docker run not performed. No shared database changes or commit/push/merge.
- Fresh reviewer identified the flag-forwarding issue; it was reproduced and fixed. Reviewer hit a usage limit before final sign-off; author inspection and executable guard/argument/runtime checks supplement it. This is not team approval.

Reference: [Maven Enforcer requireJavaVersion](https://maven.apache.org/enforcer/enforcer-rules/requireJavaVersion.html).
