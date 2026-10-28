package com.jevon.personalos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.data.*
import com.jevon.personalos.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.io.ByteArrayInputStream

class ScheduleDatabaseTest {
    private val day=LocalDate.parse("2026-10-28").toEpochDay()
    private val instant=Instant.parse("2026-10-28T02:00:00Z").toEpochMilli()
    private fun reminder()=ReminderEntity("r","Daily reminder",instant,recurrence=Recurrence(Frequency.DAILY,1,day),notes="Notes",zoneId="Asia/Jakarta")
    private fun isolated(block:suspend(PersonalDatabase,PersonalDao)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,PersonalDatabase::class.java).build()
        try { block(db,db.personalDao()) } finally { db.close() }
    }
    private suspend fun reject(block:suspend()->Unit) { var failed=false; try {block()} catch(_:Exception){failed=true}; assertTrue(failed) }
    @Test fun eventCrudAndValidation()=isolated { _,dao ->
        val e=EventEntity("e","Event",instant,instant+3600000,"Asia/Jakarta",notes="Notes")
        dao.saveEvent(e,true); dao.saveEvent(e.copy(title="Renamed"),false)
        assertEquals(0L,dao.event("e")!!.revision)
        dao.saveEvent(e.copy(startsAtMillis=instant+1000),false); assertEquals(1L,dao.event("e")!!.revision)
        reject { dao.saveEvent(e.copy(zoneId="Invalid/Zone"),false) }
        dao.saveAlerts("EVENT","e",setOf(0,15,60),540); assertEquals(3,dao.alertPreferences().size)
        dao.deleteEvent("e"); assertNull(dao.event("e")); assertTrue(dao.alertPreferences().isEmpty())
    }
    @Test fun reminderCompletionIsUniquePerOccurrenceAndReopens()=isolated { _,dao ->
        dao.saveReminder(reminder(),true)
        repeat(2) { dao.completeReminder("r",0,day,true,instant) }
        assertEquals(1,dao.completions("r").size); assertNull(dao.reminder("r")!!.completedAtMillis)
        dao.completeReminder("r",0,day+1,true,instant); assertEquals(2,dao.completions("r").size)
        dao.completeReminder("r",0,day,false,instant); assertEquals(day+1,dao.completions("r").single().occurrenceEpochDay)
        dao.deleteReminder("r"); assertTrue(dao.completions("r").isEmpty())
    }
    @Test fun scheduleEditsStartRevisionAndPreservePriorHistory()=isolated { _,dao ->
        dao.saveReminder(reminder(),true); dao.completeReminder("r",0,day,true,instant)
        dao.saveReminder(reminder().copy(title="Renamed"),false); assertEquals(0L,dao.reminder("r")!!.revision)
        dao.saveReminder(reminder().copy(dueAtMillis=instant+86400000,recurrence=Recurrence(Frequency.DAILY,1,day+1)),false)
        assertEquals(1L,dao.reminder("r")!!.revision); assertEquals(0L,dao.completions("r").single().revision)
        reject { dao.completeReminder("r",0,day,true,instant) }
        dao.completeReminder("r",1,day+1,true,instant); assertEquals(2,dao.completions("r").size)
    }
    @Test fun undatedCompletionAndAlertGuards()=isolated { _,dao ->
        dao.saveReminder(ReminderEntity("u","Undated",null),true)
        dao.completeReminder("u",0,null,true,instant); assertEquals(instant,dao.reminder("u")!!.completedAtMillis)
        dao.completeReminder("u",0,null,false,instant); assertNull(dao.reminder("u")!!.completedAtMillis)
        reject { dao.saveAlerts("REMINDER","u",setOf(0),540) }
        reject { dao.saveReminder(ReminderEntity("bad","Repeat undated",null,recurrence=Recurrence(Frequency.WEEKLY)),true) }
    }
    @Test fun sharedPreferencesAndSchedulingHistorySurviveReplacement()=isolated { db,dao ->
        dao.saveReminder(reminder(),true); dao.completeReminder("r",0,day,true,instant)
        dao.saveAlerts("REMINDER","r",setOf(0,15,1440),600)
        val store=BackupStore(db); val before=store.snapshot(); val bytes=BackupCodec.encode(before,"phase3")
        dao.deleteReminder("r"); store.replace(BackupCodec.decode(ByteArrayInputStream(bytes)))
        assertEquals(before,store.snapshot())
        reject { store.replace(PreparedBackup(before.copy(completions=before.completions+before.completions),"","")) }
        assertEquals(before,store.snapshot())
        dao.saveReminder(dao.reminder("r")!!.copy(dueAtMillis=null,recurrence=Recurrence(null)),false)
        assertTrue(dao.alertPreferences().isEmpty())
    }
}
