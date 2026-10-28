package com.jevon.personalos.alerts

import android.content.Intent
import com.jevon.personalos.schedule.RecordTarget
import java.time.LocalDate

internal fun Intent.recordTarget():RecordTarget? {
    val domain=getStringExtra("recordDomain")?:return null
    val id=getStringExtra("recordId")?:return null
    if(domain !in setOf("EVENT","REMINDER","SUBSCRIPTION")||id.isBlank()||id.length>200)return null
    val day=getLongExtra("occurrenceDay",Long.MIN_VALUE).takeIf{it!=Long.MIN_VALUE&&runCatching{LocalDate.ofEpochDay(it)}.isSuccess}
    return RecordTarget(domain,id,day)
}
