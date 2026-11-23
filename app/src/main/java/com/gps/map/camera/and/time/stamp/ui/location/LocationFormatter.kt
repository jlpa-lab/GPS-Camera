package com.gps.map.camera.and.time.stamp.ui.location

import android.content.Context
import com.gps.map.camera.and.time.stamp.ui.settings.MeasurementFormatter
import java.util.Locale
import kotlin.math.abs

object LocationFormatter {
    fun latitude(value: Double): String = formatCoordinate(value, "N", "S")

    fun longitude(value: Double): String = formatCoordinate(value, "E", "W")

    fun accuracy(context: Context, meters: Float): String =
        MeasurementFormatter.accuracy(context, meters)

    fun shareCoordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

    private fun formatCoordinate(value: Double, positive: String, negative: String): String {
        val hemisphere = if (value >= 0) positive else negative
        return String.format(Locale.getDefault(), "%.4f° %s", abs(value), hemisphere)
    }
}
