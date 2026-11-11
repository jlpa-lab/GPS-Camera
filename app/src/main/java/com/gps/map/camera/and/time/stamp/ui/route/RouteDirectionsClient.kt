package com.gps.map.camera.and.time.stamp.ui.route

import android.content.Context
import android.net.Uri
import com.google.android.gms.maps.model.LatLng
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Small client for the Google Directions endpoint used by Route Plan. */
class RouteDirectionsClient(private val context: Context) {

    suspend fun findRoute(
        origin: LatLng,
        destination: LatLng,
        mode: RouteTravelMode
    ): RouteDetails? {
        val googleRoute = readMapsApiKey()?.let { apiKey ->
            requestGoogleRoute(origin, destination, mode, apiKey)
        }
        return googleRoute ?: requestOpenRoute(origin, destination, mode)
    }

    private fun requestGoogleRoute(
        origin: LatLng,
        destination: LatLng,
        mode: RouteTravelMode,
        apiKey: String
    ): RouteDetails? {
        val requestUrl = Uri.parse(GOOGLE_ENDPOINT).buildUpon()
            .appendQueryParameter("origin", "${origin.latitude},${origin.longitude}")
            .appendQueryParameter("destination", "${destination.latitude},${destination.longitude}")
            .appendQueryParameter("mode", mode.directionsValue)
            .appendQueryParameter("key", apiKey)
            .build()
        return requestJson(requestUrl.toString(), ::parseGoogleResponse)
    }

    private fun requestOpenRoute(
        origin: LatLng,
        destination: LatLng,
        mode: RouteTravelMode
    ): RouteDetails? {
        val profile = when (mode) {
            RouteTravelMode.DRIVE -> "driving"
            RouteTravelMode.WALK -> "foot"
            RouteTravelMode.CYCLE -> "bike"
        }
        val coordinates = "${origin.longitude},${origin.latitude};" +
            "${destination.longitude},${destination.latitude}"
        val requestUrl = Uri.parse("$OPEN_ROUTE_ENDPOINT/$profile/$coordinates").buildUpon()
            .appendQueryParameter("overview", "full")
            .appendQueryParameter("geometries", "polyline")
            .appendQueryParameter("steps", "false")
            .build()
        return requestJson(requestUrl.toString(), ::parseOpenRouteResponse)
    }

    private fun requestJson(
        requestUrl: String,
        parser: (String) -> RouteDetails?
    ): RouteDetails? {
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = REQUEST_TIMEOUT_MS
            readTimeout = REQUEST_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "GPS-Camera/1.0 (route planning)")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            parser(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseGoogleResponse(response: String): RouteDetails? {
        val root = JSONObject(response)
        if (root.optString("status") != "OK") return null
        val route = root.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val leg = route.optJSONArray("legs")?.optJSONObject(0) ?: return null
        val distance = leg.optJSONObject("distance")?.optInt("value", -1) ?: -1
        val duration = leg.optJSONObject("duration")?.optInt("value", -1) ?: -1
        val encodedPath = route.optJSONObject("overview_polyline")?.optString("points")
        if (distance < 0 || duration < 0 || encodedPath.isNullOrBlank()) return null

        val path = decodePolyline(encodedPath)
        return if (path.size >= 2) {
            RouteDetails(path, distance, duration)
        } else {
            null
        }
    }

    private fun parseOpenRouteResponse(response: String): RouteDetails? {
        val root = JSONObject(response)
        if (root.optString("code") != "Ok") return null
        val route = root.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val distance = route.optDouble("distance", -1.0)
        val duration = route.optDouble("duration", -1.0)
        val encodedPath = route.optString("geometry")
        if (distance < 0.0 || duration < 0.0 || encodedPath.isBlank()) return null

        val path = decodePolyline(encodedPath)
        return if (path.size >= 2) {
            RouteDetails(path, distance.toInt(), duration.toInt())
        } else {
            null
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val points = mutableListOf<LatLng>()
        var index = 0
        var latitude = 0
        var longitude = 0

        while (index < encoded.length) {
            val latitudeDelta = decodeValue(encoded, index)
            index = latitudeDelta.nextIndex
            val longitudeDelta = decodeValue(encoded, index)
            index = longitudeDelta.nextIndex
            latitude += latitudeDelta.value
            longitude += longitudeDelta.value
            points += LatLng(latitude / 100000.0, longitude / 100000.0)
        }
        return points
    }

    private fun decodeValue(encoded: String, startIndex: Int): DecodedValue {
        var index = startIndex
        var result = 0
        var shift = 0
        var chunk: Int
        do {
            if (index >= encoded.length) throw IOException("Invalid route polyline")
            chunk = encoded[index++].code - 63
            result = result or ((chunk and 0x1f) shl shift)
            shift += 5
        } while (chunk >= 0x20)

        val value = if ((result and 1) != 0) {
            -(result shr 1) - 1
        } else {
            result shr 1
        }
        return DecodedValue(value, index)
    }

    private fun readMapsApiKey(): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.geo.API_KEY")
                ?.takeIf { it.isNotBlank() && !it.contains("YOUR_MAPS_API_KEY") }
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            null
        }
    }

    private data class DecodedValue(val value: Int, val nextIndex: Int)

    private companion object {
        const val GOOGLE_ENDPOINT = "https://maps.googleapis.com/maps/api/directions/json"
        const val OPEN_ROUTE_ENDPOINT = "https://router.project-osrm.org/route/v1"
        const val REQUEST_TIMEOUT_MS = 12_000
    }
}
