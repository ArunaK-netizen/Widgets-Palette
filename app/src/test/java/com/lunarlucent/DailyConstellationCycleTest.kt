package com.lunarlucent

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyConstellationCycleTest {
    @Test
    fun advancesExactlyOneConstellationPerCalendarDay() {
        val firstDay = LocalDate.of(2026, 10, 27)

        assertEquals(0, DailyConstellationCycle.index(firstDay, firstDay, 88))
        assertEquals(1, DailyConstellationCycle.index(firstDay, firstDay.plusDays(1), 88))
        assertEquals(2, DailyConstellationCycle.index(firstDay, firstDay.plusDays(2), 88))
    }

    @Test
    fun restartsAfterShowingAllEightyEightConstellations() {
        val firstDay = LocalDate.of(2026, 10, 27)

        assertEquals(87, DailyConstellationCycle.index(firstDay, firstDay.plusDays(87), 88))
        assertEquals(0, DailyConstellationCycle.index(firstDay, firstDay.plusDays(88), 88))
        assertEquals(1, DailyConstellationCycle.index(firstDay, firstDay.plusDays(89), 88))
    }

    @Test
    fun earlierLocalDateDoesNotProduceANegativeIndex() {
        val firstDay = LocalDate.of(2026, 10, 27)

        assertEquals(0, DailyConstellationCycle.index(firstDay, firstDay.minusDays(1), 88))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnEmptyConstellationList() {
        DailyConstellationCycle.index(
            LocalDate.of(2026, 10, 27),
            LocalDate.of(2026, 10, 27),
            0
        )
    }
}
