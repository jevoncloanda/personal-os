package com.jevon.personalos

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.YearMonth

/** Run in a new instrumentation process after MoneyWorkflowTest. Reads its disposable records. */
class MoneyPersistenceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun workflowRecordsSurviveAProcessRestart() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val dao = (compose.activity.application as PersonalOsApplication).database.personalDao()
        val bankName = runBlocking {
            val plan = dao.budgetProjections(YearMonth.now().atDay(1).toEpochDay()).last { it.budget.name.startsWith("P1 test budget ") }
            val suffix = plan.budget.name.removePrefix("P1 test budget ")
            val bank = dao.balances().first { it.account.name == "P1 test bank $suffix" }
            val unit = java.math.BigDecimal.TEN.pow(currencyDigits(bank.account.currencyCode)).longValueExact()
            assertEquals(1200 * unit, bank.balance)
            assertEquals(100 * unit, plan.spent)
            assertEquals(-20 * unit, plan.summary().remainingMinor)
            bank.account.name
        }
        compose.onAllNodesWithText("Money").filter(hasClickAction()).onLast().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("MoneyList").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Accounts").performClick()
        compose.onNodeWithTag("MoneyList").performScrollToNode(hasText(bankName))
        compose.onNodeWithText(bankName).assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "review/${InstrumentationRegistry.getArguments().getString("captureFolder") ?: "phase-1"}/cold-restart-money.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
