package com.jevon.personalos.data

import androidx.room.*
import com.jevon.personalos.today.BudgetSummary
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency

val accountTypes = listOf("CASH", "BANK", "E_WALLET", "INVESTMENT", "OTHER")
const val DEFAULT_CURRENCY = "IDR"

fun currencyDigits(code: String): Int {
    val currency = Currency.getInstance(code)
    require(currency.defaultFractionDigits >= 0) { "Choose a currency with monetary units" }
    return if (code == "IDR") 2 else currency.defaultFractionDigits
}

/** Unambiguous decimal input: no grouping/exponents, exact currency precision. */
fun parseMoney(text: String, code: String, signed: Boolean = false): Long {
    require(text.matches(if (signed) Regex("-?[0-9]+(\\.[0-9]+)?") else Regex("[0-9]+(\\.[0-9]+)?"))) { "Use digits and a decimal point, without grouping" }
    val value = try { BigDecimal(text).setScale(currencyDigits(code), RoundingMode.UNNECESSARY).movePointRight(currencyDigits(code)).longValueExact() }
    catch (_: ArithmeticException) { throw IllegalArgumentException("Amount is too large or has too many decimal places") }
    require(signed || value > 0) { "Amount must be greater than zero" }
    return value
}

fun moneyInput(value: Long, code: String): String = BigDecimal.valueOf(value, currencyDigits(code)).toPlainString()

data class AccountBalance(@Embedded val account: AccountEntity, val incoming: Long, val outgoing: Long) {
    val balance: Long get() {
        val exact = BigInteger.valueOf(account.openingBalanceMinor).add(BigInteger.valueOf(incoming)).subtract(BigInteger.valueOf(outgoing))
        if (exact.bitLength() > 63) throw ArithmeticException("Account balance exceeds signed 64-bit range")
        return exact.toLong()
    }
}
data class CurrencyTotal(val currencyCode: String, val kind: EntryKind, val amount: Long)
data class CategorySpending(val categoryId: String?, val currencyCode: String, val amount: Long)
data class BudgetProjection(@Embedded val budget: BudgetEntity, val spent: Long) {
    fun summary() = BudgetSummary(budget, spent)
}
data class MoneySnapshot(
    val accounts: List<AccountBalance> = emptyList(), val categories: List<CategoryEntity> = emptyList(),
    val budgets: List<BudgetProjection> = emptyList(), val totals: List<CurrencyTotal> = emptyList(),
    val entries: List<EntryEntity> = emptyList(), val count: Int = 0,
    val recent: List<EntryEntity> = emptyList(),
    val categorySpending: List<CategorySpending> = emptyList(),
    val linkedSubscriptions: Map<String, String> = emptyMap(),
)

