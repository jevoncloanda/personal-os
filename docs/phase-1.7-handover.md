# Phase 1.7 — backup, restore and data protection

Completed 27 October 2026. The final debug APK is installed on the Samsung SM-S942B. Backup and recovery gates passed without replacing personal data; backups remain unencrypted and require trusted storage.

## Implementation plan (26 October 2026)

1. Keep Room schema v2 and existing financial writers unchanged. Add a backup-specific DAO for bounded, transaction-consistent reads and ordered bulk replacement with foreign keys enabled.
2. Define JSON format `com.jevon.personalos.backup`, format version 1, independently versioned from Room. Explicitly map all nine persisted tables and every current field, including historical/archive/completion state and recurrence. Exclude DataStore appearance preferences, debug fixtures, screenshots, cache and internal paths.
3. Bound input/output to 8 MiB, 20,000 total records, depth 12 and bounded strings/JSON nodes. Use strict UTF-8, reject duplicate keys, non-integer numbers, unknown/missing fields and unsupported versions. SHA-256 covers the canonical envelope excluding its integrity field; this detects damage but provides neither authentication nor encryption.
4. Validate identities, references, currencies, dates, financial aggregates and stored domain semantics before preview, and again before the replacement transaction. Preserve valid legacy irregular/overlapping budget intervals rather than applying current editor-only restrictions to historical data.
5. Add Backup & Restore to existing settings with native SAF create/open contracts, background work, preview counts, strong replacement confirmation and clear cancellation/error/success states. Preferences remain unchanged, avoiding a cross-store atomicity claim.
6. Test codec limits/corruption and complete semantic database round trips, rollback and reactive projections in isolated databases. Install without clearing data. Exercise document-picker export/restore only in a debug-only isolated review environment; never restore over the live personal database during tests.
7. Run debug/release/JVM/lint, physical regressions where available, independent code/visual review and finalize this handover with actual evidence.

## Inspected ownership and boundaries

`PersonalDatabase` v2 contains accounts, categories, entries, budgets, events, reminders, subscriptions, subscription_payments and library_items. Money editors own the first four. Other domain tables have persisted models and Today queries but no completed production editors; backups must preserve any rows that exist, not assume they are empty. Debug `DemoCatalog` is in memory and never a backup source. `SettingsStore` has only theme and wallpaper-color preferences; both are device preferences and excluded. Existing Android `allowBackup=false` and data-extraction exclusions remain authoritative.

## Implemented features and ownership

Implementation continued on 27 October after a tool-service usage interruption. Settings → Backup & restore provides native SAF Export backup and Restore backup. Import first reads and validates, then shows all nine counts and creation metadata. Review replacement opens a second Material dialog; its destructive button remains disabled until the acknowledgement is checked. Export-current-data advice is visible in both preview and confirmation. Reading/export have cancelable progress; the confirmed replacement has a short noncancelable commit boundary. Success follows output flush/close or database commit, never just picker selection.

The Settings launcher and dialog were renamed from Appearance to Settings after independent visual review, because recovery now shares that entry point. Theme and wallpaper controls remain intact. The existing Inter, blue atmosphere, outline icons and native panels/buttons were reused without changing design tokens. The independent documenter compared source and physical captures and preserved DESIGN.md and its sidecar as an ordinary extension.

Production ownership:

- `backup/BackupJson.kt`: strict parsing, resource limits, canonical writer and digest.
- `backup/BackupCodec.kt`: explicit versioned envelope and field mappings.
- `backup/BackupData.kt`: complete historical-graph and financial-range validation.
- `backup/BackupStore.kt`, `data/BackupDao.kt`: consistent snapshots and atomic replacement.
- `backup/BackupViewModel.kt`: background work, cancellation, document cleanup and outcome state.
- `ui/BackupScreen.kt`, `ui/PersonalOsApp.kt`: SAF, Settings route, disclosure, preview and confirmation.
- `data/PersonalDatabase.kt`: additive DAO accessor only; schema stays version 2.

