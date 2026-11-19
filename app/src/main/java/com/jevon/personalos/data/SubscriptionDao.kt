package com.jevon.personalos.data

import androidx.room.*
import com.jevon.personalos.subscriptions.*
import java.time.LocalDate
import java.util.UUID

@Dao
abstract class SubscriptionDao : MoneyDao() {
    @Query("SELECT * FROM entries WHERE id IN (SELECT entryId FROM subscription_payments)")
    abstract suspend fun paymentExpenses(): List<EntryEntity>
    @Query("SELECT * FROM subscriptions ORDER BY active DESC,nextDueEpochDay,name COLLATE NOCASE,id")
    abstract suspend fun subscriptions(): List<SubscriptionEntity>
    @Query("SELECT * FROM subscriptions WHERE id=:id") abstract suspend fun subscription(id: String): SubscriptionEntity?
    @Query("SELECT * FROM subscription_payments ORDER BY dueEpochDay DESC,id") abstract suspend fun subscriptionPayments(): List<SubscriptionPaymentEntity>
    @Query("SELECT * FROM subscription_payments WHERE subscriptionId=:id ORDER BY dueEpochDay DESC") abstract suspend fun paymentsFor(id: String): List<SubscriptionPaymentEntity>
    @Query("SELECT * FROM subscription_payments WHERE id=:id") protected abstract suspend fun payment(id: String): SubscriptionPaymentEntity?
    @Insert protected abstract suspend fun insertSubscription(value: SubscriptionEntity)
    @Update protected abstract suspend fun updateSubscription(value: SubscriptionEntity): Int
    @Insert protected abstract suspend fun insertPayment(value: SubscriptionPaymentEntity)
    @Query("DELETE FROM subscription_payments WHERE id=:id") protected abstract suspend fun removePayment(id: String)

    @Transaction
    open suspend fun saveSubscription(value: SubscriptionEntity, create: Boolean) {
        require(value.id.isNotBlank() && value.id.length <= 200 && value.name.isNotBlank()) { "Subscription name is required" }
        require(listOf(value.name, value.description, value.notes).all { it.length <= 8192 }) { "Subscription text is too long" }
        currencyDigits(value.currencyCode)
        require(value.amountMinor > 0) { "Amount must be greater than zero" }
        val old = if (create) null else requireNotNull(subscription(value.id)) { "Subscription no longer exists" }
        val history = if (create) emptyList() else paymentsFor(value.id)
        require(history.isEmpty() || old?.currencyCode == value.currencyCode) { "Currency cannot change after payments; create a separate subscription" }
        val date = LocalDate.ofEpochDay(value.nextDueEpochDay)
        require(date.year in 1..9999 && value.recurrence.interval in 1..1000) { "Choose a valid date and interval (1–1000)" }
        LocalDate.ofEpochDay(value.recurrence.anchorEpochDay)
        require(renewalOnOrAfter(value, value.nextDueEpochDay) == value.nextDueEpochDay) { "Next renewal must belong to the billing schedule and precede its end" }
        value.accountId?.let { id ->
            val a = requireNotNull(findAccount(id)) { "Payment account no longer exists" }
            require(!a.isBudgetOnly && a.currencyCode == value.currencyCode && (!a.archived || old?.accountId == id)) { "Choose an active account in the subscription currency" }
        }
        value.categoryId?.let { id ->
            val c = requireNotNull(findCategory(id)) { "Expense category no longer exists" }
            require(c.kind != "INCOME" && (!c.archived || old?.categoryId == id)) { "Choose an active expense category" }
        }
        val next = nextUnpaid(value, history.map { it.dueEpochDay }.toSet())
        val saved = value.copy(nextDueEpochDay = next ?: value.nextDueEpochDay, active = value.active && next != null)
        if (create) insertSubscription(saved) else check(updateSubscription(saved) == 1)
    }

    @Transaction
    open suspend fun setSubscriptionActive(id: String, active: Boolean) {
        val value = requireNotNull(subscription(id)) { "Subscription no longer exists" }
        saveSubscription(value.copy(active = active), false)
    }

    @Transaction
    open suspend fun recordSubscriptionPayment(id: String, due: Long, expense: EntryEntity) {
        val value = requireNotNull(subscription(id)) { "Subscription no longer exists" }
        require(value.active) { "Reactivate the subscription before recording a payment" }
        require(renewalOnOrAfter(value, due) == due) { "Choose an unpaid renewal from the current schedule" }
        val paid = paymentsFor(id).map { it.dueEpochDay }.toSet()
        require(due !in paid) { "This renewal is already paid. Correct it in payment history" }
        require(expense.kind == EntryKind.EXPENSE && expense.currencyCode == value.currencyCode) { "Payment must be an expense in the subscription currency" }
        saveEntry(expense, true)
        insertPayment(SubscriptionPaymentEntity(UUID.randomUUID().toString(), id, due, expense.id))
        val next = nextUnpaid(value, paid + due)
        check(updateSubscription(value.copy(nextDueEpochDay = next ?: value.nextDueEpochDay, active = next != null)) == 1)
    }

    /** Temporarily detach inside this transaction so the ordinary ledger validator owns corrections. */
    @Transaction
    open suspend fun correctSubscriptionPayment(paymentId: String, expense: EntryEntity) {
        val link = requireNotNull(payment(paymentId)) { "Payment no longer exists" }
        val value = requireNotNull(subscription(link.subscriptionId))
        require(expense.id == link.entryId && expense.kind == EntryKind.EXPENSE && expense.currencyCode == value.currencyCode) { "Keep the linked expense and its currency" }
        removePayment(link.id)
        saveEntry(expense, false)
        insertPayment(link)
    }

    @Transaction
    open suspend fun undoSubscriptionPayment(paymentId: String) {
        val link = requireNotNull(payment(paymentId)) { "Payment no longer exists" }
        val value = requireNotNull(subscription(link.subscriptionId))
        removePayment(link.id)
        deleteEntry(link.entryId)
        // Reopen this occurrence only if it still belongs to the current schedule.
        val schedule = value.copy(nextDueEpochDay = value.recurrence.anchorEpochDay)
        if (renewalOnOrAfter(schedule, link.dueEpochDay) == link.dueEpochDay && link.dueEpochDay < value.nextDueEpochDay) {
            check(updateSubscription(value.copy(nextDueEpochDay = link.dueEpochDay)) == 1)
        }
    }
}
