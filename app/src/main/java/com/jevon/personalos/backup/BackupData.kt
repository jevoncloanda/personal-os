package com.jevon.personalos.backup

import com.jevon.personalos.data.*
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class BackupData(
    val accounts: List<AccountEntity> = emptyList(), val categories: List<CategoryEntity> = emptyList(),
    val entries: List<EntryEntity> = emptyList(), val budgets: List<BudgetEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(), val reminders: List<ReminderEntity> = emptyList(),
    val subscriptions: List<SubscriptionEntity> = emptyList(), val payments: List<SubscriptionPaymentEntity> = emptyList(),
    val library: List<LibraryItemEntity> = emptyList(),
) {
    val counts get() = linkedMapOf("Accounts" to accounts.size, "Categories" to categories.size, "Transactions" to entries.size,
        "Budgets" to budgets.size, "Events" to events.size, "Reminders" to reminders.size,
        "Subscriptions" to subscriptions.size, "Payment links" to payments.size, "Library items" to library.size)

    /** Historical restore: archived references and legacy budget intervals are legal.
     * Current editor restrictions on selecting archived records are not historical constraints.
     */
    fun validate() {
        require(counts.values.sum() <= MAX_RECORDS) { "Backup exceeds the 20,000-record limit." }
        fun text(value: String, required: Boolean = false) {
            require(value.length <= MAX_TEXT && (!required || value.isNotBlank())) { "A required text field is missing or too long." }
            checkedBackupText(value)
        }
        fun <T> identities(rows: List<T>, id: (T) -> String) {
            val seen = HashSet<String>()
            rows.forEach { value -> val key = id(value); text(key, true); require(key.length <= 200 && seen.add(key)) { "Invalid or duplicate record ID." } }
        }
        fun currency(code: String) { require(code.matches(Regex("[A-Z]{3}"))) { "Invalid currency code." }; currencyDigits(code) }
        fun day(value: Long) { LocalDate.ofEpochDay(value) }
        fun timestamp(value: Long) { Instant.ofEpochMilli(value).atZone(ZoneId.of("UTC")) }
        fun recurrence(value: Recurrence) {
            require(value.interval > 0) { "Invalid recurrence interval." }; day(value.anchorEpochDay)
            value.untilEpochDay?.let { day(it); require(it >= value.anchorEpochDay) { "Invalid recurrence end date." } }
        }
        identities(accounts) { it.id }; identities(categories) { it.id }; identities(entries) { it.id }
        identities(budgets) { it.id }; identities(events) { it.id }; identities(reminders) { it.id }
        identities(subscriptions) { it.id }; identities(payments) { it.id }; identities(library) { it.id }
        val accountMap = accounts.associateBy { it.id }; val categoryMap = categories.associateBy { it.id }
        val entryMap = entries.associateBy { it.id }; val subscriptionMap = subscriptions.associateBy { it.id }
        accounts.forEach { text(it.name, true); text(it.notes); currency(it.currencyCode); require(it.type in accountTypes) { "Invalid account type." } }
        categories.forEach { text(it.name, true); require(it.kind == null || it.kind in listOf("EXPENSE", "INCOME")) { "Invalid category type." } }
        val totals = HashMap<Pair<String, EntryKind>, BigInteger>()
        val incoming = HashMap<String, BigInteger>(); val outgoing = HashMap<String, BigInteger>()
        fun <K> sum(map: MutableMap<K, BigInteger>, key: K, amount: Long) {
            val result = (map[key] ?: BigInteger.ZERO) + BigInteger.valueOf(amount)
            require(result.bitLength() <= 63) { "Financial totals exceed the supported range." }; map[key] = result
        }
        entries.forEach { entry ->
            currency(entry.currencyCode); day(entry.occurredOnEpochDay); text(entry.note)
            require(entry.amountMinor > 0 && entry.minuteOfDay in 0..1439) { "Invalid transaction amount or time." }
            require(when (entry.kind) {
                EntryKind.EXPENSE -> entry.fromAccountId != null && entry.toAccountId == null
                EntryKind.INCOME -> entry.fromAccountId == null && entry.toAccountId != null
                EntryKind.TRANSFER -> entry.fromAccountId != null && entry.toAccountId != null && entry.fromAccountId != entry.toAccountId && entry.categoryId == null
            }) { "Invalid transaction accounts." }
            listOfNotNull(entry.fromAccountId, entry.toAccountId).forEach { id ->
                require(accountMap[id]?.currencyCode == entry.currencyCode) { "A transaction has a missing or mismatched account." }
            }
            entry.categoryId?.let { id ->
                val category = requireNotNull(categoryMap[id]) { "A transaction category is missing." }
                require(category.kind == null || category.kind == entry.kind.name) { "Transaction category type does not match." }
            }
            sum(totals, entry.currencyCode to entry.kind, entry.amountMinor)
            entry.fromAccountId?.let { sum(outgoing, it, entry.amountMinor) }
            entry.toAccountId?.let { sum(incoming, it, entry.amountMinor) }
        }
        accounts.forEach { account ->
            val balance = BigInteger.valueOf(account.openingBalanceMinor) + (incoming[account.id] ?: BigInteger.ZERO) - (outgoing[account.id] ?: BigInteger.ZERO)
            require(balance.bitLength() <= 63) { "An account balance exceeds the supported range." }
        }
        budgets.forEach { budget ->
            text(budget.name, true); currency(budget.currencyCode); day(budget.startEpochDay); day(budget.endExclusiveEpochDay)
            require(budget.limitMinor > 0 && budget.endExclusiveEpochDay > budget.startEpochDay) { "Invalid budget interval or limit." }
            budget.categoryId?.let { id -> require(categoryMap.containsKey(id) && categoryMap[id]?.kind != "INCOME") { "A budget category is missing or incompatible." } }
        }
        events.forEach { text(it.title, true); text(it.location); timestamp(it.startsAtMillis); timestamp(it.endsAtMillis); require(it.endsAtMillis > it.startsAtMillis); ZoneId.of(it.zoneId); recurrence(it.recurrence) }
        reminders.forEach { text(it.title, true); it.dueAtMillis?.let(::timestamp); it.completedAtMillis?.let(::timestamp); recurrence(it.recurrence) }
        subscriptions.forEach { s ->
            text(s.name, true); text(s.description); text(s.notes); currency(s.currencyCode)
            require(s.amountMinor > 0); day(s.nextDueEpochDay); recurrence(s.recurrence)
            s.accountId?.let { require(accountMap[it]?.currencyCode == s.currencyCode) { "Subscription account is missing or has another currency." } }
            s.categoryId?.let { require(categoryMap.containsKey(it) && categoryMap[it]?.kind != "INCOME") { "Subscription expense category is missing or incompatible." } }
        }
        val paymentPeriods = HashSet<Pair<String, Long>>(); val paymentEntries = HashSet<String>()
        payments.forEach { payment ->
            day(payment.dueEpochDay)
            val subscription = requireNotNull(subscriptionMap[payment.subscriptionId]) { "Payment subscription is missing." }
            val expense = requireNotNull(entryMap[payment.entryId]) { "Payment expense is missing." }
            require(expense.kind == EntryKind.EXPENSE && expense.currencyCode == subscription.currencyCode) { "Invalid payment expense." }
            require(paymentPeriods.add(payment.subscriptionId to payment.dueEpochDay) && paymentEntries.add(payment.entryId)) { "Duplicate subscription payment." }
        }
        library.forEach { text(it.title, true); timestamp(it.updatedAtMillis); require(it.progress >= 0 && (it.total == null || it.total > 0 && it.progress <= it.total)) { "Invalid library progress." } }
    }
}
