param([string]$TestClass = '', [string]$CapturePrefix = 'phone', [string]$DeviceSerial = $env:ANDROID_SERIAL)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if ($DeviceSerial) { $env:ANDROID_SERIAL = $DeviceSerial }
$lockState = (& $adb shell dumpsys window policy | Out-String)
if ($lockState -match '(?m)^\s+showing=true\s*$') { throw 'Unlock the selected phone before UI tests; no installation or test retry was attempted.' }
$androidUser = (& $adb shell am get-current-user | Out-String).Trim()
if ($androidUser -notmatch '^\d+$') { throw 'Could not determine current Android user; no installation attempted.' }
& $adb install --user $androidUser -r (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $adb install --user $androidUser -r (Join-Path $root 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$testArguments = @('shell', 'am', 'instrument', '--user', $androidUser, '-w', '-e', 'capturePrefix', $CapturePrefix)
if ($TestClass) { $testArguments += @('-e', 'class', $TestClass) }
$testArguments += 'com.jevon.personalos.dev.test/androidx.test.runner.AndroidJUnitRunner'
$result = (& $adb @testArguments 2>&1 | Out-String)
Write-Output $result
$reportDirectory = Join-Path $root 'app\build\reports'
New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
Set-Content -LiteralPath (Join-Path $reportDirectory "device-test-$CapturePrefix.txt") -Value $result
$passed = $result -match 'OK \(\d+ tests?\)'
New-Item -ItemType Directory -Force -Path (Join-Path $root 'docs\screenshots') | Out-Null
& $adb pull '/sdcard/Android/data/com.jevon.personalos.dev/files/review' (Join-Path $root '.tools')
if (Test-Path -LiteralPath (Join-Path $root '.tools\review')) {
    Get-ChildItem -LiteralPath (Join-Path $root '.tools\review') -Filter '*.png' | Copy-Item -Destination (Join-Path $root 'docs\screenshots')
}
if (-not $passed) { exit 1 }
