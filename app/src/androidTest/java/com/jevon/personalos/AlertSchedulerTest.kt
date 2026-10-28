package com.jevon.personalos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.data.*
import com.jevon.personalos.alerts.*
import com.jevon.personalos.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.io.ByteArrayInputStream

class AlertSchedulerTest {
    private val zone=ZoneId.of("Asia/Jakarta")
    private val now=Instant.parse("2026-10-28T01:00:00Z")
    private val day=LocalDate.of(2026,10,28).toEpochDay()
    private fun isolated(test:suspend(PersonalDatabase,PersonalDao,LocalAlerts)->Unit)=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,PersonalDatabase::class.java).build()
        val alerts=LocalAlerts(context,db,"test-${java.util.UUID.randomUUID()}",clock=Clock.fixed(now,zone),deliveryAllowed={false})
        try{test(db,db.personalDao(),alerts)}finally{alerts.cancelAlarm();db.close()}
    }
    @Test fun deniedPermissionQueuesWithoutDeliveringAndReconcileIsIdempotent()=isolated {_,dao,alerts->
        dao.saveReminder(ReminderEntity("r","Test",now.plusSeconds(3600).toEpochMilli(),zoneId=zone.id),true)
        dao.saveAlerts("REMINDER","r",setOf(0,15),540)
        repeat(2){alerts.reconcile()}
        assertFalse(alerts.enabled("REMINDER")); assertNull(alerts.nextScheduledAt); assertEquals(2,dao.pendingAlerts().size)
        assertEquals(2,dao.pendingAlerts().map{it.key}.distinct().size); assertNull(alerts.lastError)
    }
    @Test fun editingCompletionReopeningAndDeletionRebuildCorrectNextKeys()=isolated {_,dao,alerts->
        val r=ReminderEntity("r","Test",now.plusSeconds(3600).toEpochMilli(),recurrence=Recurrence(Frequency.DAILY,1,day),zoneId=zone.id)
        dao.saveReminder(r,true);dao.saveAlerts("REMINDER","r",setOf(0),540);alerts.reconcile()
        val first=dao.pendingAlerts().single()
        dao.completeReminder("r",0,day,true,now.toEpochMilli());alerts.reconcile()
        assertEquals(day+1,dao.pendingAlerts().single().occurrenceEpochDay)
        dao.completeReminder("r",0,day,false,now.toEpochMilli());alerts.reconcile()
        assertEquals(first.key,dao.pendingAlerts().single().key)
        dao.saveReminder(r.copy(dueAtMillis=r.dueAtMillis!!+60000),false);alerts.reconcile()
        assertNotEquals(first.key,dao.pendingAlerts().single().key)
        dao.deleteReminder("r");alerts.reconcile();assertTrue(dao.pendingAlerts().isEmpty())
    }
    @Test fun restartAndSimulatedRecoveryRebuildOneGlobalScheduleAndRestoreDiscardsRuntime()=isolated {db,dao,alerts->
        dao.saveEvent(EventEntity("e","Test event",now.plusSeconds(3600).toEpochMilli(),now.plusSeconds(7200).toEpochMilli(),zone.id),true)
        dao.saveAlerts("EVENT","e",setOf(0,30),540);alerts.reconcile()
        val before=dao.pendingAlerts()
        val restarted=LocalAlerts(InstrumentationRegistry.getInstrumentation().targetContext,db,"recovery-simulation",clock=Clock.fixed(now,zone),deliveryAllowed={false})
        try{restarted.reconcile();assertEquals(before,dao.pendingAlerts())}finally{restarted.cancelAlarm()}
        dao.saveAlertState(before.first().copy(delivered=true))
        val store=BackupStore(db);val payload=BackupCodec.encode(store.snapshot(),"test")
        store.replace(BackupCodec.decode(ByteArrayInputStream(payload)))
        assertNull(dao.alertState(before.first().key));alerts.reconcile();assertEquals(2,dao.pendingAlerts().size)
    }
    @Test fun inactiveSubscriptionsAndPaidRenewalsDoNotKeepTheirOldAlerts()=isolated {_,dao,alerts->
        val s=SubscriptionEntity("s","Test plan",100,"USD",day+3,recurrence=Recurrence(Frequency.MONTHLY,1,day+3))
        dao.saveSubscription(s,true);dao.saveAlerts("SUBSCRIPTION","s",setOf(4320,0),540);alerts.reconcile();assertEquals(2,dao.pendingAlerts().size)
        dao.setSubscriptionActive("s",false);alerts.reconcile();assertTrue(dao.pendingAlerts().isEmpty())
        dao.setSubscriptionActive("s",true);alerts.reconcile();assertEquals(2,dao.pendingAlerts().size)
    }
}
