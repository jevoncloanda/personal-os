# Data model

## Current: Phase 2 subscription and payment contract

Room is version 3. Migration 2→3 preserves existing rows and links, adds `subscriptions.description` and `notes` (empty text defaults), optional `accountId`/`categoryId` with RESTRICT foreign keys and indexes. Payments keep unique `(subscriptionId,dueEpochDay)` and unique `entryId`; expenses remain ordinary positive integer-minor-unit ledger rows. Subscription currency is fixed after payment history exists. A referenced default account cannot change currency. Historical archived references remain legal; new payment selection requires active matching-currency accounts and expense-compatible categories.

Backup exports format 2/schema mapping 3; readers accept both this mapping and the Phase 1.7 format 1/schema mapping 2. Legacy subscriptions receive explicit empty text/null-reference defaults. All sections/fields are required for their version; unsupported combinations and invalid new references fail before replacement. Historical Phase 1.7 details below are retained as prior-version context.

Renewals derive from their original local-date anchor: January 31 → February 28/29 → March 31; February 29 annual plans return to February 29 in leap years. Daily/weekly/monthly/yearly intervals 1–1000 are editable. Paid occurrence dates stay immutable; actual expense dates/amounts/accounts/categories/notes can be corrected on the same entry. Undo explicitly removes the link and expense atomically and reopens the date if still in the current schedule, without reactivating an inactive plan. Early/late payment never changes the anchor. Paying a later cycle leaves earlier cycles unpaid. Editing the next date/frequency explicitly reanchors future forecasts and preserves payment history. Cancellation stops forecasts and retains history.

## Phase 1.7 portable backup contract

All nine current tables and every persisted field are included in format `com.jevon.personalos.backup`, backup version 1, data schema version 2. Full replacement retains IDs, archive/completion state, recurrence, financial effective epoch days/local minute-of-day, exact signed opening balances and positive integer entry minor units. It does not convert currencies or serialize derived balances. Stored legacy irregular/overlapping budgets remain supported; no current-editor normalization rewrites their history. All actual domain rows are included even where production management editors are still deferred.

The importer validates the complete graph, including FK references, enum/type/range checks, payment uniqueness and expense compatibility, plus conservative lifetime currency/kind and per-account incoming/outgoing sums and resulting signed-64-bit balance limits. Every section and v1 field is required; unknown fields/unsupported formats or schema mappings are rejected. Appearance preferences and all in-memory previews/caches are excluded. The checksum provides damage detection, not confidentiality or author authentication. Exact canonicalization, size limits and rollback proof are in [the Phase 1.7 handover](phase-1.7-handover.md). Room remains v2; backup format evolution requires explicit readers independently of later Room migrations.

## Phase 1.5 currency presentation

New account and budget editors default explicitly to IDR. The shared formatter defaults IDR to Indonesian grouping/symbol (`Rp25.000`, fractional minor units `Rp25.000,50`) and USD to US dollar formatting; an explicit caller locale is still supported. This affects presentation only. Existing USD/other currency records, opening balances, entries and budgets are unchanged. An empty account's currency can be corrected explicitly; an account with history keeps its currency and requires a separate correctly denominated account for future entries. No FX or automatic history rewrite exists. Default currency is not inferred from wallpaper, device language, alphabetic account order or a combined balance.

Room remains version 2 with the existing 1→2 migration. New spending-category queries and pure presentation totals add no tables or columns. Overall budgets and forecasts retain their previous accounting rules.

## Phase 1 policies (current)

- Account opening balance is a signed adjustment, not an income entry. Current balance includes every stored entry, including future effective dates. Negative balances are allowed. Editing the opening balance intentionally changes current balance; currency is immutable once any transaction references the account. Account type and optional notes are metadata.
- Accounts and categories are archived/restored, never deleted by the UI. History keeps references and renamed labels. Archived references can be retained when correcting an existing transaction; new transactions cannot use them. Category type stays fixed. Legacy v1 categories have no type and remain compatible with either income or expense. Default categories initialize only an entirely empty catalog and are never recreated over custom/archived records.
- Positive amounts use exact ISO minor-unit precision (IDR explicitly two digits). Input uses an ungrouped decimal point, rejects exponent/grouped/overprecision/overflow values, and never uses floating point for accounting. Signed opening balances accept zero. Transfers have no category and require distinct same-currency accounts.
- Effective local date is authoritative for budgets; `minuteOfDay` records local transaction time without reassigning historical dates when timezone changes. Migrated v1 entries default to 00:00 because v1 recorded no time. Editing/deleting a transfer changes both account effects atomically. Entries linked to subscription settlements cannot be edited/deleted through the general Money workflow.
- New budgets are exact calendar months with positive limits. Overlap is rejected within the same currency and scope (overall or one category). Category budgets are independent sublimits, never added to the overall limit. Existing v1 irregular/overlapping budgets are preserved; editing requires a valid monthly interval without a conflict. Today selects an exact current-calendar-month overall budget ordered by currency code then stable ID, limited to one. Legacy irregular budgets remain visible in Money while active but are not mislabeled as monthly on Today.
- All financial arithmetic is checked. A write that would overflow balances, per-account incoming/outgoing sums, or lifetime totals per currency/kind rolls back. That aggregate ceiling is conservative: even offsetting historical flows cannot each exceed `Long.MAX_VALUE`. Progress uses floating point only for visual interpolation.
- Migration 1→2 adds account `type='OTHER'` / `notes=''`, nullable category `kind` / `archived=false`, and entry `minuteOfDay=0`. No record, existing field, foreign key or financial amount is rewritten. No destructive fallback exists. API 26 compatibility uses exact BigDecimal conversion and checked BigInteger range comparison.

