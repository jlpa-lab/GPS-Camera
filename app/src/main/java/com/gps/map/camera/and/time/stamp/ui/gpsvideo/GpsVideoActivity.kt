package com.gps.map.camera.and.time.stamp.ui.gpsvideo

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.pref.AppSharedPreferencesApp
import com.gps.map.camera.and.time.stamp.ui.location.LocationFormatter
import com.gps.map.camera.and.time.stamp.utils.location.GpsLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** CameraX video capture with the same GPS source used by the map tools. */
class GpsVideoActivity : AppCompatActivity() {

    private enum class ScreenState { READY, RECORDING, PREVIEW, SAVE_FAILED }
    private enum class RecordingState { RECORDING, PAUSED }

    private lateinit var cameraPreview: PreviewView
    private lateinit var cameraContent: View
    private lateinit var readyTopBar: View
    private lateinit var recordingTopBar: View
    private lateinit var readyControls: View
    private lateinit var recordingControls: View
    private lateinit var timerPill: View
    private lateinit var timerDot: View
    private lateinit var previewContent: View
    private lateinit var failedContent: View
    private lateinit var stampPlace: TextView
    private lateinit var stampCoordinates: TextView
    private lateinit var stampTime: TextView
    private lateinit var previewStampPlace: TextView
    private lateinit var previewStampCoordinates: TextView
    private lateinit var previewStampTime: TextView
    private lateinit var flashButton: ImageButton
    private lateinit var recordButton: ImageButton
    private lateinit var qualityText: TextView
    private lateinit var timerText: TextView
    private lateinit var pauseButton: View
    private lateinit var pauseIcon: ImageView
    private lateinit var pauseLabel: TextView
    private lateinit var micButton: View
    private lateinit var micIcon: ImageView
    private lateinit var micLabel: TextView
    private lateinit var zoomButton: TextView
    private lateinit var player: VideoView
    private lateinit var positionText: TextView
    private lateinit var playButton: TextView
    private lateinit var soundButton: TextView
    private lateinit var galleryThumbnail: ImageView

