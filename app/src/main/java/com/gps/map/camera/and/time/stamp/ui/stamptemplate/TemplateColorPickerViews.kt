package com.gps.map.camera.and.time.stamp.ui.stamptemplate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class TemplateColorField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hsv = floatArrayOf(260f, 0.65f, 0.55f)
    var onColorChanged: ((Int) -> Unit)? = null

    fun setColor(color: Int, notify: Boolean = false) {
        Color.colorToHSV(color, hsv)
        invalidate()
        if (notify) onColorChanged?.invoke(Color.HSVToColor(hsv))
    }

    fun setHue(value: Float, notify: Boolean = false) {
        hsv[0] = value.coerceIn(0f, 360f)
        invalidate()
        if (notify) onColorChanged?.invoke(Color.HSVToColor(hsv))
    }

    fun color(): Int = Color.HSVToColor(hsv)
    fun hue(): Float = hsv[0]

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val hueColor = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, hueColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0x00FFFFFF, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        val x = hsv[1] * width
        val y = (1f - hsv[2]) * height
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        canvas.drawCircle(x, y, 9f, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE) return true
        hsv[1] = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        hsv[2] = (1f - event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
        invalidate()
        onColorChanged?.invoke(Color.HSVToColor(hsv))
        return true
    }
}

class TemplateHueField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var hue = 260f
    var onHueChanged: ((Float) -> Unit)? = null

    fun setHue(value: Float, notify: Boolean = false) {
        hue = value.coerceIn(0f, 360f)
        invalidate()
        if (notify) onHueChanged?.invoke(hue)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val colors = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
            Color.BLUE, Color.MAGENTA, Color.RED
        )
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height / 2f, height / 2f, paint)
        paint.shader = null
        val x = hue / 360f * width
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        canvas.drawCircle(x, height / 2f, height / 2f + 2f, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE) return true
        hue = (event.x / width.coerceAtLeast(1) * 360f).coerceIn(0f, 360f)
        invalidate()
        onHueChanged?.invoke(hue)
        return true
    }
}
