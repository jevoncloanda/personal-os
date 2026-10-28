package com.jevon.personalos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Bulk recovery access only. BackupStore owns validation and the encompassing transaction. */
@Dao
internal interface BackupDao {
    @Query("SELECT (SELECT COUNT(*) FROM accounts)+(SELECT COUNT(*) FROM categories)+(SELECT COUNT(*) FROM entries)+(SELECT COUNT(*) FROM budgets)+(SELECT COUNT(*) FROM events)+(SELECT COUNT(*) FROM reminders)+(SELECT COUNT(*) FROM subscriptions)+(SELECT COUNT(*) FROM subscription_payments)+(SELECT COUNT(*) FROM library_items)")
    suspend fun recordCount(): Long
    @Query("SELECT COALESCE(SUM(bytes),0) FROM (SELECT length(CAST(id||name||currencyCode||type||notes AS BLOB)) AS bytes FROM accounts UNION ALL SELECT length(CAST(id||name||COALESCE(kind,'') AS BLOB)) FROM categories UNION ALL SELECT length(CAST(id||kind||currencyCode||COALESCE(fromAccountId,'')||COALESCE(toAccountId,'')||COALESCE(categoryId,'')||note AS BLOB)) FROM entries UNION ALL SELECT length(CAST(id||name||currencyCode||COALESCE(categoryId,'') AS BLOB)) FROM budgets UNION ALL SELECT length(CAST(id||title||zoneId||location||COALESCE(repeat_frequency,'') AS BLOB)) FROM events UNION ALL SELECT length(CAST(id||title||COALESCE(repeat_frequency,'') AS BLOB)) FROM reminders UNION ALL SELECT length(CAST(id||name||currencyCode||COALESCE(repeat_frequency,'') AS BLOB)) FROM subscriptions UNION ALL SELECT length(CAST(id||subscriptionId||entryId AS BLOB)) FROM subscription_payments UNION ALL SELECT length(CAST(id||title||kind||status AS BLOB)) FROM library_items)")
    suspend fun textBytes(): Long
    @Query("SELECT COALESCE(SUM(length(CAST(description||notes||COALESCE(accountId,'')||COALESCE(categoryId,'') AS BLOB))),0) FROM subscriptions")
    suspend fun subscriptionTextBytes(): Long
    @Query("SELECT * FROM accounts ORDER BY id") suspend fun accounts(): List<AccountEntity>
    @Query("SELECT * FROM categories ORDER BY id") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM entries ORDER BY id") suspend fun entries(): List<EntryEntity>
    @Query("SELECT * FROM budgets ORDER BY id") suspend fun budgets(): List<BudgetEntity>
    @Query("SELECT * FROM events ORDER BY id") suspend fun events(): List<EventEntity>
    @Query("SELECT * FROM reminders ORDER BY id") suspend fun reminders(): List<ReminderEntity>
    @Query("SELECT * FROM subscriptions ORDER BY id") suspend fun subscriptions(): List<SubscriptionEntity>
    @Query("SELECT * FROM subscription_payments ORDER BY id") suspend fun payments(): List<SubscriptionPaymentEntity>
    @Query("SELECT * FROM library_items ORDER BY id") suspend fun library(): List<LibraryItemEntity>
    @Insert suspend fun accounts(rows: List<AccountEntity>)
    @Insert suspend fun categories(rows: List<CategoryEntity>)
    @Insert suspend fun entries(rows: List<EntryEntity>)
    @Insert suspend fun budgets(rows: List<BudgetEntity>)
    @Insert suspend fun events(rows: List<EventEntity>)
    @Insert suspend fun reminders(rows: List<ReminderEntity>)
    @Insert suspend fun subscriptions(rows: List<SubscriptionEntity>)
    @Insert suspend fun payments(rows: List<SubscriptionPaymentEntity>)
    @Insert suspend fun library(rows: List<LibraryItemEntity>)
    @Query("DELETE FROM subscription_payments") suspend fun clearPayments()
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM events") suspend fun clearEvents()
    @Query("DELETE FROM reminders") suspend fun clearReminders()
    @Query("DELETE FROM subscriptions") suspend fun clearSubscriptions()
    @Query("DELETE FROM library_items") suspend fun clearLibrary()
}
