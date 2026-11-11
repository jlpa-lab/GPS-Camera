package com.gps.map.camera.and.time.stamp.ui.livevideo

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gps.map.camera.and.time.stamp.R

/** Plays the YouTube live stream selected from the live-camera map. */
class YoutubeVideoActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_VIDEO_ID = "live_video.youtube_id"
        const val EXTRA_VIDEO_TITLE = "live_video.title"
        const val EXTRA_PIN_ID = "live_video.pin_id"
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_video)

        val title = intent.getStringExtra(EXTRA_VIDEO_TITLE).orEmpty()
        val videoId = normalizeYoutubeId(intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty())
        if (videoId == null) {
            Toast.makeText(this, R.string.invalid_youtube_link, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<TextView>(R.id.youtube_video_title).text = title
        findViewById<ImageButton>(R.id.youtube_video_back).setOnClickListener { finish() }
        webView = findViewById(R.id.youtube_web_view)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    findViewById<View>(R.id.youtube_loading).visibility = View.GONE
                    Toast.makeText(this@YoutubeVideoActivity, R.string.youtube_load_error, Toast.LENGTH_LONG).show()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                findViewById<View>(R.id.youtube_loading).visibility = View.GONE
            }
        }

        val embedUrl = "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&rel=0"
        val html = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
            <style>html,body{margin:0;width:100%;height:100%;background:#000;overflow:hidden}iframe{width:100%;height:100%;border:0}</style>
            </head><body><iframe src="$embedUrl" title="Live video" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe></body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
    }

    private fun normalizeYoutubeId(value: String): String? {
        val trimmed = value.trim()
        val id = when {
            trimmed.contains("youtu.be/") -> trimmed.substringAfter("youtu.be/").substringBefore('?').substringBefore('/')
            trimmed.contains("youtube.com/watch") -> android.net.Uri.parse(trimmed).getQueryParameter("v")
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
