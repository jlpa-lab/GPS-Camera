package com.gps.map.camera.and.time.stamp.ui.gallery

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File

object GalleryMediaRepository {
    fun queryCaptures(
        context: Context,
        filter: GalleryFilter,
        limit: Int,
        offset: Int
    ): List<GalleryCapture> {
        val images = if (filter != GalleryFilter.VIDEOS) {
            queryImages(context, limit + offset)
        } else emptyList()
        val videos = if (filter != GalleryFilter.PHOTOS) {
            queryVideos(context, limit + offset)
        } else emptyList()
        return (images + videos)
            .sortedByDescending { it.dateAdded }
            .drop(offset)
            .take(limit)
            .filter { isStillAvailable(context, it.uri) }
    }

    private fun queryImages(context: Context, count: Int): List<GalleryCapture> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        return query(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, imageSelection(context), count) { cursor, uri ->
            val coordinates = readLocation(context, uri)
            GalleryCapture(
                uri = uri,
                type = GalleryCaptureType.PHOTO,
                dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)),
                displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)),
                latitude = coordinates?.first,
                longitude = coordinates?.second,
                hasBakedStamp = hasBakedStamp(context, uri)
            )
        }
    }

    private fun queryVideos(context: Context, count: Int): List<GalleryCapture> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )
        return query(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, videoSelection(context), count) { cursor, uri ->
            GalleryCapture(
                uri = uri,
                type = GalleryCaptureType.VIDEO,
                dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)),
                displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)),
                durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
            )
        }
    }

    private fun <T> query(
        context: Context,
        collection: Uri,
        projection: Array<String>,
        selection: String,
        count: Int,
        map: (Cursor, Uri) -> T
    ): List<T> {
        val result = mutableListOf<T>()
        // Some OEM MediaStore providers reject SQL LIMIT/OFFSET in sortOrder.
        // Read in the provider's supported order and page the small result in memory.
        val order = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        context.contentResolver.query(collection, projection, selection, null, order)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (cursor.moveToNext()) {
                if (result.size >= count) break
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                runCatching { result += map(cursor, uri) }
            }
        }
        return result
    }

    private fun imageSelection(context: Context): String = appOwnedSelection(
        context,
        "Pictures/GPS Camera/",
        MediaStore.Images.Media.DATA
    )

    private fun videoSelection(context: Context): String = appOwnedSelection(
        context,
        "Movies/GPS Video/",
        MediaStore.Video.Media.DATA
    )

    private fun appOwnedSelection(context: Context, relativePath: String, legacyPathColumn: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} = '$relativePath'"
        } else {
            val root = Environment.getExternalStoragePublicDirectory(
                if (relativePath.startsWith("Movies")) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            )
            "$legacyPathColumn LIKE '${File(root, relativePath.substringAfter('/')).absolutePath}/%'"
        }
    }

    private fun readLocation(context: Context, uri: Uri): Pair<Double, Double>? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val exif = ExifInterface(descriptor.fileDescriptor)
            exif.latLong?.let { it[0].toDouble() to it[1].toDouble() }
        }
    } catch (_: Exception) {
        null
    }

    private fun hasBakedStamp(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            ExifInterface(descriptor.fileDescriptor).getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION) == STAMP_MARKER
        } ?: false
    } catch (_: Exception) {
        false
    }

    private fun isStillAvailable(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: Exception) {
        false
    }

    private const val STAMP_MARKER = "GPS Camera stamped"
}
