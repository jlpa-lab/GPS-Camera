package com.gps.map.camera.and.time.stamp.ui.gpsvideo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.graphics.SurfaceTexture
import android.view.Surface
import com.gps.map.camera.and.time.stamp.ui.location.LocationFormatter
import com.gps.map.camera.and.time.stamp.ui.settings.StampTemplate
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VideoStampData(
    val place: String,
    val latitude: Double?,
    val longitude: Double?,
    val recordedAtMillis: Long,
    val template: StampTemplate
)

/** Re-encodes a recorded clip with the same GPS stamp burned into every frame. */
object VideoStampBurner {
    fun burn(input: File, output: File, stamp: VideoStampData): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var renderer: FrameRenderer? = null
        var muxer: MediaMuxer? = null
        var audioExtractor: MediaExtractor? = null
        var lastStampBitmap: Bitmap? = null
        var lastStampSecond = Long.MIN_VALUE

        return try {
            extractor.setDataSource(input.absolutePath)
            val videoTrack = findTrack(extractor, "video/") ?: return false
            val videoFormat = extractor.getTrackFormat(videoTrack)
            val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val frameRate = if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
            } else 30
            val videoMime = videoFormat.getString(MediaFormat.KEY_MIME) ?: return false

            audioExtractor = MediaExtractor().apply {
                setDataSource(input.absolutePath)
            }
            val audioTrack = findTrack(audioExtractor, "audio/")
            val audioFormat = audioTrack?.let { audioExtractor.getTrackFormat(it) }

