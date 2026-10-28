package com.lunarlucent

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.RemoteViews
import java.io.IOException

internal object MoonWidgetImage {
    fun update(
        context: Context,
        views: RemoteViews,
        imageViewId: Int,
        phaseAngleDegrees: Double
    ) = update(context, views, phaseAngleDegrees, imageViewId)

    fun update(
        context: Context,
        views: RemoteViews,
        phaseAngleDegrees: Double,
        vararg imageViewIds: Int
    ) {
        val availableAssets = try {
            context.assets.list("moon")
                .orEmpty()
                .filter { filename ->
                    filename.matches(Regex("""\d{3}\.png""")) &&
                        filename.substringBefore('.').toIntOrNull()?.let { it in 0 until 360 } == true
                }
                .toSet()
        } catch (exception: IOException) {
            Log.w(TAG, "Unable to list bundled Moon images", exception)
            return
        }

        val image = MoonImageSelector.closest(phaseAngleDegrees, availableAssets)
        if (image == null) {
            Log.w(TAG, "No bundled Moon images found in assets/moon")
            return
        }

        try {
            val bitmap = context.assets.open("moon/${image.assetName}").use(BitmapFactory::decodeStream)
            if (bitmap == null) {
                Log.w(TAG, "Unable to decode bundled Moon image: ${image.assetName}")
            } else {
                imageViewIds.forEach { imageViewId ->
                    views.setImageViewBitmap(imageViewId, bitmap)
                }
            }
        } catch (exception: IOException) {
            Log.w(TAG, "Unable to open bundled Moon image: ${image.assetName}", exception)
        }
    }

    private const val TAG = "MoonWidgetImage"
}