@Dao
abstract class MoneyDao {
    @Query("SELECT a.*, COALESCE((SELECT SUM(amountMinor) FROM entries WHERE toAccountId=a.id),0) AS incoming, COALESCE((SELECT SUM(amountMinor) FROM entries WHERE fromAccountId=a.id),0) AS outgoing FROM accounts a ORDER BY archived, name COLLATE NOCASE, id")
    abstract suspend fun balances(): List<AccountBalance>
    @Query("SELECT * FROM accounts WHERE id=:id") protected abstract suspend fun findAccount(id: String): AccountEntity?
    @Query("SELECT * FROM categories ORDER BY archived, name COLLATE NOCASE, id") abstract suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM categories WHERE id=:id") protected abstract suspend fun findCategory(id: String): CategoryEntity?
    @Query("SELECT * FROM entries WHERE id=:id") abstract suspend fun findEntry(id: String): EntryEntity?
    @Query("SELECT * FROM budgets WHERE id=:id") protected abstract suspend fun findBudget(id: String): BudgetEntity?
    @Query("SELECT (SELECT COUNT(*) FROM entries WHERE fromAccountId=:id OR toAccountId=:id)+(SELECT COUNT(*) FROM subscriptions WHERE accountId=:id)") protected abstract suspend fun accountUse(id: String): Int
    @Query("SELECT COUNT(*) FROM subscription_payments WHERE entryId=:id") protected abstract suspend fun paymentUse(id: String): Int
    @Query("SELECT currencyCode,kind,SUM(amountMinor) AS amount FROM entries WHERE kind!='TRANSFER' AND occurredOnEpochDay>=:start AND occurredOnEpochDay<:end GROUP BY currencyCode,kind ORDER BY currencyCode")
    abstract suspend fun totals(start: Long, end: Long): List<CurrencyTotal>
    @Query("SELECT categoryId,currencyCode,SUM(amountMinor) AS amount FROM entries WHERE kind='EXPENSE' AND occurredOnEpochDay>=:start AND occurredOnEpochDay<:end GROUP BY categoryId,currencyCode ORDER BY currencyCode,amount DESC,categoryId")
    abstract suspend fun categorySpending(start: Long, end: Long): List<CategorySpending>
    @Query("SELECT b.*, COALESCE((SELECT SUM(amountMinor) FROM entries e WHERE e.kind='EXPENSE' AND e.currencyCode=b.currencyCode AND e.occurredOnEpochDay>=b.startEpochDay AND e.occurredOnEpochDay<b.endExclusiveEpochDay AND (b.categoryId IS NULL OR b.categoryId=e.categoryId)),0) AS spent FROM budgets b WHERE b.startEpochDay<=:day AND b.endExclusiveEpochDay>:day ORDER BY b.currencyCode,b.categoryId,b.id")
    abstract suspend fun budgetProjections(day: Long): List<BudgetProjection>
    @Query("SELECT * FROM entries WHERE occurredOnEpochDay>=:start AND occurredOnEpochDay<:end AND (:kind='' OR kind=:kind) AND (:accountId='' OR fromAccountId=:accountId OR toAccountId=:accountId) AND (:categoryId='' OR categoryId=:categoryId) AND (:currency='' OR currencyCode=:currency) AND (instr(lower(note),lower(:search))>0 OR id IN (SELECT e.id FROM entries e LEFT JOIN accounts a ON a.id=e.fromAccountId LEFT JOIN accounts b ON b.id=e.toAccountId LEFT JOIN categories c ON c.id=e.categoryId WHERE instr(lower(COALESCE(a.name,'')||' '||COALESCE(b.name,'')||' '||COALESCE(c.name,'')),lower(:search))>0)) ORDER BY occurredOnEpochDay DESC,minuteOfDay DESC,id LIMIT :limit OFFSET :offset")
    abstract suspend fun history(start: Long, end: Long, kind: String, accountId: String, categoryId: String, currency: String, search: String, limit: Int, offset: Int): List<EntryEntity>
    @Query("SELECT COUNT(*) FROM entries WHERE occurredOnEpochDay>=:start AND occurredOnEpochDay<:end") abstract suspend fun entryCount(start: Long, end: Long): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun insertAccount(value: AccountEntity)
    @Update protected abstract suspend fun updateAccount(value: AccountEntity): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun insertCategory(value: CategoryEntity)
    @Update protected abstract suspend fun updateCategory(value: CategoryEntity): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun insertEntry(value: EntryEntity)
    @Update protected abstract suspend fun updateEntry(value: EntryEntity): Int
    @Query("DELETE FROM entries WHERE id=:id") protected abstract suspend fun removeEntry(id: String)
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun insertBudget(value: BudgetEntity)
    @Update protected abstract suspend fun updateBudget(value: BudgetEntity): Int
    @Query("DELETE FROM budgets WHERE id=:id") abstract suspend fun deleteBudget(id: String)
    @Query("SELECT COUNT(*) FROM budgets WHERE id!=:id AND currencyCode=:currency AND ((categoryId IS NULL AND :category IS NULL) OR categoryId=:category) AND startEpochDay<:end AND endExclusiveEpochDay>:start")
    protected abstract suspend fun budgetConflicts(id: String, currency: String, category: String?, start: Long, end: Long): Int
    @Query("SELECT currencyCode,kind,SUM(amountMinor) AS amount FROM entries GROUP BY currencyCode,kind") protected abstract suspend fun checkedTotals(): List<CurrencyTotal>

    @Transaction
    open suspend fun saveAccount(value: AccountEntity, create: Boolean) {
        require(value.id.isNotBlank() && value.name.isNotBlank()) { "Account name is required" }
        currencyDigits(value.currencyCode)
        require(value.type in accountTypes) { "Choose an account type" }
        if (create) insertAccount(value) else {
            val old = requireNotNull(findAccount(value.id)) { "Account no longer exists" }
            require(old.currencyCode == value.currencyCode || accountUse(value.id) == 0) { "Currency cannot change after transactions exist" }
            check(updateAccount(value) == 1)
        }
        checkArithmetic()
    }

