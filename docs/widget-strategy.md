# Widget strategy

Phase 0 ships no widget receiver, fake widget or unused Glance/WorkManager dependency. V1 will use Glance with the same Room read projections as Today, accessed from an application-owned data layer, not an Activity/ViewModel. A worker or Glance action can read a bounded snapshot on an IO dispatcher.

Start with one resizable Today widget: next event, pending reminder count and next bill label, with a clear “Open Today” action. Compact sizes prioritize the next action; larger sizes add rows. Deep links must land in the owning domain. Quick-entry opens an Activity; do not put finance-editing workflows into remote widget state. Completion actions require an occurrence ID and a validated atomic DAO operation; expose none until those invariants exist.

Refresh after committed database mutations, appearance/privacy changes, widget addition and timezone/date changes. Coalesce updates. Use unique WorkManager work for eventual refresh; periodic work has a minimum interval and is inexact, so never promise minute-perfect widget times. Event/reminder alarms may trigger additional updates when appropriate. Render last-updated status only when useful and never imply a stale snapshot is current.

Glance state stores only widget configuration (selected content/privacy); Room stays the source of truth. Queries should be date-bounded and support a consistent read transaction. No debug fixture imports or exported demo mode. Before shipping, test app process death, offline mode, database migration, boot, permission denial, missing/deleted item deep links, DST, multiple widget instances and small/large host sizes.

Default widget privacy hides account balances and payment amounts. The user can explicitly enable sensitive content per widget. Home screens and screenshots can expose widget content regardless of lock-screen notification settings. Use Material-compatible Glance theming, dark/dynamic colors and accessible touch targets; avoid copying a full Compose dashboard into a widget.
