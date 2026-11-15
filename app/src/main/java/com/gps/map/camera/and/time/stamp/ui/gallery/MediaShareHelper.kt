package com.gps.map.camera.and.time.stamp.ui.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri

object MediaShareHelper {
    fun createShareIntent(uri: Uri, type: GalleryCaptureType): Intent = Intent(Intent.ACTION_SEND).apply {
        this.type = if (type == GalleryCaptureType.VIDEO) "video/*" else "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun openChooser(context: Context, uri: Uri, type: GalleryCaptureType, title: String) {
        context.startActivity(Intent.createChooser(createShareIntent(uri, type), title))
    }
}
