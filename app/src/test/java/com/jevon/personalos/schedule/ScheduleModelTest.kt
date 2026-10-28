package com.jevon.personalos.schedule

import com.jevon.personalos.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScheduleModelTest {
    private val zone=ZoneId.of("America/New_York")
    private fun millis(s:String)=LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
    private fun event(start:String="2026-03-07T02:30",end:String="2026-03-07T03:30",r:Frequency?=Frequency.DAILY)=EventEntity("e","Event",millis(start),millis(end),zone.id,recurrence=Recurrence(r,1,LocalDateTime.parse(start).toLocalDate().toEpochDay()))
    @Test fun dstGapMovesForwardAndOverlapUsesEarlierOffset() {
        val gap=eventOccurrence(event(),LocalDate.parse("2026-03-08").toEpochDay(),zone)
        assertEquals(LocalTime.of(3,30),Instant.ofEpochMilli(gap.start).atZone(zone).toLocalTime())
        assertTrue(gap.end>gap.start)
        val fall=event("2026-10-31T01:30","2026-10-31T02:30")
        val overlap=eventOccurrence(fall,LocalDate.parse("2026-11-01").toEpochDay(),zone)
        assertEquals(ZoneOffset.ofHours(-4),Instant.ofEpochMilli(overlap.start).atZone(zone).offset)
    }
    @Test fun allDayDatesStayCalendarDatesAcrossViewingZonesAndSpanDays() {
        val e=event("2026-03-07T00:00","2026-03-10T00:00",null).copy(allDay=true)
        val jakarta=ZoneId.of("Asia/Jakarta")
        val rows=eventOccurrences(e,LocalDate.parse("2026-03-08"),LocalDate.parse("2026-03-09"),jakarta)
        assertEquals(1,rows.size)
        assertEquals(LocalDate.parse("2026-03-07"),Instant.ofEpochMilli(rows.single().start).atZone(jakarta).toLocalDate())
        assertEquals(LocalDate.parse("2026-03-10"),Instant.ofEpochMilli(rows.single().end).atZone(jakarta).toLocalDate())
    }
    @Test fun seekingOldSeriesAndBoundsDoNotExpandFromAnchor() {
        val r=Recurrence(Frequency.DAILY,1,LocalDate.of(1,1,1).toEpochDay())
        assertEquals(LocalDate.of(2026,10,28).toEpochDay(),calendarDateOnOrAfter(r,r.anchorEpochDay,LocalDate.of(2026,10,28).toEpochDay()))
        val rows=eventOccurrences(event(),LocalDate.parse("2026-03-07"),LocalDate.parse("2026-04-07"),zone,3)
        assertEquals(3,rows.size); assertEquals(3,rows.map { it.day }.distinct().size)
    }
    @Test fun completionIsPerRevisionAndOccurrenceAndUndatedIsOneOff() {
        val day=LocalDate.parse("2026-03-07").toEpochDay()
        val r=ReminderEntity("r","Reminder",millis("2026-03-07T09:00"),recurrence=Recurrence(Frequency.DAILY,1,day),zoneId=zone.id)
        val completions=listOf(ReminderCompletionEntity("r",0,day,1))
        val rows=reminderOccurrences(r,completions,LocalDate.ofEpochDay(day),LocalDate.ofEpochDay(day+2),zone)
        assertTrue(rows[0].completed); assertFalse(rows[1].completed)
        assertEquals(day+1,oldestUncompleted(r,completions,zone)!!.day)
        assertFalse(reminderOccurrences(r.copy(revision=1),completions,LocalDate.ofEpochDay(day),LocalDate.ofEpochDay(day+1),zone).single().completed)
        assertNull(oldestUncompleted(ReminderEntity("u","Undated",null),emptyList(),zone)!!.due)
    }
    @Test fun monthEndLeapAndUntilRemainAnchored() {
        val anchor=LocalDate.parse("2024-01-31").toEpochDay()
        val r=Recurrence(Frequency.MONTHLY,1,anchor,LocalDate.parse("2024-03-31").toEpochDay())
        assertEquals(LocalDate.parse("2024-02-29").toEpochDay(),calendarDateOnOrAfter(r,anchor,LocalDate.parse("2024-02-01").toEpochDay()))
        assertEquals(LocalDate.parse("2024-03-31").toEpochDay(),calendarDateOnOrAfter(r,anchor,LocalDate.parse("2024-03-01").toEpochDay()))
        assertNull(calendarDateOnOrAfter(r,anchor,LocalDate.parse("2024-04-01").toEpochDay()))
    }
}
