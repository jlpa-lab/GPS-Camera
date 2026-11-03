package com.gps.map.camera.and.time.stamp.ui.earthview

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import earth.worldwind.WorldWindow

class EarthWorldWindow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WorldWindow(context, attrs) {

    init {
        setPreserveEGLContextOnPause(true)
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
        engine.renderResourceCache.remoteRetrievalQueueSize = 12
        engine.renderResourceCache.localRetrievalQueueSize = 8
    }
}
