package com.jevon.personalos

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.jevon.personalos.schedule.*
import com.jevon.personalos.data.*
import com.jevon.personalos.alerts.recordTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.*
import java.util.UUID
import java.util.regex.Pattern

/** Actual inexact-alarm delivery test, deliberately using real time and an isolated UUID database. */
class NotificationDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun capture(name:String){
        compose.waitForIdle();android.os.SystemClock.sleep(1200);val i=InstrumentationRegistry.getInstrumentation()
        val b=requireNotNull(i.uiAutomation.takeScreenshot());val folder=File(i.targetContext.getExternalFilesDir(null),"review/phase-3").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    @Test fun backgroundAlarmDeliveryAndRealNotificationTapsUseIsolatedRecords(){
        val instrument=InstrumentationRegistry.getInstrumentation();val context=instrument.targetContext;val device=UiDevice.getInstance(instrument)
        val original=context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
        val reportFolder=File(context.getExternalFilesDir(null),"review/phase-3").apply{mkdirs()}
        File(reportFolder,"notification-permission-original.txt").writeText(original.toString())
        val id=UUID.randomUUID().toString();val scenario=ActivityScenario.launch<ScheduleReviewActivity>(Intent(context,ScheduleReviewActivity::class.java).putExtra("reviewId",id))
        var activity:ScheduleReviewActivity?=null;scenario.onActivity{activity=it}
        val db=activity!!.databaseForTest();val delivery=activity!!.deliveryForTest();val dao=db.personalDao();val manager=context.getSystemService(NotificationManager::class.java)
        try{
            val initial=Instant.now().plusSeconds(600).toEpochMilli()
            runBlocking{dao.saveEvent(EventEntity("event","P3 alert event",initial,initial+3600000,ZoneId.systemDefault().id),true)}
            scenario.onActivity{it.modelForTest().focus.value=RecordTarget("EVENT","event")}
            compose.waitUntil(10000){compose.onAllNodesWithText("Notification timing").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Notification timing").performScrollTo().performClick()
            if(!original){
                compose.onNodeWithText("Enable Android notifications").performScrollTo().performClick()
                val allow=device.wait(Until.findObject(By.text(Pattern.compile("Allow|Izinkan",Pattern.CASE_INSENSITIVE))),10000)
                assertNotNull("Android notification permission prompt was not available",allow);allow.click()
                compose.waitUntil(10000){context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED}
            }
            compose.waitUntil(10000){delivery.enabled("EVENT")}
            capture("notification-settings-granted")
            compose.onNodeWithContentDescription("Cancel editing").performClick()
            val planned=ZonedDateTime.now().plusMinutes(3).withSecond(0).withNano(0)
            val start=planned.toInstant().toEpochMilli()
            runBlocking{
                dao.saveEvent(EventEntity("event","P3 alert event",start,start+3600000,planned.zone.id),false)
                dao.saveReminder(ReminderEntity("reminder","P3 alert reminder",start,zoneId=planned.zone.id),true)
                dao.saveSubscription(SubscriptionEntity("subscription","P3 alert subscription",10000,"IDR",planned.toLocalDate().toEpochDay(),recurrence=Recurrence(Frequency.MONTHLY,1,planned.toLocalDate().toEpochDay())),true)
                dao.saveAlerts("EVENT","event",setOf(0,1),540)
                dao.saveAlerts("REMINDER","reminder",setOf(0),540)
                dao.saveAlerts("SUBSCRIPTION","subscription",setOf(0),planned.hour*60+planned.minute)
                delivery.reconcile()
                assertEquals(4,dao.pendingAlerts().size);assertNotNull(delivery.nextScheduledAt)
                // A separate disposable pending alert is rescheduled, then disabled before it can fire.
                dao.saveEvent(EventEntity("canceled","P3 canceled alert",start+600000,start+660000,planned.zone.id),true)
                dao.saveAlerts("EVENT","canceled",setOf(0),540);delivery.reconcile()
                val old=dao.pendingAlerts().single{it.recordId=="canceled"}
                dao.saveEvent(dao.event("canceled")!!.copy(startsAtMillis=start+900000,endsAtMillis=start+960000),false);delivery.reconcile()
                assertNotEquals(old.key,dao.pendingAlerts().single{it.recordId=="canceled"}.key)
                dao.saveAlerts("EVENT","canceled",emptySet(),540);delivery.reconcile()
                assertTrue(dao.pendingAlerts().none{it.recordId=="canceled"})
            }
            val scheduledAt=Instant.now()
            assertTrue(manager.activeNotifications.none{it.tag?.startsWith("$id:")==true})
            device.pressHome();assertNotEquals(context.packageName,device.currentPackageName)
            val deadline=android.os.SystemClock.elapsedRealtime()+240000
            var polls=0
            fun active()=manager.activeNotifications.filter{it.tag?.startsWith("$id:")==true}
            while(active().size<4&&android.os.SystemClock.elapsedRealtime()<deadline){android.os.SystemClock.sleep(1000);if(++polls%10==0)device.pressHome()}
            assertEquals("Real best-effort delivery was not observed within four minutes; scheduling alone is not delivery proof",4,active().size)
            val observed=Instant.now();println("OBSERVED_BACKGROUND_DELIVERY: 4 alerts (event x2, reminder, subscription); scheduled at $scheduledAt; observed at $observed; app was not foreground")
            File(reportFolder,"notification-delivery.txt").writeText("Observed four actual AlarmManager notifications while app was backgrounded.\nScheduled: $scheduledAt\nObserved: $observed\nNo exact-time reliability claim.\n")
            val before=active().map{it.tag}.toSet();runBlocking{delivery.reconcile()};assertEquals(before,active().map{it.tag}.toSet())
            assertFalse(active().any{it.notification.extras.getString("android.title")=="P3 canceled alert"})
            for((title,expected) in listOf("P3 alert subscription" to "Record payment","P3 alert reminder" to "Edit reminder / series","P3 alert event" to "Edit event / series")){
                assertTrue("Notification disappeared before tapping $title; remaining test alerts: ${active().map{it.notification.extras.getString("android.title")}}",active().any{it.notification.extras.getString("android.title")==title})
                device.openNotification()
                var row=device.wait(Until.findObject(By.text(title)),5000)
                if(row==null){runCatching{UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(title)};row=device.findObject(By.text(title))}
                if(row==null)device.dumpWindowHierarchy(File(reportFolder,"notification-tray-failure.xml"))
                assertNotNull("Own notification was posted but not visible for a real tap: $title",row);row.click()
                // A collapsed Android notification group can expand on the first tap.
                if(!device.wait(Until.hasObject(By.pkg(context.packageName)),3000)){
                    device.findObject(By.text(title))?.click()
                }
                compose.waitUntil(15000){compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()}
                capture("notification-open-${if(title.contains("subscription"))"subscription" else if(title.contains("reminder"))"reminder" else "event"}")
            }
            assertNull(Intent().putExtra("recordDomain","INVALID").putExtra("recordId","x").recordTarget())
            val target=Intent().putExtra("recordDomain","REMINDER").putExtra("recordId","r").putExtra("occurrenceDay",planned.toLocalDate().toEpochDay()).recordTarget()!!
            assertEquals(planned.toLocalDate().toEpochDay(),target.day)
            // A notification can recreate the review Activity; use a fresh connection for the final assertion.
            val verify=androidx.room.Room.databaseBuilder(context,PersonalDatabase::class.java,"phase3-review-$id.db").build()
            try{assertEquals(0,runBlocking{verify.personalDao().entryCount(planned.toLocalDate().toEpochDay(),planned.toLocalDate().plusDays(1).toEpochDay())})}finally{verify.close()}
        }finally{
            delivery.cancelAlarm();manager.activeNotifications.filter{it.tag?.startsWith("$id:")==true}.forEach{manager.cancel(it.tag,it.id)}
            runCatching{scenario.close()}
            instrument.runOnMainSync{
                listOf(androidx.test.runner.lifecycle.Stage.RESUMED,androidx.test.runner.lifecycle.Stage.STARTED,androidx.test.runner.lifecycle.Stage.STOPPED,androidx.test.runner.lifecycle.Stage.CREATED)
                    .flatMap{androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)}.distinct()
                    .filterIsInstance<ScheduleReviewActivity>().filter{it.intent.getStringExtra("reviewId")==id}.forEach{it.finish()}
            }
            instrument.waitForIdleSync();context.deleteDatabase("phase3-review-$id.db")
        }
    }
}