## Format version 1

The root JSON object requires exactly `format`, `version`, `schemaVersion`, `createdAt`, `appVersion`, `data`, `integrity`.

| Field | Contract |
| --- | --- |
| format | `com.jevon.personalos.backup` |
| version | integer `1`, independent of Room's schema version |
| schemaVersion | integer `2`, the explicitly supported data mapping |
| createdAt | ISO-8601 UTC Instant ending in `Z` |
| appVersion | source application version string |
| data | all nine named arrays below, including empty arrays |
| integrity | `{ "algorithm": "SHA-256", "digest": "64 lowercase hexadecimal characters" }` |

Every v1 row field is required, including explicitly nullable fields. Unknown fields, missing sections, wrong types and unsupported format/schema versions are rejected rather than guessed. Later Room migrations do not automatically change this format: future readers must deliberately retain/translate older backup mappings or introduce a new format version. This release reads format 1/schema mapping 2 only. Existing Room v1 data first migrates nondestructively to v2, then exports through the same mapping; that path was tested.

| Section | Explicit fields |
| --- | --- |
| accounts | id, name, currencyCode, openingBalanceMinor, archived, type, notes |
| categories | id, name, nullable kind, archived |
| entries | id, kind, amountMinor, currencyCode, occurredOnEpochDay, nullable fromAccountId/toAccountId/categoryId, note, minuteOfDay |
| budgets | id, name, currencyCode, limitMinor, startEpochDay, endExclusiveEpochDay, nullable categoryId |
| events | id, title, startsAtMillis, endsAtMillis, zoneId, allDay, location, recurrence |
| reminders | id, title, nullable dueAtMillis/completedAtMillis, recurrence |
| subscriptions | id, name, amountMinor, currencyCode, nextDueEpochDay, active, recurrence |
| subscriptionPayments | id, subscriptionId, dueEpochDay, entryId |
| libraryItems | id, title, kind, status, progress, nullable total, updatedAtMillis |

Each recurrence object requires nullable `frequency`, integer `interval`, `anchorEpochDay`, nullable `untilEpochDay`. No Room identity table, SQLite file/WAL, row index, private absolute path or derived balance is serialized. Amounts remain exact integer minor units: signed Long opening balances and positive Long entry/forecast/limit values. Local epoch days and minute-of-day remain authoritative for financial history; event zones and epoch-millisecond instants retain their original semantics.

## Completeness, exclusions and preferences

All stored records are included, not just active Today projections: archived accounts/categories, all ledger dates, all budget intervals, completed/undated reminders, inactive subscriptions, payment links and every library status. Future-domain tables are not fabricated or filled with placeholders.

The non-destructive personal-database audit on the final device run verified preservation of the existing personal records. Detailed counts remain in local, untracked test evidence. The complete semantic snapshot compared equal before/after the personal Settings/navigation test. No personal restore was performed, and no new financial test records were inserted into that database in this phase.

DataStore theme/wallpaper preferences are excluded and remain unchanged by restore. There is no cross-storage atomicity promise. Debug DemoCatalog fixtures are in-memory and excluded; the isolated review host's deliberately labeled disposable records exist only in its own database. Screenshots, caches, private test files, build outputs, navigation state and unrelated phone data are not backup sources. Automatic Android backup and data-transfer exclusions remain unchanged.

## Integrity, validation and resource limits

SHA-256 covers UTF-8 bytes of the canonical complete root **excluding only `integrity`**. Canonical objects have keys sorted by Kotlin/Java string order, no whitespace, JSON double-quoted strings, escaped quotes/backslashes, and lowercase four-digit Unicode escapes for control/surrogate code units. Numbers are base-10 integers with no float/exponent conversion; booleans/null use JSON literals. Arrays preserve their order for checksum verification; export sorts each record array by stable ID. JSON input formatting/key order may differ without breaking the checksum if the same canonical values are retained.