            val encodeFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfoSurface.COLOR_FORMAT_SURFACE)
                setInteger(MediaFormat.KEY_BIT_RATE, width * height * 4)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encoderSurface = encoder.createInputSurface()
            renderer = FrameRenderer(width, height, encoderSurface)
            encoder.start()

            decoder = MediaCodec.createDecoderByType(videoMime)
            decoder.configure(videoFormat, renderer.decoderSurface, null, 0)
            decoder.start()
            extractor.selectTrack(videoTrack)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoMuxerTrack = -1
            var audioMuxerTrack = -1
            var muxerStarted = false
            var inputFinished = false
            var decodeFinished = false
            val decoderInfo = MediaCodec.BufferInfo()
            val encoderInfo = MediaCodec.BufferInfo()

            fun startMuxerIfReady() {
                if (muxerStarted || videoMuxerTrack < 0) return
                if (audioFormat != null && audioMuxerTrack < 0) {
                    audioMuxerTrack = muxer!!.addTrack(audioFormat)
                }
                muxer!!.start()
                muxerStarted = true
                audioExtractor?.selectTrack(audioTrack ?: -1)
            }

            fun writeAudioUntil(presentationTimeUs: Long) {
                if (!muxerStarted || audioMuxerTrack < 0 || audioExtractor == null) return
                val audio = audioExtractor!!
                while (true) {
                    val sampleTime = audio.sampleTime
                    if (sampleTime < 0 || sampleTime > presentationTimeUs) return
                    val buffer = ByteBufferHolder.ensure(audio)
                    val sampleSize = audio.readSampleData(buffer, 0)
                    if (sampleSize < 0) return
                    val info = MediaCodec.BufferInfo()
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = sampleTime
                    info.flags = audio.sampleFlags
                    buffer.limit(sampleSize)
                    muxer!!.writeSampleData(audioMuxerTrack, buffer, info)
                    audio.advance()
                }
            }

            fun drainEncoder(endOfStream: Boolean = false): Boolean {
                if (endOfStream) encoder!!.signalEndOfInputStream()
                while (true) {
                    when (val index = encoder!!.dequeueOutputBuffer(encoderInfo, 10_000)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> return !endOfStream
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoMuxerTrack = muxer!!.addTrack(encoder!!.outputFormat)
                            startMuxerIfReady()
                        }
                        else -> if (index >= 0) {
                            if (encoderInfo.size > 0 && muxerStarted) {
                                writeAudioUntil(encoderInfo.presentationTimeUs)
                                val output = encoder!!.getOutputBuffer(index) ?: return false
                                output.position(encoderInfo.offset)
                                output.limit(encoderInfo.offset + encoderInfo.size)
                                muxer!!.writeSampleData(videoMuxerTrack, output, encoderInfo)
                            }
                            val finished = encoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            encoder!!.releaseOutputBuffer(index, false)
                            if (finished) return false
                        }
                    }
                }
            }

            while (!decodeFinished) {
                if (!inputFinished) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex) ?: return false
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputFinished = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(decoderInfo, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val eos = decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (decoderInfo.size > 0) {
                            decoder.releaseOutputBuffer(outputIndex, true)
                            renderer.awaitFrame()
                            val stampSecond = decoderInfo.presentationTimeUs / 1_000_000L
                            if (lastStampBitmap == null || stampSecond != lastStampSecond) {
                                lastStampBitmap?.recycle()
                                lastStampBitmap = createStamp(width, height, stamp, decoderInfo.presentationTimeUs)
                                lastStampSecond = stampSecond
                            }
                            renderer.draw(lastStampBitmap!!, decoderInfo.presentationTimeUs)
                            drainEncoder()
                        } else {
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                        if (eos) decodeFinished = true
                    }
                }
            }

            drainEncoder(endOfStream = true)
            writeAudioUntil(Long.MAX_VALUE)
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { renderer?.release() }
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            runCatching { audioExtractor?.release() }
            runCatching { lastStampBitmap?.recycle() }
            if (!output.exists() || output.length() == 0L) output.delete()
        }
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(prefix)) return index
        }
        return null
    }

    private fun createStamp(width: Int, height: Int, data: VideoStampData, presentationTimeUs: Long): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = width / 720f
        val margin = 28f * scale
        val panelHeight = if (data.template == StampTemplate.MINIMAL) 170f else 220f
        val panel = RectF(
            margin,
            height - panelHeight * scale - margin,
            width - margin,
            height - margin
        )
        canvas.drawRoundRect(panel, 22f * scale, 22f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x9907111D.toInt() })

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF7EBCFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 5f * scale
        }
        val pinX = panel.left + 42f * scale
        val pinY = panel.top + 48f * scale
        canvas.drawCircle(pinX, pinY, 15f * scale, pinPaint)
        val pinPath = Path().apply {
            moveTo(pinX - 12f * scale, pinY + 8f * scale)
            lineTo(pinX, pinY + 27f * scale)
            lineTo(pinX + 12f * scale, pinY + 8f * scale)
        }
        canvas.drawPath(pinPath, pinPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
            textSize = 26f * scale
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD4DEEA.toInt()
            textSize = 23f * scale
        }
        val x = panel.left + 82f * scale
        canvas.drawText(data.place, x, panel.top + 58f * scale, titlePaint)
        val coordinates = if (data.latitude == null || data.longitude == null) {
            "Locating…"
        } else {
            "${LocationFormatter.latitude(data.latitude)}  ${LocationFormatter.longitude(data.longitude)}"
        }
        canvas.drawText(coordinates, x, panel.top + 100f * scale, bodyPaint)
        if (data.template != StampTemplate.MINIMAL) {
            val time = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.US)
                .format(Date(data.recordedAtMillis + presentationTimeUs / 1_000L))
            canvas.drawText(time, x, panel.top + 140f * scale, bodyPaint)
        }
        return bitmap
    }

    private object MediaCodecInfoSurface {
        const val COLOR_FORMAT_SURFACE = 0x7F000789
    }

    private object ByteBufferHolder {
        private var buffer: java.nio.ByteBuffer? = null
        fun ensure(extractor: MediaExtractor): java.nio.ByteBuffer {
            val format = extractor.getTrackFormat(0)
            val needed = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                1024 * 1024
            }
            if (buffer == null || buffer!!.capacity() < needed) {
                buffer = java.nio.ByteBuffer.allocate(needed)
            }
            buffer!!.clear()
            return buffer!!
        }
    }

    private class FrameRenderer(private val width: Int, private val height: Int, encoderSurface: Surface) {
        private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val context: android.opengl.EGLContext
        private val window: android.opengl.EGLSurface
        private val textureId: Int
        private val overlayTexture: Int
        private val surfaceTexture: SurfaceTexture
        val decoderSurface: Surface
        private var frameAvailable = false
        private val frameLock = Object()
        private var currentOverlay: Bitmap? = null
        private val externalProgram: Int
        private val overlayProgram: Int

        init {
            EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0)
            val config = chooseConfig()
            context = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0
            )
            window = EGL14.eglCreateWindowSurface(display, config, encoderSurface, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(display, window, window, context)
            textureId = createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            overlayTexture = createTexture(GLES20.GL_TEXTURE_2D)
            surfaceTexture = SurfaceTexture(textureId)
            surfaceTexture.setOnFrameAvailableListener {
                synchronized(frameLock) {
                    frameAvailable = true
                    frameLock.notifyAll()
                }
            }
            decoderSurface = Surface(surfaceTexture)
            externalProgram = buildProgram(EXTERNAL_VERTEX, EXTERNAL_FRAGMENT)
            overlayProgram = buildProgram(OVERLAY_VERTEX, OVERLAY_FRAGMENT)
        }

        fun awaitFrame() {
            synchronized(frameLock) {
                while (!frameAvailable) frameLock.wait(5_000L)
                frameAvailable = false
            }
            surfaceTexture.updateTexImage()
        }

        fun draw(overlay: Bitmap, presentationTimeUs: Long) {
            EGL14.eglMakeCurrent(display, window, window, context)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            drawExternalFrame()
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexture)
            if (overlay != currentOverlay) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, overlay, 0)
                currentOverlay = overlay
            }
            drawOverlay()
            EGLExt.eglPresentationTimeANDROID(display, window, presentationTimeUs * 1_000L)
            EGL14.eglSwapBuffers(display, window)
        }

        fun release() {
            decoderSurface.release()
            surfaceTexture.release()
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, window)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }

        private fun drawExternalFrame() {
            GLES20.glUseProgram(externalProgram)
            val position = GLES20.glGetAttribLocation(externalProgram, "aPosition")
            val texture = GLES20.glGetAttribLocation(externalProgram, "aTexCoord")
            val matrix = GLES20.glGetUniformLocation(externalProgram, "uTexMatrix")
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glEnableVertexAttribArray(texture)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, FULL_QUAD)
            GLES20.glVertexAttribPointer(texture, 2, GLES20.GL_FLOAT, false, 0, FULL_TEX)
            val transform = FloatArray(16)
            surfaceTexture.getTransformMatrix(transform)
            GLES20.glUniformMatrix4fv(matrix, 1, false, transform, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(position)
            GLES20.glDisableVertexAttribArray(texture)
        }

        private fun drawOverlay() {
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glUseProgram(overlayProgram)
            val position = GLES20.glGetAttribLocation(overlayProgram, "aPosition")
            val texture = GLES20.glGetAttribLocation(overlayProgram, "aTexCoord")
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glEnableVertexAttribArray(texture)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, FULL_QUAD)
            GLES20.glVertexAttribPointer(texture, 2, GLES20.GL_FLOAT, false, 0, FULL_TEX)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexture)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(position)
            GLES20.glDisableVertexAttribArray(texture)
            GLES20.glDisable(GLES20.GL_BLEND)
        }

        private fun chooseConfig(): android.opengl.EGLConfig {
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            val attributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, 4,
                EGL14.EGL_NONE
            )
            EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)
            return configs[0]!!
        }

        private fun createTexture(target: Int): Int {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            GLES20.glBindTexture(target, textures[0])
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            return textures[0]
        }

        private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
            fun compile(type: Int, source: String): Int {
                val shader = GLES20.glCreateShader(type)
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                return shader
            }
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, vertexSource))
            GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource))
            GLES20.glLinkProgram(program)
            return program
        }

        companion object {
            private val FULL_QUAD = java.nio.ByteBuffer.allocateDirect(8 * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).position(0)
            }
            private val FULL_TEX = java.nio.ByteBuffer.allocateDirect(8 * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)).position(0)
            }
            private const val EXTERNAL_VERTEX = """
                attribute vec4 aPosition;
                attribute vec4 aTexCoord;
                uniform mat4 uTexMatrix;
                varying vec2 vTexCoord;
                void main() { gl_Position = aPosition; vTexCoord = (uTexMatrix * aTexCoord).xy; }
            """
            private const val EXTERNAL_FRAGMENT = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTexCoord;
                uniform samplerExternalOES sTexture;
                void main() { gl_FragColor = texture2D(sTexture, vTexCoord); }
            """
            private const val OVERLAY_VERTEX = """
                attribute vec4 aPosition;
                attribute vec4 aTexCoord;
                varying vec2 vTexCoord;
                void main() { gl_Position = aPosition; vTexCoord = aTexCoord.xy; }
            """
            private const val OVERLAY_FRAGMENT = """
                precision mediump float;
                varying vec2 vTexCoord;
                uniform sampler2D sTexture;
                void main() { gl_FragColor = texture2D(sTexture, vTexCoord); }
            """
        }
    }
}
