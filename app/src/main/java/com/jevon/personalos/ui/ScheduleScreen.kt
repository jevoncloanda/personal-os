package com.jevon.personalos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jevon.personalos.data.*
import com.jevon.personalos.schedule.*
import com.jevon.personalos.today.formatMoney
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(model:ScheduleViewModel,requested:RecordTarget?,handled:()->Unit,openSubscription:(String)->Unit,configureAlerts:(RecordTarget)->Unit) {
    val data by model.state.collectAsStateWithLifecycle(); val selected by model.date.collectAsStateWithLifecycle()
    val focus by model.focus.collectAsStateWithLifecycle(); val busy by model.busy.collectAsStateWithLifecycle(); val error by model.error.collectAsStateWithLifecycle()
    var mode by rememberSaveable {mutableStateOf("Agenda")}; var editor by rememberSaveable {mutableStateOf<String?>(null)}
    var showCompleted by rememberSaveable {mutableStateOf(false)}; var deleting by rememberSaveable {mutableStateOf(false)}
    var visibleKind by rememberSaveable { mutableStateOf("All") }
    LaunchedEffect(requested){if(requested!=null){model.clearError();if(requested.domain.startsWith("NEW_")){model.focus.value=null;editor=requested.domain.removePrefix("NEW_")}else model.focus.value=requested;handled()}}
    BackHandler(focus!=null&&editor==null){model.focus.value=null;model.clearError()}
    val snapshot=data
    if(snapshot==null){Column(Modifier.padding(20.dp)){if(error==null)CircularProgressIndicator()else{Text(error!!);Button(onClick=model::retry){Text("Try again")}}};return}
    val event=focus?.takeIf{it.domain=="EVENT"}?.let{snapshot.eventRecords[it.id]}
    val reminder=focus?.takeIf{it.domain=="REMINDER"}?.let{snapshot.reminderRecords[it.id]}
    val now=model.now()
    fun open(target:RecordTarget){model.clearError();model.focus.value=target}
    LazyColumn(Modifier.fillMaxSize().testTag("ScheduleList"),
        contentPadding = if (focus == null) PaddingValues(16.dp, 0.dp, 16.dp, 88.dp)
            else PaddingValues(20.dp, 0.dp, 20.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if(focus==null){
            item {
                PorosCard(contentPadding = 12.dp, verticalSpacing = 4.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(selected.format(DateTimeFormatter.ofPattern("MMMM uuuu")),
                            Modifier.weight(1f).semantics { heading() },
                            style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { model.date.value = selected.minusMonths(1) }) {
                            Icon(AppIcons.ChevronLeft, "Previous month")
                        }
                        IconButton(onClick = { model.date.value = selected.plusMonths(1) }) {
                            Icon(AppIcons.ChevronRight, "Next month")
                        }
                        IconToggleButton(
                            checked = mode == "Month",
                            onCheckedChange = { mode = if (it) "Month" else "Agenda" },
                            modifier = Modifier.semantics { stateDescription = mode },
                        ) {
                            Icon(AppIcons.CalendarMonth,
                                if (mode == "Month") "Show week agenda" else "Show month calendar")
                        }
                    }
                    if (mode == "Agenda") WeekStrip(selected, now.toLocalDate(), snapshot, now.zone) {
                        model.date.value = it
                    } else MonthGrid(selected, now.toLocalDate(), snapshot, now.zone) {
                        model.date.value = it
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { model.date.value = selected.minusDays(1) }) {
                            Icon(AppIcons.ChevronLeft, "Previous day")
                        }
                        Text(selected.format(DateTimeFormatter.ofPattern("EEE, d MMM")),
                            Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { model.date.value = selected.plusDays(1) }) {
                            Icon(AppIcons.ChevronRight, "Next day")
                        }
                        TextButton(onClick = { model.date.value = now.toLocalDate() }) { Text("Today") }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("All", "Events", "Reminders").forEach { kind ->
                        FilterChip(selected = visibleKind == kind, onClick = { visibleKind = kind },
                            label = { Text(kind) }, shape = CircleShape,
                            modifier = Modifier.heightIn(min = 48.dp))
                    }
                    FilterChip(selected = showCompleted, onClick = { showCompleted = !showCompleted },
                        label = { Text(if (showCompleted) "Hide completed" else "Show completed") },
                        shape = CircleShape, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            item {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    FilledTonalButton(onClick={model.clearError();editor="EVENT"}){Icon(AppIcons.Add,null);Text("New event",Modifier.padding(start=6.dp))}
                    OutlinedButton(onClick={model.clearError();editor="REMINDER"}){Text("New reminder")}
                }
            }
            if(snapshot.truncated)item{Text("Showing bounded results. Select a narrower date or reduce overlapping series to see more.",color=MaterialTheme.colorScheme.error)}
            val from=selected.atStartOfDay(now.zone).toInstant().toEpochMilli()
            val until=selected.plusDays(if(mode=="Month")1 else 7).atStartOfDay(now.zone).toInstant().toEpochMilli()
            val overdue=if(selected==now.toLocalDate())snapshot.overdue else emptyList()
            if(overdue.isNotEmpty() && visibleKind != "Events"){
                item{Text("Overdue",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.error)}
                items(overdue,key={"overdue:${it.key}"}){ReminderRow(it,now,busy,{model.complete(it,!it.completed)},{open(RecordTarget("REMINDER",it.reminder.id,it.day))}, agenda = true)}
            }
            val overdueKeys=overdue.map{it.key}.toSet()
            val rows=ArrayList<AgendaItem>()
            if (visibleKind != "Reminders") snapshot.events.filter{it.end>from&&it.start<until}.forEach{rows+=AgendaItem(maxOf(it.start,from),"event:${it.event.id}:${it.day}",event=it)}
            if (visibleKind != "Events") snapshot.reminders.filter{it.due!=null&&it.due>=from&&it.due<until&&it.key !in overdueKeys&&(showCompleted||!it.completed)}.forEach{rows+=AgendaItem(it.due!!,"reminder:${it.key}",reminder=it)}
            if (visibleKind == "All") snapshot.forecasts.filter{it.day>=selected.toEpochDay()&&it.day<selected.plusDays(if(mode=="Month")1 else 7).toEpochDay()}.forEach{rows+=AgendaItem(LocalDate.ofEpochDay(it.day).atTime(9,0).atZone(now.zone).toInstant().toEpochMilli(),"forecast:${it.subscription.id}:${it.day}",forecast=it)}
            item {
                Text(if (mode == "Month") "Selected day" else "Next seven days",
                    Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            }
            if (rows.isEmpty()) item {
                PorosCard(contentPadding = 16.dp) {
                    Text("No scheduled items", style = MaterialTheme.typography.titleSmall)
                    Text("No events, dated reminders or unpaid renewals here. Add a commitment or choose another date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            rows.sortedWith(compareBy<AgendaItem>{it.time}.thenBy{it.key}).groupBy{Instant.ofEpochMilli(it.time).atZone(now.zone).toLocalDate()}.forEach{(day,items)->
                item("date:$day") {
                    Text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
                        Modifier.padding(top = 8.dp).semantics { heading() },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(items,key={it.key}){row->when{
                    row.event!=null->EventRow(row.event,now.zone){open(RecordTarget("EVENT",row.event.event.id,row.event.day))}
                    row.reminder!=null->ReminderRow(row.reminder,now,busy,{model.complete(row.reminder,!row.reminder.completed)},{open(RecordTarget("REMINDER",row.reminder.reminder.id,row.reminder.day))}, agenda = true)
                    row.forecast!=null->PorosCard(contentPadding = 12.dp, verticalSpacing = 0.dp) {
                        MoneyLedgerRow(AppIcons.Autorenew, row.forecast.subscription.name,
                            "Unpaid payment forecast · ${LocalDate.ofEpochDay(row.forecast.day)} · ${row.forecast.subscription.currencyCode}",
                            formatMoney(row.forecast.subscription.amountMinor, row.forecast.subscription.currencyCode), false,
                        ) { openSubscription(row.forecast.subscription.id) }
                    }
                }}
            }
            val undated=snapshot.reminders.filter{visibleKind != "Events"&&it.due==null&&(showCompleted||!it.completed)}
            if(undated.isNotEmpty())item{Text("Undated reminders",style=MaterialTheme.typography.titleLarge)}
            items(undated,key={"undated:${it.key}"}){ReminderRow(it,now,busy,{model.complete(it,!it.completed)},{open(RecordTarget("REMINDER",it.reminder.id))}, agenda = true)}
        }else{
            item{TextButton(onClick={model.focus.value=null}){Text("All schedule")}}
            if(event==null&&reminder==null)item{Text("This record is no longer available.")}
            if(event!=null){
                item{Text(event.title,style=MaterialTheme.typography.headlineMedium)}
                val day=focus?.day?.takeIf{calendarDateOnOrAfter(event.recurrence,eventBaseDate(event).toEpochDay(),it)==it}?:eventBaseDate(event).toEpochDay()
                val occurrence=eventOccurrence(event,day,now.zone)
                item{PorosCard{
                    Text(if(event.allDay)"All day · ${LocalDate.ofEpochDay(day)}" else "${Instant.ofEpochMilli(occurrence.start).atZone(now.zone).format(DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm"))} – ${Instant.ofEpochMilli(occurrence.end).atZone(now.zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}",style=MaterialTheme.typography.titleMedium)
                    Text("Time zone: ${event.zoneId}")
                    if(event.location.isNotBlank())Text(event.location)
                    if(event.notes.isNotBlank())Text(event.notes)
                    Text(repeatLabel(event.recurrence))
                    Text("Editing or deleting applies to the entire series. Single-event occurrence edits are not available.",style=MaterialTheme.typography.bodySmall)
                    Button(enabled=!busy,onClick={model.clearError();editor="EVENT"}){Text("Edit event / series")}
                    OutlinedButton(onClick={configureAlerts(RecordTarget("EVENT",event.id))}){Text("Notification timing")}
                    TextButton(enabled=!busy,onClick={deleting=true}){Text("Delete event / series",color=MaterialTheme.colorScheme.error)}
                }}
            }
            if(reminder!=null){
                val history=snapshot.completions[reminder.id].orEmpty()
                val specified=focus?.day?.takeIf{reminderBaseDate(reminder)?.toEpochDay()?.let{base->calendarDateOnOrAfter(reminder.recurrence,base,it)==it}==true}
                val occurrence=specified?.let{reminderOccurrence(reminder,it,history.map{it.revision to it.occurrenceEpochDay}.toSet(),now.zone)}?:oldestUncompleted(reminder,history,now.zone)?:reminderOccurrence(reminder,reminderBaseDate(reminder)?.toEpochDay(),history.map{it.revision to it.occurrenceEpochDay}.toSet(),now.zone)
                item{Text(reminder.title,style=MaterialTheme.typography.headlineMedium)}
                item{PorosCard{
                    ReminderRow(occurrence,now,busy,{model.complete(occurrence,!occurrence.completed)},{})
                    if(reminder.notes.isNotBlank())Text(reminder.notes)
                    Text(repeatLabel(reminder.recurrence));Text("Time zone: ${reminder.zoneId}",style=MaterialTheme.typography.bodySmall)
                    Text("Completion affects this occurrence only. Editing changes the entire series; schedule changes start a new revision and retain prior completion history.",style=MaterialTheme.typography.bodySmall)
                    Button(enabled=!busy,onClick={model.clearError();editor="REMINDER"}){Text("Edit reminder / series")}
                    OutlinedButton(enabled=reminder.dueAtMillis!=null,onClick={configureAlerts(RecordTarget("REMINDER",reminder.id))}){Text("Notification timing")}
                    TextButton(enabled=!busy,onClick={deleting=true}){Text("Delete reminder / series",color=MaterialTheme.colorScheme.error)}
                }}
                if(reminder.recurrence.frequency!=null){
                    item{Text("Occurrences",style=MaterialTheme.typography.titleLarge)}
                    val rows=reminderOccurrences(reminder,history,now.toLocalDate(),now.toLocalDate().plusDays(14),now.zone,14)
                    items(rows,key={it.key}){ReminderRow(it,now,busy,{model.complete(it,!it.completed)},{open(RecordTarget("REMINDER",it.reminder.id,it.day))})}
                    if(history.isNotEmpty())item{Text("Completed history",style=MaterialTheme.typography.titleLarge)}
                    items(history.sortedByDescending{it.completedAtMillis}.take(20),key={"history:${it.revision}:${it.occurrenceEpochDay}"}){Text("${LocalDate.ofEpochDay(it.occurrenceEpochDay)} · ${if(it.revision==reminder.revision)"Current series" else "Previous schedule revision ${it.revision}"}",style=MaterialTheme.typography.bodyMedium)}
                }
            }
        }
        if(error!=null&&editor==null)item{Text(error!!,color=MaterialTheme.colorScheme.error)}
    }
    if (editor == "EVENT") key(focus?.id) {
        EventEditor(event, selected, busy, error, { editor = null },
            snapshot.alerts.filter { event != null && it.eventId == event.id },
            openAlerts = event?.let { { editor = null; configureAlerts(RecordTarget("EVENT", it.id)) } }) { value, create ->
            model.event(value, create) { editor = null; model.focus.value = RecordTarget("EVENT", value.id) }
        }
    }
    if (editor == "REMINDER") key(focus?.id) {
        ReminderEditor(reminder, selected, busy, error, { editor = null },
            snapshot.alerts.filter { reminder != null && it.reminderId == reminder.id },
            openAlerts = reminder?.takeIf { it.dueAtMillis != null }?.let { { editor = null; configureAlerts(RecordTarget("REMINDER", it.id)) } }) { value, create ->
            model.reminder(value, create) { editor = null; model.focus.value = RecordTarget("REMINDER", value.id) }
        }
    }
    if(deleting&&focus!=null)AlertDialog(onDismissRequest={deleting=false},title={Text("Delete entire record or series?")},text={Text("All occurrences, completion history and alert preferences for this record will be removed. Money and subscription records are unaffected.")},confirmButton={TextButton(enabled=!busy,onClick={val target=focus!!;deleting=false;model.delete(target){model.focus.value=null}}){Text("Delete series")}},dismissButton={TextButton(onClick={deleting=false}){Text("Keep record")}})
}

private data class AgendaItem(val time:Long,val key:String,val event:EventOccurrence?=null,val reminder:ReminderOccurrence?=null,val forecast:RenewalForecast?=null)
internal fun repeatLabel(r:Recurrence):String {
    val unit=when(r.frequency){Frequency.DAILY->"day";Frequency.WEEKLY->"week";Frequency.MONTHLY->"month";Frequency.YEARLY->"year";null->return "Does not repeat"}
    val label=if(r.interval==1)when(r.frequency){Frequency.DAILY->"Repeats daily";Frequency.WEEKLY->"Repeats weekly";Frequency.MONTHLY->"Repeats monthly";else->"Repeats yearly"}else "Every ${r.interval} ${unit}s"
    return label+r.untilEpochDay?.let{" · through ${LocalDate.ofEpochDay(it)}"}.orEmpty()
}

@Composable
private fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    data: ScheduleSnapshot,
    zone: ZoneId,
    choose: (LocalDate) -> Unit,
) {
    val monday = selected.minusDays(selected.dayOfWeek.value - 1L)
    val occupied = remember(data, monday, zone) {
        (0..6).map { monday.plusDays(it.toLong()) }.filter {
            scheduleDayOccupied(it, data, zone)
        }.toSet()
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val dayWidth = maxOf(48.dp, 40.dp * LocalDensity.current.fontScale)
        val width = maxOf(maxWidth, dayWidth * 7 + 18.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()).width(width).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(7) { index ->
                val date = monday.plusDays(index.toLong())
                val active = date == selected
                Surface(
                    onClick = { choose(date) },
                    color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = if (active) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f).semantics {
                        this.selected = active
                        contentDescription = "Select $date${if (date == today) ", today" else ""}"
                    },
                ) {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEEEE")),
                            style = MaterialTheme.typography.labelSmall)
                        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.size(4.dp).background(
                            when {
                                active -> MaterialTheme.colorScheme.onPrimary
                                date == today -> LocalAtmosphere.current.positive
                                date in occupied -> MaterialTheme.colorScheme.primary
                                else -> Color.Transparent
                            }, CircleShape,
                        ))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReminderRow(
    item: ReminderOccurrence,
    now: ZonedDateTime,
    busy: Boolean,
    complete: () -> Unit,
    open: () -> Unit,
    agenda: Boolean = false,
) {
    val overdue=!item.completed&&item.due!=null&&if(item.reminder.dateOnly)item.day!!<now.toLocalDate().toEpochDay()else item.due<now.toInstant().toEpochMilli()
    val status=when{item.completed->"Completed";item.due==null->"Undated";overdue->"Overdue";Instant.ofEpochMilli(item.due).atZone(now.zone).toLocalDate()==now.toLocalDate()->"Due today";else->"Upcoming"}
    if (agenda) {
        AgendaReminderContent(item, now, busy, status, overdue, complete, open)
        return
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Checkbox(item.completed,onCheckedChange={complete()},enabled=!busy,modifier=Modifier.semantics{contentDescription="${if(item.completed)"Reopen" else "Complete"} ${item.reminder.title} ${item.day?.let{LocalDate.ofEpochDay(it)}?:"undated"}"})
        TextButton(onClick=open,modifier=Modifier.weight(1f),shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(vertical=8.dp,horizontal=12.dp)){
            Column(Modifier.fillMaxWidth()){
                Text(item.reminder.title,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurface)
                Text("$status${item.due?.let{" · "+Instant.ofEpochMilli(it).atZone(now.zone).format(DateTimeFormatter.ofPattern(if(item.reminder.dateOnly)"d MMM uuuu" else "d MMM, HH:mm"))}.orEmpty()}${if(item.reminder.recurrence.frequency!=null)" · Repeats" else ""}",style=MaterialTheme.typography.bodySmall,color=if(overdue)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    }
}
@Composable
private fun AgendaReminderContent(
    item: ReminderOccurrence,
    now: ZonedDateTime,
    busy: Boolean,
    status: String,
    overdue: Boolean,
    complete: () -> Unit,
    open: () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    Surface(color = if (item.completed) MaterialTheme.colorScheme.surfaceContainerLow
        else LocalAtmosphere.current.panel, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(item.completed, onCheckedChange = { complete() }, enabled = !busy,
                modifier = Modifier.semantics {
                    contentDescription = "${if (item.completed) "Reopen" else "Complete"} ${item.reminder.title} ${item.day?.let { LocalDate.ofEpochDay(it) } ?: "undated"}"
                })
            Surface(onClick = open, color = Color.Transparent, shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.reminder.title, style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface)
                    if (item.reminder.notes.isNotBlank()) Text(item.reminder.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (largeText) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis)
                    Text("$status${item.due?.let { " · " + Instant.ofEpochMilli(it).atZone(now.zone).format(DateTimeFormatter.ofPattern(if (item.reminder.dateOnly) "d MMM uuuu" else "d MMM, HH:mm")) }.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            overdue -> MaterialTheme.colorScheme.error
                            item.completed -> LocalAtmosphere.current.positive
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                    if (item.reminder.recurrence.frequency != null) Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Autorenew, null, Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(repeatLabel(item.reminder.recurrence),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(item: EventOccurrence, zone: ZoneId, open: () -> Unit) {
    val time = if (item.event.allDay) "All day" else Instant.ofEpochMilli(item.start)
        .atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 320.dp && LocalDensity.current.fontScale <= 1.3f) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(48.dp).fillMaxHeight().padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Text(time, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.width(1.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
                }
                EventAgendaCard(item, zone, Modifier.weight(1f), open)
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            EventAgendaCard(item, zone, Modifier.fillMaxWidth(), open)
        }
    }
}

@Composable
private fun EventAgendaCard(item: EventOccurrence, zone: ZoneId, modifier: Modifier, open: () -> Unit) {
    Surface(onClick = open, modifier = modifier, color = LocalAtmosphere.current.panel,
        shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.CalendarMonth, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Event", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(item.event.title, style = MaterialTheme.typography.titleMedium)
            if (item.event.location.isNotBlank()) Text(item.event.location,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Ends ${Instant.ofEpochMilli(item.end).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.event.recurrence.frequency != null) Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Autorenew, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(repeatLabel(item.event.recurrence), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun scheduleDayOccupied(day: LocalDate, data: ScheduleSnapshot, zone: ZoneId): Boolean {
    val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val until = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return data.events.any { it.start < until && it.end > from } ||
        data.reminders.any { it.due != null && it.due >= from && it.due < until } ||
        data.forecasts.any { it.day == day.toEpochDay() }
}

@Composable
private fun MonthGrid(
    selected: LocalDate,
    today: LocalDate,
    data: ScheduleSnapshot,
    zone: ZoneId,
    choose: (LocalDate) -> Unit,
) {
    val first = selected.withDayOfMonth(1)
    val start = first.minusDays(first.dayOfWeek.value - 1L)
    val occupied = remember(data, start, zone) {
        (0..41).map { start.plusDays(it.toLong()) }.filter {
            scheduleDayOccupied(it, data, zone)
        }.toSet()
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val dayWidth = maxOf(48.dp, 40.dp * LocalDensity.current.fontScale)
        val width = maxOf(maxWidth, dayWidth * 7)
        Column(Modifier.horizontalScroll(rememberScrollState()).width(width)) {
            Row {
                (0..6).forEach { index ->
                    Text(start.plusDays(index.toLong()).format(DateTimeFormatter.ofPattern("EEEEE")),
                        Modifier.weight(1f).padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            repeat(6) { week ->
                Row {
                    repeat(7) { index ->
                        val date = start.plusDays((week * 7 + index).toLong())
                        val active = date == selected
                        TextButton(
                            onClick = { choose(date) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                this.selected = active
                                contentDescription = "Select $date${if (date == today) ", today" else ""}"
                            },
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                            ),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall,
                                    color = when {
                                        active -> MaterialTheme.colorScheme.onPrimary
                                        date == today -> MaterialTheme.colorScheme.primary
                                        date.month == selected.month -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    })
                                Box(Modifier.size(4.dp).background(
                                    when {
                                        date !in occupied -> Color.Transparent
                                        active -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.primary
                                    }, CircleShape,
                                ))
                            }
                        }
                    }
                }
            }
        }
    }
}
