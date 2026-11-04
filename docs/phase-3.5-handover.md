# Phase 3.5 — Today personalization and safe record review

4 November 2026, Asia/Jakarta. Implemented, installed on Samsung SM-S942B with `adb install -r`, and locally committed. No Phase 4 work or push. Personal financial/scheduling records were preserved.

## Root cause

The production `todayBudget` query filtered to the current calendar month's overall budgets, then used `ORDER BY currencyCode,id LIMIT 1`. AUD therefore preceded IDR. Its A$100 spending against A$80 was correct; selection was the usability defect. The old sample projection also selected the first matching overall budget. Both now use the same explicit selection policy. The obsolete first-currency SQL query is removed.

The reported `P1 test budget 402330` is still present, still AUD, still A$80, with its A$100 expense retained. Its period is 1 October–1 November 2026 (exclusive end). It is already expired on the verification date. The installed personal database has **zero eligible November overall budgets**; personal empty-state evidence alone must not be mistaken for proof of a populated currency choice. Current-period, multi-currency selection is additionally proved with isolated fixtures and JVM cases.

## Preferred currency

Settings now accepts a validated monetary ISO currency code. Missing or unsupported saved currency defaults to IDR. DataStore keys are `preferred_currency` and `today_budget_id`; existing theme and wallpaper keys retain their behavior.

The preference supplies defaults for new accounts, budgets and subscriptions, the initial Money balance currency, and Today's automatic budget choice. Money retains its currency-specific balance selector and falls back to an available account currency when the preferred currency has no active accounts. Amounts remain explicitly currency-labeled. Existing editors retain each record's saved currency; transaction and payment currencies still come from their selected accounts/plans. A preference change does not convert balances, rewrite rows, combine currencies, or reinterpret subscription payments. Draft currency choices are not overwritten.

## Today budget rules

1. Eligible means an **overall** budget with exact current-calendar-month start and exclusive next-month start. Category limits and legacy intervals that merely overlap the month are ineligible.
2. A saved eligible ID wins, including a deliberately selected foreign currency. Changing preferred currency or adding another budget cannot displace it.
3. Without a valid explicit choice, choose only the preferred currency. Multiple candidates use stable ID order, independent of input/name order. Current writers prohibit same-currency overlapping scope, but legacy backups can contain multiple candidates; fallback is deterministic for those too.
4. With no preferred-currency candidate, show a useful no-budget state. Never silently substitute another currency. The chooser still offers eligible budgets in other currencies, preferred-currency choices first.
5. Deleted, expired or changed-scope choices use the same automatic fallback. The stored ID remains until the user chooses another budget or Automatic; the chooser explains an unavailable saved choice. No automatic entity or preference cleanup occurs.
6. Budget entities have no archive flag. Account/category archival does not itself archive an overall budget. There is no invented budget-archive state or Room change.

Today identifies the chosen name and actual ISO currency, retains explicit overspending and the shared financial progress component, and offers **Choose Today budget**. **Manage budgets** opens Money's Budgets section at the current month. Priorities, monthly finance, upcoming schedule/payments and currently enjoying retain their existing structure. Production spending reuses Money's `budgetProjections` and `BudgetSummary`; there is no new parallel calculation or FX layer.

## Development-record review

Money's Accounts, History, Budgets and Categories sections offer **Review possible test records**. The small read-only sheet matches only exact case-sensitive `P1 test ` / `P15 restore ` prefixes traced to the earlier physical-device harnesses. Currency, amounts, unusual names and linked relationships alone never flag a candidate. Other prefixes are intentionally not guessed.

The installed read-only inventory found **42 labeled candidates**: **19 accounts, 16 transactions, seven budgets, zero labeled categories**. They include the reported AUD `402330` group, other `P1 test` bank/cash/lunch/income/budget groups, and four `P15 restore` IDR accounts. These are candidates for human review, not certified disposable records. Exact IDs and full relationship details remain in ignored `.tools/review/phase-3.5/personal-review.txt`, avoiding redundant personal-data documentation in Git.

