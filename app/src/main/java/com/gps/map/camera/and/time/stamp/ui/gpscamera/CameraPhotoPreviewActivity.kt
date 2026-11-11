package com.gps.map.camera.and.time.stamp.ui.gpscamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gps.map.camera.and.time.stamp.R

/** Shows the raw capture before the user proceeds to the GPS stamp editor. */
class CameraPhotoPreviewActivity : AppCompatActivity() {
    private lateinit var photoUri: Uri
    private lateinit var photoView: ImageView
    private lateinit var retakeButton: ImageButton
    private lateinit var nextButton: TextView
    private var latitude: Double = Double.NaN
    private var longitude: Double = Double.NaN
    private var capturedAt: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_preview)

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

        findViewById<ImageButton>(R.id.preview_back_button).setOnClickListener { finish() }
        retakeButton.setOnClickListener {
            contentResolver.delete(photoUri, null, null)
            finish()
        }
        nextButton.setOnClickListener {
            startActivity(Intent(this, CameraStampEditorActivity::class.java).apply {
                putExtra(CameraActivity.EXTRA_PHOTO_URI, photoUri.toString())
                putExtra(CameraActivity.EXTRA_LATITUDE, latitude)
                putExtra(CameraActivity.EXTRA_LONGITUDE, longitude)
                putExtra(CameraActivity.EXTRA_CAPTURED_AT, capturedAt)
            })
            finish()
        }
    }
}
