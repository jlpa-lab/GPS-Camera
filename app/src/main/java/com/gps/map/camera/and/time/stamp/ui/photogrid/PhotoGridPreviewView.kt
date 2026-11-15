package com.gps.map.camera.and.time.stamp.ui.photogrid

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.gps.map.camera.and.time.stamp.R

class PhotoGridPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val grid = GridLayout(context)

    init {
        clipToOutline = true
        outlineProvider = ViewOutlineProviderCompat()
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun show(uris: List<Uri>, layout: PhotoGridLayout, spacing: Int, borderColor: Int) {
        // The border choice belongs to each photo. The gutter and outer frame stay dark.
        setBackgroundColor(context.getColor(R.color.home_surface))
        grid.removeAllViews()
        grid.columnCount = when (layout) {
            PhotoGridLayout.VERTICAL -> 1
            PhotoGridLayout.HORIZONTAL -> uris.size
            PhotoGridLayout.TWO_BY_TWO -> 2
        }.coerceAtLeast(1)
        grid.rowCount = when (layout) {
            PhotoGridLayout.VERTICAL -> uris.size
            PhotoGridLayout.HORIZONTAL -> 1
            PhotoGridLayout.TWO_BY_TWO -> if (uris.size <= 2) 1 else 2
        }.coerceAtLeast(1)
        uris.forEachIndexed { index, uri ->
            val cell = FrameLayout(context).apply {
                clipChildren = false
            }
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = ViewOutlineProviderCompat()
            }
            cell.addView(image, FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            ))
            cell.addView(View(context).apply {
                background = GradientDrawable().apply {
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke(
                        (2f * resources.displayMetrics.density).toInt(),
                        borderColor
                    )
                    cornerRadius = 24f * resources.displayMetrics.density
                }
            }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            val params = cellParams(index, uris.size, layout, spacing)
            grid.addView(cell, params)
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(image)
        }
    }

    private fun cellParams(
        index: Int,
        count: Int,
        layout: PhotoGridLayout,
        spacing: Int
    ): GridLayout.LayoutParams {
        val gap = (spacing * resources.displayMetrics.density).toInt()
        return when {
            count == 3 && layout == PhotoGridLayout.TWO_BY_TWO -> {
                val left = index == 0
                GridLayout.LayoutParams(
                    GridLayout.spec(if (left) 0 else index - 1, if (left) 2 else 1, 1f),
                    GridLayout.spec(if (left) 0 else 1, 1, 1f)
                ).apply {
                    width = 0
                    height = 0
                    setMargins(if (left) 0 else gap, if (!left && index == 2) gap else 0, 0, 0)
                }
            }
            layout == PhotoGridLayout.VERTICAL -> GridLayout.LayoutParams(
                GridLayout.spec(index, 1, 1f), GridLayout.spec(0, 1, 1f)
            ).apply {
                width = 0
                height = 0
                setMargins(0, if (index == 0) 0 else gap, 0, 0)
            }
            layout == PhotoGridLayout.HORIZONTAL -> GridLayout.LayoutParams(
                GridLayout.spec(0, 1, 1f), GridLayout.spec(index, 1, 1f)
            ).apply {
                width = 0
                height = 0
                setMargins(if (index == 0) 0 else gap, 0, 0, 0)
            }
            else -> {
                val row = index / 2
                val column = index % 2
                GridLayout.LayoutParams(
                    GridLayout.spec(row, 1, 1f), GridLayout.spec(column, 1, 1f)
                ).apply {
                    width = 0
                    height = 0
                    setMargins(if (column == 0) 0 else gap, if (row == 0) 0 else gap, 0, 0)
                }
            }
        }
    }

    private class ViewOutlineProviderCompat : android.view.ViewOutlineProvider() {
        override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
            outline.setRoundRect(
                0,
                0,
                view.width,
                view.height,
                24f * view.resources.displayMetrics.density
            )
        }
    }
}
