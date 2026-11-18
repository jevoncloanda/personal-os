package com.jevon.personalos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.error as fieldError
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jevon.personalos.data.*
import com.jevon.personalos.schedule.*
import java.time.*
import java.util.UUID

@Composable
private fun RepeatFields(
    frequency: String,
    interval: String,
    until: String,
    enabled: Boolean,
    setFrequency: (String) -> Unit,
    setInterval: (String) -> Unit,
    setUntil: (String) -> Unit,
) {
    val dirty = LocalMarkDraftDirty.current
    Text("REPEAT", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            "NONE" to "Never",
            "DAILY" to "Daily",
            "WEEKLY" to "Weekly",
            "MONTHLY" to "Monthly",
            "YEARLY" to "Yearly",
        ).forEach { (value, label) ->
            FilterChip(
                selected = frequency == value,
                enabled = enabled,
                onClick = {
                    if (frequency != value) {
                        dirty()
                        setFrequency(value)
                    }
                },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = if (frequency == value) {
                    { Icon(AppIcons.CheckCircle, null, Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
    if (frequency != "NONE") {
        Field("Repeat interval", interval, enabled, KeyboardType.Number, change = setInterval)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.weight(1f)) {
                BillingDateField("Repeat through (optional)", until, enabled, compact = true,
                    change = setUntil)
            }
            if (until.isNotBlank()) IconButton(enabled = enabled, onClick = {
                dirty()
                setUntil("")
            }) {
                Icon(AppIcons.Add, "Remove repeat end date", Modifier.rotate(45f))
            }
        }
    }
}

private fun recurrence(frequency: String, interval: String, until: String, base: LocalDate) = Recurrence(
    if (frequency == "NONE") null else Frequency.valueOf(frequency),
    if (frequency == "NONE") 1 else interval.toIntOrNull() ?: throw IllegalArgumentException("Use a whole repeat interval"),
    base.toEpochDay(), if (frequency == "NONE" || until.isBlank()) null else LocalDate.parse(until).toEpochDay())

@Composable
private fun TimeZoneOption(zone: String, enabled: Boolean, change: (String) -> Unit) {
    val device = ZoneId.systemDefault().id
    var override by rememberSaveable { mutableStateOf(false) }
    val dirty = LocalMarkDraftDirty.current
    if (override || zone != device) {
        Field("Time zone", zone, enabled, change = change)
        TextButton(enabled = enabled, onClick = {
            dirty()
            change(device)
            override = false
        }) { Text("Use device time zone") }
    } else TextButton(enabled = enabled, onClick = { override = true }) {
        Text("Time zone · $device")
    }
}

private fun alertSummary(alerts: List<AlertPreferenceEntity>): String {
    val leads = alerts.map { it.leadMinutes }.distinct().sorted()
    val timing = leads.take(2).joinToString(" · ") {
        if (it == 0) "At start / due time" else "$it min before"
    }
    return timing + if (leads.size > 2) " · +${leads.size - 2} more" else ""
}

private fun optionsSummary(
    frequency: String,
    interval: String,
    until: String,
    notes: String,
    zone: String,
    alerts: List<AlertPreferenceEntity>,
    location: String = "",
): String {
    val repeat = runCatching { recurrence(frequency, interval, until, LocalDate.ofEpochDay(0)) }
        .getOrNull()
    return buildList {
        if (frequency != "NONE") add(repeat?.let(::repeatLabel) ?: "Repeat needs attention")
        if (alerts.isNotEmpty()) add("Alerts · ${alertSummary(alerts)}")
        if (notes.isNotBlank()) add("Notes added")
        if (location.isNotBlank()) add("Location added")
        if (zone != ZoneId.systemDefault().id) add(zone)
    }.joinToString(" · ").ifBlank { "Repeat, alerts, notes and time zone" }
}

@Composable
private fun NotificationSummary(
    alerts: List<AlertPreferenceEntity>,
    open: (() -> Unit)?,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Clock, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("Notifications", Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.titleSmall)
            if (open != null) TextButton(
                enabled = enabled && !LocalEditorDirty.current,
                onClick = open,
            ) { Text("Configure", style = MaterialTheme.typography.labelMedium) }
        }
        Text(
            if (alerts.isEmpty()) "Notifications off" else alertSummary(alerts),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (open == null) Text(
            "Set notification timing after saving.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ) else if (LocalEditorDirty.current) Text(
            "Save changes before editing notification timing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DateTimePair(
    content: @Composable RowScope.() -> Unit,
    largeContent: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.15f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = largeContent)
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

private fun repeatIssue(frequency: String, interval: String, until: String, date: String): String? {
    if (frequency == "NONE") return null
    val count = interval.toIntOrNull()
    if (count == null || count !in 1..1000) return "Use a repeat interval from 1 to 1000"
    if (until.isBlank()) return null
    val last = runCatching { LocalDate.parse(until) }.getOrNull()
        ?: return "Choose a valid repeat end date"
    val first = runCatching { LocalDate.parse(date) }.getOrNull()
    return if (first != null && last < first) "Repeat end must not precede the first date" else null
}

private fun eventTimingIssue(
    date: String,
    lastDate: String,
    startTime: String,
    endTime: String,
    zone: String,
    allDay: Boolean,
): String? = runCatching {
    val z = ZoneId.of(zone)
    val startDate = LocalDate.parse(date)
    val finalDate = LocalDate.parse(lastDate)
    val a = if (allDay) startDate.atStartOfDay(z)
        else startDate.atTime(LocalTime.parse(startTime)).atZone(z)
    val b = if (allDay) finalDate.plusDays(1).atStartOfDay(z)
        else finalDate.atTime(LocalTime.parse(endTime)).atZone(z)
    when {
        startDate.year !in 1..9999 -> "Choose a start date from year 1 to 9999"
        b.toInstant() <= a.toInstant() -> "End must follow start"
        Duration.between(a.toInstant(), b.toInstant()).toDays() > 3660 ->
            "Events can span up to ten years"
        else -> null
    }
}.getOrElse { "Choose valid event dates and times" }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ScheduleErrorGroup(
    message: String?,
    firstError: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    val showError = LocalValidateFields.current && message != null
    Column(
        Modifier.fillMaxWidth().bringIntoViewRequester(requester),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
        if (showError) Text(
            requireNotNull(message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { fieldError(requireNotNull(message)) },
        )
    }
    LaunchedEffect(showError, message, firstError) {
        if (showError && message == firstError) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
}

@Composable
internal fun EventEditor(old: EventEntity?, selected: LocalDate, busy: Boolean, error: String?, close: () -> Unit,
    alerts: List<AlertPreferenceEntity> = emptyList(), openAlerts: (() -> Unit)? = null, save: (EventEntity, Boolean) -> Unit) {
    val id = rememberSaveable { old?.id ?: UUID.randomUUID().toString() }
    val base = old?.let { Instant.ofEpochMilli(it.startsAtMillis).atZone(ZoneId.of(it.zoneId)) }
        ?: selected.atTime(9, 0).atZone(ZoneId.systemDefault())
    val end = old?.let { Instant.ofEpochMilli(it.endsAtMillis).atZone(ZoneId.of(it.zoneId)) } ?: base.plusHours(1)
    var title by rememberSaveable { mutableStateOf(old?.title.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(old?.notes.orEmpty()) }
    var location by rememberSaveable { mutableStateOf(old?.location.orEmpty()) }
    var allDay by rememberSaveable { mutableStateOf(old?.allDay ?: false) }
    var date by rememberSaveable { mutableStateOf(base.toLocalDate().toString()) }
    var lastDate by rememberSaveable { mutableStateOf((if (old?.allDay == true) end.toLocalDate().minusDays(1) else end.toLocalDate()).toString()) }
    var startTime by rememberSaveable { mutableStateOf(base.toLocalTime().withSecond(0).withNano(0).toString()) }
    var endTime by rememberSaveable { mutableStateOf(end.toLocalTime().withSecond(0).withNano(0).toString()) }
    var zone by rememberSaveable { mutableStateOf(old?.zoneId ?: ZoneId.systemDefault().id) }
    var frequency by rememberSaveable { mutableStateOf(old?.recurrence?.frequency?.name ?: "NONE") }
    var interval by rememberSaveable { mutableStateOf((old?.recurrence?.interval ?: 1).toString()) }
    var until by rememberSaveable { mutableStateOf(old?.recurrence?.untilEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()) }

    val titleIssue = when {
        title.isBlank() -> "Title is required"
        title.trim().length > 8192 -> "Title must be 8192 characters or fewer"
        else -> null
    }
    val zoneIssue = if (runCatching { ZoneId.of(zone) }.isFailure) "Choose a valid time zone" else null
    val timingIssue = if (zoneIssue == null)
        eventTimingIssue(date, lastDate, startTime, endTime, zone, allDay) else null
    val recurrenceIssue = repeatIssue(frequency, interval, until, date)
    val notesIssue = if (notes.trim().length > 8192) "Notes must be 8192 characters or fewer" else null
    val locationIssue = if (location.trim().length > 8192)
        "Location must be 8192 characters or fewer" else null
    val advancedIssue = listOfNotNull(zoneIssue, recurrenceIssue, notesIssue, locationIssue).firstOrNull()
    val firstError = listOfNotNull(
        titleIssue, zoneIssue, timingIssue, recurrenceIssue, notesIssue, locationIssue,
    ).firstOrNull()

    EditorFrame(if (old == null) "New event" else "Edit entire event series", busy, error, close, {
        require(title.isNotBlank()) { "Title is required" }
        val z = ZoneId.of(zone)
        val startDate = LocalDate.parse(date)
        val finalDate = LocalDate.parse(lastDate)
        val a = if (allDay) startDate.atStartOfDay(z) else startDate.atTime(LocalTime.parse(startTime)).atZone(z)
        val b = if (allDay) finalDate.plusDays(1).atStartOfDay(z) else finalDate.atTime(LocalTime.parse(endTime)).atZone(z)
        require(b.toInstant() > a.toInstant()) { "End must follow start" }
        val proposed = recurrence(frequency, interval, until, startDate)
        val repeat = if (old != null && startDate == eventBaseDate(old) && old.recurrence.copy(anchorEpochDay = proposed.anchorEpochDay) == proposed) old.recurrence else proposed
        save(EventEntity(id, title.trim(), a.toInstant().toEpochMilli(), b.toInstant().toEpochMilli(), z.id,
            allDay, location.trim(), repeat, notes.trim(), old?.revision ?: 0), old == null)
    },
        compactLayout = true,
        saveLabel = "Save event",
        revealOptionsError = titleIssue == null && firstError != null,
    ) {
        Field(
            "Event title", title, !busy,
            prominent = true,
            autoFocus = old == null,
            error = titleIssue,
        ) { title = it }
        FormToggle("All-day event", allDay, !busy) { allDay = it }
        val changeDate: (String) -> Unit = { value ->
            if (lastDate == date) lastDate = value
            date = value
        }
        ScheduleErrorGroup(timingIssue, firstError) {
            DateTimePair(content = {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BillingDateField("Start date", date, !busy, compact = true, change = changeDate)
                    if (!allDay) CalendarTimeField("Start time", startTime, !busy, compact = true) {
                        startTime = it
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BillingDateField(
                        if (allDay) "Last day (inclusive)" else "End date",
                        lastDate, !busy, compact = true,
                    ) { lastDate = it }
                    if (!allDay) CalendarTimeField("End time", endTime, !busy, compact = true) {
                        endTime = it
                    }
                }
            }, largeContent = {
                BillingDateField("Start date", date, !busy, compact = true, change = changeDate)
                if (!allDay) CalendarTimeField("Start time", startTime, !busy, compact = true) {
                    startTime = it
                }
                BillingDateField(
                    if (allDay) "Last day (inclusive)" else "End date",
                    lastDate, !busy, compact = true,
                ) { lastDate = it }
                if (!allDay) CalendarTimeField("End time", endTime, !busy, compact = true) {
                    endTime = it
                }
            })
        }
        MoreOptions(
            optionsSummary(frequency, interval, until, notes, zone, alerts, location),
            validationError = LocalValidateFields.current && advancedIssue != null,
            enabled = !busy,
        ) {
            ScheduleErrorGroup(recurrenceIssue, firstError) {
                RepeatFields(frequency, interval, until, !busy,
                    { frequency = it }, { interval = it }, { until = it })
            }
            ScheduleErrorGroup(locationIssue, firstError) {
                Field("Location (optional)", location, !busy) { location = it }
            }
            ScheduleErrorGroup(notesIssue, firstError) {
                Field("Notes (optional)", notes, !busy, single = false) { notes = it }
            }
            ScheduleErrorGroup(zoneIssue, firstError) {
                TimeZoneOption(zone, !busy) { zone = it }
            }
            NotificationSummary(alerts, openAlerts, !busy)
            if (old != null) Text(
                "Changes apply to the entire series.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ReminderEditor(old: ReminderEntity?, selected: LocalDate, busy: Boolean, error: String?, close: () -> Unit,
    alerts: List<AlertPreferenceEntity> = emptyList(), openAlerts: (() -> Unit)? = null, save: (ReminderEntity, Boolean) -> Unit) {
    val id = rememberSaveable { old?.id ?: UUID.randomUUID().toString() }
    val zoneId = old?.zoneId ?: ZoneId.systemDefault().id
    val base = old?.dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(zoneId)) } ?: selected.atTime(9, 0).atZone(ZoneId.of(zoneId))
    var title by rememberSaveable { mutableStateOf(old?.title.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(old?.notes.orEmpty()) }
    var hasDate by rememberSaveable { mutableStateOf(old == null || old.dueAtMillis != null) }
    var timed by rememberSaveable { mutableStateOf(old?.let { !it.dateOnly } ?: false) }
    var date by rememberSaveable { mutableStateOf(base.toLocalDate().toString()) }
    var time by rememberSaveable { mutableStateOf(base.toLocalTime().withSecond(0).withNano(0).toString()) }
    var zone by rememberSaveable { mutableStateOf(zoneId) }
    var frequency by rememberSaveable { mutableStateOf(old?.recurrence?.frequency?.name ?: "NONE") }
    var interval by rememberSaveable { mutableStateOf((old?.recurrence?.interval ?: 1).toString()) }
    var until by rememberSaveable { mutableStateOf(old?.recurrence?.untilEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()) }

    val titleIssue = when {
        title.isBlank() -> "Title is required"
        title.trim().length > 8192 -> "Title must be 8192 characters or fewer"
        else -> null
    }
    val zoneIssue = if (runCatching { ZoneId.of(zone) }.isFailure) "Choose a valid time zone" else null
    val timingIssue = if (hasDate) runCatching {
        require(LocalDate.parse(date).year in 1..9999)
        if (timed) LocalTime.parse(time)
        Unit
    }.exceptionOrNull()?.let { "Choose a valid due date and optional time" } else null
    val recurrenceIssue = if (hasDate) repeatIssue(frequency, interval, until, date) else null
    val notesIssue = if (notes.trim().length > 8192) "Notes must be 8192 characters or fewer" else null
    val advancedIssue = listOfNotNull(zoneIssue, recurrenceIssue, notesIssue).firstOrNull()
    val firstError = listOfNotNull(
        titleIssue, zoneIssue, timingIssue, recurrenceIssue, notesIssue,
    ).firstOrNull()

    EditorFrame(if (old == null) "New reminder" else "Edit entire reminder series", busy, error, close, {
        require(title.isNotBlank()) { "Title is required" }
        val z = ZoneId.of(zone)
        val day = LocalDate.parse(date)
        val due = when {
            !hasDate -> null
            timed -> day.atTime(LocalTime.parse(time)).atZone(z).toInstant().toEpochMilli()
            else -> day.atStartOfDay(z).toInstant().toEpochMilli()
        }
        val proposed = if (hasDate) recurrence(frequency, interval, until, day) else Recurrence(null)
        val sameDate = !hasDate && old?.dueAtMillis == null || day == old?.let(::reminderBaseDate)
        val repeat = if (old != null && sameDate && old.recurrence.copy(anchorEpochDay = proposed.anchorEpochDay) == proposed) old.recurrence else proposed
        save(ReminderEntity(id, title.trim(), due, old?.completedAtMillis, repeat, notes.trim(), z.id, !timed, old?.revision ?: 0), old == null)
    },
        compactLayout = true,
        saveLabel = "Save reminder",
        revealOptionsError = titleIssue == null && firstError != null,
    ) {
        Field(
            "Reminder title", title, !busy,
            prominent = true,
            autoFocus = old == null,
            error = titleIssue,
        ) { title = it }
        val dirty = LocalMarkDraftDirty.current
        ScheduleErrorGroup(timingIssue, firstError) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    BillingDateField(
                        "Due date (optional)", if (hasDate) date else "",
                        !busy, compact = true,
                    ) {
                        date = it
                        hasDate = true
                    }
                }
                if (hasDate) IconButton(enabled = !busy, onClick = {
                    dirty()
                    hasDate = false
                }) { Icon(AppIcons.Add, "Remove due date", Modifier.rotate(45f)) }
            }
            if (hasDate) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        // A date-only reminder stays untimed until the picker confirms a time.
                        CalendarTimeField(
                            "Time (optional)", if (timed) time else "",
                            !busy, compact = true,
                        ) {
                            time = it
                            timed = true
                        }
                    }
                    if (timed) IconButton(enabled = !busy, onClick = {
                        dirty()
                        timed = false
                    }) { Icon(AppIcons.Add, "Remove due time", Modifier.rotate(45f)) }
                }
            } else Text(
                "No date · no repeats or alerts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoreOptions(
            optionsSummary(
                if (hasDate) frequency else "NONE", interval, until, notes, zone,
                if (hasDate) alerts else emptyList(),
            ),
            validationError = LocalValidateFields.current && advancedIssue != null,
            enabled = !busy,
        ) {
            if (hasDate) ScheduleErrorGroup(recurrenceIssue, firstError) {
                RepeatFields(frequency, interval, until, !busy,
                    { frequency = it }, { interval = it }, { until = it })
            } else Text(
                "Add a due date to repeat this reminder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScheduleErrorGroup(notesIssue, firstError) {
                Field("Notes (optional)", notes, !busy, single = false) { notes = it }
            }
            ScheduleErrorGroup(zoneIssue, firstError) {
                TimeZoneOption(zone, !busy) { zone = it }
            }
            if (hasDate) NotificationSummary(alerts, openAlerts, !busy)
            if (old != null) Text(
                "Changes apply to the entire series. Completed occurrences stay in history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
