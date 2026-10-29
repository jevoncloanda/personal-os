# Phase 3 — calendar, reminders and local alerts

## Plan and decisions — 28 October 2026

Baseline `049c5f3` captures verified Phase 2. Private screenshots/reports/exports, local SDK/JDK/cache, database files and signing material are ignored; original handovers containing device/audit metadata remain preserved under ignored `.tools/baseline-original-docs`. Source, tests, Room schemas, wrapper, licensed assets and approved design reference are committed. No push/publication.

Extend events/reminders rather than replacing them. Add notes, reminder zone/date-only meaning and revision counters. Calendar dates of all-day events and date-only reminders derive in their stored zone and retain date meaning in another viewing zone. Timed series retain wall-clock start/end in their stored zone. Java time resolves DST gaps forward by the gap and overlaps to the earlier offset. Month-end/leap recurrence derives from the original anchor. Projections seek directly into bounded visible ranges rather than expanding from the beginning.

Recurring reminder completion uses a unique reminder/revision/nominal-date key. Schedule changes start a new revision; prior completion history remains stored. Title/notes-only edits preserve current occurrence completion. One-off and undated completion retain the existing completion timestamp. Entire-series editing/deletion is explicit; single-occurrence event edits are deferred. Default agenda emphasizes upcoming/overdue items; month mode selects a date. Subscription forecasts call the Phase 2 recurrence code and never create calendar or Money rows.

Use one persisted, shared alert-preference table with optional event/reminder/subscription foreign keys (exactly one owner), multiple lead minutes and local time for all-day/date-only/subscription alerts. No preference rows means alerts off. The configure form offers subscription presets 14/7/3/1/0 days (3-day suggestion), event/reminder minute presets and bounded custom notice. Defaults remain off until a relevant user action enables alerts. Existing/imported older records get no surprise alerts.

Use a single next AlarmManager wakeup to service a bounded set of next alerts, not an indefinite repeating alarm per occurrence. A private runtime ledger derives identities from domain/record/revision/nominal date/lead and is excluded from backups. Reconciliation validates queued deliveries against current Room data, skips paid/completed/inactive records and prevents repeated delivery. Immutable PendingIntents use explicit components and stable URI identities. Channels and runtime permission are checked, with contextual explanation/request and Settings return refresh. No exact-alarm permission, foreground service, backend or guaranteed timing claim. Reconcile on changes, application start/resume, alarm, boot and time/zone changes, and restore invalidation. Never reboot the personal phone solely for testing.

