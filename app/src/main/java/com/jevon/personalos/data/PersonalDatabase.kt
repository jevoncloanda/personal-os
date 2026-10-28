package com.jevon.personalos.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PersonalDao : ScheduleDao() {
    @Query("SELECT * FROM events ORDER BY startsAtMillis") abstract fun observeEvents(): Flow<List<EventEntity>>
    @Query("SELECT * FROM reminders WHERE completedAtMillis IS NULL ORDER BY dueAtMillis") abstract fun observeReminders(): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM budgets ORDER BY startEpochDay DESC") abstract fun observeBudgets(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM entries") abstract fun observeEntries(): Flow<List<EntryEntity>>
    @Query("SELECT * FROM subscriptions WHERE active = 1 ORDER BY nextDueEpochDay") abstract fun observeSubscriptions(): Flow<List<SubscriptionEntity>>
    @Query("SELECT * FROM library_items WHERE status = 'ACTIVE' ORDER BY updatedAtMillis DESC") abstract fun observeActiveLibrary(): Flow<List<LibraryItemEntity>>
    @Query("SELECT * FROM events WHERE endsAtMillis>:now AND startsAtMillis<:end ORDER BY startsAtMillis LIMIT 4") abstract suspend fun todayEvents(now: Long, end: Long): List<EventEntity>
    @Query("SELECT * FROM reminders WHERE completedAtMillis IS NULL AND (dueAtMillis IS NULL OR dueAtMillis<:end) ORDER BY dueAtMillis IS NULL,dueAtMillis LIMIT 4") abstract suspend fun todayReminders(end: Long): List<ReminderEntity>
    @Query("SELECT * FROM subscriptions WHERE active=1 AND nextDueEpochDay<=:end ORDER BY nextDueEpochDay LIMIT 4") abstract suspend fun todaySubscriptions(end: Long): List<SubscriptionEntity>
    @Query("SELECT * FROM library_items WHERE status='ACTIVE' ORDER BY updatedAtMillis DESC LIMIT 3") abstract suspend fun todayLibrary(): List<LibraryItemEntity>
}

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, EntryEntity::class, BudgetEntity::class,
        EventEntity::class, ReminderEntity::class, SubscriptionEntity::class,
        SubscriptionPaymentEntity::class, LibraryItemEntity::class, ReminderCompletionEntity::class,
        AlertPreferenceEntity::class, AlertStateEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class PersonalDatabase : RoomDatabase() {
    abstract fun personalDao(): PersonalDao
    internal abstract fun backupDao(): BackupDao
    companion object {
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE events ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN zoneId TEXT NOT NULL DEFAULT 'UTC'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN dateOnly INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS reminder_completions (reminderId TEXT NOT NULL, revision INTEGER NOT NULL, occurrenceEpochDay INTEGER NOT NULL, completedAtMillis INTEGER NOT NULL, PRIMARY KEY(reminderId,revision,occurrenceEpochDay), FOREIGN KEY(reminderId) REFERENCES reminders(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS alert_preferences (id TEXT NOT NULL PRIMARY KEY, eventId TEXT, reminderId TEXT, subscriptionId TEXT, leadMinutes INTEGER NOT NULL, minuteOfDay INTEGER NOT NULL, FOREIGN KEY(eventId) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(reminderId) REFERENCES reminders(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(subscriptionId) REFERENCES subscriptions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                listOf("eventId", "reminderId", "subscriptionId").forEach { column -> db.execSQL("CREATE UNIQUE INDEX index_alert_preferences_${column}_leadMinutes ON alert_preferences($column,leadMinutes)") }
                db.execSQL("CREATE TABLE IF NOT EXISTS alert_state (`key` TEXT NOT NULL PRIMARY KEY, domain TEXT NOT NULL, recordId TEXT NOT NULL, revision INTEGER NOT NULL, occurrenceEpochDay INTEGER NOT NULL, leadMinutes INTEGER NOT NULL, triggerAtMillis INTEGER NOT NULL, delivered INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX index_alert_state_delivered ON alert_state(delivered)")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN accountId TEXT REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN categoryId TEXT REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT")
                db.execSQL("CREATE INDEX index_subscriptions_accountId ON subscriptions(accountId)")
                db.execSQL("CREATE INDEX index_subscriptions_categoryId ON subscriptions(categoryId)")
            }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN type TEXT NOT NULL DEFAULT 'OTHER'")
                db.execSQL("ALTER TABLE accounts ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE categories ADD COLUMN kind TEXT")
                db.execSQL("ALTER TABLE categories ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE entries ADD COLUMN minuteOfDay INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
