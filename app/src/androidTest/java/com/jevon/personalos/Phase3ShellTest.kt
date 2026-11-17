package com.jevon.personalos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import com.jevon.personalos.backup.BackupStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Shipping-shell routing and unsaved drafts only; personal entities are never written. */
class Phase3ShellTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun awake() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    private fun tap(text: String) = compose.onAllNodesWithText(text).filter(hasClickAction()).onLast().performClick()
    @Test fun quickAddRoutesAndScheduleDraftsSurviveRecreationWithoutWrites() {
        val app = compose.activity.application as PersonalOsApplication
        val store = BackupStore(app.database)
        val before = runBlocking { store.snapshot() }
        for ((action, field) in listOf("New event" to "Event title", "New reminder" to "Reminder title")) {
            compose.onNodeWithContentDescription("Quick add").performClick()
            tap(action)
            compose.onNodeWithText(field).performTextReplacement("Unsaved Phase 3 draft")
            compose.onNodeWithText("More options").performScrollTo().performClick()
            compose.onNodeWithText("Notes (optional)").performScrollTo().performTextReplacement("Retained advanced draft")
            compose.onNodeWithText("More options").performScrollTo().performClick()
            Espresso.closeSoftKeyboard()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText(field).assertTextContains("Unsaved Phase 3 draft")
            compose.onNodeWithText("Notes (optional)").assertDoesNotExist()
            compose.onNodeWithText("More options").performScrollTo().performClick()
            compose.onNodeWithText("Notes (optional)").performScrollTo().assertTextContains("Retained advanced draft")
            compose.onNodeWithText("More options").performScrollTo().performClick()
            Espresso.pressBack()
            tap("Keep editing")
            compose.onNodeWithText(field).assertTextContains("Unsaved Phase 3 draft")
            compose.onNodeWithContentDescription("Cancel editing").performClick()
            tap("Discard")
            compose.onNodeWithText("New event").assertExists()
        }
        for ((action, heading) in listOf("Add expense" to "Add expense", "Add income" to "Add income", "Transfer" to "Add transfer", "New subscription" to "Subscription name")) {
            compose.onNodeWithContentDescription("Quick add").performClick()
            tap(action)
            compose.onNodeWithText(heading).assertExists()
            compose.onNodeWithContentDescription("Cancel editing").performClick()
        }
        tap("Schedule")
        tap("Month")
        compose.onAllNodesWithText("Today", substring = false).onFirst().assertExists()
        tap("Agenda")
        tap("Today")
        compose.onNodeWithContentDescription("Quick add").assertExists()
        assertEquals(before, runBlocking { store.snapshot() })
    }
}
