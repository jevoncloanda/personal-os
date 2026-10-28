package com.jevon.personalos.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jevon.personalos.alerts.*
import com.jevon.personalos.data.*
import com.jevon.personalos.schedule.RecordTarget
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
internal fun AlertSettingsScreen(database:PersonalDatabase,delivery:LocalAlerts,target:RecordTarget,close:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var prefs by remember(target){mutableStateOf<List<AlertPreferenceEntity>?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf(delivery.status(target.domain))}
    LaunchedEffect(target){try {prefs=database.personalDao().alertPreferences().filter{it.domain==target.domain && it.recordId==target.id}}catch(_:Exception){error="Couldn't load alert preferences. Close and try again."}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->scope.launch {
        // Permission and notification app-op state can settle on different frames after the system dialog.
        repeat(10){delivery.reconcile();status=delivery.status(target.domain);if(!granted||delivery.enabled(target.domain))return@launch;delay(200)}
    }}
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner,delivery,target) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME)scope.launch {delivery.reconcile();status=delivery.status(target.domain)}}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}
    }
    val loaded=prefs
    if(loaded==null){Column(Modifier.padding(20.dp)){if(error!=null)Text(error!!) else CircularProgressIndicator();TextButton(onClick=close){Text("Back")}};return}
    var enabled by rememberSaveable(target.id,target.domain){mutableStateOf(loaded.isNotEmpty())}
    var selected by rememberSaveable(target.id,target.domain){mutableStateOf(loaded.map{it.leadMinutes}.joinToString(",").ifBlank{if(target.domain=="SUBSCRIPTION")"4320" else "0"})}
    var time by rememberSaveable(target.id,target.domain){mutableStateOf(LocalTime.ofSecondOfDay((loaded.firstOrNull()?.minuteOfDay?:540)*60L).toString())}
    var custom by rememberSaveable {mutableStateOf("")}
    val choices=if(target.domain=="SUBSCRIPTION")listOf(20160 to "14 days before",10080 to "7 days before",4320 to "3 days before",1440 to "1 day before",0 to "Billing day")
        else listOf(0 to "At start / due time",5 to "5 minutes before",15 to "15 minutes before",30 to "30 minutes before",60 to "1 hour before",1440 to "1 day before")
    EditorFrame("Notification timing",busy,error,close,{
        val leads=if(enabled)selected.split(',').filter{it.isNotBlank()}.map{it.toInt()}.toSet() else emptySet()
        require(!enabled||leads.isNotEmpty()){"Select at least one lead time"}
        val local=LocalTime.parse(time)
        busy=true;error=null
        scope.launch {try {database.personalDao().saveAlerts(target.domain,target.id,leads,local.hour*60+local.minute);delivery.reconcile();close()}
            catch(e:IllegalArgumentException){error=e.message} catch(_:Exception){error="Couldn't save alert preferences. Try again."}finally{busy=false}}
    }) {
        val dirty=LocalMarkDraftDirty.current
        Row(Modifier.fillMaxWidth().toggleable(enabled,enabled=!busy,role=Role.Checkbox,onValueChange={dirty();enabled=it}).heightIn(min=56.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(enabled,onCheckedChange=null);Text("Alerts for this record",Modifier.padding(start=12.dp))}
        Text("$status",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!delivery.enabled(target.domain))Button(onClick={if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Enable Android notifications")}
        TextButton(onClick={context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,"personal-os-${target.domain.lowercase()}"))}){Text("Android category settings")}
        Text("Choose when this device should alert you. Saving a reminder or payment forecast never records a Money expense.",style=MaterialTheme.typography.bodySmall)
        val values=selected.split(',').mapNotNull{it.toIntOrNull()}.toSet()
        (choices+values.filter{v->choices.none{it.first==v}}.map{it to "$it minutes before"}).forEach{(lead,label)->
            Row(Modifier.fillMaxWidth().toggleable(lead in values,enabled=enabled&&!busy,role=Role.Checkbox,onValueChange={checked->dirty();selected=(if(checked)values+lead else values-lead).sorted().joinToString(",")}).heightIn(min=48.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(lead in values,onCheckedChange=null,enabled=enabled&&!busy);Text(label,Modifier.padding(start=12.dp))}
        }
        if(target.domain!="SUBSCRIPTION"){
            Field("Custom notice (minutes)",custom,enabled&&!busy,androidx.compose.ui.text.input.KeyboardType.Number){custom=it}
            OutlinedButton(enabled=enabled&&!busy,onClick={val n=custom.toIntOrNull();if(n==null||n !in 0..20160)error="Use 0–20160 minutes" else {dirty();selected=(values+n).sorted().joinToString(",");custom="";error=null}}){Text("Add custom notice")}
        }
        CalendarTimeField("Date-based alert time",time,enabled&&!busy){time=it}
        Text("This local time applies to subscriptions, all-day events and date-only reminders. Timed items use their own event/due time. Up to six alerts; no exact-time guarantee.",style=MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarTimeField(label:String,value:String,enabled:Boolean,change:(String)->Unit) {
    var picker by remember{mutableStateOf(false)}
    val dirty=LocalMarkDraftDirty.current
    Field("$label (HH:mm)",value,enabled){change(it)}
    TextButton(enabled=enabled,onClick={picker=true}){Text("Choose ${label.lowercase()}")}
    if(picker){
        val initial=runCatching{LocalTime.parse(value)}.getOrDefault(LocalTime.of(9,0))
        val state=rememberTimePickerState(initial.hour,initial.minute,true)
        AlertDialog(onDismissRequest={picker=false},title={Text(label)},text={TimePicker(state)},confirmButton={TextButton(onClick={dirty();change("%02d:%02d".format(state.hour,state.minute));picker=false}){Text("Use time")}},dismissButton={TextButton(onClick={picker=false}){Text("Cancel")}})
    }
}
