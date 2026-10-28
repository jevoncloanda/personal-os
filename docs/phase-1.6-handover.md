# Phase 1.6 — premium UI fidelity and design-system refinement

Date: 25 October 2026. **Complete at the documented Samsung phone scope; final debug build installed.** This continues the approved Phase 1.5 sky-blue native design. Financial storage, schema, accounting and formatter implementations are unchanged. No commits or pushes.

## Summary and baseline audit

Inspected the approved three-screen finance reference and actual Phase 1.5 Samsung captures for Money light/dark, Today, History, the account keyboard editor and Quick Add. The reference has more compact grotesk typography, lighter outline icons, quieter supporting controls and tighter information groupings. The incumbent platform sans, strong chip borders, oversized pills, detached period selector and vertically stacked spending card made Money feel heavier and pushed budgets down.

The refinement keeps the approved sky/mist palette, opaque tonal frost, native Material interaction, priorities-first Today hierarchy and existing working workflows. It does not copy iOS chrome, create financial claims or add decorative charts.

## Typography and licensing

Selected **Inter** over Plus Jakarta Sans and Manrope for its compact, open grotesk forms and financial numeral clarity. This is a visual assessment; the reference's actual font is unidentified. See [font provenance and full rationale](design/fonts/README.md) and [OFL license](design/fonts/Inter-OFL.txt).

One unmodified official Google Fonts variable TTF is bundled, with explicit Android weight instances 400/500/600 and optical size 14sp. The SIL OFL 1.1 notice is also packaged in the APK. All 15 Material roles now have explicit family, size, line height, weight and tracking. Balance is 36/42sp semibold with tabular figures; greeting/monthly spending 28/34; financial summaries 24/30 and 22/28; screen titles 20/26; section titles 16/22; ledger titles 14/20; body 16/24, 14/20, 12/17; labels 14/20, 12/16, 11/16. Headline tracking stays between -0.7 and -0.4sp; balance uses -0.9sp. Comparable financial roles use `tnum`.

## Icons, buttons and surfaces

- A 23-icon Lucide outline subset uses one 24-unit grid, rounded caps/joins and a 1.75-unit stroke. Paths render as native Compose vectors. The full ISC/Feather notices accompany the APK; [source and transformation details](design/icon-provenance.md) are recorded. The unused extended Material icon dependency was removed. Native state glyphs for switches, radios and checkboxes stay Material.
- Money actions use smaller type, 20dp corners, tighter vertical padding and distinct blue content on tonal surfaces, retaining minimum 48dp targets and larger-text wrapping.
- Section chips become native scrolling underline tabs, reducing competing pill outlines while retaining all five destinations.
- Form choices use labeled, start-aligned 12dp outlined controls with consistent disclosure chevrons. Save uses the shared type and softer 20dp geometry. Input semantics, keyboard types, validation, draft identity/restoration and dirty-state handling are preserved.
- Shared panels retain opaque fills and fine borders; native shadow decreases from 2dp to 0.5dp. This preserves Samsung's translucency workaround and avoids the previous shadow rectangle artifact.
- Icon badges become 36dp with 20dp vectors. They are decorative/supporting containers, not reduced interactive targets.

## Layout and navigation

Money's header loses unnecessary vertical padding, with a quieter balance label and compact currency choice. The selected month now belongs inside the monthly panel, above a wrapping spending/income comparison. The scope note remains explicit: recorded expenses only; transfers and forecasts excluded. Budgets, truthful category shares, accounts and recent history retain their order and calculations.

Today keeps date/greeting, priorities, compact monthly budget, later plans, payment forecasts and leisure. Greeting spacing and type are tighter; populated rows, empty states and sections inherit the same type/icon system. No new decorative cards were added.

Compact native bottom navigation uses a 72dp content row plus Android gesture insets, 22dp icons and a 48dp circular Quick Add with restrained 2dp elevation. Active states use stable blue tonal indicators. App-local font scale above 1.3 retains a separate labeled Quick Add and 80dp navigation content row. Expanded screens retain the existing native rail.

## Atmosphere and motion decision

Retained the simple sky-to-mist gradient in light mode and deliberate navy gradient in dark mode. The reference's atmosphere is primarily tonal; extra cloud illustrations or speckles would add visual noise to this bounded refinement. No new runtime blur, large raster backdrop, constant animation or GPU effect was introduced. Native ripple, selection, sheet and navigation feedback remain the interaction language. Wallpaper personalization continues to affect supporting roles without replacing the brand blue.

## Evidence and verification

