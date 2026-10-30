package com.gps.map.camera.and.time.stamp.ui.earthview

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class EarthPlace(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val placeId: String? = null
)

object EarthPlaceSearch {

    private const val ENDPOINT = "https://nominatim.openstreetmap.org/search"
    private const val FALLBACK_ENDPOINT = "https://photon.komoot.io/api/"
    private const val USER_AGENT = "GPS-Camera/1.0 (location search)"

    @Throws(IOException::class)
    fun search(query: String): List<EarthPlace> {
        return try {
            searchNominatim(query)
        } catch (primaryError: IOException) {
            searchPhoton(query, primaryError)
        }
    }

    private fun searchNominatim(query: String): List<EarthPlace> {
        val language = Locale.getDefault().toLanguageTag()
        val requestUrl = Uri.parse(ENDPOINT).buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("format", "json")
            .appendQueryParameter("accept-language", language)
            .appendQueryParameter("limit", "8")
            .build()

        val connection = (URL(requestUrl.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", language)
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Nominatim returned HTTP ${connection.responseCode}")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parse(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun searchPhoton(query: String, primaryError: IOException): List<EarthPlace> {
        val requestUrl = Uri.parse(FALLBACK_ENDPOINT).buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("limit", "8")
            .build()
        val connection = (URL(requestUrl.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Photon HTTP ${connection.responseCode}")
            }
            parsePhoton(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (fallbackError: IOException) {
            throw IOException(
                "Nominatim: ${primaryError.message}; Photon: ${fallbackError.message}",
                fallbackError
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(response: String): List<EarthPlace> {
        val results = JSONArray(response)
        return buildList(results.length()) {
            for (index in 0 until results.length()) {
                val result = results.optJSONObject(index) ?: continue
                val latitude = result.optString("lat").toDoubleOrNull() ?: continue
                val longitude = result.optString("lon").toDoubleOrNull() ?: continue
                if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue

                val address = result.optString("display_name").trim()
                val name = result.optString("name").trim().ifEmpty {
                    address.substringBefore(',').trim()
                }
                if (name.isNotEmpty() && address.isNotEmpty()) {
                    add(
                        EarthPlace(
                            name = name,
                            address = address,
                            latitude = latitude,
                            longitude = longitude,
                            placeId = result.optString("place_id").takeIf(String::isNotEmpty)
                        )
                    )
                }
            }
        }
    }

    private fun parsePhoton(response: String): List<EarthPlace> {
        val features = JSONObject(response).optJSONArray("features") ?: return emptyList()
        return buildList(features.length()) {
            for (index in 0 until features.length()) {
                val feature = features.optJSONObject(index) ?: continue
                val coordinates = feature.optJSONObject("geometry")
                    ?.optJSONArray("coordinates") ?: continue
                if (coordinates.length() < 2) continue

                val longitude = coordinates.optDouble(0, Double.NaN)
                val latitude = coordinates.optDouble(1, Double.NaN)
                if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue

                val properties = feature.optJSONObject("properties") ?: continue
                val name = properties.optString("name").trim().ifEmpty {
                    properties.optString("city").trim()
                }
                val address = listOf(
                    properties.optString("street").trim(),
                    properties.optString("housenumber").trim(),
                    properties.optString("city").trim(),
                    properties.optString("state").trim(),
                    properties.optString("country").trim()
                ).filter(String::isNotEmpty).distinct().joinToString(", ")

                if (name.isNotEmpty() && address.isNotEmpty()) {
                    add(
                        EarthPlace(
                            name = name,
                            address = address,
                            latitude = latitude,
                            longitude = longitude,
                            placeId = feature.optString("id").takeIf(String::isNotEmpty)
                        )
                    )
                }
            }
        }
    }
}
