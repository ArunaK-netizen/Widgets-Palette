package com.lunarlucent

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal object DailyConstellationCycle {
    fun index(firstDay: LocalDate, currentDay: LocalDate, itemCount: Int): Int {
        require(itemCount > 0) { "Constellation cycle must contain at least one item" }
        val elapsedDays = ChronoUnit.DAYS.between(firstDay, currentDay).coerceAtLeast(0)
        return (elapsedDays % itemCount).toInt()
    }
}
