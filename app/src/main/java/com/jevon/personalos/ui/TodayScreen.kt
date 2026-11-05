package com.jevon.personalos.ui

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
            if (choosing && selectBudget != null) AlertDialog(onDismissRequest = { choosing = false },
                title = { Text("Today budget") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Choose an overall budget for ${YearMonth.from(state.content.date)}. Automatic uses ${state.content.preferredCurrency} only.")
                    if (state.content.requestedBudgetId != null && state.content.eligibleBudgets.none { it.id == state.content.requestedBudgetId })
                        Text("Your saved choice is no longer eligible this month. The automatic fallback is shown.", Modifier.padding(vertical = 8.dp))
                    val options = listOf<String?>(null) + state.content.eligibleBudgets.sortedWith(compareBy<BudgetEntity> { it.currencyCode != state.content.preferredCurrency }.thenBy { it.id }).map { it.id }
                    options.forEach { id ->
                        val budget = state.content.eligibleBudgets.firstOrNull { it.id == id }
                        TextButton(onClick = { selectBudget(id); choosing = false }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (id == state.content.requestedBudgetId) "Selected: " else "") +
                                (budget?.let { "${it.name} · ${it.currencyCode}" } ?: "Automatic · ${state.content.preferredCurrency}"))
                        }
                    }
                } },
                confirmButton = { TextButton(onClick = { choosing = false; open("money-budgets") }) { Text("Manage budgets") } },
                dismissButton = { TextButton(onClick = { choosing = false }) { Text("Close") } })
        }
    }
}

@Composable
private fun TodayBody(content: TodayContent, open: (String) -> Unit,complete:((com.jevon.personalos.schedule.ReminderOccurrence)->Unit)?, choose: (() -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 28.dp),
        ) {
            Text(content.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(when (content.now.hour) { in 5..11 -> "Good morning."; in 12..16 -> "Good afternoon."; else -> "Good evening." },
                style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 16.dp))
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Column(Modifier.weight(1f)) { Agenda(content, open,complete) }
                    Column(Modifier.weight(1f)) { MoneyAndLibrary(content, open,complete,choose) }
                }
            } else { Agenda(content, open,complete); MoneyAndLibrary(content, open,complete,choose) }
        }
    }
}