    private val locationProvider by lazy { GpsLocationProvider(this) }
    private val preferences by lazy { AppSharedPreferencesApp(applicationContext) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val workExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var latestLocation: Location? = null
    private var placeName: String? = null
    private var screenState = ScreenState.READY
    private var recordingState = RecordingState.RECORDING
    private var recordingStartedAt = 0L
    private var recordingStartedWallClock = 0L
    private var pausedAt = 0L
    private var pausedTotal = 0L
    private var isMicEnabled = true
    private var isSoundEnabled = true
    private var previewPlayer: MediaPlayer? = null
    private var torchOn = false
    private var tempVideo: File? = null
    private var lastSavedVideo: Uri? = null
    private var currentZoom = 1f
    private var zoomLevels = listOf(1f)

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            showPermissionMessage(R.string.gps_video_camera_required, Manifest.permission.CAMERA)
            return@registerForActivityResult
        }
        startCamera()
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            isMicEnabled = false
            showPermissionMessage(R.string.gps_video_microphone_required, Manifest.permission.RECORD_AUDIO)
        }
        startLocationTracking()
    }

    private val clockTick = object : Runnable {
        override fun run() {
            updateStamp()
            if (!isFinishing) mainHandler.postDelayed(this, 1_000L)
        }
    }

    private val recordingTick = object : Runnable {
        override fun run() {
            if (screenState == ScreenState.RECORDING && recordingState == RecordingState.RECORDING) {
                updateRecordingControls()
            }
            mainHandler.postDelayed(this, 250L)
        }
    }

    private val playerTick = object : Runnable {
        override fun run() {
            if (::player.isInitialized && screenState == ScreenState.PREVIEW) {
                updatePlayerPosition()
                mainHandler.postDelayed(this, 250L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gps_video)
        bindViews()
        bindActions()
        restoreState(savedInstanceState)
        loadLastVideoThumbnail()
        setScreenState(screenState)
        requestMissingPermissions()
        mainHandler.post(clockTick)
        mainHandler.post(recordingTick)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })
    }

    private fun bindViews() {
        cameraPreview = findViewById(R.id.gps_video_preview)
        cameraContent = findViewById(R.id.gps_video_camera_content)
        readyTopBar = findViewById(R.id.gps_video_ready_top_bar)
        recordingTopBar = findViewById(R.id.gps_video_recording_top_bar)
        readyControls = findViewById(R.id.gps_video_ready_controls)
        recordingControls = findViewById(R.id.gps_video_recording_controls)
        timerPill = findViewById(R.id.gps_video_timer_pill)
        timerDot = findViewById(R.id.gps_video_timer_dot)
        previewContent = findViewById(R.id.gps_video_preview_content)
        failedContent = findViewById(R.id.gps_video_failed_content)
        stampPlace = findViewById(R.id.gps_video_place)
        stampCoordinates = findViewById(R.id.gps_video_coordinates)
        stampTime = findViewById(R.id.gps_video_time)
        previewStampPlace = findViewById(R.id.gps_video_preview_place)
        previewStampCoordinates = findViewById(R.id.gps_video_preview_coordinates)
        previewStampTime = findViewById(R.id.gps_video_preview_time)
        flashButton = findViewById(R.id.gps_video_flash)
        recordButton = findViewById(R.id.gps_video_record)
        qualityText = findViewById(R.id.gps_video_quality)
        timerText = findViewById(R.id.gps_video_timer)
        pauseButton = findViewById(R.id.gps_video_pause)
        pauseIcon = findViewById(R.id.gps_video_pause_icon)
        pauseLabel = findViewById(R.id.gps_video_pause_label)
        micButton = findViewById(R.id.gps_video_mic)
        micIcon = findViewById(R.id.gps_video_mic_icon)
        micLabel = findViewById(R.id.gps_video_mic_label)
        zoomButton = findViewById(R.id.gps_video_zoom)
        player = findViewById(R.id.gps_video_player)
        positionText = findViewById(R.id.gps_video_position)
        playButton = findViewById(R.id.gps_video_play)
        soundButton = findViewById(R.id.gps_video_sound)
        galleryThumbnail = findViewById(R.id.gps_video_gallery_thumbnail)
    }

    private fun bindActions() {
        findViewById<View>(R.id.gps_video_back).setOnClickListener { finish() }
        findViewById<View>(R.id.gps_video_preview_back).setOnClickListener { handleBack() }
        findViewById<View>(R.id.gps_video_failed_back).setOnClickListener { handleBack() }
        flashButton.setOnClickListener { toggleTorch() }
        recordButton.setOnClickListener { startRecording() }
        findViewById<View>(R.id.gps_video_flip).setOnClickListener { switchCamera() }
        findViewById<View>(R.id.gps_video_gallery).setOnClickListener { openLastVideo() }
        pauseButton.setOnClickListener { togglePause() }
        findViewById<View>(R.id.gps_video_stop).setOnClickListener { stopRecording() }
        micButton.setOnClickListener { toggleMic() }
        zoomButton.setOnClickListener { cycleZoom() }
        playButton.setOnClickListener { togglePlayback() }
        soundButton.setOnClickListener { toggleSound() }
        findViewById<View>(R.id.gps_video_save).setOnClickListener { saveVideo() }
        findViewById<View>(R.id.gps_video_record_again).setOnClickListener { confirmDiscard() }
        findViewById<View>(R.id.gps_video_retry_save).setOnClickListener { saveVideo() }
        findViewById<View>(R.id.gps_video_keep_previewing).setOnClickListener {
            setScreenState(ScreenState.PREVIEW)
        }
    }

    private fun requestMissingPermissions() {
        val missing = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filterNot(::hasPermission)
        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        } else {
            startCamera()
            startLocationTracking()
        }
    }

    private fun startCamera() {
        if (!hasPermission(Manifest.permission.CAMERA)) return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(cameraPreview.surfaceProvider)
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.FHD,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)
                        )
                    )
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                activeCamera = provider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    videoCapture
                )
                qualityText.text = getString(R.string.gps_video_quality, "1080", 30)
                updateTorchAvailability()
                updateZoomLevels()
            } catch (_: Exception) {
                Toast.makeText(this, R.string.gps_video_camera_error, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startLocationTracking() {
        if (!locationProvider.hasPermission()) {
            Toast.makeText(this, R.string.gps_video_location_optional, Toast.LENGTH_SHORT).show()
            return
        }
        locationProvider.findCurrentLocation(::onLocationUpdate) { }
        locationProvider.startLocationUpdates(::onLocationUpdate) { }
    }

    private fun onLocationUpdate(location: Location) {
        val changedMeaningfully = latestLocation == null || latestLocation!!.distanceTo(location) >= 50f
        latestLocation = location
        updateStamp()
        if (changedMeaningfully) {
            locationProvider.reverseGeocode(location) { address ->
                if (!address.isNullOrBlank()) {
                    placeName = address
                    updateStamp()
                }
            }
        }
    }

    private fun updateStamp() {
        val stampDate = if (screenState == ScreenState.PREVIEW && recordingStartedWallClock > 0L) {
            Date(recordingStartedWallClock)
        } else {
            Date()
        }
        val formattedTime = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.US).format(stampDate)
        stampTime.text = formattedTime
        previewStampTime.text = formattedTime
        val location = latestLocation
        if (location == null) {
            stampPlace.text = getString(R.string.gps_video_locating)
            stampCoordinates.text = getString(R.string.gps_video_locating)
            previewStampPlace.text = getString(R.string.gps_video_locating)
            previewStampCoordinates.text = getString(R.string.gps_video_locating)
        } else {
            val place = placeName ?: getString(R.string.location_your_location)
            val coordinates = "${LocationFormatter.latitude(location.latitude)}  ${LocationFormatter.longitude(location.longitude)}"
            stampPlace.text = place
            stampCoordinates.text = coordinates
            previewStampPlace.text = place
            previewStampCoordinates.text = coordinates
        }
    }

    private fun startRecording() {
        if (screenState != ScreenState.READY || recording != null) return
        if (!hasEnoughStorage()) {
            Toast.makeText(this, R.string.gps_video_storage_low, Toast.LENGTH_LONG).show()
            return
        }
        val capture = videoCapture ?: return
        val outputFile = runCatching {
            File.createTempFile("gps-video-", ".mp4", cacheDir)
        }.getOrNull() ?: return

        tempVideo = outputFile
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        var pending = capture.output.prepareRecording(this, outputOptions)
        if (isMicEnabled && hasPermission(Manifest.permission.RECORD_AUDIO)) {
            pending = pending.withAudioEnabled()
        }
        recordingState = RecordingState.RECORDING
        pausedTotal = 0L
        recordingStartedAt = SystemClock.elapsedRealtime()
        recordingStartedWallClock = System.currentTimeMillis()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    setScreenState(ScreenState.RECORDING)
                    updateRecordingControls()
                }
                is VideoRecordEvent.Finalize -> onRecordingFinalized(event)
            }
        }
    }

    private fun onRecordingFinalized(event: VideoRecordEvent.Finalize) {
        recording = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (event.error == VideoRecordEvent.Finalize.ERROR_NONE && tempVideo?.exists() == true) {
            preparePreview()
        } else if (tempVideo?.exists() == true && tempVideo?.length() ?: 0L > 0L) {
            // CameraX can still leave a playable partial file after an interruption.
            preparePreview()
        } else {
            Toast.makeText(this, R.string.gps_video_recording_failed, Toast.LENGTH_LONG).show()
            setScreenState(ScreenState.READY)
        }
    }

    private fun stopRecording() {
        if (screenState != ScreenState.RECORDING) return
        recording?.stop()
    }

    private fun togglePause() {
        val activeRecording = recording ?: return
        if (recordingState == RecordingState.RECORDING) {
            activeRecording.pause()
            recordingState = RecordingState.PAUSED
            pausedAt = SystemClock.elapsedRealtime()
            pauseLabel.setText(R.string.gps_video_resume)
            updateRecordingControls()
        } else {
            activeRecording.resume()
            pausedTotal += SystemClock.elapsedRealtime() - pausedAt
            recordingState = RecordingState.RECORDING
            pauseLabel.setText(R.string.gps_video_pause)
            updateRecordingControls()
        }
    }

    private fun toggleMic() {
        val activeRecording = recording ?: return
        isMicEnabled = !isMicEnabled
        activeRecording.mute(!isMicEnabled)
        micLabel.setText(if (isMicEnabled) R.string.gps_video_mic_on else R.string.gps_video_mic_off)
    }

    private fun cycleZoom() {
        if (zoomLevels.isEmpty()) return
        val next = zoomLevels[(zoomLevels.indexOf(currentZoom) + 1) % zoomLevels.size]
        currentZoom = next
        activeCamera?.cameraControl?.setZoomRatio(next)
        zoomButton.text = if (next < 1f) "%.1f×".format(Locale.US, next) else "${next.roundToInt()}×"
    }

    private fun updateZoomLevels() {
        val state = activeCamera?.cameraInfo?.zoomState?.value ?: return
        val values = mutableListOf<Float>()
        if (state.minZoomRatio < 1f) values += state.minZoomRatio
        values += 1f
        if (state.maxZoomRatio >= 2f) values += 2f
        if (state.maxZoomRatio >= 3f) values += 3f
        zoomLevels = values.distinct()
        currentZoom = zoomLevels.firstOrNull() ?: 1f
        activeCamera?.cameraControl?.setZoomRatio(currentZoom)
        zoomButton.text = if (currentZoom < 1f) "%.1f×".format(Locale.US, currentZoom) else "${currentZoom.roundToInt()}×"
    }

    private fun switchCamera() {
        if (screenState != ScreenState.READY) return
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCamera()
    }

    private fun toggleTorch() {
        val camera = activeCamera ?: return
        if (!camera.cameraInfo.hasFlashUnit()) return
        torchOn = !torchOn
        camera.cameraControl.enableTorch(torchOn)
    }

    private fun updateTorchAvailability() {
        val available = activeCamera?.cameraInfo?.hasFlashUnit() == true
        flashButton.visibility = if (available) View.VISIBLE else View.INVISIBLE
        flashButton.isEnabled = available
        if (!available) torchOn = false
    }

    private fun preparePreview() {
        cameraProvider?.unbindAll()
        val file = tempVideo ?: return
        player.setVideoPath(file.absolutePath)
        player.setOnPreparedListener { mediaPlayer ->
            previewPlayer = mediaPlayer
            mediaPlayer.isLooping = false
            mediaPlayer.setVolume(if (isSoundEnabled) 1f else 0f, if (isSoundEnabled) 1f else 0f)
            mediaPlayer.start()
            mediaPlayer.pause()
            updatePlayerPosition()
        }
        player.setOnCompletionListener { playButton.setText(R.string.gps_video_play) }
        setScreenState(ScreenState.PREVIEW)
        playerTick.run()
    }

    private fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
            playButton.setText(R.string.gps_video_play)
        } else {
            player.start()
            playButton.setText(R.string.gps_video_pause)
        }
    }

    private fun toggleSound() {
        isSoundEnabled = !isSoundEnabled
        previewPlayer?.setVolume(if (isSoundEnabled) 1f else 0f, if (isSoundEnabled) 1f else 0f)
        soundButton.setText(if (isSoundEnabled) R.string.gps_video_sound else R.string.gps_video_sound_off)
    }

    private fun updatePlayerPosition() {
        if (!::player.isInitialized) return
        val current = player.currentPosition.toLong()
        val duration = player.duration.coerceAtLeast(0).toLong()
        positionText.text = getString(
            R.string.gps_video_position,
            formatDuration(current),
            formatDuration(duration)
        )
    }

    private fun saveVideo() {
        val file = tempVideo ?: return
        if (!file.exists()) {
            setScreenState(ScreenState.SAVE_FAILED)
            return
        }
        setSaveButtonsEnabled(false)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val stampedFile = File.createTempFile("gps-video-stamped-", ".mp4", cacheDir)
                val stampData = VideoStampData(
                    place = placeName ?: getString(R.string.location_your_location),
                    latitude = latestLocation?.latitude,
                    longitude = latestLocation?.longitude,
                    recordedAtMillis = recordingStartedWallClock,
                    template = com.gps.map.camera.and.time.stamp.ui.settings.StampTemplate.fromKey(
                        preferences.defaultStamp
                    )
                )
                val burnedIn = VideoStampBurner.burn(file, stampedFile, stampData)
                val saved = if (burnedIn) copyToGallery(stampedFile) else null
                stampedFile.delete()
                saved
            }
            setSaveButtonsEnabled(true)
            if (result != null) {
                lastSavedVideo = result
                preferences.sharedPref.edit().putString(LAST_VIDEO_URI, result.toString()).apply()
                file.delete()
                tempVideo = null
                loadLastVideoThumbnail()
                Toast.makeText(this@GpsVideoActivity, R.string.gps_video_saved, Toast.LENGTH_SHORT).show()
                setScreenState(ScreenState.READY)
            } else {
                setScreenState(ScreenState.SAVE_FAILED)
            }
        }
    }

    private fun copyToGallery(file: File): Uri? {
        if (!hasEnoughStorage(20L * 1024L * 1024L)) return null
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "GPS_Video_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/GPS Video")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = contentResolver.insert(collection, values) ?: return null
        return try {
            contentResolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(file).use { input -> input.copyTo(output) }
            } ?: error("Unable to open video output")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                    null,
                    null
                )
            }
            uri
        } catch (_: Exception) {
            contentResolver.delete(uri, null, null)
            null
        }
    }

    private fun loadLastVideoThumbnail() {
        val savedUri = preferences.sharedPref.getString(LAST_VIDEO_URI, null)?.let(Uri::parse)
        if (savedUri == null) {
            galleryThumbnail.setImageDrawable(null)
            return
        }
        workExecutor.execute {
            val bitmap = runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(this, savedUri)
                    retriever.getFrameAtTime(0)
                }
            }.getOrNull()
            runOnUiThread {
                if (bitmap != null) galleryThumbnail.setImageBitmap(bitmap)
            }
        }
    }

    private fun openLastVideo() {
        val uri = preferences.sharedPref.getString(LAST_VIDEO_URI, null)?.let(Uri::parse)
        if (uri == null) {
            Toast.makeText(this, R.string.gps_video_no_saved_video, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, R.string.gps_video_no_saved_video, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setScreenState(next: ScreenState) {
        screenState = next
        val cameraVisible = next == ScreenState.READY || next == ScreenState.RECORDING
        cameraContent.visibility = if (cameraVisible) View.VISIBLE else View.GONE
        previewContent.visibility = if (next == ScreenState.PREVIEW) View.VISIBLE else View.GONE
        failedContent.visibility = if (next == ScreenState.SAVE_FAILED) View.VISIBLE else View.GONE
        readyTopBar.visibility = if (next == ScreenState.READY) View.VISIBLE else View.GONE
        recordingTopBar.visibility = if (next == ScreenState.RECORDING) View.VISIBLE else View.GONE
        readyControls.visibility = if (next == ScreenState.READY) View.VISIBLE else View.GONE
        recordingControls.visibility = if (next == ScreenState.RECORDING) View.VISIBLE else View.GONE
        timerPill.visibility = if (next == ScreenState.RECORDING) View.VISIBLE else View.GONE
        if (next != ScreenState.RECORDING) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (next == ScreenState.RECORDING) updateRecordingControls()
        updateStamp()
    }

    private fun updateRecordingControls() {
        val paused = recordingState == RecordingState.PAUSED
        timerDot.setBackgroundResource(
            if (paused) R.drawable.bg_gps_video_timer_dot_paused
            else R.drawable.bg_gps_video_timer_dot
        )
        timerText.setTextColor(Color.parseColor(if (paused) "#FFD47D" else "#EEF4FC"))
        timerText.text = if (paused) {
            getString(R.string.gps_video_paused_timer, formatDuration(recordingDurationMillis()))
        } else {
            formatRecordingDuration(recordingDurationMillis())
        }
        pauseIcon.setImageResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        pauseLabel.setText(if (paused) R.string.gps_video_resume else R.string.gps_video_pause)
    }

    private fun setSaveButtonsEnabled(enabled: Boolean) {
        val saveButton = findViewById<TextView>(R.id.gps_video_save)
        saveButton.isEnabled = enabled
        saveButton.setText(if (enabled) R.string.gps_video_save else R.string.gps_video_saving)
        findViewById<View>(R.id.gps_video_retry_save).isEnabled = enabled
    }

    private fun handleBack() {
        when (screenState) {
            ScreenState.READY -> finish()
            ScreenState.RECORDING -> confirmStopRecording()
            ScreenState.PREVIEW, ScreenState.SAVE_FAILED -> confirmDiscard()
        }
    }

    private fun confirmStopRecording() {
        AlertDialog.Builder(this)
            .setMessage(R.string.gps_video_stop_confirmation)
            .setNegativeButton(R.string.gps_video_cancel, null)
            .setPositiveButton(R.string.gps_video_stop_recording) { _, _ -> stopRecording() }
            .show()
    }

    private fun confirmDiscard() {
        AlertDialog.Builder(this)
            .setMessage(R.string.gps_video_discard_confirmation)
            .setNegativeButton(R.string.gps_video_cancel, null)
            .setPositiveButton(R.string.gps_video_record_again) { _, _ -> discardPreview() }
            .show()
    }

    private fun discardPreview() {
        player.stopPlayback()
        previewPlayer = null
        tempVideo?.delete()
        tempVideo = null
        setScreenState(ScreenState.READY)
        startCamera()
    }

    private fun showPermissionMessage(message: Int, permission: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setNegativeButton(R.string.gps_video_cancel, null)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .show()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasEnoughStorage(minimumBytes: Long = 100L * 1024L * 1024L): Boolean {
        val stats = StatFs(cacheDir.absolutePath)
        return stats.availableBytes >= minimumBytes
    }

    private fun recordingDurationMillis(): Long {
        if (recordingStartedAt == 0L) return 0L
        val end = if (recordingState == RecordingState.PAUSED) pausedAt else SystemClock.elapsedRealtime()
        return (end - recordingStartedAt - pausedTotal).coerceAtLeast(0L)
    }

    private fun formatDuration(millis: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun formatRecordingDuration(millis: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3_600, (seconds % 3_600) / 60, seconds % 60)
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        val path = savedInstanceState?.getString(STATE_TEMP_VIDEO)
        if (!path.isNullOrBlank() && File(path).exists()) {
            tempVideo = File(path)
            screenState = ScreenState.PREVIEW
            preparePreview()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TEMP_VIDEO, tempVideo?.absolutePath)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        if (screenState == ScreenState.RECORDING && !isChangingConfigurations) {
            recording?.stop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        locationProvider.stopLocationUpdates()
        mainHandler.removeCallbacksAndMessages(null)
        workExecutor.shutdownNow()
        if (screenState == ScreenState.READY) tempVideo?.delete()
        super.onDestroy()
    }

    companion object {
        private const val LAST_VIDEO_URI = "gps_video_last_uri"
        private const val STATE_TEMP_VIDEO = "gps_video_temp_video"
    }
}
