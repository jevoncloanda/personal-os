package com.jevon.personalos

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.data.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** App-only rendered evidence; opens and cancels drafts without saving financial data. */
class Phase16UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PersonalOsApplication
    @Before fun awake() { compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    private fun click(label: String) { compose.onAllNodesWithText(label).filter(hasClickAction()).onLast().performClick() }
    private fun section(label: String) {
        compose.onNodeWithTag("MoneyList").performScrollToIndex(0)
        compose.onAllNodesWithText(label).filter(hasClickAction()).onLast().performScrollTo().performClick()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.uiAutomation.waitForIdle(600, 5000)
        // SystemUI theme transitions are outside Compose's idle clock.
        android.os.SystemClock.sleep(1500)
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val folder = File(instrument.targetContext.getExternalFilesDir(null), "review/${InstrumentationRegistry.getArguments().getString("captureFolder") ?: "phase-1.6"}").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun captureRefinedSystemAndNativeEditors() {
        val original = runBlocking { app.settings.settings.first() }
        try {
            runBlocking { app.settings.setTheme(ThemeMode.LIGHT); app.settings.setDynamicColor(false) }
            compose.onNodeWithContentDescription("Developer previews").performClick(); click("Personal")
            capture("today-personal-light")
            click("Money")
            compose.waitUntil(10000) { compose.onAllNodesWithTag("MoneyList").fetchSemanticsNodes().isNotEmpty() }
            section("Overview"); capture("money-overview-light")
            compose.onNodeWithTag("MoneyList").performScrollToNode(hasText("Recent activity"))
            compose.onNodeWithTag("MoneyList").performTouchInput { swipeUp() }
            capture("money-overview-scrolled")
            section("History"); capture("history-light")
            section("Accounts"); capture("accounts-light")
            compose.onNodeWithContentDescription("Quick add").performClick(); capture("quick-add-light")
            click("Expense"); capture("expense-editor")
            compose.onNodeWithContentDescription("Cancel editing").performClick()
            compose.onNodeWithContentDescription("Quick add").performClick(); click("Transfer"); capture("transfer-editor")
            compose.onNodeWithContentDescription("Cancel editing").performClick()
            compose.onNodeWithContentDescription("Settings").performClick(); capture("appearance-light"); click("Money")
            runBlocking { app.settings.setTheme(ThemeMode.DARK) }
            click("Today"); capture("today-personal-dark")
            click("Money"); section("Overview"); capture("money-overview-dark")
            compose.runOnIdle {
                val bars = androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                org.junit.Assert.assertFalse(bars.isAppearanceLightStatusBars)
                org.junit.Assert.assertFalse(bars.isAppearanceLightNavigationBars)
            }
        } finally { runBlocking { app.settings.setTheme(original.theme); app.settings.setDynamicColor(original.dynamicColor) } }
    }
}
