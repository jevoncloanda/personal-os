# Poros Android redesign

## Implementation

The existing native Compose application now uses Poros branding and the approved Obsidian & Electric Indigo identity. Today, Money overview/history/budgets/accounts/categories, subscriptions, Schedule, Library/detail, Settings, backup and notification settings share the same light/dark components. The shell has five destinations, a compact navigation bar, an expanded navigation rail, and a global Quick Add sheet prioritizing expense, reminder and event.

Editors retain native pickers, validation, draft restoration and discard confirmation. Money input is amount-first with a decimal keyboard and one persistent Save action. Transactions open a read-only detail before explicit editing; subscription-linked expenses route to their payment history. Library rows show real artwork/progress and offer existing progress controls. Schedule adds a week strip and presentation filters without changing recurrence or completion rules.

The finish-review batch grouped Today’s financial controls, shortened empty states, gave Expense primary emphasis, moved Subscriptions before accounts/history, compacted Library filters and the Settings theme selector with larger-text fallbacks, consolidated Schedule controls, removed the redundant event label and hardened dialog-owned system-bar appearance.

## Reference decisions

Authority: `design/poros-stitch/01_DARK_PRIMARY`, then `02_LIGHT_REFERENCE`, then design-system prose. Conflicting palette variations were resolved in favor of the primary dark HTML/screens: canvas `#0D0E16`, panel `#1E1F28`, foreground `#E3E1ED`, secondary text `#C8C4D7`, primary `#C7BFFF`, primary container `#8D7FFF`, positive `#66DBB3`. Light uses `#FAF8FF`, white panels and `#4648D4` primary. Bundled Inter and Lucide remain; cards use 16dp corners, controls 12dp, and opaque tonal surfaces replace the old sky gradients and frosted panels.

`PorosBackground`, `PorosCard`, `SectionTitle`, `LedgerRow`, `FinancialProgress`, `EditorFrame`, `AmountField`, `Field`, `Choice` and native date/time wrappers provide the shared implementation. System/light/dark preferences and optional wallpaper supporting accents remain persisted through the existing DataStore.

The supplied orbit/core concept is implemented as a vector, with mask-safe adaptive launcher foreground, themed monochrome icon and Android 12+ splash icon. Launcher label, in-app branding, notification wording and suggested export filename use Poros. Application/package IDs, database, entities and backup identifiers are unchanged.

## Deliberate adaptations

Stitch sample records, names, balances and artwork are replaced by real projections and compact empty states. Native Material pickers, menus, dialogs and insets replace fixed HTML dimensions. Existing currency selection, explicit Today budget and fallback behavior take priority over mock totals. Category budgets and currencies are never added together. Payment history remains distinct from unpaid forecasts. No invented profile, trends, encryption, sync, QR tools, Tasks, widgets or metadata APIs were added.

## Verification — 16 November 2026

- Fresh final Gradle pass: 72 JVM tests passed; debug APK, instrumentation APK and release APK built; `lintDebug` passed with 0 errors and 22 warnings (dependency/tooling recommendations, existing resource/API/style warnings). No dependency upgrades were made.
- Samsung SM-S942B over Wi-Fi: installed with `adb install -r`, preserving app data. 54 isolated database/backup/recurrence/link/settings/Today tests and read-only personal-data audit passed. The audit checked foreign keys and a complete in-memory backup round trip. UI navigation checks also compare complete personal snapshots before/after.
- Production notification PendingIntent routing test attempted but stopped at its precondition: notifications are disabled for the debug app. No permission was reset or forced. Routing code and the cold-start graph-readiness guard remain intact; a fresh routing pass needs enabled notifications.
- 12 unique native UI tests passed (66 unique passing instrumentation tests including the 54 data checks): amount validation/category retention, read-only detail/edit/deletion confirmation, linked-payment routing, Settings persistence, backup navigation, all Quick Add routes, unsaved draft recreation/discard, calendar/event/reminder workflows, Library progress/persistence, Today budget selection/currency and subscription payment/correction/undo. The old picker text selector was updated to its accessible icon description and passed on rerun. The personal-writing legacy `MoneyWorkflowTest` was not run.
- Five interaction/Settings/shell checks also passed at font scale 1.3; the original device scale 0.9 was read first and restored in `finally`. Fresh captures cover the main screens, empty/populated Today, both themes, wallpaper accents, editor/decimal keyboard, Quick Add, Library detail and subscriptions. Evidence: `screenshots/poros/` and `screenshots/poros-large/` (local screenshots are ignored by Git). Settled recaptures replaced initial loading/saving frames.
- After the review batch, the JVM/build/lint pass succeeded again. The affected native UI checks passed again at normal and 1.3 text scales, including a focused-dialog light-status-bar assertion. A Library screenshot’s keyboard-dismissal timing issue was corrected in its isolated test before the final pass.
- The final Today refinement moves budget selection into its header as a labeled 48dp icon target alongside Money, with 4dp internal gaps and no standalone chooser row. Today selection/overspending and shell checks passed again at both text sizes; the original 0.9 scale was restored.
- Independent finish review: **ship for the scored fixes**. All seven material findings were scored resolved after the batched corrections and final Today refinement. This is a verdict on those findings, not an exhaustive whole-app audit. No TalkBack, tablet, emulator, launcher-mask device matrix or end-to-end alarm timing result is claimed. No remaining visual fixes from that review are open.

