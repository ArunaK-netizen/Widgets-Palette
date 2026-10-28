package com.lunarlucent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max

internal object ConstellationWidgetGraphics {
    private const val BITMAP_SIZE = 440
    private const val DISK_CACHE_DIRECTORY = "constellation-widget-art"
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount
    }
    private val preloadExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "constellation-widget-art").apply { isDaemon = true }
    }
    private val preloading = ConcurrentHashMap.newKeySet<String>()

    fun createRoundedPhoto(context: Context, image: String): Bitmap =
        cachedPhoto(context, image, BITMAP_SIZE, circular = false)

    fun createCircularPhoto(context: Context, image: String): Bitmap =
        cachedPhoto(context, image, 112, circular = true)

    private fun cachedPhoto(context: Context, image: String, size: Int, circular: Boolean): Bitmap {
        val key = "${if (circular) "circular" else "photo"}:$image"
        cache.get(key)?.let { return it }
        val bitmap = createPhoto(context, image, size, circular)
        cache.put(key, bitmap)
        return bitmap
    }

    private fun createPhoto(
        context: Context,
        image: String,
        size: Int,
        circular: Boolean
    ): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = if (size < 200) 8 else 2
        }
        val source = context.assets.open("constellations/images/$image").use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Unable to decode bundled constellation photo: $image")
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val scale = max(size.toFloat() / source.width, size.toFloat() / source.height)
        val width = source.width * scale
        val height = source.height * scale
        val destination = RectF(
            (size - width) / 2f,
            (size - height) / 2f,
            (size + width) / 2f,
            (size + height) / 2f
        )
        val path = Path().apply {
            if (circular) {
                addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
            } else {
                addRoundRect(
                    RectF(0f, 0f, size.toFloat(), size.toFloat()),
                    size * 0.12f,
                    size * 0.12f,
                    Path.Direction.CW
                )
            }
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(source, null, destination, FILTER_PAINT)
        canvas.restore()
        source.recycle()
        return output
    }

    fun createSchematic(
        context: Context,
        constellation: Constellation
    ): Bitmap {
        val key = "schematic:${constellation.abbreviation}"
        cache.get(key)?.let { return it }
        val diskFile = cachedFile(context, key)
        if (diskFile.isFile) {
            BitmapFactory.decodeFile(diskFile.absolutePath)?.let { bitmap ->
                cache.put(key, bitmap)
                return bitmap
            }
            if (!diskFile.delete()) {
                Log.w(TAG, "Could not remove invalid cached schematic: ${diskFile.absolutePath}")
            }
        }
        return drawSchematic(context, constellation).also { bitmap ->
            cache.put(key, bitmap)
        }
    }

    fun preload(context: Context, constellation: Constellation) {
        val schematicKey = "schematic:${constellation.abbreviation}"
        val photoKey = "photo:${constellation.image}"
        val circularKey = "circular:${constellation.image}"
        if (cache.get(schematicKey) != null &&
            cache.get(photoKey) != null &&
            cache.get(circularKey) != null
        ) return
        if (!preloading.add(constellation.abbreviation)) return
        val appContext = context.applicationContext
        preloadExecutor.execute {
            try {
                createRoundedPhoto(appContext, constellation.image)
                createCircularPhoto(appContext, constellation.image)
                val schematic = createSchematic(appContext, constellation)
                persistSchematic(appContext, constellation.abbreviation, schematic)
            } catch (exception: Exception) {
                Log.w(TAG, "Could not preload artwork for ${constellation.abbreviation}", exception)
            } finally {
                preloading.remove(constellation.abbreviation)
            }
        }
    }

    private fun drawSchematic(
        context: Context,
        constellation: Constellation
    ): Bitmap {
        val size = BITMAP_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        drawGrid(canvas, size, center)

        val projectedSegments = constellation.lineSegments.map { segment ->
            segment.map { point -> project(point, constellation) }
        }
        val maxExtent = projectedSegments
            .flatten()
            .maxOfOrNull { max(kotlin.math.abs(it.x), kotlin.math.abs(it.y)) }
            ?.coerceAtLeast(1.0)
            ?: 1.0
        val scale = size * 0.44 / maxExtent
        val drawingCenterY = size * 0.44f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(207, 211, 217)
            strokeWidth = 2.2f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val starCoordinates = LinkedHashSet<Pair<Double, Double>>()
        projectedSegments.forEach { segment ->
            if (segment.isNotEmpty()) {
                val path = Path()
                segment.forEachIndexed { index, point ->
                    val x = (center + point.x * scale).toFloat()
                    val y = (drawingCenterY - point.y * scale).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    starCoordinates += point.ra to point.dec
                }
                canvas.drawPath(path, paint)
            }
        }

        starCoordinates.forEachIndexed { index, coordinate ->
            val point = project(SkyPoint(coordinate.first, coordinate.second), constellation)
            val radius = if (index == starCoordinates.size / 2) 5.5f else 4f
            val x = (center + point.x * scale).toFloat()
            val y = (drawingCenterY - point.y * scale).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = when (index % 9) {
                0 -> Color.rgb(255, 164, 139)
                4 -> Color.rgb(151, 199, 255)
                else -> Color.rgb(208, 228, 247)
            }
            canvas.drawCircle(x, y, radius, paint)
        }

        paint.color = Color.rgb(229, 228, 231)
        paint.typeface = Typeface.create(context.resources.getFont(R.font.geist_variable), Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        paint.letterSpacing = 1.0f
        paint.textSize = 22f
        val title = constellation.name.uppercase(Locale.ROOT)
        val availableTitleWidth = size * 0.88f
        while (paint.measureText(title) > availableTitleWidth && paint.textSize > 12f) {
            paint.textSize -= 1f
        }
        canvas.drawText(title, center, size * 0.94f, paint)
        return bitmap
    }

    private fun persistSchematic(context: Context, abbreviation: String, bitmap: Bitmap) {
        val destination = cachedFile(context, "schematic:$abbreviation")
        if (destination.isFile) return
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode cached constellation schematic for $abbreviation"
                }
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete()
                if (!destination.isFile) {
                    error("Could not save cached constellation schematic for $abbreviation")
                }
            }
        } catch (exception: Exception) {
            temporary.delete()
            throw exception
        }
    }

    private fun cachedFile(context: Context, key: String): File {
        val directory = File(context.cacheDir, DISK_CACHE_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            error("Could not create constellation artwork cache at ${directory.absolutePath}")
        }
        val safeName = key.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(directory, "$safeName.png")
    }

    private fun drawGrid(canvas: Canvas, size: Int, center: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(24, 224, 225, 228)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(center, center, size * 0.36f, paint)
        canvas.drawLine(center, size * 0.05f, center, size * 0.95f, paint)
        canvas.drawLine(size * 0.05f, center, size * 0.95f, center, paint)
    }

    private fun project(point: SkyPoint, constellation: Constellation): ProjectedPoint {
        var raDelta = point.rightAscensionDegrees - constellation.rightAscensionDegrees
        if (raDelta > 180.0) raDelta -= 360.0
        if (raDelta < -180.0) raDelta += 360.0
        val x = raDelta * cos(Math.toRadians(constellation.declinationDegrees))
        val y = point.declinationDegrees - constellation.declinationDegrees
        return ProjectedPoint(x, y, point.rightAscensionDegrees, point.declinationDegrees)
    }

    private data class ProjectedPoint(
        val x: Double,
        val y: Double,
        val ra: Double,
        val dec: Double
    )

    private val FILTER_PAINT = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private const val TAG = "ConstellationWidgetArt"
}
