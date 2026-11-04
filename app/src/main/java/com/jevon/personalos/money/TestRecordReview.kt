package com.jevon.personalos.money

import com.jevon.personalos.data.*
import com.jevon.personalos.today.formatMoney
import java.time.LocalDate

/** Only labels traced to the retained Phase 1 / 1.5 device harnesses. Never a deletion rule. */
fun hasDevelopmentLabel(label: String): Boolean =
    label.startsWith("P1 test ") || label.startsWith("P15 restore ")

data class TestRecordCandidate(val section: String, val id: String, val title: String, val details: List<String>)

fun reviewTestRecords(accounts: List<AccountEntity>, entries: List<EntryEntity>, categories: List<CategoryEntity>,
    budgets: List<BudgetEntity>, subscriptions: List<SubscriptionEntity>, payments: List<SubscriptionPaymentEntity>): List<TestRecordCandidate> {
    fun account(id: String?) = accounts.firstOrNull { it.id == id }?.let { "${it.name} [${it.id}]" } ?: "None"
    fun category(id: String?) = categories.firstOrNull { it.id == id }?.let { "${it.name} [${it.id}]" } ?: "Uncategorized"
    fun entry(e: EntryEntity) = "Transaction [${e.id}]: ${e.note.ifBlank { e.kind.name }} · ${e.currencyCode} ${formatMoney(e.amountMinor, e.currencyCode)} · ${LocalDate.ofEpochDay(e.occurredOnEpochDay)}\nFrom: ${account(e.fromAccountId)}; to: ${account(e.toAccountId)}; category: ${category(e.categoryId)}" +
        if (payments.any { it.entryId == e.id }) "\nLinked subscription payment: correct through Subscriptions payment history." else ""
    fun budget(b: BudgetEntity) = "Budget: ${b.name} [${b.id}] · ${b.currencyCode} ${formatMoney(b.limitMinor, b.currencyCode)} · ${LocalDate.ofEpochDay(b.startEpochDay)} to ${LocalDate.ofEpochDay(b.endExclusiveEpochDay)} (exclusive) · ${category(b.categoryId)}"
    fun subscription(s: SubscriptionEntity) = "Subscription: ${s.name} [${s.id}] · ${s.currencyCode}; history must be kept."
    return buildList {
        accounts.filter { hasDevelopmentLabel(it.name) }.forEach { a ->
            val linked = entries.filter { it.fromAccountId == a.id || it.toAccountId == a.id }
            add(TestRecordCandidate("Accounts", a.id, a.name, listOf("Account · ${a.currencyCode}${if (a.archived) " · Archived" else ""}. Archive to hide from active balances; history remains.") +
                linked.map(::entry) + subscriptions.filter { it.accountId == a.id }.map(::subscription) +
                budgets.filter { b -> linked.any { e -> e.kind == EntryKind.EXPENSE && e.currencyCode == b.currencyCode && e.occurredOnEpochDay in b.startEpochDay until b.endExclusiveEpochDay && (b.categoryId == null || b.categoryId == e.categoryId) } }.map(::budget)))
        }
        entries.filter { hasDevelopmentLabel(it.note) }.forEach { e ->
            add(TestRecordCandidate("History", e.id, e.note, listOf(entry(e), "Deleting an expense/income changes the account balance and spending totals. Deleting a transfer changes both balances. Review the date in History first.") +
                budgets.filter { b -> e.kind == EntryKind.EXPENSE && e.currencyCode == b.currencyCode && e.occurredOnEpochDay in b.startEpochDay until b.endExclusiveEpochDay && (b.categoryId == null || b.categoryId == e.categoryId) }.map(::budget)))
        }
        categories.filter { hasDevelopmentLabel(it.name) }.forEach { c ->
            add(TestRecordCandidate("Categories", c.id, c.name, listOf("Category. Archive to stop new use; past relationships remain.") + entries.filter { it.categoryId == c.id }.map(::entry) +
                budgets.filter { it.categoryId == c.id }.map(::budget) + subscriptions.filter { it.categoryId == c.id }.map(::subscription)))
        }
        budgets.filter { hasDevelopmentLabel(it.name) }.forEach { b ->
            add(TestRecordCandidate("Budgets", b.id, b.name, listOf(budget(b), "Deleting this limit keeps all transactions and account balances. Find its month in Budgets before acting.") +
                entries.filter { e -> e.kind == EntryKind.EXPENSE && e.currencyCode == b.currencyCode && e.occurredOnEpochDay in b.startEpochDay until b.endExclusiveEpochDay && (b.categoryId == null || b.categoryId == e.categoryId) }.map(::entry)))
        }
    }.sortedWith(compareBy<TestRecordCandidate> { it.section }.thenBy { it.id })
}
