package com.gps.map.camera.and.time.stamp.ui.gpscamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gps.map.camera.and.time.stamp.R

/** Final preview shown after the GPS stamp has been saved. */
class CameraSavedPhotoActivity : AppCompatActivity() {
    private lateinit var savedPhotoUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_saved_photo)

        val photoUriText = intent.getStringExtra(CameraActivity.EXTRA_PHOTO_URI)
        if (photoUriText.isNullOrBlank()) {
            finish()
            return
        }
        savedPhotoUri = Uri.parse(photoUriText)
        findViewById<ImageView>(R.id.saved_photo_image).setImageURI(savedPhotoUri)

        findViewById<ImageButton>(R.id.saved_photo_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.saved_photo_delete).setOnClickListener { confirmDelete() }
        findViewById<TextView>(R.id.share_whatsapp).setOnClickListener { shareWithPackage("com.whatsapp") }
        findViewById<TextView>(R.id.share_facebook).setOnClickListener { shareWithPackage("com.facebook.katana") }
        findViewById<TextView>(R.id.share_instagram).setOnClickListener { shareWithPackage("com.instagram.android") }
        findViewById<TextView>(R.id.share_more).setOnClickListener { shareWithChooser() }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_photo_title)
            .setMessage(R.string.delete_photo_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val deletedRows = contentResolver.delete(savedPhotoUri, null, null)
                if (deletedRows > 0) finish()
                else Toast.makeText(this, R.string.photo_delete_failed, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun shareWithPackage(packageName: String) {
        val shareIntent = createShareIntent()
        try {
            shareIntent.setPackage(packageName)
            startActivity(shareIntent)
        } catch (_: Exception) {
            shareWithChooser()
        }
    }

    private fun shareWithChooser() {
        try {
            startActivity(Intent.createChooser(createShareIntent(), getString(R.string.share_photo)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.photo_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createShareIntent() = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, savedPhotoUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
