package com.lunarlucent

import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.nextMoonQuarter
import io.github.cosinekitty.astronomy.searchMoonPhase
import io.github.cosinekitty.astronomy.searchMoonQuarter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LunarPhaseCalculatorTest {
    @Test
    fun astronomicalQuarterEventsHaveExpectedPhaseAndIllumination() {
        val expected = mapOf(
            0 to ("New Moon" to 0.0),
            1 to ("First Quarter" to 0.5),
            2 to ("Full Moon" to 1.0),
            3 to ("Last Quarter" to 0.5)
        )

        expected.forEach { (quarter, expectation) ->
            val event = quarterEvent(quarter)
            val result = LunarPhaseCalculator.calculate(event.toInstant())

            assertEquals(expectation.first, result.phase)
            assertEquals(expectation.second, result.illuminationFraction, ILLUMINATION_TOLERANCE)
            if (quarter == 0) {
                assertTrue("New Moon age should be near zero", result.ageDays < 0.0001)
            }
            if (quarter == 2) {
                assertEquals(100, (result.illuminationFraction * 100.0).roundToInt())
                assertEquals(100, result.illuminationPercent)
            }
        }
    }

    @Test
    fun waxingAndWaningGibbousUseTheDocumentedAngleDirection() {
        val waxingTime = phaseEvent(135.0)
        val waningTime = phaseEvent(225.0)
        val waxing = LunarPhaseCalculator.calculate(waxingTime)
        val waning = LunarPhaseCalculator.calculate(waningTime)
        val waxingBefore = illuminationAt(waxingTime.minus(1, ChronoUnit.HOURS))
        val waxingAfter = illuminationAt(waxingTime.plus(1, ChronoUnit.HOURS))
        val waningBefore = illuminationAt(waningTime.minus(1, ChronoUnit.HOURS))
        val waningAfter = illuminationAt(waningTime.plus(1, ChronoUnit.HOURS))

        assertEquals("Waxing Gibbous", waxing.phase)
        assertTrue(waxing.illuminationFraction > 0.5)
        assertTrue(waxingAfter > waxingBefore)
        assertEquals("Waning Gibbous", waning.phase)
        assertTrue(waning.illuminationFraction > 0.5)
        assertTrue(waningAfter < waningBefore)
        assertEquals(135.0, waxing.phaseAngleDegrees, PHASE_ANGLE_TOLERANCE)
        assertEquals(225.0, waning.phaseAngleDegrees, PHASE_ANGLE_TOLERANCE)
    }

    @Test
    fun nearFullWaningMoonIsNotMislabelledAsFullOrWaxing() {
        val instant = phaseEvent(198.0)
        val result = LunarPhaseCalculator.calculate(instant)
        val image = MoonImageSelector.closest(result.phaseAngleDegrees)

        assertEquals("Waning Gibbous", result.phase)
        assertTrue("Near-full waning illumination should be 97-99%", result.illuminationPercent in 97..99)
        assertEquals(198, image!!.sourcePhaseAngleDegrees)
        assertEquals("198.png", image.assetName)
    }

    @Test
    fun phaseClassificationUsesConsistentHalfwayBoundaries() {
        val boundaries = listOf(
            Triple(22.5, "New Moon", "Waxing Crescent"),
            Triple(67.5, "Waxing Crescent", "First Quarter"),
            Triple(112.5, "First Quarter", "Waxing Gibbous"),
            Triple(247.5, "Waning Gibbous", "Last Quarter"),
            Triple(292.5, "Last Quarter", "Waning Crescent"),
            Triple(337.5, "Waning Crescent", "New Moon")
        )
        boundaries.forEach { (boundary, before, after) ->
            assertEquals(before, LunarPhaseCalculator.classifyPhase(boundary - 0.001))
            assertEquals(after, LunarPhaseCalculator.classifyPhase(boundary))
        }
        assertEquals("Waxing Gibbous", LunarPhaseCalculator.classifyPhase(179.499))
        assertEquals("Full Moon", LunarPhaseCalculator.classifyPhase(179.5))
        assertEquals("Full Moon", LunarPhaseCalculator.classifyPhase(180.5))
        assertEquals("Waning Gibbous", LunarPhaseCalculator.classifyPhase(180.501))
    }

    @Test
    fun lunarAgeIsMeasuredBetweenActualNewMoonEvents() {
        val previousNewMoon = searchMoonPhase(0.0, testStartTime(), 35.0)!!
        val nextNewMoon = searchMoonPhase(0.0, previousNewMoon.addDays(1.0), 35.0)!!
        val previousInstant = previousNewMoon.toInstant()
        val nextInstant = nextNewMoon.toInstant()
        val instant = previousInstant.plusSeconds(
            ChronoUnit.SECONDS.between(previousInstant, nextInstant) / 2
        )
        val result = LunarPhaseCalculator.calculate(instant)
        val actualLunationDays =
            ChronoUnit.MILLIS.between(previousInstant, nextInstant) / MILLIS_PER_DAY

        assertTrue(result.ageDays >= 0.0)
        assertTrue(result.ageDays < actualLunationDays)
        assertEquals(actualLunationDays / 2.0, result.ageDays, 0.001)
    }

    @Test
    fun nextFullMoonIsAnAstronomicalEventAfterTheObservation() {
        val instant = phaseEvent(135.0)
        val result = LunarPhaseCalculator.calculate(instant)

        assertTrue(result.nextFullMoon.isAfter(instant))
        assertEquals(
            180.0,
            LunarPhaseCalculator.calculate(result.nextFullMoon).phaseAngleDegrees,
            PHASE_ANGLE_TOLERANCE
        )
    }

    @Test
    fun fullMoonEventFormattingUsesTheRequestedTimeZone() {
        val event = Instant.parse("2026-10-27T20:30:00Z")

        assertEquals(
            "Wed, 28 Oct · 02:00",
            LunarPhaseCalculator.formatFullMoonEvent(
                event,
                ZoneId.of("Asia/Kolkata"),
                Locale.US
            )
        )
    }

    private fun quarterEvent(quarter: Int): Time {
        var event = searchMoonQuarter(testStartTime())
        repeat(8) {
            if (event.quarter == quarter) return event.time
            event = nextMoonQuarter(event)
        }
        error("No quarter $quarter found in the test window")
    }

    private fun phaseEvent(angle: Double): Instant =
        searchMoonPhase(angle, testStartTime(), 35.0)!!.toInstant()

    private fun illuminationAt(instant: Instant): Double =
        illumination(Body.Moon, instant.toAstronomyTime()).phaseFraction

    private fun testStartTime(): Time = Time(2024, 1, 1, 0, 0, 0.0)

    private fun Time.toInstant(): Instant =
        Instant.ofEpochMilli(toMillisecondsSince1970())

    private fun Instant.toAstronomyTime(): Time {
        val utc = atZone(ZoneOffset.UTC)
        return Time(
            utc.year,
            utc.monthValue,
            utc.dayOfMonth,
            utc.hour,
            utc.minute,
            utc.second + utc.nano / 1_000_000_000.0
        )
    }

    companion object {
        private const val ILLUMINATION_TOLERANCE = 0.02
        private const val PHASE_ANGLE_TOLERANCE = 0.02
        private const val MILLIS_PER_DAY = 86_400_000.0
    }
}

