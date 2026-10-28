package com.lunarlucent

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

class MoonOnlyWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach {
            WidgetAppearance.clearInstance(context, WIDGET_APPEARANCE_KEY, it)
        }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        val intent = Intent(context, MoonOnlyWidgetProvider::class.java).setAction(ACTION_DAILY_REFRESH)
        val pending = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
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
        manager.getAppWidgetIds(
            android.content.ComponentName(context, MoonOnlyWidgetProvider::class.java)
        ).forEach { render(context, manager, it) }
    }

    private fun scheduleDaily(context: Context) {
        val intent = Intent(context, MoonOnlyWidgetProvider::class.java).setAction(ACTION_DAILY_REFRESH)
        val pending = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextLocalMidnight = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .set(AlarmManager.RTC, nextLocalMidnight, pending)
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val data = LunarPhaseCalculator.calculate()
        val views = RemoteViews(context.packageName, R.layout.widget_moon_only)
        val appearanceKey = WidgetAppearance.keyForInstance(context, WIDGET_APPEARANCE_KEY, id)
        val inset = WidgetAppearance.insetPixels(context, appearanceKey)
        WidgetAppearance.apply(
            context,
            views,
            R.id.moon_only_widget_background,
            appearanceKey
        )
        views.setViewPadding(R.id.moon_only_image, inset, inset, inset, inset)
        MoonWidgetImage.update(context, views, R.id.moon_only_image, data.phaseAngleDegrees)
        manager.updateAppWidget(id, views)
    }

    companion object {
        private const val TAG = "MoonOnlyWidgetProvider"
        private const val ACTION_DAILY_REFRESH = "com.lunarlucent.MOON_ONLY_DAILY_REFRESH"
        private const val ACTION_APPEARANCE_CHANGED = "com.lunarlucent.APPEARANCE_CHANGED"
        private const val WIDGET_APPEARANCE_KEY = "moon_only"
        private const val ALARM_REQUEST_CODE = 902
    }
}
