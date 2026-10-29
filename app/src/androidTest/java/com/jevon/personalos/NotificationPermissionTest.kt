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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.*
import java.util.UUID
import java.util.regex.Pattern

/** Contextual permission and native Settings return; isolated records only. */
class NotificationPermissionTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun capture(name:String){
        compose.waitForIdle();android.os.SystemClock.sleep(1200);val i=InstrumentationRegistry.getInstrumentation()
        val b=requireNotNull(i.uiAutomation.takeScreenshot());val folder=File(i.targetContext.getExternalFilesDir(null),"review/phase-3").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    @Test fun revokedPermissionShowsSettledBlockedStateAndDoesNotArmAlerts(){
        val instrument=InstrumentationRegistry.getInstrumentation();val context=instrument.targetContext
        org.junit.Assume.assumeTrue("Run after controlled revocation; revoking this process from itself would kill instrumentation",context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_DENIED)
        val id=UUID.randomUUID().toString()
        val scenario=ActivityScenario.launch<ScheduleReviewActivity>(Intent(context,ScheduleReviewActivity::class.java).putExtra("reviewId",id))
        var activity:ScheduleReviewActivity?=null;scenario.onActivity{activity=it}
        try{
            val start=Instant.now().plusSeconds(3600).toEpochMilli()
            runBlocking{
                val dao=activity!!.databaseForTest().personalDao()
                dao.saveEvent(EventEntity("revoked-event","Revoked permission review",start,start+3600000,ZoneId.systemDefault().id),true)
                dao.saveAlerts("EVENT","revoked-event",setOf(0),540)
                activity!!.deliveryForTest().reconcile()
                assertFalse(activity!!.deliveryForTest().status("EVENT").enabled)
                assertNull(activity!!.deliveryForTest().nextScheduledAt)
                assertEquals(1,dao.pendingAlerts().size)
            }
            scenario.onActivity{it.modelForTest().focus.value=RecordTarget("EVENT","revoked-event")}
            compose.waitUntil(10000){compose.onAllNodesWithText("Notification timing").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Notification timing").performScrollTo().performClick()
            compose.onNodeWithText("Alerts are blocked",substring=true).assertExists()
            compose.onNodeWithText("Enable Android notifications").assertExists()
            capture("notification-settings-revoked")
        }finally{activity?.deliveryForTest()?.cancelAlarm();scenario.close();context.deleteDatabase("phase3-review-$id.db")}
    }
    @Test fun permissionStatusAndCategorySettingsReturnRefreshInIsolatedStorage(){
        val instrument=InstrumentationRegistry.getInstrumentation();val context=instrument.targetContext
        val device=UiDevice.getInstance(instrument);val id=UUID.randomUUID().toString()
        val scenario=ActivityScenario.launch<ScheduleReviewActivity>(Intent(context,ScheduleReviewActivity::class.java).putExtra("reviewId",id))
        var activity:ScheduleReviewActivity?=null;scenario.onActivity{activity=it}
        var changedCategory=false
        try{
            val start=Instant.now().plusSeconds(3600).toEpochMilli()
            runBlocking{activity!!.databaseForTest().personalDao().saveEvent(EventEntity("permission-event","Permission review",start,start+3600000,ZoneId.systemDefault().id),true)}
            scenario.onActivity{it.modelForTest().focus.value=RecordTarget("EVENT","permission-event")}
            compose.waitUntil(10000){compose.onAllNodesWithText("Notification timing").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Notification timing").performScrollTo().performClick()
            if(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                compose.onNodeWithText("Alerts are blocked",substring=true).assertExists();capture("notification-settings-denied")
                compose.onNodeWithText("Enable Android notifications").performScrollTo().performClick()
                val allow=device.wait(Until.findObject(By.text(Pattern.compile("Allow|Izinkan",Pattern.CASE_INSENSITIVE))),10000)
                assertNotNull("Contextual Android permission prompt unavailable",allow);allow.click()
            }
            try{compose.waitUntil(15000){compose.onAllNodesWithText("Best-effort local alerts.",substring=true).fetchSemanticsNodes().isNotEmpty()}}
            catch(e:Throwable){capture("notification-settings-grant-failure");throw e}
            capture("notification-settings-granted")
            compose.onNodeWithText("Android category settings").performScrollTo().performClick()
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")),10000))
            val toggle=device.wait(Until.findObject(By.res("com.android.settings","sesl_switchbar_switch")),10000)
            assertNotNull("Samsung category master switch unavailable",toggle);assertTrue(toggle.isChecked);changedCategory=true;toggle.click();device.pressBack()
            compose.waitUntil(10000){compose.onAllNodesWithText("Alerts are blocked",substring=true).fetchSemanticsNodes().isNotEmpty()}
            capture("notification-settings-category-disabled")
            compose.onNodeWithText("Android category settings").performScrollTo().performClick()
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")),10000))
            val disabled=device.wait(Until.findObject(By.res("com.android.settings","sesl_switchbar_switch")),10000)
            assertNotNull(disabled);assertFalse(disabled.isChecked);disabled.click();device.pressBack()
            compose.waitUntil(10000){compose.onAllNodesWithText("Best-effort local alerts.",substring=true).fetchSemanticsNodes().isNotEmpty()}
            capture("notification-settings-return-enabled")
        }finally{
            val manager=context.getSystemService(NotificationManager::class.java)
            if(changedCategory&&manager.getNotificationChannel("personal-os-event")?.importance==NotificationManager.IMPORTANCE_NONE){
                context.startActivity(Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,"personal-os-event").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                val restore=device.wait(Until.findObject(By.res("com.android.settings","sesl_switchbar_switch")),10000)
                checkNotNull(restore){"Restore the test category's original enabled state"}
                if(!restore.isChecked)restore.click()
                device.pressBack()
            }
            activity?.deliveryForTest()?.cancelAlarm();scenario.close();context.deleteDatabase("phase3-review-$id.db")
        }
    }
}
