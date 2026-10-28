package com.jevon.personalos

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.YearMonth

/** Uses uniquely named disposable records only. No existing record is edited/deleted. */
class MoneyWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val suffix = (System.currentTimeMillis() % 1000000).toString()
    private val bank = "P1 test bank $suffix"
    private val cash = "P1 test cash $suffix"
    private val expense = "P1 test lunch $suffix"
    private val income = "P1 test income $suffix"
    private val transfer = "P1 test transfer $suffix"
    private val budgetName = "P1 test budget $suffix"
    private val app get() = compose.activity.application as PersonalOsApplication
    private var code = "IDR"
    @Before fun awake() { compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    private fun click(text: String) { compose.onAllNodesWithText(text).filter(hasClickAction()).onLast().performClick() }
    private fun field(label: String, value: String) { compose.onNodeWithText(label).performScrollTo().performTextReplacement(value) }
    private fun navigate(label: String) { compose.onAllNodesWithText(label).filter(hasClickAction()).onLast().performClick() }
    private fun section(label: String) { compose.onNodeWithTag("MoneyList").performScrollToIndex(0); compose.onAllNodesWithText(label).filter(hasClickAction()).onLast().performScrollTo().performClick() }
    private fun save() { click("Save"); compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Cancel editing").fetchSemanticsNodes().isEmpty() } }
    private fun select(label: String, value: String) { compose.onNodeWithText("$label:", substring = true).performScrollTo().performClick(); click(value) }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "review/${InstrumentationRegistry.getArguments().getString("captureFolder") ?: "phase-1"}").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun createAccount(name: String, opening: String) {
        section("Accounts"); click("Create account")
        field("Account name", name); field("Currency (ISO code, e.g. IDR)", code)
        field("Opening balance", opening); save()
    }
    private fun add(kind: String, note: String, amount: String) {
        compose.onNodeWithContentDescription("Quick add").performClick(); click(if (kind == "TRANSFER") "Transfer" else "Add ${kind.lowercase()}")
        if (kind != "INCOME") select("From account", "$bank · $code")
        if (kind != "EXPENSE") select(if (kind == "TRANSFER") "To account" else "Account", "${if (kind == "TRANSFER") cash else bank} · $code")
        field("Amount ($code)", amount)
        field("Transaction notes (optional)", note); save()
    }
    @Test fun realMoneyFlowsPersistAndUpdateToday() {
        val original = runBlocking { app.settings.settings.first() }
        // Choose an unused currency, so an existing overall budget is never changed.
        val used = runBlocking { app.database.personalDao().balances().map { it.account.currencyCode }.toSet() + app.database.personalDao().budgetProjections(YearMonth.now().atDay(1).toEpochDay()).map { it.budget.currencyCode } }
        val candidates = listOf("IDR", "USD", "EUR", "GBP", "AUD", "CAD", "CHF", "SGD", "NZD") +
            java.util.Currency.getAvailableCurrencies().filter { it.defaultFractionDigits == 2 }.map { it.currencyCode }.sorted()
        code = candidates.first { it !in used }
        try {
            runBlocking { app.settings.setTheme(ThemeMode.LIGHT); app.settings.setDynamicColor(false) }
            navigate("Money")
            compose.waitUntil(10000) { compose.onAllNodesWithTag("MoneyList").fetchSemanticsNodes().isNotEmpty() }
            createAccount(bank, "1000")
            add("EXPENSE", expense, "125.50")
            add("INCOME", income, "300")
            createAccount(cash, "50")
            add("TRANSFER", transfer, "200")
            section("History")
            field("Search notes, account, category", suffix)
            click("Filters")
            select("Kind", "Expense")
            screenshot("history-filters")
            click("Reset filters"); click("Show results")
            compose.onNodeWithTag("MoneyList").performScrollToNode(hasText(expense))
            click(expense); field("Amount ($code)", "100"); save()
            compose.onNodeWithTag("MoneyList").performScrollToNode(hasText(transfer))
            click(transfer); compose.onNodeWithText("Delete transaction").performScrollTo().performClick(); click("Delete")
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Cancel editing").fetchSemanticsNodes().isEmpty() }
            section("Budgets"); click("Create budget")
            field("Budget name", budgetName); field("Currency (ISO code)", code); field("Budget limit", "500"); save()
            compose.onNodeWithTag("MoneyList").performScrollToNode(hasText("$budgetName · $code"))
            click("$budgetName · $code"); field("Budget limit", "80"); save()
            screenshot("budget-overspent")
            val digits = currencyDigits(code)
            val multiplier = java.math.BigDecimal.TEN.pow(digits).longValueExact()
            runBlocking {
                val dao = app.database.personalDao()
                assertEquals(1200 * multiplier, dao.balances().first { it.account.name == bank }.balance)
                assertEquals(50 * multiplier, dao.balances().first { it.account.name == cash }.balance)
                val plan = dao.budgetProjections(YearMonth.now().atDay(1).toEpochDay()).first { it.budget.name == budgetName }
                assertEquals(100 * multiplier, plan.spent)
                assertEquals(-20 * multiplier, plan.summary().remainingMinor)
            }
            section("Accounts"); screenshot("accounts-light")
            section("Overview"); screenshot("money-overview-light")
            section("History"); compose.onNodeWithTag("MoneyList").performScrollToNode(hasText(expense)); screenshot("history-light")
            navigate("Today")
            compose.onNodeWithContentDescription("Developer previews").performClick(); click("Personal")
            compose.onNodeWithText("This month").performScrollTo()
            // Today deterministically chooses the first overall currency; inspect its actual projection.
            runBlocking { assertNotNull(app.database.observeToday(java.time.ZonedDateTime.now()).first().budget) }
            screenshot("today-personal-light")
            runBlocking { app.settings.setTheme(ThemeMode.DARK) }
            compose.waitUntil(5000) { runBlocking { app.settings.settings.first().theme == ThemeMode.DARK } }
            compose.waitForIdle()
            // SystemUI tint transitions live outside Compose/Accessibility idleness.
            android.os.SystemClock.sleep(1_500)
            compose.runOnIdle {
                val bars = androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                assertFalse(bars.isAppearanceLightStatusBars)
                assertFalse(bars.isAppearanceLightNavigationBars)
            }
            compose.waitForIdle(); screenshot("today-personal-dark")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("This month").performScrollTo().assertExists()
            navigate("Money")
            compose.waitUntil(10000) { compose.onAllNodesWithTag("MoneyList").fetchSemanticsNodes().isNotEmpty() }
            section("Accounts"); compose.onNodeWithTag("MoneyList").performScrollToNode(hasText(bank)); screenshot("accounts-dark")
            runBlocking { assertTrue(app.database.personalDao().balances().any { it.account.name == bank }) }
            // Cancellation proof: typed drafts are not persisted.
            compose.onNodeWithTag("MoneyList").performScrollToIndex(0); click("Create account")
            field("Account name", "P1 unsaved $suffix"); compose.onNodeWithContentDescription("Cancel editing").performClick()
            click("Discard")
            runBlocking { assertFalse(app.database.personalDao().balances().any { it.account.name == "P1 unsaved $suffix" }) }
        } finally { runBlocking { app.settings.setTheme(original.theme); app.settings.setDynamicColor(original.dynamicColor) } }
    }
}
