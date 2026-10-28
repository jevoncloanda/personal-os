package com.jevon.personalos

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.data.*
import com.jevon.personalos.today.formatMoney
import com.jevon.personalos.today.TodayViewModel
import com.jevon.personalos.ui.PersonalOsApp
import com.jevon.personalos.ui.PersonalOsTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class Phase15UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PersonalOsApplication
    @Before fun awake() { compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    private fun click(label: String) { compose.onAllNodesWithText(label).filter(hasClickAction()).onLast().performClick() }
    private fun field(label: String, value: String) { compose.onNodeWithText(label).performScrollTo().performTextReplacement(value) }
    private fun section(label: String) { compose.onNodeWithTag("MoneyList").performScrollToIndex(0); compose.onAllNodesWithText(label).filter(hasClickAction()).onLast().performScrollTo().performClick() }
    private fun save() { click("Save"); compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Cancel editing").fetchSemanticsNodes().isEmpty() } }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.uiAutomation.waitForIdle(600, 5000)
        android.os.SystemClock.sleep(1500)
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val folder = File(instrument.targetContext.getExternalFilesDir(null), "review/${InstrumentationRegistry.getArguments().getString("captureFolder") ?: "phase-1.5"}").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun rupiahDefaultsValidationAndEditorsSurviveRecreation() {
        val name = "P15 restore ${UUID.randomUUID().toString().take(6)}"
        click("Money")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("MoneyList").fetchSemanticsNodes().isNotEmpty() }
        section("Accounts"); click("Create account")
        compose.onNodeWithText("Currency (ISO code, e.g. IDR)").assertTextContains("IDR")
        click("Save")
        compose.onAllNodesWithText("Account name is required").onFirst().assertIsDisplayed()
        capture("account-validation")
        field("Account name", name); field("Opening balance", "25000.50")
        compose.onNodeWithText("Opening balance").performClick()
        capture("account-editor-keyboard")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Account name").assertTextContains(name)
        compose.onNodeWithText("Opening balance").performScrollTo().assertTextContains("25000.50")
        capture("account-editor-restored")
        save()
        val id = runBlocking { app.database.personalDao().balances().single { it.account.name == name }.also { assertEquals("IDR", it.account.currencyCode); assertEquals(2_500_050L, it.balance) }.account.id }
        compose.onNodeWithTag("MoneyList").performScrollToNode(hasText(name)); click(name)
        field("Opening balance", "30000.50")
        compose.activityRule.scenario.recreate(); save()
        runBlocking { val found = app.database.personalDao().balances().single { it.account.name == name }; assertEquals(id, found.account.id); assertEquals(3_000_050L, found.balance) }
        section("Accounts"); click("Create account"); field("Account name", "$name cancelled")
        compose.onNodeWithContentDescription("Cancel editing").performClick()
        click("Keep editing")
        compose.onNodeWithText("Account name").assertTextContains("$name cancelled")
        compose.onNodeWithContentDescription("Cancel editing").performClick(); click("Discard")
        runBlocking { assertFalse(app.database.personalDao().balances().any { it.account.name == "$name cancelled" }) }
        // Existing original v1/v2 records stay untouched; only the named disposable account was written.
        assertEquals("Rp25.000", formatMoney(2_500_000,"IDR"))
        assertEquals("$25,000", formatMoney(2_500_000,"USD"))
    }

    @Test fun captureSharedSystemAndDarkMoney() {
        val original = runBlocking { app.settings.settings.first() }
        try {
            runBlocking { app.settings.setTheme(ThemeMode.LIGHT); app.settings.setDynamicColor(false) }
            click("Money")
            compose.waitUntil(10000) { compose.onAllNodesWithTag("MoneyList").fetchSemanticsNodes().isNotEmpty() }
            section("Overview"); capture("money-overview-light")
            compose.onNodeWithTag("MoneyList").performScrollToNode(hasText("Where it went")); capture("money-category-spending")
            section("History"); click("Filters"); capture("history-filters"); click("Show results")
            capture("history-light")
            runBlocking { app.settings.setTheme(ThemeMode.DARK) }
            compose.waitUntil(5000) { runBlocking { app.settings.settings.first().theme == ThemeMode.DARK } }
            section("Overview"); capture("money-overview-dark")
            click("Schedule"); capture("schedule-dark")
            click("Library"); capture("library-dark")
            compose.onNodeWithContentDescription("Settings").performClick(); capture("appearance-dark"); click("Done")
            compose.onNodeWithContentDescription("Quick add").performClick(); capture("quick-add-dark")
        } finally { runBlocking { app.settings.setTheme(original.theme); app.settings.setDynamicColor(original.dynamicColor) } }
    }

    @Test fun appLocalLargeTextKeepsNavigationAndQuickAddAccessible() {
        val settings = runBlocking { app.settings.settings.first() }
        compose.activityRule.scenario.onActivity { activity ->
            val density = activity.resources.displayMetrics.density
            activity.setContent {
                PersonalOsTheme(settings) {
                    CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                        PersonalOsApp(viewModel<TodayViewModel>(), settings)
                    }
                }
            }
        }
        compose.onNodeWithText("Priorities").performScrollTo().assertExists()
        capture("large-text-today")
        click("Money")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("MoneyList").fetchSemanticsNodes().isNotEmpty() }
        capture("large-text-money")
        compose.onNodeWithText("Quick add").performClick()
        compose.onNodeWithText("Add expense").assertExists()
        // Only this test composition uses 1.5x; no phone display/font/security setting changes.
    }
}
