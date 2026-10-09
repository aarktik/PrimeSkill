# Simple script deliberately uses $args so Maven -o/-v/-e are not PowerShell common parameters.
$MavenArguments = $args
$ErrorActionPreference = 'Stop'
$jdkHome = $env:JAVA17_HOME
if ([string]::IsNullOrWhiteSpace($jdkHome)) {
    $jdkHome = [Environment]::GetEnvironmentVariable('JAVA17_HOME','User')
}
if ([string]::IsNullOrWhiteSpace($jdkHome)) { $jdkHome = $env:JAVA_HOME }
if ([string]::IsNullOrWhiteSpace($jdkHome) -or
    -not (Test-Path -LiteralPath (Join-Path $jdkHome 'bin/java.exe')) -or
    -not (Test-Path -LiteralPath (Join-Path $jdkHome 'bin/javac.exe'))) {
    throw 'Install JDK 17 and set JAVA17_HOME to its installation directory.'
}
$releaseFile = Join-Path $jdkHome 'release'
if (-not (Test-Path -LiteralPath $releaseFile) -or
    -not (Select-String -LiteralPath $releaseFile -Pattern '^JAVA_VERSION="17(?:\.|\")' -Quiet)) {
    throw 'JAVA17_HOME must point to JDK 17.'
}
$maven = Get-Command mvn -ErrorAction Stop
$previousHome = $env:JAVA_HOME
$previousPath = $env:PATH
$result = 1
try {
    $env:JAVA_HOME = $jdkHome
    $env:PATH = (Join-Path $jdkHome 'bin') + [IO.Path]::PathSeparator + $env:PATH
    & $maven.Source @MavenArguments
    $result = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousHome
    $env:PATH = $previousPath
}
exit $result
