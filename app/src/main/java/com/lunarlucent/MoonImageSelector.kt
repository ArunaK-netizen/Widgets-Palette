package com.lunarlucent

import java.util.Locale
import kotlin.math.abs

data class MoonImageSample(
    val assetName: String,
    val sourcePhaseAngleDegrees: Int
)

object MoonImageSelector {
    val samples: List<MoonImageSample> = (0 until 360).map { phaseAngle ->
        MoonImageSample("%03d.png".format(Locale.ROOT, phaseAngle), phaseAngle)
    }

    fun closest(
        phaseAngleDegrees: Double,
        availableAssets: Set<String> = samples.mapTo(mutableSetOf()) { it.assetName }
    ): MoonImageSample? {
        val angle = LunarPhaseCalculator.normalizePhaseAngle(phaseAngleDegrees)
        return samples
            .asSequence()
            .filter { it.assetName in availableAssets }
            .minByOrNull { angularDistance(angle, it.sourcePhaseAngleDegrees.toDouble()) }
    }

    private fun angularDistance(first: Double, second: Double): Double {
        val difference = abs(first - second)
        return minOf(difference, 360.0 - difference)
    }
}
