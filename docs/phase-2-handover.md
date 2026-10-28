# Phase 2 — subscriptions and recurring payments

Completed 28 October 2026. Final debug build installed on Samsung SM-S942B; 42 JVM tests and 37 distinct physical tests passed. Personal records were preserved. Backups remain unencrypted.

## Implementation plan — 28 October 2026

Extend the existing subscription entity with description, notes and optional account/category references. Room v2→v3 adds nullable foreign keys and empty text defaults without rewriting historical rows. Keep payment occurrence uniqueness and the existing ledger validation/arithmetic boundary. Export backup format 2/schema mapping 3 while continuing to read format 1/schema mapping 2 with explicit safe defaults. An installed older app cannot read the expanded schema or new backup format; rollback requires the prior app and a separately retained prior backup, never a destructive downgrade.

Reuse anchored calendar arithmetic for daily, weekly, monthly and yearly intervals. Keep missed cycles unpaid, permit explicit reanchoring of future renewals, skip recorded occurrences, bound forecasts and separate exact currency-specific equivalents from actual expenses. Correct paid amount/date/account/category on the same linked expense; ordinary Money edit/delete remains blocked with guidance to Subscriptions. Cancel by deactivating, preserving history.

Extend Money → Subscriptions and Today → renewal detail; Quick Add opens a subscription editor. Reuse Inter, blue atmosphere, shared panels/rows and full-screen editor patterns. Verify isolated financial/migration/backup/reactive tests, debug/release/JVM/lint and safe physical workflows/captures. Never seed or replace personal records, clear app data, change phone settings, commit or push.

## Delivered behavior

Subscriptions is a working child of Money, reached also from Today renewal rows and Quick Add → New subscription. It supports creation, editing, description/notes, currency/price, default account/category, custom interval, active/inactive states, search/filter and payment history. Cancel marks the local plan inactive, stops forecasts and preserves expenses/history; it does not cancel a provider contract. Reactivation is explicit. The existing four primary destinations, Inter, blue atmosphere, local outline icons, shared fields/panels/ledger rows and native interactions remain.

The list shows currency-separated monthly/annual billing-rate equivalents. Detail shows up to 12 future unpaid dates within the next year, the oldest missed unpaid date and history. Today keeps priorities first, showing at most four unpaid renewals due within seven days or overdue; changes invalidate reactively. No forecast writes a Money entry. No background service, automatic debit, remote metadata or notifications exists.

## Dates, costs and corrections

- Dates are local calendar days, not UTC billing instants. Every occurrence derives from the original anchor: January 29/30/31 clamps in February and returns to the original day in March. A February 29 yearly anchor returns in leap years. Daily/weekly/monthly/yearly intervals 1–1000 are editable. Existing recurrence end dates remain respected; no general scheduling engine was added.
- Early/late payments retain their billed occurrence and actual effective payment date independently. Paying a later occurrence does not erase earlier unpaid cycles. The cursor advances past contiguous paid occurrences only. Valid older data with a paid cursor is skipped in Today, the list and the payment form without rewriting history.
- Changing the next date, frequency or interval explicitly reanchors future renewals and omits earlier unrecorded dates from future forecasts. The form discloses this. Price-only changes retain the original anchor; recorded expenses keep their actual amounts. Currency cannot change once history exists.
- Equivalent calculations sum exact BigInteger rational minor units per currency and round HALF_UP once, independently for monthly and annual outputs. A month is 1/12 of a year; daily/weekly conversions use the Gregorian mean year, 146097/400 days. These are billing-rate equivalents, not actual charges or a claim that a finite schedule bills for a full year. Large presentation totals do not overflow Long; persisted amounts remain positive Long minor units with existing precision rules, including IDR's two digits. Different currencies never combine.
- Record payment creates one genuine expense plus its unique `(subscriptionId,dueEpochDay)` link inside one Room transaction. Same-currency, active-reference and arithmetic validation comes from MoneyDao. Actual amount may differ from forecast; account balance, monthly/category spending and budgets then update through existing ledger queries.
- History correction retains the occurrence, payment identity and entry identity. Amount/date/account/category/note change together; temporarily detaching the link is entirely inside the transaction, so failure rolls back both. Direct linked Money edits/deletes remain blocked with Subscriptions guidance. A confirmed Undo removes the erroneous expense and link atomically and reopens its date only when it still belongs to the current schedule. It never reactivates a canceled plan.
- Archived defaults remain visible and historical payments remain valid. New payments require an active same-currency account and active expense-compatible category; corrections may retain their existing archived references. Account currency is fixed while transactions or subscription defaults reference it. No shadow balances, currency conversion, silent merging or automatic payments exist.

