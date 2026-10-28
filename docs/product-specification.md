# Personal OS — product specification

## Phase 2 delivered scope

Persistent subscriptions support creation/editing, optional description/notes/account/category defaults, custom daily/weekly/monthly/yearly intervals, active/inactive lifecycle and payment history. Currency-separated billing-rate equivalents and bounded unpaid renewals help plan; only explicitly recorded payments create actual Money expenses. Payment/link writes, corrections and undo are atomic. Today reacts to unpaid renewals and payment changes. Room v3 and backup format 2 preserve every new field; format 1 imports remain supported. See [Phase 2 handover](phase-2-handover.md) for policies and actual validation. Notifications, automatic debits, provider cancellation, bank integration and cloud synchronization remain deferred.

<!-- impeccable:product-schema 1 -->

## Platform and stack

Android only for V1. Kotlin, Compose, Material 3, Navigation Compose, Room, Coroutines/Flow, ViewModel and DataStore. WorkManager and Glance are planned when background work and widgets have real behavior to own. No backend or cloud synchronization. Name, launcher mark and palette are provisional.

Phase 1.5 visual authority is the user-approved finance reference and [sky-blue direction](design/phase-1.5-direction.md). IDR is the explicit new-account/new-budget default, independent of device locale; the formatter uses Indonesian Rupiah grouping by default. Other denominations remain supported and are never converted/combined. The balance header totals active accounts within one selected currency. Category-spending bars show real selected-month expense shares with no forecasts, transfers or invented trends.

## Users and purpose

A person managing daily commitments, personal spending and leisure on their own Android device. Today connects these domains so the user can answer “what needs my attention?” without maintaining several disconnected dashboards. No team roles, shared accounts or commercial claims are assumed.

## Primary journeys

1. Open Today, see upcoming events, outstanding reminders and bills, and decide the next action.
2. Record an expense against an account; see its effect on the matching budget. Transfer between accounts without increasing spending.
3. Plan an event or reminder; receive a useful notification if permission is granted.
4. Track a recurring subscription, then record its actual payment once.
5. Resume a book, film, series or game and update progress.
6. Glance at a privacy-conscious home-screen widget and open the corresponding app screen.

## Domain requirements and scope

| Domain | V1 target | Phase 0 delivered slice |
|---|---|---|
| Today | Local overview and contextual actions | Hybrid priorities and compact domain summaries; coherent real monthly financial projection; debug-only previews |
| Money | Accounts, categories, expenses, income, same-currency transfers, monthly/category budgets | Persistent account/category management, entry creation/correction/deletion, monthly overall/category budgets, filtered paged history and real totals |
| Schedule | Timed/all-day events, actionable reminders, bounded recurrence | Entities, recurrence date helper; foundation destination |
| Subscriptions | Renewal schedule and explicit actual-payment tracking | Entities and upcoming-payment preview; child destination of Money |
| Library | Movie/series/game/book, lifecycle status, explicit progress unit | Entities, active-item preview; foundation destination |
| Widgets | Today and quick-entry deep links | Strategy only |

Phase 1.7 adds explicit Settings backup/export and validated full-replacement restore through Android's document picker. The portable JSON contains all persisted personal records, requires checksum/semantic validation and a preview plus explicit destructive confirmation, and excludes device appearance preferences. It is unencrypted, so trusted storage is required. No silent merge or automatic restore is performed.

Future work: encrypted backups, optional synchronization, currency conversion, richer recurrence/exceptions, integrations and media metadata lookup. None is assumed implemented. Android calendar synchronization, bank connections, streaming-provider connections, account login and AI features are outside V1's initial scope.

## Cross-domain behavior

- An expense contributes to matching period/category/currency budgets; income and transfers never contribute.
- A subscription is a forecast. Marking it paid must atomically create/link an expense and a unique due-period payment, then advance its schedule. Merely becoming due never creates an expense.
- Events reserve time; reminders represent things to do. Completing a reminder does not cancel an event.
- Today shows ongoing/upcoming events ending after now and starting before local tomorrow, pending reminders due before tomorrow plus undated reminders, the first active overall budget, active subscriptions overdue or due within seven days, and active library items. Lists are bounded to 4/4/4/3; “View all” leads to the responsible domain.
- Today selects one exact current-calendar-month overall budget, ordered by currency code then stable ID. Category budgets never add to its limit. Money shows currencies separately; a future Today currency chooser can replace this deterministic default.

## Offline, notifications and privacy

All functionality works without an app network requirement; the app requests no network permission. Store user records in app-private Room, appearance in app-private DataStore. No analytics, advertisements or embedded trackers. Android sandbox isolation is not database encryption. Automatic device backup remains disabled to avoid silently exporting financial data. Phase 1.7 supports user-selected SAF destinations: Android providers may offer local or remote storage, so the user chooses a trusted location. The app itself implements no cloud synchronization. Backups are unencrypted and checksums do not authenticate the author; encryption remains future work.

No notifications are scheduled in Phase 0. V1 requests Android 13+ notification permission at a meaningful opt-in, provides channels for reminders/events/bills, supports denial without blocking core use, and avoids financial amounts in lock-screen text by default. No exact-alarm permission until precision needs justify it. Reboot/timezone changes and recurrence cancellation must be addressed before shipping scheduling.

## Accessibility and inclusion

Use Material controls, at least 48 dp interactive targets, readable type at 1.3× and 2×, scrollable layouts, semantic headings, meaningful icon descriptions, grouped reading order, and text for overdue/over-budget state. Support light/dark and Android 12+ dynamic colors. Honor locale-aware dates and currency, device timezone, RTL layout and system animations. Copy is English-only in this foundation; extraction/translation and TalkBack-on-device testing remain release work. Do not infer currency from device language; users choose account currency in the future.

## Acceptance and explicit assumptions

- Four top-level destinations: Today, Money, Schedule, Library. Subscriptions belongs to Money.
- Populated demo and empty state are visibly identifiable in the debug-only Developer previews dialog and never seeded into Room. Personal is the default; Money always uses real storage. Release starts from an empty personal database with default editable category definitions.
- Today has actual date formatting, a greeting, priorities, a monthly budget, later-today events, upcoming payments, active media and functional Money Quick Add. Other domain editors remain deferred.
- Appearance settings persist, navigation supports system Back, expanded width uses a rail, and content respects system insets.
- Build and feasible checks are executed, with actual results and limitations recorded in the handover.

Assumptions requiring later approval: single person/device, minimum Android 8/API 26, manual entry, bounded daily/weekly/monthly/yearly recurrence, one displayed overall budget, no FX transfers, English-first, provisional name, and IDR used only in debug examples. These are implementation assumptions, not user-approved product commitments.
