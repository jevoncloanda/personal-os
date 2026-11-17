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
import java.time.LocalDate

/** Native UI fixtures only. Save/delete callbacks never open personal storage. */
class PorosInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val entry = EntryEntity("review", EntryKind.EXPENSE, 1250, "USD",
        LocalDate.now().toEpochDay(), "bank", categoryId = "food", note = "Review lunch")
    private val data = MoneySnapshot(
        accounts = listOf(AccountBalance(AccountEntity("bank", "Review bank", "USD"), 0, 0)),
        categories = listOf(CategoryEntity("food", "Food", "EXPENSE")))

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.uiAutomation.waitForIdle(600, 5000)
        android.os.SystemClock.sleep(1200)
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val captureFolder = InstrumentationRegistry.getArguments().getString("captureFolder") ?: "poros"
        val folder = File(instrument.targetContext.getExternalFilesDir(null), "review/$captureFolder").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun amountValidationCategoryRetentionAndExplicitSave() {
        var saved: EntryEntity? = null
        val dark = mutableStateOf(true)
        compose.setContent { PersonalOsTheme(Settings(if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT, false)) {
            EntryEditor(entry, entry.id, EntryKind.EXPENSE, data, false, null, {},
                { value, create, _ -> assertFalse(create); saved = value }, { _, _ -> fail("Unexpected deletion") })
        } }
        compose.onNodeWithText("Amount (USD)").performTextReplacement("12.345")
        capture("expense-keyboard-dark")
        compose.onNodeWithText("Save").performClick()
        compose.onAllNodesWithText("Amount is too large or has too many decimal places").onFirst().assertExists()
        assertNull(saved)
        compose.onNodeWithText("Amount (USD)").performTextReplacement("24.50")
        compose.onNodeWithText("Amount (USD)").performImeAction()
        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.onNodeWithText("Transaction kind: Expense").performScrollTo().performClick()
        compose.onNodeWithText("Expense", substring = false).performClick()
        compose.onNodeWithText("Category: Food").performScrollTo().assertExists()
        compose.onNodeWithText("Amount (USD)").performScrollTo()
        capture("expense-dark")
        compose.runOnIdle { dark.value = false }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val instrument = InstrumentationRegistry.getInstrumentation()
            compose.waitUntil(5000) {
                var lightBars = false
                instrument.runOnMainSync {
                    val decor = android.view.inspector.WindowInspector.getGlobalWindowViews().firstOrNull { it.hasWindowFocus() }
                    lightBars = decor?.windowInsetsController?.systemBarsAppearance?.and(android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS) == android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                }
                lightBars
            }
        }
        capture("expense-light")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals(2450L, saved!!.amountMinor)
            assertEquals("USD", saved!!.currencyCode)
            assertEquals("food", saved!!.categoryId)
            assertEquals("bank", saved!!.fromAccountId)
        }
    }

    @Test fun detailRequiresExplicitEditAndConfirmsDeletion() {
        var editing by mutableStateOf(false)
        var deleted = false
        compose.setContent { PersonalOsTheme(Settings(ThemeMode.DARK, false)) {
            if (editing) EntryEditor(entry, entry.id, EntryKind.EXPENSE, data, false, null, {}, { _, _, _ -> }, { _, _ -> })
            else EntryDetail(entry, data, false, null, {}, { editing = true }, { _, _ -> deleted = true }, {})
        } }
        compose.onNodeWithText("Transaction detail").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        capture("transaction-detail-dark")
        compose.onNodeWithText("Delete transaction").performScrollTo().performClick()
        assertFalse(deleted)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Edit details").performScrollTo().performClick()
        compose.onNodeWithText("Edit transaction").assertExists()
        compose.onNodeWithText("Amount (USD)").assertExists()
        assertFalse(deleted)
    }

    @Test fun linkedPaymentRoutesToOwnerWithoutEditOrDelete() {
        var opened = ""
        compose.setContent { PersonalOsTheme(Settings(ThemeMode.DARK, false)) {
            EntryDetail(entry, data.copy(linkedSubscriptions = mapOf(entry.id to "plan")), false, null, {},
                { fail("Linked expense cannot be edited here") }, { _, _ -> fail("Linked expense cannot be deleted here") }, { opened = it })
        } }
        compose.onNodeWithText("Edit details").assertDoesNotExist()
        compose.onNodeWithText("Delete transaction").assertDoesNotExist()
        compose.onNodeWithText("Open payment history").performScrollTo().performClick()
        assertEquals("plan", opened)
    }
}
