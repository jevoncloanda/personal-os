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
        if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithTag("SubscriptionList").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("SubscriptionList").performScrollToNode(hasText(text))
        }
        val node = compose.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) { /* Host actions have no scrolling ancestor. */ }
        node.performClick()
    }
    private fun field(label: String, text: String) = compose.onNodeWithText(label).performScrollTo().performTextReplacement(text)
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.uiAutomation.waitForIdle(500, 3000)
        android.os.SystemClock.sleep(1500)
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val folder = File(instrument.targetContext.getExternalFilesDir(null), "review/phase-2").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
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
            compose.onNodeWithText("Next unpaid: ${day.plusMonths(1)}").assertExists()
            tap("Record payment")
            compose.onNodeWithText("Renewal occurrence (YYYY-MM-DD)").assertExists()
            compose.onNodeWithText(day.plusMonths(1).toString()).assertExists()
            field("Renewal occurrence (YYYY-MM-DD)", "2500-01-31")
            tap("Choose renewal occurrence")
            compose.onNodeWithText("Use date").assertExists()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("2500-01-31").assertExists()
        } finally { scenario.close(); context.deleteDatabase("phase2-review-$reviewId.db") }
    }
    @Test fun managePayCorrectCancelAndReopenDisposableSubscriptions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reviewId = UUID.randomUUID().toString()
        val intent = Intent(context, SubscriptionReviewActivity::class.java).putExtra("reviewId", reviewId)
        var scenario = ActivityScenario.launch<SubscriptionReviewActivity>(intent)
        var db: PersonalDatabase? = null
        fun database(): PersonalDatabase { scenario.onActivity { db = it.databaseForTest() }; return requireNotNull(db) }
        fun waitFor(text: String) { compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        try {
            waitFor("New subscription"); capture("subscriptions-empty")
            tap("New subscription"); field("Subscription name", "Review streaming")
            field("Description (optional)", "Monthly household plan")
            field("Price", "49000")
            field("Next renewal date (YYYY-MM-DD)", LocalDate.now().toString())
            tap("Default payment account: Choose when paying"); tap("Review bank")
            tap("Default expense category: Uncategorized"); tap("Services")
            capture("subscription-editor")
            compose.onNodeWithText("Save").performClick(); waitFor("Record payment")
            val subscriptionId = runBlocking { database().personalDao().subscriptions().single().id }
            capture("subscription-detail-light")
            tap("All subscriptions"); tap("New subscription")
            field("Subscription name", "Review annual")
            field("Currency code", "USD"); field("Price", "120")
            tap("Billing frequency: Monthly"); tap("Yearly")
            compose.onNodeWithText("Save").performClick(); waitFor("Record payment")
            tap("All subscriptions"); capture("subscriptions-light")
            tap("Review dark"); capture("subscriptions-dark"); tap("Review light")
            compose.onNodeWithText("Review streaming", substring = true).performScrollTo().performClick()
            tap("Edit subscription"); field("Price", "50000")
            field("Next renewal date (YYYY-MM-DD)", LocalDate.now().toString())
            compose.onNodeWithText("Save").performClick(); waitFor("Record payment")
            tap("Record payment"); field("Actual paid amount (IDR)", "48000")
            capture("record-payment")
            compose.onNodeWithText("Save").performClick(); waitFor("Record payment")
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
            field("Actual paid amount (IDR)", "47000"); compose.onNodeWithText("Save").performClick(); waitFor("Record payment")
            assertEquals(4_700_000L, runBlocking { dao.findEntry(link.entryId)!!.amountMinor })
            tap("Money"); compose.waitUntil(10000) { compose.onAllNodesWithTag("MoneyList").fetchSemanticsNodes().isNotEmpty() }; capture("linked-money")
            tap("Today"); capture("today-after-payment")
            assertTrue(runBlocking { database().observeToday(java.time.ZonedDateTime.now()).first().subscriptions.none { it.id == subscriptionId } })
            compose.onAllNodesWithText("Subscriptions").onFirst().performClick()
            compose.onNodeWithText("Review streaming", substring = true).performScrollTo().performClick()
            tap("Cancel subscription"); tap("Mark inactive")
            compose.onNodeWithText("Reactivate subscription").performScrollTo().assertExists(); capture("subscription-inactive-history")
            compose.onNodeWithTag("SubscriptionList").performScrollToNode(hasText("Paid · ${LocalDate.now()}", substring = true)); capture("subscription-payment-history")
            val before = runBlocking { BackupStore(database()).snapshot() }
            runBlocking { BackupStore(database()).replace(BackupCodec.decode(ByteArrayInputStream(BackupCodec.encode(before,"review-phase2")))) }
            assertEquals(before, runBlocking { BackupStore(database()).snapshot() })
            scenario.close()
            scenario = ActivityScenario.launch(intent)
            waitFor("New subscription"); tap("Show: Active"); tap("Inactive")
            compose.onNodeWithText("Review streaming", substring = true).performScrollTo().assertExists()
            assertEquals(1, runBlocking { database().personalDao().paymentsFor(subscriptionId).size })
            capture("subscriptions-reopened")
        } finally { scenario.close(); context.deleteDatabase("phase2-review-$reviewId.db") }
    }
}
