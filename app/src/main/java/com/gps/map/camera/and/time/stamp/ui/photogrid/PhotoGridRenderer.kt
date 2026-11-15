package com.gps.map.camera.and.time.stamp.ui.photogrid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.roundToInt

object PhotoGridRenderer {
    fun render(
        context: Context,
        uris: List<Uri>,
        layout: PhotoGridLayout,
        spacing: Int,
        borderColor: Int
    ): Bitmap {
        return try {
            renderAtSize(context, uris, layout, spacing, borderColor, MAX_SIZE)
        } catch (_: OutOfMemoryError) {
            System.gc()
            renderAtSize(context, uris, layout, spacing, borderColor, RETRY_SIZE)
        }
    }

    private fun renderAtSize(
        context: Context,
        uris: List<Uri>,
        layout: PhotoGridLayout,
        spacing: Int,
        borderColor: Int,
        decodeSize: Int
    ): Bitmap {
        val photos = mutableListOf<Bitmap>()
        var output: Bitmap? = null
        try {
            uris.forEach { photos += decodePhoto(context, it, decodeSize) }
            val rendered = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
            output = rendered
            val canvas = Canvas(rendered)
            canvas.drawColor(PREVIEW_BACKGROUND_COLOR)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
                style = Paint.Style.STROKE
                strokeWidth = OUTPUT_BORDER_WIDTH
            }
            val cells = cellsFor(photos.size, layout, spacing)
            cells.forEachIndexed { index, rect ->
                drawCenterCrop(canvas, photos[index], rect, paint, borderPaint)
            }
            return rendered
        } catch (error: Throwable) {
            output?.takeUnless { it.isRecycled }?.recycle()
            throw error
        } finally {
            photos.forEach { photo ->
                if (!photo.isRecycled) photo.recycle()
            }
        }
    }

    private fun decodePhoto(context: Context, uri: Uri, maxSize: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodeWithFallback(context, uri, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Could not read selected photo dimensions: $uri"
        }

        val sample = calculateSample(bounds.outWidth, bounds.outHeight, maxSize)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = decodeWithFallback(context, uri, options)
            ?: error("Could not decode selected photo: $uri")
        return applyOrientation(context, uri, decoded)
    }

    /**
     * A few Android media providers return a null InputStream for a perfectly
     * valid MediaStore item. The file descriptor path is the provider-safe
     * fallback and is also what the gallery thumbnail loader uses internally.
     */
    private fun decodeWithFallback(
        context: Context,
        uri: Uri,
        options: BitmapFactory.Options
    ): Bitmap? {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)?.let { return it }
        }
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)?.let { return it }
        }
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)?.let { return it }
        }
        return null
    }

    private fun applyOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                ExifInterface(it.fileDescriptor).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (upright !== bitmap) bitmap.recycle()
        return upright
    }

    private fun calculateSample(width: Int, height: Int, maxSize: Int): Int {
        var sample = 1
        while (max(width / sample, height / sample) > maxSize) sample *= 2
        return sample
    }

    private fun cellsFor(count: Int, layout: PhotoGridLayout, spacing: Int): List<Rect> {
        val size = OUTPUT_SIZE
        val gap = (spacing.coerceIn(0, 24) * OUTPUT_SIZE / REFERENCE_PREVIEW_DP).roundToInt()
        return when {
            count == 3 && layout == PhotoGridLayout.TWO_BY_TWO -> {
                val left = (size - gap) * 0.58f
                val right = size - left - gap
                listOf(
                    Rect(0, 0, left.roundToInt(), size),
                    Rect(left.roundToInt() + gap, 0, size, (size - gap) / 2),
                    Rect(left.roundToInt() + gap, (size + gap) / 2, size, size)
                )
            }
            layout == PhotoGridLayout.VERTICAL -> {
                val cellHeight = (size - gap * (count - 1)) / count
                List(count) { index ->
                    val top = index * (cellHeight + gap)
                    Rect(0, top, size, top + cellHeight)
                }
            }
            layout == PhotoGridLayout.HORIZONTAL -> {
                val cellWidth = (size - gap * (count - 1)) / count
                List(count) { index ->
                    val left = index * (cellWidth + gap)
                    Rect(left, 0, left + cellWidth, size)
                }
            }
            else -> {
                val columns = 2
                val rows = if (count <= 2) 1 else 2
                val cellWidth = (size - gap * (columns - 1)) / columns
                val cellHeight = (size - gap * (rows - 1)) / rows
                List(count) { index ->
                    val row = index / columns
                    val column = index % columns
                    val left = column * (cellWidth + gap)
                    val top = row * (cellHeight + gap)
                    Rect(left, top, left + cellWidth, top + cellHeight)
                }
            }
        }
    }

    private fun drawCenterCrop(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: Rect,
        paint: Paint,
        borderPaint: Paint
    ) {
        val sourceRatio = bitmap.width.toFloat() / bitmap.height
        val targetRatio = destination.width().toFloat() / destination.height()
        val source = if (sourceRatio > targetRatio) {
            val width = (bitmap.height * targetRatio).roundToInt()
            Rect((bitmap.width - width) / 2, 0, (bitmap.width + width) / 2, bitmap.height)
        } else {
            val height = (bitmap.width / targetRatio).roundToInt()
            Rect(0, (bitmap.height - height) / 2, bitmap.width, (bitmap.height + height) / 2)
        }
        val clip = Path().apply {
            addRoundRect(RectF(destination), OUTPUT_CORNER_RADIUS, OUTPUT_CORNER_RADIUS, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(bitmap, source, destination, paint)
        canvas.restore()
        canvas.drawRoundRect(RectF(destination), OUTPUT_CORNER_RADIUS, OUTPUT_CORNER_RADIUS, borderPaint)
    }

    private const val OUTPUT_SIZE = 1600
    private const val OUTPUT_CORNER_RADIUS = 120f
    private const val OUTPUT_BORDER_WIDTH = 6f
    private const val REFERENCE_PREVIEW_DP = 320f
    private const val PREVIEW_BACKGROUND_COLOR = 0xFF142338.toInt()
    private const val MAX_SIZE = 1200
    private const val RETRY_SIZE = 800
}
