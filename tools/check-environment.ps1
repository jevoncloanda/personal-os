$ErrorActionPreference = 'Continue'
Write-Output 'Network / stable artifact metadata'
foreach ($url in @('https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/2.2.20/kotlin-gradle-plugin-2.2.20.pom', 'https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/8.11.1/gradle-8.11.1.pom', 'https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2025.09.01/compose-bom-2025.09.01.pom', 'https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/2.8.1/room-runtime-2.8.1.pom')) {
  try { $response = Invoke-WebRequest -Uri $url -UseBasicParsing; Write-Output "$($response.StatusCode) $url" } catch { Write-Output $_.Exception.Message }
}
