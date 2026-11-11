package com.gps.map.camera.and.time.stamp.ui.measurement

import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Geodesic calculations used by the area measurement screen. */
object AreaMeasurementCalculator {
    private const val EARTH_RADIUS_METERS = 6_378_137.0

    fun areaSquareMeters(points: List<LatLng>): Double {
        if (points.size < 3) return 0.0

        var signedArea = 0.0
        points.indices.forEach { index ->
            val current = points[index]
            val next = points[(index + 1) % points.size]
            signedArea += Math.toRadians(next.longitude - current.longitude) *
                (2.0 + sin(Math.toRadians(current.latitude)) + sin(Math.toRadians(next.latitude)))
        }
        return abs(signedArea * EARTH_RADIUS_METERS * EARTH_RADIUS_METERS / 2.0)
    }

    fun perimeterMeters(points: List<LatLng>): Double {
        if (points.size < 2) return 0.0
        return points.indices.sumOf { index ->
            distanceMeters(points[index], points[(index + 1) % points.size])
        }
    }

    private fun distanceMeters(start: LatLng, end: LatLng): Double {
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(end.longitude - start.longitude)
        val haversine = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
        return 2.0 * EARTH_RADIUS_METERS * atan2(sqrt(haversine), sqrt(1.0 - haversine))
    }
}
