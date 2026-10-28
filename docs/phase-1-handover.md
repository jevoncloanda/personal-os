# Personal OS — Phase 1 handover

24 October 2026, Asia/Jakarta. Native Kotlin/Compose, local-first. No emulator, backend, commits, pushes, app-data clearing, device-security changes or unrelated system changes were used.

## Delivered

Money is a persistent usable domain: create/edit/archive/restore accounts; record expense/income/same-currency transfers; correct kind/amount/accounts/category/date/time/notes; delete entries with confirmation; browse monthly history with search, currency/kind/account/category filters and pages; create/rename/archive/restore categories; create/edit/delete overall or category monthly budgets. Opening and calculated balances, separate currency income/spending, budget remaining/progress/overspending and recent entries are real Room data. Money always uses personal storage, even while Today previews samples.

Today now leads with date/greeting and compact priorities, then monthly finances, later-today events and recurring-payment forecasts, active leisure, and relevant undated reminders. Empty calendars no longer take a primary section. Existing debug previews remain memory-only behind a debug-only Developer previews action; Personal is the default. Expense, income and transfer Quick Add actions open working editors. Other domain actions explicitly say preview. Native blue primary roles refine the incumbent Material system while retaining dynamic color and deliberate light/dark themes.

Forms have labeled fields, currency precision help, scroll/IME insets, keyboard Next actions, validation, recoverable save errors, busy protection, cancellation without writes and destructive-action confirmation. Native selection menus handle bounded choices. Date/month/time currently use explicit YYYY-MM-DD / YYYY-MM / HH:mm text input. Full-screen native editor dialogs allow more room than small alert forms. Account opening balances permit a minus sign via the text keyboard.

## Financial and architecture decisions

- Retained one Room database, one app module, application-owned dependencies, ViewModel/Flow, Navigation Compose and Material 3. `MoneyDao` owns financial rules; inherited by `PersonalDao`, so existing insertion callers use the same validation.
- Opening balances are signed adjustments, never income. Current account balance is opening + all incoming − all outgoing, including future-dated entries. Negative balances are valid. Currency cannot change once transactions exist.
- Accounts/categories are archived/restored rather than deleted. Historical references remain. Renames intentionally change labels in history. Archived references can be retained while correcting their original transaction; new transactions require active references. Category kind is fixed. V1 categories remain compatible with either income or expense.
- Positive exact integer minor units; ISO precision with explicit IDR two-digit handling. Decimal parsing rejects grouping, exponents, invalid currency, overprecision, zero transaction amounts and overflow. No floating-point accounting or currency conversion. Progress alone uses floating point.
- A transfer is one row with distinct same-currency accounts and no category. Editing/reversing/deleting it changes both effects within one transaction. General entry corrections/deletion reject subscription-payment-linked entries; settlement remains a future domain operation.
- Each financial mutation validates within a Room transaction and checks arithmetic before commit. SQLite integer SUM is used, never floating-point TOTAL. Account final balance uses exact intermediate arithmetic and checked Long conversion. Lifetime sums per currency/kind and each account's incoming/outgoing sums must fit Long, even if offsetting values would yield a valid net. This conservative aggregate ceiling is deliberate; failures roll back.
- New budgets are exact calendar months, inclusive start/exclusive end. Same-currency/same-scope overlap is rejected. Overall includes all expenses; category budgets are separate sublimits, never added together. Income, transfers, opening balances and subscription forecasts do not count. Transaction effective local date is authoritative; local time does not shift the date on timezone changes.
- Today chooses one exact current-month overall budget ordered by currency code then stable ID. It does not add currencies. Without a budget it offers Money setup. Legacy irregular/overlapping v1 budgets are preserved, remain visible in Money while active, and must satisfy current rules when edited.
- Room invalidation plus `withTransaction` emits a coherent projection. SQL aggregates balances, monthly income/expense and budget spending. History reads 51 rows (50 visible, plus next-page detection); Overview separately reads five unfiltered recent entries. Today reads one overall budget and bounded event/reminder/payment/library lists. No full ledger list is collected into Compose.
- Account/category catalogs remain metadata lists. Integrity aggregate checks scan stored financial totals on writes; high-volume optimization needs a measured, tested incremental design later. No benchmark is claimed.
- Filters/month/page are saved in SavedStateHandle. Editor fields are saveable drafts. A restored unavailable edit identity blocks saving instead of accidentally creating a duplicate.

## Migration

Room v1 → v2 is explicit, additive and nondestructive:

| Table | New fields/defaults |
|---|---|
| accounts | `type='OTHER'`, `notes=''` |
| categories | nullable `kind`, `archived=0` |
| entries | `minuteOfDay=0` |

