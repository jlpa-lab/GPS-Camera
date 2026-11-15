package com.gps.map.camera.and.time.stamp.ui.stamptemplate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StampRenderData(
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val capturedAt: Long = System.currentTimeMillis(),
    val capturedAtText: String? = null,
    val weather: String? = null
)

/** Draws each template as dynamic vector/text content over the original photo. */
object StampRenderer {
    private const val BLUE = 0xFF0D6BFF.toInt()
    private const val PANEL = 0xB317283C.toInt()
    private const val MAP_BG = 0xFFF1F5EF.toInt()
    private const val MAP_ROAD = 0xFFD7E0D8.toInt()
    private const val MAP_WATER = 0xFF9FD2E0.toInt()
    private const val RED = 0xFFE85B5B.toInt()
    private const val YELLOW = 0xFFFFD35A.toInt()

    fun render(photo: Bitmap, settings: StampCustomization, data: StampRenderData, map: Bitmap? = null): Bitmap {
        val result = photo.copy(Bitmap.Config.ARGB_8888, true)
        draw(Canvas(result), RectF(0f, 0f, photo.width.toFloat(), photo.height.toFloat()), settings, data, map)
        return result
    }

    fun draw(canvas: Canvas, bounds: RectF, settings: StampCustomization, data: StampRenderData, map: Bitmap? = null) {
        val scale = (bounds.width() / 384f).coerceAtLeast(0.75f)
        val address = displayAddress(settings, data.address)
        val addressLines = splitAddress(address)
        val coordinates = if (settings.showCoordinates) formatCoordinates(data.latitude, data.longitude) else null
        val date = if (settings.showDateTime) data.capturedAtText?.takeIf { it.isNotBlank() }
            ?: formatDate(data.capturedAt, settings.dateFormat) else null
        val weather = data.weather?.takeIf { it.isNotBlank() }
        val panel = panelBounds(bounds, settings.position)

        canvas.drawRoundRect(panel, 12f * scale, 12f * scale, paint(settings.backgroundColor))
        templateBorder(canvas, panel, scale, settings.templateKey)
        when (settings.templateKey) {
            "location_spotlight" -> drawLocationSpotlight(canvas, panel, scale, settings, addressLines, coordinates, date, weather, map)
            "time_first" -> drawTimeFirst(canvas, panel, scale, settings, addressLines, coordinates, date, weather, map)
            "centered_destination" -> drawCenteredDestination(canvas, panel, scale, settings, addressLines, coordinates, date, weather, map)
            "side_map" -> drawSideMap(canvas, panel, scale, settings, addressLines, coordinates, date, weather, map)
            "travel_footer" -> drawTravelFooter(canvas, panel, scale, settings, addressLines, coordinates, date, weather, map)
            "ocean_postcard" -> drawOceanPostcard(canvas, panel, scale, settings, addressLines, coordinates, date)
            "coral_travel_ticket" -> drawCoralTicket(canvas, panel, scale, settings, addressLines, coordinates, date, map)
            "mint_location_label" -> drawMintLabel(canvas, panel, scale, settings, addressLines, coordinates, date, map)
            "lavender_photo_journal" -> drawLavenderJournal(canvas, panel, scale, settings, addressLines, coordinates, date)
            "amber_field_note" -> drawAmberNote(canvas, panel, scale, settings, addressLines, coordinates, date, map)
            "teal_destination_tag" -> drawTealTag(canvas, panel, scale, settings, addressLines, coordinates, date, map)
            else -> when (layoutFor(settings.templateKey)) {
            1 -> drawLayoutOne(canvas, panel, scale, settings, addressLines, coordinates, date, weather)
            2 -> drawLayoutTwo(canvas, panel, scale, settings, addressLines, coordinates, date, map)
            3 -> drawLayoutThree(canvas, panel, scale, settings, addressLines, coordinates, date, map)
            4 -> drawLayoutFour(canvas, panel, scale, settings, addressLines, coordinates, date, weather, map)
            5 -> drawLayoutFive(canvas, panel, scale, settings, addressLines, coordinates, date, weather)
            6 -> drawLayoutSix(canvas, panel, scale, settings, addressLines, coordinates, date, weather, map)
            7 -> drawLayoutSeven(canvas, panel, scale, settings, addressLines, coordinates, date, map)
            else -> drawLayoutEight(canvas, panel, scale, settings, addressLines, coordinates, date, weather, map)
            }
        }
    }

    private fun layoutFor(key: String): Int = when (key) {
        "stamp_1", "location_spotlight", "ocean_postcard" -> 1
        "stamp_2", "side_map", "mint_location_label" -> 2
        "stamp_3", "centered_destination", "lavender_photo_journal" -> 3
        "stamp_4", "travel_footer", "coral_travel_ticket" -> 4
        "stamp_5", "time_first", "amber_field_note" -> 5
        "stamp_6", "teal_destination_tag" -> 6
        "stamp_7" -> 7
        else -> 8
    }

