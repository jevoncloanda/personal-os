package com.jevon.personalos

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.schedule.*
import com.jevon.personalos.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.*
import java.util.UUID

class ScheduleWorkflowTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun tap(label:String){
        if(compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty())compose.onNodeWithTag("ScheduleList").performScrollToNode(hasText(label))
        val n=compose.onNodeWithText(label);try{n.performScrollTo()}catch(_:AssertionError){};n.performClick()
    }
    private fun field(label:String,value:String)=compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
    private fun waitFor(text:String){compose.waitUntil(10000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun capture(name:String){
        compose.waitForIdle();val i=InstrumentationRegistry.getInstrumentation();i.uiAutomation.waitForIdle(500,3000);android.os.SystemClock.sleep(1200)
        val b=requireNotNull(i.uiAutomation.takeScreenshot());val folder=File(i.targetContext.getExternalFilesDir(null),"review/phase-3").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    @Test fun createManageCompleteCalendarAndReminderSeriesInDisposableStorage(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val id=UUID.randomUUID().toString()
        val now=Instant.parse("2026-10-28T02:00:00Z")
        val scenario=ActivityScenario.launch<ScheduleReviewActivity>(Intent(context,ScheduleReviewActivity::class.java).putExtra("reviewId",id).putExtra("clockMillis",now.toEpochMilli()))
        var activity:ScheduleReviewActivity?=null;scenario.onActivity{activity=it}
        val db=activity!!.databaseForTest()
        try{
            waitFor("New event");capture("schedule-empty")
            tap("New event");field("Event title","Review design session")
            field("Notes (optional)","Bring sketches")
            field("Start date (YYYY-MM-DD)","2026-10-28");field("Start time (HH:mm)","10:00")
            field("End date (YYYY-MM-DD)","2026-10-28");field("End time (HH:mm)","11:00")
            field("Location (optional)","Studio");compose.onNodeWithText("Save").performClick();waitFor("Edit event / series")
            capture("event-detail")
            tap("All schedule");tap("New event");field("Event title","Review all-day trip");tap("All-day event")
            field("Start date (YYYY-MM-DD)","2026-10-29");field("Last day (inclusive) (YYYY-MM-DD)","2026-10-31")
            compose.onNodeWithText("Save").performClick();waitFor("Edit event / series")
            tap("All schedule");tap("New reminder");field("Reminder title","Review daily check-in")
            field("Due date (YYYY-MM-DD)","2026-10-28");tap("Repeat: Does not repeat");tap("Daily")
            compose.onNodeWithText("Save").performClick();waitFor("Edit reminder / series")
            val reminder=runBlocking{db.personalDao().reminderCandidates(0,Long.MAX_VALUE,0).single{it.title=="Review daily check-in"}}
            compose.onAllNodesWithContentDescription("Complete Review daily check-in 2026-10-28").onFirst().performClick()
            compose.waitUntil(10000){runBlocking{db.personalDao().completions(reminder.id).size==1}}
            compose.onAllNodesWithContentDescription("Complete Review daily check-in 2026-10-29").onFirst().performScrollTo().assertExists()
            capture("recurring-reminder-completed")
            tap("Edit reminder / series");field("Notes (optional)","Keep this habit")
            compose.onNodeWithText("Save").performClick();waitFor("Edit reminder / series")
            assertEquals(1,runBlocking{db.personalDao().completions(reminder.id).size})
            tap("All schedule");tap("Month");capture("schedule-month-light")
            tap("Review dark");capture("schedule-month-dark");tap("Review light")
            compose.onNodeWithContentDescription("Select 2026-10-29").performClick();capture("selected-day")
            tap("Agenda");capture("schedule-agenda")
            tap("New reminder");field("Reminder title","Review undated task");tap("Has due date")
            compose.onNodeWithText("Save").performClick();waitFor("Edit reminder / series")
            compose.onNodeWithContentDescription("Complete Review undated task undated").performClick()
            compose.onNodeWithContentDescription("Reopen Review undated task undated").assertExists()
            compose.onNodeWithContentDescription("Reopen Review undated task undated").performClick()
            tap("All schedule")
            tap("Review Today");capture("today-real-schedule")
            tap("Review Schedule")
            scenario.recreate();scenario.onActivity{activity=it}
            assertEquals(2,runBlocking{activity!!.databaseForTest().personalDao().eventCandidates(0,Long.MAX_VALUE).size})
            assertNotNull(runBlocking{activity!!.databaseForTest().personalDao().reminder(reminder.id)})
        }finally{activity?.deliveryForTest()?.cancelAlarm();scenario.close();context.deleteDatabase("phase3-review-$id.db")}
    }
}
