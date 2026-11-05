# Architecture

Phase 4 adds one shared Library DAO/model/ViewModel/editor for movies, series, games and books. LibraryDao extends ScheduleDao in the existing DAO chain; bounded Room queries return 60 collection rows plus one lookahead, four highlights per status and three recently updated active items for Today. Search/filter/sort and selection live in SavedStateHandle; editor drafts survive Activity recreation. No network client or backend is required. Room is v5 with an explicit 4→5 migration. Backup format 4 reads formats 1–3 and includes portable Library information while excluding private cover images. See [Phase 4 handover](phase-4-handover.md) for semantics, provider decisions and actual proof.

## Phase 3 scheduling and shared alerts

`ScheduleDao` extends the existing DAO chain. Events/reminders keep their original identities and timestamp fields, with notes, reminder zone/date-only semantics and schedule revisions added by Room 3→4. `reminder_completions` uniquely records reminder/revision/nominal-date completion. Schedule edits preserve old history while starting a new revision; title/notes changes preserve occurrence identity. One-off completion stays on the reminder. Legacy repeating-template completion means its first occurrence, not every future occurrence.

`ScheduleModel` owns direct calendar seeking, bounded event/reminder projections and explicit DST gap/overlap behavior. Subscriptions delegate calendar seeking to the same helper and Schedule calls existing `upcomingRenewals`. `ScheduleViewModel` reads coherent Room ranges; dated, overdue and undated reminder candidates are bounded separately so old completed history cannot hide current work. Today carries exact occurrence keys and observes completion invalidation. Schedule offers agenda and month selection; editors and notification settings reuse the existing native design system.

One shared `alert_preferences` table has exactly one event/reminder/subscription owner, enforced by DAO/backup validation and real foreign keys. `LocalAlerts` reconciles next alerts against Room, using a private nonportable `alert_state` ledger and a single stable inexact AlarmManager wakeup. Explicit immutable broadcast/activity PendingIntents carry stable identities. Delivery revalidates current owners, revision, completion/payment and preferences. Runtime permission and channel checks precede posting. Source invalidation, app start/resume, boot/package replacement, time/zone broadcasts and restore drive reconciliation; no continuously running service or exact-alarm permission is used. Restore clears derived runtime state atomically and invalidation rebuilds it. There is no transaction spanning SQLite and Android NotificationManager; stable tags/onlyAlertOnce reduce replay effects but are not a universal exactly-once delivery guarantee.

Backup format 3/schema mapping 4 includes completion history and notification preferences, with explicit readers for formats 1 and 2. OS alarm identities, notification receipts and device permission state are excluded. Old all-day/date-only calendar meaning derives in the stored zone; timed recurrence retains wall-clock times in that zone. Full policies and actual verification are recorded in [Phase 3 handover](phase-3-handover.md). Prior phase sections below remain historical where superseded.

## Phase 2 subscriptions

`SubscriptionDao` extends `MoneyDao` and is inherited by `PersonalDao`. It owns subscription validation, occurrence uniqueness, expense/payment-link transactions, correction and undo. Recording delegates to the ordinary ledger writer inside the same Room transaction; corrections temporarily detach/reinsert the same link within that transaction. Direct Money edit/delete stays blocked for linked expenses, with a route hint. No shadow balance or automatic debit exists.

Room v3 adds description/notes and nullable account/category foreign keys through explicit 2→3 ALTER statements; 1→2 remains registered. Account currency changes are also blocked while referenced by a subscription. Archiving preserves references but new payments require active accounts/categories. `SubscriptionModel` owns anchored dates, bounded forecast generation and exact rational cost equivalents. `SubscriptionViewModel` observes subscription/payment/ledger/catalog invalidation. Today observes payment links too and shows at most four unpaid approaching/overdue renewals. Navigation keeps subscriptions under Money; Quick Add opens its real editor.

Backup format 2/schema mapping 3 includes every added field and validates references. Format 1/schema mapping 2 remains readable with empty description/notes and null defaults. Expanded subscription text participates in the snapshot preflight. Restore clears subscriptions before their account/category parents, retaining FK enforcement and the single replacement transaction. Phase 1.7 sections below describe the prior version; current compatibility and verification are in [Phase 2 handover](phase-2-handover.md).

## Phase 1.7 backup and recovery

