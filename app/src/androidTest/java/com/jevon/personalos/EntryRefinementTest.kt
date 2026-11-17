package com.jevon.personalos

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.data.*
import com.jevon.personalos.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.*

/** Editor callbacks use local values. No personal database or preferences are written. */
class EntryRefinementTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026, 11, 16)
    private val theme = mutableStateOf(ThemeMode.DARK)

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.uiAutomation.waitForIdle(600, 5000)
        android.os.SystemClock.sleep(1000)
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val folderName = InstrumentationRegistry.getArguments().getString("captureFolder") ?: "refinement"
        val folder = File(instrument.targetContext.getExternalFilesDir(null), "review/$folderName").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun reminderDateOnlyOptionalTimeAndUndatedRemainDistinct() {
        var saved: ReminderEntity? = null
        compose.setContent { PersonalOsTheme(Settings(theme.value, false)) {
            ReminderEditor(null, day, false, null, {}) { value, create -> assertTrue(create); saved = value }
        } }
        compose.onNodeWithText("Reminder title").performTextReplacement("Collect parcel")
        compose.onNodeWithText("Reminder title").performImeAction()
        compose.onNodeWithText("Notes (optional)").assertDoesNotExist()
        compose.onNodeWithText("Time zone").assertDoesNotExist()
        capture("reminder-dark")
        compose.onNodeWithText("Save").performClick()
        assertTrue(saved!!.dateOnly)
        assertEquals(day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), saved!!.dueAtMillis)
        compose.runOnIdle { theme.value = ThemeMode.LIGHT }
        capture("reminder-light")
        compose.runOnIdle { theme.value = ThemeMode.DARK }
        compose.onNodeWithText("Add due time").performClick()
        compose.onNodeWithContentDescription("Choose due time").performClick()
        compose.onNodeWithText("Type time").performClick()
        val fields = compose.onAllNodes(hasSetTextAction())
        val count = fields.fetchSemanticsNodes().size
        fields[count - 2].performTextReplacement("10")
        fields[count - 1].performTextReplacement("30")
        compose.onNodeWithText("Use time").performClick()
        compose.onNodeWithText("Save").performClick()
        assertFalse(saved!!.dateOnly)
        assertEquals(LocalTime.of(10, 30), Instant.ofEpochMilli(saved!!.dueAtMillis!!).atZone(ZoneId.systemDefault()).toLocalTime())
        compose.onNodeWithContentDescription("Remove due time").performClick()
        compose.onNodeWithText("Save").performClick()
        assertTrue(saved!!.dateOnly)
        compose.onNodeWithContentDescription("Remove due date").performClick()
        compose.onNodeWithText("Save").performClick()
        assertNull(saved!!.dueAtMillis)
        assertNull(saved!!.recurrence.frequency)
        compose.onNodeWithText("Add due date").assertExists()
        capture("reminder-undated-dark")
        compose.runOnIdle { theme.value = ThemeMode.LIGHT }
        capture("reminder-undated-light")
    }

    @Test fun collapsedReminderRetainsAdvancedValuesAndErrorsRevealThem() {
        val zone = ZoneId.of("Asia/Tokyo")
        val old = ReminderEntity("existing", "Weekly review", day.atTime(14, 15).atZone(zone).toInstant().toEpochMilli(),
            recurrence = Recurrence(Frequency.WEEKLY, 2, day.toEpochDay(), day.plusMonths(2).toEpochDay()),
            notes = "Keep this note", zoneId = zone.id, dateOnly = false, revision = 7)
        var saved: ReminderEntity? = null
        var openedAlerts = false
        compose.setContent { PersonalOsTheme(Settings(theme.value, false)) {
            ReminderEditor(old, day, false, null, {}, openAlerts = { openedAlerts = true }) { value, create -> assertFalse(create); saved = value }
        } }
        compose.onNodeWithText("Notes (optional)").assertDoesNotExist()
        compose.onNodeWithText("Notes added", substring = true).assertExists()
        compose.onNodeWithText("Every 2 weeks", substring = true).assertExists()
        compose.onNodeWithText("Save").performClick()
        assertEquals(old, saved)
        compose.onNodeWithText("More options").performClick()
        compose.onNodeWithText("Notification timing").performScrollTo().performClick()
        assertTrue(openedAlerts)
        compose.onNodeWithContentDescription("Remove due date").performScrollTo().performClick()
        compose.onNodeWithText("No date · no repeats or alerts").assertExists()
        compose.onNodeWithText("Every 2 weeks", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Notification timing").assertDoesNotExist()
        compose.onNodeWithText("Add due date").performClick()
        compose.onNodeWithText("Every 2 weeks", substring = true).assertExists()
        compose.onNodeWithText("Time zone").performScrollTo().performTextReplacement("Invalid/Zone")
        compose.onNodeWithText("Notification timing").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.onNodeWithText("Time zone").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Time zone").performScrollTo().assertIsDisplayed()
        assertEquals(old, saved)
        compose.onNodeWithText("Time zone").performTextReplacement(zone.id)
        compose.onNodeWithText("Save").performClick()
        assertEquals(old, saved)
        capture("reminder-advanced-dark")
    }

    @Test fun eventAllDayAndHiddenDetailsRetainTheirValues() {
        val zone = ZoneId.systemDefault()
        val old = EventEntity("event", "Two-day visit", day.atStartOfDay(zone).toInstant().toEpochMilli(),
            day.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli(), zone.id, true, "Studio",
            Recurrence(Frequency.MONTHLY, 1, day.toEpochDay()), "Bring sketches", 3)
        var saved: EventEntity? = null
        compose.setContent { PersonalOsTheme(Settings(theme.value, false)) {
            EventEditor(old, day, false, null, {}) { value, create -> assertFalse(create); saved = value }
        } }
        compose.onNodeWithText("Location (optional)").assertDoesNotExist()
        compose.onNodeWithText("Location added", substring = true).assertExists()
        compose.onNodeWithContentDescription("Choose start time").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()
        assertEquals(old, saved)
        capture("event-dark")
        compose.runOnIdle { theme.value = ThemeMode.LIGHT }
        capture("event-light")
        compose.onNodeWithContentDescription("All-day event").performClick()
        compose.onNodeWithContentDescription("Choose start time").assertExists()
        compose.onNodeWithContentDescription("All-day event").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(old, saved)
    }

    @Test fun expenseUsesOnlyValidRememberedSelectionsAndKeepsOptionalDetails() {
        val data = MoneySnapshot(accounts = listOf(
            AccountBalance(AccountEntity("bank", "Everyday", "USD"), 0, 0),
            AccountBalance(AccountEntity("cash", "Cash", "IDR"), 0, 0)),
            categories = listOf(CategoryEntity("food", "Food", "EXPENSE")))
        var saved: EntryEntity? = null
        compose.setContent { PersonalOsTheme(Settings(theme.value, false)) {
            EntryEditor(null, "new", EntryKind.EXPENSE, data, false, null, {},
                { value, _, _ -> saved = value }, { _, _ -> }, "bank", "food")
        } }
        compose.onNodeWithText("From account: Everyday · USD").assertExists()
        compose.onNodeWithText("Category: Food").assertExists()
        compose.onNodeWithText("Transaction notes (optional)").assertDoesNotExist()
        compose.onNodeWithText("Amount (USD)").performTextReplacement("12.50")
        capture("quick-expense-keyboard-dark")
        compose.onNodeWithText("Amount (USD)").performImeAction()
        capture("quick-expense-dark")
        compose.onNodeWithText("Save").performClick()
        assertEquals("bank", saved!!.fromAccountId)
        assertEquals("food", saved!!.categoryId)
        assertEquals("USD", saved!!.currencyCode)
        assertEquals(1250L, saved!!.amountMinor)
        assertEquals(LocalDate.now().toEpochDay(), saved!!.occurredOnEpochDay)
        compose.runOnIdle { theme.value = ThemeMode.LIGHT }
        capture("quick-expense-light")
    }
    @Test fun archivedAccountAndIncompatibleCategoryAreNotReused() {
        val data = MoneySnapshot(accounts = listOf(
            AccountBalance(AccountEntity("old", "Archived", "USD", archived = true), 0, 0),
            AccountBalance(AccountEntity("cash", "Cash", "IDR"), 0, 0)),
            categories = listOf(CategoryEntity("salary", "Salary", "INCOME")))
        compose.setContent { PersonalOsTheme(Settings(ThemeMode.DARK, false)) {
            EntryEditor(null, "new", EntryKind.EXPENSE, data, false, null, {}, { _, _, _ -> }, { _, _ -> }, "old", "salary")
        } }
        compose.onNodeWithText("From account: Cash · IDR").assertExists()
        compose.onNodeWithText("Category: Uncategorized").assertExists()
        compose.onNodeWithText("Amount (IDR)").assertExists()
    }
}
