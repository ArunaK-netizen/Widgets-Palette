package com.lunarlucent

import android.content.Context
import org.json.JSONObject
import java.util.Locale

internal data class Constellation(
    val name: String,
    val abbreviation: String,
    val region: String,
    val description: String,
    val rightAscensionDegrees: Double,
    val declinationDegrees: Double,
    val image: String,
    val credit: String,
    val sourcePage: String,
    val originalPhotoSource: String,
    val licenseUrl: String,
    val lineSegments: List<List<SkyPoint>>
)

internal data class SkyPoint(
    val rightAscensionDegrees: Double,
    val declinationDegrees: Double
)

internal object ConstellationCatalog {
    @Volatile
    private var cached: List<Constellation>? = null

    fun all(context: Context): List<Constellation> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: read(context).also { cached = it }
        }
    }

    private fun read(context: Context): List<Constellation> {
        val json = context.assets.open("constellations/constellations.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val data = json.getJSONArray("constellations")
        val result = ArrayList<Constellation>(data.length())
        for (index in 0 until data.length()) {
            val item = data.getJSONObject(index)
            val segments = item.getJSONArray("lines")
            val lineSegments = ArrayList<List<SkyPoint>>(segments.length())
            for (segmentIndex in 0 until segments.length()) {
                val points = segments.getJSONArray(segmentIndex)
                val line = ArrayList<SkyPoint>(points.length())
                for (pointIndex in 0 until points.length()) {
                    val point = points.getJSONArray(pointIndex)
                    line += SkyPoint(point.getDouble(0), point.getDouble(1))
                }
                lineSegments += line
            }
            result += Constellation(
                name = item.getString("name"),
                abbreviation = item.getString("abbreviation"),
                region = item.getString("region"),
                description = item.getString("description"),
                rightAscensionDegrees = item.getDouble("raCenterDegrees"),
                declinationDegrees = item.getDouble("decCenterDegrees"),
                image = item.getString("image"),
                credit = item.getString("credit"),
                sourcePage = item.getString("sourcePage"),
                originalPhotoSource = item.optString("originalPhotoSource"),
                licenseUrl = item.getString("licenseUrl"),
                lineSegments = lineSegments
            )
        }
        check(result.size == EXPECTED_CONSTELLATION_COUNT) {
            "Expected $EXPECTED_CONSTELLATION_COUNT bundled constellations; found ${result.size}"
        }
        check(result.map { it.abbreviation }.toSet().size == EXPECTED_CONSTELLATION_COUNT) {
            "Bundled constellation abbreviations must be unique"
        }
        return result.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private const val EXPECTED_CONSTELLATION_COUNT = 88
}
