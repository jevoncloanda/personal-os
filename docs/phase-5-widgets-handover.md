# Phase 5 widget handover

Completed 18 November 2026. Phase 5.1 Quick Expense was already committed in `ee33349`.
The subsequent dashboard and renewal work builds on that implementation.

## Implemented behavior

- Quick Expense retains its accepted configurable presets, one-tap recording, custom amount and Undo.
- Budget Tracker, Today Overview and Upcoming Schedule use responsive Jetpack Glance layouts,
  branded headers and light/dark tonal panels. Budget emphasizes the remaining amount and spending
  progress. Widget minimum sizes remain unchanged by subscription integration.
- Today selects the next active, unpaid subscription forecast from today through seven days ahead,
  inclusive. It shows the name, currency, amount and renewal date when the layout has room. Existing
  budget and event/reminder priorities control which secondary sections fit.
- Upcoming Schedule merges events, open reminders and unpaid subscription forecasts within its
  existing 30-day window. Subscription rows include a distinct label, date and amount/currency where
  space permits. Equal timestamps use stable domain and record-ID ordering.
- Both renewal actions open the existing Subscriptions destination and selected subscription.
  Displaying or tapping a forecast creates no payment or Money entry.
- Renewal dates come from `scheduleRange` and the existing subscription recurrence calculations.
  Recorded installments and inactive subscriptions are excluded by those domain rules. Each
  displayed amount retains its own currency.
- Existing DashboardRefresh observes subscription and payment tables alongside dashboard sources.
  It retains unique queued refresh work, the existing periodic worker, midnight scheduling and
  time/timezone/boot/package-change handling.

## Code entry points

- `app/src/main/java/com/jevon/personalos/widget/DashboardData.kt` owns widget read projections,
  renewal selection and chronological merging.
- `app/src/main/java/com/jevon/personalos/widget/DashboardWidgets.kt` owns responsive presentation
  and widget actions.
- `app/src/main/java/com/jevon/personalos/widget/DashboardRefresh.kt` owns refresh scheduling.
- `MainActivity.kt` and `ui/PersonalOsApp.kt` route widget destinations through existing navigation.
- Widget provider metadata lives under `app/src/main/res/xml`. Colors and labels use the existing
  `values/widget.xml` and `values-night/widget.xml` resources.

## Verification

The Phase 5.3 debug build and four focused `DashboardRenewalTest` tests passed. The tests cover
seven-day selection, stable chronological merging, separate currencies, 30-day bounds,
deduplication, month-end forecast dates, recorded installments and inactive subscriptions.
The existing `DashboardDataTest` and opt-in `DashboardWidgetSmokeTest` remain available.
Broad JVM, instrumentation, lint and release suites were not repeated for this device handover.

The verified debug APK was installed with data-preserving replacement on Samsung SM-S942B.
At the launcher's existing sizes, Today was 2×4 and Upcoming Schedule was 2×3. Temporary fixtures
provided an unpaid USD 12.99 renewal due 19 November, an event on 20 November and a reminder on
21 November. Today displayed the renewal below its event summary. Upcoming displayed all three
in chronological order. Both renewal taps opened the selected Subscriptions detail. Names, dates,
labels and amounts were readable without clipping. Saving the temporary subscription through the
normal editor triggered automatic widget refresh.

Temporary fixtures were inserted using an ignored local debug verification utility. Cleanup
deleted only the three unique fixture IDs. Before/after SHA-256 comparison of the canonical
logical database rows and the settings/Quick Expense preference files passed exactly. No payments
or financial entries were created. The device utility was removed, the widgets were refreshed
after cleanup and the screen timeout remained 120000 milliseconds.

Device screenshots, logs, local fixture tooling and build outputs remain ignored and are excluded
from the commit. This verification covers the current dark-mode launcher layouts. Light mode,
other font scales and a comprehensive resize matrix were outside this focused device pass.


## Stitch widget visual update — 18 November 2026

Today now leads with a large day number and the existing selected commitment, with budget and
renewal context secondary. Upcoming uses an open date/time rail with labeled indigo events,
amber reminders and green renewal forecasts. Budget leads with remaining amount, a 12dp
spending track and responsive spent/limit context. Repeated branding and nested content panels
were removed. Existing actual-dp/font-scale sizing, data projections, actions, refresh paths and
provider minimum sizes are unchanged. Quick Expense source and existing colors are unchanged.

The supplied Stitch screen.png and code.html governed composition and colors; static opaque
surfaces and platform widget type replace CSS blur, shadows, web fonts and animation.
One assembleDebug passed. Samsung SM-S942B checks covered 201×234dp Today and 429×234dp
Schedule/Budget, including populated and empty states. Temporary visual-check records supplied
a commitment, reminder, renewal and unspent budget; the original database/WAL/SHM were restored
with exact SHA-256 equality afterward. The initial signing mismatch required user-authorized
uninstall/reinstall; all 19 original data/preference files were restored and verified before
visual verification. Widget placements were recreated. No focused data tests were needed because
selection/calculation code was unchanged. Tall sizes, light mode, larger font scales and
overspending rendering were not device-verified in this bounded pass. Local evidence is ignored.