Executed source gates: debug APK, test APK, unsigned release APK, 23 JVM tests and Android lint passed. The final build completed successfully after the screenshot-driven correction. Lint has zero errors and 17 advisories: dependency/version suggestions (12), one modifier-parameter convention, backup extraction guidance, Kapt guidance and two deliberately packaged license resources flagged unused. The initial compile exposed stale icon imports; these were corrected before the successful gates.

Physical verification used Samsung SM-S942B, Android 17/API 37, current Android user 0, through authorized wireless ADB. No emulator, personal-data reset or phone setting/security change was used. A dropped transport was recovered from its advertised endpoint; a later locked screen correctly prevented the runner until the user unlocked it.

| Gate | Actual result | Report |
| --- | --- | --- |
| JVM regression | 23 passed: Today 10, input 5, currency 4, visualization 4 | `app/build/test-results/testDebugUnitTest/` |
| Initial physical UI | 9 passed: Foundation 3, visual review 1, Money workflow 1, Phase 1.5 UI 3, Phase 1.6 capture 1 | `app/build/reports/phase-1.6-ui-initial.txt` |
| Final physical recapture | 4 passed: visual states, dark/shared system, 1.5x text, focused final capture | `app/build/reports/phase-1.6-ui-final.txt` |
| Financial data and migration | 11 passed: isolated Money database 10, migration 1 | `app/build/reports/phase-1.6-data.txt` |
| Separate-process persistence | 1 passed | `app/build/reports/phase-1.6-persistence.txt` |
| Final cold launch | Status ok, COLD, 389ms total, app process alive | `app/build/reports/phase-1.6-startup.txt` |
| Font/license distribution | Bundled TTF and both full notices byte-matched to optimized release APK entries | `app/build/reports/phase-1.6-packaged-licenses.txt` |

There are **21 distinct physical instrumentation tests**, with four repeated for the final capture (25 passing executions). The workflow proves expense/income/transfer, correction/deletion, currency-specific balances, budget overspending, reactive Today, discard handling and persistence. The editor regression proves numeric keyboard, IDR defaults, validation and recreation/restoration. Final recapture avoids additional financial writes.

The cold-launch PID-scoped error log contains only nonfatal vendor `perf_hint` session-size and `VibratorInfo` HAL-profile messages. No application exception or fatal crash was observed during these runs. This is smoke-test evidence, not a performance benchmark.

The existing workflow harness now chooses a deterministic unused two-decimal ISO currency after its preferred list is exhausted. It retains uniquely named disposable records and does not edit existing personal records. `Phase16UiTest` captures the refined system and opens/cancels expense and transfer drafts without saving financial records.

## Before/after evidence

Baseline: [Money light](screenshots/phase-1.5/money-overview-light.png), [Money dark](screenshots/phase-1.5/money-overview-dark.png), [Today dark](screenshots/phase-1.5/today-personal-dark.png), [History](screenshots/phase-1.5/history-light.png), [account editor](screenshots/phase-1.5/account-editor-keyboard.png), [Quick Add](screenshots/phase-1.5/quick-add-dark.png).

| Surface | Phase 1.6 actual Samsung evidence |
| --- | --- |
| Today personal | [Light](screenshots/phase-1.6/today-personal-light.png), [dark](screenshots/phase-1.6/today-personal-dark.png) |
| Today populated preview / lower content / empty | [Priorities](screenshots/phase-1.6/phase-1.6-light.png), [personal-domain sections](screenshots/phase-1.6/phase-1.6-light-lower.png), [empty](screenshots/phase-1.6/phase-1.6-empty.png) — in-memory sample data is explicitly labeled |
| Money overview | [Light](screenshots/phase-1.6/money-overview-light.png), [dark](screenshots/phase-1.6/money-overview-dark.png), [scrolled ledger](screenshots/phase-1.6/money-overview-scrolled.png) |
| History / filters / accounts | [History](screenshots/phase-1.6/history-light.png), [filters](screenshots/phase-1.6/history-filters.png), [accounts](screenshots/phase-1.6/accounts-light.png) |
| Editors | [Expense](screenshots/phase-1.6/expense-editor.png), [transfer](screenshots/phase-1.6/transfer-editor.png), [account numeric keyboard](screenshots/phase-1.6/account-editor-keyboard.png), [restored account draft](screenshots/phase-1.6/account-editor-restored.png), [validation](screenshots/phase-1.6/account-validation.png) |
| Quick Add | [Light](screenshots/phase-1.6/quick-add-light.png), [dark](screenshots/phase-1.6/quick-add-dark.png) |
| Appearance | [Light](screenshots/phase-1.6/appearance-light.png), [dark](screenshots/phase-1.6/appearance-dark.png) |
| 1.5x app-local text | [Today](screenshots/phase-1.6/large-text-today.png), [Money](screenshots/phase-1.6/large-text-money.png) |
| Foundation screens | [Schedule](screenshots/phase-1.6/schedule-dark.png), [Library](screenshots/phase-1.6/library-dark.png) |

