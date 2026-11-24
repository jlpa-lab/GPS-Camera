package com.gps.map.camera.and.time.stamp.ui.livevideo

import android.annotation.SuppressLint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.gps.map.camera.and.time.stamp.R
import kotlin.math.abs

/** Landscape-only player opened by the Live Camera full-screen action. */
class LiveCameraFullscreenActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var preview: ImageView
    private lateinit var play: ImageView
    private lateinit var pauseIcon: ImageView
    private lateinit var pauseLabel: TextView
    private lateinit var muteIcon: ImageView
    private lateinit var muteLabel: TextView
    private lateinit var chromeHeader: View
    private lateinit var chromeControls: View

    private val chromeHandler = Handler(Looper.getMainLooper())
    private val hideChromeRunnable = Runnable { setChromeVisible(false) }

    private var videoId = ""
    private var isPlaying = false
    private var isPaused = false
    private var isMuted = true
    private var chromeVisible = true
    private var chromeVisibleAtDown = true
    private var downX = 0f
    private var downY = 0f

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        setContentView(R.layout.activity_live_camera_fullscreen)

        videoId = normalizeYoutubeId(intent.getStringExtra(YoutubeVideoActivity.EXTRA_VIDEO_ID).orEmpty()).orEmpty()
        if (videoId.isBlank()) {
            finish()
            return
        }

        val title = intent.getStringExtra(YoutubeVideoActivity.EXTRA_VIDEO_TITLE).orEmpty()
        findViewById<TextView>(R.id.live_camera_fullscreen_title).text = title
        chromeHeader = findViewById(R.id.live_camera_fullscreen_header)
        chromeControls = findViewById(R.id.live_camera_fullscreen_controls)
        findViewById<View>(R.id.live_camera_fullscreen_back).setOnClickListener { finish() }
        findViewById<View>(R.id.live_camera_fullscreen_exit).setOnClickListener { finish() }

        preview = findViewById(R.id.live_camera_fullscreen_preview)
        play = findViewById(R.id.live_camera_fullscreen_play)
        pauseIcon = findViewById(R.id.live_camera_fullscreen_pause_icon)
        pauseLabel = findViewById(R.id.live_camera_fullscreen_pause_label)
        muteIcon = findViewById(R.id.live_camera_fullscreen_mute_icon)
        muteLabel = findViewById(R.id.live_camera_fullscreen_mute_label)
        webView = findViewById(R.id.live_camera_fullscreen_web_view)

        Glide.with(this)
            .load("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
            .placeholder(R.drawable.bg_live_video_black)
            .error(R.drawable.bg_live_video_black)
            .centerCrop()
            .into(preview)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        play.setOnClickListener { startPlayback() }
        findViewById<View>(R.id.live_camera_fullscreen_pause).setOnClickListener { togglePause() }
        findViewById<View>(R.id.live_camera_fullscreen_mute).setOnClickListener { toggleMute() }
        updateControlState()
        showChrome()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                chromeVisibleAtDown = chromeVisible
                showChrome()
            }
            MotionEvent.ACTION_UP -> {
                val isTap = abs(event.rawX - downX) < 24f && abs(event.rawY - downY) < 24f
                if (isTap && chromeVisibleAtDown && !isPointInsideChrome(event.rawX, event.rawY)) {
                    hideChrome()
                } else {
                    showChrome()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun startPlayback() {
        if (isPlaying) return
        isPlaying = true
        isPaused = false
        isMuted = true
        preview.visibility = View.GONE
        play.visibility = View.GONE
        webView.visibility = View.VISIBLE
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

    private fun updateControlState() {
        val showPause = isPlaying && !isPaused
        pauseIcon.setImageResource(if (showPause) R.drawable.ic_pause else R.drawable.ic_play_live)
        pauseLabel.setText(if (showPause) R.string.live_camera_pause else R.string.live_camera_play)
        muteIcon.setImageResource(if (isMuted) R.drawable.ic_volume_off_live else R.drawable.ic_volume_on_live)
        muteLabel.setText(if (isMuted) R.string.live_camera_muted else R.string.live_camera_unmuted)
    }

    private fun showChrome() {
        setChromeVisible(true)
        chromeHandler.removeCallbacks(hideChromeRunnable)
        chromeHandler.postDelayed(hideChromeRunnable, 3500L)
    }

    private fun hideChrome() {
        chromeHandler.removeCallbacks(hideChromeRunnable)
        setChromeVisible(false)
    }

    private fun isPointInsideChrome(rawX: Float, rawY: Float): Boolean =
        isPointInside(chromeHeader, rawX, rawY) ||
            isPointInside(chromeControls, rawX, rawY) ||
            isPointInside(play, rawX, rawY)

    private fun isPointInside(view: View, rawX: Float, rawY: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false
        val bounds = Rect()
        view.getGlobalVisibleRect(bounds)
        return bounds.contains(rawX.toInt(), rawY.toInt())
    }

    private fun setChromeVisible(visible: Boolean) {
        chromeVisible = visible
        val visibility = if (visible) View.VISIBLE else View.GONE
        chromeHeader.visibility = visibility
        chromeControls.visibility = visibility
        if (!isPlaying) play.visibility = visibility
    }

    private fun sendPlayerCommand(command: String) {
        val script = "document.querySelector('iframe').contentWindow.postMessage('{\"event\":\"command\",\"func\":\"$command\",\"args\":\"\"}', '*');"
        webView.evaluateJavascript(script, null)
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
        chromeHandler.removeCallbacks(hideChromeRunnable)
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }
}
