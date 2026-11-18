package com.jevon.personalos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jevon.personalos.data.*
import com.jevon.personalos.today.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(state: TodayState, retry: () -> Unit, open: (String) -> Unit, selectBudget: ((String?) -> Unit)? = null,
    complete:((com.jevon.personalos.schedule.ReminderOccurrence)->Unit)?=null) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    when (state) {
        TodayState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading today" })
        }
        TodayState.Error -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Today couldn’t load", style = MaterialTheme.typography.titleLarge)
            Text("Your local data has not been changed. Try opening it again.", Modifier.padding(vertical = 12.dp))
            Button(onClick = retry) { Text("Try again") }
        }
        is TodayState.Ready -> {
            TodayBody(state.content, open,complete, if (selectBudget == null) null else { { choosing = true } })
            if (choosing && selectBudget != null) TodayBudgetPicker(state.content, selectBudget, { open("money-budgets") }) { choosing = false }
        }
    }
}

@Composable
internal fun TodayBudgetPicker(content: TodayContent, select: (String?) -> Unit, manage: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Today budget") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Choose an overall budget for ${YearMonth.from(content.date)}. Automatic uses ${content.preferredCurrency} only.")
            if (content.requestedBudgetId != null && content.eligibleBudgets.none { it.id == content.requestedBudgetId })
                Text("Your saved choice is no longer eligible this month. The automatic fallback is shown.", Modifier.padding(vertical = 8.dp))
            val options = listOf<String?>(null) + content.eligibleBudgets.sortedWith(compareBy<BudgetEntity> { it.currencyCode != content.preferredCurrency }.thenBy { it.id }).map { it.id }
            options.forEach { id ->
                val budget = content.eligibleBudgets.firstOrNull { it.id == id }
                TextButton(onClick = { select(id); dismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (id == content.requestedBudgetId) "Selected: " else "") +
                        (budget?.let { "${it.name} · ${it.currencyCode}" } ?: "Automatic · ${content.preferredCurrency}"))
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { dismiss(); manage() }) { Text("Manage budgets") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Close") } })
}

@Composable
private fun TodayBody(
    content: TodayContent,
    open: (String) -> Unit,
    complete: ((com.jevon.personalos.schedule.ReminderOccurrence) -> Unit)?,
    choose: (() -> Unit)?,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    content.date.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when (content.now.hour) {
                        in 5..11 -> "Good morning."
                        in 12..16 -> "Good afternoon."
                        else -> "Good evening."
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TodayAgenda(content, open, complete)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TodayMoneyAndLibrary(content, open, complete, choose)
                    }
                }
            } else {
                TodayAgenda(content, open, complete)
                TodayMoneyAndLibrary(content, open, complete, choose)
            }
        }
    }
}

