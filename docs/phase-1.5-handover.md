# Personal OS — Phase 1.5 handover

24 October 2026, Asia/Jakarta. Existing Kotlin/Compose/Room application retained. No emulator, commits, pushes, backend, AI, bank integration, destructive migration, application-data clearing or unrelated system changes.

## Summary

Today, Money, native editors, navigation and the remaining foundation surfaces share the approved sky-blue visual language. The actual reference is retained at [finance-ui-direction.png](design/references/finance-ui-direction.png). Its sky/mist ground, rounded pale panels, financial type hierarchy, pill actions, restrained depth and central action have been adapted to Android; iPhone chrome and banking/AI claims were excluded.

Money now leads with an explicitly denominated total of active accounts, followed by expense/income/transfer actions, month selection, actual spending/income, budgets, category spending shares, account rows and recent activity. History groups entries by effective date and retains search, filters and paging. Today retains priorities before compact financial headroom, plans/payment forecasts and leisure. Quick Add lives in native navigation, no longer over scroll content; large text gets a separate action row and expanded layouts retain a rail.

## Currency diagnosis and exact behavior

The account editor already defaulted to IDR before this phase. Settings contain appearance only, not a hidden currency preference. The Phase 1 dollar screenshots show actual USD test accounts/budgets: the test deliberately chose an unused currency to avoid modifying an existing overall budget. Today selected an existing overall budget deterministically; it did not convert or relabel it. Two presentation/default issues remained: the shared formatter followed the device locale for IDR, and a new budget could inherit the alphabetically first existing currency.

- New accounts and budgets now use the central `DEFAULT_CURRENCY = "IDR"`; existing records retain their own code.
- IDR defaults to `id-ID`: `Rp25.000`, `Rp1.500.000`, fractional `Rp25.000,50`, negative `-Rp25.000`, zero `Rp0`. Currency spacing is normalized across JVM/Android ICU.
- USD defaults to US formatting (`$25,000`, `$12.34`). Other currencies retain their ISO precision and normal locale formatting. Explicit formatter locale overrides remain supported.
- Stored money is still Long integer minor units; IDR remains two decimal minor digits. Input is an ungrouped decimal point, independent of localized output. No floating-point accounting or FX is introduced.
- An account without transactions can have its currency explicitly corrected in the editor. With history, the DAO rejects currency changes and the UI explains creating a separate correctly denominated account. Historical USD entries/opening balances/budgets are never silently changed to IDR.
- The Money header groups active balances by currency, excluding archived accounts. A presentation-only BigInteger total prevents multiple valid Long balances wrapping when combined. Separate currencies are never summed. Today keeps its prior deterministic overall-budget selection rule; actual foreign-currency budgets still display their actual denomination.

## Financial preservation and visualizations

Room stays at version 2. Neither table shape nor stored record changes are needed for this phase; existing `MIGRATION_1_2` and exported v1/v2 schemas remain. Financial writes continue through the same transactional DAO validations. Opening balances, allowed negative balances, same-currency single-row transfers, corrections/deletion, archive/history, budgets and forecasts retain their previous accounting rules.

A month-bounded SQL GROUP BY adds expense-category aggregates to the existing coherent transactional snapshot. The category bars use all actual selected-month/selected-currency spending as their denominator. The first four categories are shown individually, with a remaining tail as Other categories. Empty/zero data produces no invented chart. Amount text accompanies progress semantics. Income, transfers and forecasts never count as category spending; category budgets are not added to an overall budget. No chart dependency, decorative trend line, savings claim or fabricated insight was added.

Only clearly identified disposable `P1 test …` and `P15 restore …` records were created/edited by physical workflow tests. Earlier personal records were not edited or deleted. These records remain available for review and may make the app contain several actual currencies. Their balances and transactions are test inputs processed by the normal forms/Room writes, not demo data seeded for screenshots. Debug Today samples remain isolated memory-only previews with disclosure.

## Design system and screens

The reusable primitives in `VisualSystem.kt` provide atmospheric background, frosted-looking panel, icon badge, adaptive ledger row, progress and system-bar appearance. Theme tokens are centralized in `Theme.kt`.

- Ground: light sky `#B2DDF8` → mist `#E8F3FC` → `#F2F5FC`; night `#173E66` → `#11273F` → `#0B1829`.
- Panels: light `#F0F7FD`, night `#1C334D`, fine role-based borders and 2dp elevation. Opaque tonal panels approximate frosting; alpha rows have no shadow. No runtime backdrop blur or per-frame GPU effect.
- Primary: accessible blue `#2462C3` / pale night blue `#A8CDFF`; ink `#172533` / `#EAF2FD`. Semantic income, warning and error roles remain separate. Wallpaper colors affect supporting roles while primary/atmosphere stay blue.
- Type: existing platform sans, no font download/license dependency. Material financial display 38sp/44sp with semibold weight and tabular numeral features; deliberate heading/title hierarchy and readable metadata.
- Shape: 28dp panels, 20dp row surfaces, 12dp fields/icon badges, native pill actions; compact spacing and wrapping/scrolling instead of fixed text heights.
- Forms: shared atmosphere and native labeled fields; field validation after Save, visible form errors, dirty-draft Discard/Keep editing, saveable fields/identity and keyboard-safe Save. Date/time remain explicit ISO/24-hour text input.