The checksum detects accidental damage and modification without a recomputed digest. It authenticates no author: someone who changes a file can recompute it. **Files are unencrypted and readable by anyone with access.** Store them only in trusted locations. The app has no analytics, new network permission, cloud login or synchronization. SAF may offer remote providers chosen by the user; the app does not implement or guarantee those providers' privacy/durability.

Limits: 8 MiB actual document/output bytes; 20,000 total rows across the nine sections; at most 20,000 items in an individual parsed array; depth 12; 400,000 parsed value nodes; 8,192 UTF-16 code units per text field; 200 per record ID. Input is strict UTF-8 and JSON; duplicate keys, malformed Unicode, floats/exponents, numeric overflow, duplicate IDs, broken references and unsupported fields are rejected. Snapshot count/text-size preflight runs inside the read transaction before materializing rows. Parsing/encoding use bounded in-memory representations within these caps, not a claim of constant-memory streaming for arbitrary ledgers.

Validation checks enums, positive amounts/limits, ISO currency support, legal financial dates/minute-of-day, event order/zones, recurrence dates/intervals, library progress and payment uniqueness/expense compatibility. Archived historical references remain valid. Stored legacy irregular/overlapping budget intervals are preserved instead of normalized to current editor rules. BigInteger checks lifetime currency/kind aggregates, per-account inflows/outflows and final signed-64-bit balances before any replacement. No currency conversion, rounding or historical rewrite occurs.

## Atomicity, cancellation and failure recovery

Snapshot reads all nine tables inside one Room transaction. Full replacement validates before clearing anything, then clears payment links, entries and budgets before referenced parents; inserts accounts/categories/subscriptions before entries/budgets and payment links last. Foreign keys stay enabled, inserts use ABORT, and all operations share one Room transaction. An injected mid-insert failure and cancellation inside an isolated transaction both proved complete rollback. Room invalidation updates subscribed Money/Today queries only after commit. No database reopen or application restart is needed.

The preview holds validated records in memory, not a mutable file reference. Activity recreation retains it; process death discards it and requires reselecting/revalidating/confirming. No preview or URI is persisted into device preferences. A confirmed UI restore runs its transaction to a definite outcome; the UI does not offer Cancel during commit. System Back can leave the screen while the operation completes. SQLite provides old-or-new transaction recovery on process interruption; process-kill/power-loss testing itself was not executed.

Export does not change Room data. Completion follows successful write, flush and close. Failure/cancellation attempts deletion of only the newly chosen SAF output. If the provider refuses cleanup, the UI warns that an incomplete file may remain and should be deleted. Process termination before cleanup and storage-provider stalls/durability are outside the app's complete control; a truncated backup will not pass validation. A checksum cannot guarantee a provider's later remote persistence.

Independent code review identified one cancellation-before-IO ownership race. The final fix enters cleanup ownership with an undispatched coroutine before IO can queue; noncancelable cleanup includes both deletion and terminal feedback. A working-state guard prevents overlapping operations during cleanup. The exact queued-dispatch race, provider refusal, partial pipe write, import cancellation and corrupt input are now exercised on the physical device. Cancellation is ordinary feedback, not a successful restore or a technical exception dump.

## Tests and actual results

| Gate | Executed result | Local report |
| --- | --- | --- |
| Debug/test APK and unsigned release | Passed after final production changes | Gradle outputs under `app/build/outputs/apk/` |
| JVM regression | 34 passed: 23 existing + 11 backup codec/validation tests | `app/build/test-results/testDebugUnitTest/` |
| Android lint | Zero errors; 19 advisory warnings | `app/build/reports/lint-results-debug.html` / `.xml` |
| Isolated database/I/O/financial/migration | 20 passed on final reviewed build | `app/build/reports/phase-1.7-release-data.txt` |
| Full SAF + personal Settings + foundation UI | 5 passed before Settings naming correction; full replacement only in isolated host | `app/build/reports/phase-1.7-ui-verified.txt` |
| Settings naming correction recapture | 4 passed after the naming correction | `app/build/reports/phase-1.7-settings-confirmed.txt` |

