package com.jevon.personalos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.*
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
    LazyColumn(Modifier.fillMaxSize().testTag("ScheduleList"),contentPadding=PaddingValues(20.dp,0.dp,20.dp,28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(focus==null){
            item {
                MonthSelector(YearMonth.from(selected),{model.date.value=selected.minusMonths(1)},{model.date.value=selected.plusMonths(1)})
                PrimaryTabRow(selectedTabIndex=if(mode=="Agenda")0 else 1,containerColor=androidx.compose.ui.graphics.Color.Transparent,divider={}){
                    listOf("Agenda","Month").forEach{label->Tab(selected=mode==label,onClick={mode=label},text={Text(label)})}
                }
                if (mode == "Agenda") WeekStrip(selected, now.toLocalDate()) { model.date.value = it }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick={model.date.value=selected.minusDays(1)}) { Icon(AppIcons.ChevronLeft, "Previous day") }
                    Text(selected.format(DateTimeFormatter.ofPattern("EEE, d MMM uuuu")),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    IconButton(onClick={model.date.value=selected.plusDays(1)}) { Icon(AppIcons.ChevronRight, "Next day") }
                    TextButton(onClick={model.date.value=now.toLocalDate()}){Text("Today")}
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    FilledTonalButton(onClick={model.clearError();editor="EVENT"}){Icon(AppIcons.Add,null);Text("New event",Modifier.padding(start=6.dp))}
                    OutlinedButton(onClick={model.clearError();editor="REMINDER"}){Text("New reminder")}
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Events", "Reminders").forEach { kind ->
                        FilterChip(selected = visibleKind == kind, onClick = { visibleKind = kind }, label = { Text(kind) })
                    }
                    FilterChip(selected = showCompleted, onClick = { showCompleted = !showCompleted }, label = { Text(if(showCompleted)"Hide completed" else "Show completed") })
                }
            }
            if(mode=="Month")item {MonthGrid(selected,now.toLocalDate(),snapshot,now.zone){model.date.value=it}}
            if(snapshot.truncated)item{Text("Showing bounded results. Select a narrower date or reduce overlapping series to see more.",color=MaterialTheme.colorScheme.error)}
            val from=selected.atStartOfDay(now.zone).toInstant().toEpochMilli()
            val until=selected.plusDays(if(mode=="Month")1 else 7).atStartOfDay(now.zone).toInstant().toEpochMilli()
            val overdue=if(selected==now.toLocalDate())snapshot.overdue else emptyList()
            if(overdue.isNotEmpty() && visibleKind != "Events"){
                item{Text("Overdue",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.error)}
                items(overdue,key={"overdue:${it.key}"}){ReminderRow(it,now,busy,{model.complete(it,!it.completed)},{open(RecordTarget("REMINDER",it.reminder.id,it.day))})}
            }
            val overdueKeys=overdue.map{it.key}.toSet()
            val rows=ArrayList<AgendaItem>()
            if (visibleKind != "Reminders") snapshot.events.filter{it.end>from&&it.start<until}.forEach{rows+=AgendaItem(maxOf(it.start,from),"event:${it.event.id}:${it.day}",event=it)}
            if (visibleKind != "Events") snapshot.reminders.filter{it.due!=null&&it.due>=from&&it.due<until&&it.key !in overdueKeys&&(showCompleted||!it.completed)}.forEach{rows+=AgendaItem(it.due!!,"reminder:${it.key}",reminder=it)}
            if (visibleKind == "All") snapshot.forecasts.filter{it.day>=selected.toEpochDay()&&it.day<selected.plusDays(if(mode=="Month")1 else 7).toEpochDay()}.forEach{rows+=AgendaItem(LocalDate.ofEpochDay(it.day).atTime(9,0).atZone(now.zone).toInstant().toEpochMilli(),"forecast:${it.subscription.id}:${it.day}",forecast=it)}
            item{Text(if(mode=="Month")"Selected day" else "Next seven days",style=MaterialTheme.typography.titleLarge)}
            if(rows.isEmpty())item{Text("No events, dated reminders or unpaid renewals here. Add a commitment or choose another date.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            rows.sortedWith(compareBy<AgendaItem>{it.time}.thenBy{it.key}).groupBy{Instant.ofEpochMilli(it.time).atZone(now.zone).toLocalDate()}.forEach{(day,items)->
                item("date:$day"){Text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),style=MaterialTheme.typography.titleMedium)}
                items(items,key={it.key}){row->when{
                    row.event!=null->EventRow(row.event,now.zone){open(RecordTarget("EVENT",row.event.event.id,row.event.day))}
                    row.reminder!=null->ReminderRow(row.reminder,now,busy,{model.complete(row.reminder,!row.reminder.completed)},{open(RecordTarget("REMINDER",row.reminder.reminder.id,row.reminder.day))})
                    row.forecast!=null->LedgerRow(AppIcons.Autorenew,row.forecast.subscription.name,"Unpaid payment forecast · ${LocalDate.ofEpochDay(row.forecast.day)}",formatMoney(row.forecast.subscription.amountMinor,row.forecast.subscription.currencyCode),onClick={openSubscription(row.forecast.subscription.id)})
                }}
            }
            val undated=snapshot.reminders.filter{visibleKind != "Events"&&it.due==null&&(showCompleted||!it.completed)}
            if(undated.isNotEmpty())item{Text("Undated reminders",style=MaterialTheme.typography.titleLarge)}
            items(undated,key={"undated:${it.key}"}){ReminderRow(it,now,busy,{model.complete(it,!it.completed)},{open(RecordTarget("REMINDER",it.reminder.id))})}
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
private fun WeekStrip(selected: LocalDate, today: LocalDate, choose: (LocalDate) -> Unit) {
    val monday = selected.minusDays(selected.dayOfWeek.value - 1L)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(7) { index ->
            val date = monday.plusDays(index.toLong())
            val active = date == selected
            Surface(onClick = { choose(date) }, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.small, modifier = Modifier.widthIn(min = 48.dp).semantics { this.selected = active; contentDescription = "Select $date${if (date == today) ", today" else ""}" }) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEEEE")), style = MaterialTheme.typography.labelSmall)
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable internal fun ReminderRow(item:ReminderOccurrence,now:ZonedDateTime,busy:Boolean,complete:()->Unit,open:()->Unit) {
    val overdue=!item.completed&&item.due!=null&&if(item.reminder.dateOnly)item.day!!<now.toLocalDate().toEpochDay()else item.due<now.toInstant().toEpochMilli()
    val status=when{item.completed->"Completed";item.due==null->"Undated";overdue->"Overdue";Instant.ofEpochMilli(item.due).atZone(now.zone).toLocalDate()==now.toLocalDate()->"Due today";else->"Upcoming"}
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
@Composable private fun EventRow(item:EventOccurrence,zone:ZoneId,open:()->Unit){
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if(item.event.allDay) "All day" else Instant.ofEpochMilli(item.start).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")),
            Modifier.widthIn(min = 48.dp, max = 76.dp).padding(top = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Surface(onClick = open, modifier = Modifier.weight(1f), color = LocalAtmosphere.current.panel, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.CalendarMonth, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(item.event.title, style = MaterialTheme.typography.titleMedium)
                }
                if (item.event.location.isNotBlank()) Text(item.event.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Ends ${Instant.ofEpochMilli(item.end).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.event.recurrence.frequency != null) Text(repeatLabel(item.event.recurrence), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun MonthGrid(selected:LocalDate,today:LocalDate,data:ScheduleSnapshot,zone:ZoneId,choose:(LocalDate)->Unit){
    val first=selected.withDayOfMonth(1);val start=first.minusDays(first.dayOfWeek.value-1L)
    val occupied=remember(data,start,zone){(0..41).map{start.plusDays(it.toLong())}.filter{day->
        val a=day.atStartOfDay(zone).toInstant().toEpochMilli();val b=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        data.events.any{it.start<b&&it.end>a}||data.reminders.any{it.due!=null&&it.due>=a&&it.due<b}||data.forecasts.any{it.day==day.toEpochDay()}
    }.toSet()}
    BoxWithConstraints(Modifier.fillMaxWidth()){
        val width=maxOf(maxWidth,336.dp)
        Column(Modifier.horizontalScroll(rememberScrollState()).width(width)){
            Row{(0..6).forEach{n->Text(start.plusDays(n.toLong()).format(DateTimeFormatter.ofPattern("EEEEE")),Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.labelMedium)}}
            repeat(6){week->Row{repeat(7){index->val date=start.plusDays((week*7+index).toLong())
                TextButton(onClick={choose(date)},modifier=Modifier.weight(1f).heightIn(min=48.dp).semantics{this.selected=date==selected;contentDescription="Select $date${if(date==today)", today" else ""}"},colors=ButtonDefaults.textButtonColors(containerColor=if(date==selected)MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)){
                    Column(horizontalAlignment=Alignment.CenterHorizontally){Text(date.dayOfMonth.toString(),color=when{date==today->MaterialTheme.colorScheme.primary;date.month==selected.month->MaterialTheme.colorScheme.onSurface;else->MaterialTheme.colorScheme.onSurfaceVariant});if(date in occupied)Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary,androidx.compose.foundation.shape.CircleShape))else Spacer(Modifier.height(4.dp))}
                }
            }}}
        }
    }
}