All implementation changes remain uncommitted. Original Stitch files and the unrelated untracked notification-device harness are preserved.

## Entry workflow refinement — 16 November 2026

Reminder and event editors now use prominent title inputs, compact native date/time rows and one Save action. Reminder date removal and optional time are explicit. New reminders keep the selected-date, date-only default. Recurrence, notes, event location and time-zone overrides move into More options, with summaries of active values. Existing notification settings are summarized there and link to the native timing editor after draft changes are saved. Collapsed values retain their saved state, and validation errors expand the section. Native pickers retain alternate date/time input.

Expense entry shows amount, account and category first. Description, date/time overrides and transaction kind are under More options. Successful new entries remember valid account/category choices per transaction kind in the current saved app session. A sole active account can be preselected. Category selection stays optional under existing financial rules. Archived or incompatible choices are rejected before prefill. Currency stays visible with the selected account.

Today uses the monthly financial card as its focal point, with open supporting sections and smaller greeting/empty states. Money has flatter month/action sections, a bounded budget preview, denser recent rows and wrapping section tabs that expose Categories fully. Shared changes are in FormComponents, EditorFrame, Field, Choice and the native date/time wrappers. Schedule editor composition is extracted into ScheduleEditors.

Fresh checks for this refinement passed 16 focused JVM tests and 37 unique instrumentation tests after correcting one invalid test fixture. Seven key checks also passed at font scale 1.3. Targeted confirmations passed for the notification shortcut, its save-first guard, precise recurrence summaries, and removing/restoring the date of a recurring draft. The Samsung SM-S942B received a data-preserving install, and complete personal snapshots matched before/after navigation. Its original 0.9 font scale was restored. Final debug build and lint passed with zero errors and 22 warnings.

Before captures are in `screenshots/poros-refinement-before/`. Fresh dark/light, keyboard and larger-text captures are in `screenshots/poros-refinement/` and `screenshots/poros-refinement-large/`. Visual inspection confirmed compact editors, visible currency and Save controls, complete Money tab labels and open dashboard sections. These local screenshot folders are ignored by Git.


## Final verification checkpoint — 17 November 2026

Paused at the user’s request before Samsung installation or instrumentation. The consolidated run completed successfully after fixing a Compose receiver-scope compilation error in LibraryCover.kt. Debug, unsigned release and instrumentation APKs built. Fresh JVM results: 72 tests, 0 failures, 0 errors, 0 skipped. Android lint: 0 errors, 20 warnings. `git diff --check` passed.

The connected SM-S942B’s installed debug APK and freshly built APK have the same SHA-256 signing certificate, beginning `24496bd8`. No installation, uninstall, data clearing, permission change, theme change or font-scale change was performed. Notifications are disabled (`POST_NOTIFICATION: ignore`), so production notification-routing verification remains gated.

Next work is isolated instrumentation, updating existing UI test selectors for the refined controls, followed by actual dark/light and larger-text captures and comparison. Existing disposable SubscriptionReviewActivity, ScheduleReviewActivity and LibraryReviewActivity hosts provide isolated records. Legacy MoneyWorkflowTest and MoneyPersistenceTest use personal-storage fixtures and should not be used for this non-destructive verification. No fresh instrumentation or visual-verification result is claimed at this checkpoint. All changes remain uncommitted.


## Final verification completed — 17 November 2026

This supersedes the paused checkpoint above. The consolidated desktop run passed debug, unsigned release and instrumentation builds, 72 JVM tests and lint with 0 errors and 20 warnings. The focused instrumentation APK rebuild passed after test maintenance. The LibraryCover receiver-scope compilation correction was the only production correction in this verification phase. Obsolete UI selectors, the reminder save/reopen fixture and lazy-list scrolling in the larger-text subscription test were corrected in existing tests.

Samsung SM-S942B received a data-preserving debug update. The separately signed test package was reinstalled with explicit user authorization. There are 75 unique passing Android checks across the focused runs, comprising 56 data/backup/migration/settings/audit checks and 19 normal-size UI checks. All nine representative larger-text checks passed across affected reruns at device scale 1.3, including one app-local 1.5 check. The final subscription retry passed after scrolling its lazy list to the requested control. Device scale was restored and independently confirmed as 0.9. Theme preferences were restored by the existing fixture cleanup. Personal snapshots and the read-only FK/backup round-trip audit passed. Disposable review records were isolated from personal storage.

Actual settled Samsung rendering was compared against the approved Stitch package for Today, Money overview/history/detail/budgets/accounts/categories, Expense/Income/Transfer, Reminder/Event editors, subscriptions and payment history, Library artwork/fallback/detail, Schedule agenda/month, Settings/backup and Quick Add. Populated and empty states, dark/light appearance, keyboard/pickers, long titles and larger text were reviewed. No additional material visual defect or UI-induced functional regression was found. The approved raster logo was also checked in Samsung app information. Selected local evidence is in `screenshots/poros-final/` (25 captures) and `screenshots/poros-final-large/` (8 captures), ignored by Git.

Notifications remain disabled, so production notification routing is unverified. TalkBack, tablet layouts, the launcher-mask device matrix and end-to-end alarm timing were outside the executed checks. The release artifact is unsigned. All changes remain uncommitted, with no push. The unrelated notification harness is preserved.