Every capture was opened in a bounded two-round inspection, using full-size key views and an overview contact sheet for capture validity. The first round showed a malformed Library outline: Lucide's SVG packs arc flags in a form Compose's path parser interpreted differently. Explicit flag separators fix the native vector; the final Library, navigation and Quick Add captures confirm the intended open book. The first scrolled capture had only brought a lower heading into partial view; a real swipe now captures the overview ledger. Capture helpers also wait for external SystemUI tint settling. Final light/dark system icons, financial figures, controls and 1.5x wrapping were visually inspected separately from test assertions.

Money's budget empty panel now fits wholly above navigation in the normal first viewport; it was clipped in the baseline. Today retains its priorities-first hierarchy and clear personal-domain sections. Reference differences remain deliberate: native labeled Android navigation, true recorded amounts and compact monthly comparison instead of invented trend graphics. Extra cloud art was not needed.

Screenshots show retained disposable `P1 test ...` and `P15 restore ...` records from regression runs. The change in the IDR balance between phases includes the new disposable restoration-test account; it is not a formatting conversion. Today's existing A$ budget follows its established currency-selection rule and is not mislabeled IDR. No existing personal record was edited, deleted or relabeled for appearance.

Independent review is recorded in [the review record](design/phase-1.6-review.md). The full rendered review found no material rendered defect at the supplied phone scope and rated typography, material and ground as Match. Its one material fix was stale design documentation. A separate documenter updated [DESIGN.md](DESIGN.md) and `.impeccable/design.json` from current source; all 15 type roles, native geometry, licensing, evidence paths and sidecar syntax were checked. The same reviewer then scored that documentation fix **resolved**, with final disposition **ship** at that fix's scope. This does not expand the earlier rendered scope or certify every app behavior.

## Limits and next phase

No claim of pixel-perfect reference reproduction. Phone screenshot evidence is the visual gate; tablet/foldable, 2x text, RTL, TalkBack and performance tracing remain outside the executed scope unless explicitly added below. Existing explicit ISO date/time inputs, conservative dirty tracking and Today currency-selection behavior remain unchanged. Release remains unsigned; this phase does not implement backup/export, new business domains or localization.

The Impeccable context tool also reported the existing PRODUCT.md pointer as a legacy schema. It was preserved because schema repair is outside this visual refinement; a separately requested `init` can refresh that record. Existing foundation-screen status copy is retained and is not canonized as a reusable eyebrow style.

Recommended next: intentional export/recovery and localization/input polish before relying on irreplaceable records. Subscription settlement remains the next separate domain slice.

## Build and deployment

```powershell
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug assembleRelease --console plain
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/device.ps1 devices -l
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial YOUR_PHONE_SERIAL -CapturePrefix phase-1.6 -ScreenshotDirectory docs/screenshots/phase-1.6 -ReportName phase-1.6-ui -TestClass com.jevon.personalos.FoundationTest,com.jevon.personalos.VisualReviewTest,com.jevon.personalos.MoneyWorkflowTest,com.jevon.personalos.Phase15UiTest,com.jevon.personalos.Phase16UiTest
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial YOUR_PHONE_SERIAL -DataOnly -SkipInstall -ReportName phase-1.6-data
# Final nonmutating recapture subset after the initial regression run:
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial YOUR_PHONE_SERIAL -CapturePrefix phase-1.6 -ScreenshotDirectory docs/screenshots/phase-1.6 -ReportName phase-1.6-ui-final -TestClass 'com.jevon.personalos.VisualReviewTest,com.jevon.personalos.Phase15UiTest#captureSharedSystemAndDarkMoney,com.jevon.personalos.Phase15UiTest#appLocalLargeTextKeepsNavigationAndQuickAddAccessible,com.jevon.personalos.Phase16UiTest'
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-phase1-device.ps1 -DeviceSerial YOUR_PHONE_SERIAL -SkipInstall -CapturePrefix phase-1.6 -ScreenshotDirectory docs/screenshots/phase-1.6 -ReportName phase-1.6-persistence -TestClass com.jevon.personalos.MoneyPersistenceTest
```

The device runner installs with `-r`, scopes the current Android user and refuses locked UI runs. It never clears app data. Latest artifacts: `app/build/outputs/apk/debug/app-debug.apk`, `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, and `app/build/outputs/apk/release/app-release-unsigned.apk`.
