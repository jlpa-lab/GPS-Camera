package com.gps.map.camera.and.time.stamp.ui.gpscamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ads.AdRemoteConfig
import com.gps.map.camera.and.time.stamp.ads.AdsManager
import com.gps.map.camera.and.time.stamp.ads.banner_all
import com.gps.map.camera.and.time.stamp.ui.camera.CameraActivity
import com.gps.map.camera.and.time.stamp.ui.gallery.GalleryCaptureType
import com.gps.map.camera.and.time.stamp.ui.gallery.MediaShareHelper

/** Final preview shown after the GPS stamp has been saved. */
class CameraSavedPhotoActivity : AppCompatActivity() {
    private lateinit var savedPhotoUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_saved_photo)
        loadSavedPhotoBanner()
        val photoUriText = intent.getStringExtra(CameraActivity.EXTRA_PHOTO_URI)
        if (photoUriText.isNullOrBlank()) {
            finish()
            return
        }
        savedPhotoUri = Uri.parse(photoUriText)
        findViewById<ImageView>(R.id.saved_photo_image).setImageURI(savedPhotoUri)

        findViewById<ImageButton>(R.id.saved_photo_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.saved_photo_share).setOnClickListener { shareWithChooser() }
        findViewById<TextView>(R.id.saved_photo_take_another).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
            finish()
        }
    }
    private fun loadSavedPhotoBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, findViewById(R.id.fr_banner), isCollapse = false
        )
    }
    private fun shareWithChooser() {
        try {
            startActivity(Intent.createChooser(createShareIntent(), getString(R.string.share_photo)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.photo_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createShareIntent() =
        MediaShareHelper.createShareIntent(savedPhotoUri, GalleryCaptureType.PHOTO)
}
