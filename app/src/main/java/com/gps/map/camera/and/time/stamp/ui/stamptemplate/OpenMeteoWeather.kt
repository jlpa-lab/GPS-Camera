package com.gps.map.camera.and.time.stamp.ui.stamptemplate

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class WeatherSnapshot(
    val temperatureText: String,
    val condition: String,
    val isDay: Boolean
) {
    val uiText: String get() = "$temperatureText\n$condition"

    // The renderer keeps this small flag with the value so every template uses
    // the same day/night result without another weather request.
    val stampText: String get() = (if (isDay) "" else "[NIGHT]") + uiText
}

/** One short-lived, location-aware weather snapshot shared by the app's stamps. */
object OpenMeteoWeather {
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private const val CACHE_DISTANCE_DEGREES = 0.02

    private data class CachedWeather(
        val latitude: Double,
        val longitude: Double,
        val fetchedAt: Long,
        val value: WeatherSnapshot
    )

    @Volatile
    private var cached: CachedWeather? = null

    /** Synchronized so simultaneous template/camera requests cannot duplicate the call. */
    @Synchronized
    fun fetchCached(latitude: Double, longitude: Double): WeatherSnapshot? {
        if (latitude.isNaN() || longitude.isNaN()) return null
        val now = System.currentTimeMillis()
        cached?.let { entry ->
            val fresh = now - entry.fetchedAt < CACHE_TTL_MS
            val nearby = kotlin.math.abs(entry.latitude - latitude) <= CACHE_DISTANCE_DEGREES &&
                kotlin.math.abs(entry.longitude - longitude) <= CACHE_DISTANCE_DEGREES
            if (fresh && nearby) return entry.value
        }

        val value = request(latitude, longitude) ?: return null
        cached = CachedWeather(latitude, longitude, now, value)
        return value
    }

    private fun request(latitude: Double, longitude: Double): WeatherSnapshot? {
        val connection = runCatching {
            URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code,is_day&timezone=auto"
            ).openConnection() as HttpURLConnection
        }.getOrNull() ?: return null

        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.requestMethod = "GET"
            if (connection.responseCode !in 200..299) return null
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(json).optJSONObject("current") ?: return null
            val temperature = current.optDouble("temperature_2m", Double.NaN)
            if (temperature.isNaN()) return null
            val code = current.optInt("weather_code", -1)
            val isDay = current.optInt("is_day", 1) == 1
            WeatherSnapshot(
                temperatureText = String.format(Locale.getDefault(), "%.0f°C", temperature),
                condition = description(code, isDay),
                isDay = isDay
            )
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun description(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "Clear sky" else "Clear night"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rainy"
        71, 73, 75, 77 -> "Snowy"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather unavailable"
    }
}
