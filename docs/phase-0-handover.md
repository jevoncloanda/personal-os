# Phase 0 handover

Completed overnight, 22–23 October 2026 (Asia/Jakarta). Product name and branding remain provisional. No commit, push or publication was performed.

## Recovery update — 23 October 2026

The supplied resume request reports a Windows `VIDEO_TDR_FAILURE` interruption. Recovery preserved the existing project rather than recreating it. Source, Gradle files, all specifications, the finished design record, exported Room schema and prior APK/test evidence were intact. Design JSON and Room schema parsed successfully; recompilation also verified source/resource integrity. The directory is still not a Git repository. See [the recovery checklist](recovery-checklist.md).

Work already completed before recovery: all Phase 0 product/architecture/UX/design documentation, Kotlin/Compose navigation and Today foundation, Room/DataStore setup, isolated debug data, prior unit/lint/build checks, and historical emulator captures/review. **No emulator was launched, created or used during recovery.** No GPU driver, Windows, BIOS, phone display or other unrelated system settings were changed.

New recovery work: select a physical ADB transport, scope installs/tests to the current Android user, rerun local checks, deploy the debug app to the phone, perform native tests/screenshots and inspect app-scoped runtime logs. Physical screenshot review also exposed an appearance preference race: rapid controls saved stale whole-setting copies, allowing one preference to overwrite the other. The fix uses independent atomic DataStore key edits, with a concurrent-edit regression test and stronger screenshot prerequisites. No business workflow or product scope was added.

Changed production files: `data/SettingsStore.kt`, `today/TodayViewModel.kt`, `ui/PersonalOsApp.kt`. Changed tests: `FoundationTest.kt`, `VisualReviewTest.kt`. Changed support/report files: `tools/run-device-tests.ps1`, `README.md`, `docs/architecture.md`, this handover, and new `docs/recovery-checklist.md`. New physical screenshots use `docs/screenshots/physical-sm-s942b-*`; old emulator evidence is preserved separately. Temporary audit/launch scripts live in ignored `.tools/`.

### Physical phone and recovery checks

ADB reports Samsung **SM-S942B**, Android **17**, API **37**. The app's minimum API 26 supports it. Two authorized wireless transports were listed; commands selected one explicitly. Installations targeted current Android user 0, preserved app data with `-r`, and did not clear storage. An initial unscoped package-list query hit Samsung's profile restrictions; no restricted profile was accessed or modified.

The first physical attempt passed the Room test but could not see Compose UI because the phone was locked. Window policy confirmed keyguard showing and not occluded. After the user unlocked the phone, all three existing native tests passed. Screenshot inspection then found the theme race described above; tests were strengthened rather than treating those initial theme labels as proof.

The phone subsequently re-locked during a rebuild. Tests now keep only their own Activity awake, and the runner refuses to start when keyguard is showing. A blocking screenshot synchronization check was replaced with Compose-aware frame advancement while awaiting saved preferences. These are test-harness changes; phone timeout and security settings were preserved.

### Recovery validation

| Check executed after recovery | Actual result |
|---|---|
| Recovered-file debug/release builds, Kotlin/Room compilation | Passed; the initial `--rerun-tasks` run executed all 136 tasks |
| Post-preference-fix debug/release builds and test APK | Passed |
| JVM unit tests | 10 passed, 0 failures/errors |
| Android lint | 0 errors, 14 advisory dependency/Kapt warnings |
| Physical installation, current user 0 | Debug app and test APK installed successfully; app data preserved |
| Original physical native suite after first unlock | 3 tests passed; later screenshot review exposed a theme-label mismatch, so that pass did not certify light mode |
| Post-fix physical Room, navigation and concurrent-preference checks | All 3 behavior tests passed on the updated app |
| Final physical suite including confirmed-theme screenshots | Passed: **4 tests**, 0 failures (`OK (4 tests)`). Saved theme and wallpaper-color combination is checked before each capture |
| Final independent cold launch and app-scoped logcat | Final app cold launch succeeded; app process alive; no `AndroidRuntime` fatal exception in inspected output. Vendor `perf_hint` and vibrator HAL errors were nonfatal |

