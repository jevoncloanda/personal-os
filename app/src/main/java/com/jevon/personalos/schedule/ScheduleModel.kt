package com.jevon.personalos.schedule

import com.jevon.personalos.data.*
import com.jevon.personalos.today.occurrenceDate
import java.time.*
import java.time.temporal.ChronoUnit

/** Seek into a calendar series in constant work; no expansion from its original year. */
fun calendarDateOnOrAfter(r: Recurrence, baseDay: Long, requested: Long): Long? {
    if (r.frequency == null) return baseDay.takeIf { it >= requested && (r.untilEpochDay == null || it <= r.untilEpochDay) }
    val anchor = LocalDate.ofEpochDay(r.anchorEpochDay)
    val target = LocalDate.ofEpochDay(maxOf(requested, baseDay, r.anchorEpochDay))
    val units = when (r.frequency) {
        Frequency.DAILY -> ChronoUnit.DAYS.between(anchor, target)
        Frequency.WEEKLY -> ChronoUnit.WEEKS.between(anchor, target)
        Frequency.MONTHLY -> ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), target.withDayOfMonth(1))
        Frequency.YEARLY -> (target.year - anchor.year).toLong()
    }
    return try {
        var index = (units / r.interval).coerceAtLeast(0)
        var date = occurrenceDate(anchor, r.frequency, r.interval, index)
        if (date < target) date = occurrenceDate(anchor, r.frequency, r.interval, ++index)
        date.toEpochDay().takeIf { date.year in 1..9999 && (r.untilEpochDay == null || it <= r.untilEpochDay) }
    } catch (_: DateTimeException) { null } catch (_: ArithmeticException) { null }
}

data class EventOccurrence(val event: EventEntity, val day: Long, val start: Long, val end: Long)
data class ReminderOccurrence(val reminder: ReminderEntity, val day: Long?, val due: Long?, val completed: Boolean) {
    val key get() = "${reminder.id}:${reminder.revision}:${day ?: "undated"}"
}

fun eventBaseDate(event: EventEntity): LocalDate = Instant.ofEpochMilli(event.startsAtMillis).atZone(ZoneId.of(event.zoneId)).toLocalDate()
fun reminderBaseDate(reminder: ReminderEntity): LocalDate? = reminder.dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(reminder.zoneId)).toLocalDate() }
/** Old backups could mark a repeating template complete. Interpret that as its first occurrence only. */
fun legacyCompletionDay(reminder:ReminderEntity):Long? {
    if(reminder.recurrence.frequency==null || reminder.completedAtMillis==null)return null
    val base=reminderBaseDate(reminder)?.toEpochDay()?:return null
    return calendarDateOnOrAfter(reminder.recurrence,base,base)
}

fun eventOccurrence(event: EventEntity, day: Long, viewingZone: ZoneId): EventOccurrence {
    val zone = ZoneId.of(event.zoneId)
    val start = Instant.ofEpochMilli(event.startsAtMillis).atZone(zone).toLocalDateTime()
    val end = Instant.ofEpochMilli(event.endsAtMillis).atZone(zone).toLocalDateTime()
    val date = LocalDate.ofEpochDay(day)
    // atZone: gaps shift forward by the gap, overlaps choose the earlier offset.
    val a = if (event.allDay) date.atStartOfDay(viewingZone) else date.atTime(start.toLocalTime()).atZone(zone)
    val b = if (event.allDay) date.plusDays(ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()).coerceAtLeast(1)).atStartOfDay(viewingZone)
        else date.atTime(start.toLocalTime()).plus(Duration.between(start, end)).atZone(zone)
    val finish = b.toInstant().toEpochMilli().takeIf { it > a.toInstant().toEpochMilli() }
        ?: Math.addExact(a.toInstant().toEpochMilli(),Duration.between(start,end).toMillis().coerceAtLeast(1))
    return EventOccurrence(event, day, a.toInstant().toEpochMilli(), finish)
}

