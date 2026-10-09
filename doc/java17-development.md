# Java 17 — PrimeSkill development

Primary build/test runtime is JDK17. `java.version=17` keeps compilation targeting17; Maven Enforcer additionally requires the JVM running Maven to be17 (`[17,18)`). This avoids treating a release17 compilation on JDK26 as a Java17 runtime test.

## This machine

- Temurin JDK17.0.20.1+1: `C:\Users\ACER\.jdks\jdk-17.0.20.1+1`.
- Persistent user variable `JAVA17_HOME` points to that installation. Java25/26 remain installed; system/global JAVA_HOME and PATH were not replaced.
- Ignored `.vscode/settings.json` in the personal checkout and candidate selects JavaSE-17 and supplies Java17 to new VS Code terminals. Already-open terminals or other applications may still use26.
- Use the project wrapper below from any shell; it temporarily sets JAVA_HOME/PATH, runs Maven, restores the caller environment and preserves Maven's exit code.

## Commands (from repository root)

```powershell
./scripts/mvn-java17.ps1 -version
./scripts/mvn-java17.ps1 -B -f code/pom.xml test
./scripts/test-postgres.ps1
```

`test-postgres.ps1` now defaults to the Java17 Maven wrapper. Custom `-MavenCommand` callers must supply Java17 themselves; Enforcer rejects another JVM for this project's lifecycle builds. The wrapper is Windows-specific; Linux CI already selects Java17 with setup-java.

On another Windows machine, install JDK17 and set `JAVA17_HOME` to that machine's own path. No user-specific absolute installation path is committed in the wrapper/POM. `.java-version` is an advisory marker for compatible version managers, not an installer.

## Clean and migration rehearsal

Run clean **before** starting the disposable PostgreSQL runner: its cluster lives under `code/target`. Never clean that directory while the runner is active. Keep uncommitted worktrees/evidence outside target.

The active candidate now resides at `D:\PrimeSkill-worktrees\b1-role-e`. Prior Java26 evidence was archived under `D:\PrimeSkill-worktrees\evidence\before-java17`. The old target-based directory is a retained snapshot with `.git.archived`, not the active worktree. New Java17 evidence is under `D:\PrimeSkill-worktrees\evidence\java17`.

The migration rehearsal launches Java separately from Maven. Run it with JAVA_HOME/PATH17 for the whole rehearsal session or pass `-JavaCommand <jdk17>\bin\java.exe`; the Maven wrapper restores PATH on return and does not change a later standalone `java` command.

Official reference: [Maven Enforcer Java version rule](https://maven.apache.org/enforcer/enforcer-rules/requireJavaVersion.html).