Logs: `app/build/reports/device-test-physical-sm-s942b.txt` and `app/build/reports/physical-startup.txt`. Each runner invocation replaces its report with the actual latest result. Earlier emulator results below remain historical evidence, not a claim of emulator use in recovery. ADB logcat was restricted to this app's PID; no unrelated app logs or phone content were inspected. Screenshots were not taken while the lock screen was showing.

Final visual inspection confirms distinct static light/dark rendering, correctly scaled IDR budget and payment amounts, overdue reminder text, populated budget/library content, and preserved native navigation. Empty/personal modes, dynamic colors and Quick Add are captured by the same passing suite. This verifies the implemented prototype on this phone; it does not certify hardware gestures, all accessibility services or release performance. The application remains installed for review. No outstanding ADB/device blocker remains.

## Completed work

- Product requirements, architecture, data model, native UX and widget strategy are documented in this directory.
- A buildable single-module Kotlin Android app: Compose/Material 3, Navigation Compose, ViewModel/Flow, Room version 1 with exported schema, and DataStore appearance settings.
- Today displays local date/greeting, ongoing/upcoming events, pending reminders, monthly budget, upcoming subscription renewals and active leisure items. It supports populated demo, empty demo, loading and recoverable storage error states.
- Money, Schedule, Library and Money/subscriptions destinations explicitly identify themselves as foundation screens. Quick Add opens a native sheet and navigates to the appropriate foundation; it does not claim to save records.
- Static light/dark palettes and Android 12+ wallpaper colors; bottom navigation switches to a rail on expanded widths. Expanded Today uses two columns.
- Debug fixtures are isolated in the debug source set and have no write access to Room. Personal mode observes actual local storage. The release catalog is empty and disabled; debug and release application IDs differ.
- Financial invariants and date/recurrence behavior have focused tests. Native screenshots are real emulator captures, not generated comps.

## Repository and environment findings

The working directory was empty and was **not a Git repository**. There were no existing application files, project-local instructions or user changes to overwrite. The supplied AGENTS instruction imported `C:\Users\jevon\.codex\RTK.md`; subsequent shell commands used RTK.

Installed environment: Android Studio, SDK platforms 28/31/33/34/36 and preview 37.0, build tools 35.0.0/36.0.0, platform tools, Gradle 8.14 cache, and one `Medium_Phone_API_37.0` AVD with usable WHPX acceleration. Global Java and Android Studio's JBR were too new for this Gradle version. An isolated Microsoft OpenJDK **21.0.8** was downloaded into ignored `.tools/`; system Java was not changed. Maven metadata requests confirmed the pinned AGP, Kotlin, Compose BOM and Room versions exist.

The initial sandbox could not resolve Maven hosts or start ADB; approved commands enabled dependency resolution and emulator access. No Android-tooling blocker remained for the executed checks.

## Project structure

```text
app/
  src/main/java/com/jevon/personalos/
    MainActivity.kt, PersonalOsApplication.kt
    data/       Room entities, DAO, database, settings
    today/      pure overview rules and TodayViewModel
    ui/         theme, navigation shell, Today, reusable rows/states
  src/debug/    realistic in-memory DemoCatalog
  src/release/  disabled empty catalog
  src/test/     finance/date/recurrence tests
  src/androidTest/  Room/UI tests and screenshot capture
  schemas/      Room version 1 JSON
docs/           specifications, design system, handover, screenshots
gradle/wrapper/ reproducible Gradle launcher
tools/          isolated JDK bootstrap, build/emulator/test helpers
```

## Technical decisions

One app module and a small application-owned dependency graph are sufficient. Hilt, a generic repository interface and unused services would add overhead without owning any behavior yet. Room/Kapt generates persistence code; KSP migration is a future build-tool improvement. WorkManager and Glance are deliberately deferred to the scheduling/widget phase rather than added as idle dependencies.

Pinned toolchain: Gradle 8.14, AGP 8.11.1, Kotlin/Compose compiler 2.2.20, Compose BOM 2025.09.01, Room 2.8.1, DataStore 1.1.7, Navigation/Lifecycle 2.9.4, Coroutines 1.10.2. Compile/target SDK 36; min SDK 26; Java bytecode 17, Gradle runtime JDK 21. Versions are stable and compiled together; they are not claimed to be the newest available.

