package com.gps.map.camera.and.time.stamp.ui.gallery

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

object MediaStoreImageSaver {
    fun saveBitmap(context: Context, bitmap: Bitmap, displayName: String): Uri =
        save(context, displayName) { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                "Could not encode image"
            }
        }

    fun saveFile(context: Context, imageFile: File, displayName: String): Uri =
        save(context, displayName) { output ->
            FileInputStream(imageFile).use { input -> input.copyTo(output) }
        }

    private fun save(context: Context, displayName: String, write: (java.io.OutputStream) -> Unit): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GPS Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val folder = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "GPS Camera"
                )
                check(folder.exists() || folder.mkdirs()) { "Could not create photo folder" }
                put(MediaStore.Images.Media.DATA, File(folder, displayName).absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val destination = context.contentResolver.insert(collection, values)
            ?: error("Could not create photo entry")
        try {
            val output = context.contentResolver.openOutputStream(destination, "w")
                ?: error("Could not open photo output")
            output.use(write)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(
                    destination,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null
                )
            }
            return destination
        } catch (error: Exception) {
            context.contentResolver.delete(destination, null, null)
            throw error
        }
    }
}
