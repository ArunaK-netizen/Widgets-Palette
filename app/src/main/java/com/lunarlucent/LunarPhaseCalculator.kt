package com.lunarlucent

import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase
import io.github.cosinekitty.astronomy.searchMoonPhase
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class LunarSnapshot(
    val phaseAngleDegrees: Double,
    val illuminationFraction: Double,
    val illuminationPercent: Int,
    val phase: String,
    val ageDays: Double,
    val nextFullMoon: Instant
)

object LunarPhaseCalculator {
    private const val NEW_MOON_ANGLE = 0.0
    private const val FULL_MOON_ANGLE = 180.0
    private const val FULL_MOON_TOLERANCE_DEGREES = 0.5
    private const val SEARCH_WINDOW_DAYS = 35.0
    private const val ONE_MILLISECOND_IN_DAYS = 1.0 / 86_400_000.0
    private const val SECONDS_PER_DAY = 86_400.0

    fun calculate(instant: Instant = Instant.now()): LunarSnapshot {
        val time = instant.toAstronomyTime()
        val phaseAngle = normalizePhaseAngle(moonPhase(time))
        val illuminationFraction = illumination(Body.Moon, time)
            .phaseFraction
            .coerceIn(0.0, 1.0)
        val phase = classifyPhase(phaseAngle)
        val previousNewMoonFromProbe = searchMoonPhase(
            NEW_MOON_ANGLE,
            time.addDays(ONE_MILLISECOND_IN_DAYS),
            -SEARCH_WINDOW_DAYS
        ) ?: error("Astronomy Engine could not find the previous New Moon from $instant")
        val previousNewMoon =
            if (previousNewMoonFromProbe.toInstant().isAfter(instant)) {
                searchMoonPhase(NEW_MOON_ANGLE, time, -SEARCH_WINDOW_DAYS)
                    ?: error("Astronomy Engine could not find the previous New Moon from $instant")
            } else {
                previousNewMoonFromProbe
            }
        val previousNewMoonInstant = previousNewMoon.toInstant()
        val nextNewMoonInstant = findPhaseStrictlyAfter(
            NEW_MOON_ANGLE,
            time,
            instant
        ).toInstant()
        val ageSeconds = secondsBetween(previousNewMoonInstant, instant)
        val lunarMonthSeconds = secondsBetween(previousNewMoonInstant, nextNewMoonInstant)
        check(ageSeconds >= 0.0 && ageSeconds < lunarMonthSeconds) {
            "Astronomy Engine returned invalid New Moon events around $instant"
        }

        val nextFullMoon = findPhaseStrictlyAfter(FULL_MOON_ANGLE, time, instant)

        return LunarSnapshot(
            phaseAngleDegrees = phaseAngle,
            illuminationFraction = illuminationFraction,
            illuminationPercent = (illuminationFraction * 100.0)
                .roundToInt()
                .coerceIn(0, if (phase == "Full Moon") 100 else 99),
            phase = phase,
            ageDays = ageSeconds / SECONDS_PER_DAY,
            nextFullMoon = nextFullMoon.toInstant()
        )
    }

    fun classifyPhase(phaseAngleDegrees: Double): String {
        val angle = normalizePhaseAngle(phaseAngleDegrees)
        return when {
            abs(angle - FULL_MOON_ANGLE) <= FULL_MOON_TOLERANCE_DEGREES -> "Full Moon"
            angle < 22.5 || angle >= 337.5 -> "New Moon"
            angle < 67.5 -> "Waxing Crescent"
            angle < 112.5 -> "First Quarter"
            angle < FULL_MOON_ANGLE -> "Waxing Gibbous"
            angle < 247.5 -> "Waning Gibbous"
            angle < 292.5 -> "Last Quarter"
            else -> "Waning Crescent"
        }
    }

    fun normalizePhaseAngle(phaseAngleDegrees: Double): Double {
        require(phaseAngleDegrees.isFinite()) { "Phase angle must be finite" }
        return ((phaseAngleDegrees % 360.0) + 360.0) % 360.0
    }

    fun formatFullMoonEvent(
        event: Instant,
        zoneId: ZoneId,
        locale: Locale = Locale.getDefault()
    ): String = DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", locale)
        .withZone(zoneId)
        .format(event)

    private fun findPhaseStrictlyAfter(targetAngle: Double, time: Time, instant: Instant): Time {
        val event = searchMoonPhase(targetAngle, time, SEARCH_WINDOW_DAYS)
            ?: error("Astronomy Engine could not find phase $targetAngle after $instant")
        return if (event.toInstant().isAfter(instant)) {
            event
        } else {
            searchMoonPhase(
                targetAngle,
                time.addDays(ONE_MILLISECOND_IN_DAYS),
                SEARCH_WINDOW_DAYS
            ) ?: error("Astronomy Engine could not find the following phase $targetAngle after $instant")
        }
    }

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

    private fun Time.toInstant(): Instant =
        Instant.ofEpochMilli(toMillisecondsSince1970())

    private fun secondsBetween(start: Instant, end: Instant): Double {
        val duration = Duration.between(start, end)
        return duration.seconds + duration.nano / 1_000_000_000.0
    }
}
