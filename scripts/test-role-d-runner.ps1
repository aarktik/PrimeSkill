param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [ValidateRange(1024,65535)][int]$Port = 15433
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$testDirectory = Join-Path $repo ('code/target/runner-preparation-tests/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDirectory -Force | Out-Null
$mavenStub = Join-Path $testDirectory 'maven-preparation-stub.ps1'
[IO.File]::WriteAllText($mavenStub, "Write-Output 'PREPARATION TEST ONLY: Maven is stubbed; no Java suite ran.'`nexit 0`n")
$checks = 0
function Assert-Preparation([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Preparation regression: $Message" }
    $script:checks++
}

# These test runner preparation only. A disposable cluster is still started/stopped,
# but the Maven stub does not compile Java or execute application tests.
foreach ($case in @(
    @{ Ref='0a92dbc3a80538917bc400d8862d8644c5dd4ec9'; Existing=$false },
    @{ Ref='3187098fac9d91c14d1fdfe40bb765797cde8cfd'; Existing=$true }
)) {
    $logPath = Join-Path $testDirectory ($case.Ref.Substring(0,7) + '.log')
    & (Join-Path $repo 'scripts/check-role-d-postgres.ps1') -RoleDRef $case.Ref `
        -PostgresBin $PostgresBin -Port $Port -MavenCommand $mavenStub `
        -IncludePublishingRace -ApplyReviewLockCandidate *> $logPath
    Assert-Preparation ($LASTEXITCODE -eq 0) 'Runner must finish preparation.'
    $log = [IO.File]::ReadAllText($logPath)
    $match = [regex]::Match($log, '(?m)^Isolated checkout: (.+)\r?$')
    Assert-Preparation $match.Success 'Runner must record checkout provenance.'
    $checkout = $match.Groups[1].Value.Trim()
    $lockSource = [IO.File]::ReadAllText((Join-Path $checkout 'code/src/main/java/com/example/toolhub/repository/ToolRepository.java'))
    $toolSource = [IO.File]::ReadAllText((Join-Path $checkout 'code/src/main/java/com/example/toolhub/domain/entity/Tool.java'))
    Assert-Preparation (([regex]::Matches($lockSource,'Optional\s*<\s*Tool\s*>\s+findForUpdateById\s*\(')).Count -eq 1) 'Exactly one Tool lock.'
    Assert-Preparation (([regex]::Matches($toolSource,'void\s+changeStatus\s*\(')).Count -eq 1) 'Exactly one status mutator.'
    $pom = [xml][IO.File]::ReadAllText((Join-Path $checkout 'code/pom.xml'))
    $profiles = $pom.SelectNodes("/*[local-name()='project']/*[local-name()='profiles']/*[local-name()='profile'][*[local-name()='id']='postgres-it']")
    Assert-Preparation ($profiles.Count -eq 1) 'Exactly one PostgreSQL Maven profile.'
    $reviewSource = [IO.File]::ReadAllText((Join-Path $checkout 'code/src/main/java/com/example/toolhub/service/impl/ReviewServiceImpl.java'))
    Assert-Preparation ($reviewSource.Contains('entityManager.refresh(tool)')) 'Review freshness protection must remain.'
    foreach ($fixture in @('RoleDPostgresIT.java','ReviewPublishingRacePostgresIT.java')) {
        Assert-Preparation (Test-Path -LiteralPath (Join-Path $checkout "code/src/test/java/com/example/toolhub/$fixture")) "Missing fixture: $fixture"
    }
    if ($case.Existing) {
        Assert-Preparation ($log.Contains('Candidate skipped:')) 'Already fixed review must skip candidate.'
        Assert-Preparation ($log.Contains('Existing postgres-it profile preserved.')) 'Existing profile must be retained.'
        # Only added test fixtures should differ; tracked production/config/profile bytes must match.
        $changed = & git -C $checkout diff --name-only $case.Ref -- code/src/main code/pom.xml `
            code/src/test/resources code/src/test/java/com/example/toolhub/ToolHubApplicationTests.java `
            code/src/test/java/com/example/toolhub/support scripts/test-postgres.ps1
        Assert-Preparation (-not $changed) 'Combined production/config/profile must remain unchanged.'
    } else {
        Assert-Preparation (-not $log.Contains('Candidate skipped:')) 'Legacy candidate must actually apply.'
        Assert-Preparation (Test-Path -LiteralPath (Join-Path $checkout 'code/src/test/resources/application.properties')) 'Legacy fallback test defaults must exist.'
    }
}
Write-Host "Runner preparation checks passed: $checks. Maven was stubbed; this is not an application-suite result."
Write-Host "Preparation logs: $testDirectory"
