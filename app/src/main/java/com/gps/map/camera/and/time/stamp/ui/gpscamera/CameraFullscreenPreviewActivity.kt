package com.gps.map.camera.and.time.stamp.ui.gpscamera

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gps.map.camera.and.time.stamp.R
import java.io.File

class CameraFullscreenPreviewActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PREVIEW_PATH = "gps_camera_fullscreen_preview_path"
    }

    private var previewFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_fullscreen_preview)

        val path = intent.getStringExtra(EXTRA_PREVIEW_PATH)
        if (path.isNullOrBlank()) {
            finish()
            return
        }
        previewFile = File(path)
        findViewById<ImageView>(R.id.fullscreen_preview_image).setImageURI(android.net.Uri.fromFile(previewFile))
        findViewById<ImageButton>(R.id.fullscreen_preview_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.fullscreen_preview_save).setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onDestroy() {
        previewFile?.delete()
        super.onDestroy()
    }
}
