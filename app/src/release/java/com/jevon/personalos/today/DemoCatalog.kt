package com.jevon.personalos.today

import java.time.LocalDate
import java.time.ZoneId

object DemoCatalog {
    const val available = false
    fun snapshot(date: LocalDate, zone: ZoneId): Snapshot = Snapshot()
}
