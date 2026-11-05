package com.jevon.personalos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.data.*
import com.jevon.personalos.today.formatMoney
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun keepTestWindowAwake() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test fun independentAppearanceChangesPreserveEachOther() = runBlocking {
        val store = (compose.activity.application as PersonalOsApplication).settings
        val original = store.settings.first()
        try {
            store.setTheme(ThemeMode.DARK)
            store.setDynamicColor(true)
            coroutineScope {
                launch { store.setTheme(ThemeMode.LIGHT) }
                launch { store.setDynamicColor(false) }
            }
            val saved = store.settings.first()
            assertEquals(ThemeMode.LIGHT, saved.theme)
            assertFalse(saved.dynamicColor)
        } finally {
            store.setTheme(original.theme)
            store.setDynamicColor(original.dynamicColor)
        }
    }

    @Test fun emptyStateNavigationAndQuickAdd() {
        compose.onNodeWithContentDescription("Developer previews").performClick()
        compose.onNodeWithText("Empty", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Nothing urgent").assertExists()
        compose.onNodeWithText("Create an overall monthly budget, or choose an existing budget in another currency.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Quick add").performClick()
        compose.onNodeWithText("Transfer").performClick()
        compose.onNodeWithText("Add transfer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel editing").performClick()
        compose.onNodeWithTag("MoneyList").assertIsDisplayed()
        compose.onNodeWithTag("MoneyList").performScrollToNode(hasText("Subscriptions"))
        compose.onNodeWithText("Subscriptions").performClick()
        compose.onNodeWithText("Your recurring plans").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Schedule").performClick()
        compose.onNodeWithText("New event").assertIsDisplayed()
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Your Library").assertIsDisplayed()
        compose.onAllNodesWithText("Today").filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Nothing urgent").assertExists()
    }

    @Test fun databaseStartsEmptyAndRejectsInvalidTransfer() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, PersonalDatabase::class.java).build()
        try {
            val dao = db.personalDao()
            assertTrue(dao.observeEntries().first().isEmpty())
            db.personalDao().saveAccount(AccountEntity("a", "Bank", "USD"), true)
            db.personalDao().saveAccount(AccountEntity("b", "Cash", "IDR"), true)
            try {
                dao.recordEntry(EntryEntity("bad", EntryKind.TRANSFER, 100, "USD", 0, "a", "b"))
                fail("Mixed-currency transfer should be rejected")
            } catch (_: IllegalArgumentException) { }
            assertTrue(dao.observeEntries().first().isEmpty())
            db.openHelper.writableDatabase.execSQL("UPDATE accounts SET currencyCode = 'USD' WHERE id = 'b'")
            dao.recordEntry(EntryEntity("good", EntryKind.TRANSFER, 100, "USD", 0, "a", "b"))
            assertEquals(1, dao.observeEntries().first().size)
            assertTrue(formatMoney(166_000_000, "IDR", Locale.US).contains("1,660,000"))
        } finally { db.close() }
    }
}