    private fun panelBounds(bounds: RectF, position: TemplatePosition): RectF {
        val margin = bounds.width() * 0.035f
        val top = if (position == TemplatePosition.TOP) bounds.top + bounds.height() * 0.18f else bounds.top + bounds.height() * 0.28f
        val bottom = if (position == TemplatePosition.TOP) bounds.top + bounds.height() * 0.72f else bounds.top + bounds.height() * 0.84f
        return RectF(bounds.left + margin, top, bounds.right - margin, bottom)
    }

    private fun drawLocationSpotlight(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, w: String?, map: Bitmap?) {
        val pad = p.width() * .035f
        if (set.showMapIcon) drawMap(c, RectF(p.left + pad, p.top + p.height() * .10f, p.left + p.width() * .22f, p.top + p.height() * .55f), map, s, false)
        drawAddressColor(c, a, p.left + p.width() * .24f, p.top + p.height() * .23f, p.width() * .44f, s, 0xFFFFD34F.toInt())
        val label = textPaint(Color.WHITE, 11f * s, false)
        if (co != null) {
            val lines = co.split(" · ")
            c.drawText("Latitude", p.left + p.width() * .70f, p.top + p.height() * .20f, label)
            c.drawText(lines.getOrNull(0) ?: co, p.left + p.width() * .70f, p.top + p.height() * .34f, textPaint(Color.WHITE, 12f * s, true))
            c.drawText("Longitude", p.left + p.width() * .70f, p.top + p.height() * .49f, label)
            c.drawText(lines.getOrNull(1) ?: "", p.left + p.width() * .70f, p.top + p.height() * .63f, textPaint(Color.WHITE, 12f * s, true))
        }
        if (set.showWeather && w != null) drawWeather(c, RectF(p.left + pad, p.bottom - p.height() * .38f, p.left + p.width() * .23f, p.bottom - pad), w, s)
        if (d != null) drawDateLines(c, RectF(p.left + p.width() * .32f, p.bottom - p.height() * .38f, p.right - pad, p.bottom - pad), d, s)
    }

    private fun drawTimeFirst(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, w: String?, map: Bitmap?) {
        val pad = p.width() * .035f
        if (set.showMapIcon) drawMap(c, RectF(p.left + pad, p.top + p.height() * .10f, p.left + p.width() * .20f, p.top + p.height() * .50f), map, s, false)
        if (d != null) drawLargeDate(c, d, p.left + p.width() * .24f, p.top + p.height() * .25f, s, 0xFFFFD34F.toInt())
        if (set.showWeather && w != null) drawWeather(c, RectF(p.right - p.width() * .25f, p.top + p.height() * .10f, p.right - pad, p.top + p.height() * .45f), w, s)
        drawAddressColor(c, a, p.left + pad, p.top + p.height() * .63f, p.width() * .60f, s, 0xFFFFD34F.toInt())
        drawCoordinates(c, co, RectF(p.left + pad, p.bottom - p.height() * .22f, p.right - pad, p.bottom - pad), s)
    }

    private fun drawCenteredDestination(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, w: String?, map: Bitmap?) {
        val inner = RectF(p.left + p.width() * .18f, p.top - p.height() * .05f, p.right - p.width() * .18f, p.bottom)
        drawDarkBox(c, inner, s)
        if (set.showMapIcon) drawMap(c, RectF(inner.centerX() - inner.width() * .16f, inner.top + 6f * s, inner.centerX() + inner.width() * .16f, inner.top + inner.height() * .32f), map, s, false)
        drawAddressColor(c, a, inner.left + 8f * s, inner.top + inner.height() * .58f, inner.width() - 16f * s, s, 0xFFFFD34F.toInt(), TemplateAlignment.CENTER)
        drawCoordinates(c, co, RectF(inner.left + 8f * s, inner.bottom - inner.height() * .29f, inner.right - 8f * s, inner.bottom - inner.height() * .16f), s)
        val footer = listOfNotNull(w?.removePrefix("[NIGHT]")?.split('\n')?.firstOrNull(), d).joinToString(" · ")
        if (footer.isNotBlank()) drawCenteredText(c, footer, inner.centerX(), inner.bottom - 9f * s, s, Color.WHITE)
    }

