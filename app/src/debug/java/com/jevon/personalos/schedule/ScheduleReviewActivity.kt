package com.jevon.personalos.schedule

import android.app.Application
import android.os.Bundle
import android.content.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import com.jevon.personalos.alerts.*
import com.jevon.personalos.data.*
import com.jevon.personalos.subscriptions.SubscriptionViewModel
import com.jevon.personalos.today.TodayState
import com.jevon.personalos.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collect
import java.time.*
import java.util.UUID

class ScheduleReviewActivity:ComponentActivity(){
    private var request by mutableStateOf<RecordTarget?>(null)
    private val host:ScheduleReviewHost by viewModels{viewModelFactory{initializer{ScheduleReviewHost(application,intent.getStringExtra("reviewId")?:UUID.randomUUID().toString(),intent.getLongExtra("clockMillis",Long.MIN_VALUE).takeIf{it!=Long.MIN_VALUE})}}}
    private val model:ScheduleViewModel by viewModels{viewModelFactory{initializer{ScheduleViewModel(host.database,host.clock)}}}
    private val subscriptions:SubscriptionViewModel by viewModels{viewModelFactory{initializer{SubscriptionViewModel(host.database)}}}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);request=intent.recordTarget()}
    override fun onResume(){super.onResume();lifecycleScope.launch{host.delivery.reconcile()}}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);enableEdgeToEdge();request=intent.recordTarget()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent{
            var dark by rememberSaveable{mutableStateOf(false)};var route by rememberSaveable{mutableStateOf("schedule")}
            var alertDomain by rememberSaveable{mutableStateOf<String?>(null)};var alertId by rememberSaveable{mutableStateOf("")}
            var subRequest by rememberSaveable{mutableStateOf<String?>(null)}
            val now=ZonedDateTime.now(host.clock?:Clock.systemDefaultZone())
            val today by remember{host.database.observeToday(now)}.collectAsState(initial=null)
            fun alert(target:RecordTarget){alertDomain=target.domain;alertId=target.id}
            LaunchedEffect(request){request?.let{if(it.domain=="SUBSCRIPTION"){route="subscriptions";subRequest=it.id}else route="schedule"}}
            PersonalOsTheme(Settings(if(dark)ThemeMode.DARK else ThemeMode.LIGHT,false)){
                AtmosphericBackground{SystemBarAppearance()
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)){
                        Text("Isolated Schedule review · disposable data",Modifier.padding(12.dp),style=MaterialTheme.typography.labelMedium)
                        FlowRow{
                            TextButton(onClick={dark=!dark}){Text(if(dark)"Review light" else "Review dark")}
                            TextButton(onClick={route="schedule"}){Text("Review Schedule")}
                            TextButton(onClick={route="today"}){Text("Review Today")}
                            TextButton(onClick={route="subscriptions"}){Text("Review Subscriptions")}
                        }
                        Box(Modifier.weight(1f)){
                            when(route){
                                "schedule"->ScheduleScreen(model,request,{request=null},{route="subscriptions";subRequest=it},::alert)
                                "today"->TodayScreen(today?.let{TodayState.Ready(it)}?:TodayState.Loading,{}, {path->
                                    if(path.startsWith("subscription/")){route="subscriptions";subRequest=path.substringAfter('/')}
                                    else{route="schedule";request=RecordTarget(if(path.startsWith("event/"))"EVENT" else "REMINDER",android.net.Uri.decode(path.substringAfter('/').substringBefore('?')),path.substringAfter("?day=","").toLongOrNull())}
                                }){model.complete(it,!it.completed)}
                                "subscriptions"->SubscriptionScreen(subscriptions,subRequest,{subRequest=null},{alert(RecordTarget("SUBSCRIPTION",it))})
                            }
                        }
                    }
                    alertDomain?.let{domain->AlertSettingsScreen(host.database,host.delivery,RecordTarget(domain,alertId)){alertDomain=null}}
                }
            }
        }
    }
    internal fun databaseForTest()=host.database
    internal fun deliveryForTest()=host.delivery
    internal fun modelForTest()=model
}

class ScheduleReviewHost(app:Application,id:String,millis:Long?):AndroidViewModel(app){
    init{require(runCatching{UUID.fromString(id)}.isSuccess)}
    val clock:Clock?=millis?.let{Clock.fixed(Instant.ofEpochMilli(it),ZoneId.of("Asia/Jakarta"))}
    val database=Room.databaseBuilder(app,PersonalDatabase::class.java,"phase3-review-$id.db").addMigrations(PersonalDatabase.MIGRATION_1_2,PersonalDatabase.MIGRATION_2_3,PersonalDatabase.MIGRATION_3_4,PersonalDatabase.MIGRATION_4_5).build()
    val delivery=reviewDelivery(app,database,id)
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val observer=viewModelScope.launch(Dispatchers.IO){database.invalidationTracker.createFlow("events","reminders","reminder_completions","alert_preferences","subscriptions","subscription_payments").debounce(100).collect{delivery.reconcile()}}
    override fun onCleared(){observer.cancel();database.close()}
}

internal fun reviewDelivery(context:Context,database:PersonalDatabase,id:String)=LocalAlerts(context,database,id,ScheduleReviewAlertReceiver::class.java,recordIntent={state->Intent(context,ScheduleReviewActivity::class.java).putExtra("reviewId",id).putExtra("recordDomain",state.domain).putExtra("recordId",state.recordId).putExtra("occurrenceDay",state.occurrenceEpochDay)})

class ScheduleReviewAlertReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action!="com.jevon.personalos.ALERT_TICK")return
        val id=intent.data?.lastPathSegment?:return
        if(runCatching{UUID.fromString(id)}.isFailure)return
        val result=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{
            val db=Room.databaseBuilder(context,PersonalDatabase::class.java,"phase3-review-$id.db").addMigrations(PersonalDatabase.MIGRATION_1_2,PersonalDatabase.MIGRATION_2_3,PersonalDatabase.MIGRATION_3_4,PersonalDatabase.MIGRATION_4_5).build()
            try{reviewDelivery(context,db,id).reconcile()}finally{db.close();result.finish()}
        }
    }
}
