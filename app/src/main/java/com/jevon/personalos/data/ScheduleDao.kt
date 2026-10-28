package com.jevon.personalos.data

import androidx.room.*
import com.jevon.personalos.schedule.*
import java.time.*

@Dao
abstract class ScheduleDao : SubscriptionDao() {
    @Query("SELECT * FROM events WHERE startsAtMillis<:end AND (endsAtMillis>:start OR (repeat_frequency IS NOT NULL AND (repeat_untilEpochDay IS NULL OR repeat_untilEpochDay+((endsAtMillis-startsAtMillis)/86400000)+2>=:fromDay))) ORDER BY startsAtMillis,id LIMIT 1001")
    abstract suspend fun eventCandidates(start: Long, end: Long,fromDay:Long=Long.MIN_VALUE): List<EventEntity>
    @Query("SELECT * FROM reminders WHERE (dueAtMillis>=:start AND dueAtMillis<:end) OR (repeat_frequency IS NOT NULL AND dueAtMillis<:end AND (repeat_untilEpochDay IS NULL OR repeat_untilEpochDay>=:fromDay)) ORDER BY dueAtMillis,id LIMIT 1001")
    abstract suspend fun reminderCandidates(start:Long,end: Long,fromDay:Long): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE repeat_frequency IS NULL AND completedAtMillis IS NULL AND dueAtMillis<:before ORDER BY dueAtMillis,id LIMIT 21")
    abstract suspend fun overdueReminderCandidates(before:Long):List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE dueAtMillis IS NULL ORDER BY completedAtMillis IS NOT NULL,title,id LIMIT 101")
    abstract suspend fun undatedReminderCandidates():List<ReminderEntity>
    @Query("SELECT * FROM events WHERE id=:id") abstract suspend fun event(id: String): EventEntity?
    @Query("SELECT * FROM reminders WHERE id=:id") abstract suspend fun reminder(id: String): ReminderEntity?
    @Query("SELECT * FROM reminder_completions WHERE reminderId=:id") abstract suspend fun completions(id: String): List<ReminderCompletionEntity>
    @Insert protected abstract suspend fun insertEvent(value: EventEntity)
    @Update protected abstract suspend fun updateEvent(value: EventEntity): Int
    @Insert protected abstract suspend fun insertReminder(value: ReminderEntity)
    @Update protected abstract suspend fun updateReminder(value: ReminderEntity): Int
    @Query("DELETE FROM events WHERE id=:id") abstract suspend fun deleteEvent(id: String)
    @Query("DELETE FROM reminders WHERE id=:id") abstract suspend fun deleteReminder(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertCompletion(value: ReminderCompletionEntity)
    @Query("DELETE FROM reminder_completions WHERE reminderId=:id AND revision=:revision AND occurrenceEpochDay=:day")
    protected abstract suspend fun removeCompletion(id: String, revision: Long, day: Long)
    @Query("SELECT * FROM alert_preferences ORDER BY id LIMIT 5001") abstract suspend fun alertPreferences(): List<AlertPreferenceEntity>
    @Query("DELETE FROM alert_preferences WHERE (:domain='EVENT' AND eventId=:id) OR (:domain='REMINDER' AND reminderId=:id) OR (:domain='SUBSCRIPTION' AND subscriptionId=:id)")
    protected abstract suspend fun clearAlerts(domain: String, id: String)
    @Insert protected abstract suspend fun insertAlerts(values: List<AlertPreferenceEntity>)
    @Query("SELECT * FROM alert_state WHERE delivered=0") abstract suspend fun pendingAlerts(): List<AlertStateEntity>
    @Query("SELECT delivered FROM alert_state WHERE `key`=:key") abstract suspend fun alertDelivered(key: String): Boolean?
    @Query("SELECT * FROM alert_state WHERE `key`=:key") abstract suspend fun alertState(key: String): AlertStateEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveAlertState(value: AlertStateEntity)
    @Query("DELETE FROM alert_state WHERE delivered=0") abstract suspend fun clearPendingAlerts()

    private fun text(id: String, title: String, notes: String) {
        require(id.isNotBlank() && id.length <= 200 && title.isNotBlank()) { "Title is required" }
        require(title.length <= 8192 && notes.length <= 8192) { "Text is too long" }
    }
    private fun recurrence(value: Recurrence, base: LocalDate?) {
        require(value.interval in 1..1000) { "Use an interval from 1 to 1000" }
        require(base != null || value.frequency == null) { "Repeating reminders need a due date" }
        if (base != null) require(base.year in 1..9999) { "Choose a date from year 1 to 9999" }
        value.untilEpochDay?.let { require(base != null && it >= base.toEpochDay()) { "Repeat end must not precede the first date" }; LocalDate.ofEpochDay(it) }
        LocalDate.ofEpochDay(value.anchorEpochDay)
    }
    @Transaction open suspend fun saveEvent(value: EventEntity, create: Boolean) {
        text(value.id,value.title,value.notes); require(value.location.length <= 8192)
        ZoneId.of(value.zoneId); require(value.endsAtMillis > value.startsAtMillis) { "End must follow start" }
        val base = eventBaseDate(value); recurrence(value.recurrence,base)
        require(Duration.ofMillis(Math.subtractExact(value.endsAtMillis,value.startsAtMillis)).toDays() <= 3660) { "Events can span up to ten years" }
        val old = if (create) null else requireNotNull(event(value.id)) { "Event no longer exists" }
        val changed = old != null && (old.startsAtMillis != value.startsAtMillis || old.endsAtMillis != value.endsAtMillis || old.zoneId != value.zoneId || old.allDay != value.allDay || old.recurrence != value.recurrence)
        val saved = value.copy(revision = if (changed) Math.addExact(old!!.revision,1) else old?.revision ?: 0)
        if (create) insertEvent(saved) else check(updateEvent(saved)==1)
    }
    @Transaction open suspend fun saveReminder(value: ReminderEntity, create: Boolean) {
        text(value.id,value.title,value.notes); ZoneId.of(value.zoneId)
        recurrence(value.recurrence,reminderBaseDate(value))
        val old = if (create) null else requireNotNull(reminder(value.id)) { "Reminder no longer exists" }
        val changed = old != null && (old.dueAtMillis != value.dueAtMillis || old.zoneId != value.zoneId || old.dateOnly != value.dateOnly || old.recurrence != value.recurrence)
        if(changed)legacyCompletionDay(old!!)?.let {insertCompletion(ReminderCompletionEntity(old.id,old.revision,it,requireNotNull(old.completedAtMillis)))}
        val saved = value.copy(revision = if (changed) Math.addExact(old!!.revision,1) else old?.revision ?: 0,
            completedAtMillis = if(value.recurrence.frequency!=null){if(changed)null else old?.completedAtMillis}else old?.takeIf{it.recurrence.frequency==null}?.completedAtMillis)
        if (create) insertReminder(saved) else check(updateReminder(saved)==1)
        if(saved.dueAtMillis==null) clearAlerts("REMINDER",saved.id)
    }
    @Transaction open suspend fun completeReminder(id: String, revision: Long, day: Long?, complete: Boolean, nowMillis: Long) {
        val value = requireNotNull(reminder(id)) { "Reminder no longer exists" }
        require(value.revision == revision) { "This series changed. Open its current occurrence" }
        if (value.recurrence.frequency == null) {
            require(day==reminderBaseDate(value)?.toEpochDay()) { "This reminder date changed. Open its current occurrence" }
            check(updateReminder(value.copy(completedAtMillis = if (complete) value.completedAtMillis ?: nowMillis else null))==1)
        } else {
            val base = requireNotNull(reminderBaseDate(value)).toEpochDay()
            require(day != null && calendarDateOnOrAfter(value.recurrence,base,day)==day) { "Choose an occurrence in the current series" }
            if(!complete && day==legacyCompletionDay(value))check(updateReminder(value.copy(completedAtMillis=null))==1)
            if (complete) insertCompletion(ReminderCompletionEntity(id,revision,day,nowMillis)) else removeCompletion(id,revision,day)
        }
    }
    @Transaction open suspend fun saveAlerts(domain: String, id: String, leads: Set<Int>, minuteOfDay: Int) {
        require(leads.size<=6 && leads.all { it in 0..20160 } && minuteOfDay in 0..1439) { "Choose up to six alerts within fourteen days" }
        when(domain) {
            "EVENT" -> requireNotNull(event(id)) { "Event no longer exists" }
            "REMINDER" -> { val r=requireNotNull(reminder(id)); require(leads.isEmpty() || r.dueAtMillis!=null) { "Undated reminders cannot send alerts" } }
            "SUBSCRIPTION" -> { requireNotNull(subscription(id)); require(leads.all { it in setOf(0,1440,4320,10080,20160) }) { "Choose subscription day presets" } }
            else -> throw IllegalArgumentException("Unknown alert type")
        }
        clearAlerts(domain,id)
        insertAlerts(leads.sorted().map { lead -> AlertPreferenceEntity(java.util.UUID.randomUUID().toString(), if(domain=="EVENT")id else null, if(domain=="REMINDER")id else null, if(domain=="SUBSCRIPTION")id else null,lead,minuteOfDay) })
    }
}
