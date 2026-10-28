package com.lunarlucent

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.util.Linkify
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ConstellationCreditsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(Color.rgb(244, 243, 245))
        }
        content.addView(text("PHOTO CREDITS", 12f, 0xFF777579.toInt(), true))
        content.addView(text("88 constellations", 28f, 0xFF29282B.toInt(), true).apply {
            setPadding(0, dp(6), 0, dp(8))
        })
        content.addView(
            text(
                "Each constellation photograph is a COSMO360-hosted 960 px WebP derivative. " +
                    "The profile shows its photographer credit; the full source and CC BY 4.0 license links are below.",
                14f,
                0xFF55545A.toInt(),
                false
            ).apply {
                setPadding(0, 0, 0, dp(20))
            }
        )

        ConstellationCatalog.all(this).forEach { constellation ->
            content.addView(text(constellation.name, 17f, 0xFF29282B.toInt(), true))
            val attribution = buildString {
                append(constellation.credit)
                append("\nSource: ")
                append(constellation.sourcePage)
                if (constellation.originalPhotoSource.isNotBlank()) {
                    append("\nOriginal photo: ")
                    append(constellation.originalPhotoSource)
                }
                append("\nLicense: Creative Commons Attribution 4.0 International\n")
                append(constellation.licenseUrl)
                append("\nImage: ")
                append(constellation.image)
            }
            val details = text(attribution, 12f, 0xFF55545A.toInt(), false).apply {
                Linkify.addLinks(this, Linkify.WEB_URLS)
                movementMethod = LinkMovementMethod.getInstance()
                setLinkTextColor(0xFF58565D.toInt())
                setPadding(0, dp(4), 0, dp(18))
            }
            content.addView(details)
        }

        setContentView(ScrollView(this).apply {
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        })
    }

    private fun text(value: String, sizeSp: Float, color: Int, medium: Boolean) =
        TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            typeface = Typeface.create(
                resources.getFont(R.font.geist_variable),
                if (medium) Typeface.BOLD else Typeface.NORMAL
            )
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
