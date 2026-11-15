package com.gps.map.camera.and.time.stamp.ui.photogrid

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.gps.map.camera.and.time.stamp.ui.gallery.PhotoGalleryPhoto

object PhotoGridMediaAccess {
    fun queryPhotos(context: Context, limit: Int, offset: Int): List<PhotoGalleryPhoto> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val photos = mutableListOf<PhotoGalleryPhoto>()
        // LIMIT/OFFSET in sortOrder is rejected by several Samsung MediaStore versions.
        val order = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            order
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            var position = 0
            while (cursor.moveToNext()) {
                if (position++ < offset) continue
                if (photos.size >= limit) break
                val uri = ContentUris.withAppendedId(
                    collection,
                    cursor.getLong(idColumn)
                )
                photos += PhotoGalleryPhoto(
                    uri = uri,
                    dateAdded = cursor.getLong(dateColumn),
                    hasLocation = hasExifLocation(context, uri)
                )
            }
        }
        return photos
    }

    private fun hasExifLocation(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val coordinates = FloatArray(2)
            ExifInterface(descriptor.fileDescriptor).getLatLong(coordinates)
        } ?: false
    } catch (_: Exception) {
        false
    }
}
