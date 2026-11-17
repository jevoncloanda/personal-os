package com.jevon.personalos.ui

import androidx.compose.foundation.layout.*
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
private fun RepeatFields(frequency: String, interval: String, until: String, enabled: Boolean,
    setFrequency: (String) -> Unit, setInterval: (String) -> Unit, setUntil: (String) -> Unit) {
    Choice("Repeat", frequency, listOf("NONE" to "Does not repeat", "DAILY" to "Daily", "WEEKLY" to "Weekly",
        "MONTHLY" to "Monthly", "YEARLY" to "Yearly"), enabled, choose = setFrequency)
    if (frequency != "NONE") {
        Field("Repeat interval", interval, enabled, KeyboardType.Number, change = setInterval)
        BillingDateField("Repeat through (optional)", until, enabled, change = setUntil)
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
        Field("Time zone", zone, enabled,
            error = if (runCatching { ZoneId.of(zone) }.isFailure) "Use a valid time zone, such as Asia/Jakarta." else null,
            change = change)
        TextButton(enabled = enabled, onClick = { dirty(); change(device); override = false }) { Text("Use device time zone") }
    } else TextButton(enabled = enabled, onClick = { override = true }) { Text("Time zone · $device") }
}

private fun optionsSummary(frequency: String, interval: String, until: String, notes: String, zone: String,
    alerts: List<AlertPreferenceEntity>, location: String = ""): String {
    val repeat = runCatching { recurrence(frequency, interval, until, LocalDate.ofEpochDay(0)) }.getOrNull()
    return buildList {
        if (frequency != "NONE") add(repeat?.let(::repeatLabel) ?: "Repeat needs attention")
        if (notes.isNotBlank()) add("Notes added")
        if (location.isNotBlank()) add("Location added")
        if (zone != ZoneId.systemDefault().id) add(zone)
        if (alerts.isNotEmpty()) add("${alerts.size} alerts")
    }.joinToString(" · ").ifBlank { "Repeat, notes and time zone" }
}

@Composable
private fun NotificationSummary(alerts: List<AlertPreferenceEntity>, open: (() -> Unit)?) {
    Text(if (alerts.isEmpty()) "Notifications off" else "${alerts.size} notification${if (alerts.size == 1) "" else "s"} configured",
        style = MaterialTheme.typography.titleSmall)
    if (open == null) Text("Set notification timing after saving.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    else {
        TextButton(enabled = !LocalEditorDirty.current, onClick = open) { Text("Notification timing") }
        if (LocalEditorDirty.current) Text("Save changes before editing notification timing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DateTimePair(content: @Composable RowScope.() -> Unit, largeContent: @Composable ColumnScope.() -> Unit) {
    if (LocalDensity.current.fontScale > 1.15f) Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = largeContent)
    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
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
    }) {
        Field("Event title", title, !busy, prominent = true, error = if (title.isBlank()) "Title is required" else null) { title = it }
        FormToggle("All-day event", allDay, !busy) { allDay = it }
        val changeDate: (String) -> Unit = { value -> if (lastDate == date) lastDate = value; date = value }
        DateTimePair(content = {
            Box(Modifier.weight(1f)) { BillingDateField("Start date", date, !busy, compact = true, change = changeDate) }
            Box(Modifier.weight(1f)) { BillingDateField(if (allDay) "Last day (inclusive)" else "End date", lastDate, !busy, compact = true) { lastDate = it } }
        }, largeContent = {
            BillingDateField("Start date", date, !busy, compact = true, change = changeDate)
            BillingDateField(if (allDay) "Last day (inclusive)" else "End date", lastDate, !busy, compact = true) { lastDate = it }
        })
        if (!allDay) DateTimePair(content = {
            Box(Modifier.weight(1f)) { CalendarTimeField("Start time", startTime, !busy, compact = true) { startTime = it } }
            Box(Modifier.weight(1f)) { CalendarTimeField("End time", endTime, !busy, compact = true) { endTime = it } }
        }, largeContent = {
            CalendarTimeField("Start time", startTime, !busy, compact = true) { startTime = it }
            CalendarTimeField("End time", endTime, !busy, compact = true) { endTime = it }
        })
        MoreOptions(optionsSummary(frequency, interval, until, notes, zone, alerts, location)) {
            RepeatFields(frequency, interval, until, !busy, { frequency = it }, { interval = it }, { until = it })
            Field("Location (optional)", location, !busy) { location = it }
            Field("Notes (optional)", notes, !busy, single = false) { notes = it }
            TimeZoneOption(zone, !busy) { zone = it }
            NotificationSummary(alerts, openAlerts)
            if (old != null) Text("Changes apply to the entire series.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    }) {
        Field("Reminder title", title, !busy, prominent = true, error = if (title.isBlank()) "Title is required" else null) { title = it }
        val dirty = LocalMarkDraftDirty.current
        if (hasDate) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f)) { BillingDateField("Due date", date, !busy, compact = true) { date = it } }
                IconButton(enabled = !busy, onClick = { dirty(); hasDate = false }) { Icon(AppIcons.Add, "Remove due date", Modifier.rotate(45f)) }
            }
            if (timed) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f)) { CalendarTimeField("Due time", time, !busy, compact = true) { time = it } }
                IconButton(enabled = !busy, onClick = { dirty(); timed = false }) { Icon(AppIcons.Add, "Remove due time", Modifier.rotate(45f)) }
            } else TextButton(enabled = !busy, onClick = { dirty(); timed = true }) { Icon(AppIcons.Add, null, Modifier.size(18.dp)); Text("Add due time", Modifier.padding(start = 8.dp)) }
        } else {
            TextButton(enabled = !busy, onClick = { dirty(); hasDate = true }) { Icon(AppIcons.CalendarMonth, null, Modifier.size(18.dp)); Text("Add due date", Modifier.padding(start = 8.dp)) }
            Text("No date · no repeats or alerts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MoreOptions(optionsSummary(if (hasDate) frequency else "NONE", interval, until, notes, zone, if (hasDate) alerts else emptyList())) {
            if (hasDate) RepeatFields(frequency, interval, until, !busy, { frequency = it }, { interval = it }, { until = it })
            else Text("Add a due date to repeat this reminder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Field("Notes (optional)", notes, !busy, single = false) { notes = it }
            TimeZoneOption(zone, !busy) { zone = it }
            if (hasDate) NotificationSummary(alerts, openAlerts)
            if (old != null) Text("Changes apply to the entire series. Completed occurrences stay in history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
