package com.jevon.personalos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
fun TodayScreen(state: TodayState, retry: () -> Unit, open: (String) -> Unit) {
    when (state) {
        TodayState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading today" })
        }
        TodayState.Error -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Today couldn’t load", style = MaterialTheme.typography.titleLarge)
            Text("Your local data has not been changed. Try opening it again.", Modifier.padding(vertical = 12.dp))
            Button(onClick = retry) { Text("Try again") }
        }
        is TodayState.Ready -> TodayBody(state.content, open)
    }
}

@Composable
private fun TodayBody(content: TodayContent, open: (String) -> Unit) {
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
                    Column(Modifier.weight(1f)) { Agenda(content, open) }
                    Column(Modifier.weight(1f)) { MoneyAndLibrary(content, open) }
                }
            } else { Agenda(content, open); MoneyAndLibrary(content, open) }
        }
    }
}

@Composable
private fun Agenda(content: TodayContent, open: (String) -> Unit) {
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
    dueSoon.forEach { reminder ->
        val due = Instant.ofEpochMilli(requireNotNull(reminder.dueAtMillis)).atZone(content.now.zone)
        val overdue = due.isBefore(content.now)
        ContentRow(AppIcons.RadioButtonUnchecked, reminder.title,
            if (overdue) "Overdue · ${due.format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}" else "Today · ${due.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}",
            supportingColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    EventRows(imminent, content)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun EventRows(events: List<EventEntity>, content: TodayContent) {
    events.forEachIndexed { index, event ->
        val start = Instant.ofEpochMilli(event.startsAtMillis).atZone(content.now.zone)
        val ongoing = event.startsAtMillis <= content.now.toInstant().toEpochMilli()
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.widthIn(min = 58.dp)) {
                Text(if (event.allDay) "All day" else start.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
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
private fun MoneyAndLibrary(content: TodayContent, open: (String) -> Unit) {
    SectionHeading("This month", "Money", { open("money") })
    val summary = content.budget
    if (summary == null) {
        EmptyMessage("Give your spending a plan", "Set a monthly budget to see actual spending and what remains.")
        TextButton(onClick = { open("money") }) { Text("Set up in Money") }
    }
    else FrostedPanel {
            Text(summary.budget.name, style = MaterialTheme.typography.titleSmall)
            val remaining = summary.remainingMinor
            Text("${formatMoney(kotlin.math.abs(remaining), summary.budget.currencyCode)} ${if (remaining >= 0) "left" else "over budget"}",
                Modifier.padding(top = 2.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineMedium,
                color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            FinancialProgress(summary.fraction, remaining < 0, "${formatMoney(summary.spentMinor, summary.budget.currencyCode)} spent of ${formatMoney(summary.budget.limitMinor, summary.budget.currencyCode)}")
    }
    Spacer(Modifier.height(16.dp))
    val later = content.events.filter { it.startsAtMillis > content.now.plusHours(1).toInstant().toEpochMilli() }
    if (later.isNotEmpty()) {
        SectionHeading("Later today", "Schedule", { open("schedule") })
        EventRows(later, content)
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
        val progress = when (item.kind) {
            LibraryKind.SERIES -> "Episode ${item.progress}${item.total?.let { " of $it" } ?: ""}"
            LibraryKind.GAME -> "${item.progress} hours played"
            LibraryKind.BOOK -> "Page ${item.progress}${item.total?.let { " of $it" } ?: ""}"
            LibraryKind.MOVIE -> "${item.progress} minutes watched"
        }
        ContentRow(when (item.kind) { LibraryKind.GAME -> AppIcons.SportsEsports; LibraryKind.BOOK -> AppIcons.MenuBook; else -> AppIcons.Movie }, item.title, progress)
    }
    val undated = content.reminders.filter { it.dueAtMillis == null }
    if (undated.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        SectionHeading("When you have a moment", "Reminders", { open("schedule") })
        undated.forEach { ContentRow(AppIcons.RadioButtonUnchecked, it.title, "No due date") }
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