@Composable
private fun Agenda(content: TodayContent, open: (String) -> Unit,complete:((com.jevon.personalos.schedule.ReminderOccurrence)->Unit)?) {
    val imminent = content.events.filter { it.startsAtMillis <= content.now.plusHours(1).toInstant().toEpochMilli() }
    val dueSoon = content.reminders.filter { it.dueAtMillis != null }
    if (imminent.isEmpty() && dueSoon.isEmpty()) {
        FrostedPanel(contentPadding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(AppIcons.CheckCircle, positive = true)
                Column(Modifier.weight(1f)) {
                    Text("Priorities", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text("Nothing urgent", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { open("schedule") }) { Text("Schedule") }
            }
        }
        Spacer(Modifier.height(16.dp))
        return
    }
    FrostedPanel {
    SectionHeading("Priorities", "Schedule", { open("schedule") })
    if(content.scheduleReminders.isNotEmpty()&&complete!=null)content.scheduleReminders.filter{it.due!=null}.forEach{item->ReminderRow(item,content.now,false,{complete(item)},{open("reminder/${android.net.Uri.encode(item.reminder.id)}?day=${item.day}")})}
    else dueSoon.forEach { reminder ->
        val due = Instant.ofEpochMilli(requireNotNull(reminder.dueAtMillis)).atZone(content.now.zone)
        val overdue = due.isBefore(content.now)
        ContentRow(AppIcons.RadioButtonUnchecked, reminder.title,
            if (overdue) "Overdue · ${due.format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}" else "Today · ${due.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}",
            supportingColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    EventRows(imminent, content,open)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun EventRows(events: List<EventEntity>, content: TodayContent,open:(String)->Unit) {
    events.forEachIndexed { index, event ->
        val start = Instant.ofEpochMilli(event.startsAtMillis).atZone(content.now.zone)
        val ongoing = event.startsAtMillis <= content.now.toInstant().toEpochMilli()
        val nominal=content.scheduleEvents.firstOrNull{it.event.id==event.id&&it.start==event.startsAtMillis}?.day
        Row(Modifier.fillMaxWidth().clickable{open("event/${android.net.Uri.encode(event.id)}${nominal?.let{"?day=$it"}.orEmpty()}")}.heightIn(min=48.dp).padding(vertical = 12.dp).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.widthIn(min = 58.dp)) {
                Text(if (event.allDay) "All day" else start.format(DateTimeFormatter.ofPattern(if(start.toLocalDate()==content.date)"HH:mm" else "d MMM\nHH:mm")),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (ongoing) Text("Now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                if (event.location.isNotBlank()) Text(event.location, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (index < events.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MoneyAndLibrary(content: TodayContent, open: (String) -> Unit,complete:((com.jevon.personalos.schedule.ReminderOccurrence)->Unit)?, choose: (() -> Unit)?) {
    SectionHeading("This month", "Money", { open("money") })
    val summary = content.budget
    if (choose != null) TextButton(onClick = choose) { Text("Choose Today budget") }
    if (summary == null) {
        EmptyMessage("No ${content.preferredCurrency} budget this month", "Create an overall monthly budget, or choose an existing budget in another currency.")
        TextButton(onClick = { open("money-budgets") }) { Text("Manage budgets") }
    }
    else FrostedPanel {
            Text("${summary.budget.name} · ${summary.budget.currencyCode}", style = MaterialTheme.typography.titleSmall)
            val remaining = summary.remainingMinor
            Text("${formatMoney(kotlin.math.abs(remaining), summary.budget.currencyCode)} ${if (remaining >= 0) "left" else "over budget"}",
                Modifier.padding(top = 2.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineMedium,
                color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            FinancialProgress(summary.fraction, remaining < 0, "${formatMoney(summary.spentMinor, summary.budget.currencyCode)} spent of ${formatMoney(summary.budget.limitMinor, summary.budget.currencyCode)}")
    }
    Spacer(Modifier.height(16.dp))
    val later = content.events.filter { it.startsAtMillis > content.now.plusHours(1).toInstant().toEpochMilli() }
    if (later.isNotEmpty()) {
        SectionHeading("Coming up", "Schedule", { open("schedule") })
        EventRows(later, content,open)
        Spacer(Modifier.height(16.dp))
    }
    SectionHeading("Upcoming payments", "Subscriptions", { open("subscriptions") })
    if (content.subscriptions.isEmpty()) EmptyMessage("No payments on the horizon", "Renewals due in the next seven days will appear here.")
    content.subscriptions.forEach { item ->
        val due = LocalDate.ofEpochDay(item.nextDueEpochDay)
        val whenDue = when { due < content.date -> "Overdue · ${due.format(DateTimeFormatter.ofPattern("d MMM"))}"; due == content.date -> "Due today"; due == content.date.plusDays(1) -> "Due tomorrow"; else -> "Due ${due.format(DateTimeFormatter.ofPattern("EEE, d MMM"))}" }
        TextButton(onClick = { open("subscription/${item.id}") }, modifier = Modifier.fillMaxWidth()) {
            ContentRow(AppIcons.Autorenew, item.name, "$whenDue · ${formatMoney(item.amountMinor, item.currencyCode)} unpaid forecast",
                supportingColor = if (due < content.date) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionHeading("Currently enjoying", "Library", { open("library") })
    if (content.library.isEmpty()) EmptyMessage("A little space for downtime", "What you’re watching, playing, or reading will appear here.")
    content.library.forEach { item ->
        LibraryRow(item) { open("library/${android.net.Uri.encode(item.id)}") }
    }
    val undated = content.reminders.filter { it.dueAtMillis == null }
    if (undated.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        SectionHeading("When you have a moment", "Reminders", { open("schedule") })
        if(complete!=null&&content.scheduleReminders.isNotEmpty())content.scheduleReminders.filter{it.due==null}.forEach{item->ReminderRow(item,content.now,false,{complete(item)},{open("reminder/${android.net.Uri.encode(item.reminder.id)}")})}
        else undated.forEach { ContentRow(AppIcons.RadioButtonUnchecked, it.title, "No due date") }
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

@Composable
private fun ContentRow(icon: ImageVector, title: String, supporting: String, supportingColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(supporting, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = supportingColor)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyTodayPreview() { PersonalOsTheme(Settings(dynamicColor = false)) { Surface { TodayScreen(TodayState.Ready(Snapshot().forToday(ZonedDateTime.parse("2026-10-22T09:00:00+07:00[Asia/Jakarta]"))), {}, {}) } } }