All tables use stable caller-generated string IDs (UUIDs in future editors). Money is a positive signed-64-bit count of ISO-currency minor units, never floating point. Financial dates use local epoch day; timed schedule records use epoch milliseconds plus an IANA event timezone. Library update timestamps use epoch milliseconds.

| Table | Purpose and important fields |
|---|---|
| accounts | Name, currency, signed opening balance, archived flag |
| categories | User-facing spending category |
| entries | Kind, positive amount, currency, local financial date, source/destination accounts, optional category and note |
| budgets | Currency, limit, inclusive start/exclusive end, optional category |
| events | Title, start/end instants, zone ID, all-day flag, location, recurrence anchor |
| reminders | Title, nullable due instant, nullable completion instant, recurrence anchor |
| subscriptions | Positive forecast amount, currency, next due day, active flag, recurrence anchor |
| subscription_payments | Subscription, due day, linked actual expense |
| library_items | Title, kind, lifecycle status, progress, optional total, update timestamp |

## Money invariants

Expense: source account only. Income: destination account only. Transfer: distinct source and destination, with no spending category. Transfer is one row; its two account effects cannot become half a transfer. All referenced accounts must exist, be active and match the entry currency. `recordEntry` checks them inside a Room transaction and uses ABORT for duplicate IDs. Cross-currency transfers are unsupported. Account balance is opening + incoming income/transfers − outgoing expenses/transfers; balance UI is deferred. Negative balances are permitted; overdraft rules are not invented.

Entity constructors validate entry shape and positive amount. Foreign keys and indices enforce references; account/category deletion is restricted. This is app-private storage, not an API accepting arbitrary SQL. Future imports and all future write paths must use validated DAO operations; direct SQL bypasses constructor checks. A later financial editing workflow must centralize edits, validate ISO currency with `Currency.getInstance`, support corrections/refunds explicitly, and prove balance arithmetic without overflow.

Budget periods are concrete half-open local-date intervals, not “month number” alone. A monthly editor will materialize the next interval from a calendar month. A category budget filters by category; overall budgets include uncategorized expenses. Currency must match. No exchange-rate arithmetic. Today shows the first active overall budget, not the sum of overlapping budgets. Before budget editing, disallow conflicting overall budgets for the same currency/period or make selection explicit.

## Forecasts, recurrence and schedule

Subscription renewal forecasts do not affect balances or budgets. A payment uniquely identifies `(subscriptionId, dueEpochDay)` and each expense can settle only one payment. Before exposing payment recording, add a transactional operation validating expense kind/currency/amount, linking or creating the entry, and advancing due day atomically. Schema constraints alone do not prove that semantic relationship. Price changes apply to forecasts; history keeps the actual entry amount.

Recurrence supports DAILY/WEEKLY/MONTHLY/YEARLY, positive interval, original anchor and optional inclusive until day. `occurrenceDate` derives occurrence N from the original anchor: Jan 31 -> Feb 28 -> Mar 31; leap-day anniversaries clip in non-leap years. No RRULE text parser, “last weekday”, exceptions or completion history yet. Anchors are local dates. Recurring event wall-clock time must be resolved in the stored event timezone, with explicit DST gap/overlap rules in the scheduler phase. The foundation stores base records; it does not expand recurring events/reminders or advance subscriptions.

Event ends must follow starts. All-day events reserve date boundaries in the stored event zone, with exclusive end. Reminders have no duration and may have no due time. A future recurring reminder needs occurrence completion records; a single completion timestamp is only enough for one-off reminders. Do not build recurring reminder completion by toggling the template.

## Library progress

Kind fixes progress units: movie = elapsed minutes, series = completed episodes, game = elapsed whole hours, book = pages read. Progress is nonnegative; known total is positive and bounds progress. Status is PLANNED, ACTIVE, COMPLETED or DROPPED. Game percent completion and series season hierarchy are future decisions. Do not label hours as percentage or infer completion from an unknown total.

## Migrations and optional future synchronization

Version 1 is the first schema, exported under `app/schemas/`. Preserve it in source control. Every future version needs tested forward migrations; no destructive fallback. Include realistic old fixtures, negative opening balances, transfers, nullable reminders and recurrence anchors. No migration test exists yet because there is no prior released schema.

Stable IDs avoid coupling records to UI position and could support an optional later sync design. There is no sync protocol, automatic deletion merge, remote identifier or backend. Explicit SAF export/recovery is implemented in Phase 1.7; automatic Android backup remains disabled.
