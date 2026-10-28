package com.lunarlucent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.widget.RemoteViews
import kotlin.math.roundToInt

internal data class WidgetAppearanceSettings(
    val color: Int = DEFAULT_COLOR,
    val opacity: Int = 80,
    val cornerRadius: Int = 28,
    val mode: String = MODE_LUCENT,
    val inset: Int = 8
) {
    companion object {
        const val MODE_LUCENT = "lucent"
        const val MODE_GLASS = "glass"
        const val MODE_SOLID = "solid"
        const val DEFAULT_COLOR = 0xFF7B7A7D.toInt()
    }
}

internal object WidgetAppearance {
    private const val PREFERENCES = "widget_appearance"
    private const val BITMAP_SIZE = 440

    fun load(context: Context, widgetKey: String): WidgetAppearanceSettings {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (".instance." in widgetKey && !preferences.contains("$widgetKey.color")) {
            return load(context, widgetKey.substringBefore(".instance."))
        }
        val defaults = WidgetAppearanceSettings()
        return WidgetAppearanceSettings(
            color = preferences.getInt("$widgetKey.color", defaults.color),
            opacity = preferences.getInt("$widgetKey.opacity", defaults.opacity).coerceIn(10, 100),
            cornerRadius = preferences.getInt("$widgetKey.radius", defaults.cornerRadius).coerceIn(8, 48),
            mode = preferences.getString("$widgetKey.mode", defaults.mode) ?: defaults.mode,
            inset = preferences.getInt("$widgetKey.inset", defaults.inset).coerceIn(0, 20)
        )
    }

    fun keyForInstance(context: Context, widgetKey: String, widgetId: Int): String {
        val instanceKey = "$widgetKey.instance.$widgetId"
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return if (preferences.contains("$instanceKey.color")) instanceKey else widgetKey
    }

    fun clearInstance(context: Context, widgetKey: String, widgetId: Int) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove("$widgetKey.instance.$widgetId.color")
            .remove("$widgetKey.instance.$widgetId.opacity")
            .remove("$widgetKey.instance.$widgetId.radius")
            .remove("$widgetKey.instance.$widgetId.mode")
            .remove("$widgetKey.instance.$widgetId.inset")
            .apply()
    }

    fun save(context: Context, widgetKey: String, settings: WidgetAppearanceSettings) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putInt("$widgetKey.color", settings.color)
            .putInt("$widgetKey.opacity", settings.opacity.coerceIn(10, 100))
            .putInt("$widgetKey.radius", settings.cornerRadius.coerceIn(8, 48))
            .putString("$widgetKey.mode", settings.mode)
            .putInt("$widgetKey.inset", settings.inset.coerceIn(0, 20))
            .apply()
    }

    fun apply(
        context: Context,
        views: RemoteViews,
        backgroundViewId: Int,
        widgetKey: String
    ) {
        val settings = load(context, widgetKey)
        views.setImageViewBitmap(backgroundViewId, createBackground(settings))
    }

    fun insetPixels(context: Context, widgetKey: String): Int =
        (load(context, widgetKey).inset * context.resources.displayMetrics.density).roundToInt()

    private fun createBackground(settings: WidgetAppearanceSettings): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = settings.cornerRadius * 2f
        val bounds = RectF(0f, 0f, BITMAP_SIZE.toFloat(), BITMAP_SIZE.toFloat())
        val shape = Path().apply {
            addRoundRect(bounds, radius, radius, Path.Direction.CW)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                (settings.opacity * 255 / 100f).roundToInt(),
                Color.red(settings.color),
                Color.green(settings.color),
                Color.blue(settings.color)
            )
        }
        canvas.drawPath(shape, paint)
        if (settings.mode == WidgetAppearanceSettings.MODE_GLASS) {
            canvas.save()
            canvas.clipPath(shape)
            paint.shader = android.graphics.LinearGradient(
                0f,
                0f,
                0f,
                BITMAP_SIZE.toFloat(),
                intArrayOf(0x36FFFFFF, 0x08FFFFFF, 0x00000000),
                floatArrayOf(0f, 0.28f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(bounds, paint)
            paint.shader = null
            paint.color = 0x20FFFFFF
            canvas.drawRoundRect(
                RectF(BITMAP_SIZE * 0.06f, BITMAP_SIZE * 0.025f, BITMAP_SIZE * 0.94f, BITMAP_SIZE * 0.07f),
                radius,
                radius,
                paint
            )
            canvas.restore()
        }
        bitmap.prepareToDraw()
        return bitmap
    }
}