Affected screens: Today, Money overview/accounts/history/budgets/categories, account/entry/transfer/budget/category editors, Quick Add, Appearance, Schedule/Library foundations, subscription entry point, loading/error/empty states. Schedule, library, subscription settlement and widgets remain intentionally unimplemented domains.

## Screenshot-driven corrections and independent review

The first physical seven-test capture pass succeeded but actual images exposed black inherited dark headings, an inner rectangle from translucent shadowed panels on the Samsung, overly tall action grouping and stale local validation text. One correction batch established explicit content colors, used opaque tonal panels, compacted controls and cleared local validation when editing. A second native pass confirmed readable dark headings and clean panels; all seven UI tests passed again.

The independent Impeccable review inspected 24 named native captures against the approved reference and requested: one-row normal-size Money actions, a compact empty Priorities presentation, per-window system-bar contrast across theme/editor changes, numeric signed opening-balance input, and current product/design persistence. The fix batch implements weighted actions with a large-text wrap fallback; a compact empty-state row; focus/theme-aware Activity/Dialog system-bar policy; decimal keyboard with an explicit negative opening-balance checkbox; and `docs/PRODUCT.md` as a small pointer to existing authority. DESIGN.md and schema-version-2 design.json are updated from actual source/evidence by the independent documenter. Native Kotlin has no HTML/CSS detector verdict; no shipping raster assets were introduced.

The final eight-test native suite passed, including the 1.5× text variant. The first verdict resolved density, numeric input and documentation but still found a dark-Today system-bar contrast issue in actual pixels despite passing flag assertions. The final correction posts policy application after native focus dispatch and the capture waits for the external SystemUI tint transition. The focused Money workflow passed again and the fresh 23:51 dark Today capture shows readable light status/gesture icons. The reviewer scored the remaining finding resolved, retained the other three resolved findings, and returned **ship for the four scored fixes**. That scope is not presented as a new whole-app certification. The independent documenter inspected the final evidence and updated both design records accordingly.

## Executed checks

| Check | Result |
|---|---|
| Debug and test APK builds | Passed |
| Unsigned release APK | Passed |
| JVM unit tests | 23 passed: existing 15 + 4 currency + 4 financial-presentation tests |
| Android lint | 0 errors, 15 advisory warnings (dependency/tooling and one Compose parameter-order advisory) |
| Physical financial/database/migration suite | 11 passed |
| First/second physical UI suites | 7 passed each, including existing foundation/navigation/appearance and Money workflows plus two new editor/capture checks |
| Separate-process persistence test | 1 passed |
| Cold launch and app-PID-only error Logcat | COLD Status ok, process alive; no AndroidRuntime fatal in inspected output; vendor perf_hint/vibrator HAL messages were nonfatal |
| Phase 1 final History/workflow baseline gate | Passed before redesign on the reconnected Samsung |
| Final reviewer-fix UI suite and app-local 1.5× text | 8 passed; normal-size action row, compact empty priorities, numeric keyboard, restored drafts and accessible large-text navigation captured |
| Final focus-order/system-bar correction | Focused Money workflow passed again; actual 23:51 dark Today capture confirmed by independent verdict |

**20 distinct instrumentation tests passed across named invocations**: 11 database/migration, 8 UI and 1 separate-process persistence. Repeated workflow/capture runs are not added to that distinct count. Final production debug/release builds, unit tests and lint passed after the last system-bar code change. A final independent cold launch returned Status ok/COLD, the process stayed alive, and app-scoped inspected errors contained only the same nonfatal vendor messages.

Financial tests cover v1→v2 preservation, balances/opening/income/expenses, transfer edits/deletion, category/history, archive behavior, monthly/category budgets, overspending, date boundaries, input precision/overflow, reactive Today/Money snapshots, paging/filtering and currency correction without touching dollar history. Pure presentation tests cover separate currency totals, archived exclusion, aggregate overflow, truthful category proportions/tail and zero/empty cases.

New native editor tests create an IDR account through the actual form, recreate its unsaved draft, save it once, edit/recreate/save the same identity, and test Keep editing/Discard without an unintended write. An explicit native keyboard capture keeps Save visible. The app-local 1.5× composition test exercises the actual app UI without changing phone font/display/security settings; its final result is tracked separately.

Reports: `app/build/reports/phase-1.5-data.txt`, `phase-1.5-ui-first.txt`, `phase-1.5-ui-final.txt`, `phase-1.5-persistence.txt`, `phase-1.5-startup.txt`, `phase-1.5-review-fixes.txt` and focused `phase-1.5-bar-verdict.txt`. Unit XML is under `app/build/test-results/testDebugUnitTest/`; lint HTML/XML under `app/build/reports/`. Named Phase 1.5 reports retain separate results. The initial baseline gate used the existing runner's Phase 1 UI report name; its passed run is also recorded in the session outputs.

