# Phase 3 — calendar, reminders and local alerts

## Plan and decisions — 28 October 2026

Baseline `049c5f3` captures verified Phase 2. Private screenshots/reports/exports, local SDK/JDK/cache, database files and signing material are ignored; original handovers containing device/audit metadata remain preserved under ignored `.tools/baseline-original-docs`. Source, tests, Room schemas, wrapper, licensed assets and approved design reference are committed. No push/publication.

Extend events/reminders rather than replacing them. Add notes, reminder zone/date-only meaning and revision counters. Calendar dates of all-day events and date-only reminders derive in their stored zone and retain date meaning in another viewing zone. Timed series retain wall-clock start/end in their stored zone. Java time resolves DST gaps forward by the gap and overlaps to the earlier offset. Month-end/leap recurrence derives from the original anchor. Projections seek directly into bounded visible ranges rather than expanding from the beginning.

Recurring reminder completion uses a unique reminder/revision/nominal-date key. Schedule changes start a new revision; prior completion history remains stored. Title/notes-only edits preserve current occurrence completion. One-off and undated completion retain the existing completion timestamp. Entire-series editing/deletion is explicit; single-occurrence event edits are deferred. Default agenda emphasizes upcoming/overdue items; month mode selects a date. Subscription forecasts call the Phase 2 recurrence code and never create calendar or Money rows.

Use one persisted, shared alert-preference table with optional event/reminder/subscription foreign keys (exactly one owner), multiple lead minutes and local time for all-day/date-only/subscription alerts. No preference rows means alerts off. The configure form offers subscription presets 14/7/3/1/0 days (3-day suggestion), event/reminder minute presets and bounded custom notice. Defaults remain off until a relevant user action enables alerts. Existing/imported older records get no surprise alerts.

Use a single next AlarmManager wakeup to service a bounded set of next alerts, not an indefinite repeating alarm per occurrence. A private runtime ledger derives identities from domain/record/revision/nominal date/lead and is excluded from backups. Reconciliation validates queued deliveries against current Room data, skips paid/completed/inactive records and prevents repeated delivery. Immutable PendingIntents use explicit components and stable URI identities. Channels and runtime permission are checked, with contextual explanation/request and Settings return refresh. No exact-alarm permission, foreground service, backend or guaranteed timing claim. Reconcile on changes, application start/resume, alarm, boot and time/zone changes, and restore invalidation. Never reboot the personal phone solely for testing.

Current guidance inspected: [AlarmManager scheduling](https://developer.android.com/develop/background-work/services/alarms), [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission), [channels](https://developer.android.com/develop/ui/compose/notifications/channels). Inexact alarms can be delayed, especially under power restrictions; permission and channel denial do not affect stored reminders.

Room migration and backup versioning preserve prior formats explicitly. Completion history and alert preferences are portable; derived OS scheduling state is not. Validate focused core tests before logical commits, then full build/lint/regressions and isolated Samsung UI/delivery checks. App screenshots stay local under `docs/screenshots/phase-3/`. Delivery must be observed separately from scheduling acceptance.

## Results

Implementation in progress. Actual verification and commit hashes will be recorded at closeout.