    private fun drawSideMap(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, w: String?, map: Bitmap?) {
        val pad = p.width() * .035f
        drawAddressColor(c, a, p.left + pad, p.top + p.height() * .25f, p.width() * .62f, s, 0xFFFFD34F.toInt())
        if (set.showMapIcon) drawMap(c, RectF(p.right - p.width() * .28f, p.top + p.height() * .08f, p.right - pad, p.top + p.height() * .52f), map, s, true)
        drawCoordinates(c, co, RectF(p.left + pad, p.top + p.height() * .51f, p.left + p.width() * .55f, p.top + p.height() * .65f), s)
        if (set.showWeather && w != null) drawWeather(c, RectF(p.left + pad, p.bottom - p.height() * .35f, p.left + p.width() * .24f, p.bottom - pad), w, s)
        if (d != null) drawDateLines(c, RectF(p.left + p.width() * .30f, p.bottom - p.height() * .35f, p.right - pad, p.bottom - pad), d, s)
    }

    private fun drawTravelFooter(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, w: String?, map: Bitmap?) {
        val pad = p.width() * .035f
        if (set.showMapIcon) drawMap(c, RectF(p.left + pad, p.top + p.height() * .08f, p.left + p.width() * .20f, p.top + p.height() * .48f), map, s, false)
        drawAddressColor(c, a, p.left + p.width() * .24f, p.top + p.height() * .24f, p.width() * .62f, s, 0xFFFFD34F.toInt())
        drawCoordinates(c, co, RectF(p.left + pad, p.top + p.height() * .54f, p.right - pad, p.top + p.height() * .70f), s)
        if (d != null) drawDateLines(c, RectF(p.left + pad, p.top + p.height() * .72f, p.left + p.width() * .64f, p.bottom - pad), d, s)
        if (set.showWeather && w != null) drawWeather(c, RectF(p.right - p.width() * .25f, p.top + p.height() * .68f, p.right - pad, p.bottom - pad), w, s)
    }

    private fun drawOceanPostcard(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?) {
        drawAddress(c, a, p.left + p.width() * .07f, p.top + p.height() * .25f, p.width() * .66f, s, TemplateAlignment.LEFT)
        drawCamera(c, p.right - p.width() * .16f, p.top + p.height() * .30f, s)
        if (d != null) drawDatePill(c, RectF(p.left + p.width() * .04f, p.top + p.height() * .48f, p.left + p.width() * .63f, p.top + p.height() * .72f), d, s, dateColor(set))
        drawCoordinates(c, co, RectF(p.left + p.width() * .05f, p.bottom - p.height() * .24f, p.right - p.width() * .05f, p.bottom - p.height() * .08f), s)
    }

    private fun drawCoralTicket(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, map: Bitmap?) {
        if (set.showMapIcon) drawMap(c, RectF(p.left + p.width() * .05f, p.top + p.height() * .10f, p.left + p.width() * .23f, p.top + p.height() * .48f), map, s, false)
        drawAddress(c, a, p.left + p.width() * .27f, p.top + p.height() * .23f, p.width() * .65f, s, TemplateAlignment.LEFT)
        if (d != null) drawDatePill(c, RectF(p.left + p.width() * .05f, p.top + p.height() * .53f, p.left + p.width() * .63f, p.top + p.height() * .75f), d, s, dateColor(set))
        drawCoordinates(c, co, RectF(p.left + p.width() * .05f, p.bottom - p.height() * .24f, p.right - p.width() * .05f, p.bottom - p.height() * .08f), s)
        drawSmallText(c, "PHOTO LOCATION", p.left + p.width() * .05f, p.bottom - p.height() * .03f, s, 0xFFFFB8B8.toInt())
    }

    private fun drawMintLabel(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, map: Bitmap?) {
        if (set.showMapIcon) drawMap(c, RectF(p.left + p.width() * .05f, p.top + p.height() * .12f, p.left + p.width() * .25f, p.top + p.height() * .54f), map, s, false)
        drawAddress(c, a, p.left + p.width() * .29f, p.top + p.height() * .26f, p.width() * .62f, s, TemplateAlignment.LEFT)
        drawCoordinates(c, co, RectF(p.left + p.width() * .29f, p.top + p.height() * .42f, p.right - p.width() * .05f, p.top + p.height() * .56f), s)
        if (d != null) drawDatePill(c, RectF(p.left + p.width() * .05f, p.top + p.height() * .64f, p.left + p.width() * .62f, p.top + p.height() * .86f), d, s, dateColor(set))
    }