class MoonImageSelectorTest {
    @Test
    fun packageMappingContainsAllThreeHundredSixtyUniquePhaseAngles() {
        assertEquals(360, MoonImageSelector.samples.size)
        assertEquals(0, MoonImageSelector.samples.first().sourcePhaseAngleDegrees)
        assertEquals(90, MoonImageSelector.samples[90].sourcePhaseAngleDegrees)
        assertEquals(180, MoonImageSelector.samples[180].sourcePhaseAngleDegrees)
        assertEquals(270, MoonImageSelector.samples[270].sourcePhaseAngleDegrees)
        assertEquals(359, MoonImageSelector.samples.last().sourcePhaseAngleDegrees)
    }

    @Test
    fun selectsNearestOneDegreeImage() {
        assertEquals("000.png", MoonImageSelector.closest(0.0)!!.assetName)
        assertEquals("198.png", MoonImageSelector.closest(198.0)!!.assetName)
        assertEquals(198, MoonImageSelector.closest(198.0)!!.sourcePhaseAngleDegrees)
        assertEquals("359.png", MoonImageSelector.closest(359.0)!!.assetName)
    }

    @Test
    fun wrapsPhaseAnglesAroundZeroAndThreeHundredSixty() {
        assertEquals("000.png", MoonImageSelector.closest(360.0)!!.assetName)
        assertEquals("000.png", MoonImageSelector.closest(359.99)!!.assetName)
        assertEquals("000.png", MoonImageSelector.closest(-0.5)!!.assetName)
    }

    @Test
    fun missingMoonAssetsAreHandledWithoutInventingAnImage() {
        assertNull(MoonImageSelector.closest(90.0, emptySet()))
        val onlyAvailable = MoonImageSelector.closest(90.0, setOf("089.png", "091.png"))
        assertNotNull(onlyAvailable)
        assertEquals("089.png", onlyAvailable!!.assetName)
    }
}
