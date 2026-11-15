package com.gps.map.camera.and.time.stamp.ui.gallery

import android.net.Uri

enum class GalleryCaptureType { PHOTO, VIDEO }

data class GalleryCapture(
    val uri: Uri,
    val type: GalleryCaptureType,
    val dateAdded: Long,
    val displayName: String,
    val durationMs: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val hasBakedStamp: Boolean = false
)