Money uses integer minor units. A single validated transfer row prevents half-transfers and is excluded from spending. Budget periods have inclusive start/exclusive end. Subscription forecasts never create transactions. Android ICU's zero-decimal IDR display behavior is overridden to retain ISO two-digit storage precision; both JVM and emulator assertions cover it. No destructive database fallback, analytics, runtime network permission, cloud service or backend exists. Backup and device-transfer exclusions are explicit.

## Assumptions needing product approval later

Single person/device; Android 8+; manual account/entry creation; English-first; no FX transfers; bounded recurrence rather than arbitrary RRULEs; first active overall budget on Today; whole-hour game progress; provisional name/mark/static teal palette. IDR is a debug example, not an enforced user currency. Backup/recovery and optional encryption/synchronization need a separate product decision before storing important real data.

## Original overnight validation results (historical)

| Executed check | Actual result |
|---|---|
| Gradle wrapper generation | Passed with Gradle 8.14/JDK 21 |
| Debug APK and Kotlin/Room compilation | Passed |
| Unsigned release APK and Kotlin/Room compilation | Passed |
| `testDebugUnitTest` | 10 tests, 0 failures/errors |
| `lintDebug` | 0 errors, 14 warnings (dependency-update and Kapt guidance) |
| Room emulator test | Fresh database empty; mixed-currency transfer rejected without a write; valid same-currency transfer recorded atomically; IDR formatting correct |
| Native UI test | Empty state, Quick Add routing, Money/subscriptions back, Schedule, Library, return to Today passed |
| Native screenshot test | Light/dark, empty/personal, dynamic colors and Quick Add passed |
| Final standard `connectedDebugAndroidTest` | Passed: 3 tests, 0 failures; Gradle BUILD SUCCESSFUL |
| 1.3× font capture test | Passed on phone emulator |
| Expanded-width capture test | Passed with rail and two columns on resized phone emulator |

The first instrumented run failed because older Espresso reflected a removed input method on Android 17. Test dependencies were upgraded to AndroidX Test runner 1.7.0, extension 1.3.0 and Espresso 3.7.0. Later checks exposed a missing accessible FAB label, Android/JVM IDR formatting disagreement, a test asserting selection on a child label, and screenshots captured before native dialogs had disappeared. These were corrected; the successful results above refer to later executions, not the failed attempts.

Native tests were also invoked directly with `am instrument` so screenshot files survived Gradle test cleanup. Their output is saved by `tools/run-device-tests.ps1` under `app/build/reports/device-test-*.txt`. The final standard connected Gradle task then passed on the final app build. Gradle reports live under `app/build/reports/`; JVM XML results under `app/build/test-results/testDebugUnitTest/`. Emulator font scale, display size and density were restored after validation.

## Independent review and design record

The Impeccable native finish reviewer checked the required phone, dark, empty, dynamic, 1.3× and expanded captures against the UX direction. The initial review requested one material fix: paint the full-window background above the rail so white status icons remain legible in dark mode. That fix was applied and the same expanded dark/empty/personal captures were replaced. The reviewer’s verdict pass scored that fix **resolved**, with disposition **ship for the scored fix**; this is not a whole-app or release certification.

The visual system is recorded from finished source in [DESIGN.md](DESIGN.md), with machine-readable details in `.impeccable/design.json`. User-specified native Material controls, system type roles and Android conventions governed the build. The user's autonomous brief superseded optional design interviews/comp approvals, and all Markdown records remain under `docs/`. No web detector was run on native Kotlin. No generated raster asset ships in the app.

## Screenshot evidence

Recovery physical captures use `physical-sm-s942b-*` and come from the Samsung phone at 1080×2340. The separate `physical-sm-s942b-adb-launch.png` is captured with ADB screencap and pulled from this app's external files, rather than from the instrumentation screenshot helper. Screen content follows the phone's clock: after the demo events have ended, populated mode correctly shows a clear calendar and overdue sample reminders. It does not invent upcoming events merely to fill the screen.

Final physical evidence:

- [Light Today](screenshots/physical-sm-s942b-light.png), [budget/payments/library](screenshots/physical-sm-s942b-light-lower.png), [dark Today](screenshots/physical-sm-s942b-dark.png).
- [Empty](screenshots/physical-sm-s942b-empty.png), [personal data](screenshots/physical-sm-s942b-personal.png), [dynamic colors](screenshots/physical-sm-s942b-dynamic.png), [Quick Add](screenshots/physical-sm-s942b-quick-add.png).
- [Direct ADB launch capture](screenshots/physical-sm-s942b-adb-launch.png).