## Device and before/after evidence

Samsung SM-S942B, Android 17/API 37, selected authorized wireless transport `<authorized-device-serial>`. Installs target current Android user 0 with `-r`, preserving app data. The phone locked/disconnected during long implementation/review work; tests stopped at those boundaries and resumed only after the user restored access. No phone timeout/security/display setting or Windows/GPU setting was changed. Tests use only their own Activity keep-screen-on flag.

Before: `docs/screenshots/phase-1/` contains the original basic Material surfaces. The first Phase 1.5 inspection set is preserved under `.impeccable/review/phase-1.5-first/phase-1.5/` as diagnostic evidence. Final/current native captures are under `docs/screenshots/phase-1.5/`:

- `phase-1.5-{light,light-lower,dark,empty,personal,dynamic,quick-add}.png`: Today preview and appearance states.
- `money-overview-{light,dark}.png`, `money-category-spending.png`, `accounts-{light,dark}.png`, `budget-overspent.png`.
- `history-light.png`, `history-filters.png`, `today-personal-{light,dark}.png`.
- `account-validation.png`, `account-editor-keyboard.png`, `account-editor-restored.png`.
- `schedule-dark.png`, `library-dark.png`, `appearance-dark.png`, `quick-add-dark.png`, `cold-restart-money.png`.
- `large-text-today.png`, `large-text-money.png`: passing app-local 1.5× native composition captures.

All existing named Phase 1.5 captures are actual physical app screenshots, not browser renders, generated comps or emulator images. Sample Today is explicitly disclosed; Money screenshots use real persisted disposable test records. No reference-fidelity percentage or whole-app certification is implied.

## Changed files and remaining limits

Production: `ui/Theme.kt`, new `VisualSystem.kt`/`MoneyOverview.kt`, `TodayScreen.kt`, `MoneyScreen.kt`, `MoneyEditors.kt`, `PersonalOsApp.kt`, `MainActivity.kt`; `today/TodayModel.kt` formatter; `data/MoneyDao.kt` defaults/category aggregate, `MoneyQueries.kt` projection; new pure `money/MoneyPresentation.kt`. Tests: new `CurrencyPresentationTest.kt`, `MoneyVisualizationTest.kt`, `Phase15UiTest.kt`; existing `MoneyDatabaseTest.kt`, `MoneyWorkflowTest.kt`, `MoneyPersistenceTest.kt`, `FoundationTest.kt` adapted/extended. Support: additive capture/report arguments in `tools/run-phase1-device.ps1`. Documentation: README, design/product/UX/architecture/data records, retained reference and this handover. No dependency/schema/build-tool migration was introduced.

Limits: no 2× font, TalkBack, RTL/localization, tablet/foldable hardware, older physical APIs, arbitrary wallpaper contrast or performance benchmark certification. The 1.5× variant is app-local Compose density, not a claim about every Samsung system-font option. Long catalogs still use full metadata lists; checked financial sums still have the conservative Phase 1 Long aggregate limit. Dirty tracking is conservative, so editing then reverting a field can still prompt before discard. Date/time pickers, broader translations and a Today currency chooser remain future polish. Release stays unsigned/unoptimized and automatic backup remains disabled.

The Phase 1 pending History/editor-restoration gate and the Phase 1.5 final device/review gate are closed at the executed scope. Recommended next: intentional export/recovery and product localization before relying on irreplaceable records, then subscription settlement as the next domain slice. No extra domain work was included here. No commits or pushes were made; pre-existing uncommitted files remain.

## Exact build and deployment commands

From `C:\Jevon\Code\personal-os`:

```powershell
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug assembleRelease --console plain
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/device.ps1 devices -l
# Substitute a currently authorized physical serial; no emulator.
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial YOUR_PHONE_SERIAL -CapturePrefix phase-1.5 -ScreenshotDirectory docs/screenshots/phase-1.5 -ReportName phase-1.5-review-fixes -TestClass 'com.jevon.personalos.FoundationTest,com.jevon.personalos.VisualReviewTest,com.jevon.personalos.MoneyWorkflowTest,com.jevon.personalos.Phase15UiTest'
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial YOUR_PHONE_SERIAL -DataOnly -SkipInstall -ReportName phase-1.5-data
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial YOUR_PHONE_SERIAL -SkipInstall -CapturePrefix phase-1.5 -ScreenshotDirectory docs/screenshots/phase-1.5 -ReportName phase-1.5-persistence -TestClass com.jevon.personalos.MoneyPersistenceTest
```

The runner installs both APKs with `-r`, refuses locked UI access, records results and pulls only this app's review files. Financial workflow tests create disposable records. Latest APKs: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release-unsigned.apk`. Release has a separate application ID.