These runs cover 25 distinct passing physical-device tests; the final four-test recapture repeats existing coverage and is not counted again. The final startup smoke reported Status `ok`, LaunchState `UNKNOWN (0)`, WaitTime 678 ms and a live app PID. The inspected PID-scoped log contained vendor perf_hint/VibratorInfo messages and no observed application fatal. This is startup smoke evidence, not a cold-start benchmark. Report: `app/build/reports/phase-1.7-startup.txt`.

The 20 data tests are BackupDatabaseTest 6, BackupIoTest 3, MoneyDatabaseTest 10 and MigrationTest 1. They cover empty/fresh/replacement round trips; every stored field/table; multiple currencies; signed opening balances; income/expenses/transfers; archived references; legacy/monthly/category budgets; recurrence/timestamps/completion; payment links/library statuses; all calculated balances and monthly spending; active reactive Money/Today collectors; FK enforcement; rollback and preservation on validation/failure/cancellation. MigrationTest also backs up migrated v1 records and restores them into a fresh v2 database.

The 11 JVM tests cover canonical determinism, semantic full/empty round trips, extreme Long opening balances, metadata/data checksum damage, missing/unknown/wrong fields, unsupported versions, duplicates/references/currencies/zones/payment conflicts, aggregate/balance overflow, malformed/truncated files, invalid UTF-8, excessive size/depth/array count/text and malformed surrogate strings. The existing currency/Today/financial presentation suites remain unchanged in behavior.

The UI run exercised actual CreateDocument cancellation and successful save, OpenDocument selection of that file, preview counts, disabled confirmation before acknowledgement, cancellation back to the preview, light/dark presentation, replacement of empty disposable data and exact post-restore records/balance. The personal smoke only opened Settings/recovery and navigated Money/Today, including Activity recreation, while comparing all rows unchanged and restoring its original appearance preferences.

Earlier runs exposed a fixed debug-control test-helper scroll error and the cancellation terminal-feedback race, both resolved before the final passing gates. One later label-capture attempt lost the Compose window when keyguard reactivated; ADB confirmed the lock screen and sleeping interaction state. No workaround changed security or timeout settings.

## Independent review and design evidence

The independent code review inspected all nine-table mappings, untrusted-input limits, checksum coverage, financial bounds, FK order/rollback, reactive updates, preferences, SAF/preview/confirmation and isolated debug routes. Its one material cleanup finding was scored **resolved** after the fix; no further material regression was identified within that fix review. The reviewer did not execute tests; the results above are separate physical/JVM evidence.

The independent visual review found typography/material/ground faithful and no redesign needed, then requested the Settings title/launcher rename for discoverability. Its follow-up inspected the corrected capture and source, scored the finding resolved and returned a scoped ship disposition with no naming-fix regression observed. The independent documenter found no new reusable tokens and preserved existing DESIGN.md and schema-2 design.json after comparing source with light/dark/confirmation captures. Native Kotlin has no HTML/CSS detector verdict; none was run. Visual judgments do not certify financial correctness.

## Screenshots and physical-device scope

All app evidence is actual Samsung SM-S942B Android 17/API 37 capture. Final verification uses explicit USB serial `<authorized-device-serial>`; the earlier wireless transport was also this same authorized phone. No emulator, data clear, phone security/timeout change or unrelated system change was used.

| Evidence | File under `docs/screenshots/phase-1.7/` |
| --- | --- |
| Settings entry and real app recovery light/dark | [Settings](screenshots/phase-1.7/settings-entry.png), [light](screenshots/phase-1.7/settings-backup-light.png), [dark](screenshots/phase-1.7/settings-backup-dark.png) |
| Isolated backup and successful export | [Backup](screenshots/phase-1.7/backup-light.png), [saved](screenshots/phase-1.7/backup-export-success.png) |
| Native document-create controls | [Filename/Save region](screenshots/phase-1.7/document-create.png) — an unaltered-pixel crop only, omitting unrelated provider file previews |
| Preview light/dark, gated confirmation, success | [Preview light](screenshots/phase-1.7/restore-preview-light.png), [preview dark](screenshots/phase-1.7/restore-preview-dark.png), [confirmation](screenshots/phase-1.7/restore-confirmation.png), [success](screenshots/phase-1.7/restore-success.png) |
| Existing personal Money/Today smoke | [Money](screenshots/phase-1.7/money-regression.png), [Today](screenshots/phase-1.7/today-regression.png) |

