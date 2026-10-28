package com.jevon.personalos.schedule

import com.jevon.personalos.alerts.*
import com.jevon.personalos.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class AlertPlanTest {
    @Test fun subscriptionDayNoticeKeepsLocalHourAcrossDst() {
        val z=ZoneId.of("America/New_York")
        for(date in listOf(LocalDate.parse("2026-11-02"),LocalDate.parse("2026-03-09"))) {
            val s=SubscriptionEntity("s","Plan",100,"USD",date.toEpochDay(),recurrence=Recurrence(Frequency.MONTHLY,1,date.toEpochDay()))
            val alert=alertForOccurrence(AlertPreferenceEntity("p",subscriptionId="s",leadMinutes=4320,minuteOfDay=540),date.toEpochDay(),AlertCatalog(subscriptions=mapOf("s" to s)),z)!!
            val local=Instant.ofEpochMilli(alert.state.triggerAtMillis).atZone(z)
            assertEquals(date.minusDays(3),local.toLocalDate()); assertEquals(LocalTime.of(9,0),local.toLocalTime())
        }
    }
    private val zone=ZoneId.of("Asia/Jakarta")
    private val clock=Clock.fixed(Instant.parse("2026-10-28T01:00:00Z"),zone)
    private val day=LocalDate.now(clock).toEpochDay()
    @Test fun subscriptionMultipleLeadTimesHaveIndependentStableKeysAndNeverWriteMoney() {
        val s=SubscriptionEntity("s","Plan",10000,"IDR",day+3,recurrence=Recurrence(Frequency.MONTHLY,1,day+3))
        val c=AlertCatalog(subscriptions=mapOf(s.id to s))
        val a=nextAlert(AlertPreferenceEntity("a",subscriptionId="s",leadMinutes=4320),c,clock)!!
        val b=nextAlert(AlertPreferenceEntity("b",subscriptionId="s",leadMinutes=0),c,clock)!!
        assertEquals(Instant.parse("2026-10-28T02:00:00Z").toEpochMilli(),a.state.triggerAtMillis)
        assertNotEquals(a.state.key,b.state.key)
        assertEquals(a.state.key,nextAlert(AlertPreferenceEntity("other-id",subscriptionId="s",leadMinutes=4320),c,clock)!!.state.key)
        assertNull(nextAlert(AlertPreferenceEntity("a",subscriptionId="s",leadMinutes=0),c.copy(subscriptions=mapOf(s.id to s.copy(active=false))),clock))
        val paid=c.copy(payments=listOf(SubscriptionPaymentEntity("p","s",day+3,"entry")))
        assertTrue(nextAlert(AlertPreferenceEntity("a",subscriptionId="s",leadMinutes=0),paid,clock)!!.state.occurrenceEpochDay>day+3)
    }
    @Test fun completedReminderCancelsOnlyItsOccurrenceAndReopenKeepsIdentity() {
        val r=ReminderEntity("r","Reminder",Instant.parse("2026-10-28T02:00:00Z").toEpochMilli(),recurrence=Recurrence(Frequency.DAILY,1,day),zoneId=zone.id)
        val p=AlertPreferenceEntity("p",reminderId="r",leadMinutes=15)
        val c=AlertCatalog(reminders=mapOf("r" to r))
        val first=nextAlert(p,c,clock)!!
        val done=c.copy(completions=listOf(ReminderCompletionEntity("r",0,day,clock.millis())))
        assertNull(alertForOccurrence(p,day,done,zone))
        assertEquals(day+1,nextAlert(p,done,clock)!!.state.occurrenceEpochDay)
        assertEquals(first.state.key,nextAlert(p,c,clock)!!.state.key)
        assertNull(nextAlert(p,c.copy(reminders=mapOf("r" to r.copy(dueAtMillis=null,recurrence=Recurrence(null)))),clock))
    }
    @Test fun eventEditChangesRevisionIdentityAndAllDayUsesLocalAlertTime() {
        val start=LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
        val e=EventEntity("e","All day",start,start+86400000,zone.id,true)
        val p=AlertPreferenceEntity("p",eventId="e",leadMinutes=0,minuteOfDay=600)
        val c=AlertCatalog(events=mapOf("e" to e))
        val first=nextAlert(p,c,clock)!!
        assertEquals(LocalTime.of(10,0),Instant.ofEpochMilli(first.state.triggerAtMillis).atZone(zone).toLocalTime())
        assertNotEquals(first.state.key,nextAlert(p,c.copy(events=mapOf("e" to e.copy(revision=1))),clock)!!.state.key)
        assertNull(nextAlert(p,c.copy(events=emptyMap()),clock))
    }
    @Test fun newPastAlertsAreNotBackfilledAndTimeZoneChangesRecomputeDateAlerts() {
        val r=ReminderEntity("r","Date reminder",LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli(),dateOnly=true,zoneId=zone.id)
        val p=AlertPreferenceEntity("p",reminderId="r",leadMinutes=0,minuteOfDay=540)
        val c=AlertCatalog(reminders=mapOf("r" to r))
        assertNull(nextAlert(p,c,Clock.fixed(Instant.parse("2026-10-28T04:00:00Z"),zone)))
        assertNotEquals(alertForOccurrence(p,day,c,zone)!!.state.triggerAtMillis,alertForOccurrence(p,day,c,ZoneId.of("Europe/London"))!!.state.triggerAtMillis)
    }
}
