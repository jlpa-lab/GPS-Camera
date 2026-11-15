package com.gps.map.camera.and.time.stamp.ui.gpsvideo

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView
import kotlin.math.min

/** Keeps portrait recordings centered in the result frame instead of pinning them to the left. */
class CenteredVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VideoView(context, attrs, defStyleAttr) {

    private var sourceWidth = 0
    private var sourceHeight = 0

    fun setVideoSize(width: Int, height: Int) {
        sourceWidth = width
        sourceHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        val videoWidth = sourceWidth
        val videoHeight = sourceHeight

        if (availableWidth > 0 && availableHeight > 0 && videoWidth > 0 && videoHeight > 0) {
            val scale = min(
                availableWidth.toFloat() / videoWidth,
                availableHeight.toFloat() / videoHeight
            )
            setMeasuredDimension(
                (videoWidth * scale).toInt(),
                (videoHeight * scale).toInt()
            )
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