    private fun drawLavenderJournal(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?) {
        val inner = RectF(p.left, p.top - p.height() * .08f, p.left + p.width() * .68f, p.bottom - p.height() * .05f)
        drawDarkBox(c, inner, s); drawCamera(c, inner.left + 30f * s, inner.top + 28f * s, s)
        drawSmallText(c, "Captured here", inner.left + 72f * s, inner.top + 30f * s, s, 0xFFD8C7FF.toInt())
        drawAddress(c, a, inner.left + 72f * s, inner.top + 54f * s, inner.width() - 80f * s, s, TemplateAlignment.LEFT)
        drawCoordinates(c, co, RectF(inner.left + 10f * s, inner.top + inner.height() * .60f, inner.right - 10f * s, inner.top + inner.height() * .72f), s)
        if (d != null) drawDatePill(c, RectF(inner.left + 10f * s, inner.bottom - 36f * s, inner.left + inner.width() * .85f, inner.bottom - 8f * s), d, s, dateColor(set))
    }

    private fun drawAmberNote(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, map: Bitmap?) {
        drawSmallText(c, "LOCATION NOTE", p.left + p.width() * .05f, p.top + p.height() * .20f, s, 0xFFFFD56C.toInt())
        drawAddress(c, a, p.left + p.width() * .05f, p.top + p.height() * .38f, p.width() * .70f, s, TemplateAlignment.LEFT)
        if (set.showMapIcon) drawMap(c, RectF(p.right - p.width() * .25f, p.top + p.height() * .24f, p.right - p.width() * .06f, p.top + p.height() * .62f), map, s, false)
        drawCoordinates(c, co, RectF(p.left + p.width() * .05f, p.top + p.height() * .60f, p.right - p.width() * .05f, p.top + p.height() * .74f), s)
        if (d != null) drawDatePill(c, RectF(p.left + p.width() * .05f, p.bottom - p.height() * .25f, p.left + p.width() * .63f, p.bottom - p.height() * .06f), d, s, dateColor(set))
    }

    private fun drawTealTag(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, map: Bitmap?) {
        if (set.showMapIcon) drawMap(c, RectF(p.left + p.width() * .05f, p.top + p.height() * .10f, p.left + p.width() * .25f, p.top + p.height() * .50f), map, s, false)
        drawAddress(c, a, p.left + p.width() * .29f, p.top + p.height() * .24f, p.width() * .63f, s, TemplateAlignment.LEFT)
        if (d != null) drawDatePill(c, RectF(p.left + p.width() * .05f, p.top + p.height() * .58f, p.left + p.width() * .78f, p.top + p.height() * .80f), d, s, dateColor(set))
        drawCamera(c, p.right - p.width() * .12f, p.top + p.height() * .68f, s)
        drawCoordinates(c, co, RectF(p.left + p.width() * .05f, p.bottom - p.height() * .18f, p.right - p.width() * .05f, p.bottom - p.height() * .05f), s)
    }

    private fun drawAddressColor(c: Canvas, lines: List<String>, x: Float, baseline: Float, width: Float, s: Float, color: Int, alignment: TemplateAlignment = TemplateAlignment.LEFT) {
        if (lines.isEmpty()) return
        val align = when (alignment) { TemplateAlignment.LEFT -> Paint.Align.LEFT; TemplateAlignment.CENTER -> Paint.Align.CENTER; TemplateAlignment.RIGHT -> Paint.Align.RIGHT }
        val title = textPaint(color, 15f * s, true).apply { textAlign = align }
        val body = textPaint(Color.WHITE, 12f * s, false).apply { textAlign = align }
        val anchor = when (alignment) { TemplateAlignment.LEFT -> x; TemplateAlignment.CENTER -> x + width / 2f; TemplateAlignment.RIGHT -> x + width }
        val titleLines = wrap(lines.first(), title, width).take(2)
        titleLines.forEachIndexed { i, line -> c.drawText(line, anchor, baseline + i * 18f * s, title) }
        val bodyLines = lines.drop(1).flatMap { wrap(it, body, width) }.take(2)
        val bodyBaseline = baseline + titleLines.size * 18f * s
        bodyLines.forEachIndexed { i, line -> c.drawText(line, anchor, bodyBaseline + i * 15f * s, body) }
    }

    private fun drawLargeDate(c: Canvas, value: String, x: Float, baseline: Float, s: Float, color: Int) {
        val parts = value.split(" · ")
        c.drawText(parts.firstOrNull() ?: value, x, baseline, textPaint(color, 23f * s, true))
        if (parts.size > 1) c.drawText(parts.drop(1).joinToString(" · "), x, baseline + 17f * s, textPaint(Color.WHITE, 11f * s, false))
    }

    private fun drawCenteredText(c: Canvas, value: String, x: Float, baseline: Float, s: Float, color: Int) {
        c.drawText(value, x, baseline, textPaint(color, 11f * s, false).apply { textAlign = Paint.Align.CENTER })
    }

    private fun drawSmallText(c: Canvas, value: String, x: Float, baseline: Float, s: Float, color: Int) {
        c.drawText(value, x, baseline, textPaint(color, 10f * s, true))
    }

