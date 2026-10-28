$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$toolsDir = Join-Path $root '.tools'
New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
$archive = Join-Path $toolsDir 'jdk21.zip'
Invoke-WebRequest -Uri 'https://aka.ms/download-jdk/microsoft-jdk-21.0.8-windows-x64.zip' -OutFile $archive -UseBasicParsing
Expand-Archive -LiteralPath $archive -DestinationPath $toolsDir -Force
Get-ChildItem -LiteralPath $toolsDir -Directory | Select-Object FullName
