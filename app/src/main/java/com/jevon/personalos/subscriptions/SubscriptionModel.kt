package com.jevon.personalos.subscriptions

import com.jevon.personalos.data.*
import com.jevon.personalos.today.occurrenceDate
import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Only subscription billing. Dates derive from the original anchor, never a clamped prior date. */
fun renewalOnOrAfter(value: SubscriptionEntity, day: Long): Long? {
    val r = value.recurrence
    val start = maxOf(day, value.nextDueEpochDay)
    if (r.frequency == null) return value.nextDueEpochDay.takeIf { it >= start && (r.untilEpochDay == null || it <= r.untilEpochDay) }
    val anchor = LocalDate.ofEpochDay(r.anchorEpochDay)
    val target = LocalDate.ofEpochDay(maxOf(start, r.anchorEpochDay))
    val units = when (r.frequency) {
        Frequency.DAILY -> ChronoUnit.DAYS.between(anchor, target)
        Frequency.WEEKLY -> ChronoUnit.WEEKS.between(anchor, target)
        Frequency.MONTHLY -> ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), target.withDayOfMonth(1))
        Frequency.YEARLY -> (target.year - anchor.year).toLong()
    }
    var index = (units / r.interval).coerceAtLeast(0)
    return try {
        var date = occurrenceDate(anchor, r.frequency, r.interval, index)
        if (date < target) date = occurrenceDate(anchor, r.frequency, r.interval, ++index)
        date.toEpochDay().takeIf { date.year in 1..9999 && (r.untilEpochDay == null || it <= r.untilEpochDay) }
    } catch (_: java.time.DateTimeException) { null } catch (_: ArithmeticException) { null }
}

fun nextUnpaid(value: SubscriptionEntity, paid: Set<Long>, from: Long = value.nextDueEpochDay): Long? {
    var date = renewalOnOrAfter(value, from)
    // A unique paid date can be visited at most once; work is bounded by persisted payment count.
    repeat(paid.size + 1) {
        val current = date ?: return null
        if (current !in paid) return current
        date = renewalOnOrAfter(value, current + 1)
    }
    return null
}

fun upcomingRenewals(value: SubscriptionEntity, paid: Set<Long>, from: Long, through: Long, limit: Int = 64): List<Long> {
    require(limit in 1..128)
    if (!value.active) return emptyList()
    val result = ArrayList<Long>()
    var date = nextUnpaid(value, paid, maxOf(from, value.nextDueEpochDay))
    while (date != null && date <= through && result.size < limit) {
        result += date
        date = nextUnpaid(value, paid, date + 1)
    }
    return result
}

data class RecurringCost(val currency: String, val monthlyMinor: BigInteger, val annualMinor: BigInteger)

/** Gregorian mean year = 146097/400 days. Sum exact rationals per currency, then HALF_UP once. */
fun recurringCosts(values: List<SubscriptionEntity>): List<RecurringCost> = values.filter { it.active && it.recurrence.frequency != null }
    .groupBy { it.currencyCode }.toSortedMap().map { (currency, rows) ->
        var n = BigInteger.ZERO; var d = BigInteger.ONE
        rows.forEach { row ->
            val (num, den) = when (row.recurrence.frequency) {
                Frequency.DAILY -> 146097L to 400L
                Frequency.WEEKLY -> 146097L to 2800L
                Frequency.MONTHLY -> 12L to 1L
                else -> 1L to 1L
            }
            val rn = BigInteger.valueOf(row.amountMinor) * BigInteger.valueOf(num)
            val rd = BigInteger.valueOf(den) * BigInteger.valueOf(row.recurrence.interval.toLong())
            n = n * rd + rn * d; d *= rd
            val gcd = n.gcd(d); n /= gcd; d /= gcd
        }
        fun rounded(den: BigInteger): BigInteger = (n * BigInteger.valueOf(2) + den) / (den * BigInteger.valueOf(2))
        RecurringCost(currency, rounded(d * BigInteger.valueOf(12)), rounded(d))
    }

fun billingLabel(value: SubscriptionEntity): String {
    val r = value.recurrence
    val name = when (r.frequency) { Frequency.DAILY -> "day"; Frequency.WEEKLY -> "week"; Frequency.MONTHLY -> "month"; Frequency.YEARLY -> "year"; null -> return "One-time" }
    return if (r.interval == 1) "Every $name" else "Every ${r.interval} ${name}s"
}
