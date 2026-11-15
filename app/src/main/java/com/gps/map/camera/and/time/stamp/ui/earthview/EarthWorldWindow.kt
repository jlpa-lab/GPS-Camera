package com.gps.map.camera.and.time.stamp.ui.earthview

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import earth.worldwind.WorldWindow
import earth.worldwind.render.RenderResourceCache
import earth.worldwind.util.http.httpClientCustomizer
import io.ktor.client.plugins.HttpRequestRetry

class EarthWorldWindow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WorldWindow(
    context,
    attrs,
    renderResourceCache = RenderResourceCache(context, renderCacheCapacity(context))
) {

    init {
        setPreserveEGLContextOnPause(true)
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
        engine.renderResourceCache.remoteRetrievalQueueSize = 6
        engine.renderResourceCache.localRetrievalQueueSize = 8

        // ImageSource URL retrieval is WorldWind's supported path for WMS tiles.
        // Retry transient transport/server failures before the tile is marked absent.
        httpClientCustomizer = {
            install(HttpRequestRetry) {
                retryOnException(maxRetries = 2)
                retryOnServerErrors(maxRetries = 2)
                exponentialDelay()
            }
        }
    }

    private companion object {
        fun renderCacheCapacity(context: Context): Long {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val appMemoryBytes = (activityManager?.memoryClass ?: 256) * 1024L * 1024L
            return (appMemoryBytes / 6L)
                .coerceIn(64L * 1024L * 1024L, 128L * 1024L * 1024L)
        }
    }
}