V1 had no transaction time; midnight is a compatibility default, not inferred historical precision. Existing records/IDs/amounts/foreign keys/indices remain. Both exported schemas are retained under `app/schemas/com.jevon.personalos.data.PersonalDatabase/`.

The migration test creates the actual exported v1 schema, loads negative opening balance, archived account, transfer, expense, budget and nullable reminder fixtures, then opens with current Room and the production migration. Room validates the complete v2 schema; assertions verify preservation/defaults and foreign-key integrity. The initial Room testing helper failed inside its serialization dependency before migration execution. The final test uses the already installed SQLite helper and Android JSON APIs, avoiding that added dependency. An optional JSON `indices` member handling issue was also corrected before the passing run. Production migration did not need a destructive recovery.

## Verification executed

| Check | Actual result |
|---|---|
| Debug APK, test APK, Kotlin/Room compilation | Passed |
| Unsigned release APK | Passed |
| Final JVM tests | 15 passed: existing 10 + 5 exact input/format/balance tests |
| Android lint | Passed; 0 errors, 14 dependency/Kapt advisory warnings |
| Physical financial database tests | 9 passed |
| Physical exported-v1 migration test | 1 passed, complete Room v2 schema validation |
| Existing physical foundation tests | 3 passed: Room/transfer/IDR, navigation/Quick Add, concurrent appearance writes |
| Existing physical screenshot suite, adapted to hidden developer controls | 1 passed: populated/empty/personal/light/dark/dynamic/Quick Add |
| Physical Money end-to-end workflow | 1 passed |
| Separate fresh-process persistence verification | 1 passed |

**16 distinct instrumentation tests passed across separate invocations**, not one final all-tests report. The first combined UI run passed the four foundation/visual tests but its new workflow stopped on an ambiguous Accounts selector. The focused workflow passed after the selector was corrected. No financial application error caused that selector failure.

Financial tests cover opening/balances, income/expense, changed accounts, transfer reversal/edit/deletion, archived records, category renaming/reassignment, mixed currency and invalid time/category rejection, half-open budgets, category limits, overspending, coherent reactive Today/Money snapshots, overflow rollback, filtering/paging and default-catalog preservation. Unit coverage adds currency precision, ambiguous decimal rejection, Long boundaries, signed balances and formatting. The original 10 foundation unit tests remain.

The native Money workflow created two accounts, recorded expense/income, transferred money, corrected the expense, deleted the transfer, created/adjusted an overall budget, checked exact persisted balances, verified the Today projection, rendered light/dark, recreated the Activity and confirmed a cancelled draft was not saved. A separate instrumentation process then read the saved records and displayed their balances, proving process-restart persistence.

## Physical device and evidence boundary

Samsung SM-S942B, previously established Android 17/API 37. One authorized wireless transport was listed and explicitly selected: `<authorized-device-serial>`. Installations used current Android user 0 and `-r`, preserving data. The user unlocked once when requested; tests kept only their own Activity window awake. No timeout/security setting was changed.

**The phone disconnected before the last review fix could be installed/recaptured.** One transport inventory check then returned no devices. The final build contains the compact History Filters sheet, independent Overview recent query, saved history page and unavailable-restored-editor guard; these last changes compile and pass local checks, but are not physically rechecked or deployed. The phone retains the earlier working Phase 1 build with inline History filters. No final app-scoped Logcat check was possible after disconnect; Phase 0 logs are not presented as Phase 1 evidence. Reconnect and run the commands below to finish that last device gate. No repeated device waiting or emulator workaround was used.

Only disposable `P1 test …` records were created/changed. No pre-existing personal records were edited or removed. The initially interrupted selector run left one clearly labeled partial test account/history; the completed run left two accounts and an overspent budget. They are retained for review, not silently cleaned from personal storage. The test chooses an unused currency to avoid changing an existing overall budget. The completed captured USD run ends at bank US$1,200, cash US$50, expense US$100 and income US$300, budget US$80 with US$20 overspending; its test transfer was deleted atomically. The partial IDR test bank remains IDR1,174.50.

Reports: `app/build/reports/phase-1-data.txt` is the passing 10-test database/migration run. `app/build/reports/phase-1-ui.txt` currently contains the passing fresh-process persistence invocation; the runner replaces this file on each UI invocation. The five-test UI/individual workflow results are preserved in the session tool outputs, not misrepresented as the latest report. JVM XML and lint HTML/XML live in the normal build report folders.

## Screenshots and design review

All Phase 1 images are physical native captures under `docs/screenshots/phase-1/`, separate from historical emulator evidence:

