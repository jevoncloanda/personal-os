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
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(onClick={model.clearError();editor="EVENT"}){Icon(AppIcons.Add,null);Text("New event",Modifier.padding(start=6.dp))}
                    OutlinedButton(onClick={model.clearError();editor="REMINDER"}){Text("New reminder")}
                }
                PrimaryTabRow(selectedTabIndex=if(mode=="Agenda")0 else 1,containerColor=androidx.compose.ui.graphics.Color.Transparent,divider={}){
                    listOf("Agenda","Month").forEach{label->Tab(selected=mode==label,onClick={mode=label},text={Text(label)})}
                }
                MonthSelector(YearMonth.from(selected),{model.date.value=selected.minusMonths(1)},{model.date.value=selected.plusMonths(1)})
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text(selected.format(DateTimeFormatter.ofPattern("EEE, d MMM uuuu")),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    TextButton(onClick={model.date.value=now.toLocalDate()}){Text("Today")}
                }
                FlowRow {
                    TextButton(onClick={model.date.value=selected.minusDays(1)}){Text("Previous day")}
                    TextButton(onClick={model.date.value=selected.plusDays(1)}){Text("Next day")}
                    TextButton(onClick={showCompleted=!showCompleted}){Text(if(showCompleted)"Hide completed" else "Show completed")}
                }
            }
            if(mode=="Month")item {MonthGrid(selected,now.toLocalDate(),snapshot,now.zone){model.date.value=it}}
            if(snapshot.truncated)item{Text("Showing bounded results. Select a narrower date or reduce overlapping series to see more.",color=MaterialTheme.colorScheme.error)}
            val from=selected.atStartOfDay(now.zone).toInstant().toEpochMilli()
            val until=selected.plusDays(if(mode=="Month")1 else 7).atStartOfDay(now.zone).toInstant().toEpochMilli()
            val overdue=if(selected==now.toLocalDate())snapshot.overdue else emptyList()
            if(overdue.isNotEmpty()){
                item{Text("Overdue",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.error)}
                items(overdue,key={"overdue:${it.key}"}){ReminderRow(it,now,busy,{model.complete(it,!it.completed)},{open(RecordTarget("REMINDER",it.reminder.id,it.day))})}
            }
            val overdueKeys=overdue.map{it.key}.toSet()
            val rows=ArrayList<AgendaItem>()
            snapshot.events.filter{it.end>from&&it.start<until}.forEach{rows+=AgendaItem(maxOf(it.start,from),"event:${it.event.id}:${it.day}",event=it)}
            snapshot.reminders.filter{it.due!=null&&it.due>=from&&it.due<until&&it.key !in overdueKeys&&(showCompleted||!it.completed)}.forEach{rows+=AgendaItem(it.due!!,"reminder:${it.key}",reminder=it)}
            snapshot.forecasts.filter{it.day>=selected.toEpochDay()&&it.day<selected.plusDays(if(mode=="Month")1 else 7).toEpochDay()}.forEach{rows+=AgendaItem(LocalDate.ofEpochDay(it.day).atTime(9,0).atZone(now.zone).toInstant().toEpochMilli(),"forecast:${it.subscription.id}:${it.day}",forecast=it)}
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
            val undated=snapshot.reminders.filter{it.due==null&&(showCompleted||!it.completed)}
            if(undated.isNotEmpty())item{Text("Undated reminders",style=MaterialTheme.typography.titleLarge)}
            items(undated,key={"undated:${it.key}"}){ReminderRow(it,now,busy,{model.complete(it,!it.completed)},{open(RecordTarget("REMINDER",it.reminder.id))})}
        }else{
            item{TextButton(onClick={model.focus.value=null}){Text("All schedule")}}
            if(event==null&&reminder==null)item{Text("This record is no longer available.")}
            if(event!=null){
                item{Text(event.title,style=MaterialTheme.typography.headlineMedium)}
                val day=focus?.day?.takeIf{calendarDateOnOrAfter(event.recurrence,eventBaseDate(event).toEpochDay(),it)==it}?:eventBaseDate(event).toEpochDay()
                val occurrence=eventOccurrence(event,day,now.zone)
                item{FrostedPanel{
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
                item{FrostedPanel{
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
    if(editor=="EVENT")key(focus?.id){EventEditor(event,selected,busy,error,{editor=null}){value,create->model.event(value,create){editor=null;model.focus.value=RecordTarget("EVENT",value.id)}}}
    if(editor=="REMINDER")key(focus?.id){ReminderEditor(reminder,selected,busy,error,{editor=null}){value,create->model.reminder(value,create){editor=null;model.focus.value=RecordTarget("REMINDER",value.id)}}}
    if(deleting&&focus!=null)AlertDialog(onDismissRequest={deleting=false},title={Text("Delete entire record or series?")},text={Text("All occurrences, completion history and alert preferences for this record will be removed. Money and subscription records are unaffected.")},confirmButton={TextButton(enabled=!busy,onClick={val target=focus!!;deleting=false;model.delete(target){model.focus.value=null}}){Text("Delete series")}},dismissButton={TextButton(onClick={deleting=false}){Text("Keep record")}})
}

private data class AgendaItem(val time:Long,val key:String,val event:EventOccurrence?=null,val reminder:ReminderOccurrence?=null,val forecast:RenewalForecast?=null)
internal fun repeatLabel(r:Recurrence):String {
    val unit=when(r.frequency){Frequency.DAILY->"day";Frequency.WEEKLY->"week";Frequency.MONTHLY->"month";Frequency.YEARLY->"year";null->return "Does not repeat"}
    val label=if(r.interval==1)when(r.frequency){Frequency.DAILY->"Repeats daily";Frequency.WEEKLY->"Repeats weekly";Frequency.MONTHLY->"Repeats monthly";else->"Repeats yearly"}else "Every ${r.interval} ${unit}s"
    return label+r.untilEpochDay?.let{" · through ${LocalDate.ofEpochDay(it)}"}.orEmpty()
}

@Composable internal fun ReminderRow(item:ReminderOccurrence,now:ZonedDateTime,busy:Boolean,complete:()->Unit,open:()->Unit) {
    val overdue=!item.completed&&item.due!=null&&if(item.reminder.dateOnly)item.day!!<now.toLocalDate().toEpochDay()else item.due<now.toInstant().toEpochMilli()
    val status=when{item.completed->"Completed";item.due==null->"Undated";overdue->"Overdue";Instant.ofEpochMilli(item.due).atZone(now.zone).toLocalDate()==now.toLocalDate()->"Due today";else->"Upcoming"}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Checkbox(item.completed,onCheckedChange={complete()},enabled=!busy,modifier=Modifier.semantics{contentDescription="${if(item.completed)"Reopen" else "Complete"} ${item.reminder.title} ${item.day?.let{LocalDate.ofEpochDay(it)}?:"undated"}"})
        TextButton(onClick=open,modifier=Modifier.weight(1f),shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(vertical=8.dp,horizontal=12.dp)){
            Column(Modifier.fillMaxWidth()){
                Text(item.reminder.title,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurface)
                Text("$status${item.due?.let{" · "+Instant.ofEpochMilli(it).atZone(now.zone).format(DateTimeFormatter.ofPattern(if(item.reminder.dateOnly)"d MMM uuuu" else "d MMM, HH:mm"))}.orEmpty()}${if(item.reminder.recurrence.frequency!=null)" · Repeats" else ""}",style=MaterialTheme.typography.bodySmall,color=if(overdue)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable private fun EventRow(item:EventOccurrence,zone:ZoneId,open:()->Unit){
    LedgerRow(AppIcons.CalendarMonth,item.event.title,"${if(item.event.recurrence.frequency!=null)"Repeats · " else ""}${item.event.location.ifBlank{"Calendar event"}}\nEnds ${Instant.ofEpochMilli(item.end).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}",if(item.event.allDay)"All day" else Instant.ofEpochMilli(item.start).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")),onClick=open)
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

@Composable private fun RepeatFields(frequency:String,interval:String,until:String,enabled:Boolean,setFrequency:(String)->Unit,setInterval:(String)->Unit,setUntil:(String)->Unit){
    Choice("Repeat",frequency,listOf("NONE" to "Does not repeat","DAILY" to "Daily","WEEKLY" to "Weekly","MONTHLY" to "Monthly","YEARLY" to "Yearly"),enabled,setFrequency)
    if(frequency!="NONE"){Field("Repeat interval",interval,enabled,androidx.compose.ui.text.input.KeyboardType.Number,change=setInterval);BillingDateField("Repeat through (optional)",until,enabled,setUntil)}
}
private fun recurrence(frequency:String,interval:String,until:String,base:LocalDate)=Recurrence(if(frequency=="NONE")null else Frequency.valueOf(frequency),if(frequency=="NONE")1 else interval.toIntOrNull()?:throw IllegalArgumentException("Use a whole repeat interval"),base.toEpochDay(),if(frequency=="NONE"||until.isBlank())null else LocalDate.parse(until).toEpochDay())

@Composable private fun EventEditor(old:EventEntity?,selected:LocalDate,busy:Boolean,error:String?,close:()->Unit,save:(EventEntity,Boolean)->Unit){
    val id=rememberSaveable{old?.id?:UUID.randomUUID().toString()};val base=old?.let{Instant.ofEpochMilli(it.startsAtMillis).atZone(ZoneId.of(it.zoneId))}?:selected.atTime(9,0).atZone(ZoneId.systemDefault())
    val end=old?.let{Instant.ofEpochMilli(it.endsAtMillis).atZone(ZoneId.of(it.zoneId))}?:base.plusHours(1)
    var title by rememberSaveable{mutableStateOf(old?.title?:"")};var notes by rememberSaveable{mutableStateOf(old?.notes?:"")};var location by rememberSaveable{mutableStateOf(old?.location?:"")}
    var allDay by rememberSaveable{mutableStateOf(old?.allDay?:false)};var date by rememberSaveable{mutableStateOf(base.toLocalDate().toString())};var lastDate by rememberSaveable{mutableStateOf((if(old?.allDay==true)end.toLocalDate().minusDays(1)else end.toLocalDate()).toString())}
    var startTime by rememberSaveable{mutableStateOf(base.toLocalTime().withSecond(0).withNano(0).toString())};var endTime by rememberSaveable{mutableStateOf(end.toLocalTime().withSecond(0).withNano(0).toString())};var zone by rememberSaveable{mutableStateOf(old?.zoneId?:ZoneId.systemDefault().id)}
    var frequency by rememberSaveable{mutableStateOf(old?.recurrence?.frequency?.name?:"NONE")};var interval by rememberSaveable{mutableStateOf((old?.recurrence?.interval?:1).toString())};var until by rememberSaveable{mutableStateOf(old?.recurrence?.untilEpochDay?.let{LocalDate.ofEpochDay(it).toString()}.orEmpty())}
    EditorFrame(if(old==null)"New event" else "Edit entire event series",busy,error,close,{
        val z=ZoneId.of(zone);val startDate=LocalDate.parse(date);val finalDate=LocalDate.parse(lastDate)
        val a=if(allDay)startDate.atStartOfDay(z)else startDate.atTime(LocalTime.parse(startTime)).atZone(z)
        val b=if(allDay)finalDate.plusDays(1).atStartOfDay(z)else finalDate.atTime(LocalTime.parse(endTime)).atZone(z)
        require(b.toInstant()>a.toInstant()){"End must follow start"}
        val proposed=recurrence(frequency,interval,until,startDate)
        val repeat=if(old!=null&&startDate==eventBaseDate(old)&&old.recurrence.copy(anchorEpochDay=proposed.anchorEpochDay)==proposed)old.recurrence else proposed
        save(EventEntity(id,title.trim(),a.toInstant().toEpochMilli(),b.toInstant().toEpochMilli(),z.id,allDay,location.trim(),repeat,notes.trim(),old?.revision?:0),old==null)
    }){
        Field("Event title",title,!busy){title=it};Field("Notes (optional)",notes,!busy,single=false){notes=it}
        val dirty=LocalMarkDraftDirty.current
        Row(Modifier.fillMaxWidth().toggleable(allDay,enabled=!busy,role=Role.Checkbox,onValueChange={dirty();allDay=it}).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(allDay,null);Text("All-day event",Modifier.padding(start=12.dp))}
        BillingDateField("Start date",date,!busy){date=it};if(!allDay)CalendarTimeField("Start time",startTime,!busy){startTime=it}
        BillingDateField(if(allDay)"Last day (inclusive)" else "End date",lastDate,!busy){lastDate=it};if(!allDay)CalendarTimeField("End time",endTime,!busy){endTime=it}
        Field("Time zone",zone,!busy){zone=it};Field("Location (optional)",location,!busy){location=it}
        RepeatFields(frequency,interval,until,!busy,{frequency=it},{interval=it},{until=it})
        Text("Timed events keep wall-clock times in this zone. DST gaps move forward; overlaps use the earlier offset. All-day events keep their calendar dates. Editing changes the entire series. Configure notification timing after saving.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun ReminderEditor(old:ReminderEntity?,selected:LocalDate,busy:Boolean,error:String?,close:()->Unit,save:(ReminderEntity,Boolean)->Unit){
    val id=rememberSaveable{old?.id?:UUID.randomUUID().toString()};val zoneId=old?.zoneId?:ZoneId.systemDefault().id
    val base=old?.dueAtMillis?.let{Instant.ofEpochMilli(it).atZone(ZoneId.of(zoneId))}?:selected.atTime(9,0).atZone(ZoneId.of(zoneId))
    var title by rememberSaveable{mutableStateOf(old?.title?:"")};var notes by rememberSaveable{mutableStateOf(old?.notes?:"")};var hasDate by rememberSaveable{mutableStateOf(old==null||old.dueAtMillis!=null)};var timed by rememberSaveable{mutableStateOf(old?.let{!it.dateOnly}?:false)}
    var date by rememberSaveable{mutableStateOf(base.toLocalDate().toString())};var time by rememberSaveable{mutableStateOf(base.toLocalTime().withSecond(0).withNano(0).toString())};var zone by rememberSaveable{mutableStateOf(zoneId)}
    var frequency by rememberSaveable{mutableStateOf(old?.recurrence?.frequency?.name?:"NONE")};var interval by rememberSaveable{mutableStateOf((old?.recurrence?.interval?:1).toString())};var until by rememberSaveable{mutableStateOf(old?.recurrence?.untilEpochDay?.let{LocalDate.ofEpochDay(it).toString()}.orEmpty())}
    EditorFrame(if(old==null)"New reminder" else "Edit entire reminder series",busy,error,close,{
        val z=ZoneId.of(zone);val day=LocalDate.parse(date)
        val due=if(!hasDate)null else if(timed)day.atTime(LocalTime.parse(time)).atZone(z).toInstant().toEpochMilli()else day.atStartOfDay(z).toInstant().toEpochMilli()
        val proposed=if(hasDate)recurrence(frequency,interval,until,day)else Recurrence(null)
        val repeat=if(old!=null&&(!hasDate&&old.dueAtMillis==null||day==reminderBaseDate(old))&&old.recurrence.copy(anchorEpochDay=proposed.anchorEpochDay)==proposed)old.recurrence else proposed
        save(ReminderEntity(id,title.trim(),due,old?.completedAtMillis,repeat,notes.trim(),z.id,!timed,old?.revision?:0),old==null)
    }){
        Field("Reminder title",title,!busy){title=it};Field("Notes (optional)",notes,!busy,single=false){notes=it}
        val dirty=LocalMarkDraftDirty.current
        Row(Modifier.fillMaxWidth().toggleable(hasDate,enabled=!busy,role=Role.Checkbox,onValueChange={dirty();hasDate=it;if(!it)frequency="NONE"}).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(hasDate,null);Text("Has due date",Modifier.padding(start=12.dp))}
        if(hasDate){
            BillingDateField("Due date",date,!busy){date=it}
            Row(Modifier.fillMaxWidth().toggleable(timed,enabled=!busy,role=Role.Checkbox,onValueChange={dirty();timed=it}).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(timed,null);Text("Specific due time",Modifier.padding(start=12.dp))}
            if(timed)CalendarTimeField("Due time",time,!busy){time=it}
            Field("Time zone",zone,!busy){zone=it}
            RepeatFields(frequency,interval,until,!busy,{frequency=it},{interval=it},{until=it})
        }
        Text("Undated reminders have no notifications. Complete or reopen a single occurrence from the list. Editing changes the entire series; date changes retain previous completion history. Configure notification timing after saving.",style=MaterialTheme.typography.bodySmall)
    }
}
