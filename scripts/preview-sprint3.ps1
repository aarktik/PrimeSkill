param(
    [string]$MavenCommand = 'mvn',
    [string]$JavaCommand = 'java',
    [int]$Port = 18080
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repo
try {
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($listener) { throw "Port $Port is already in use. Stop the existing preview or start with -Port <free-port>." }
    $classpathFile = Join-Path $repo 'code/target/preview-classpath.txt'
    $maven = Get-Command $MavenCommand -ErrorAction SilentlyContinue
    if (-not $maven) { throw "Maven command '$MavenCommand' not found. Pass -MavenCommand with its executable path." }
    $java = Get-Command $JavaCommand -ErrorAction SilentlyContinue
    if (-not $java) { throw "Java command '$JavaCommand' not found. Pass -JavaCommand with its executable path." }
    & $maven.Source -B -f code/pom.xml -DskipTests test-compile dependency:build-classpath "-Dmdep.outputFile=target/preview-classpath.txt"
    if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE" }
    $runtime = (Get-Content -Raw $classpathFile).Trim()
    $classpath = @((Join-Path $repo 'code/target/test-classes'), (Join-Path $repo 'code/target/classes'), $runtime) -join [IO.Path]::PathSeparator
    Write-Host 'Temporary local review accounts (H2 is in-memory and disappears when stopped):'
    Write-Host '  Owner: owner@sprint3.test / ReviewOnly123!'
    Write-Host '  Admin: admin@sprint3.test / ReviewOnly123!'
    Write-Host "  URL:   http://127.0.0.1:$Port"
    Write-Host 'Press Ctrl+C to stop. No Docker, external database, or credentials are used.'
    & $java.Source -cp $classpath com.example.toolhub.Sprint3PreviewApplication "--server.port=$Port"
    if ($LASTEXITCODE -ne 0) { throw "Preview exited with code $LASTEXITCODE" }
} finally { Pop-Location }