- `phase-1-light.png`, `phase-1-light-lower.png`, `phase-1-dark.png`: populated Today, including sample-data disclosure.
- `phase-1-empty.png`, `phase-1-personal.png`, `phase-1-dynamic.png`, `phase-1-quick-add.png`: preview/appearance/sheet states before the Money test records were completed.
- `accounts-light.png`, `accounts-dark.png`, `budget-overspent.png`, `today-personal-light.png`, `today-personal-dark.png`: real disposable Money records and Today integration.
- `history-light.png`: **pre-refinement** inline-filter History; not evidence of the final Filters sheet.
- `cold-restart-money.png`: separate-process persisted account screen.

The independent Impeccable reviewer validated the supplied 13-capture packet, retained the native hierarchy/amounts/blue roles and requested three material fixes: explicit direction authority, current product/design records, and compact History filters. The contract now quotes the actual user's existing-project/native/blue-refinement/autonomous constraints without inventing a retrospective concept seed. Product documentation and the token-bearing design record/sidecar were updated. History now keeps search/active filters above results and moves four selectors to a native Filters sheet with 8dp gaps. The verdict pass scored authority and persistence resolved, History density partial pending native capture, with disposition **fix**. The last physical render remains unverified due to disconnect; no whole-surface approval is claimed. Native Kotlin has no HTML/CSS detector pass; no shipping raster assets were introduced.

## Changed files

- Persistence: `data/Entities.kt`, `PersonalDatabase.kt`; new `MoneyDao.kt`, `MoneyQueries.kt`; exported schema `2.json`; database builder migration registration in `PersonalOsApplication.kt`.
- State: new `money/MoneyViewModel.kt`; `today/TodayViewModel.kt` uses the coherent bounded personal projection and defaults to Personal.
- UI: new `ui/MoneyScreen.kt`, `MoneyEditors.kt`; `PersonalOsApp.kt`, `TodayScreen.kt`, `Theme.kt`.
- Tests: new `MoneyInputTest.kt`, `MoneyDatabaseTest.kt`, `MigrationTest.kt`, `MoneyWorkflowTest.kt`, `MoneyPersistenceTest.kt`; existing `FoundationTest.kt` and `VisualReviewTest.kt` adapted without removing their behavioral coverage.
- Build/support: `app/build.gradle.kts` exposes exported schemas as test assets; new `tools/run-phase1-device.ps1`; expanded Android/secrets `.gitignore`.
- Documentation: README, product/architecture/data/UX specifications, this handover, `docs/DESIGN.md`, `.impeccable/design.json`, physical screenshot directory.

Git was confirmed absent and initialized as requested. All existing/new source remains uncommitted; no commit/push or remote was created. SDK local paths, `.tools/`, Gradle/build output, APK/AAB files, keystores and environment secrets are ignored. Ignore behavior was checked for local.properties, isolated JDK and APK output.

## Remaining limits and next phase

No requested core Money workflow is a placeholder. The remaining Phase 1 gate is physical re-verification/deployment of the final refinements after reconnection. Typed date/time inputs, English copy, full account/category selectors, conservative aggregate checks and a deterministic first-currency Today budget are documented usability/performance choices. TalkBack, 2× fonts, RTL/localization, tablet/foldable layouts, hardware gesture/performance profiling, older physical Android versions, forced storage failures and arbitrary dynamic-wallpaper contrast are not certified by this run. Release remains unsigned/unoptimized. Export/backup recovery is still deferred and automatic Android backup remains disabled.

Future domain editors, recurring occurrence/completion semantics, notifications, subscription settlement, library edits, widgets, FX, cloud/auth/backend and sync were not added. Recommended next: complete the final phone gate, then subscription settlement with a unique due-period/actual-expense transaction. Address intentional export/recovery before relying on the app for irreplaceable records; schedule/notification and widget work remains a separate phase. Product decisions remaining: a Today currency chooser, opening-balance audit history, native date/time pickers/localization and explicit backup/export policy.

## Exact commands

From `C:\Jevon\Code\personal-os`:

```powershell
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug assembleRelease --console plain
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/device.ps1 devices -l
# Substitute one currently authorized physical transport; no emulator.
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial YOUR_PHONE_SERIAL
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial YOUR_PHONE_SERIAL -DataOnly -SkipInstall
# Separate invocation proves a fresh process reads the saved workflow records.
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial YOUR_PHONE_SERIAL -TestClass com.jevon.personalos.MoneyPersistenceTest -SkipInstall
```

The first runner command installs both APKs for the current user with `-r`, refuses locked-phone UI tests and saves/pulls app-only screenshots. DataOnly uses isolated test databases and can run while locked. Money workflow tests create clearly identified disposable records; do not substitute personal records. Wireless serials change. No final transport is presently connected.

Latest build artifacts: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release-unsigned.apk`. Use the debug APK for data-preserving review; the release application ID is separate.
