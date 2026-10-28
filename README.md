# Personal OS — Android foundation

A local-first Kotlin/Compose personal app with persistent Money, hybrid Today, the approved blue/Inter design system, versioned backup/restore and usable subscriptions. Phase 2 adds recurring forecasts and explicit payments linked atomically to Money, including correction, undo and retained history after cancellation. Schedule and Library editors remain deferred.

Current features, policies and actual verification: [Phase 2 handover](docs/phase-2-handover.md). Recovery format and security foundations: [Phase 1.7 handover](docs/phase-1.7-handover.md). Current exports use backup format 2; valid format 1 backups remain readable. Backups are unencrypted and need trusted storage.

Start with [the Phase 1.5 handover](docs/phase-1.5-handover.md), [Phase 1 history](docs/phase-1-handover.md), [product specification](docs/product-specification.md), [architecture](docs/architecture.md), [data model](docs/data-model.md), [UX specification](docs/ux-specification.md), and [widget strategy](docs/widget-strategy.md).

The finished native design tokens and component conventions are in [DESIGN.md](docs/DESIGN.md).

New accounts and budgets default to IDR. IDR uses Indonesian presentation (`Rp25.000`); actual USD records retain dollar formatting. Currency changes never convert financial history. See the handover for the root cause, safe correction policy and executed verification.

## Build

Requires JDK 17 or 21, Android SDK platform 36/build tools 36.0.0, and internet for the first dependency resolution. The wrapper pins Gradle 8.14. Set `JAVA_HOME` to a compatible JDK and `ANDROID_HOME` to the SDK, or create an ignored `local.properties` containing `sdk.dir=C:/Users/your-name/AppData/Local/Android/sdk`.

PowerShell, from this directory:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\sdk"
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleRelease
.\gradlew.bat installDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.jevon.personalos.debug/com.jevon.personalos.MainActivity
```

On this workstation an isolated JDK is available in `.tools/`; the convenience script finds it without changing system Java:

```powershell
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 assembleDebug testDebugUnitTest lintDebug assembleRelease --console plain
```

If a compatible JDK is absent, `tools/bootstrap-jdk.ps1` downloads Microsoft OpenJDK 21 into the ignored workspace `.tools/`. It does not install Java globally. Android Studio can open the project root; select JDK 21 for Gradle. Release APKs are unsigned and not intended for distribution.

## Run and test

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Debug and release start in Personal mode, reading real Room data. The debug-only Developer previews app-bar action offers Populated / Empty / Personal Today previews. Fixtures never seed a database; Money always uses real storage. Default categories are ordinary editable catalog records. Release uses a separate application ID. No notifications are scheduled.

```powershell
# Use an authorized, unlocked physical phone. Phone Link alone does not enable ADB.
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/device.ps1 devices -l
# Select one authorized transport when ADB lists more than one:
$env:ANDROID_SERIAL = 'YOUR_AUTHORIZED_PHONE_SERIAL'
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 assembleDebug assembleDebugAndroidTest --console plain
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-device-tests.ps1 -CapturePrefix physical
```

Instrumented tests cover navigation, empty rendering and transaction integrity, and capture native review images in the debug app's external files `review/` directory. The runner targets the current Android user, preserves app data with install `-r`, and accepts `-DeviceSerial` or `ANDROID_SERIAL`. Keep the phone unlocked during UI tests. Screenshots from recorded validation are under `docs/screenshots/`; `physical-*` captures are distinct from historical emulator images. Pure unit tests cover finance/date/recurrence rules. Report paths and exact results are in the handover.

Do not launch or use an emulator during recovery from the reported Windows graphics crash. The existing `start-emulator.ps1` helper is historical and was not used in recovery. No GPU drivers or Windows/device display settings need to be changed.

The project requests no internet permission and has no cloud service, telemetry, login or API key. All Gradle downloads are build-time only. Settings now supports explicit portable backup and validated full-replacement restore through Android's document picker; files are unencrypted and should be stored in trusted locations. Automatic Android backup remains disabled. See [Phase 1.7 recovery contract and evidence](docs/phase-1.7-handover.md).
