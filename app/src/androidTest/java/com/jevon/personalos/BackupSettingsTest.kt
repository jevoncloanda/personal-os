package com.jevon.personalos

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.backup.BackupStore
import com.jevon.personalos.data.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Non-destructive installed-app smoke. No export or restore is invoked on personal storage. */
class BackupSettingsTest {
    private val captureFolder get() = InstrumentationRegistry.getArguments().getString("captureFolder") ?: "phase-1.7"
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.uiAutomation.waitForIdle(600, 5000); android.os.SystemClock.sleep(1500)
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val folder = File(instrument.targetContext.getExternalFilesDir(null), "review/$captureFolder").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun settingsRouteThemesAndNavigationPreservePersonalRecords() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val app = compose.activity.application as PersonalOsApplication
        val original = runBlocking { app.settings.settings.first() }
        val store = BackupStore(app.database)
        val before = runBlocking { store.snapshot() }
        try {
            runBlocking { app.settings.setTheme(ThemeMode.LIGHT); app.settings.setDynamicColor(false) }
            compose.onNodeWithContentDescription("Settings").performClick(); capture("settings-entry")
            compose.onNodeWithText("Dark").performClick()
            compose.waitUntil(5000) { runBlocking { app.settings.settings.first().theme == ThemeMode.DARK } }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Dark").assertIsSelected()
            capture("settings-dark")
            compose.onNodeWithText("Light").performClick()
            compose.onNodeWithText("Backup & restore").performScrollTo().performClick()
            compose.onNodeWithText("Export backup").assertExists(); capture("settings-backup-light")
            runBlocking { app.settings.setTheme(ThemeMode.DARK) }; capture("settings-backup-dark")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Restore backup").assertExists()
            compose.onNodeWithContentDescription("Back").performClick()
            for (screen in listOf("Schedule", "Library")) {
                compose.onAllNodesWithText(screen).filter(hasClickAction()).onLast().performClick()
                val tag = if (screen == "Library") "LibraryList" else "ScheduleList"
                compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
                capture(screen.lowercase())
            }
            compose.onAllNodesWithText("Money").filter(hasClickAction()).onLast().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("MoneyList").fetchSemanticsNodes().isNotEmpty() }; capture("money-regression")
            compose.onNodeWithText("Categories").assertIsDisplayed().performClick()
            compose.onNodeWithText("Add category").assertExists()
            compose.onNodeWithText("Overview").performClick()
            compose.onAllNodesWithText("Today").filter(hasClickAction()).onLast().performClick(); capture("today-regression")
            compose.onNodeWithContentDescription("Quick add").performClick()
            capture("quick-add-dark")
            compose.onNodeWithText("Subscription").performClick()
            compose.onNodeWithContentDescription("Subscription name").performTextInput("Unsaved review draft")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Unsaved review draft").assertExists()
            compose.onNodeWithContentDescription("Cancel editing").performClick()
            compose.onNodeWithText("Discard").performClick()
            assertEquals(before, runBlocking { store.snapshot() })
            val counts = before.counts.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            val instrument = InstrumentationRegistry.getInstrumentation()
            File(instrument.targetContext.getExternalFilesDir(null), "review/$captureFolder/personal-table-counts.txt").writeText(counts)
        } finally { runBlocking { app.settings.setTheme(original.theme); app.settings.setDynamicColor(original.dynamicColor) } }
    }
}