Current guidance inspected: [AlarmManager scheduling](https://developer.android.com/develop/background-work/services/alarms), [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission), [channels](https://developer.android.com/develop/ui/compose/notifications/channels). Inexact alarms can be delayed, especially under power restrictions; permission and channel denial do not affect stored reminders.

Room migration and backup versioning preserve prior formats explicitly. Completion history and alert preferences are portable; derived OS scheduling state is not. Validate focused core tests before logical commits, then full build/lint/regressions and isolated Samsung UI/delivery checks. App screenshots stay local under `docs/screenshots/phase-3/`. Delivery must be observed separately from scheduling acceptance.

## Implemented workflows

Schedule defaults to a useful agenda, with month navigation, date selection and a Today jump. Create, edit and delete timed, all-day and multi-day events, with notes, location text, stored time zone and optional alerts. Reminders support optional dates/times, notes, completion/reopening, overdue presentation and recurrence. Undated reminders never schedule alerts. Series edit/delete scope is explicit; individual event edits and week view are deferred.

Today uses persisted, bounded projections with overdue and due-today reminders ahead of upcoming events and relevant subscription renewals. Completion invalidates both screens reactively. Links retain nominal occurrence dates. Quick Add includes event/reminder actions alongside existing financial actions. Subscription forecasts reuse Phase 2 logic, remain distinguishable from events and never create expenses automatically.

Daily, weekly, monthly and yearly series accept custom intervals and optional end dates. Month-end and leap-day calculations stay anchored to the original date. Timed recurrence retains stored-zone wall time; DST gaps move forward and overlaps choose the earlier offset. A gap that collapses an event's end retains its positive wall duration. Visible-range seeking and capped projections prevent indefinite expansion. Subscription forecasts cap each plan at 128 occurrences and the combined list at 200, reporting truncation.

Recurring reminder completion identifies `(reminderId, revision, occurrenceEpochDay)`, with database uniqueness and idempotent writes. Completing one occurrence leaves future dates available. Schedule changes create a new revision while retaining history; title/notes edits preserve the current revision. One-off completion survives due-date changes. Legacy recurring scalar completion represents the first occurrence only.

## Alert policy and recovery

One local scheduler serves all three domains. Subscription presets are 14/7/3/1/0 days, with multiple leads and a configurable local time defaulting to 09:00. Three days is a form suggestion; alerts remain off until configured. Events/reminders offer 0/5/15/30/60/1440-minute presets and custom notice up to fourteen days, at most six preferences. Date-based notices subtract calendar days and then apply local time; timed notices subtract elapsed minutes. Paid/skipped/inactive subscription renewals and completed reminder occurrences are excluded according to existing occurrence state.

Three Android channels, contextual `POST_NOTIFICATIONS`, immutable explicit PendingIntents and stable identities support permission-aware delivery and record navigation. The scheduler uses one inexact AlarmManager wakeup and bounded next-occurrence plans. It reconciles edits, completion/reopening, deletion, source invalidation, app start/resume, boot/package replacement, time/zone changes and restoration. `RECEIVE_BOOT_COMPLETED` is declared; no exact-alarm access, location permission or foreground service is needed. Permission/channel denial leaves underlying records usable, with status and Settings access. Permission-result refresh is bounded to accommodate grant/app-op propagation.

Delivery is best effort and can be delayed by Android/device power management. Current records are revalidated before posting; stable tags and `onlyAlertOnce` reduce duplicate effects. SQLite and Android posting do not share a transaction, so universal exactly-once delivery is not claimed. Real reboot, process-kill and Doze behavior were not certified.

## Persistence and compatibility

Room v4 adds event/reminder fields, reminder completion history, shared alert preferences and a derived alert ledger through explicit 3→4 migration. Earlier migrations remain registered. Backup format 3/schema mapping 4 includes all eleven portable tables and accepts formats 1/schema2 and 2/schema3 with explicit defaults. Graph, recurrence and occurrence relationships are validated before replacement. Restore remains atomic; failure rolls back. Derived alert state is cleared atomically and rebuilt through database invalidation, never imported as durable OS identifiers. Permission state is not portable.

## Executed validation — 28–29 October 2026

- Debug, Android-test and unsigned release builds passed. The final debug APK was installed without clearing data. The last recurrence-copy change compiled after the full gate run.
- JVM: **54 tests passed**. Android lint: **0 errors, 19 advisory warnings** on the final functional build; the subsequent wording-only change was compile-checked without repeating lint/full suites.
- Initial physical data run: **41 passed** across Schedule, alert scheduling, Room migrations, subscriptions, backup database/I/O and Money.
- Focused closeout data run: **14 passed**, including eight Schedule cases, four scheduler cases, record-link routing and the read-only personal-data audit. Ten overlap earlier coverage: **45 distinct passing device data cases** overall.
- `ScheduleWorkflowTest` passed: timed/all-day multi-day events, recurring reminder completion with future occurrences intact, edit/history, month/date selection, light/dark screens, undated completion/reopening, Today and Activity recreation. Together with data tests this is **46 distinct passing physical cases**, not a passing full UI suite.
- Notification physical case **failed** during `ActivityScenario.close` with `Current state was null unexpectedly`; cleanup masked the remaining tap result. Before that failure, **four real notifications were observed while backgrounded**: two event leads, one reminder and one subscription. Scheduled 16:43:12 UTC and observed 16:45:18 UTC on 28 October. Subscription notification tapping opened its correct detail. Cancellation/disabled fixture and stable-tag reconciliation assertions passed before tapping. This is partial delivery evidence, not exact-time certification or a passing notification suite.
- The corrected notification harness compiled but could not be rerun after the lock guard stopped UI execution. Its file remains deliberately uncommitted: `app/src/androidTest/java/com/jevon/personalos/NotificationDeviceTest.kt`.
- Own-UID error-log inspection found no application fatal in the inspected log. The upgraded personal database passed a read-only foreign-key audit and full portable in-memory encode/decode equality check, including the available earlier baseline comparison. This was not a before/after personal UI semantic audit.

Local, ignored evidence: `app/build/reports/phase-3-data.txt`, `phase-3-closeout-data.txt`, `phase-3-device.txt`, `phase-3-app-log.txt`, and `.tools/review/phase-3/notification-delivery.txt` / `personal-audit.txt`.

Validation entry points: `tools/gradle.ps1 assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug assembleRelease --console plain`; device classes run through `tools/run-phase1-device.ps1` with isolated fixtures, using `-DataOnly` where no UI was required. No emulator, personal restore, phone reboot, security/power-setting change or personal test-record insertion was used. The original denied notification grant state was restored and verified (`granted=false`); this does not assert restoration of every Android permission flag.

## Screenshots and review

Meaningful local captures are under `docs/screenshots/phase-3/`: `schedule-empty.png`, `schedule-agenda.png`, `schedule-month-light.png`, `schedule-month-dark.png`, `selected-day.png`, `event-detail.png`, `recurring-reminder-completed.png`, `today-real-schedule.png` and `notification-open-subscription.png`. Screenshots stay ignored to protect personal/device evidence. The file named `notification-settings-granted.png` shows a transition with blocked status, not settled permission proof.

Independent code review found six material issues: completion loss, DST day leads, zone-range boundaries, completed-history starvation, lost Today occurrence dates and forecast truncation. All were fixed with focused coverage; the follow-up source review found no remaining material issue in its scope. The reviewer did not execute tests.

Visual review remains **fix / pending rendered proof**. Reminder button padding/shape and natural recurrence wording were corrected in source and compiled after capture; fresh screenshots were unavailable. Existing design tokens, `DESIGN.md` and `.impeccable/design.json` were preserved.

## Remaining physical gates

### Final-verification attempt — 29 October 2026

Starting from `e0e4636`, inspected the outstanding notification harness, prior reports and screenshots. The Samsung wireless service was discovered and connected once, but the first lock-state check reported `showing=true`. No UI/notification case was launched, no permission was changed, and no dependent checks were retried. The corrected harness remains compile-validated only and deliberately uncommitted. No new production defect was established by this attempt.

One independent final gate passed: debug/Android-test/unsigned-release builds, **54 JVM tests (zero failures/errors/skips)** and lint **0 errors / 19 warnings**. Gradle reused matching task results where valid. One physical data run passed **all 45 cases** together: Schedule 8, alerts 4, migrations 3, subscriptions 9, backup database 6, backup I/O 3, Money 10, routing 1 and read-only personal audit 1. These repeat the existing distinct coverage; they do not increase the cumulative total. APKs were installed as updates without storage clearing. Reports: `app/build/reports/phase-3-final-closeout-data.txt` and `phase-3-final-closeout-app-log.txt`. The inspected own-UID error log contained no application fatal.

Reinspection of the older reminder screenshot confirmed clipped leading glyphs and old recurrence copy; current source fixes still require recapture. The older permission screenshot displays blocked status and remains unsuitable as granted-state proof. Fresh screenshots, large text, UI regressions, settings-return permission refresh, event/reminder notification taps and the corrected full notification run are still blocked by the locked phone. Actual delivery evidence remains the earlier four background alerts and subscription tap; real reboot and Doze remain untested. **Phase 3 is not ready to close.**

The original five commits are unchanged, ending at `e0e4636` (`docs: record Phase 3 verification and remaining device gates`). This attempt changes only this handover, committed as `docs: record blocked Phase 3 final verification`. No push, personal-record modification or phone security/power/timeout change occurred. Working-tree exception remains the untracked `NotificationDeviceTest.kt`; retaining it preserves the unfinished work honestly.

The Samsung locked. Following the user's overnight instruction, UI attempts stopped without repeated retries or unlock requests. Remaining gates are:

- Event/reminder notification taps and a complete passing corrected notification-device case.
- Fresh reminder-row clipping/copy captures; settled granted/revoked status and return-from-Settings verification.
- Integrated shipping-shell, Quick Add, draft and general UI regression rerun against Phase 3. Earlier suite history is not proof for this build.
- Enlarged text, TalkBack, RTL/tablet checks and real reboot/process-kill/Doze delivery conditions.

Production implementation and independent automated checks are validated as above; **Phase 3 physical and visual verification is incomplete**. Existing personal records were preserved. No push or publication occurred. Close these hardware/accessibility gates before starting another product domain.

## Local Git history

- `049c5f3` — Phase 2 baseline, `feat: establish Personal OS through subscriptions`.
- `df42c8e` — persistence/migration/backup, `feat: persist schedule series completions and portable alert preferences`.
- `b968eb5` — recurrence/projections/shared alerts, `feat: reconcile shared local alerts and bounded schedule projections`.
- `f817db3` — UI/Today/Quick Add and validated integration, `feat: connect Schedule workflows and notification settings to Today`.
- Final documentation commit: `docs: record Phase 3 verification and remaining device gates` (resolve with `git log -1`; its own hash cannot be embedded in the same commit).

Only coherent validated work was committed. The unfinished notification-device harness remains visible and uncommitted; generated APKs, disposable data and private evidence are excluded.