The isolated host is visibly labeled disposable data. No real replacement of personal data occurred for a screenshot. The first confirmation image captured too early; explicit Compose settling and a disabled-button assertion now precede the valid final capture. The picker crop verifies native controls only; it is not evidence for full-screen app layout. Test-created `personal-os-review-*.json` files contain only disposable review records.

## Changed files, limits and next phase

New production files: five `backup/*.kt` files, `data/BackupDao.kt`, `ui/BackupScreen.kt`. Modified production: `PersonalDatabase.kt`, `PersonalOsApp.kt`, `app/build.gradle.kts` (small strict Gson parser dependency). Test-only UIAutomator supports the system picker. Debug-only `BackupReviewData`, nonexported `BackupReviewActivity` and nonexported `BackupFailureProvider` provide isolated fixtures/failure injection. New tests: `BackupCodecTest`, `BackupDatabaseTest`, `BackupIoTest`, `BackupUiTest`, `BackupSettingsTest`. MigrationTest gains migrated-backup round-trip proof; existing screenshot tests use the new Settings description. Material documentation updates are this handover, architecture, data model, product, UX and README. Existing Room schema exports and financial writers/formatter stay unchanged.

Limits: no encryption, signature/authentication, automatic schedule, merge, cloud sync or key management. No broad provider compatibility, low-storage hardware stress, power-loss, TalkBack, RTL, 2x text or tablet/foldable certification is claimed. Current Money's unchanged narrow summary column can wrap long real currency amounts; the screenshot retains that existing behavior rather than altering finance UI in this recovery phase. Starter categories still follow the existing empty-catalog initialization policy; restore itself writes exactly the validated snapshot. Release remains unsigned/unoptimized.

Future encryption should use established authenticated-encryption and password/KDF/key-management libraries with a portable recovery design, authenticated metadata, explicit versioning and independently tested wrong-password/tamper behavior. No custom cryptography or unsupported security claim was introduced. Recommended next phase: input/localization and accessibility polish; subscription settlement can follow as a separately scoped business-domain slice.

## Exact build/device commands

```powershell
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug assembleRelease --console plain
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/device.ps1 devices -l
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial <authorized-device-serial> -DataOnly -ReportName phase-1.7-release-data -TestClass 'com.jevon.personalos.BackupDatabaseTest,com.jevon.personalos.BackupIoTest,com.jevon.personalos.MoneyDatabaseTest,com.jevon.personalos.MigrationTest'
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial <authorized-device-serial> -SkipInstall -CapturePrefix phase-1.7 -ScreenshotDirectory docs/screenshots/phase-1.7 -ReportName phase-1.7-ui-verified -TestClass 'com.jevon.personalos.BackupUiTest,com.jevon.personalos.BackupSettingsTest,com.jevon.personalos.FoundationTest'
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial <authorized-device-serial> -SkipInstall -CapturePrefix phase-1.7 -ScreenshotDirectory docs/screenshots/phase-1.7 -ReportName phase-1.7-settings-confirmed -TestClass 'com.jevon.personalos.BackupSettingsTest,com.jevon.personalos.FoundationTest'
```

Use a currently authorized physical serial. The runner installs with `-r`, never clears app data, and refuses a locked UI run. Data-only runs use isolated databases. Do not run a replacement against the live personal database merely to reproduce test evidence. Preserve only the native picker-control region when preparing shared screenshot evidence, since a provider may display unrelated files.

No commits or pushes were made. Existing uncommitted/untracked user content remains intact.
