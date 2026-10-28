package com.jevon.personalos

import android.content.Intent
import com.jevon.personalos.alerts.recordTarget
import org.junit.Assert.*
import org.junit.Test

class RecordLinksTest {
    @Test fun notificationTargetsKeepDomainRecordAndOccurrenceWithoutAcceptingUnknownRoutes(){
        for(domain in listOf("EVENT","REMINDER","SUBSCRIPTION")){
            val record=Intent().putExtra("recordDomain",domain).putExtra("recordId","record/:?with-reserved-characters").putExtra("occurrenceDay",20754L).recordTarget()!!
            assertEquals(domain,record.domain);assertEquals("record/:?with-reserved-characters",record.id);assertEquals(20754L,record.day)
        }
        assertNull(Intent().putExtra("recordDomain","UNKNOWN").putExtra("recordId","x").recordTarget())
        assertNull(Intent().putExtra("recordDomain","EVENT").putExtra("recordId","").recordTarget())
        assertNull(Intent().putExtra("recordDomain","EVENT").putExtra("recordId","x".repeat(201)).recordTarget())
        assertNull(Intent().putExtra("recordDomain","REMINDER").putExtra("recordId","x").putExtra("occurrenceDay",Long.MAX_VALUE).recordTarget()!!.day)
    }
}
