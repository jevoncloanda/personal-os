$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = Join-Path $env:LOCALAPPDATA 'Android\sdk'
& (Join-Path $sdk 'platform-tools\adb.exe') devices -l
$emulator = Join-Path $sdk 'emulator\emulator.exe'
Start-Process -FilePath $emulator -ArgumentList @('-avd', 'Medium_Phone_API_37.0', '-no-snapshot-save', '-no-boot-anim', '-no-window', '-gpu', 'swiftshader', '-no-audio') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $root '.tools\emulator-out.log') -RedirectStandardError (Join-Path $root '.tools\emulator-error.log')
Write-Output 'Started the existing phone AVD headlessly; no snapshots will be saved.'
