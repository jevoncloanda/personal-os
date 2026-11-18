param([Parameter(Mandatory=$true)][string]$DeviceSerial, [switch]$DataOnly, [switch]$SkipInstall, [string]$TestClass = '', [string]$CapturePrefix = 'phase-1', [string]$ScreenshotDirectory = 'docs\screenshots\phase-1', [string]$ReportName = '')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:USERPROFILE 'AppData\Local\Android\sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$deviceArgs = @('-s', $DeviceSerial)
if (-not $DataOnly) {
    $lockState = (& $adb @deviceArgs shell dumpsys window policy | Out-String)
    if ($lockState -match '(?m)^\s+showing=true\s*$') { throw 'Phone is locked; UI checks were not attempted. Data-only checks can run independently.' }
}
$androidUser = (& $adb @deviceArgs shell am get-current-user | Out-String).Trim()
if ($androidUser -notmatch '^\d+$') { throw 'Cannot determine current Android user.' }
if (-not $SkipInstall) {
    & $adb @deviceArgs install --user $androidUser -r (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & $adb @deviceArgs install --user $androidUser -r (Join-Path $root 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
$classes = if ($DataOnly) { 'com.jevon.personalos.MoneyDatabaseTest,com.jevon.personalos.MigrationTest' } else { 'com.jevon.personalos.FoundationTest,com.jevon.personalos.VisualReviewTest,com.jevon.personalos.MoneyWorkflowTest' }
if ($TestClass) { $classes = $TestClass }
$result = (& $adb @deviceArgs shell am instrument --user $androidUser -w -e class $classes -e capturePrefix $CapturePrefix -e captureFolder $CapturePrefix 'com.jevon.personalos.dev.test/androidx.test.runner.AndroidJUnitRunner' 2>&1 | Out-String)
Write-Output $result
$report = if ($DataOnly) { 'phase-1-data' } else { 'phase-1-ui' }
if ($ReportName) { $report = $ReportName }
New-Item -ItemType Directory -Force -Path (Join-Path $root 'app\build\reports') | Out-Null
Set-Content -LiteralPath (Join-Path $root "app\build\reports\$report.txt") -Value $result
if (-not $DataOnly) {
    $destination = Join-Path $root $ScreenshotDirectory
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    & $adb @deviceArgs pull '/sdcard/Android/data/com.jevon.personalos.dev/files/review' (Join-Path $root '.tools')
    Get-ChildItem -LiteralPath (Join-Path $root '.tools\review') -Filter "$CapturePrefix-*.png" -ErrorAction SilentlyContinue | Copy-Item -Destination $destination
    $flowCaptures = Join-Path $root ".tools\review\$CapturePrefix"
    if (Test-Path -LiteralPath $flowCaptures) { Get-ChildItem -LiteralPath $flowCaptures -Filter '*.png' | Copy-Item -Destination $destination }
}
if ($result -notmatch 'OK \(\d+ tests?\)') { exit 1 }
