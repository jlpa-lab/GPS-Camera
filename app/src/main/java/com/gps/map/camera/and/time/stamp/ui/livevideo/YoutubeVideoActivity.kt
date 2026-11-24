package com.gps.map.camera.and.time.stamp.ui.livevideo

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.gps.map.camera.and.time.stamp.R

/** Detail screen and player for a selected live camera. */
class YoutubeVideoActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_VIDEO_ID = "live_video.youtube_id"
        const val EXTRA_VIDEO_TITLE = "live_video.title"
        const val EXTRA_LOCATION = "live_video.location"
        const val EXTRA_PIN_ID = "live_video.pin_id"
    }

    private lateinit var webView: WebView
    private lateinit var previewImage: ImageView
    private lateinit var previewPlay: ImageView
    private lateinit var loading: View
    private lateinit var pauseIcon: ImageView
    private lateinit var pauseLabel: TextView
    private lateinit var muteIcon: ImageView
    private lateinit var muteLabel: TextView
    private lateinit var locationLabel: TextView
    private lateinit var reconnectButton: TextView
    private lateinit var exploreButton: TextView

    private var videoId: String = ""
    private var cameraTitle: String = ""
    private var cameraLocation: String = ""
    private var isPlaying = false
    private var isPaused = false
    private var isMuted = true
    private var streamInterrupted = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_video)

        val title = intent.getStringExtra(EXTRA_VIDEO_TITLE).orEmpty()
        videoId = normalizeYoutubeId(intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()).orEmpty()
        if (videoId.isBlank()) {
            Toast.makeText(this, R.string.invalid_youtube_link, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<ImageButton>(R.id.youtube_video_back).setOnClickListener { returnToLiveVideo() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = returnToLiveVideo()
        })
        cameraTitle = title
        cameraLocation = intent.getStringExtra(EXTRA_LOCATION).orEmpty().ifBlank { fallbackLocation(title) }
        findViewById<TextView>(R.id.youtube_video_title).text = title
        locationLabel = findViewById(R.id.youtube_video_location)
        locationLabel.text = "LIVE • $cameraLocation"

        previewImage = findViewById(R.id.youtube_preview_image)
        previewPlay = findViewById(R.id.youtube_preview_play)
        loading = findViewById(R.id.youtube_loading)
        pauseIcon = findViewById(R.id.youtube_pause_icon)
        pauseLabel = findViewById(R.id.youtube_pause_label)
        muteIcon = findViewById(R.id.youtube_mute_icon)
        muteLabel = findViewById(R.id.youtube_mute_label)
        reconnectButton = findViewById(R.id.youtube_reconnect_button)
        exploreButton = findViewById(R.id.youtube_explore_cameras)
        webView = findViewById(R.id.youtube_web_view)

        Glide.with(this)
            .load("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
            .placeholder(R.drawable.bg_live_video_black)
            .error(R.drawable.bg_live_video_black)
            .centerCrop()
            .into(previewImage)

        previewPlay.setOnClickListener { startPlayback() }
        findViewById<View>(R.id.youtube_pause_button).setOnClickListener { togglePause() }
        findViewById<View>(R.id.youtube_mute_button).setOnClickListener { toggleMute() }
        findViewById<View>(R.id.youtube_fullscreen_button).setOnClickListener { openFullscreen() }
        reconnectButton.setOnClickListener { startPlayback() }
        exploreButton.setOnClickListener { returnToLiveVideo() }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) showPlaybackError()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loading.visibility = View.GONE
                checkStreamAvailability()
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true) showPlaybackError()
            }

            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                showPlaybackError()
                return true
            }
        }
        updateControlState()
    }

    @SuppressLint("JavascriptInterface")
    private fun startPlayback() {
        if (isPlaying) return
        isPlaying = true
        isPaused = false
        isMuted = true
        streamInterrupted = false
        previewImage.visibility = View.GONE
        previewPlay.visibility = View.GONE
        webView.visibility = View.VISIBLE
        loading.visibility = View.VISIBLE
        locationLabel.text = "LIVE • $cameraLocation"
        reconnectButton.visibility = View.GONE
        exploreButton.setText(R.string.live_camera_explore)
        updateControlState()

        val embedUrl = "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&enablejsapi=1&mute=1"
        val html = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
            <style>html,body{margin:0;width:100%;height:100%;background:#000;overflow:hidden}iframe{width:100%;height:100%;border:0}</style>
            </head><body><iframe src="$embedUrl" title="Live camera" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe></body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
    }

    private fun togglePause() {
        if (!isPlaying) {
            startPlayback()
            return
        }
        if (isPaused) {
            sendPlayerCommand("playVideo")
            isPaused = false
        } else {
            sendPlayerCommand("pauseVideo")
            isPaused = true
        }
        updateControlState()
    }

    private fun toggleMute() {
        if (!isPlaying) startPlayback()
        isMuted = !isMuted
        sendPlayerCommand(if (isMuted) "mute" else "unMute")
        updateControlState()
    }

    private fun openFullscreen() {
        startActivity(Intent(this, LiveCameraFullscreenActivity::class.java).apply {
            putExtra(EXTRA_VIDEO_ID, videoId)
            putExtra(EXTRA_VIDEO_TITLE, cameraTitle)
            putExtra(EXTRA_LOCATION, cameraLocation)
        })
    }

    private fun sendPlayerCommand(command: String) {
        val script = "document.querySelector('iframe').contentWindow.postMessage('{\"event\":\"command\",\"func\":\"$command\",\"args\":\"\"}', '*');"
        webView.evaluateJavascript(script, null)
    }

    private fun showPlaybackError() {
        if (streamInterrupted) return
        streamInterrupted = true
        loading.visibility = View.GONE
        webView.visibility = View.GONE
        previewImage.visibility = View.VISIBLE
        previewPlay.visibility = View.VISIBLE
        isPlaying = false
        isPaused = false
        locationLabel.text = getString(R.string.live_camera_stream_interrupted)
        reconnectButton.visibility = View.VISIBLE
        exploreButton.setText(R.string.live_camera_other_cameras)
        updateControlState()
    }

    private fun updateControlState() {
        val showPause = isPlaying && !isPaused
        pauseIcon.setImageResource(if (showPause) R.drawable.ic_pause else R.drawable.ic_play_live)
        pauseLabel.setText(if (showPause) R.string.live_camera_pause else R.string.live_camera_play)
        muteIcon.setImageResource(if (isMuted) R.drawable.ic_volume_off_live else R.drawable.ic_volume_on_live)
        muteLabel.setText(if (isMuted) R.string.live_camera_muted else R.string.live_camera_unmuted)
    }

    private fun checkStreamAvailability(attempt: Int = 0) {
        if (!isPlaying || streamInterrupted || isFinishing) return
        val script = """
            (function(){
                var text = document.body ? document.body.innerText : '';
                try {
                    var frame = document.querySelector('iframe');
                    if (frame && frame.contentDocument && frame.contentDocument.body) {
                        text += ' ' + frame.contentDocument.body.innerText;
                    }
                } catch (e) {}
                return text;
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            val message = result.orEmpty().lowercase()
            val unavailable = message.contains("video unavailable") ||
                message.contains("live stream recording is not available") ||
                message.contains("this video is unavailable")
            if (unavailable) {
                showPlaybackError()
            } else if (attempt < 8 && isPlaying && !streamInterrupted) {
                webView.postDelayed({ checkStreamAvailability(attempt + 1) }, 1000L)
            }
        }
    }

    private fun fallbackLocation(title: String): String {
        val value = title.lowercase()
        return when {
            "new york" in value || "manhattan" in value -> "New York, USA"
            "chicago" in value -> "Chicago, USA"
            "tokyo" in value -> "Tokyo, Japan"
            "sydney" in value -> "Sydney, Australia"
            "dubai" in value -> "Dubai, UAE"
            title.contains(",") -> title.substringAfterLast(",").trim()
            else -> "Worldwide"
        }
    }

    private fun returnToLiveVideo() {
        if (isTaskRoot) startActivity(Intent(this, LiveVideoActivity::class.java))
        finish()
    }

    private fun normalizeYoutubeId(value: String): String? {
        val trimmed = value.trim()
        val id = when {
            trimmed.contains("youtu.be/") -> trimmed.substringAfter("youtu.be/").substringBefore('?').substringBefore('/')
            trimmed.contains("youtube.com/watch") -> Uri.parse(trimmed).getQueryParameter("v")
            trimmed.contains("youtube.com/embed/") -> trimmed.substringAfter("/embed/").substringBefore('?').substringBefore('/')
            else -> trimmed
        }
        return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,20}")) }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }
}