Expandable details show account references, transactions/dates/currencies, categories, affected budget scopes and subscription/payment dependencies where present. Lazy rows keep expanded relationships scrollable. Review links return to the existing management section; History and Budgets may require selecting the date/month shown in the review. There is no bulk delete, cascade cleanup, new database-admin surface or personal-storage SQL deletion.

Existing workflows remain responsible for actions: archive accounts/categories to retain history; budget deletion keeps entries/balances; deleting an individual expense/income changes balances and spending, while deleting a transfer affects both accounts. Linked subscription payments must be corrected in Subscriptions payment history. Related personal records are displayed for context and never classified as disposable merely because they are linked.

## Data safety and backup treatment

No personal entity was inserted, edited, deleted, archived, restored, or currency-relabeled by this phase's verification. Full portable personal snapshots compared equal before/after installed-app tests, including their failed first harness attempt and final passing run. The read-only audit also passed foreign-key validation and complete in-memory backup encode/decode equality, including its available earlier count baseline. Test preference changes were restored in `finally` blocks.

Room remains **v4**, with all explicit nondestructive migrations unchanged. Backup remains **format 3 / schema mapping 4**, accepting earlier formats as before. No destructive fallback is introduced. New DataStore choices are **device-local preferences, excluded from exported backup files**, like appearance. Restore leaves them unchanged; an absent/ineligible restored budget ID safely follows the fallback policy. Existing financial/scheduling backup data and atomic replacement rules are unchanged. Android automatic app backup remains disabled. No preference portability is claimed.

## Validation

| Gate | Result |
| --- | --- |
| JVM `testDebugUnitTest` | **61 passed**, zero failures/errors/skips: prior 54 plus seven personalization/review cases |
| Debug APK, Android-test APK | Passed; final production debug APK installed with `-r`, no data clear |
| Unsigned release APK | Passed |
| `lintDebug` | **0 errors, 19 existing advisory warnings** |
| Physical data/integration | **40 distinct cases passed**: Money 10, subscriptions 9, Schedule 8, backup DB 6, migrations 3, new Today DB 1, DataStore 2, personal audit 1 |
| Physical focused UI | **2 passed**: installed-app personalization/review and isolated Today currency/overspending fixture |
| App-scoped crash inspection | `logcat -d --uid=10037 -v threadtime AndroidRuntime:E '*:S'` returned no error entries |
| Git whitespace | `git diff --check` and staged check passed |

The initial device data run was 38/39: the old reactive Money fixture assumed automatic USD selection. It now explicitly sets USD, additionally asserts default IDR does not select it, and the focused rerun passed. The initial installed-app UI harness failed locating the virtualized bottom Close button; scrolling its lazy list fixed the harness, and both final UI cases passed. Neither failure required changing financial persistence or arithmetic.

Real DataStore file tests initially failed in the Windows JVM environment with `FileStorage` rename errors. Those tests belong to Android storage instrumentation and both passed on the Samsung, including default/legacy IDR, unsupported preference handling, preferred currency and explicit-ID persistence across store recreation, clearing explicit selection, and retaining appearance settings. No production storage workaround or new dependency was added.

Relevant commands (all through `rtk`; host SDK is `C:\Users\jevon\AppData\Local\Android\sdk`):

```powershell
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -Command '$env:ANDROID_HOME="C:\Users\jevon\AppData\Local\Android\sdk"; & .\tools\gradle.ps1 testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug assembleRelease --console plain'
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial PHONE_SERIAL -DataOnly -TestClass com.jevon.personalos.TodayPersonalizationDatabaseTest,com.jevon.personalos.SettingsStoreTest,com.jevon.personalos.MoneyDatabaseTest,com.jevon.personalos.SubscriptionDatabaseTest,com.jevon.personalos.ScheduleDatabaseTest,com.jevon.personalos.BackupDatabaseTest,com.jevon.personalos.MigrationTest -ReportName phase-3.5-data
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial PHONE_SERIAL -DataOnly -SkipInstall -TestClass com.jevon.personalos.MoneyDatabaseTest#reactiveTodayAndMoneyEmitCommittedTotals,com.jevon.personalos.PersonalDataAuditTest -ReportName phase-3.5-data-confirmed
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial PHONE_SERIAL -TestClass com.jevon.personalos.TodayPersonalizationDeviceTest,com.jevon.personalos.TodayPersonalizationUiTest -CapturePrefix phase-3.5 -ScreenshotDirectory docs/screenshots/phase-3.5 -ReportName phase-3.5-ui-confirmed
```

