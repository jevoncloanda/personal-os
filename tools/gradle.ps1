param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArguments)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$isolatedJdk = Get-ChildItem -LiteralPath (Join-Path $root '.tools') -Directory -ErrorAction SilentlyContinue | Where-Object Name -Like 'jdk-21*' | Select-Object -First 1
if ($isolatedJdk) { $env:JAVA_HOME = $isolatedJdk.FullName }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17 or 21; see README.md.' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\sdk' }
Set-Location -LiteralPath $root
if (Test-Path -LiteralPath (Join-Path $root 'gradlew.bat')) {
    & (Join-Path $root 'gradlew.bat') @GradleArguments
} else {
    $cachedGradle = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.14-all') -Recurse -Filter gradle.bat | Select-Object -First 1
    if (-not $cachedGradle) { throw 'Gradle 8.14 is needed to generate the wrapper.' }
    & $cachedGradle.FullName @GradleArguments
}
exit $LASTEXITCODE