    @Transaction
    open suspend fun saveCategory(value: CategoryEntity, create: Boolean) {
        require(value.id.isNotBlank() && value.name.isNotBlank()) { "Category name is required" }
        require(value.kind == null || value.kind in listOf("EXPENSE", "INCOME"))
        if (create) insertCategory(value) else {
            val old = requireNotNull(findCategory(value.id)) { "Category no longer exists" }
            require(old.kind == value.kind) { "Category type cannot change; create another category" }
            check(updateCategory(value) == 1)
        }
    }

    @Transaction
    open suspend fun ensureDefaultCategories() {
        // Only initialize an entirely empty catalog; never reseed an edited/archived catalog.
        if (categories().isEmpty()) {
            listOf("Food", "Transport", "Home", "Health", "Leisure", "Other expense").forEachIndexed { i, name -> insertCategory(CategoryEntity("default-expense-$i", name, "EXPENSE")) }
            listOf("Salary", "Interest", "Other income").forEachIndexed { i, name -> insertCategory(CategoryEntity("default-income-$i", name, "INCOME")) }
        }
    }

    private suspend fun validateEntry(entry: EntryEntity, old: EntryEntity?) {
        require(entry.id.isNotBlank())
        currencyDigits(entry.currencyCode)
        LocalDate.ofEpochDay(entry.occurredOnEpochDay)
        require(entry.minuteOfDay in 0..1439) { "Time must be between 00:00 and 23:59" }
        listOfNotNull(entry.fromAccountId, entry.toAccountId).forEach { id ->
            val account = requireNotNull(findAccount(id)) { "Account does not exist" }
            require(account.currencyCode == entry.currencyCode) { "Accounts must use the same currency; conversion is not supported" }
            require(!account.archived || id == old?.fromAccountId || id == old?.toAccountId) { "Choose an active account" }
        }
        entry.categoryId?.let { id ->
            val category = requireNotNull(findCategory(id)) { "Category does not exist" }
            require(!category.archived || old?.categoryId == id) { "Choose an active category" }
            require(category.kind == null || category.kind == entry.kind.name) { "Category must match expense or income" }
        }
    }

    @Transaction
    open suspend fun recordEntry(entry: EntryEntity) { saveEntry(entry, true) }

    @Transaction
    open suspend fun saveEntry(entry: EntryEntity, create: Boolean) {
        val old = if (create) null else requireNotNull(findEntry(entry.id)) { "Transaction no longer exists" }
        require(old == null || paymentUse(entry.id) == 0) { "Linked subscription payment. Open Subscriptions → payment history to correct it" }
        validateEntry(entry, old)
        if (create) insertEntry(entry) else check(updateEntry(entry) == 1)
        checkArithmetic()
    }

    @Transaction
    open suspend fun deleteEntry(id: String) {
        require(paymentUse(id) == 0) { "Linked subscription payment. Correct it in Subscriptions; canceling a plan preserves paid expenses" }
        removeEntry(id)
        checkArithmetic()
    }

    private suspend fun checkArithmetic() {
        // SQLite SUM raises on overflow; never use TOTAL (floating point). Any failure rolls back the write.
        checkedTotals()
        balances().forEach { it.balance }
    }

    @Transaction
    open suspend fun saveBudget(value: BudgetEntity, create: Boolean) {
        require(value.id.isNotBlank() && value.name.isNotBlank()) { "Budget name is required" }
        currencyDigits(value.currencyCode)
        val month = YearMonth.from(LocalDate.ofEpochDay(value.startEpochDay))
        require(value.startEpochDay == month.atDay(1).toEpochDay() && value.endExclusiveEpochDay == month.plusMonths(1).atDay(1).toEpochDay()) { "Choose a calendar month" }
        value.categoryId?.let {
            val c = requireNotNull(findCategory(it)) { "Category does not exist" }
            require((!c.archived || findBudget(value.id)?.categoryId == it) && c.kind != "INCOME") { "Choose an active expense category" }
        }
        require(budgetConflicts(value.id, value.currencyCode, value.categoryId, value.startEpochDay, value.endExclusiveEpochDay) == 0) { "An overlapping budget already exists for this currency and category" }
        if (create) insertBudget(value) else check(updateBudget(value) == 1) { "Budget no longer exists" }
    }
}