@Composable
private fun TodayPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = LocalAtmosphere.current.panel,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun TodayHeading(icon: ImageVector, title: String, action: String, open: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            title,
            Modifier.weight(1f).padding(start = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = open, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(action, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TodayAgenda(
    content: TodayContent,
    open: (String) -> Unit,
    complete: ((com.jevon.personalos.schedule.ReminderOccurrence) -> Unit)?,
) {
    val imminent = content.events.filter {
        it.startsAtMillis <= content.now.plusHours(1).toInstant().toEpochMilli()
    }
    val dated = content.reminders.filter { it.dueAtMillis != null }
    if (imminent.isEmpty() && dated.isEmpty()) {
        Column {
            TodayHeading(AppIcons.CheckCircle, "Priorities", "Schedule", { open("schedule") })
            Text(
                "Nothing urgent today",
                Modifier.padding(start = 28.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    imminent.forEach { TodayEvent(it, content, open, urgent = true) }
    if (dated.isNotEmpty()) TodayPanel {
        TodayHeading(AppIcons.CheckCircle, "Action priorities", "Schedule", { open("schedule") })
        TodayReminders(content, dated, complete, open)
    }
}

@Composable
private fun TodayEvent(
    event: EventEntity,
    content: TodayContent,
    open: (String) -> Unit,
    urgent: Boolean,
) {
    val start = Instant.ofEpochMilli(event.startsAtMillis).atZone(content.now.zone)
    val ongoing = event.startsAtMillis <= content.now.toInstant().toEpochMilli()
    val nominal = content.scheduleEvents.firstOrNull {
        it.event.id == event.id && it.start == event.startsAtMillis
    }?.day
    val route = "event/${android.net.Uri.encode(event.id)}${nominal?.let { "?day=$it" }.orEmpty()}"
    Surface(
        onClick = { open(route) },
        shape = RoundedCornerShape(12.dp),
        color = LocalAtmosphere.current.panel,
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (urgent) Box(
                Modifier.width(5.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary),
            )
            Column(
                Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        color = if (urgent) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(
                            when {
                                ongoing -> "Happening now"
                                urgent -> "Starting soon"
                                start.toLocalDate() == content.date -> "Today"
                                else -> start.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
                            },
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (urgent) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (event.allDay) "All day" else start.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                if (event.location.isNotBlank()) Text(
                    event.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("View event", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Icon(AppIcons.ChevronRight, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun TodayReminders(
    content: TodayContent,
    reminders: List<ReminderEntity>,
    complete: ((com.jevon.personalos.schedule.ReminderOccurrence) -> Unit)?,
    open: (String) -> Unit,
) {
    reminders.forEach { reminder ->
        val occurrence = content.scheduleReminders.firstOrNull {
            it.reminder.id == reminder.id && it.due == reminder.dueAtMillis
        }
        val due = reminder.dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(content.now.zone) }
        val overdue = due != null && if (reminder.dateOnly) due.toLocalDate() < content.date
            else due.isBefore(content.now)
        val detail = when {
            due == null -> "No due date"
            overdue -> "Overdue · ${due.format(DateTimeFormatter.ofPattern("d MMM"))}"
            reminder.dateOnly -> "Due today"
            else -> "Today · ${due.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}"
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (occurrence != null && complete != null) Checkbox(
                    checked = occurrence.completed,
                    onCheckedChange = { complete(occurrence) },
                    modifier = Modifier.semantics {
                        contentDescription = "${if (occurrence.completed) "Reopen" else "Complete"} ${reminder.title} ${occurrence.day?.let { LocalDate.ofEpochDay(it) } ?: "undated"}"
                    },
                ) else Icon(
                    AppIcons.RadioButtonUnchecked, null,
                    Modifier.padding(12.dp).size(20.dp),
                    tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Row(
                    Modifier.weight(1f).clickable {
                        val day = occurrence?.day?.let { "?day=$it" }.orEmpty()
                        open("reminder/${android.net.Uri.encode(reminder.id)}$day")
                    }.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 6.dp)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(reminder.title, style = MaterialTheme.typography.titleSmall)
                        Text(detail, style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(AppIcons.ChevronRight, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TodayMoneyAndLibrary(
    content: TodayContent,
    open: (String) -> Unit,
    complete: ((com.jevon.personalos.schedule.ReminderOccurrence) -> Unit)?,
    choose: (() -> Unit)?,
) {
    TodayFinances(content, open, choose)
    val later = content.events.filter {
        it.startsAtMillis > content.now.plusHours(1).toInstant().toEpochMilli()
    }
    if (later.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TodayHeading(AppIcons.CalendarMonth, "Coming up", "Schedule", { open("schedule") })
        later.forEach { TodayEvent(it, content, open, urgent = false) }
    }
    if (content.subscriptions.isEmpty()) {
        Column {
            TodayHeading(AppIcons.Autorenew, "Upcoming payments", "Subscriptions", { open("subscriptions") })
            Text("No renewals in the next 7 days", Modifier.padding(start = 28.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else TodayPanel {
        TodayHeading(AppIcons.Autorenew, "Upcoming payments", "Subscriptions", { open("subscriptions") })
        content.subscriptions.forEach {
            TodayPayment(it, content.date) { open("subscription/${it.id}") }
        }
    }
    if (content.library.isEmpty()) {
        Column {
            TodayHeading(AppIcons.Movie, "Currently enjoying", "Library", { open("library") })
            Text("Your active titles will appear here", Modifier.padding(start = 28.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else TodayPanel {
        TodayHeading(AppIcons.Movie, "Currently enjoying", "Library", { open("library") })
        content.library.forEach { item ->
            Surface(
                onClick = { open("library/${android.net.Uri.encode(item.id)}") },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryCover(item, Modifier.size(48.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        if (item.subtitle.isNotBlank()) Text(
                            item.subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LibraryProgress(item, compact = true)
                    }
                }
            }
        }
    }
    val undated = content.reminders.filter { it.dueAtMillis == null }
    if (undated.isNotEmpty()) TodayPanel {
        TodayHeading(AppIcons.CheckCircle, "When you have a moment", "Reminders", { open("schedule") })
        TodayReminders(content, undated, complete, open)
    }
}

@Composable
private fun TodayFinances(content: TodayContent, open: (String) -> Unit, choose: (() -> Unit)?) {
    val summary = content.budget
    TodayPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.AccountBalanceWallet, null, Modifier.size(20.dp),
                tint = LocalAtmosphere.current.positive)
            Text("Monthly finances",
                Modifier.weight(1f).padding(start = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleMedium)
            if (choose != null) IconButton(onClick = choose) {
                Icon(AppIcons.ExpandMore, "Choose Today budget", Modifier.size(20.dp))
            }
            TextButton(onClick = { open("money") }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Money", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (summary == null) {
            Text("No ${content.preferredCurrency} budget this month",
                style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Choose an overall monthly budget.", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { open("money-budgets") },
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Manage budgets", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Text("${summary.budget.name} · ${summary.budget.currencyCode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column {
                    Text("Spent this month", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(summary.spentMinor, summary.budget.currencyCode),
                        style = MaterialTheme.typography.headlineLarge)
                }
                Column(Modifier.align(Alignment.Bottom).padding(bottom = 4.dp)) {
                    Text("Budget", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(summary.budget.limitMinor, summary.budget.currencyCode),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            val remaining = summary.remainingMinor
            LinearProgressIndicator(
                progress = { summary.fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (remaining < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                drawStopIndicator = {},
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "${formatMoney(java.math.BigInteger.valueOf(remaining).abs(), summary.budget.currencyCode)} ${if (remaining < 0) "over budget" else "remaining"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (remaining < 0) MaterialTheme.colorScheme.error
                        else LocalAtmosphere.current.positive,
                )
                val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(
                    content.date, content.date.withDayOfMonth(content.date.lengthOfMonth()),
                )
                Text("$daysLeft days left", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TodayPayment(item: SubscriptionEntity, date: LocalDate, open: () -> Unit) {
    val due = LocalDate.ofEpochDay(item.nextDueEpochDay)
    val whenDue = when {
        due < date -> "Overdue · ${due.format(DateTimeFormatter.ofPattern("d MMM"))}"
        due == date -> "Due today"
        due == date.plusDays(1) -> "Tomorrow, ${due.format(DateTimeFormatter.ofPattern("d MMM"))}"
        else -> due.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    Surface(
        onClick = open,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconBadge(AppIcons.Autorenew)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(whenDue, style = MaterialTheme.typography.bodySmall,
                        color = if (due <= date.plusDays(1)) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Unpaid forecast · ${item.currencyCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMoney(item.amountMinor, item.currencyCode),
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
internal fun SectionHeading(title: String, action: String, onClick: () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center) {
        Text(title, Modifier.align(Alignment.CenterVertically).padding(vertical = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
internal fun EmptyMessage(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(description, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyTodayPreview() { PersonalOsTheme(Settings(dynamicColor = false)) { Surface { TodayScreen(TodayState.Ready(Snapshot().forToday(ZonedDateTime.parse("2026-10-22T09:00:00+07:00[Asia/Jakarta]"))), {}, {}) } } }
