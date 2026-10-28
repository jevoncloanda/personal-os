package com.jevon.personalos.data

import androidx.room.*

enum class EntryKind { EXPENSE, INCOME, TRANSFER }
enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }
enum class LibraryKind { MOVIE, SERIES, GAME, BOOK }
enum class LibraryStatus { PLANNED, ACTIVE, COMPLETED, DROPPED }

class DatabaseConverters {
    @TypeConverter fun entry(value: String): EntryKind = EntryKind.valueOf(value)
    @TypeConverter fun entry(value: EntryKind): String = value.name
    @TypeConverter fun frequency(value: String?): Frequency? = value?.let(Frequency::valueOf)
    @TypeConverter fun frequency(value: Frequency?): String? = value?.name
    @TypeConverter fun kind(value: String): LibraryKind = LibraryKind.valueOf(value)
    @TypeConverter fun kind(value: LibraryKind): String = value.name
    @TypeConverter fun status(value: String): LibraryStatus = LibraryStatus.valueOf(value)
    @TypeConverter fun status(value: LibraryStatus): String = value.name
}

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currencyCode: String,
    val openingBalanceMinor: Long = 0,
    val archived: Boolean = false,
    @ColumnInfo(defaultValue = "'OTHER'") val type: String = "OTHER",
    @ColumnInfo(defaultValue = "''") val notes: String = "",
)

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val kind: String? = null, @ColumnInfo(defaultValue = "0") val archived: Boolean = false)

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["fromAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("fromAccountId"), Index("toAccountId"), Index("categoryId"), Index("occurredOnEpochDay")],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val kind: EntryKind,
    val amountMinor: Long,
    val currencyCode: String,
    val occurredOnEpochDay: Long,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    @ColumnInfo(defaultValue = "0") val minuteOfDay: Int = 0,
) {
    init {
        require(amountMinor > 0) { "Amounts must be positive minor units" }
        require(currencyCode.matches(Regex("[A-Z]{3}")))
        require(when (kind) {
            EntryKind.EXPENSE -> fromAccountId != null && toAccountId == null
            EntryKind.INCOME -> fromAccountId == null && toAccountId != null
            EntryKind.TRANSFER -> fromAccountId != null && toAccountId != null && fromAccountId != toAccountId && categoryId == null
        }) { "Entry accounts must match its kind; transfers cannot carry an expense category" }
    }
}

@Entity(
    tableName = "budgets",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("categoryId"), Index(value = ["startEpochDay", "endExclusiveEpochDay"])],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currencyCode: String,
    val limitMinor: Long,
    val startEpochDay: Long,
    val endExclusiveEpochDay: Long,
    val categoryId: String? = null,
) {
    init { require(limitMinor > 0 && endExclusiveEpochDay > startEpochDay) }
}

// A bounded frequency model, not an incomplete RFC 5545 parser.
data class Recurrence(
    val frequency: Frequency?,
    val interval: Int = 1,
    val anchorEpochDay: Long = 0,
    val untilEpochDay: Long? = null,
) { init { require(interval > 0) } }

@Entity(tableName = "events", indices = [Index("startsAtMillis")])
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startsAtMillis: Long,
    val endsAtMillis: Long,
    val zoneId: String,
    val allDay: Boolean = false,
    val location: String = "",
    @Embedded(prefix = "repeat_") val recurrence: Recurrence = Recurrence(null),
) { init { require(endsAtMillis > startsAtMillis) } }

@Entity(tableName = "reminders", indices = [Index("dueAtMillis")])
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val dueAtMillis: Long?,
    val completedAtMillis: Long? = null,
    @Embedded(prefix = "repeat_") val recurrence: Recurrence = Recurrence(null),
)

@Entity(tableName = "subscriptions", indices = [Index("nextDueEpochDay"), Index("accountId"), Index("categoryId")], foreignKeys = [
    ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
])
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amountMinor: Long,
    val currencyCode: String,
    val nextDueEpochDay: Long,
    val active: Boolean = true,
    @Embedded(prefix = "repeat_") val recurrence: Recurrence,
    @ColumnInfo(defaultValue = "''") val description: String = "",
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
) { init { require(amountMinor > 0) } }

@Entity(
    tableName = "subscription_payments",
    foreignKeys = [
        ForeignKey(entity = SubscriptionEntity::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["subscriptionId", "dueEpochDay"], unique = true), Index(value = ["entryId"], unique = true)],
)
data class SubscriptionPaymentEntity(
    @PrimaryKey val id: String,
    val subscriptionId: String,
    val dueEpochDay: Long,
    val entryId: String,
)

@Entity(tableName = "library_items", indices = [Index("status")])
data class LibraryItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: LibraryKind,
    val status: LibraryStatus,
    val progress: Int = 0,
    val total: Int? = null,
    val updatedAtMillis: Long,
) {
    init { require(progress >= 0 && (total == null || total > 0 && progress <= total)) }
}
