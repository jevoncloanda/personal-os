package com.jevon.personalos.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.jevon.personalos.data.*
import com.jevon.personalos.subscriptions.upcomingRenewals
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class RecordTarget(val domain:String,val id:String,val day:Long?=null)
data class RenewalForecast(val subscription:SubscriptionEntity,val day:Long)
data class ScheduleSnapshot(val events:List<EventOccurrence>,val reminders:List<ReminderOccurrence>,val overdue:List<ReminderOccurrence>,
    val forecasts:List<RenewalForecast>,val eventRecords:Map<String,EventEntity>,val reminderRecords:Map<String,ReminderEntity>,
    val completions:Map<String,List<ReminderCompletionEntity>>,val alerts:List<AlertPreferenceEntity>,val truncated:Boolean)

suspend fun PersonalDao.scheduleRange(from:LocalDate,until:LocalDate,now:ZonedDateTime,focus:RecordTarget?=null):ScheduleSnapshot {
    val eventRows=eventCandidates(from.minusDays(2).atStartOfDay(now.zone).toInstant().toEpochMilli(),until.plusDays(2).atStartOfDay(now.zone).toInstant().toEpochMilli(),from.minusDays(2).toEpochDay())
    val start=from.minusDays(2).atStartOfDay(now.zone).toInstant().toEpochMilli()
    val reminderRows=reminderCandidates(start,until.plusDays(2).atStartOfDay(now.zone).toInstant().toEpochMilli(),from.minusDays(2).toEpochDay())
    val oldRows=overdueReminderCandidates(start);val undatedRows=undatedReminderCandidates()
    val events=eventRows.take(1000).associateBy {it.id}.toMutableMap()
    val reminders=(reminderRows.take(1000)+oldRows.take(20)+undatedRows.take(100)).associateBy {it.id}.toMutableMap()
    if(focus?.domain=="EVENT")event(focus.id)?.let {events[it.id]=it}
    if(focus?.domain=="REMINDER")reminder(focus.id)?.let {reminders[it.id]=it}
    val completions=reminders.keys.associateWith {id->
        val rows=completions(id);val r=reminders.getValue(id);val old=legacyCompletionDay(r)
        if(old!=null&&rows.none{it.revision==r.revision&&it.occurrenceEpochDay==old})rows+ReminderCompletionEntity(id,r.revision,old,r.completedAtMillis!!) else rows
    }
    val eventItems=ArrayList<EventOccurrence>(); val reminderItems=ArrayList<ReminderOccurrence>()
    events.values.forEach { if(eventItems.size<1000)eventItems+=eventOccurrences(it,from,until,now.zone,minOf(128,1000-eventItems.size)) }
    reminders.values.forEach { if(reminderItems.size<1000)reminderItems+=reminderOccurrences(it,completions[it.id].orEmpty(),from,until,now.zone,minOf(128,1000-reminderItems.size)) }
    val oldest=reminders.values.mapNotNull {oldestUncompleted(it,completions[it.id].orEmpty(),now.zone)}
        .filter {it.due!=null && if(it.reminder.dateOnly)it.day!!<now.toLocalDate().toEpochDay() else it.due<now.toInstant().toEpochMilli()}
        .sortedWith(compareBy<ReminderOccurrence>{it.due}.thenBy{it.reminder.id}).take(20)
    val payments=subscriptionPayments().groupBy {it.subscriptionId}
    val forecasts=ArrayList<RenewalForecast>();var forecastTruncated=false
    val order=compareBy<RenewalForecast>{it.day}.thenBy{it.subscription.id}
    subscriptions().filter{it.active}.forEach{s->
        upcomingRenewals(s,payments[s.id].orEmpty().map{it.dueEpochDay}.toSet(),from.toEpochDay(),until.minusDays(1).toEpochDay(),128).forEach{forecasts+=RenewalForecast(s,it)}
        if(forecasts.size>200){forecastTruncated=true;forecasts.sortWith(order);while(forecasts.size>200)forecasts.removeAt(forecasts.lastIndex)}
    }
    forecasts.sortWith(order)
    return ScheduleSnapshot(eventItems.sortedWith(compareBy<EventOccurrence>{it.start}.thenBy{it.event.id}),reminderItems.sortedWith(compareBy<ReminderOccurrence>{it.due?:Long.MAX_VALUE}.thenBy{it.reminder.id}),oldest,forecasts,events,reminders,completions,alertPreferences(),eventRows.size>1000||reminderRows.size>1000||oldRows.size>20||undatedRows.size>100||forecastTruncated||eventItems.size==1000||reminderItems.size==1000)
}

class ScheduleViewModel(private val database:PersonalDatabase,private val clock:Clock?=null):ViewModel() {
    val date=MutableStateFlow(LocalDate.now(clock?:Clock.systemDefaultZone()))
    val focus=MutableStateFlow<RecordTarget?>(null)
    val busy=MutableStateFlow(false); val error=MutableStateFlow<String?>(null)
    private val retry=MutableStateFlow(0)
    private val times=flow {while(true){emit(ZonedDateTime.now(clock?:Clock.systemDefaultZone()));delay(30_000)}}
    @OptIn(ExperimentalCoroutinesApi::class)
    val state:StateFlow<ScheduleSnapshot?> = combine(date,focus,retry,times){d,f,_,time->Triple(d,f,time)}.flatMapLatest {(selected,focus,now)->
        database.invalidationTracker.createFlow("events","reminders","reminder_completions","alert_preferences","subscriptions","subscription_payments").map {
            database.withTransaction<ScheduleSnapshot?> {
                val first=selected.withDayOfMonth(1); val from=first.minusDays(first.dayOfWeek.value-1L)
                database.personalDao().scheduleRange(from,maxOf(from.plusDays(42),selected.plusDays(8)),now,focus)
            }
        }.catch {error.value="Schedule couldn't load. Your records are preserved. Try again.";emit(null)}
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    fun now():ZonedDateTime=ZonedDateTime.now(clock?:Clock.systemDefaultZone())
    fun retry(){error.value=null;retry.value++}
    fun clearError(){error.value=null}
    private fun write(done:()->Unit={},action:suspend()->Unit){
        if(busy.value)return;busy.value=true;error.value=null
        viewModelScope.launch {try{action();done()}catch(e:CancellationException){throw e}catch(e:IllegalArgumentException){error.value=e.message?:"Check the fields"}catch(_:Exception){error.value="Couldn't save. Try again; no partial change was saved."}finally{busy.value=false}}
    }
    fun event(e:EventEntity,create:Boolean,done:()->Unit)=write(done){database.personalDao().saveEvent(e,create)}
    fun reminder(r:ReminderEntity,create:Boolean,done:()->Unit)=write(done){database.personalDao().saveReminder(r,create)}
    fun delete(target:RecordTarget,done:()->Unit)=write(done){when(target.domain){"EVENT"->database.personalDao().deleteEvent(target.id);"REMINDER"->database.personalDao().deleteReminder(target.id)}}
    fun complete(item:ReminderOccurrence,complete:Boolean)=write {database.personalDao().completeReminder(item.reminder.id,item.reminder.revision,item.day,complete,now().toInstant().toEpochMilli())}
    fun alerts(target:RecordTarget,leads:Set<Int>,minute:Int,done:()->Unit)=write(done){database.personalDao().saveAlerts(target.domain,target.id,leads,minute)}
}
