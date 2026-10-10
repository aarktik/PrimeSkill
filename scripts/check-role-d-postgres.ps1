param(
    [string]$RoleDRef = 'origin/naphat_67338800363_02',
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [ValidateRange(1024,65535)][int]$Port = 15432,
    [string]$MavenCommand = 'mvn',
    [switch]$IncludePublishingRace,
    [switch]$ApplyReviewLockCandidate
)
$ErrorActionPreference = 'Stop'
if ($ApplyReviewLockCandidate -and -not $IncludePublishingRace) { throw 'The lock candidate requires -IncludePublishingRace.' }
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$maven = Get-Command $MavenCommand -ErrorAction Stop
$commit = (& git -C $repo rev-parse --verify --end-of-options "$RoleDRef^{commit}")
if ($LASTEXITCODE -ne 0) { throw 'Role D ref is unavailable; fetch it before running this script.' }
$runName = 'role-d-' + $commit.Substring(0,7) + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
$checkout = Join-Path $repo "code/target/role-d-checks/$runName"
if (Test-Path -LiteralPath $checkout) { throw 'Checkout path already exists; refusing to overwrite it.' }
& git clone --shared --no-hardlinks --no-checkout $repo $checkout
if ($LASTEXITCODE -ne 0) { throw 'Could not create isolated checkout.' }
& git -C $checkout checkout --detach $commit
if ($LASTEXITCODE -ne 0) { throw 'Could not select the pinned D commit.' }
Write-Host "Role D commit: $commit"
Write-Host "Isolated checkout: $checkout"
Push-Location $checkout
$databaseVariables = @('SUPABASE_DB_URL','SUPABASE_DB_USERNAME','SUPABASE_DB_PASSWORD','MAVEN_ARGS')
$originalDatabaseValues = @{}
foreach ($variable in $databaseVariables) {
    $originalDatabaseValues[$variable] = [Environment]::GetEnvironmentVariable($variable,'Process')
    [Environment]::SetEnvironmentVariable($variable,$null,'Process')
}
try {
    & $maven.Source -B -f code/pom.xml verify *> baseline-verify.log
    $baselineExit = $LASTEXITCODE
    Write-Host "Unmodified D baseline exit code: $baselineExit (see baseline-verify.log)"
    # Continue after recording baseline failures so PostgreSQL environment gaps can be distinguished.
    foreach ($directory in @('scripts','code/src/test/resources','code/src/test/java/com/example/toolhub/support')) {
        New-Item -ItemType Directory -Path (Join-Path $checkout $directory) -Force | Out-Null
    }
    # Preserve the target's existing runtime/profile/guard; fill missing test support only.
    foreach ($relative in @(
        'scripts/test-postgres.ps1',
        'code/src/test/java/com/example/toolhub/support/PostgresTestDatabaseGuard.java',
        'code/src/test/resources/application-postgres-test.properties'
    )) {
        $destination = Join-Path $checkout $relative
        if (-not (Test-Path -LiteralPath $destination)) {
            Copy-Item -LiteralPath (Join-Path $repo $relative) -Destination $destination
        }
    }
    # Legacy D lacks A's test profile. Only then supply fallback defaults; never replace A's config.
    $testDefaultsPath = Join-Path $checkout 'code/src/test/resources/application.properties'
    $aProfilePath = Join-Path $checkout 'code/src/test/resources/application-test.properties'
    if (-not (Test-Path -LiteralPath $aProfilePath) -and -not (Test-Path -LiteralPath $testDefaultsPath)) {
        $productionDefaults = [IO.File]::ReadAllText((Join-Path $checkout 'code/src/main/resources/application.properties'))
        $testDatabaseDefaults = [IO.File]::ReadAllText((Join-Path $repo 'code/src/test/resources/application-postgres-test.properties'))
        [IO.File]::WriteAllText((Join-Path $checkout 'code/src/test/resources/application.properties'),"$productionDefaults`n$testDatabaseDefaults",[Text.UTF8Encoding]::new($false))
    }
    Copy-Item -LiteralPath (Join-Path $repo 'test/role-d-postgres/RoleDPostgresIT.java') -Destination code/src/test/java/com/example/toolhub/RoleDPostgresIT.java
    if ($IncludePublishingRace) {
        $publishingFiles = @(
            'domain/enums/PublishingAction.java',
            'service/PublishingService.java',
            'service/impl/PublishingServiceImpl.java',
            'service/publishing/PublishingState.java',
            'service/publishing/PublishingStateMachine.java',
            'exception/InvalidStateTransitionException.java'
        )
        foreach ($file in $publishingFiles) {
            $relative = 'code/src/main/java/com/example/toolhub/' + $file
            $destination = Join-Path $checkout $relative
            New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
            if (-not (Test-Path -LiteralPath $destination)) {
                Copy-Item -LiteralPath (Join-Path $repo $relative) -Destination $destination
            }
        }
        $repositoryPath = Join-Path $checkout 'code/src/main/java/com/example/toolhub/repository/ToolRepository.java'
        $repository = [IO.File]::ReadAllText($repositoryPath)
        $lockDefinitions = [regex]::Matches($repository, 'Optional\s*<\s*Tool\s*>\s+findForUpdateById\s*\(').Count
        if ($lockDefinitions -gt 1) { throw 'Target ToolRepository already has duplicate lock methods.' }
        if ($lockDefinitions -eq 1 -and -not $repository.Contains('PESSIMISTIC_WRITE')) { throw 'Existing Tool lock needs manual review.' }
        if ($lockDefinitions -eq 0) {
            $repository = $repository.Replace('public interface ToolRepository extends JpaRepository<Tool, Long> {', @'
public interface ToolRepository extends JpaRepository<Tool, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tool t where t.id = :id")
    Optional<Tool> findForUpdateById(@Param("id") Long id);
'@)
            if (-not $repository.Contains('findForUpdateById')) { throw 'Could not insert the missing Tool lock method.' }
            [IO.File]::WriteAllText($repositoryPath,$repository,[Text.UTF8Encoding]::new($false))
        }
        $toolPath = Join-Path $checkout 'code/src/main/java/com/example/toolhub/domain/entity/Tool.java'
        $toolSource = [IO.File]::ReadAllText($toolPath)
        $statusDefinitions = [regex]::Matches($toolSource, 'void\s+changeStatus\s*\(').Count
        if ($statusDefinitions -gt 1) { throw 'Target Tool already has duplicate changeStatus methods.' }
        if ($statusDefinitions -eq 0) {
            $toolSource = $toolSource.Replace('    public void updateCategory(', '    public void changeStatus(ToolStatus status) { this.status = status; }' + "`n" + '    public void updateCategory(')
            if (-not $toolSource.Contains('void changeStatus(')) { throw 'Could not insert the missing Tool status method.' }
            [IO.File]::WriteAllText($toolPath,$toolSource,[Text.UTF8Encoding]::new($false))
        }
        Copy-Item -LiteralPath (Join-Path $repo 'test/review-publishing-race/ReviewPublishingRacePostgresIT.java') -Destination code/src/test/java/com/example/toolhub/ReviewPublishingRacePostgresIT.java
        $reviewSource = [IO.File]::ReadAllText((Join-Path $checkout 'code/src/main/java/com/example/toolhub/service/impl/ReviewServiceImpl.java'))
        $existingReviewLock = $reviewSource.Contains('findForUpdateById') -or $reviewSource.Contains('lockToolForReview')
        if ($ApplyReviewLockCandidate -and $existingReviewLock) {
            Write-Host 'Candidate skipped: target review implementation already contains a Tool lock. Source preserved.'
        }
        if ($ApplyReviewLockCandidate -and -not $existingReviewLock) {
            $candidate = Join-Path $repo 'test/review-publishing-race/review-lock-candidate.patch'
            # Composition already supplies E's identical repository lock method.
            $repositoryExclude = '--exclude=code/src/main/java/com/example/toolhub/repository/ToolRepository.java'
            & git apply --check $repositoryExclude $candidate
            if ($LASTEXITCODE -ne 0) { throw 'Review lock candidate no longer applies to the D snapshot.' }
            & git apply $repositoryExclude $candidate
            if ($LASTEXITCODE -ne 0) { throw 'Could not apply review lock candidate.' }
        }
    }
    $ePom = [xml][IO.File]::ReadAllText((Join-Path $repo 'code/pom.xml'))
    $profileXPath = "/*[local-name()='project']/*[local-name()='profiles']/*[local-name()='profile'][*[local-name()='id']='postgres-it']"
    $sourceProfile = $ePom.SelectSingleNode($profileXPath)
    if (-not $sourceProfile) { throw 'E PostgreSQL Maven profile is missing.' }
    $pomPath = Join-Path $checkout 'code/pom.xml'
    $dPomText = [IO.File]::ReadAllText($pomPath)
    $dPom = [xml]$dPomText
    $existingProfiles = $dPom.SelectNodes($profileXPath)
    if ($existingProfiles.Count -gt 1) { throw 'Target already has duplicate postgres-it profiles.' }
    $existingProfile = $dPom.SelectSingleNode($profileXPath)
    if ($existingProfile) {
        $failsafe = $existingProfile.SelectSingleNode(".//*[local-name()='plugin'][*[local-name()='artifactId']='maven-failsafe-plugin']")
        if (-not $failsafe) { throw 'Existing postgres-it profile has no Failsafe plugin; manual review required.' }
        $includes = @($failsafe.SelectNodes(".//*[local-name()='includes']/*[local-name()='include']") | ForEach-Object { $_.InnerText })
        $goals = @($failsafe.SelectNodes(".//*[local-name()='goals']/*[local-name()='goal']") | ForEach-Object { $_.InnerText })
        if (-not $failsafe -or $includes -notcontains '**/*PostgresIT.java' -or $goals -notcontains 'integration-test' -or $goals -notcontains 'verify') {
            throw 'Existing postgres-it profile needs manual review: expected Failsafe include and verify goals are missing.'
        }
        Write-Host 'Existing postgres-it profile preserved.'
    } else {
        $profilesNode = $dPom.SelectSingleNode("/*[local-name()='project']/*[local-name()='profiles']")
        if ($profilesNode) {
            # Append to the existing profiles block without replacing unrelated profiles.
            $profilesNode.AppendChild($dPom.ImportNode($sourceProfile, $true)) | Out-Null
        } else {
            $profilesNode = $dPom.CreateElement('profiles', $dPom.DocumentElement.NamespaceURI)
            $profilesNode.AppendChild($dPom.ImportNode($sourceProfile, $true)) | Out-Null
            $dPom.DocumentElement.AppendChild($profilesNode) | Out-Null
        }
        [IO.File]::WriteAllText($pomPath, $dPom.OuterXml, [Text.UTF8Encoding]::new($false))
    }
    & ./scripts/test-postgres.ps1 -PostgresBin $PostgresBin -Port $Port -MavenCommand $MavenCommand *> postgres-verify.log
    $verifyExit = $LASTEXITCODE
    Get-Content postgres-verify.log -Tail 25
    Write-Host "Unmodified baseline: exit $baselineExit; PostgreSQL overlay: exit $verifyExit"
    Write-Host "Reports preserved at: $checkout/code/target"
} finally {
    foreach ($variable in $databaseVariables) {
        [Environment]::SetEnvironmentVariable($variable,$originalDatabaseValues[$variable],'Process')
    }
    Pop-Location
}
exit $verifyExit
