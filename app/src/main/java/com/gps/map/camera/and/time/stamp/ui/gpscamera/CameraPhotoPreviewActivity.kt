package com.gps.map.camera.and.time.stamp.ui.gpscamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ads.AdRemoteConfig
import com.gps.map.camera.and.time.stamp.ads.AdsManager
import com.gps.map.camera.and.time.stamp.ads.banner_all
import com.gps.map.camera.and.time.stamp.ui.camera.CameraActivity

/** Shows the raw capture before the user proceeds to the GPS stamp editor. */
class CameraPhotoPreviewActivity : AppCompatActivity() {
    private lateinit var photoUri: Uri
    private lateinit var photoView: ImageView
    private lateinit var retakeButton: Button
    private lateinit var nextButton: Button
    private var latitude: Double = Double.NaN
    private var longitude: Double = Double.NaN
    private var capturedAt: String = ""
    private var toolsCameraMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toolsCameraMode = intent.getBooleanExtra(CameraActivity.EXTRA_TOOLS_CAMERA, false)
        setContentView(if (toolsCameraMode) R.layout.activity_tools_camera_preview else R.layout.activity_camera_preview)
        AdsManager.loadInterAll(this, ignoreLimit = true)
        if(toolsCameraMode) { loadPhotoPreviewBanner()}
        val photoUriText = intent.getStringExtra(CameraActivity.EXTRA_PHOTO_URI)
        if (photoUriText == null) {
            finish()
            return
        }
        photoUri = Uri.parse(photoUriText)
        latitude = intent.getDoubleExtra(CameraActivity.EXTRA_LATITUDE, Double.NaN)
        longitude = intent.getDoubleExtra(CameraActivity.EXTRA_LONGITUDE, Double.NaN)
        capturedAt = intent.getStringExtra(CameraActivity.EXTRA_CAPTURED_AT).orEmpty()

        photoView = findViewById(R.id.preview_image)
        photoView.setImageURI(photoUri)
        retakeButton = findViewById(R.id.preview_retake_button)
        nextButton = findViewById(R.id.preview_next_button)
        findViewById<TextView>(R.id.preview_title).setText(
            if (toolsCameraMode) R.string.camera_preview_title else R.string.preview_title
        )
        if (toolsCameraMode) nextButton.setText(R.string.add_gps_stamp)

        findViewById<ImageButton>(R.id.preview_back_button).setOnClickListener { finish() }
        retakeButton.setOnClickListener {
            contentResolver.delete(photoUri, null, null)
            finish()
        }

        nextButton.setOnClickListener {
            val openStampEditor = {
                startActivity(Intent(this, CameraStampEditorActivity::class.java).apply {
                    putExtra(CameraActivity.EXTRA_PHOTO_URI, photoUri.toString())
                    putExtra(CameraActivity.EXTRA_LATITUDE, latitude)
                    putExtra(CameraActivity.EXTRA_LONGITUDE, longitude)
                    putExtra(CameraActivity.EXTRA_CAPTURED_AT, capturedAt)
                    putExtra(CameraActivity.EXTRA_TOOLS_CAMERA, toolsCameraMode)
                })
                finish()
            }

            if (toolsCameraMode) {
                AdsManager.showInterAll(this, onAction = openStampEditor)
            } else {
                openStampEditor()
            }
        }
    }

    private fun loadPhotoPreviewBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, findViewById(R.id.fr_banner), isCollapse = false
        )
    }
}
