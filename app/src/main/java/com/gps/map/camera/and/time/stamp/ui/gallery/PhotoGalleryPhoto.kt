package com.gps.map.camera.and.time.stamp.ui.gallery

import android.net.Uri

data class PhotoGalleryPhoto(
    val uri: Uri,
    val dateAdded: Long,
    val hasLocation: Boolean
)
