$ErrorActionPreference = 'Stop'
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\sdk' }
& (Join-Path $sdk 'platform-tools\adb.exe') @args
exit $LASTEXITCODE
