package com.jevon.personalos

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.backup.BackupStore
import com.jevon.personalos.data.*
import com.jevon.personalos.money.reviewTestRecords
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.ZonedDateTime

/** Installed-app review: preferences/drafts only; full portable personal graph compared before/after. */
class TodayPersonalizationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PersonalOsApplication
    private fun waitFor(text: String) = compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun tap(text: String) { val node = compose.onNodeWithText(text); try { node.performScrollTo() } catch (_: AssertionError) { }; node.performClick() }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.uiAutomation.waitForIdle(500, 3000); android.os.SystemClock.sleep(1000)
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val folder = File(instrument.targetContext.getExternalFilesDir(null), "review/phase-3.5").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun personalizationNavigationAndReviewPreserveAllPersonalRecords() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val original = runBlocking { app.settings.settings.first() }
        val store = BackupStore(app.database)
        val before = runBlocking { store.snapshot() }
        try {
            runBlocking { app.settings.setPreferredCurrency("IDR"); app.settings.setTodayBudget(null); app.settings.setTheme(ThemeMode.LIGHT); app.settings.setDynamicColor(false) }
            compose.onNodeWithContentDescription("Developer previews").performClick(); tap("Personal")
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Choose Today budget").fetchSemanticsNodes().isNotEmpty() }
            val today = runBlocking { app.database.observeToday(ZonedDateTime.now(), app.settings.settings.first()).first() }
            assertTrue(today.budget == null || today.budget!!.budget.currencyCode == "IDR")
            capture("personal-today-idr-light")
            compose.onNodeWithContentDescription("Choose Today budget").performClick(); capture("personal-budget-chooser"); tap("Close")
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Preferred currency (ISO code)").performTextReplacement("USD")
            tap("Save currency")
            compose.waitUntil(10000) { runBlocking { app.settings.settings.first().preferredCurrency == "USD" } }
            capture("preferred-currency-settings"); tap("Today")
            compose.activityRule.scenario.recreate()
            assertEquals("USD", runBlocking { app.settings.settings.first().preferredCurrency })
            // Choose a currency with no current budget to guarantee a no-budget state without entity writes.
            val absent = listOf("IDR", "USD", "AUD", "BHD").first { code -> today.eligibleBudgets.none { it.currencyCode == code } }
            runBlocking { app.settings.setPreferredCurrency(absent) }
            waitFor("No $absent budget this month")
            compose.onNodeWithText("No $absent budget this month").performScrollTo().assertIsDisplayed()
            capture("personal-no-budget-light")
            runBlocking { app.settings.setTheme(ThemeMode.DARK) }; capture("personal-no-budget-dark")
            tap("Manage budgets")
            waitFor("Monthly budgets")
            compose.onNodeWithText("Create budget").performClick()
            compose.onNodeWithText(absent).assertExists()
            compose.onNodeWithContentDescription("Cancel editing").performClick()
            tap("Review possible test records"); waitFor("Possible test records"); capture("personal-test-record-review")
            compose.onAllNodesWithText("Review details and relationships", substring = true).onFirst().performClick()
            capture("personal-test-record-relationships")
            compose.onNodeWithTag("TestRecordReviewList").performScrollToNode(hasText("Close review")); tap("Close review")
            compose.onAllNodesWithText("Schedule").filter(hasClickAction()).onLast().performClick()
            waitFor("New event")
            compose.onAllNodesWithText("Today").filter(hasClickAction()).onLast().performClick()
            compose.onNodeWithText("Subscriptions").performScrollTo().performClick()
            waitFor("Your recurring plans")
            assertEquals(before, runBlocking { store.snapshot() })
            val candidates = reviewTestRecords(before.accounts, before.entries, before.categories, before.budgets, before.subscriptions, before.payments)
            val folder = File(compose.activity.getExternalFilesDir(null), "review/phase-3.5").apply { mkdirs() }
            File(folder, "personal-review.txt").writeText("Full personal portable graph unchanged.\nEligible current budgets: ${today.eligibleBudgets.size}\n" + candidates.joinToString("\n\n") { "${it.section}: ${it.title} [${it.id}]\n${it.details.joinToString("\n")}" })
        } finally {
            runBlocking {
                app.settings.setPreferredCurrency(original.preferredCurrency); app.settings.setTodayBudget(original.todayBudgetId)
                app.settings.setTheme(original.theme); app.settings.setDynamicColor(original.dynamicColor)
                assertEquals(before, store.snapshot())
            }
        }
    }
}