## Persistence, migration and recovery

Room v3 adds `description`, `notes`, `accountId`, `categoryId` to subscriptions. Explicit 2→3 ALTER statements preserve all old rows; nullable references have RESTRICT foreign keys and indexes. The existing 1→2 migration remains registered. Existing payment uniqueness and entry references remain unchanged. Exported schema: `app/schemas/com.jevon.personalos.data.PersonalDatabase/3.json`.

Backup exports `com.jevon.personalos.backup`, format 2, schema mapping 3. It includes every added field and validates account currency/category references. Format 1/schema mapping 2 imports remain supported with empty description/notes and null optional references, while exact per-version field validation remains strict. Expanded text is included in bounded snapshot preflight. Restore clears subscription children before account/category parents inside the same transaction, without disabling foreign keys. Checksum/caps/security remain as in Phase 1.7: SHA-256 is damage detection, not authentication; files are unencrypted and require trusted storage. Appearance preferences remain separate and excluded.

Both fresh schema creation and migrations from exported v1/v2 schemas are tested, including archived accounts, balances, transfers, reminders, old subscriptions/payment links, FK checks and complete backup/restore afterward. An older installed app cannot read Room v3 or format-2 backups; retain a prior backup and prior app for rollback. There is no destructive downgrade fallback.

## Verification and review

| Gate | Actual result | Evidence |
| --- | --- | --- |
| Debug/test APK and unsigned release builds | Passed | `app/build/outputs/apk/` |
| JVM tests | 42 passed, zero failures | `app/build/test-results/testDebugUnitTest/` |
| Android lint | Zero errors, 19 advisory warnings | `app/build/reports/lint-results-debug.html` |
| Isolated Samsung data tests | 30 passed | `app/build/reports/phase-2-data.txt` |
| Full subscription workflow | Passed, then repeated successfully on the final build | `app/build/reports/phase-2-workflow-confirmed.txt` |
| Final legacy/calendar/SAF/UI regression suite | 7 passed on the final build | `app/build/reports/phase-2-ui-final.txt` |

The 30 data tests comprise SubscriptionDatabaseTest 9, MigrationTest 2, MoneyDatabaseTest 10, BackupDatabaseTest 6 and BackupIoTest 3. The final UI suite adds SubscriptionWorkflowTest 2, BackupUiTest 1, BackupSettingsTest 1 and FoundationTest 3: 37 distinct physical tests in total, without counting repeated runs again. New coverage proves management/cancellation, exact linked financial/budget changes, early/late/missed occurrences, duplicate/concurrent attempts, correction and rollback, account/category/currency safeguards, overflow rollback, undo, new-field backup replacement and reactive Today updates. Seven new pure recurrence/equivalent tests plus one legacy/new-format codec test extend the 34 prior JVM tests.

The isolated UI workflow creates monthly IDR and annual USD plans, edits price/date, checks cost summaries in light/dark, records an actual payment differing from forecast, proves the linked expense/balance/budget values, rejects a duplicate attempt, corrects the same expense, checks Money/Today, cancels with history retained, performs a complete backup replacement and closes/reopens a separately named database to prove persistence. The legacy UI test checks a paid imported cursor and typed dates outside the native picker's default range. Personal Settings/navigation/draft-restoration smoke compares all nine tables before/after. No personal restore or test subscription is written to the personal database.

The personal audit matched the prior local snapshot; detailed counts remain in untracked device evidence. The complete personal snapshot compared equal around the smoke test, with original appearance preferences restored. Physical device: Samsung SM-S942B, Android 17/API 37, wireless serial `<authorized-device-serial>` during this run. No emulator, data clear, phone security/timeout or unrelated system change occurred.

Critical review traced billing-date seek/anchoring, exact cost rounding, ledger ownership, uniqueness/races, rollback, archived references, backup completeness/limits, migration FKs and reactive Today. It found and corrected the legacy paid-cursor display/form case. Lint also caught an API-33 BigInteger constant, replaced with an API-26-compatible value. Earlier UI attempts found test-only offscreen lazy-list and ambiguous host-button selectors; both were corrected. A locked-phone attempt was refused before UI input; the final suite passed after the user unlocked it. The independent visual reviewer returned **ship**, matching TYPE/MATERIAL/GROUND and native form/navigation conventions, with no material fixes. Its verdict does not certify accounting correctness. The independent documenter compared finished source and light/dark captures to DESIGN.md and its sidecar, found no new durable tokens and preserved both files.

