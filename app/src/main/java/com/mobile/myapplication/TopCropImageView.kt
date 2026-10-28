package com.mobile.myapplication

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import kotlin.math.max

/** Fills the hero width while preserving the artwork's important top edge. */
class TopCropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    private val topCropMatrix = Matrix()

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val changed = super.setFrame(left, top, right, bottom)
        updateTopCropMatrix(drawable)
        return changed
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        updateTopCropMatrix(drawable)
    }

    private fun updateTopCropMatrix(image: Drawable?) {
        if (image == null || width == 0 || height == 0) return
        val drawableWidth = image.intrinsicWidth.toFloat()
        val drawableHeight = image.intrinsicHeight.toFloat()
        if (drawableWidth <= 0f || drawableHeight <= 0f) return

        // Fill without side gaps and let excess height crop from the bottom.
        val scale = max(width / drawableWidth, height / drawableHeight)
        val horizontalOffset = (width - drawableWidth * scale) / 2f
        topCropMatrix.reset()
        topCropMatrix.setScale(scale, scale)
        topCropMatrix.postTranslate(horizontalOffset, 0f)
        imageMatrix = topCropMatrix
    }
}








