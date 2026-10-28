package com.lunarlucent

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class LunarWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val id = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val manager = AppWidgetManager.getInstance(context)
                    if (supportsInformationPage(manager.getAppWidgetOptions(id))) {
                        val prefs = context.getSharedPreferences("widget_pages", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("page_$id", !prefs.getBoolean("page_$id", false)).apply()
                    }
                    render(context, manager, id)
                }
            }
            ACTION_DAILY_REFRESH,
            ACTION_APPEARANCE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                refreshAll(context)
                scheduleDaily(context)
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
        scheduleDaily(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        render(context, manager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences("widget_pages", Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach {
            editor.remove("page_$it")
            WidgetAppearance.clearInstance(context, WIDGET_APPEARANCE_KEY, it)
        }
        editor.apply()
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        val intent = Intent(context, LunarWidgetProvider::class.java).setAction(ACTION_DAILY_REFRESH)
        val pending = PendingIntent.getBroadcast(
            context,
            901,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
        }
        super.onDisabled(context)
    }

    private fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, LunarWidgetProvider::class.java)).forEach { render(context, manager, it) }
    }

    private fun scheduleDaily(context: Context) {
        val intent = Intent(context, LunarWidgetProvider::class.java).setAction(ACTION_DAILY_REFRESH)
        val pending = PendingIntent.getBroadcast(context, 901, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextLocalMidnight = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        alarm.set(AlarmManager.RTC, nextLocalMidnight, pending)
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val now = Instant.now()
        val data = LunarPhaseCalculator.calculate(now)
        val prefs = context.getSharedPreferences("widget_pages", Context.MODE_PRIVATE)
        val options = manager.getAppWidgetOptions(id)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val compact = minWidth in 1 until COMPACT_CELL_THRESHOLD_DP &&
            minHeight in 1 until COMPACT_CELL_THRESHOLD_DP
        val compactPanel = isCompactPanelSize(minWidth, minHeight)
        val info = !compactPanel && supportsInformationPage(options) &&
            prefs.getBoolean("page_$id", false)
        val views = RemoteViews(context.packageName, R.layout.widget_lunar)
        val appearanceKey = WidgetAppearance.keyForInstance(context, WIDGET_APPEARANCE_KEY, id)
        val inset = WidgetAppearance.insetPixels(context, appearanceKey)
        WidgetAppearance.apply(
            context,
            views,
            R.id.lunar_widget_background,
            appearanceKey
        )
        val imagePadding = (if (compact) 0 else (5 * context.resources.displayMetrics.density).roundToInt()) + inset
        views.setViewPadding(R.id.moon_image, imagePadding, imagePadding, imagePadding, imagePadding)
        views.setViewPadding(R.id.info_page, inset, inset, inset, inset)
        views.setViewPadding(R.id.portrait_page, inset, inset, inset, inset)
        views.setViewPadding(R.id.widget_root, 0, 0, 0, 0)
        views.setViewVisibility(
            R.id.moon_image,
            if (info || compactPanel) android.view.View.GONE else android.view.View.VISIBLE
        )
        views.setViewVisibility(R.id.info_page, if (info) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(
            R.id.portrait_page,
            if (compactPanel) android.view.View.VISIBLE else android.view.View.GONE
        )
        views.setTextViewText(R.id.today_label, "TODAY'S MOON")
        views.setTextViewText(R.id.phase_name, data.phase)
        views.setTextViewText(R.id.illumination, "${data.illuminationPercent}%")
        views.setTextViewText(
            R.id.moon_age,
            "AGE  ${"%.1f".format(Locale.getDefault(), data.ageDays)} DAYS"
        )
        views.setTextViewText(
            R.id.phase_date,
            LocalDate.ofInstant(now, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
        )
        views.setTextViewText(R.id.portrait_phase_name, data.phase)
        views.setTextViewText(R.id.portrait_illumination, "${data.illuminationPercent}%")
        views.setTextViewText(
            R.id.portrait_date,
            LocalDate.ofInstant(now, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        )
        MoonWidgetImage.update(
            context,
            views,
            data.phaseAngleDegrees,
            R.id.moon_image,
            R.id.portrait_moon_image
        )
        val toggle = Intent(context, LunarWidgetProvider::class.java).setAction(ACTION_TOGGLE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        val pending = PendingIntent.getBroadcast(context, id, toggle, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        manager.updateAppWidget(id, views)
    }

    private fun supportsInformationPage(options: Bundle): Boolean {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        return minWidth >= INFORMATION_PAGE_MIN_SIZE_DP &&
            minHeight >= INFORMATION_PAGE_MIN_SIZE_DP
    }

    private fun isCompactPanelSize(minWidth: Int, minHeight: Int): Boolean {
        val narrow = minWidth in 1 until INFORMATION_PAGE_MIN_SIZE_DP
        val short = minHeight in 1 until INFORMATION_PAGE_MIN_SIZE_DP
        return narrow xor short
    }

    companion object {
        private const val COMPACT_CELL_THRESHOLD_DP = 100
        private const val INFORMATION_PAGE_MIN_SIZE_DP = 100
        private const val ACTION_TOGGLE = "com.lunarlucent.TOGGLE"
        private const val ACTION_DAILY_REFRESH = "com.lunarlucent.DAILY_REFRESH"
        private const val ACTION_APPEARANCE_CHANGED = "com.lunarlucent.APPEARANCE_CHANGED"
        private const val WIDGET_APPEARANCE_KEY = "lunar"
    }
}
