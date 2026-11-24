package com.gps.map.camera.and.time.stamp.ui.gpscamera

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.gps.map.camera.and.time.stamp.R
import java.io.File

class CameraSavingActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PREVIEW_PATH = "gps_camera_saving_preview_path"
        private var current: CameraSavingActivity? = null

        fun finishSaving() {
            current?.runOnUiThread { current?.finish() }
        }
    }

    private var previewFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        setContentView(R.layout.activity_camera_saving)

        val path = intent.getStringExtra(EXTRA_PREVIEW_PATH)
        if (path.isNullOrBlank()) {
            finish()
            return
        }
        previewFile = File(path)
        findViewById<ImageView>(R.id.saving_photo_image).setImageURI(Uri.fromFile(previewFile))
        findViewById<ImageButton>(R.id.saving_photo_back).setOnClickListener { }
    }

    override fun onDestroy() {
        if (current === this) current = null
        previewFile?.delete()
        super.onDestroy()
    }
}