    private fun drawCamera(c: Canvas, x: Float, y: Float, s: Float) {
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4C9FEF.toInt(); style = Paint.Style.FILL }
        c.drawRoundRect(RectF(x - 18f * s, y - 12f * s, x + 18f * s, y + 12f * s), 5f * s, 5f * s, body)
        c.drawCircle(x, y, 9f * s, paint(0xFF172B55.toInt()))
        c.drawCircle(x, y, 5f * s, paint(0xFF4E6ECA.toInt()))
        c.drawRect(x - 10f * s, y - 17f * s, x - 1f * s, y - 11f * s, body)
    }

    private fun drawLayoutOne(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, w: String?) {
        val pad = p.width() * .035f
        if (d != null) drawDatePill(c, RectF(p.left + pad, p.top + pad, p.left + p.width() * .57f, p.top + p.height() * .22f), d, s, dateColor(set))
        if (set.showWeather && w != null) drawWeather(c, RectF(p.right - p.width() * .36f, p.top + pad, p.right - pad, p.top + p.height() * .40f), w, s)
        drawAddress(c, a, p.left + pad, p.top + p.height() * .43f, p.width() * .54f, s, set.alignment)
        drawCoordinates(c, co, RectF(p.left + pad, p.bottom - p.height() * .25f, p.right - p.width() * .38f, p.bottom - pad), s)
    }

    private fun drawLayoutTwo(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, map: Bitmap?) {
        val pad = p.width() * .035f
        val mapRect = RectF(p.left + pad, p.top + pad, p.left + p.width() * .27f, p.top + p.height() * .58f)
        if (set.showMapIcon) drawMap(c, mapRect, map, s, false)
        drawAddress(c, a, p.left + p.width() * .32f, p.top + p.height() * .16f, p.width() * .58f, s, TemplateAlignment.LEFT)
        if (d != null) drawDatePill(c, RectF(p.left + p.width() * .31f, p.top + p.height() * .43f, p.left + p.width() * .82f, p.top + p.height() * .65f), d, s, dateColor(set))
        drawCoordinates(c, co, RectF(p.left + pad, p.bottom - p.height() * .23f, p.right - pad, p.bottom - pad), s)
    }

    private fun drawLayoutThree(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, map: Bitmap?) {
        val pad = p.width() * .035f
        drawAddress(c, a, p.left + pad, p.top + p.height() * .18f, p.width() * .58f, s, TemplateAlignment.LEFT)
        if (d != null) drawDatePill(c, RectF(p.left + pad, p.top + p.height() * .48f, p.left + p.width() * .58f, p.top + p.height() * .70f), d, s, dateColor(set))
        val mapRect = RectF(p.right - p.width() * .28f, p.top + p.height() * .08f, p.right - pad, p.top + p.height() * .56f)
        if (set.showMapIcon) drawMap(c, mapRect, map, s, true)
        drawCoordinates(c, co, RectF(p.left + pad, p.bottom - p.height() * .25f, p.right - p.width() * .38f, p.bottom - pad), s)
    }

    private fun drawLayoutFour(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, w: String?, map: Bitmap?) {
        val pad = p.width() * .035f
        if (d != null) drawDatePill(c, RectF(p.left + pad, p.top + pad, p.left + p.width() * .60f, p.top + p.height() * .22f), d, s, dateColor(set))
        if (set.showWeather && w != null) drawWeather(c, RectF(p.right - p.width() * .36f, p.top + pad, p.right - pad, p.top + p.height() * .43f), w, s)
        drawAddress(c, a, p.left + pad, p.top + p.height() * .59f, p.width() * .55f, s, TemplateAlignment.LEFT)
        val mapRect = RectF(p.right - p.width() * .28f, p.top + p.height() * .55f, p.right - pad, p.bottom - p.height() * .14f)
        if (set.showMapIcon) drawMap(c, mapRect, map, s, false)
        drawCoordinates(c, co, RectF(p.left + pad, p.bottom - p.height() * .23f, p.right - p.width() * .35f, p.bottom - pad), s)
    }

    private fun drawLayoutFive(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, w: String?) {
        val pad = p.width() * .035f
        if (set.showWeather && w != null) drawWeather(c, RectF(p.left + pad, p.top + pad, p.left + p.width() * .31f, p.top + p.height() * .43f), w, s)
        if (d != null) drawDatePill(c, RectF(p.left + p.width() * .36f, p.top + pad, p.left + p.width() * .93f, p.top + p.height() * .22f), d, s, dateColor(set))
        drawAddress(c, a, p.left + p.width() * .36f, p.top + p.height() * .45f, p.width() * .58f, s, TemplateAlignment.LEFT)
        drawCoordinates(c, co, RectF(p.left + pad, p.bottom - p.height() * .25f, p.right - p.width() * .35f, p.bottom - pad), s)
    }

    private fun drawLayoutSix(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, w: String?, map: Bitmap?) {
        val pad = p.width() * .035f
        if (set.showWeather && w != null) drawWeather(c, RectF(p.left + pad, p.top + pad, p.left + p.width() * .30f, p.top + p.height() * .39f), w, s)
        if (d != null) drawDatePill(c, RectF(p.left + p.width() * .33f, p.top + pad, p.right - pad, p.top + p.height() * .22f), d, s, dateColor(set))
        drawCoordinates(c, co, RectF(p.left + p.width() * .33f, p.top + p.height() * .25f, p.right - pad, p.top + p.height() * .45f), s)
        val mapRect = RectF(p.left + pad, p.top + p.height() * .48f, p.left + p.width() * .29f, p.bottom - p.height() * .12f)
        if (set.showMapIcon) drawMap(c, mapRect, map, s, false)
        drawAddress(c, a, p.left + p.width() * .33f, p.top + p.height() * .53f, p.width() * .61f, s, TemplateAlignment.LEFT)
    }

    private fun drawLayoutSeven(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, map: Bitmap?) {
        val pad = p.width() * .035f
        val mapRect = RectF(p.left + pad, p.top + pad, p.left + p.width() * .48f, p.top + p.height() * .48f)
        if (set.showMapIcon) drawMap(c, mapRect, map, s, false)
        val dateBox = RectF(p.left + p.width() * .52f, p.top + pad, p.right - pad, p.top + p.height() * .48f)
        drawDarkBox(c, dateBox, s)
        if (d != null) drawDateLines(c, dateBox, d, s)
        drawAddress(c, a, p.left + pad, p.top + p.height() * .57f, p.width() * .58f, s, TemplateAlignment.LEFT)
        drawCoordinates(c, co, RectF(p.left + pad, p.bottom - p.height() * .23f, p.right - p.width() * .38f, p.bottom - pad), s)
    }

    private fun drawLayoutEight(c: Canvas, p: RectF, s: Float, set: StampCustomization, a: List<String>, co: String?, d: String?, w: String?, map: Bitmap?) {
        val pad = p.width() * .035f
        val mapRect = RectF(p.left + pad, p.top + pad, p.left + p.width() * .28f, p.top + p.height() * .52f)
        if (set.showMapIcon) drawMap(c, mapRect, map, s, false)
        drawAddress(c, a, p.left + p.width() * .32f, p.top + p.height() * .18f, p.width() * .62f, s, TemplateAlignment.LEFT)
        if (d != null) drawDatePill(c, RectF(p.left + pad, p.top + p.height() * .56f, p.left + p.width() * .60f, p.top + p.height() * .78f), d, s, dateColor(set))
        if (set.showWeather && w != null) drawWeather(c, RectF(p.left + p.width() * .63f, p.top + p.height() * .54f, p.right - pad, p.top + p.height() * .85f), w, s)
        drawCoordinates(c, co, RectF(p.left + pad, p.bottom - p.height() * .23f, p.right - pad, p.bottom - pad), s)
    }

    private fun drawDatePill(c: Canvas, r: RectF, value: String, s: Float, color: Int = BLUE) {
        c.drawRoundRect(r, 7f * s, 7f * s, paint(color))
        val p = textPaint(Color.WHITE, 13f * s, false)
        c.drawText(wrap(value, p, r.width() - 10f * s).firstOrNull() ?: value, r.left + 8f * s, r.centerY() + p.textSize * .35f, p)
    }

    private fun dateColor(set: StampCustomization): Int = when (set.templateKey) {
        "coral_travel_ticket" -> 0xFFE05B68.toInt()
        "mint_location_label" -> 0xFF65D0B0.toInt()
        "lavender_photo_journal" -> 0xFFB694EE.toInt()
        "amber_field_note" -> 0xFFF2B84B.toInt()
        "teal_destination_tag" -> 0xFF32B8BF.toInt()
        else -> BLUE
    }

    private fun templateBorder(c: Canvas, r: RectF, s: Float, key: String) {
        val color = when (key) {
            "location_spotlight", "time_first", "centered_destination", "side_map", "travel_footer" -> 0xFF5D9BDB.toInt()
            "coral_travel_ticket" -> 0xFFE05B68.toInt()
            "mint_location_label" -> 0xFF65D0B0.toInt()
            "lavender_photo_journal" -> 0xFFB694EE.toInt()
            "amber_field_note" -> 0xFFF2B84B.toInt()
            "teal_destination_tag" -> 0xFF32B8BF.toInt()
            else -> null
        } ?: return
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * s
        }
        c.drawRoundRect(r, 12f * s, 12f * s, outline)
    }

    private fun drawWeather(c: Canvas, r: RectF, value: String, s: Float) {
        drawDarkBox(c, r, s)
        val night = value.startsWith("[NIGHT]")
        val cleanValue = value.removePrefix("[NIGHT]")
        val lines = cleanValue.split('\n').filter { it.isNotBlank() }
        val p = textPaint(Color.WHITE, 13f * s, false)
        val condition = lines.getOrNull(1).orEmpty().lowercase(Locale.getDefault())
        when {
            night -> drawMoon(c, r.left + 18f * s, r.top + 20f * s, 7f * s)
            condition.contains("rain") || condition.contains("drizzle") || condition.contains("snow") -> drawCloud(c, r.left + 18f * s, r.top + 21f * s, 7f * s, true)
            condition.contains("cloud") || condition.contains("overcast") || condition.contains("fog") -> drawCloud(c, r.left + 18f * s, r.top + 21f * s, 7f * s, false)
            else -> drawSun(c, r.left + 18f * s, r.top + 20f * s, 6f * s)
        }
        c.drawText(lines.firstOrNull() ?: value, r.left + 31f * s, r.top + 24f * s, p)
        if (lines.size > 1) c.drawText(lines[1], r.left + 8f * s, r.top + 48f * s, p)
    }

    private fun drawCloud(c: Canvas, x: Float, y: Float, radius: Float, drops: Boolean) {
        val cloud = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDCEBEE.toInt() }
        c.drawCircle(x - radius * .55f, y, radius * .72f, cloud)
        c.drawCircle(x + radius * .15f, y - radius * .35f, radius, cloud)
        c.drawRoundRect(RectF(x - radius * 1.25f, y, x + radius * 1.25f, y + radius * .72f), radius * .3f, radius * .3f, cloud)
        if (drops) {
            val rain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF75B9E8.toInt()
                strokeWidth = radius * .22f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(x - radius * .5f, y + radius * .95f, x - radius * .8f, y + radius * 1.5f, rain)
            c.drawLine(x + radius * .2f, y + radius * .95f, x - radius * .1f, y + radius * 1.5f, rain)
        }
    }

    private fun drawSun(c: Canvas, x: Float, y: Float, radius: Float) {
        c.drawCircle(x, y, radius, paint(YELLOW))
        val rays = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = YELLOW
            style = Paint.Style.STROKE
            strokeWidth = radius * .35f
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val inner = radius * 1.55f
            val outer = radius * 2.1f
            c.drawLine(
                x + kotlin.math.cos(angle).toFloat() * inner,
                y + kotlin.math.sin(angle).toFloat() * inner,
                x + kotlin.math.cos(angle).toFloat() * outer,
                y + kotlin.math.sin(angle).toFloat() * outer,
                rays
            )
        }
    }

    private fun drawMoon(c: Canvas, x: Float, y: Float, radius: Float) {
        c.drawCircle(x, y, radius, paint(YELLOW))
        // Cut the right side with the panel colour to form a crescent.
        c.drawCircle(x + radius * .42f, y - radius * .32f, radius, paint(PANEL))
    }

    private fun drawAddress(c: Canvas, lines: List<String>, x: Float, baseline: Float, width: Float, s: Float, alignment: TemplateAlignment) {
        if (lines.isEmpty()) return
        val align = when (alignment) { TemplateAlignment.LEFT -> Paint.Align.LEFT; TemplateAlignment.CENTER -> Paint.Align.CENTER; TemplateAlignment.RIGHT -> Paint.Align.RIGHT }
        val title = textPaint(Color.WHITE, 15f * s, true).apply { textAlign = align }
        val body = textPaint(Color.WHITE, 12f * s, false).apply { textAlign = align }
        val anchor = when (alignment) { TemplateAlignment.LEFT -> x; TemplateAlignment.CENTER -> x + width / 2f; TemplateAlignment.RIGHT -> x + width }
        val titleLines = wrap(lines.first(), title, width).take(2)
        titleLines.forEachIndexed { i, line -> c.drawText(line, anchor, baseline + i * 18f * s, title) }
        val bodyLines = lines.drop(1).flatMap { wrap(it, body, width) }.take(2)
        val bodyBaseline = baseline + titleLines.size * 18f * s
        bodyLines.forEachIndexed { i, line -> c.drawText(line, anchor, bodyBaseline + i * 15f * s, body) }
    }

    private fun drawCoordinates(c: Canvas, value: String?, r: RectF, s: Float) {
        if (value == null) return
        drawDarkBox(c, r, s)
        c.drawText(value, r.left + 8f * s, r.centerY() + 4f * s, textPaint(Color.WHITE, 11f * s, false))
    }

    private fun drawDateLines(c: Canvas, r: RectF, date: String, s: Float) {
        val parts = date.split(" · ")
        val p = textPaint(Color.WHITE, 13f * s, false)
        c.drawText(parts.firstOrNull() ?: date, r.left + 10f * s, r.top + 24f * s, p)
        c.drawText(parts.drop(1).joinToString(" · "), r.left + 10f * s, r.top + 47f * s, p)
    }

    private fun drawDarkBox(c: Canvas, r: RectF, s: Float) = c.drawRoundRect(r, 8f * s, 8f * s, paint(PANEL))

    private fun drawMap(c: Canvas, r: RectF, bitmap: Bitmap?, s: Float, circle: Boolean) {
        val path = Path().apply { if (circle) addOval(r, Path.Direction.CW) else addRoundRect(r, 9f * s, 9f * s, Path.Direction.CW) }
        c.save(); c.clipPath(path)
        if (bitmap != null) c.drawBitmap(bitmap, null, r, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) else drawMapPlaceholder(c, r, s)
        c.restore()
    }

    private fun drawMapPlaceholder(c: Canvas, r: RectF, s: Float) {
        c.drawRect(r, paint(MAP_BG))
        val roads = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MAP_ROAD; strokeWidth = 6f * s; style = Paint.Style.STROKE }
        c.drawLine(r.left, r.top + r.height() * .28f, r.right, r.top + r.height() * .42f, roads)
        c.drawLine(r.left + r.width() * .44f, r.top, r.left + r.width() * .35f, r.bottom, roads)
        val water = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MAP_WATER; strokeWidth = r.width() * .18f; style = Paint.Style.STROKE }
        val river = Path().apply { moveTo(r.left + r.width() * .68f, r.top); cubicTo(r.left + r.width() * .42f, r.top + r.height() * .35f, r.left + r.width() * .84f, r.top + r.height() * .65f, r.left + r.width() * .56f, r.bottom) }
        c.drawPath(river, water)
        c.drawCircle(r.centerX(), r.centerY(), 6f * s, paint(RED))
        c.drawCircle(r.centerX(), r.centerY(), 2f * s, paint(Color.WHITE))
    }

    fun formatCoordinates(latitude: Double, longitude: Double): String {
        if (latitude.isNaN() || longitude.isNaN()) return "Location unavailable"
        val lat = String.format(Locale.getDefault(), "%.4f° %s", kotlin.math.abs(latitude), if (latitude >= 0) "N" else "S")
        val lon = String.format(Locale.getDefault(), "%.4f° %s", kotlin.math.abs(longitude), if (longitude >= 0) "E" else "W")
        return "$lat · $lon"
    }

    fun formatDate(time: Long, format: TemplateDateFormat): String {
        val pattern = when (format) {
            TemplateDateFormat.SHORT_MONTH -> "dd MMM yyyy · hh:mm a"
            TemplateDateFormat.NUMERIC -> "dd/MM/yyyy · hh:mm a"
            TemplateDateFormat.TIME_FIRST -> "hh:mm a · dd MMM yyyy"
            TemplateDateFormat.WEEKDAY -> "EEEE, dd MMM yyyy · hh:mm a"
            TemplateDateFormat.ISO -> "yyyy-MM-dd · HH:mm"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
    }

    private fun displayAddress(settings: StampCustomization, original: String): String {
        if (settings.addressMode == TemplateAddressMode.MANUAL && settings.manualAddress.isNotBlank()) return settings.manualAddress
        return when (settings.addressDetail) {
            TemplateAddressDetail.NONE -> ""
            TemplateAddressDetail.CITY -> original.split(',').takeLast(2).joinToString(", ").trim().ifBlank { original }
            TemplateAddressDetail.FULL -> original.ifBlank { "Banff National Park, Alberta, Canada" }
        }
    }

    private fun splitAddress(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val parts = value.split(',').map { it.trim() }.filter { it.isNotBlank() }
        return if (parts.size > 1) listOf(parts.first(), parts.drop(1).joinToString(", ")) else listOf(value)
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = size; typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun wrap(text: String, p: Paint, width: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>(); var current = ""
        text.split(Regex("\\s+")).forEach { word ->
            if (current.isBlank() && p.measureText(word) > width) {
                var remaining = word
                while (remaining.isNotEmpty()) {
                    val count = p.breakText(remaining, true, width, null).coerceAtLeast(1)
                    lines += remaining.take(count)
                    remaining = remaining.drop(count)
                }
                return@forEach
            }
            val next = if (current.isBlank()) word else "$current $word"
            if (p.measureText(next) > width && current.isNotBlank()) { lines += current; current = word } else current = next
        }
        if (current.isNotBlank()) lines += current
        return lines
    }
}
