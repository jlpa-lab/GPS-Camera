package com.gps.map.camera.and.time.stamp.ui.gpsvideo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ads.AdRemoteConfig
import com.gps.map.camera.and.time.stamp.ads.AdsManager
import com.gps.map.camera.and.time.stamp.ads.banner_all
import com.gps.map.camera.and.time.stamp.ui.gallery.GalleryCaptureType
import com.gps.map.camera.and.time.stamp.ui.gallery.MediaShareHelper

/** Result screen shown after a GPS video has been burned and saved. */
class GpsVideoSavedActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_VIDEO_URI = "gps_video_saved_uri"
    }

    private lateinit var savedVideoUri: Uri
    private lateinit var player: VideoView
    private lateinit var playButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gps_video_saved)
        loadBanner()

        val uriText = intent.getStringExtra(EXTRA_VIDEO_URI)
        if (uriText.isNullOrBlank()) {
            finish()
            return
        }
        savedVideoUri = Uri.parse(uriText)
        player = findViewById(R.id.gps_video_saved_player)
        playButton = findViewById(R.id.gps_video_saved_play)
        player.setVideoURI(savedVideoUri)
        player.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = false
            mediaPlayer.start()
            playButton.setText(R.string.gps_video_pause)
        }
        player.setOnCompletionListener {
            playButton.setText(R.string.gps_video_play)
        }
        player.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, R.string.gps_video_save_failed, Toast.LENGTH_SHORT).show()
            true
        }

        findViewById<View>(R.id.gps_video_saved_back).setOnClickListener { finish() }
        playButton.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
                playButton.setText(R.string.gps_video_play)
            } else {
                player.start()
                playButton.setText(R.string.gps_video_pause)
            }
        }
        findViewById<TextView>(R.id.gps_video_saved_share).setOnClickListener { shareVideo() }
        findViewById<TextView>(R.id.gps_video_saved_record_again).setOnClickListener {
            startActivity(Intent(this, GpsVideoActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(GpsVideoActivity.EXTRA_START_FRESH, true)
            })
            finish()
        }
    }

    private fun loadBanner() {
        AdsManager.loadBanner(
            this,
            AdRemoteConfig.banner_all,
            findViewById(R.id.fr_banner),
            isCollapse = false
        )
    }

    private fun shareVideo() {
        try {
            startActivity(
                Intent.createChooser(
                    MediaShareHelper.createShareIntent(savedVideoUri, GalleryCaptureType.VIDEO),
                    getString(R.string.gps_video_share)
                )
            )
        } catch (_: Exception) {
            Toast.makeText(this, R.string.gps_video_share_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
