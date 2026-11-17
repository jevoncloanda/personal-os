package com.jevon.personalos.alerts

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.room.withTransaction
import com.jevon.personalos.MainActivity
import com.jevon.personalos.PersonalOsApplication
import com.jevon.personalos.R
import com.jevon.personalos.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

data class AlertStatus(val enabled:Boolean,val message:String)

class LocalAlerts(private val context:Context, private val database:PersonalDatabase, private val namespace:String="personal",
    private val receiver:Class<out BroadcastReceiver> = AlertReceiver::class.java, private val clock:Clock?=null, private val deliveryAllowed:((String)->Boolean)?=null,
    private val recordIntent:(AlertStateEntity)->Intent = { target -> Intent(context,MainActivity::class.java).putExtra("recordDomain",target.domain).putExtra("recordId",target.recordId).putExtra("occurrenceDay",target.occurrenceEpochDay) }) {
    private val mutex=Mutex()
    private val manager=context.getSystemService(NotificationManager::class.java)
    private val alarms=context.getSystemService(AlarmManager::class.java)
    var lastError:String?=null; private set
    var nextScheduledAt:Long?=null; private set
    fun createChannels() { listOf("EVENT" to "Calendar events","REMINDER" to "Reminders","SUBSCRIPTION" to "Subscription renewals").forEach { (id,name)->manager.createNotificationChannel(NotificationChannel(channel(id),name,NotificationManager.IMPORTANCE_DEFAULT).apply { description="Local Poros alerts · best-effort timing"; lockscreenVisibility=Notification.VISIBILITY_PRIVATE }) } }
    private fun channel(domain:String)="personal-os-${domain.lowercase()}"
    fun enabled(domain:String):Boolean = deliveryAllowed?.invoke(domain) ?: ((Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled() && manager.getNotificationChannel(channel(domain))?.let{it.importance!=NotificationManager.IMPORTANCE_NONE}==true)
    fun status(domain:String):AlertStatus {
        val allowed=enabled(domain)
        return AlertStatus(allowed,when {lastError!=null->lastError!!; !allowed->"Alerts are blocked by notification permission or this category. Your records still work. Enable alerts or review Android settings."; else->"Best-effort local alerts. Android may delay delivery under battery restrictions."})
    }
    private fun alarmIntent()=Intent(context,receiver).setAction("com.jevon.personalos.ALERT_TICK").setData(Uri.Builder().scheme("personalos").authority("alarm").appendPath(namespace).build())
    private fun pending()=PendingIntent.getBroadcast(context,0,alarmIntent(),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancelAlarm() { alarms.cancel(pending());nextScheduledAt=null }
    private suspend fun catalog(prefs:List<AlertPreferenceEntity>):AlertCatalog {
        val dao=database.personalDao()
        val events=LinkedHashMap<String,EventEntity>(); val reminders=LinkedHashMap<String,ReminderEntity>(); val subs=LinkedHashMap<String,SubscriptionEntity>()
        prefs.forEach { p -> when(p.domain) {
            "EVENT"->if(p.recordId !in events)dao.event(p.recordId)?.let { events[it.id]=it }
            "REMINDER"->if(p.recordId !in reminders)dao.reminder(p.recordId)?.let {reminders[it.id]=it}
            else->if(p.recordId !in subs)dao.subscription(p.recordId)?.let {subs[it.id]=it}
        } }
        return AlertCatalog(events,reminders,subs,reminders.keys.flatMap {dao.completions(it)},subs.keys.flatMap {dao.paymentsFor(it)})
    }
    suspend fun reconcile() = mutex.withLock { withContext(Dispatchers.IO) {
        try {
            createChannels()
            val currentClock=clock?:Clock.systemDefaultZone()
            val now=currentClock.millis()
            var next:Long?=null
            database.withTransaction {
                val dao=database.personalDao(); val prefs=dao.alertPreferences()
                require(prefs.size<=5000) { "Too many configured alerts. Reduce alert preferences to 5,000 or fewer." }
                val data=catalog(prefs)
                val byOwner=prefs.associateBy {Triple(it.domain,it.recordId,it.leadMinutes)}
                val queued=dao.pendingAlerts()
                queued.forEach { old ->
                    val pref=byOwner[Triple(old.domain,old.recordId,old.leadMinutes)]
                    val plan=pref?.let {alertForOccurrence(it,old.occurrenceEpochDay,data,currentClock.zone)}
                    val valid=plan?.state==old
                    if(valid && old.triggerAtMillis<=now && old.triggerAtMillis>=now-86_400_000 && enabled(old.domain) && dao.alertDelivered(old.key)!=true) {
                        // Stable tag + onlyAlertOnce makes replay update the same notification after a process interruption.
                        val target=recordIntent(old).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).setData(Uri.Builder().scheme("personalos").authority("record").appendPath(namespace).appendPath(old.key).build())
                        val open=PendingIntent.getActivity(context,0,target,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        manager.notify("$namespace:${old.key}",0,NotificationCompat.Builder(context,channel(old.domain)).setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle(plan!!.title).setContentText(plan.body).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
                            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setTimeoutAfter(86_400_000).build())
                        dao.saveAlertState(old.copy(delivered=true))
                    }
                }
                // Cancel delivered notifications whose owner, occurrence or preferences no longer permit them.
                manager.activeNotifications.filter {it.tag?.startsWith("$namespace:")==true}.forEach { notification ->
                    val key=notification.tag.substringAfter(':')
                    val saved=dao.alertState(key)
                    val pref=saved?.let {byOwner[Triple(it.domain,it.recordId,it.leadMinutes)]}
                    val valid=saved!=null && pref!=null && alertForOccurrence(pref,saved.occurrenceEpochDay,data,currentClock.zone)?.state?.key==key
                    if(!valid)manager.cancel(notification.tag,notification.id)
                }
                dao.clearPendingAlerts()
                prefs.forEach { pref ->
                    var cursor=currentClock
                    // A future key may already have been delivered before a clock change. Seek past those receipts.
                    repeat(128) {
                        val plan=nextAlert(pref,data,cursor)?:return@forEach
                        if(dao.alertDelivered(plan.state.key)!=true) {
                            dao.saveAlertState(plan.state)
                            if(enabled(pref.domain))next=minOf(next?:Long.MAX_VALUE,plan.state.triggerAtMillis)
                            return@forEach
                        }
                        cursor=Clock.fixed(java.time.Instant.ofEpochMilli(plan.state.triggerAtMillis+1),currentClock.zone)
                    }
                }
            }
            cancelAlarm()
            next?.let { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,it,pending());nextScheduledAt=it }
            lastError=null
        } catch(e:CancellationException){throw e} catch(_:Exception) { cancelAlarm(); lastError="Alerts couldn't be refreshed. Open Poros and retry; your records are preserved." }
    } }
}

open class AlertReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!="com.jevon.personalos.ALERT_TICK")return
        val result=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {try {(context.applicationContext as PersonalOsApplication).alerts.reconcile()}finally{result.finish()} }
    }
}
class AlertRecoveryReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_MY_PACKAGE_REPLACED))return
        val result=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {try {(context.applicationContext as PersonalOsApplication).alerts.reconcile()}finally{result.finish()} }
    }
}
