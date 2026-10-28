package com.lunarlucent

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class ConstellationWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_NEXT_PAGE -> {
                val id = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val preferences =
                        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    val page = (preferences.getInt(pageKey(id), 0) + 1) % PAGE_COUNT
                    preferences.edit().putInt(pageKey(id), page).apply()
                    render(context, AppWidgetManager.getInstance(context), id, page)
                }
            }
            ACTION_DAILY_REFRESH,
            ACTION_APPEARANCE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> {
                refreshAll(context)
                scheduleDaily(context)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            render(context, appWidgetManager, id, savedPage(context, id))
        }
        scheduleDaily(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach {
            editor.remove(pageKey(it))
            editor.remove(startDateKey(it))
            WidgetAppearance.clearInstance(context, WIDGET_APPEARANCE_KEY, it)
        }
        editor.apply()
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        val pending = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, ConstellationWidgetProvider::class.java).setAction(ACTION_DAILY_REFRESH),
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
        val component = ComponentName(context, ConstellationWidgetProvider::class.java)
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        manager.getAppWidgetIds(component).forEach { id ->
            render(context, manager, id, preferences.getInt(pageKey(id), 0))
        }
    }

    private fun scheduleDaily(context: Context) {
        val intent = Intent(context, ConstellationWidgetProvider::class.java)
            .setAction(ACTION_DAILY_REFRESH)
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
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextLocalMidnight, pending)
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        page: Int
    ) {
        val constellations = ConstellationCatalog.all(context)
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val firstDay = preferences.getString(startDateKey(id), null)?.let(LocalDate::parse)
            ?: today.also {
                preferences.edit().putString(startDateKey(id), it.toString()).apply()
            }
        val index = DailyConstellationCycle.index(firstDay, today, constellations.size)
        val constellation = constellations[index]

        val layout = when (page) {
            PAGE_SCHEMATIC -> R.layout.widget_constellation_schematic
            PAGE_PROFILE -> R.layout.widget_constellation_profile
            else -> R.layout.widget_constellation_photo
        }
        val views = RemoteViews(context.packageName, layout)
        val appearanceKey = WidgetAppearance.keyForInstance(context, WIDGET_APPEARANCE_KEY, id)
        WidgetAppearance.apply(
            context,
            views,
            R.id.constellation_widget_background,
            appearanceKey
        )
        val inset = WidgetAppearance.insetPixels(context, appearanceKey)
        when (page) {
            PAGE_SCHEMATIC -> views.setViewPadding(
                R.id.constellation_schematic,
                inset,
                inset,
                inset,
                inset
            )
            PAGE_PROFILE -> views.setViewPadding(
                R.id.constellation_profile_content,
                inset + (6 * context.resources.displayMetrics.density).toInt(),
                inset + (6 * context.resources.displayMetrics.density).toInt(),
                inset + (6 * context.resources.displayMetrics.density).toInt(),
                inset + (6 * context.resources.displayMetrics.density).toInt()
            )
            else -> views.setViewPadding(R.id.constellation_photo, inset, inset, inset, inset)
        }
        when (page) {
            PAGE_SCHEMATIC -> {
                views.setImageViewBitmap(
                    R.id.constellation_schematic,
                    ConstellationWidgetGraphics.createSchematic(context, constellation)
                )
                views.setContentDescription(
                    R.id.constellation_schematic,
                    "${constellation.name} star-line schematic"
                )
            }
            PAGE_PROFILE -> {
                views.setImageViewBitmap(
                    R.id.constellation_profile_image,
                    ConstellationWidgetGraphics.createCircularPhoto(context, constellation.image)
                )
                val title = constellation.name.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
                views.setTextViewText(R.id.constellation_title, title)
                views.setTextViewTextSize(
                    R.id.constellation_title,
                    TypedValue.COMPLEX_UNIT_SP,
                    titleTextSize(context, manager, id, title)
                )
                views.setTextViewText(R.id.constellation_subtitle, profileSubtitle(constellation))
                views.setTextViewText(
                    R.id.constellation_profile_lead,
                    profileLead(constellation.description)
                )
                val detail = profileDetail(constellation.description)
                views.setTextViewText(R.id.constellation_profile_detail, detail)
                views.setViewVisibility(
                    R.id.constellation_profile_detail,
                    if (detail.isEmpty()) View.GONE else View.VISIBLE
                )
                views.setContentDescription(
                    R.id.constellation_profile_image,
                    constellation.name
                )
            }
            else -> {
                views.setImageViewBitmap(
                    R.id.constellation_photo,
                    ConstellationWidgetGraphics.createRoundedPhoto(context, constellation.image)
                )
                views.setContentDescription(
                    R.id.constellation_photo,
                    "${constellation.name}. ${constellation.credit}. Licensed under CC BY 4.0."
                )
            }
        }
        ConstellationWidgetGraphics.preload(context, constellation)

        val nextPage = Intent(context, ConstellationWidgetProvider::class.java)
            .setAction(ACTION_NEXT_PAGE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        val pending = PendingIntent.getBroadcast(
            context,
            id,
            nextPage,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.constellation_widget_root, pending)
        manager.updateAppWidget(id, views)
    }

    private fun savedPage(context: Context, id: Int): Int =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getInt(pageKey(id), 0)
            .coerceIn(0, PAGE_COUNT - 1)

    private fun profileSubtitle(constellation: Constellation): String =
        if (constellation.abbreviation == "Ori") "THE HUNTER"
        else constellation.region.uppercase(Locale.ROOT)

    private fun profileLead(description: String): String =
        "${description.substringBefore(". ").trimEnd('.')}."

    private fun profileDetail(description: String): String =
        description.substringAfter(". ", missingDelimiterValue = "").trim()

    private fun titleTextSize(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        title: String
    ): Float {
        val widgetWidth = manager.getAppWidgetOptions(id).getInt(
            AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            DEFAULT_WIDGET_WIDTH_DP
        )
        val availableWidthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            (widgetWidth - PROFILE_HORIZONTAL_PADDING_DP * 2 - PROFILE_IMAGE_SIZE_DP - 8)
                .coerceAtLeast(MIN_TITLE_WIDTH_DP).toFloat(),
            context.resources.displayMetrics
        )
        val paint = Paint().apply {
            typeface = Typeface.create(
                context.resources.getFont(R.font.geist_variable),
                Typeface.NORMAL
            )
        }
        var sizeSp = MAX_TITLE_SIZE_SP
        while (sizeSp > MIN_TITLE_SIZE_SP) {
            paint.textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sizeSp,
                context.resources.displayMetrics
            )
            val letterSpacingWidth = title.length * paint.textSize * TITLE_LETTER_SPACING
            if (paint.measureText(title) + letterSpacingWidth <= availableWidthPx) break
            sizeSp -= 0.5f
        }
        return sizeSp
    }

    private fun pageKey(id: Int) = "page_$id"
    private fun startDateKey(id: Int) = "start_date_$id"

    companion object {
        private const val ACTION_NEXT_PAGE = "com.lunarlucent.CONSTELLATION_NEXT_PAGE"
        private const val ACTION_DAILY_REFRESH = "com.lunarlucent.CONSTELLATION_DAILY_REFRESH"
        private const val ACTION_APPEARANCE_CHANGED = "com.lunarlucent.APPEARANCE_CHANGED"
        private const val WIDGET_APPEARANCE_KEY = "constellation"
        private const val PAGE_COUNT = 3
        private const val PAGE_SCHEMATIC = 1
        private const val PAGE_PROFILE = 2
        private const val PREFERENCES = "constellation_widget_pages"
        private const val ALARM_REQUEST_CODE = 903
        private const val DEFAULT_WIDGET_WIDTH_DP = 140
        private const val PROFILE_HORIZONTAL_PADDING_DP = 14
        private const val PROFILE_IMAGE_SIZE_DP = 44
        private const val MIN_TITLE_WIDTH_DP = 24
        private const val MAX_TITLE_SIZE_SP = 13f
        private const val MIN_TITLE_SIZE_SP = 8f
        private const val TITLE_LETTER_SPACING = 0.05f
    }
}
