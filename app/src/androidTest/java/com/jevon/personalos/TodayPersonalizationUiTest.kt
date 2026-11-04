package com.jevon.personalos

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.data.*
import com.jevon.personalos.today.*
import com.jevon.personalos.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.*

/** Pure UI fixtures. No personal Room records or DataStore preferences are written. */
class TodayPersonalizationUiTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val folder = File(instrument.targetContext.getExternalFilesDir(null), "review/phase-3.5").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun emptyStateExplicitForeignBudgetAndOverspendingUseActualCurrency() {
        val now = ZonedDateTime.parse("2026-11-04T09:00:00+07:00[Asia/Jakarta]")
        val start = LocalDate.of(2026, 11, 1).toEpochDay()
        val end = LocalDate.of(2026, 12, 1).toEpochDay()
        val budget = BudgetEntity("aud", "Review AUD plan", "AUD", 8000, start, end)
        val fixture = Snapshot(budgets = listOf(budget), entries = listOf(EntryEntity("expense", EntryKind.EXPENSE, 10000, "AUD", start, "a")))
        val preferences = mutableStateOf(Settings(theme = ThemeMode.LIGHT, dynamicColor = false))
        var opened = ""
        compose.setContent {
            PersonalOsTheme(preferences.value) { Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                TodayScreen(TodayState.Ready(fixture.forToday(now, preferences.value)), {}, { opened = it },
                    selectBudget = { preferences.value = preferences.value.copy(todayBudgetId = it) })
            } }
        }
        compose.onNodeWithText("No IDR budget this month").performScrollTo().assertIsDisplayed()
        capture("today-no-preferred-budget-light")
        compose.onNodeWithText("Choose Today budget").performScrollTo().performClick()
        compose.onNodeWithText("Review AUD plan · AUD").performClick()
        compose.onNodeWithText("Review AUD plan · AUD").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("${formatMoney(2000, "AUD")} over budget").assertExists()
        compose.onNodeWithText("${formatMoney(10000, "AUD")} spent of ${formatMoney(8000, "AUD")}").assertExists()
        capture("today-explicit-aud-overspent-light")
        compose.runOnIdle { preferences.value = preferences.value.copy(theme = ThemeMode.DARK) }
        capture("today-explicit-aud-overspent-dark")
        compose.onNodeWithText("Choose Today budget").performScrollTo().performClick()
        compose.onNodeWithText("Automatic · IDR").performClick()
        compose.onNodeWithText("No IDR budget this month").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Manage budgets").performScrollTo().performClick()
        assertEquals("money-budgets", opened)
    }
}