These final physical captures replace the earlier pre-fix candidates. Historical emulator links below retain their original provenance.

The following historical captures come from the installed native app on the Android 17/API 37 preview emulator. Baseline phone is 1080×2400. Expanded width is the same AVD temporarily resized to 1600×2400 at 240 dpi; **this is not independent tablet-device testing**. `phone-font130-*` captures use 1.3× system text.

- [Phone light](screenshots/phone-light.png), [lower Today content](screenshots/phone-light-lower.png), [phone dark](screenshots/phone-dark.png).
- [Empty](screenshots/phone-empty.png), [personal data](screenshots/phone-personal.png), [dynamic colors](screenshots/phone-dynamic.png), [Quick Add](screenshots/phone-quick-add.png).
- [Larger text](screenshots/phone-font130-light.png), [expanded light](screenshots/expanded-light.png), [expanded dark](screenshots/expanded-dark.png).

## Known limits and next phase

This is a reviewable foundation, not a finished personal-finance/calendar app. There are no business editors, reminder completion, recurrence expansion, notifications, actual subscription settlement operation, widgets, import/export or backups. Room schema represents those domains, but schema shape does not replace their future validated transactional write operations.

Today observes small full-table flows and can briefly combine independently updated snapshots. Before larger datasets or widgets, add bounded transactional projections and a deliberate overall-budget selection rule. Recurring templates need occurrence history and DST policy before completion/notification workflows. Schema version 1 has no predecessor, so there is no migration execution to test yet; every later change requires preservation tests.

English UI copy is not fully externalized. Accessibility semantics, scrolling and historical 1.3× text were exercised, but TalkBack, 2× text, RTL/localization, hardware gesture/performance profiling, API 26–36 device coverage, forced storage failure rendering, and arbitrary wallpaper contrast are not certified. Recovery adds physical API 37 app verification as detailed above; this does not certify every Android version. Release is unsigned and unoptimized; signing/minification/release hardening belong before distribution. DataStore settings persist across launches and capture runs, but deliberate process-death/restore testing remains future work.

Recommended Phase 1: one complete Money workflow—create an account, record expense/income, same-currency transfer, configure a monthly budget, and see real totals in Today. Test edits/corrections and migrations alongside it. Add subscription settlement next, then schedule occurrences/notifications and finally widgets once their read/write invariants exist.

## Exact build and run commands

From `C:\Jevon\Code\personal-os` in PowerShell, using the isolated workstation JDK:

```powershell
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 assembleDebug testDebugUnitTest lintDebug assembleRelease --console plain
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/device.ps1 devices -l
# Select one authorized physical transport. Wireless addresses may change:
$env:ANDROID_SERIAL = '<authorized-device-serial>'
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/device.ps1 install --user 0 -r app/build/outputs/apk/debug/app-debug.apk
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/device.ps1 shell am start -W --user 0 -n com.jevon.personalos.debug/com.jevon.personalos.MainActivity
```

For native tests and preserved captures:

```powershell
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 assembleDebug assembleDebugAndroidTest --console plain
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-device-tests.ps1 -DeviceSerial $env:ANDROID_SERIAL -CapturePrefix physical-sm-s942b
```

Portable build: set `JAVA_HOME` to JDK 17/21 and `ANDROID_HOME` to an SDK with platform 36, then run `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleRelease`. Open the root directory in Android Studio and run the `app` debug configuration. APKs: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release-unsigned.apk`.

Use the physical phone for the recovery workflow and keep it unlocked. The runner refuses to begin while keyguard is showing; tests keep only their own Activity window awake and release that flag with the test Activity. It does not change screen-timeout settings. Do not invoke the historical emulator helper. If ADB is unavailable later, enable developer options/USB or wireless debugging, pair/authorize ADB, then unlock the phone; Phone Link alone is insufficient. An `unauthorized` device needs its debugging authorization accepted, and an `offline` device needs its cable/wireless debugging connection restored. Do not retry an unavailable device in a loop.