`BackupStore` owns the single-Room consistency boundary. A bounded `BackupDao` snapshot reads all nine persisted tables in one transaction. Restore validates the full historical dataset before clearing anything, then deletes children before parents and inserts parents before children in one `withTransaction`, with foreign keys enabled. Failed inserts/cancellation roll back the whole replacement. Room invalidation refreshes existing Money and Today projections after commit; no process restart is required. Schema remains v2 with the existing nondestructive 1→2 migration.

`BackupCodec` explicitly maps the version-1 application-owned JSON contract, independently of Room file paths and entity reflection. `BackupJson` uses Gson's strict streaming reader with document/depth/node/text/record limits and an explicit canonical writer. SHA-256 covers the canonical envelope excluding its integrity field. This detects accidental changes but authenticates no author and encrypts nothing. `BackupData` validates the complete historical graph and exact BigInteger aggregate/balance bounds, allowing archived references and existing legacy budget intervals. Current editor-only selection restrictions are not imposed on historical restore.

`BackupViewModel` runs provider/database/codec work off the main thread. SAF create/open contracts in `BackupScreen` supply transient document access without broad permissions. Preview is held in memory; process death requires reselecting/revalidating and confirming again. Export cleanup is owned before cancellable IO dispatch; cleanup and terminal feedback have a noncancelable boundary. If a provider refuses deletion, the UI explicitly reports possible incomplete output. The confirmed database commit is noncancelable from the UI; process interruption still has SQLite's atomic old-or-new outcome. Cancellation of reading/export remains available. No cross-store transaction is claimed: appearance DataStore preferences are deliberately excluded and unchanged.

Debug-only nonexported `BackupReviewActivity` and `BackupFailureProvider` use disposable in-memory data/private cache and do not access `PersonalOsApplication.database`. They support actual SAF round-trip and provider-failure tests without replacing live personal records. Production entry is Settings → Backup & restore. See [Phase 1.7 handover](phase-1.7-handover.md) for the exact format, limits and executed evidence. Automatic Android backup remains disabled.

## Phase 1.6 visual-system refinement

Theme.kt now owns a complete Inter-based Material type scale, loading one bundled variable TTF with explicit weight/optical-size instances. AppIcons.kt owns the small licensed native-vector outline subset; no icon runtime dependency or network font loading is introduced. The unused extended Material icon dependency was removed. VisualSystem.kt retains the shared tone/border approach and focused-window system-bar policy, with quieter panel elevation and smaller decorative badges. Money's composition, tabs, native choices and navigation proportions consume these shared roles. No persistence, schema, financial command, calculation or formatter changed in this phase.

## Phase 1.5 presentation extension

No financial schema/write invariant changed. `MoneyPresentation.kt` owns pure currency-separated active balance totals and category chart slices. Presentation-only balance aggregation uses BigInteger so multiple valid Long account balances cannot wrap when combined; storage stays Long minor units. A month-bounded SQL GROUP BY supplies actual expense-category totals in the same transactional Money snapshot. The chart uses all selected-currency spending as its denominator, with a tail grouped as Other categories, and does not fabricate a trend line.

`VisualSystem.kt` supplies the shared atmosphere, frosted-looking native panels, icon badges, adaptive ledger rows and progress treatment. Native Material components retain interaction behavior. Static gradients and alpha surfaces approximate frosting without backdrop blur or continuous GPU effects. Theme secondary roles can follow wallpaper; blue primary and atmosphere stay fixed. Formatting remains centralized in `TodayModel.kt`, with currency-aware default locales and explicit locale overrides; no stored record is relabeled or converted.

## Phase 1 financial ownership

`PersonalDao` inherits the concrete Money operations in `MoneyDao`. All account/category/entry/budget editor writes go through transactional validation there. A transfer remains one row. `MoneyViewModel` owns filters, save/error state and commands; `MoneyScreen` renders overview/history/accounts/budgets/categories with focused full-screen native editors. SavedStateHandle retains month and filters; saveable draft fields survive Activity recreation. Cancel/Back dismisses a draft without a write.

`MoneyQueries.kt` uses Room invalidation flows and `withTransaction` to read each complete financial snapshot consistently. SQL computes balances, month totals and budget spending; no ledger table is collected into Compose. History is month-bounded and fetched 51 rows at a time (50 visible plus next-page detection). Account/category catalogs remain full metadata lists. Today reads one exact-calendar-month overall budget, bounded schedule/reminder/forecast/library lists and its expense sum in one transaction. Its clock rebuilds the date bounds every 30 seconds. Nonfinancial Phase 0 observation methods remain for compatibility/tests, but the personal Today path no longer combines unrelated full-table flows.

