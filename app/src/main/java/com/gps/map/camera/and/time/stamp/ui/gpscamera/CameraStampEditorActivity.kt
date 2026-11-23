package com.gps.map.camera.and.time.stamp.ui.gpscamera

import android.content.ContentValues
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.exifinterface.media.ExifInterface
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.pref.AppSharedPreferencesApp
import com.gps.map.camera.and.time.stamp.ui.settings.StampTemplate
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampCustomization
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampCustomizationStore
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampOverlayView
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampRenderData
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampRenderer
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampTemplateCatalog
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.TemplateAlignment
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.TemplatePosition
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.OpenMeteoWeather
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.WeatherSnapshot
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.gps.map.camera.and.time.stamp.ui.camera.CameraActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** GPS stamp editor opened from the capture preview's Next button. */
class CameraStampEditorActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        private const val LOG_TAG = "GpsCameraEditor"
        private val POSITION_LABELS = arrayOf("Bottom left", "Bottom right", "Top left", "Top right")
        private val POSITION_GRAVITIES = intArrayOf(
            Gravity.BOTTOM or Gravity.START,
            Gravity.BOTTOM or Gravity.END,
            Gravity.TOP or Gravity.START,
            Gravity.TOP or Gravity.END
        )
    }

    private lateinit var sourcePhotoUri: Uri
    private lateinit var photoView: ImageView
    private lateinit var mapView: MapView
    private lateinit var mapCard: FrameLayout
    private lateinit var mapPreview: ImageView
    private lateinit var mapFallback: TextView
    private lateinit var stampPanel: View
    private lateinit var mediaFrame: FrameLayout
    private lateinit var addressText: TextView
    private lateinit var coordinatesText: TextView
    private lateinit var dateText: TextView
    private lateinit var locationClientLabel: TextView
    private lateinit var templateOverlay: StampOverlayView
    private val saveExecutor = Executors.newSingleThreadExecutor()
    private val addressExecutor = Executors.newSingleThreadExecutor()

    private var googleMap: GoogleMap? = null
    private var mapEnabled = false
    private var mapTilesLoaded = false
    private var mapCardVisible = false
    private var photoZoom = 1f
    private var photoOffsetX = 0f
    private var photoOffsetY = 0f
    private var mapCardBoundsFraction: RectF? = null
    private var stampPositionIndex = 0
    private var latitude = Double.NaN
    private var longitude = Double.NaN
    private var capturedAt = ""
    private var address = ""
    private var weatherSnapshot: WeatherSnapshot? = null
    private var stampCustomization: StampCustomization? = null
    private var templateVisible = true
    private var isSaving = false
    private var deleteSourceAfterSave = true
    private val savedPreferences by lazy { AppSharedPreferencesApp(applicationContext) }
    private val galleryPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { selectedUri ->
        if (selectedUri != null) replacePhoto(selectedUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gps_stamp_editor)

        val photoText = intent.getStringExtra(CameraActivity.EXTRA_PHOTO_URI)
        if (photoText == null) {
            finish()
            return
        }
        sourcePhotoUri = Uri.parse(photoText)
        latitude = intent.getDoubleExtra(CameraActivity.EXTRA_LATITUDE, Double.NaN)
        longitude = intent.getDoubleExtra(CameraActivity.EXTRA_LONGITUDE, Double.NaN)
        capturedAt = intent.getStringExtra(CameraActivity.EXTRA_CAPTURED_AT).orEmpty()

        photoView = findViewById(R.id.editor_photo)
        photoView.setImageURI(sourcePhotoUri)
        mediaFrame = findViewById(R.id.editor_media_frame)
        mapView = findViewById(R.id.editor_map_view)
        mapCard = findViewById(R.id.editor_map_card)
        mapPreview = findViewById(R.id.editor_map_image)
        enablePinchAndDrag(photoView, photoView)
        // Keep the gesture surface on the visible snapshot, but transform the
        // entire card so dragging the map visibly moves it across the photo.
        enablePinchAndDrag(mapPreview, mapCard)
        mapFallback = findViewById(R.id.editor_map_fallback)
        stampPanel = findViewById(R.id.editor_stamp_panel)
        addressText = findViewById(R.id.editor_address)
        coordinatesText = findViewById(R.id.editor_coordinates)
        dateText = findViewById(R.id.editor_date)
        locationClientLabel = findViewById(R.id.editor_weather)

        stampCustomization = StampCustomizationStore.read(this)
        templateOverlay = StampOverlayView(this)
        mediaFrame.addView(
            templateOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        stampPanel.visibility = View.GONE
        mapCard.visibility = View.GONE

        dateText.text = capturedAt
        val coordinateLabel = if (latitude.isNaN() || longitude.isNaN()) {
            getString(R.string.location_unavailable)
        } else {
            String.format(Locale.getDefault(), "Lat: %.5f   Long: %.5f", latitude, longitude)
        }
        coordinatesText.text = coordinateLabel
        addressText.text = getString(R.string.address_loading)
        locationClientLabel.text = getString(R.string.weather_unavailable)

        mapEnabled = hasMapsApiKey()
        if (mapEnabled) {
            mapView.onCreate(savedInstanceState)
            mapView.getMapAsync(this)
        } else {
            mapView.visibility = View.GONE
            mapFallback.visibility = View.VISIBLE
            mapFallback.setText(R.string.map_unavailable)
        }

        findViewById<View>(R.id.editor_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.editor_save).setOnClickListener { saveEditedPhoto() }
        findViewById<TextView>(R.id.editor_tab_details).setOnClickListener {
            setSelectedTab(R.id.editor_tab_details)
        }
        findViewById<TextView>(R.id.editor_tab_position).setOnClickListener { chooseStampPosition() }
        findViewById<TextView>(R.id.editor_tab_map).setOnClickListener { chooseTemplate() }
        findViewById<TextView>(R.id.editor_tab_location).setOnClickListener { toggleLocationDetails() }
        findViewById<TextView>(R.id.editor_tab_photo).setOnClickListener { galleryPicker.launch("image/*") }
        loadAddress()
        loadWeather()
        updateStampPosition()
        refreshTemplateOverlay()
    }

    override fun onStart() {
        super.onStart()
        if (mapEnabled) mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (mapEnabled) mapView.onResume()
    }

    override fun onPause() {
        if (mapEnabled) mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (mapEnabled) mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (mapEnabled) mapView.onLowMemory()
    }

    override fun onDestroy() {
        if (mapEnabled) mapView.onDestroy()
        saveExecutor.shutdown()
        addressExecutor.shutdown()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (mapEnabled) mapView.onSaveInstanceState(outState)
        outState.putInt("stamp_position", stampPositionIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isZoomControlsEnabled = false
        if (!latitude.isNaN() && !longitude.isNaN()) {
            val point = LatLng(latitude, longitude)
            map.addMarker(MarkerOptions().position(point).title(getString(R.string.current_position)))
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 15f))
            mapFallback.visibility = View.GONE
        } else {
            mapFallback.visibility = View.VISIBLE
            mapFallback.setText(R.string.map_waiting_for_location)
        }
        map.setOnCameraIdleListener {
            if (mapTilesLoaded) updateMapPreview(map)
        }
        // A camera snapshot taken as soon as the map becomes ready often contains
        // only the empty background. Wait until Google has rendered the map tiles.
        map.setOnMapLoadedCallback {
            mapTilesLoaded = true
            updateMapPreview(map)
        }
    }

    private fun updateMapPreview(map: GoogleMap) {
        map.snapshot { bitmap ->
            if (bitmap == null || isFinishing || isDestroyed) {
                bitmap?.recycle()
                return@snapshot
            }
            val previous = (mapPreview.drawable as? BitmapDrawable)?.bitmap
            mapPreview.setImageBitmap(bitmap)
            mapPreview.visibility = View.VISIBLE
            templateOverlay.setMapBitmap(bitmap)
            if (previous != null && previous !== bitmap && !previous.isRecycled) previous.recycle()
        }
    }

    private fun loadAddress() {
        if (latitude.isNaN() || longitude.isNaN()) {
            address = getString(R.string.location_unavailable)
            addressText.text = address
            return
        }
        addressExecutor.execute {
            val resolvedAddress = try {
                if (!Geocoder.isPresent()) null else {
                    @Suppress("DEPRECATION")
                    Geocoder(this, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
                        ?.firstOrNull()?.getAddressLine(0)
                }
            } catch (_: Exception) {
                null
            }
            val displayAddress = resolvedAddress?.takeIf { it.isNotBlank() }
                ?: String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude)
            address = displayAddress
            runOnUiThread {
                addressText.text = displayAddress
            refreshTemplateOverlay()
            }
        }
    }

    private fun loadWeather() {
        if (latitude.isNaN() || longitude.isNaN()) return
        addressExecutor.execute {
            val result = OpenMeteoWeather.fetchCached(latitude, longitude)
            weatherSnapshot = result
            runOnUiThread {
                locationClientLabel.text = result?.uiText ?: getString(R.string.weather_unavailable)
                refreshTemplateOverlay()
            }
        }
    }

    private fun chooseTemplate() {
        val selectedKey = stampCustomization?.templateKey ?: StampCustomizationStore.selectedTemplate(this)
        val selectedIndex = StampTemplateCatalog.items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Photo template")
            .setSingleChoiceItems(
                StampTemplateCatalog.items.map { it.name }.toTypedArray(),
                selectedIndex
            ) { dialog, which ->
                val selected = StampTemplateCatalog.items[which]
                StampCustomizationStore.selectTemplate(this, selected.key)
                stampCustomization = StampCustomizationStore.read(this, selected.key)
                templateVisible = true
                templateOverlay.visibility = View.VISIBLE
                refreshTemplateOverlay()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        setSelectedTab(R.id.editor_tab_map)
    }

    private fun refreshTemplateOverlay() {
        if (!::templateOverlay.isInitialized) return
        val customization = stampCustomization ?: StampCustomizationStore.read(this).also {
            stampCustomization = it
        }
        templateOverlay.setCustomization(customization)
        templateOverlay.setRenderData(
            StampRenderData(
                address = address.ifBlank { getString(R.string.address_loading) },
                latitude = latitude,
                longitude = longitude,
                capturedAtText = capturedAt.takeIf { it.isNotBlank() },
                weather = if (customization.showWeather) weatherSnapshot?.uiText else null
            )
        )
    }

    private fun replacePhoto(uri: Uri) {
        sourcePhotoUri = uri
        deleteSourceAfterSave = false
        photoView.setImageURI(uri)
        // Keep the capture's GPS/time stamp. A selected gallery photo replaces only the image.
        setSelectedTab(R.id.editor_tab_photo)
    }

    private fun chooseStampPosition() {
        AlertDialog.Builder(this)
            .setTitle(R.string.position)
            .setSingleChoiceItems(POSITION_LABELS, stampPositionIndex) { dialog, selected ->
                stampPositionIndex = selected
                val customization = stampCustomization ?: StampCustomizationStore.read(this)
                stampCustomization = customization.copy(
                    position = if (selected < 2) TemplatePosition.BOTTOM else TemplatePosition.TOP,
                    alignment = if (selected == 1 || selected == 3) TemplateAlignment.RIGHT else TemplateAlignment.LEFT
                )
                StampCustomizationStore.save(this, stampCustomization!!)
                refreshTemplateOverlay()
                updateStampPosition()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateStampPosition() {
        val params = stampPanel.layoutParams as ConstraintLayout.LayoutParams
        params.topToTop = ConstraintLayout.LayoutParams.UNSET
        params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        params.startToStart = ConstraintLayout.LayoutParams.UNSET
        params.endToEnd = ConstraintLayout.LayoutParams.UNSET

        params.width = if (stampPositionIndex < 2) ConstraintLayout.LayoutParams.MATCH_PARENT else dp(330)

        when (stampPositionIndex) {
            0 -> { // Bottom Left / Bottom Full Width
                params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            1 -> { // Bottom Right / Full Width
                params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            2 -> { // Top Left
                params.topToBottom = R.id.editor_tabs
                params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            }
            3 -> { // Top Right
                params.topToBottom = R.id.editor_tabs
                params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
        }

        params.marginStart = dp(14)
        params.marginEnd = dp(14)
        params.bottomMargin = dp(18)
        params.topMargin = dp(18)

        stampPanel.layoutParams = params
    }

    private fun enablePinchAndDrag(touchView: View, transformView: View) {
        var previousX = 0f
        var previousY = 0f
        var dragging = false
        val scaleDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    // Match the decompiled editor: keep applying the two-finger
                    // distance ratio without an arbitrary zoom-in/out ceiling.
                    val newScale = transformView.scaleX * detector.scaleFactor
                    transformView.scaleX = newScale
                    transformView.scaleY = newScale
                    return true
                }
            })

        touchView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    previousX = event.rawX
                    previousY = event.rawY
                    dragging = true
                }
                MotionEvent.ACTION_MOVE -> if (dragging && !scaleDetector.isInProgress && event.pointerCount == 1) {
                    transformView.translationX += event.rawX - previousX
                    transformView.translationY += event.rawY - previousY
                    previousX = event.rawX
                    previousY = event.rawY
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                    previousX = event.rawX
                    previousY = event.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            true
        }
    }

    private fun toggleMap() {
        mapCardVisible = !mapCardVisible
        mapCard.visibility = if (mapCardVisible) View.VISIBLE else View.GONE
        mapFallback.visibility = if (!mapEnabled && mapCardVisible) View.VISIBLE else View.GONE
        setSelectedTab(R.id.editor_tab_map)
    }

    private fun toggleLocationDetails() {
        setSelectedTab(R.id.editor_tab_details)
    }

    private fun setSelectedTab(selectedId: Int) {
        listOf(R.id.editor_tab_details, R.id.editor_tab_position, R.id.editor_tab_map,
            R.id.editor_tab_location, R.id.editor_tab_photo).forEach { id ->
            findViewById<TextView>(id).setTextColor(
                if (id == selectedId) 0xFF67A2FF.toInt() else 0xFFAFBDCE.toInt()
            )
        }
    }

    private fun saveEditedPhoto() {
        if (isSaving) return
        isSaving = true
        captureEditorTransforms()
        findViewById<TextView>(R.id.editor_save).isEnabled = false
        val currentMap = googleMap
        if (!mapCardVisible || currentMap == null) {
            saveExecutor.execute { renderAndSave(null) }
            return
        }

        // Map snapshot callbacks may not arrive on devices without a working Maps service/key.
        // Save the photo without the map if the callback is delayed.
        val callbackUsed = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        val timeout = Runnable {
            if (callbackUsed.compareAndSet(false, true)) {
                saveExecutor.execute { renderAndSave(null) }
            }
        }
        mainHandler.postDelayed(timeout, 1500L)
        currentMap.snapshot { mapBitmap ->
            if (callbackUsed.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeout)
                saveExecutor.execute { renderAndSave(mapBitmap) }
            } else {
                mapBitmap?.recycle()
            }
        }
    }

    private fun captureEditorTransforms() {
        if (photoView.width > 0 && photoView.height > 0) {
            photoZoom = photoView.scaleX
            photoOffsetX = photoView.translationX / photoView.width
            photoOffsetY = photoView.translationY / photoView.height
        }
        if (mapCard.width > 0 && mapCard.height > 0 && photoView.width > 0 && photoView.height > 0) {
            val scale = mapCard.scaleX
            val visualLeft = mapCard.x + mapCard.width * (1f - scale) / 2f
            val visualTop = mapCard.y + mapCard.height * (1f - scale) / 2f
            mapCardBoundsFraction = RectF(
                visualLeft / photoView.width,
                visualTop / photoView.height,
                (visualLeft + mapCard.width * scale) / photoView.width,
                (visualTop + mapCard.height * scale) / photoView.height
            )
        }
    }

    private fun renderAndSave(mapBitmap: Bitmap?) {
        var baseBitmap: Bitmap? = null
        var stampedBitmap: Bitmap? = null
        var temporaryOutput: File? = null
        var failureStage = "reading the selected photo"
        try {
            baseBitmap = decodePhotoWithOrientation(sourcePhotoUri) ?: error("Could not read the captured photo")
            failureStage = "drawing the GPS stamp"
            stampedBitmap = drawStamp(baseBitmap, mapBitmap)
            failureStage = "encoding the stamped photo"
            temporaryOutput = File(cacheDir, "gps-stamped-${System.currentTimeMillis()}.jpg")
            FileOutputStream(temporaryOutput).use { output ->
                check(stampedBitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                    "Could not encode the stamped photo"
                }
            }
            failureStage = "writing GPS metadata"
            writeExif(temporaryOutput)
            failureStage = "saving the photo to the gallery"
            val finalUri = insertPhoto(temporaryOutput)
            val savedPhotoIntent = Intent(this, CameraSavedPhotoActivity::class.java).apply {
                putExtra(CameraActivity.EXTRA_PHOTO_URI, finalUri.toString())
                putExtra(CameraActivity.EXTRA_LATITUDE, latitude)
                putExtra(CameraActivity.EXTRA_LONGITUDE, longitude)
                putExtra(CameraActivity.EXTRA_CAPTURED_AT, capturedAt)
            }
            runOnUiThread {
                try {
                    startActivity(savedPhotoIntent)
                    // Only delete the temporary app-owned capture after the next screen opens.
                    if (deleteSourceAfterSave) contentResolver.delete(sourcePhotoUri, null, null)
                    finish()
                } catch (error: ActivityNotFoundException) {
                    Log.e(LOG_TAG, "CameraSavedPhotoActivity is missing from AndroidManifest.xml", error)
                    isSaving = false
                    findViewById<TextView>(R.id.editor_save).isEnabled = true
                    Toast.makeText(this, R.string.saved_preview_activity_missing, Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    Log.e(LOG_TAG, "Could not open the saved-photo screen", error)
                    isSaving = false
                    findViewById<TextView>(R.id.editor_save).isEnabled = true
                    Toast.makeText(this, R.string.saved_preview_open_failed, Toast.LENGTH_LONG).show()
                }
            }
        } catch (error: Exception) {
            Log.e(LOG_TAG, "Failed while applying the GPS photo stamp", error)
            runOnUiThread {
                isSaving = false
                findViewById<TextView>(R.id.editor_save).isEnabled = true
                Toast.makeText(this, getString(R.string.photo_save_error_detail, failureStage, error.localizedMessage ?: error.javaClass.simpleName), Toast.LENGTH_LONG).show()
            }
        } finally {
            baseBitmap?.recycle()
            stampedBitmap?.recycle()
            mapBitmap?.recycle()
            temporaryOutput?.delete()
        }
    }

    private fun decodePhotoWithOrientation(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = 1 }
        val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (largestDimension > 0 && largestDimension.toLong() / sampleSize > 3200L && sampleSize < (1 shl 29)) {
            sampleSize *= 2
        }
        decodeOptions.inSampleSize = sampleSize
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null
        check(bitmap.width > 0 && bitmap.height > 0) { "The selected image has invalid dimensions" }
        val orientation = contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        }
        if (matrix.isIdentity) return bitmap
        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (upright !== bitmap) bitmap.recycle()
        return upright
    }

    private fun drawStamp(photo: Bitmap, mapBitmap: Bitmap?): Bitmap {
        val result = photo.copy(Bitmap.Config.ARGB_8888, true) ?: error("Could not create stamp canvas")
        val canvas = Canvas(result)
        if (photoZoom != 1f || photoOffsetX != 0f || photoOffsetY != 0f) {
            val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val centerX = photo.width / 2f
            val centerY = photo.height / 2f
            canvas.drawColor(Color.BLACK)
            canvas.save()
            canvas.translate(centerX + photoOffsetX * photo.width, centerY + photoOffsetY * photo.height)
            canvas.scale(photoZoom, photoZoom)
            canvas.translate(-centerX, -centerY)
            canvas.drawBitmap(photo, 0f, 0f, photoPaint)
            canvas.restore()
        }
        val scale = maxOf(1f, photo.width / 1080f)
        if (mapBitmap != null && mapCardVisible) {
            val mapWidth = photo.width * 0.38f
            val mapHeight = mapWidth * 1.05f
            val savedBounds = mapCardBoundsFraction
            val mapRect = if (savedBounds != null) {
                RectF(savedBounds.left * photo.width, savedBounds.top * photo.height,
                    savedBounds.right * photo.width, savedBounds.bottom * photo.height)
            } else {
                RectF((photo.width - mapWidth) / 2f, photo.height * 0.39f,
                    (photo.width + mapWidth) / 2f, photo.height * 0.39f + mapHeight)
            }
            val clip = Path().apply { addRoundRect(mapRect, 28f * scale, 28f * scale, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawBitmap(mapBitmap, null, mapRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        }

        if (templateVisible) {
            val customization = stampCustomization ?: StampCustomizationStore.read(this)
            StampRenderer.draw(
                canvas = canvas,
                bounds = RectF(0f, 0f, photo.width.toFloat(), photo.height.toFloat()),
                settings = customization,
                data = StampRenderData(
                    address = address.ifBlank { getString(R.string.address_loading) },
                    latitude = latitude,
                    longitude = longitude,
                    capturedAtText = capturedAt.takeIf { it.isNotBlank() },
                    weather = if (customization.showWeather) weatherSnapshot?.uiText else null
                ),
                map = mapBitmap
            )
        }
        return result
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        text.split(Regex("\\s+")).forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                result.add(current.toString())
                current = StringBuilder(word)
            } else current = StringBuilder(candidate)
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private fun writeExif(file: File) {
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date()))
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "GPS Camera stamped")
        if (!latitude.isNaN() && !longitude.isNaN()) exif.setLatLong(latitude, longitude)
        exif.saveAttributes()
    }

    private fun insertPhoto(file: File): Uri {
        val filename = "GPS_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GPS Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GPS Camera")
                check(folder.exists() || folder.mkdirs())
                put(MediaStore.Images.Media.DATA, File(folder, filename).absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = contentResolver.insert(collection, values) ?: error("Could not create photo entry")
        try {
            val output = contentResolver.openOutputStream(uri, "w") ?: error("Could not open photo output")
            FileInputStream(file).use { input -> output.use { input.copyTo(it) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
        } catch (error: Exception) {
            contentResolver.delete(uri, null, null)
            throw error
        }
        return uri
    }

    private fun hasMapsApiKey(): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val key = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
            !key.isNullOrBlank() && !key.contains("YOUR_MAPS_API_KEY")
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
