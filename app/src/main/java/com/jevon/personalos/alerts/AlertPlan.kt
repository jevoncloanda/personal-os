package com.jevon.personalos.alerts

import com.jevon.personalos.data.*
import com.jevon.personalos.schedule.*
import com.jevon.personalos.subscriptions.nextUnpaid
import com.jevon.personalos.today.formatMoney
import java.time.*
import java.security.MessageDigest

data class AlertCatalog(val events: Map<String,EventEntity> = emptyMap(), val reminders: Map<String,ReminderEntity> = emptyMap(),
    val subscriptions: Map<String,SubscriptionEntity> = emptyMap(), val completions: List<ReminderCompletionEntity> = emptyList(), val payments: List<SubscriptionPaymentEntity> = emptyList())
data class PlannedAlert(val state: AlertStateEntity,val title: String,val body: String)
val AlertPreferenceEntity.domain get() = when {eventId!=null->"EVENT"; reminderId!=null->"REMINDER"; else->"SUBSCRIPTION"}
val AlertPreferenceEntity.recordId get() = requireNotNull(eventId?:reminderId?:subscriptionId)

/** Reconstruct any queued occurrence from current source records before delivery. */
fun alertForOccurrence(pref: AlertPreferenceEntity,day: Long,catalog: AlertCatalog,zone: ZoneId): PlannedAlert? {
    val date=LocalDate.ofEpochDay(day)
    val title:String; val revision:Long; val baseMillis:Long; val calendarBased:Boolean
    when(pref.domain) {
        "EVENT" -> {
            val e=catalog.events[pref.recordId]?:return null
            if(calendarDateOnOrAfter(e.recurrence,eventBaseDate(e).toEpochDay(),day)!=day)return null
            title=e.title; revision=e.revision
            calendarBased=e.allDay
            baseMillis=if(e.allDay)date.atTime(LocalTime.ofSecondOfDay(pref.minuteOfDay*60L)).atZone(zone).toInstant().toEpochMilli() else eventOccurrence(e,day,zone).start
        }
        "REMINDER" -> {
            val r=catalog.reminders[pref.recordId]?:return null
            val base=reminderBaseDate(r)?.toEpochDay()?:return null
            if(calendarDateOnOrAfter(r.recurrence,base,day)!=day)return null
            val done=catalog.completions.filter {it.reminderId==r.id}.map {it.revision to it.occurrenceEpochDay}.toSet()
            val occurrence=reminderOccurrence(r,day,done,zone)
            if(occurrence.completed)return null
            title=r.title; revision=r.revision
            calendarBased=r.dateOnly
            baseMillis=if(r.dateOnly)date.atTime(LocalTime.ofSecondOfDay(pref.minuteOfDay*60L)).atZone(zone).toInstant().toEpochMilli() else requireNotNull(occurrence.due)
        }
        else -> {
            val s=catalog.subscriptions[pref.recordId]?:return null
            val paid=catalog.payments.filter {it.subscriptionId==s.id}.map {it.dueEpochDay}.toSet()
            if(!s.active || nextUnpaid(s,paid,day)!=day)return null
            title=s.name; revision=0
            calendarBased=true
            baseMillis=date.atTime(LocalTime.ofSecondOfDay(pref.minuteOfDay*60L)).atZone(zone).toInstant().toEpochMilli()
        }
    }
    val at=if(calendarBased)date.minusDays(pref.leadMinutes/1440L).atTime(LocalTime.ofSecondOfDay(pref.minuteOfDay*60L)).atZone(zone).minusMinutes((pref.leadMinutes%1440).toLong()).toInstant().toEpochMilli()
        else Math.subtractExact(baseMillis,pref.leadMinutes*60_000L)
    val raw="${pref.domain}:${pref.recordId.length}:${pref.recordId}:$revision:$day:${pref.leadMinutes}"
    val key=MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    val type=when(pref.domain){"EVENT"->"Event";"REMINDER"->"Reminder";else->"Payment forecast"}
    val money=catalog.subscriptions[pref.recordId]?.takeIf {pref.domain=="SUBSCRIPTION"}?.let {" · ${formatMoney(it.amountMinor,it.currencyCode)}"}.orEmpty()
    return PlannedAlert(AlertStateEntity(key,pref.domain,pref.recordId,revision,day,pref.leadMinutes,at),title,"$type · $date$money")
}

/** One future alert per preference, with direct calendar seeking and a bounded skip of paid/done dates. */
fun nextAlert(pref: AlertPreferenceEntity,catalog: AlertCatalog,clock: Clock): PlannedAlert? {
    val zone=clock.zone
    val today=LocalDate.now(clock)
    val from=today.plusDays(pref.leadMinutes/1440L).minusDays(2).toEpochDay()
    val recurrence:Recurrence; val base:Long
    when(pref.domain) {
        "EVENT"->{val e=catalog.events[pref.recordId]?:return null; recurrence=e.recurrence; base=eventBaseDate(e).toEpochDay()}
        "REMINDER"->{val r=catalog.reminders[pref.recordId]?:return null; recurrence=r.recurrence; base=reminderBaseDate(r)?.toEpochDay()?:return null}
        else->{val s=catalog.subscriptions[pref.recordId]?:return null; if(!s.active)return null; recurrence=s.recurrence; base=s.nextDueEpochDay}
    }
    var day=calendarDateOnOrAfter(recurrence,base,maxOf(from,base))
    val skipLimit=catalog.completions.size+catalog.payments.size+10
    repeat(skipLimit) {
        val current=day?:return null
        val candidate=alertForOccurrence(pref,current,catalog,zone)
        if(candidate!=null && candidate.state.triggerAtMillis>=clock.millis())return candidate
        day=calendarDateOnOrAfter(recurrence,base,current+1)
    }
    return null
}