## Screenshots and files

Actual Samsung captures are under `docs/screenshots/phase-2/`: [list light](screenshots/phase-2/subscriptions-light.png), [list dark](screenshots/phase-2/subscriptions-dark.png), [empty](screenshots/phase-2/subscriptions-empty.png), [editor with keyboard](screenshots/phase-2/subscription-editor.png), [detail](screenshots/phase-2/subscription-detail-light.png), [payment form](screenshots/phase-2/record-payment.png), [inactive/history](screenshots/phase-2/subscription-inactive-history.png), [reopened database](screenshots/phase-2/subscriptions-reopened.png), [linked Money](screenshots/phase-2/linked-money.png), [Today after payment](screenshots/phase-2/today-after-payment.png). Isolated screens disclose disposable data. Additional Settings/Money/Today captures come from non-destructive personal smoke.

New production files: `data/SubscriptionDao.kt`, `subscriptions/SubscriptionModel.kt`, `subscriptions/SubscriptionViewModel.kt`, `ui/SubscriptionScreen.kt`. Modified: entities/database/application migration registration; MoneyDao's referenced-account currency guard and linked-entry guidance; MoneyQueries/TodayScreen; PersonalOsApp navigation; shared MoneyEditors visibility; MoneyViewModel's default-compatible database injection; BackupCodec/Data/Store/Dao; exported schema3. No new runtime dependency. Debug-only nonexported SubscriptionReviewActivity uses a separately named UUID database, never Application.database. New tests: SubscriptionModelTest, SubscriptionDatabaseTest, SubscriptionWorkflowTest; existing migration/codec/Foundation/Settings tests extend compatibility and actual navigation proof. README and current product/architecture/model/UX docs updated. No commits or pushes.

## Limits and next phase

No scheduled notifications, automatic debits, provider cancellation, bank integration, logos/metadata service, cloud sync, new business domains or encryption. Canceled plans retain history rather than being hard-deleted. Missed cycles require explicit payment or reanchoring; they are never guessed as paid. Upcoming detail shows at most 12 dates within one year; the model caps callers at 128, and Today caps four. Search/filter runs over the local catalog and the screen uses lazy rows; no large-catalog stress benchmark, TalkBack, RTL, tablet/foldable or 2× text certification is claimed. New-plan editor supports four recurring frequencies; legacy one-time records remain readable. Native calendar selection uses its default 1900–2100 range; typed ISO dates 1–9999 remain accepted and values outside the picker range open without an invalid initial selection. Payment dates retain the existing Money date contract. App-local midnight clock refresh is every 30 seconds. Release remains unsigned/unoptimized.

Recommended next phase: accessibility/localization and large-catalog input polish, followed by separately scoped local reminders/notification permission handling. Keep notification scheduling distinct from actual payment recording.

## Reproduction commands

Set `ANDROID_HOME` to the actual SDK path on this workstation when the shell has a redirected profile; the scripts find the existing workspace JDK without changing Windows settings. Select a currently authorized physical serial; the endpoint may change.

```powershell
$env:ANDROID_HOME = 'C:\Users\jevon\AppData\Local\Android\sdk'
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug assembleRelease --console plain
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial <authorized-device-serial> -DataOnly -TestClass 'com.jevon.personalos.SubscriptionDatabaseTest,com.jevon.personalos.MigrationTest,com.jevon.personalos.MoneyDatabaseTest,com.jevon.personalos.BackupDatabaseTest,com.jevon.personalos.BackupIoTest' -ReportName phase-2-data
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial <authorized-device-serial> -TestClass 'com.jevon.personalos.SubscriptionWorkflowTest,com.jevon.personalos.BackupUiTest,com.jevon.personalos.BackupSettingsTest,com.jevon.personalos.FoundationTest' -CapturePrefix phase-2 -ScreenshotDirectory docs/screenshots/phase-2 -ReportName phase-2-ui-final
```

Installation uses `-r`; no app-data clearing. Disposable test cleanup is restricted to its UUID-named review database. Never run a destructive restore on personal data to reproduce evidence. Native document-picker captures may contain unrelated provider previews; crop the control region before sharing any such capture.
