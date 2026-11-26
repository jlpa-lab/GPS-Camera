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
import android.widget.ImageButton
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
import com.gps.map.camera.and.time.stamp.ads.AdRemoteConfig
import com.gps.map.camera.and.time.stamp.ads.AdsManager
import com.gps.map.camera.and.time.stamp.ads.banner_all
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
        private val POSITION_LABELS = arrayOf("Top", "Middle", "Bottom")
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
    private lateinit var changeImageOverlay: View
    private lateinit var stampPanel: View
    private lateinit var mediaFrame: FrameLayout
    private lateinit var addressText: TextView
    private lateinit var coordinatesText: TextView
    private lateinit var dateText: TextView
    private lateinit var locationClientLabel: TextView
    private lateinit var templateOverlay: StampOverlayView
    private val saveExecutor = Executors.newSingleThreadExecutor()
    private val addressExecutor = Executors.newSingleThreadExecutor()
    private val galleryImageExecutor = Executors.newSingleThreadExecutor()

    private var googleMap: GoogleMap? = null
    private var mapEnabled = false
    private var mapTilesLoaded = false
    private var mapCardVisible = false
    private var swappedImages = false
    private var useLiveMapInTemplate = true
    private var latestMapBitmap: Bitmap? = null
    private var stampImageUri: Uri? = null
    private var stampImageBitmap: Bitmap? = null
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
    private val toolsCameraMode: Boolean
        get() = intent.getBooleanExtra(CameraActivity.EXTRA_TOOLS_CAMERA, false)
    private val savedPreferences by lazy { AppSharedPreferencesApp(applicationContext) }
    private val stampImagePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(SmallImagePickerActivity.EXTRA_PHOTO_URI)
                ?.let { replaceStampImage(Uri.parse(it)) }
        }
    }
    private val fullscreenPreviewLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) saveEditedPhoto()
    }
    // In Tools mode, Gallery replaces the small image displayed with the map stamp.
    private val galleryPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::replaceStampImage)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(if (toolsCameraMode) R.layout.activity_tools_stamp_editor else R.layout.activity_gps_stamp_editor)
        if(toolsCameraMode) { loadStampEditorBanner()}
        AdsManager.loadInterAll(this, ignoreLimit = true)
        val photoText = intent.getStringExtra(CameraActivity.EXTRA_PHOTO_URI)
        if (photoText == null) {
            finish()
            return
        }
        sourcePhotoUri = Uri.parse(photoText)
        latitude = intent.getDoubleExtra(CameraActivity.EXTRA_LATITUDE, Double.NaN)
        longitude = intent.getDoubleExtra(CameraActivity.EXTRA_LONGITUDE, Double.NaN)
        capturedAt = intent.getStringExtra(CameraActivity.EXTRA_CAPTURED_AT).orEmpty()

        findViewById<TextView>(R.id.editor_title).setText(
            R.string.gps_camera_title
        )
        findViewById<TextView>(R.id.editor_save).setText(R.string.save)
        if (toolsCameraMode) {
            findViewById<View>(R.id.editor_tab_location).visibility = View.VISIBLE
            findViewById<View>(R.id.editor_tab_photo).visibility = View.VISIBLE
            findViewById<TextView>(R.id.editor_tab_map).setText(R.string.tab_map)
            findViewById<View>(R.id.editor_fullscreen).visibility = View.VISIBLE
        }

        photoView = findViewById(R.id.editor_photo)
        photoView.setImageURI(sourcePhotoUri)
        mediaFrame = findViewById(R.id.editor_media_frame)
        mapView = findViewById(R.id.editor_map_view)
        mapCard = findViewById(R.id.editor_map_card)
        mapPreview = findViewById(R.id.editor_map_image)
        changeImageOverlay = findViewById(R.id.editor_change_image_overlay)
        positionToolsMapCard()
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
        mapCardVisible = true
        updateStandaloneMapVisibility()

        dateText.text = capturedAt
        val coordinateLabel = if (latitude.isNaN() || longitude.isNaN()) {
            getString(R.string.location_unavailable)
        } else {
            String.format(Locale.getDefault(), "Lat: %.5f   Long: %.5f", latitude, longitude)
        }
        coordinatesText.text = coordinateLabel
        address = if (latitude.isNaN() || longitude.isNaN()) {
            getString(R.string.location_unavailable)
        } else {
            String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude)
        }
        addressText.text = editorPlaceLabel(address)
        locationClientLabel.text = getString(R.string.weather_unavailable)

        mapEnabled = hasMapsApiKey()
        if (mapEnabled) {
            mapView.onCreate(savedInstanceState)
            mapView.getMapAsync(this)
        } else {
            mapView.visibility = View.GONE
            mapPreview.setImageResource(R.drawable.home_map_view)
            mapPreview.visibility = View.VISIBLE
            mapFallback.visibility = View.GONE
        }

        findViewById<View>(R.id.editor_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.editor_save).setOnClickListener {
            AdsManager.showInterAll(this) {
                saveEditedPhoto()
            }
        }
        findViewById<ImageButton>(R.id.editor_fullscreen).setOnClickListener { openFullscreenPreview() }
        findViewById<TextView>(R.id.editor_tab_details).setOnClickListener {
            showDetailsMode()
        }
        findViewById<TextView>(R.id.editor_tab_position).setOnClickListener {
            toggleSwappedImages()
        }
        findViewById<TextView>(R.id.editor_tab_map).setOnClickListener {
            if (toolsCameraMode) chooseTemplate() else toggleMap()
        }
        findViewById<TextView>(R.id.editor_tab_location).setOnClickListener { toggleLocationDetails() }
        findViewById<TextView>(R.id.editor_tab_photo).setOnClickListener {
            if (toolsCameraMode) openMapImageGallery() else showChangeImageMode()
        }
        changeImageOverlay.setOnClickListener {
            if (toolsCameraMode) openMapImageGallery() else launchStampImagePicker()
        }
        loadAddress()
        loadWeather()
        updateStampPosition()
        refreshTemplateOverlay()
        setSelectedTab(R.id.editor_tab_details)
    }
    private fun loadStampEditorBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, findViewById(R.id.fr_banner), isCollapse = false
        )
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
        galleryImageExecutor.shutdown()
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
            latestMapBitmap = bitmap
            if (useLiveMapInTemplate && !swappedImages) mapPreview.setImageBitmap(bitmap)
            mapPreview.visibility = View.VISIBLE
            if (useLiveMapInTemplate && !swappedImages) {
                templateOverlay.setFloatingImageBitmap(bitmap)
            }
            if (previous != null && previous !== bitmap && !previous.isRecycled) previous.recycle()
        }
    }

    private fun loadAddress() {
        if (latitude.isNaN() || longitude.isNaN()) {
            address = getString(R.string.location_unavailable)
            addressText.text = editorPlaceLabel(address)
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
                addressText.text = editorPlaceLabel(displayAddress)
                refreshTemplateOverlay()
            }
        }
    }

    /** Keeps the Tools editor's compact Place row to a useful city/region label. */
    private fun editorPlaceLabel(value: String): String {
        val clean = value.replace(Regex("\\s+"), " ").trim()
        if (!toolsCameraMode || clean.isBlank()) return clean
        val parts = clean.split(',').map(String::trim).filter(String::isNotBlank)
        return when {
            parts.size >= 2 -> parts.takeLast(2).joinToString(", ")
            else -> clean
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
        templateVisible = true
        templateOverlay.visibility = View.VISIBLE
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
                updateTemplateFloatingImage()
                updateStandaloneMapVisibility()
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
        templateVisible = true
        templateOverlay.visibility = View.VISIBLE
        photoView.scaleX = 1f
        photoView.scaleY = 1f
        photoView.translationX = 0f
        photoView.translationY = 0f
        stampImageUri = null
        stampImageBitmap = null
        swappedImages = false
        deleteSourceAfterSave = false
        photoView.setImageURI(uri)
        changeImageOverlay.visibility = View.GONE
        // Keep the capture's GPS/time stamp and selected template. The picker
        // replaces only the photo itself.
        showCurrentLocationMapPreview()
        updateStandaloneMapVisibility()
        setSelectedTab(R.id.editor_tab_photo)
    }

    private fun replaceStampImage(uri: Uri) {
        // Change Image is independent of Swap: it only replaces the foreground
        // image card, leaving the camera/map arrangement and Swap state intact.
        templateVisible = true
        templateOverlay.visibility = View.VISIBLE
        stampImageUri = uri
        stampImageBitmap = null
        useLiveMapInTemplate = false
        mapView.visibility = if (mapEnabled && !swappedImages) View.VISIBLE else View.GONE
        mapCardVisible = true
        mapPreview.setImageURI(uri)
        mapPreview.visibility = View.VISIBLE
        updateStandaloneMapVisibility()
        loadSelectedMapImage(uri)
        changeImageOverlay.visibility = View.GONE
        findViewById<TextView>(R.id.editor_drag_hint).apply {
            text = getString(R.string.small_image_updated)
            setTextColor(0xFF9BE2C8.toInt())
        }
        setSelectedTab(if (toolsCameraMode) R.id.editor_tab_photo else R.id.editor_tab_details)
    }

    private fun chooseStampPosition() {
        templateVisible = true
        templateOverlay.visibility = View.VISIBLE
        AlertDialog.Builder(this)
            .setTitle(R.string.position)
            .setSingleChoiceItems(POSITION_LABELS, stampPositionIndex) { dialog, selected ->
                stampPositionIndex = selected
                val customization = stampCustomization ?: StampCustomizationStore.read(this)
                stampCustomization = customization.copy(
                    position = when (selected) {
                        0 -> TemplatePosition.TOP
                        1 -> TemplatePosition.MIDDLE
                        else -> TemplatePosition.BOTTOM
                    }
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

        params.width = ConstraintLayout.LayoutParams.MATCH_PARENT

        when (stampPositionIndex) {
            0 -> { // Top
                params.topToBottom = R.id.editor_tabs
                params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            1 -> { // Middle
                params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            else -> { // Bottom
                params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
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
        templateVisible = true
        templateOverlay.visibility = View.VISIBLE
        changeImageOverlay.visibility = View.GONE
        mapCardVisible = true
        swappedImages = false
        useLiveMapInTemplate = true
        photoView.setImageURI(sourcePhotoUri)
        mapView.visibility = if (mapEnabled) View.VISIBLE else View.GONE
        updateStandaloneMapVisibility()
        mapFallback.visibility = View.GONE
        showCurrentLocationMapPreview()
        updateTemplateFloatingImage()
        setSelectedTab(R.id.editor_tab_map)
    }

    private fun toggleSwappedImages() {
        changeImageOverlay.visibility = View.GONE
        swappedImages = !swappedImages
        if (swappedImages) {
            latestMapBitmap?.let(photoView::setImageBitmap)
                ?: photoView.setImageResource(R.drawable.home_map_view)
            mapView.visibility = View.GONE
            mapFallback.visibility = View.GONE
            if (stampImageUri != null) showStampImagePreview()
            else {
                mapPreview.setImageURI(sourcePhotoUri)
                mapPreview.visibility = View.VISIBLE
            }
        } else {
            photoView.setImageURI(sourcePhotoUri)
            mapView.visibility = if (mapEnabled) View.VISIBLE else View.GONE
            if (stampImageUri != null) showStampImagePreview()
            else showCurrentLocationMapPreview()
        }
        updateTemplateFloatingImage()
        setSelectedTab(R.id.editor_tab_position)
    }

    private fun showChangeImageMode() {
        swappedImages = false
        photoView.setImageURI(sourcePhotoUri)
        mapView.visibility = if (mapEnabled) View.VISIBLE else View.GONE
        mapCardVisible = true
        updateStandaloneMapVisibility()
        showStampImagePreview()
        if (mapCard.visibility == View.VISIBLE) {
            changeImageOverlay.visibility = View.VISIBLE
        } else {
            launchStampImagePicker()
        }
        setSelectedTab(R.id.editor_tab_photo)
    }

    private fun openFullscreenPreview() {
        val file = createEditorPreviewFile() ?: return
        fullscreenPreviewLauncher.launch(
            Intent(this, CameraFullscreenPreviewActivity::class.java).apply {
                putExtra(CameraFullscreenPreviewActivity.EXTRA_PREVIEW_PATH, file.absolutePath)
            }
        )
    }

    private fun createEditorPreviewFile(): File? {
        if (mediaFrame.width <= 0 || mediaFrame.height <= 0) return null
        val fullscreenButton = findViewById<View>(R.id.editor_fullscreen)
        val fullscreenVisibility = fullscreenButton.visibility
        val changeImageVisibility = changeImageOverlay.visibility
        val previewBitmap = Bitmap.createBitmap(
            mediaFrame.width,
            mediaFrame.height,
            Bitmap.Config.ARGB_8888
        )
        try {
            fullscreenButton.visibility = View.GONE
            changeImageOverlay.visibility = View.GONE
            val canvas = Canvas(previewBitmap)
            mediaFrame.draw(canvas)
            if (mapCardVisible && mapCard.visibility == View.VISIBLE && mapCard.alpha > 0f) {
                canvas.save()
                canvas.translate(mapCard.x - mediaFrame.x, mapCard.y - mediaFrame.y)
                mapCard.draw(canvas)
                canvas.restore()
            }
        } finally {
            fullscreenButton.visibility = fullscreenVisibility
            changeImageOverlay.visibility = changeImageVisibility
        }

        val file = File(cacheDir, "gps-editor-preview-${System.currentTimeMillis()}.png")
        return try {
            FileOutputStream(file).use { output ->
                check(previewBitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            file
        } catch (error: Exception) {
            Log.e(LOG_TAG, "Could not create editor preview", error)
            file.delete()
            null
        } finally {
            previewBitmap.recycle()
        }
    }

    private fun toggleLocationDetails() {
        templateVisible = true
        templateOverlay.visibility = View.VISIBLE
        changeImageOverlay.visibility = View.GONE
        swappedImages = false
        useLiveMapInTemplate = stampImageUri == null
        stampCustomization = (stampCustomization ?: StampCustomizationStore.read(this)).copy(
            addressMode = com.gps.map.camera.and.time.stamp.ui.stamptemplate.TemplateAddressMode.AUTOMATIC,
            addressDetail = com.gps.map.camera.and.time.stamp.ui.stamptemplate.TemplateAddressDetail.FULL,
            showCoordinates = true
        )
        photoView.setImageURI(sourcePhotoUri)
        mapView.visibility = if (mapEnabled) View.VISIBLE else View.GONE
        mapCardVisible = true
        updateStandaloneMapVisibility()
        mapFallback.visibility = View.GONE
        showStampImagePreview()
        updateTemplateFloatingImage()
        refreshTemplateOverlay()
        setSelectedTab(R.id.editor_tab_location)
    }

    private fun showDetailsMode() {
        templateVisible = !templateVisible
        templateOverlay.visibility = if (templateVisible) View.VISIBLE else View.GONE
        changeImageOverlay.visibility = View.GONE
        swappedImages = false
        photoView.setImageURI(sourcePhotoUri)
        mapView.visibility = if (mapEnabled) View.VISIBLE else View.GONE
        mapCardVisible = true
        updateStandaloneMapVisibility()
        mapFallback.visibility = View.GONE
        showStampImagePreview()
        updateTemplateFloatingImage()
        if (templateVisible) refreshTemplateOverlay()
        setSelectedTab(R.id.editor_tab_details)
    }

    private fun showCurrentLocationMapPreview() {
        latestMapBitmap?.let(mapPreview::setImageBitmap)
            ?: mapPreview.setImageResource(R.drawable.home_map_view)
        mapPreview.visibility = View.VISIBLE
    }

    private fun showStampImagePreview() {
        if (stampImageUri != null) {
            stampImageBitmap?.let(mapPreview::setImageBitmap)
                ?: mapPreview.setImageURI(stampImageUri)
            mapPreview.visibility = View.VISIBLE
        } else {
            showCurrentLocationMapPreview()
        }
    }

    private fun openMapImageGallery() {
        galleryPicker.launch("image/*")
    }

    private fun launchStampImagePicker() {
        stampImagePicker.launch(Intent(this, SmallImagePickerActivity::class.java))
    }

    private fun updateTemplateFloatingImage() {
        when {
            !useLiveMapInTemplate && stampImageUri != null -> {
                stampImageBitmap?.let(templateOverlay::setFloatingImageBitmap)
                    ?: loadTemplateFloatingImage(stampImageUri!!)
            }
            swappedImages -> loadTemplateFloatingImage(sourcePhotoUri)
            else -> templateOverlay.setFloatingImageBitmap(latestMapBitmap)
        }
    }

    private fun loadSelectedMapImage(uri: Uri) {
        galleryImageExecutor.execute {
            val bitmap = runCatching { decodePhotoWithOrientation(uri) }
                .onFailure { Log.e(LOG_TAG, "Could not read selected gallery image", it) }
                .getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed || stampImageUri != uri) {
                    bitmap?.recycle()
                } else if (bitmap == null) {
                    Toast.makeText(this, "Could not load the selected image", Toast.LENGTH_LONG).show()
                } else {
                    stampImageBitmap = bitmap
                    mapPreview.setImageBitmap(bitmap)
                    mapPreview.visibility = View.VISIBLE
                    templateOverlay.setFloatingImageBitmap(bitmap)
                }
            }
        }
    }

    private fun loadTemplateFloatingImage(uri: Uri) {
        addressExecutor.execute {
            val bitmap = runCatching { decodePhotoWithOrientation(uri) }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    bitmap?.recycle()
                } else {
                    templateOverlay.setFloatingImageBitmap(bitmap)
                }
            }
        }
    }

    private fun positionToolsMapCard() {
        if (!toolsCameraMode) return
        val params = mapCard.layoutParams as ConstraintLayout.LayoutParams
        params.width = dp(84)
        params.height = dp(100)
        params.startToStart = ConstraintLayout.LayoutParams.UNSET
        params.endToEnd = R.id.editor_media_frame
        params.topToTop = ConstraintLayout.LayoutParams.UNSET
        params.bottomToBottom = R.id.editor_media_frame
        params.marginStart = 0
        params.marginEnd = dp(12)
        params.bottomMargin = dp(24)
        mapCard.layoutParams = params
    }

    /**
     * Templates that contain a map icon render the map inside the shared stamp
     * overlay. Keep the legacy floating map card only for templates without a
     * template map so the editor and exported image never show two maps.
     */
    private fun updateStandaloneMapVisibility() {
        val usesTemplateMap = stampCustomization?.showMapIcon == true
        val showStandaloneMap = mapCardVisible && (if (toolsCameraMode) stampImageUri != null else !usesTemplateMap)
        // Keep the MapView attached so Google can render a real snapshot for
        // the template slot, but make it transparent whenever the template is
        // the visible floating map so there is never a duplicate card.
        mapCard.visibility = View.VISIBLE
        mapCard.alpha = if (showStandaloneMap) 1f else 0f
        mapCard.isClickable = showStandaloneMap
        mapPreview.isClickable = showStandaloneMap
    }

    private fun setSelectedTab(selectedId: Int) {
        listOf(R.id.editor_tab_details, R.id.editor_tab_position, R.id.editor_tab_map,
            R.id.editor_tab_location, R.id.editor_tab_photo).forEach { id ->
            val tab = findViewById<TextView>(id)
            tab.setTextColor(
                if (id == selectedId) 0xFF67A2FF.toInt() else 0xFFAFBDCE.toInt()
            )
            if (id == R.id.editor_tab_position) {
                tab.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    if (id == selectedId) R.drawable.ic_editor_position_selected
                    else R.drawable.ic_editor_position,
                    0,
                    0
                )
            } else if (id == R.id.editor_tab_details) {
                tab.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    if (id == selectedId) R.drawable.ic_editor_detail_selected
                    else R.drawable.icon_container_detail,
                    0,
                    0
                )
            } else if (id == R.id.editor_tab_photo) {
                tab.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    if (id == selectedId) R.drawable.ic_editor_photo_selected
                    else R.drawable.ic_editor_photo2,
                    0,
                    0
                )
            } else if (id == R.id.editor_tab_map) {
                tab.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    if (id == selectedId) R.drawable.ic_editor_map_selected
                    else R.drawable.ic_editor_map,
                    0,
                    0
                )
            } else if (id == R.id.editor_tab_location) {
                tab.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    if (id == selectedId) R.drawable.ic_editor_location_selected
                    else R.drawable.ic_editor_location,
                    0,
                    0
                )
            }
        }
    }

    private fun saveEditedPhoto() {
        if (isSaving) return
        isSaving = true
        captureEditorTransforms()
        findViewById<TextView>(R.id.editor_save).isEnabled = false
        createEditorPreviewFile()?.let { previewFile ->
            startActivity(Intent(this, CameraSavingActivity::class.java).apply {
                putExtra(CameraSavingActivity.EXTRA_PREVIEW_PATH, previewFile.absolutePath)
            })
        }
        val currentMap = googleMap
        if (currentMap == null) {
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
            val visualLeft = mapCard.x - mediaFrame.x + mapCard.width * (1f - scale) / 2f
            val visualTop = mapCard.y - mediaFrame.y + mapCard.height * (1f - scale) / 2f
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
            val liveMapBitmap = mapBitmap ?: latestMapBitmap
            baseBitmap = if (swappedImages) {
                val swappedSource = liveMapBitmap
                    ?: BitmapFactory.decodeResource(resources, R.drawable.home_map_view)
                swappedSource?.copy(Bitmap.Config.ARGB_8888, true)
            } else {
                decodePhotoWithOrientation(sourcePhotoUri)
            } ?: error("Could not read the editor image")
            failureStage = "drawing the GPS stamp"
            stampedBitmap = drawStamp(baseBitmap, liveMapBitmap)
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
                    CameraSavingActivity.finishSaving()
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
                CameraSavingActivity.finishSaving()
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
        val floatingBitmap = when {
            !useLiveMapInTemplate && stampImageUri != null -> decodePhotoWithOrientation(stampImageUri!!)
            swappedImages -> decodePhotoWithOrientation(sourcePhotoUri)
            else -> mapBitmap ?: latestMapBitmap
        }
        if (floatingBitmap != null && mapCardVisible && mapCard.visibility == View.VISIBLE && mapCard.alpha > 0f) {
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
            canvas.drawBitmap(floatingBitmap, null, mapRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        }
        if (templateVisible) {
            val customization = stampCustomization ?: StampCustomizationStore.read(this)
            StampRenderer.draw(
                canvas = canvas,
                bounds = RectF(0f, 0f, photo.width.toFloat(), photo.height.toFloat()),
                settings = customization,
                data = StampRenderData(
                    address = address
                        .takeIf { it.isNotBlank() && it != getString(R.string.address_loading) }
                        ?: if (!latitude.isNaN() && !longitude.isNaN()) {
                            String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude)
                        } else {
                            getString(R.string.location_unavailable)
                        },
                    latitude = latitude,
                    longitude = longitude,
                    capturedAtText = capturedAt.takeIf { it.isNotBlank() },
                    weather = if (customization.showWeather) weatherSnapshot?.uiText else null
                ),
                // The template's map/image slot is the same floating image
                // used by Swap and Change Image. Do not force it back to the
                // live map when the user selected a replacement image.
                map = floatingBitmap ?: mapBitmap
            )
        }
        if (floatingBitmap != null && floatingBitmap !== mapBitmap && !floatingBitmap.isRecycled) {
            floatingBitmap.recycle()
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
