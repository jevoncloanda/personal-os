package com.jevon.personalos

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.subscriptions.SubscriptionReviewActivity
import com.jevon.personalos.data.*
import com.jevon.personalos.backup.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID

class SubscriptionWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun tap(text: String) {
        val picker = text.substringBefore(":").takeIf {
            it in listOf("Default payment account", "Default expense category", "Billing frequency")
        }
        if (picker != null) {
            val target = hasContentDescription("Choose ${picker.lowercase()}")
            if (compose.onAllNodes(target).fetchSemanticsNodes().isEmpty()) {
                compose.onNodeWithText("More options").performScrollTo().performClick()
            }
            compose.onNode(target).performScrollTo().performClick()
            return
        }
        if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithTag("SubscriptionList").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("SubscriptionList").performScrollToNode(hasText(text))
        }
        val node = compose.onAllNodesWithText(text).onLast()
        try { node.performScrollTo() } catch (_: AssertionError) { /* Host actions have no scrolling ancestor. */ }
        node.performClick()
    }
    private fun field(label: String, text: String) {
        if (label.endsWith(" (YYYY-MM-DD)")) {
            val name = label.removeSuffix(" (YYYY-MM-DD)")
            compose.onNodeWithContentDescription("Choose ${name.lowercase()}").performScrollTo().performClick()
            compose.onNodeWithText("Type date").performClick()
            compose.onNodeWithText("Date (YYYY-MM-DD)").performTextReplacement(text)
            compose.onNodeWithText("Use date").performClick()
            return
        }
        val target = when {
            label == "Subscription name" -> hasContentDescription(label)
            label == "Price" -> hasContentDescription("Price in", substring = true) and hasSetTextAction()
            label.startsWith("Actual paid amount") -> hasContentDescription("Actual paid amount in", substring = true) and hasSetTextAction()
            else -> hasText(label)
        }
        if (label == "Description (optional)" && compose.onAllNodes(target).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithText("More options").performScrollTo().performClick()
        }
        compose.onNode(target).performScrollTo().performTextReplacement(text)
    }
    private fun save() {
        compose.onAllNodes(hasClickAction() and
            (hasText("Save", substring = true) or hasText("Record payment"))).onLast().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithContentDescription("Cancel editing").fetchSemanticsNodes().isEmpty()
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.uiAutomation.waitForIdle(500, 3000)
        android.os.SystemClock.sleep(1500)
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val folder = File(instrument.targetContext.getExternalFilesDir(null), "review/${InstrumentationRegistry.getArguments().getString("captureFolder") ?: "phase-2"}").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    @Test fun moneyEntryEditingAndManagementUseDisposableStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reviewId = UUID.randomUUID().toString()
        val scenario = ActivityScenario.launch<SubscriptionReviewActivity>(
            Intent(context, SubscriptionReviewActivity::class.java).putExtra("reviewId", reviewId)
        )
        var db: PersonalDatabase? = null
        scenario.onActivity { db = it.databaseForTest() }
        val dao = requireNotNull(db).personalDao()
        fun section(label: String) {
            compose.onNodeWithTag("MoneyList").performScrollToIndex(0)
            compose.onAllNodes(hasText(label) and hasClickAction()).onLast().performScrollTo().performClick()
        }
        fun moneyTap(label: String) {
            val target = hasText(label) and hasClickAction()
            if (compose.onAllNodes(target).fetchSemanticsNodes().isEmpty()) {
                compose.onNodeWithTag("MoneyList").performScrollToNode(target)
            }
            compose.onAllNodes(target).onLast().performScrollTo().performClick()
        }
        fun account(label: String, name: String) {
            compose.onNodeWithContentDescription("Choose $label").performScrollTo().performClick()
            tap("$name · IDR")
        }
        fun amount(value: String) {
            compose.onNodeWithContentDescription("Amount in IDR").performTextReplacement(value)
            compose.onNodeWithContentDescription("Amount in IDR").performImeAction()
        }
        try {
            runBlocking {
                dao.saveAccount(AccountEntity("review-cash", "Review cash", "IDR"), true)
                val month = java.time.YearMonth.now()
                dao.saveBudget(BudgetEntity("review-category-budget", "Services limit", "IDR", 2_000_000,
                    month.atDay(1).toEpochDay(), month.plusMonths(1).atDay(1).toEpochDay(), "review-category"), true)
            }
            tap("Review dark")
            tap("Money")
            compose.waitUntil(10000) { compose.onAllNodesWithTag("MoneyList").fetchSemanticsNodes().isNotEmpty() }
            moneyTap("Add Expense")
            account("paid from account", "Review bank")
            amount("12500")
            compose.onNodeWithText("All categories").performScrollTo().performClick()
            tap("Services")
            capture("money-expense-populated-dark")
            compose.onNodeWithText("More options").performScrollTo().performClick()
            field("Description / notes (optional)", "Verification expense")
            compose.onNodeWithText("More options").performScrollTo().performClick()
            save()
            section("History")
            moneyTap("Verification expense")
            capture("money-transaction-detail-dark")
            tap("Edit details")
            amount("13000")
            save()
            val expense = runBlocking { dao.observeEntries().first().single() }
            assertEquals(1_300_000L, expense.amountMinor)
            assertEquals("review-category", expense.categoryId)
            assertEquals("Verification expense", expense.note)
            section("Overview")
            tap("Review light")
            moneyTap("Income")
            account("receiving account", "Review bank")
            amount("500000")
            capture("money-income-populated-light")
            save()
            tap("Review dark")
            section("Overview")
            moneyTap("Transfer")
            account("from account", "Review bank")
            account("to account", "Review cash")
            amount("25000")
            capture("money-transfer-populated-dark")
            save()
            val entries = runBlocking { dao.observeEntries().first() }
            assertEquals(3, entries.size)
            assertEquals(1, entries.count { it.kind == EntryKind.TRANSFER })
            assertEquals(146_200_000L, runBlocking { dao.balances().single { it.account.id == "review-idr" }.balance })
            assertEquals(2_500_000L, runBlocking { dao.balances().single { it.account.id == "review-cash" }.balance })
            section("Overview"); capture("money-populated-dark")
            section("History"); capture("money-history-populated-dark")
            section("Budgets"); capture("money-budgets-populated-dark")
            val budgets = runBlocking { dao.budgetProjections(LocalDate.now().toEpochDay()) }
            assertEquals(2, budgets.size)
            assertTrue(budgets.all { it.spent == 1_300_000L })
            section("Accounts"); capture("money-accounts-populated-dark")
            section("Categories"); capture("money-categories-populated-dark")
            tap("Review light")
            section("Overview"); capture("money-populated-light")
        } finally {
            scenario.close()
            context.deleteDatabase("phase2-review-$reviewId.db")
        }
    }
    @Test fun importedPaidCursorIsSkippedAndCalendarHandlesTypedDatesOutsidePickerRange() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reviewId = UUID.randomUUID().toString()
        val scenario = ActivityScenario.launch<SubscriptionReviewActivity>(Intent(context, SubscriptionReviewActivity::class.java).putExtra("reviewId", reviewId))
        try {
            var database: PersonalDatabase? = null
            scenario.onActivity { database = it.databaseForTest() }
            val store = BackupStore(requireNotNull(database))
            val day = LocalDate.now()
            val due = day.toEpochDay()
            runBlocking {
                val before = store.snapshot()
                val old = SubscriptionEntity("legacy-plan", "Legacy paid plan", 10000, "IDR", due, recurrence = Recurrence(Frequency.MONTHLY, 1, due))
                val expense = EntryEntity("legacy-expense", EntryKind.EXPENSE, 10000, "IDR", due, fromAccountId = "review-idr")
                store.replace(PreparedBackup(before.copy(subscriptions = listOf(old), entries = listOf(expense), payments = listOf(SubscriptionPaymentEntity("legacy-link", old.id, due, expense.id))), "", ""))
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Legacy paid plan", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Legacy paid plan", substring = true).performClick()
            compose.onAllNodesWithText(day.plusMonths(1).toString()).onFirst().assertExists()
            tap("Record payment")
            compose.onNodeWithContentDescription("Choose renewal occurrence").assertExists()
            field("Renewal occurrence (YYYY-MM-DD)", "2500-01-31")
            compose.onNodeWithContentDescription("Choose renewal occurrence").performScrollTo().performClick()
            compose.onNodeWithText("Use date").assertExists()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText(LocalDate.of(2500, 1, 31).format(
                java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
            )).assertExists()
        } finally { scenario.close(); context.deleteDatabase("phase2-review-$reviewId.db") }
    }
    @Test fun managePayCorrectCancelAndReopenDisposableSubscriptions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reviewId = UUID.randomUUID().toString()
        val intent = Intent(context, SubscriptionReviewActivity::class.java).putExtra("reviewId", reviewId)
        var scenario = ActivityScenario.launch<SubscriptionReviewActivity>(intent)
        var db: PersonalDatabase? = null
        fun database(): PersonalDatabase { scenario.onActivity { db = it.databaseForTest() }; return requireNotNull(db) }
        fun waitFor(text: String) {
            if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("SubscriptionList").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("SubscriptionList").performScrollToNode(hasText(text))
            }
            compose.waitUntil(10000) {
                compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        }
        try {
            waitFor("New subscription"); capture("subscriptions-empty")
            tap("New subscription"); field("Subscription name", "Review streaming")
            field("Description (optional)", "Monthly household plan")
            field("Price", "49000")
            field("Next renewal date (YYYY-MM-DD)", LocalDate.now().toString())
            tap("Default payment account: Choose when paying"); tap("Review bank")
            tap("Default expense category: Uncategorized"); tap("Services")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithContentDescription("Subscription name").performScrollTo()
            capture("subscription-editor")
            save(); waitFor("Record payment")
            val subscriptionId = runBlocking { database().personalDao().subscriptions().single().id }
            capture("subscription-detail-light")
            tap("All subscriptions"); tap("New subscription")
            field("Subscription name", "Review annual")
            field("Currency code", "USD"); field("Price", "120")
            tap("Billing frequency: Monthly"); tap("Yearly")
            save(); waitFor("Record payment")
            tap("All subscriptions"); capture("subscriptions-light")
            tap("Review dark"); capture("subscriptions-dark"); tap("Review light")
            tap("Review streaming")
            tap("Edit subscription"); field("Price", "50000")
            field("Next renewal date (YYYY-MM-DD)", LocalDate.now().toString())
            save(); waitFor("Record payment")
            tap("Record payment"); field("Actual paid amount (IDR)", "48000")
            capture("record-payment")
            save(); waitFor("Record payment")
            val dao = database().personalDao()
            val link = runBlocking { dao.paymentsFor(subscriptionId).single() }
            val expense = runBlocking { dao.findEntry(link.entryId)!! }
            assertEquals(4_800_000L, expense.amountMinor)
            assertEquals(95_200_000L, runBlocking { dao.balances().first { it.account.id == "review-idr" }.balance })
            assertEquals(4_800_000L, runBlocking { dao.budgetProjections(LocalDate.now().toEpochDay()).single().spent })
            var duplicateFailed = false
            runBlocking { try { dao.recordSubscriptionPayment(subscriptionId, link.dueEpochDay, expense.copy(id = "duplicate")) } catch (_: IllegalArgumentException) { duplicateFailed = true } }
            assertTrue(duplicateFailed)
            compose.onNodeWithTag("SubscriptionList").performScrollToNode(hasText("Paid · ${LocalDate.now()}", substring = true))
            compose.onNodeWithText("Paid · ${LocalDate.now()}", substring = true).performClick()
            field("Actual paid amount (IDR)", "47000"); save(); waitFor("Record payment")
            assertEquals(4_700_000L, runBlocking { dao.findEntry(link.entryId)!!.amountMinor })
            tap("Money"); compose.waitUntil(10000) { compose.onAllNodesWithTag("MoneyList").fetchSemanticsNodes().isNotEmpty() }; capture("linked-money")
            tap("Today"); capture("today-after-payment")
            assertTrue(runBlocking { database().observeToday(java.time.ZonedDateTime.now()).first().subscriptions.none { it.id == subscriptionId } })
            compose.onAllNodesWithText("Subscriptions").onFirst().performClick()
            tap("Review streaming")
            tap("Cancel subscription"); tap("Mark inactive")
            compose.onNodeWithText("Reactivate subscription").performScrollTo().assertExists(); capture("subscription-inactive-history")
            compose.onNodeWithTag("SubscriptionList").performScrollToNode(hasText("Paid · ${LocalDate.now()}", substring = true)); capture("subscription-payment-history")
            val before = runBlocking { BackupStore(database()).snapshot() }
            runBlocking { BackupStore(database()).replace(BackupCodec.decode(ByteArrayInputStream(BackupCodec.encode(before,"review-phase2")))) }
            assertEquals(before, runBlocking { BackupStore(database()).snapshot() })
            scenario.close()
            scenario = ActivityScenario.launch(intent)
            waitFor("New subscription"); tap("Inactive")
            compose.onAllNodesWithText("Review streaming", substring = true).onLast().performScrollTo().assertExists()
            assertEquals(1, runBlocking { database().personalDao().paymentsFor(subscriptionId).size })
            capture("subscriptions-reopened")
        } finally { scenario.close(); context.deleteDatabase("phase2-review-$reviewId.db") }
    }
}
