package com.jevon.personalos

import android.app.NotificationManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.jevon.personalos.alerts.*
import com.jevon.personalos.backup.BackupStore
import com.jevon.personalos.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.*
import java.util.UUID

/** Sends the contentIntent attached by production LocalAlerts, not a reconstructed Intent.
 * This proves shipping Activity routing, not a notification-shade finger tap or alarm timing.
 */
class NotificationRoutingTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun postedProductionPendingIntentsRouteExactOwnersAndOccurrenceAndCancelObsoleteAlerts() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val context=instrument.targetContext
        val app=context.applicationContext as PersonalOsApplication
        val before=runBlocking{BackupStore(app.database).snapshot()}
        val namespace=UUID.randomUUID().toString()
        val db=Room.inMemoryDatabaseBuilder(context,PersonalDatabase::class.java).build()
        val dao=db.personalDao()
        val manager=context.getSystemService(NotificationManager::class.java)
        val now=ZonedDateTime.now().withSecond(0).withNano(0)
        val clock=Clock.fixed(now.toInstant(),now.zone)
        val delivery=LocalAlerts(context,db,namespace,clock=clock)
        fun active()=manager.activeNotifications.filter{it.tag?.startsWith("$namespace:")==true}
        fun main():MainActivity? {
            var found:MainActivity?=null
            instrument.runOnMainSync {found=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()}
            return found
        }
        try {
            val due=now.minusMinutes(2)
            val day=due.toLocalDate().toEpochDay()
            val e=EventEntity("$namespace-event","Isolated notification event",due.toInstant().toEpochMilli(),due.plusHours(1).toInstant().toEpochMilli(),now.zone.id)
            val r=ReminderEntity("$namespace-reminder","Isolated notification reminder",due.toInstant().toEpochMilli(),recurrence=Recurrence(Frequency.DAILY,1,day),zoneId=now.zone.id)
            val s=SubscriptionEntity("$namespace-subscription","Isolated notification subscription",100,"IDR",day,recurrence=Recurrence(Frequency.MONTHLY,1,day))
            runBlocking {
                dao.saveEvent(e,true);dao.saveReminder(r,true);dao.saveSubscription(s,true)
                dao.saveAlerts("EVENT",e.id,setOf(0,1),540)
                dao.saveAlerts("REMINDER",r.id,setOf(0),540)
                dao.saveAlerts("SUBSCRIPTION",s.id,setOf(0),due.hour*60+due.minute)
                val catalog=AlertCatalog(mapOf(e.id to e),mapOf(r.id to r),mapOf(s.id to s))
                dao.alertPreferences().forEach{dao.saveAlertState(requireNotNull(alertForOccurrence(it,day,catalog,now.zone)).state)}
                delivery.reconcile()
            }
            assertTrue("Controlled test requires notification grant and enabled categories",delivery.enabled("EVENT")&&delivery.enabled("REMINDER")&&delivery.enabled("SUBSCRIPTION"))
            compose.waitUntil(10000){active().size==4}
            val tags=active().map{it.tag}.toSet()
            runBlocking{delivery.reconcile()}
            assertEquals(tags,active().map{it.tag}.toSet())
            for((domain,id,title) in listOf(Triple("EVENT",e.id,e.title),Triple("REMINDER",r.id,r.title),Triple("SUBSCRIPTION",s.id,s.name))) {
                val posted=active().first{it.notification.extras.getString("android.title")==title}
                assertEquals(context.packageName,posted.notification.contentIntent.creatorPackage)
                posted.notification.contentIntent.send()
                compose.waitUntil(10000){main()?.intent?.recordTarget()?.let{it.domain==domain&&it.id==id&&it.day==day}==true}
                val missing=if(domain=="SUBSCRIPTION")"This subscription is no longer available." else "This record is no longer available."
                compose.waitUntil(10000){compose.onAllNodesWithText(missing).fetchSemanticsNodes().isNotEmpty()}
                // These UUID owners exist only in isolated storage. No record is inserted into
                // personal storage to make the shipping Activity assertion pass.
                assertEquals(id,main()!!.intent.recordTarget()!!.id)
            }
            runBlocking {
                dao.saveEvent(e.copy(startsAtMillis=e.startsAtMillis+600000,endsAtMillis=e.endsAtMillis+600000),false)
                delivery.reconcile()
                assertTrue(active().none{it.notification.extras.getString("android.title")==e.title})
                dao.completeReminder(r.id,r.revision,day,true,clock.millis());delivery.reconcile()
                assertTrue(active().none{it.notification.extras.getString("android.title")==r.title})
                assertEquals(day+1,dao.pendingAlerts().single{it.recordId==r.id}.occurrenceEpochDay)
                dao.setSubscriptionActive(s.id,false);dao.deleteEvent(e.id);delivery.reconcile()
                assertTrue(active().none{it.notification.extras.getString("android.title")==s.name})
                assertTrue(dao.pendingAlerts().none{it.recordId==e.id})
            }
            assertEquals(before,runBlocking{BackupStore(app.database).snapshot()})
        } finally {
            delivery.cancelAlarm();active().forEach{manager.cancel(it.tag,it.id)}
            instrument.runOnMainSync {
                listOf(Stage.RESUMED,Stage.STARTED,Stage.STOPPED,Stage.CREATED).flatMap{ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)}.distinct().filterIsInstance<MainActivity>().forEach{it.finish()}
            }
            instrument.waitForIdleSync();db.close()
        }
    }
}
