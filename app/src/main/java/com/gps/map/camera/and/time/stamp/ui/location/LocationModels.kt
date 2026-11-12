package com.gps.map.camera.and.time.stamp.ui.location

import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlace

enum class LocationScreenState {
    LOADING,
    FOUND,
    SEARCHING,
    UNAVAILABLE
}

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val place: EarthPlace? = null
) {
    val isGpsLocation: Boolean
        get() = accuracyMeters != null
}