Private device reports: `app/build/reports/phase-3.5-data.txt`, `phase-3.5-data-confirmed.txt`, `phase-3.5-ui.txt` (initial failure), `phase-3.5-ui-confirmed.txt` (both passed). Counts above are distinct cases, not repeated executions. Earlier passing suites were reused after test-only/harness changes; the final production UI test tag was covered by the final UI run and subsequent debug/release/lint gates. No emulator was started.

## Physical-device behavior and screenshots

Verified on the actual SM-S942B through its already-paired wireless ADB transport. Installed app: default IDR projection, preferred currency edit/save to USD, Activity recreation reading it back, guaranteed no-budget state without entity writes, new budget currency default, chooser opening, Money budget routing, expandable candidate relationships, Schedule and Subscriptions navigation, light/dark rendering and complete personal snapshot preservation. Isolated physical fixtures: deliberate AUD choice while IDR is preferred, A$100 spent of A$80 with A$20 over budget, actual currency label, return to Automatic and no-budget management action. Database tests separately cover saved IDs, deletion/ineligibility, currency-aware totals and restore preservation.

Local ignored screenshots under `docs/screenshots/phase-3.5/`:

- `personal-today-idr-light.png`, `personal-no-budget-light.png`, `personal-no-budget-dark.png` — installed personal Today, current-month empty state.
- `personal-budget-chooser.png`, `preferred-currency-settings.png` — installed chooser/Settings. Settings capture includes the keyboard; the scrollable controls remain usable, as the passing test demonstrates.
- `personal-test-record-review.png`, `personal-test-record-relationships.png` — candidate list and expanded relationship/consequence review.
- `today-no-preferred-budget-light.png`, `today-explicit-aud-overspent-light.png`, `today-explicit-aud-overspent-dark.png` — isolated Today fixtures. The light overspending capture caught a native dialog transition and is not settled visual proof. The dark fixture capture clearly shows the correct amount/name/currency; its standalone test host system bars are not the shipping Activity's bar treatment.

The installed light/dark and relationship captures were visually inspected alongside the existing Phase 3 capture. No major design change or material clipping was found in the installed views. A later optional isolated screenshot-refresh launch was stopped by the lock guard after the phone relocked; no unlock bypass or phone setting change was attempted. Passing UI results above precede that optional attempt and remain valid.

## Commits and remaining limits

- `9f44da9` — `feat(today): personalize currency and safely review test records` (source and focused tests).
- Documentation commit: `docs: record Phase 3.5 handover`; resolve its hash with `git log -1 --format=%h -- docs/phase-3.5-handover.md` because a commit cannot embed its own final hash.

The pre-existing untracked `app/src/androidTest/java/com/jevon/personalos/NotificationDeviceTest.kt` remains untouched and uncommitted. No push, personal cleanup, currency conversion, security-setting change, notification rework, backend, Library implementation or Phase 4 work occurred.

There is no current-month personal budget available to exercise a live populated personal selection across a full process restart without inserting records. Persistence is instead proved by real Android DataStore recreation, query integration and isolated picker UI; installed-app preference restoration uses Activity recreation, not a claimed cold-process selection test. No personal budget was created just to fill that gate. TalkBack, 2× text, RTL, tablet/foldable and broad performance certification are outside this focused pass. Current-period legacy duplicate handling has JVM proof; normal writers still prevent duplicates. The older Money workflow harness was updated for explicit selection but was not rerun because it creates retained records in personal storage.

The Impeccable loader reported the pre-existing PRODUCT.md schema as legacy. An optional `impeccable init` refresh belongs to a separate requested documentation task; this phase does not rewrite product authority.

Before Phase 4, the user can create a genuine current-month IDR budget and optionally choose it on Today, then make a fresh financial backup. Review development candidates individually only when their relationships are understood. A later live populated-selection cold-restart check can use that genuine budget. Start Library work only as a separate authorized phase.