fun reminderOccurrence(reminder: ReminderEntity, day: Long?, completions: Set<Pair<Long, Long>>, viewingZone: ZoneId): ReminderOccurrence {
    val zone = ZoneId.of(reminder.zoneId)
    val due = if (day == null || reminder.dueAtMillis == null) null else {
        val date = LocalDate.ofEpochDay(day)
        if (reminder.dateOnly) date.atStartOfDay(viewingZone).toInstant().toEpochMilli()
        else date.atTime(Instant.ofEpochMilli(reminder.dueAtMillis).atZone(zone).toLocalTime()).atZone(zone).toInstant().toEpochMilli()
    }
    val complete = if (reminder.recurrence.frequency == null) reminder.completedAtMillis != null else (reminder.revision to day) in completions || day==legacyCompletionDay(reminder)
    return ReminderOccurrence(reminder, day, due, complete)
}

fun eventOccurrences(event: EventEntity, from: LocalDate, until: LocalDate, zone: ZoneId, limit: Int = 128): List<EventOccurrence> {
    require(limit in 1..512 && until >= from && ChronoUnit.DAYS.between(from, until) <= 366)
    val base = eventBaseDate(event)
    val durationDays = Duration.ofMillis(event.endsAtMillis - event.startsAtMillis).toDays().coerceAtMost(3660) + 2
    var day = calendarDateOnOrAfter(event.recurrence, base.toEpochDay(), from.minusDays(durationDays).toEpochDay())
    val result = ArrayList<EventOccurrence>()
    val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
    val untilMillis = until.atStartOfDay(zone).toInstant().toEpochMilli()
    var visited = 0
    while (day != null && day < until.plusDays(2).toEpochDay() && result.size < limit && visited++ < 4096) {
        val occurrence = eventOccurrence(event, day, zone)
        if (occurrence.end > fromMillis && occurrence.start < untilMillis) result += occurrence
        day = calendarDateOnOrAfter(event.recurrence, base.toEpochDay(), day + 1)
    }
    return result
}

fun reminderOccurrences(reminder: ReminderEntity, completions: List<ReminderCompletionEntity>, from: LocalDate, until: LocalDate, zone: ZoneId, limit: Int = 128): List<ReminderOccurrence> {
    require(limit in 1..512 && until >= from && ChronoUnit.DAYS.between(from, until) <= 366)
    val base = reminderBaseDate(reminder) ?: return listOf(reminderOccurrence(reminder, null, emptySet(), zone))
    val paid = completions.map { it.revision to it.occurrenceEpochDay }.toSet()
    var day = calendarDateOnOrAfter(reminder.recurrence, base.toEpochDay(), from.minusDays(2).toEpochDay())
    val result = ArrayList<ReminderOccurrence>()
    while (day != null && day < until.plusDays(2).toEpochDay() && result.size < limit) {
        val item = reminderOccurrence(reminder, day, paid, zone)
        val localDay = if (reminder.dateOnly) day else Instant.ofEpochMilli(requireNotNull(item.due)).atZone(zone).toLocalDate().toEpochDay()
        if (localDay >= from.toEpochDay() && localDay < until.toEpochDay()) result += item
        day = calendarDateOnOrAfter(reminder.recurrence, base.toEpochDay(), day + 1)
    }
    return result
}

fun oldestUncompleted(reminder: ReminderEntity, completions: List<ReminderCompletionEntity>, zone: ZoneId): ReminderOccurrence? {
    if (reminder.recurrence.frequency == null) return reminderOccurrence(reminder, reminderBaseDate(reminder)?.toEpochDay(), emptySet(), zone).takeUnless { it.completed }
    val base = reminderBaseDate(reminder) ?: return null
    val completed = completions.filter { it.revision == reminder.revision }.map { it.occurrenceEpochDay }.toSet()+listOfNotNull(legacyCompletionDay(reminder))
    var day = calendarDateOnOrAfter(reminder.recurrence, base.toEpochDay(), base.toEpochDay())
    repeat(completed.size + 1) {
        val current = day ?: return null
        if (current !in completed) return reminderOccurrence(reminder, current, emptySet(), zone)
        day = calendarDateOnOrAfter(reminder.recurrence, base.toEpochDay(), current + 1)
    }
    return null
}