Money operations deliberately retain a checked lifetime currency/kind aggregate and checked per-account inflow/outflow sums before committing. This conservative signed-64-bit aggregate limit prevents SQLite integer overflow in later projections. It scans financial aggregates on writes; very large ledgers should replace these integrity scans with tested incremental totals before claiming high-volume performance. No benchmark or background widget optimization is claimed.

Room is version 2 with explicit `MIGRATION_1_2`; both exported schemas are retained. The sections below describe the original foundation where noted; the Phase 1 handover records current executed checks.

## Foundation shape

One Gradle app module, organized by responsibility. `MainActivity` enables edge-to-edge and creates the Compose tree. `PersonalOsApplication` lazily owns one Room database and one DataStore. `TodayViewModel` owns lifecycle-aware state, current time, debug mode and retry. `TodayModel` contains pure aggregation/formatting rules. Composables render state and emit navigation/settings actions.

```text
MainActivity -> PersonalOsTheme -> PersonalOsApp -> TodayScreen
                                     |
                              TodayViewModel
                           /         |        \
                  PersonalDao   SettingsStore   debug DemoCatalog
                       |              |           (memory only)
                     Room          DataStore
```

The graph uses simple application ownership. Hilt is unnecessary for two local dependencies; add it when real composition complexity warrants it. There is no repository interface with one implementation, generic CRUD framework, domain service layer, or speculative multi-module build. Kapt generates Room code; its maintenance-mode tradeoff is acceptable for this bounded foundation, with KSP migration available later.

## State and failure boundaries

Room provides Flow queries; a small snapshot combines domain reads. Loading, ready and failure are explicit Today states. A retry resubscribes after storage/mapping errors. The database is never reset on failure. Kotlin integer overflow is checked in monetary sums so corrupted totals fail rather than wrap. DataStore handles read IO failures with system defaults; write failures produce a snackbar. Preferences are not financial truth.

Appearance mutations update one DataStore key per atomic edit (`setTheme`, `setDynamicColor`). UI callbacks pass the changed value, not a copied snapshot of both settings. This prevents a fast theme/color sequence from overwriting a choice that has not yet flowed back to Compose. A physical-device regression test covers concurrent independent edits; screenshot tests await the requested persisted combination before capture.

The clock refreshes while the UI is subscribed, every 30 seconds. Date and timezone are re-read each tick, covering midnight, time changes and daylight-saving transitions while active. This is adequate for a foreground overview, not an alarm engine. Room queries currently observe full tables; implement bounded SQL projections and atomic multi-query snapshots as datasets grow, before performance-sensitive widgets ship. One emitted snapshot can briefly mix independent Flow updates; no financial decisions or writes occur from it.

Debug fixtures live under `src/debug`; the release source set has an empty, unavailable catalog. No fixture has a DAO reference. Mode lives in SavedStateHandle, never in user storage. Personal mode reads the actual empty database; empty demo does not substitute the database. Application IDs differ so installed debug data cannot silently become release data.

## Navigation and background responsibilities

Navigation Compose owns the four primary routes and the Money/subscriptions child. Primary destination state is restored; system Back follows the back stack. Quick Add is a native sheet routing to foundation screens and explicitly explains that forms are not available yet. No fake save buttons or successful transaction claims.

Do not instantiate WorkManager merely as an unused dependency. V1 uses it for tolerant background reconciliation/widget refresh and notification preparation, with durable due records and unique work. Precise user-visible reminders need a separate AlarmManager/permission decision. Glance reads the same validated Room projections, never a ViewModel or fixture catalog.

## Compatibility and release boundaries

Compile/target SDK 36, min SDK 26; Gradle 8.14, AGP 8.11.1, Kotlin/Compose compiler 2.2.20; Compose BOM 2025.09.01; Room 2.8.1. These are pinned stable versions verified available, not a claim to be the newest release. JDK 21 runs Gradle; emitted JVM bytecode targets 17. Dependency upgrades need a clean build, Room schema review and smoke test.

Database version 1 exports JSON schema. No destructive fallback. Every schema change needs an explicit migration, old-data fixture, migration test, index/foreign-key checks and data-preservation proof. Don't downgrade installed user databases; roll back by forward-compatible application patch or tested export/restore, not a destructive database reset.

Future sync may use existing stable string IDs, but there is no outbox, remote API, timestamp conflict policy or tombstone machinery tonight. Design synchronization only with explicit ownership, deletion and conflict requirements. Offline correctness remains authoritative.
