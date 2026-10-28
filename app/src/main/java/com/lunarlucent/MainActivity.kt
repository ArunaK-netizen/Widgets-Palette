package com.lunarlucent

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private val colors = listOf(
        0xFF7B7A7D.toInt(),
        0xFF777D82.toInt(),
        0xFF6D7772.toInt(),
        0xFF77718A.toInt(),
        0xFF8A716D.toInt(),
        0xFF5C6478.toInt(),
        0xFFB4B1B6.toInt(),
        0xFF242429.toInt()
    )
    private var widgetKey = KEY_LUNAR
    private var appearanceKey = KEY_LUNAR
    private var selectedWidgetId: Int? = null
    private var settings = WidgetAppearanceSettings()
    private var previewInformation = false
    private var constellationPreviewPage = 0
    private val widgetChips = mutableMapOf<String, TextView>()
    private val modeChips = mutableMapOf<String, TextView>()
    private lateinit var page: LinearLayout
    private lateinit var previewHost: FrameLayout
    private lateinit var instanceSelector: LinearLayout
    private lateinit var selectedWidgetLabel: TextView
    private lateinit var colorValue: TextView
    private lateinit var opacityValue: TextView
    private lateinit var radiusValue: TextView
    private lateinit var insetValue: TextView
    private lateinit var hueSeekBar: SeekBar
    private lateinit var previewHint: TextView
    private lateinit var opacitySeekBar: SeekBar
    private lateinit var radiusSeekBar: SeekBar
    private lateinit var insetSeekBar: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(PAGE_BACKGROUND)
            clipToPadding = false
            isFillViewport = true
        }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(32))
        }
        scroll.addView(page)
        setContentView(scroll)
        buildPage()
        showWidget(widgetKey)
    }

    override fun onResume() {
        super.onResume()
        if (::instanceSelector.isInitialized) {
            refreshInstanceSelector()
        }
    }

    private fun buildPage() {
        page.addView(label("WIDGET PALETTE", 11f, MUTED_TEXT, bold = true, tracking = 0.16f))
        page.addView(
            label("Your sky,\nyour style.", 32f, INK, bold = true).apply {
                setPadding(0, dp(10), 0, dp(6))
            }
        )
        page.addView(label("See your widgets. Make them yours.", 15f, SECONDARY_TEXT))

        page.addView(sectionTitle("YOUR WIDGETS", top = 26))
        val widgetSelector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        page.addView(widgetSelector)
        listOf(
            KEY_LUNAR to "Lunar",
            KEY_MOON_ONLY to "Mini Moon",
            KEY_CONSTELLATION to "Constellation"
        ).forEach { (key, title) ->
            val selector = chip(title) { showWidget(key) }
            widgetChips[key] = selector
            widgetSelector.addView(
                selector,
                LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    if (key != KEY_LUNAR) marginStart = dp(7)
                }
            )
        }

        val instanceScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        instanceSelector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        instanceScroll.addView(instanceSelector)
        page.addView(instanceScroll, marginTop(8))
        page.addView(
            label(
                "Choose a home-screen copy for its own style, or edit the shared default for uncustomized widgets.",
                11f,
                SECONDARY_TEXT
            ).apply {
                setPadding(0, dp(5), 0, 0)
            }
        )

        val previewCard = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(16))
        }
        page.addView(previewCard, marginTop(12))
        previewCard.addView(
            label("LIVE WIDGET PREVIEW", 10f, MUTED_TEXT, bold = true, tracking = 0.13f)
        )
        selectedWidgetLabel = label("", 17f, INK, bold = true).apply {
            setPadding(0, dp(5), 0, dp(12))
        }
        previewCard.addView(selectedWidgetLabel)
        previewHost = FrameLayout(this)
        previewCard.addView(
            previewHost,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220))
        )
        previewHint = label("", 12f, SECONDARY_TEXT).apply {
            setPadding(0, dp(10), 0, 0)
        }
        previewCard.addView(previewHint)
        previewCard.addView(
            chip("Add to home screen") { requestAddWidget() },
            marginTop(12)
        )

        page.addView(sectionTitle("MAKE IT YOURS", top = 25))
        page.addView(label("Background style", 14f, INK, bold = true))
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        page.addView(modeRow, marginTop(8))
        listOf(
            WidgetAppearanceSettings.MODE_LUCENT to "Lucent",
            WidgetAppearanceSettings.MODE_GLASS to "Liquid glass",
            WidgetAppearanceSettings.MODE_SOLID to "Solid"
        ).forEach { (mode, title) ->
            val selector = chip(title) {
                    settings = settings.copy(
                        mode = mode,
                        opacity = if (mode == WidgetAppearanceSettings.MODE_SOLID) 100 else settings.opacity
                    )
                    persistAndRender()
                }
            modeChips[mode] = selector
            modeRow.addView(
                selector,
                LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (mode != WidgetAppearanceSettings.MODE_LUCENT) marginStart = dp(7)
                }
            )
        }

        page.addView(sectionTitle("BACKGROUND COLOUR", top = 19))
        colorValue = label("", 13f, SECONDARY_TEXT)
        page.addView(colorValue, marginTop(4))
        val palette = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        page.addView(palette, marginTop(10))
        colors.forEach { color ->
            val swatch = View(this).apply {
                background = rounded(color, dp(20).toFloat())
                contentDescription = "Background colour ${color.toHex()}"
                setOnClickListener {
                    settings = settings.copy(color = color)
                    persistAndRender()
                }
            }
            palette.addView(
                swatch,
                LinearLayout.LayoutParams(dp(31), dp(31)).apply {
                    marginEnd = dp(8)
                }
            )
        }
        page.addView(label("Fine tune hue", 12f, SECONDARY_TEXT).apply {
            setPadding(0, dp(12), 0, dp(3))
        })
        hueSeekBar = SeekBar(this).apply {
            max = 360
            progress = colorHue(settings.color)
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF777579.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFF454449.toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        settings = settings.copy(color = Color.HSVToColor(floatArrayOf(progress.toFloat(), 0.20f, 0.49f)))
                        updatePreview()
                        colorValue.text = "Custom · ${settings.color.toHex()}"
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = persistAndRender()
            })
        }
        page.addView(hueSeekBar)

        opacityValue = label("", 13f, SECONDARY_TEXT)
        page.addView(opacityValue, marginTop(15))
        opacitySeekBar = slider(10, 100, settings.opacity) { value ->
                settings = settings.copy(opacity = value)
                opacityValue.text = "Opacity · $value%"
                updatePreview()
            }
        page.addView(opacitySeekBar, marginTop(2))
        radiusValue = label("", 13f, SECONDARY_TEXT)
        page.addView(radiusValue, marginTop(12))
        radiusSeekBar = slider(8, 48, settings.cornerRadius) { value ->
                settings = settings.copy(cornerRadius = value)
                radiusValue.text = "Corner roundness · ${value}dp"
                updatePreview()
            }
        page.addView(radiusSeekBar, marginTop(2))
        insetValue = label("", 13f, SECONDARY_TEXT)
        page.addView(insetValue, marginTop(12))
        insetSeekBar = slider(0, 20, settings.inset) { value ->
                settings = settings.copy(inset = value)
                insetValue.text = "Image breathing room · ${value}dp"
                updatePreview()
            }
        page.addView(insetSeekBar, marginTop(2))
        page.addView(
            chip("Save & apply to home screen") { persistAndRender() },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                topMargin = dp(18)
            }
        )

        page.addView(
            label(
                "Liquid glass adds a soft highlight to the tint. Android home screens do not expose wallpaper blur to widgets, so the preview uses a lightweight glass-like sheen.",
                12f,
                SECONDARY_TEXT
            ).apply {
                setPadding(0, dp(16), 0, 0)
            }
        )

        val credits = chip("Photo credits & licenses") {
            startActivity(Intent(this, ConstellationCreditsActivity::class.java))
        }
        page.addView(credits, marginTop(24))
        page.addView(
            label("Changes are saved separately for each widget design.", 11f, MUTED_TEXT).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, 0)
            }
        )
    }

    private fun showWidget(key: String) {
        widgetKey = key
        appearanceKey = key
        selectedWidgetId = null
        settings = WidgetAppearance.load(this, appearanceKey)
        previewInformation = false
        widgetChips.forEach { (itemKey, view) -> setChipSelected(view, itemKey == key) }
        modeChips.forEach { (itemKey, view) -> setChipSelected(view, itemKey == settings.mode) }
        refreshInstanceSelector()
        selectedWidgetLabel.text = widgetTitle(key)
        previewHint.text = previewHintFor(key)
        showAppearanceValues()
        updatePreview()
    }

    private fun updatePreview() {
        if (!::previewHost.isInitialized) return
        previewHost.removeAllViews()
        val isCompact = widgetKey == KEY_MOON_ONLY
        val background = GradientDrawable().apply {
            cornerRadius = dp(settings.cornerRadius).toFloat()
            val color = settings.color
            setColor(Color.argb(settings.opacity * 255 / 100, Color.red(color), Color.green(color), Color.blue(color)))
            if (settings.mode == WidgetAppearanceSettings.MODE_GLASS) {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(
                    Color.argb(minOf(255, settings.opacity * 255 / 100 + 30), 255, 255, 255),
                    Color.argb(settings.opacity * 255 / 100, Color.red(color), Color.green(color), Color.blue(color))
                )
            }
        }
        val widget = FrameLayout(this).apply {
            this.background = background
            clipToOutline = true
            setPadding(dp(settings.inset), dp(settings.inset), dp(settings.inset), dp(settings.inset))
            setOnClickListener {
                if (widgetKey == KEY_LUNAR) {
                    previewInformation = !previewInformation
                    updatePreview()
                } else if (widgetKey == KEY_CONSTELLATION) {
                    showNextConstellationPreview()
                }
            }
        }
        previewHost.addView(
            widget,
            FrameLayout.LayoutParams(
                if (isCompact) dp(148) else ViewGroup.LayoutParams.MATCH_PARENT,
                if (isCompact) dp(148) else ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        when (widgetKey) {
            KEY_CONSTELLATION -> addConstellationPreview(widget)
            KEY_LUNAR -> if (previewInformation) addMoonInformationPreview(widget) else addMoonImagePreview(widget, compact = false)
            else -> addMoonImagePreview(widget, compact = true)
        }
    }

    private fun addMoonImagePreview(widget: FrameLayout, compact: Boolean) {
        val phase = LunarPhaseCalculator.calculate()
        val asset = MoonImageSelector.closest(phase.phaseAngleDegrees)?.assetName
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(asset?.let { name ->
                assets.open("moon/$name").use(BitmapFactory::decodeStream)
            })
            contentDescription = "Current Moon phase"
        }
        val width = if (compact) dp(125) else ViewGroup.LayoutParams.MATCH_PARENT
        widget.addView(image, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        if (!compact) image.setPadding(dp(5), dp(5), dp(5), dp(5))
    }

    private fun addMoonInformationPreview(widget: FrameLayout) {
        val phase = LunarPhaseCalculator.calculate()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        content.addView(label("TODAY’S MOON", 9f, Color.LTGRAY, bold = true))
        content.addView(label(phase.phase, 15f, Color.WHITE, bold = true).apply {
            setPadding(0, dp(4), 0, dp(4))
        })
        content.addView(label("${phase.illuminationPercent}%", 35f, Color.WHITE))
        content.addView(label("ILLUMINATED  ·  AGE ${"%.1f".format(Locale.getDefault(), phase.ageDays)} DAYS", 9f, Color.LTGRAY))
        widget.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun addConstellationPreview(widget: FrameLayout) {
        val constellations = ConstellationCatalog.all(this)
        val constellation = constellations.first()
        if (constellationPreviewPage == 2) {
            val profile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val identity = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            identity.addView(label(constellation.name, 16f, Color.WHITE, bold = true))
            identity.addView(label(constellation.region.uppercase(Locale.ROOT), 9f, Color.LTGRAY, tracking = 0.12f))
            header.addView(identity)
            val thumbnail = ImageView(this).apply {
                setImageBitmap(
                    ConstellationWidgetGraphics.createCircularPhoto(
                        this@MainActivity,
                        constellation.image
                    )
                )
            }
            header.addView(thumbnail, LinearLayout.LayoutParams(dp(44), dp(44)))
            profile.addView(header)
            profile.addView(label(constellation.description.substringBefore(". ") + ".", 12f, Color.WHITE).apply {
                setPadding(0, dp(13), 0, 0)
            })
            widget.addView(profile, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            val artwork = ImageView(this).apply {
                scaleType = if (constellationPreviewPage == 1) ImageView.ScaleType.FIT_CENTER
                else ImageView.ScaleType.CENTER_CROP
                setImageBitmap(
                    if (constellationPreviewPage == 1) {
                        ConstellationWidgetGraphics.createSchematic(this@MainActivity, constellation)
                    } else {
                        assets.open("constellations/images/${constellation.image}")
                            .use(BitmapFactory::decodeStream)
                    }
                )
                contentDescription = "${constellation.name} widget preview"
            }
            widget.addView(artwork, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun showNextConstellationPreview() {
        constellationPreviewPage = (constellationPreviewPage + 1) % 3
        updatePreview()
    }

    private fun persistAndRender() {
        WidgetAppearance.save(this, appearanceKey, settings)
        showAppearanceValues()
        updatePreview()
        listOf(
            ".LunarWidgetProvider",
            ".MoonOnlyWidgetProvider",
            ".ConstellationWidgetProvider"
        ).forEach { receiver ->
            sendBroadcast(
                Intent("com.lunarlucent.APPEARANCE_CHANGED")
                    .setClassName(packageName, "com.lunarlucent${receiver}")
            )
        }
    }

    private fun previewHintFor(key: String) = when (key) {
        KEY_LUNAR -> "Tap the widget preview to view its lunar details."
        KEY_CONSTELLATION -> "Tap the preview to cycle through photo, star map, and profile."
        else -> "A live preview of your compact Moon widget."
    }

    private fun refreshInstanceSelector() {
        instanceSelector.removeAllViews()
        val all = chip("Shared default") {
            selectedWidgetId = null
            appearanceKey = widgetKey
            settings = WidgetAppearance.load(this, appearanceKey)
            refreshInstanceSelector()
            showAppearanceValues()
            updatePreview()
        }
        setChipSelected(all, selectedWidgetId == null)
        instanceSelector.addView(
            all,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply {
                marginEnd = dp(7)
            }
        )
        widgetIds(widgetKey).forEachIndexed { index, id ->
            val instance = chip("Widget ${index + 1}") {
                selectedWidgetId = id
                appearanceKey = "$widgetKey.instance.$id"
                settings = WidgetAppearance.load(this, appearanceKey)
                refreshInstanceSelector()
                showAppearanceValues()
                updatePreview()
            }
            setChipSelected(instance, selectedWidgetId == id)
            instanceSelector.addView(
                instance,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply {
                    marginEnd = dp(7)
                }
            )
        }
        selectedWidgetId?.let { id ->
            instanceSelector.addView(
                chip("Use shared style") {
                    WidgetAppearance.clearInstance(this, widgetKey, id)
                    selectedWidgetId = null
                    appearanceKey = widgetKey
                    settings = WidgetAppearance.load(this, appearanceKey)
                    refreshInstanceSelector()
                    showAppearanceValues()
                    persistAndRender()
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36))
            )
        }
    }

    private fun showAppearanceValues() {
        colorValue.text = "Selected · ${settings.color.toHex()}"
        opacityValue.text = "Opacity · ${settings.opacity}%"
        radiusValue.text = "Corner roundness · ${settings.cornerRadius}dp"
        insetValue.text = "Image breathing room · ${settings.inset}dp"
        hueSeekBar.progress = colorHue(settings.color)
        opacitySeekBar.progress = settings.opacity - 10
        radiusSeekBar.progress = settings.cornerRadius - 8
        insetSeekBar.progress = settings.inset
        modeChips.forEach { (itemKey, view) -> setChipSelected(view, itemKey == settings.mode) }
    }

    private fun widgetIds(key: String): IntArray {
        val provider = when (key) {
            KEY_MOON_ONLY -> MoonOnlyWidgetProvider::class.java
            KEY_CONSTELLATION -> ConstellationWidgetProvider::class.java
            else -> LunarWidgetProvider::class.java
        }
        return AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, provider))
    }

    private fun requestAddWidget() {
        val provider = when (widgetKey) {
            KEY_MOON_ONLY -> MoonOnlyWidgetProvider::class.java
            KEY_CONSTELLATION -> ConstellationWidgetProvider::class.java
            else -> LunarWidgetProvider::class.java
        }
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, provider), null, null)
        } else {
            previewHint.text = "Add this widget from your launcher’s widget picker."
        }
    }

    private fun slider(
        minimum: Int,
        maximum: Int,
        value: Int,
        onChange: (Int) -> Unit
    ) = SeekBar(this).apply {
        this.max = maximum - minimum
        progress = (value - minimum).coerceIn(0, max)
        progressTintList = android.content.res.ColorStateList.valueOf(0xFF777579.toInt())
        thumbTintList = android.content.res.ColorStateList.valueOf(0xFF454449.toInt())
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress + minimum)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = persistAndRender()
        })
    }

    private fun chip(title: String, onClick: () -> Unit) =
        TextView(this).apply {
            text = title
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(INK)
            typeface = geist(Typeface.BOLD)
            background = rounded(0xFFE7E6E8.toInt(), dp(20).toFloat())
            setOnClickListener { onClick() }
        }

    private fun setChipSelected(view: TextView, selected: Boolean) {
        view.background = rounded(
            if (selected) 0xFFD8D6D9.toInt() else 0xFFE7E6E8.toInt(),
            dp(20).toFloat()
        )
    }

    private fun card() = LinearLayout(this).apply {
        background = rounded(Color.WHITE, dp(25).toFloat())
        elevation = dp(2).toFloat()
    }

    private fun sectionTitle(title: String, top: Int) =
        label(title, 10f, MUTED_TEXT, bold = true, tracking = 0.14f).apply {
            setPadding(0, dp(top), 0, dp(9))
        }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        tracking: Float = 0f
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = geist(if (bold) Typeface.BOLD else Typeface.NORMAL)
        letterSpacing = tracking
    }

    private fun geist(style: Int) =
        Typeface.create(resources.getFont(R.font.geist_variable), style)

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    private fun marginTop(value: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(value)
    }

    private fun colorHue(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0].toInt().coerceIn(0, 360)
    }

    private fun Int.toHex() = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and this)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun widgetTitle(key: String) = when (key) {
        KEY_MOON_ONLY -> "Mini Moon · 1 × 1"
        KEY_CONSTELLATION -> "Constellation of the Day · 2 × 2"
        else -> "Lunar Lucent · 2 × 2"
    }

    companion object {
        private const val KEY_LUNAR = "lunar"
        private const val KEY_MOON_ONLY = "moon_only"
        private const val KEY_CONSTELLATION = "constellation"
        private const val PAGE_BACKGROUND = 0xFFF2F1F3.toInt()
        private const val INK = 0xFF29282B.toInt()
        private const val SECONDARY_TEXT = 0xFF66646A.toInt()
        private const val MUTED_TEXT = 0xFF88868C.toInt()
    }
}
