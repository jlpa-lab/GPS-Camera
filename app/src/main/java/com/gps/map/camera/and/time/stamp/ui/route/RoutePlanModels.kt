package com.gps.map.camera.and.time.stamp.ui.route

import com.google.android.gms.maps.model.LatLng

enum class RouteTravelMode(val directionsValue: String) {
    DRIVE("driving"),
    WALK("walking"),
    CYCLE("bicycling")
}

data class RouteDetails(
    val path: List<LatLng>,
    val distanceMeters: Int,
    val durationSeconds: Int
)
