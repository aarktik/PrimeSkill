param(
    [string]$MavenCommand = 'mvn',
    [string]$JavaCommand = 'java',
    [int]$Port = 18088
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repo
try {
    if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Choose a local port between 1024 and 65535.' }
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
        throw "Port $Port is already in use. Choose a free port; this script does not stop existing servers."
    }
    $maven = Get-Command $MavenCommand -ErrorAction SilentlyContinue
    $java = Get-Command $JavaCommand -ErrorAction SilentlyContinue
    if (-not $maven -or -not $java) { throw 'Java 17 and Maven are required. Pass their executable paths if needed.' }
    & $maven.Source -B -f code/pom.xml -DskipTests test-compile dependency:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/ui-preview-dependencies.txt'
    if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE" }
    $runtime = (Get-Content -Raw 'code/target/ui-preview-dependencies.txt').Trim()
    $classpath = @((Join-Path $repo 'code/target/test-classes'), (Join-Path $repo 'code/target/classes'), $runtime) -join [IO.Path]::PathSeparator
    Write-Host "Disposable UI test server: http://127.0.0.1:$Port"
    Write-Host 'owner@sprint3.test and admin@sprint3.test / ReviewOnly123!'
    Write-Host 'Uses only in-memory H2. Data disappears on exit. Press Ctrl+C to stop.'
    & $java.Source -cp $classpath com.example.toolhub.UiAcceptancePreviewApplication $Port
    if ($LASTEXITCODE -ne 0) { throw "Preview exited with code $LASTEXITCODE" }
} finally { Pop-Location }
