package com.gps.map.camera.and.time.stamp.ui.stamptemplate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View

/**
 * Shared on-screen renderer for the selected stamp template.
 *
 * The same renderer is also used when exporting a photo or video, so the
 * preview and the saved media stay visually consistent.
 */
class StampOverlayView(context: Context) : View(context) {
    private var customization = StampCustomizationStore.read(context)
    private var renderData = StampRenderData(
        address = "",
        latitude = Double.NaN,
        longitude = Double.NaN
    )
    private var mapBitmap: Bitmap? = null

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setCustomization(value: StampCustomization) {
        customization = value
        invalidate()
    }

    fun setRenderData(value: StampRenderData) {
        renderData = value
        invalidate()
    }

    fun setMapBitmap(value: Bitmap?) {
        mapBitmap = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        StampRenderer.draw(
            canvas = canvas,
            bounds = RectF(0f, 0f, width.toFloat(), height.toFloat()),
            settings = customization,
            data = renderData,
            map = mapBitmap
        )
    }
}
