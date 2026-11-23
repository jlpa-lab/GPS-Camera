package com.gps.map.camera.and.time.stamp.ui.settings

import android.content.Context
import com.gps.map.camera.and.time.stamp.pref.AppSharedPreferencesApp
import java.util.Locale

/** Formats distance readouts using the unit selected in Settings. */
object MeasurementFormatter {
    fun distance(context: Context, meters: Double): String {
        return if (unit(context) == MeasurementUnit.IMPERIAL) {
            if (meters >= 1_609.344) {
                String.format(Locale.getDefault(), "%.1f mi", meters / 1_609.344)
            } else {
                String.format(Locale.getDefault(), "%,.0f ft", meters * 3.280839895)
            }
        } else if (meters >= 1_000.0) {
            String.format(Locale.getDefault(), "%.1f km", meters / 1_000.0)
        } else {
            String.format(Locale.getDefault(), "%.0f m", meters)
        }
    }

    fun accuracy(context: Context, meters: Float): String =
        "± ${distance(context, meters.toDouble())}"

    private fun unit(context: Context): MeasurementUnit =
        MeasurementUnit.fromKey(AppSharedPreferencesApp(context.applicationContext).measurementUnit)
}
