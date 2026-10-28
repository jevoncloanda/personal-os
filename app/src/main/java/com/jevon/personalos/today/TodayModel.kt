package com.jevon.personalos.today

import com.jevon.personalos.data.*
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.time.*
import java.util.Currency
import java.util.Locale

data class Snapshot(
    val events: List<EventEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val entries: List<EntryEntity> = emptyList(),
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val library: List<LibraryItemEntity> = emptyList(),
)

data class BudgetSummary(val budget: BudgetEntity, val spentMinor: Long) {
    val remainingMinor get() = Math.subtractExact(budget.limitMinor, spentMinor)
    val fraction get() = (spentMinor.toDouble() / budget.limitMinor).coerceIn(0.0, 1.0).toFloat()
}

fun summarizeBudget(budget: BudgetEntity, entries: List<EntryEntity>): BudgetSummary = BudgetSummary(
    budget,
    entries.asSequence().filter {
        it.kind == EntryKind.EXPENSE && it.currencyCode == budget.currencyCode &&
            it.occurredOnEpochDay >= budget.startEpochDay && it.occurredOnEpochDay < budget.endExclusiveEpochDay &&
            (budget.categoryId == null || it.categoryId == budget.categoryId)
    }.fold(0L) { total, entry -> Math.addExact(total, entry.amountMinor) },
)

data class TodayContent(
    val date: LocalDate,
    val now: ZonedDateTime,
    val events: List<EventEntity>,
    val reminders: List<ReminderEntity>,
    val budget: BudgetSummary?,
    val subscriptions: List<SubscriptionEntity>,
    val library: List<LibraryItemEntity>,
)

fun Snapshot.forToday(now: ZonedDateTime): TodayContent {
    val date = now.toLocalDate()
    val end = date.plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
    return TodayContent(
        date, now,
        events.filter { it.endsAtMillis > now.toInstant().toEpochMilli() && it.startsAtMillis < end }.sortedBy { it.startsAtMillis }.take(4),
        reminders.filter { it.completedAtMillis == null && (it.dueAtMillis == null || it.dueAtMillis < end) }
            .sortedWith(compareBy<ReminderEntity> { it.dueAtMillis == null }.thenBy { it.dueAtMillis }).take(4),
        budgets.firstOrNull { it.categoryId == null && date.toEpochDay() >= it.startEpochDay && date.toEpochDay() < it.endExclusiveEpochDay }
            ?.let { summarizeBudget(it, entries) },
        subscriptions.filter { it.active && it.nextDueEpochDay <= date.plusDays(7).toEpochDay() }.sortedBy { it.nextDueEpochDay }.take(4),
        library.filter { it.status == LibraryStatus.ACTIVE }.sortedByDescending { it.updatedAtMillis }.take(3),
    )
}

fun defaultMoneyLocale(code: String): Locale = when (code) {
    "IDR" -> Locale.forLanguageTag("id-ID")
    "USD" -> Locale.US
    else -> Locale.getDefault()
}

fun formatMoney(minor: Long, code: String, locale: Locale = defaultMoneyLocale(code)): String {
    return formatMoney(BigInteger.valueOf(minor), code, locale)
}

/** Presentation-only aggregate precision; stored amounts remain signed 64-bit minor units. */
fun formatMoney(minor: BigInteger, code: String, locale: Locale = defaultMoneyLocale(code)): String {
    val currency = Currency.getInstance(code)
    // Android ICU treats IDR as zero-decimal for display, unlike ISO 4217/JVM.
    // Stored rupiah amounts use ISO's two minor digits on every runtime.
    val digits = if (code == "IDR") 2 else currency.defaultFractionDigits.coerceAtLeast(0)
    val value = BigDecimal(minor, digits)
    val formatted = NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = currency
        minimumFractionDigits = if (value.stripTrailingZeros().scale() <= 0) 0 else digits
        maximumFractionDigits = digits
    }.format(value)
    // Android ICU and the JVM differ in currency spacing. Keep the IDR presentation
    // deterministic without changing ISO storage precision or explicit locale overrides.
    return if (code == "IDR" && locale.language == "id") formatted.replace("\u00a0", "").replace("\u202f", "").replace("Rp ", "Rp") else formatted
}

/** Always derive from the original anchor: Jan 31 -> Feb 28 -> Mar 31. */
fun occurrenceDate(anchor: LocalDate, frequency: Frequency, interval: Int, occurrence: Long): LocalDate {
    require(interval > 0 && occurrence >= 0)
    val steps = Math.multiplyExact(interval.toLong(), occurrence)
    return when (frequency) {
        Frequency.DAILY -> anchor.plusDays(steps)
        Frequency.WEEKLY -> anchor.plusWeeks(steps)
        Frequency.MONTHLY -> anchor.plusMonths(steps)
        Frequency.YEARLY -> anchor.plusYears(steps)
    }
}
