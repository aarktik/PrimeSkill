param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [ValidateRange(1024, 65535)][int]$Port = 15432,
    [string]$MavenCommand = (Join-Path $PSScriptRoot 'mvn-java17.ps1')
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
foreach ($binary in @('initdb.exe', 'pg_ctl.exe', 'createdb.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin $binary))) {
        throw "Missing $binary. Pass -PostgresBin with your local PostgreSQL bin directory."
    }
}
$maven = Get-Command $MavenCommand -ErrorAction Stop
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $Port is busy; choose another -Port. Existing servers are never stopped."
}

$runName = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$runDirectory = Join-Path $repo "code/target/postgres-test/$runName"
$cluster = Join-Path $runDirectory 'data'
$passwordFile = Join-Path $runDirectory 'initdb-password.txt'
$serverLog = Join-Path $runDirectory 'postgres.log'
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$variables = @('PGPASSWORD', 'PRIMESKILL_TEST_DB_URL', 'PRIMESKILL_TEST_DB_USERNAME', 'PRIMESKILL_TEST_DB_PASSWORD')
$previousValues = @{}
foreach ($variable in $variables) { $previousValues[$variable] = [Environment]::GetEnvironmentVariable($variable, 'Process') }
$started = $false
$exitCode = 1
Push-Location $repo
try {
    $env:PGPASSWORD = [guid]::NewGuid().ToString('N')
    [IO.File]::WriteAllText($passwordFile, $env:PGPASSWORD, [Text.UTF8Encoding]::new($false))
    & (Join-Path $PostgresBin 'initdb.exe') -D $cluster -U primeskill_test --encoding=UTF8 --locale=C --auth=scram-sha-256 --pwfile=$passwordFile *> (Join-Path $runDirectory 'initdb.log')
    if ($LASTEXITCODE -ne 0) { throw "initdb failed; see $runDirectory/initdb.log" }
    Remove-Item -LiteralPath $passwordFile
    # Separate native output handles: postgres must not inherit PowerShell's capture pipe.
    $startProcess = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList @('-D', ('"' + $cluster + '"'), '-l', ('"' + $serverLog + '"'), '-o', ('"-h 127.0.0.1 -p ' + $Port + '"'), '-w', 'start') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDirectory 'startup.log') -RedirectStandardError (Join-Path $runDirectory 'startup-error.log')
    # Windows PowerShell 5.1 needs the handle retained before the process exits;
    # otherwise ExitCode can be null even when pg_ctl starts the server successfully.
    $null = $startProcess.Handle
    # Start-Process -Wait waits for the server's descendants on Windows as well.
    $startProcess.WaitForExit()
    $startProcess.Refresh()
    if ($startProcess.ExitCode -ne 0) { throw "PostgreSQL startup failed; see $serverLog" }
    $started = $true
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $Port -U primeskill_test primeskill_test_role_e
    if ($LASTEXITCODE -ne 0) { throw 'Creating the disposable test database failed.' }
    $env:PRIMESKILL_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$Port/primeskill_test_role_e"
    $env:PRIMESKILL_TEST_DB_USERNAME = 'primeskill_test'
    $env:PRIMESKILL_TEST_DB_PASSWORD = $env:PGPASSWORD
    Write-Host "Disposable PostgreSQL test cluster: $runDirectory"
    & $maven.Source -B -f code/pom.xml -Ppostgres-it verify
    $exitCode = $LASTEXITCODE
} finally {
    if ($started) {
        & (Join-Path $PostgresBin 'pg_ctl.exe') -D $cluster -m fast -w stop
        if ($LASTEXITCODE -ne 0) { Write-Warning "Could not stop the test cluster at $cluster"; $exitCode = 1 }
    }
    if (Test-Path -LiteralPath $passwordFile) { Remove-Item -LiteralPath $passwordFile }
    foreach ($variable in $variables) { [Environment]::SetEnvironmentVariable($variable, $previousValues[$variable], 'Process') }
    Pop-Location
}
exit $exitCode
