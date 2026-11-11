package com.gps.map.camera.and.time.stamp.ui.mapview

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Size
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.bases.ext.isNetwork
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlace
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlaceSearch
import com.gps.map.camera.and.time.stamp.ui.gallery.PhotoGalleryActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import android.location.LocationManager
import android.view.animation.LinearInterpolator

class MapViewActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private enum class MapState {
        NO_PLACES,
        OFFLINE,
        MAP_FAILED,
        LOCATION_TURNED_OFF,
        LOCATION_ACCESS_NEEDED
    }

    private lateinit var mapView: MapView
    private lateinit var searchInput: EditText
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchResults: RecyclerView
    private lateinit var searchModePanel: View
    private lateinit var resultsContent: View
    private lateinit var resultsTitle: TextView
    private lateinit var mapLoading: View
    private lateinit var mapLoadingIcon: ImageView
    private lateinit var statePanel: View
    private lateinit var stateIcon: ImageView
    private lateinit var stateTitle: TextView
    private lateinit var stateMessage: TextView
    private lateinit var statePrimaryButton: TextView
    private lateinit var stateSecondaryButton: TextView
    private lateinit var stylePanel: View
    private lateinit var standardStyleButton: TextView
    private lateinit var satelliteStyleButton: TextView
    private lateinit var placeDetails: View
    private lateinit var placeTitle: TextView
    private lateinit var placeAddress: TextView
    private lateinit var placeAdapter: MapPlaceAdapter
    private lateinit var locationCount: TextView
    private var googleMap: GoogleMap? = null
    private var searchJob: Job? = null
    private var photoJob: Job? = null
    private var locationAddressJob: Job? = null
    private var mapLoadingJob: Job? = null
    private var mapLoadTimeoutJob: Job? = null
    private var mapLoadingAnimator: ObjectAnimator? = null
    private var searchMarker: Marker? = null
    private var activeSharePlace: EarthPlace? = null
    private var pendingCurrentPlace: EarthPlace? = null
    private var isMapLoading = false
    private var mapState: MapState? = null
    private var userLocationShown = false
    private val photoMarkers = mutableMapOf<Marker, PhotoLocation>()
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadPhotoLocations() else updateLocationCount(0)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            if (isLocationEnabled()) {
                enableLocationLayer()
                moveToCurrentLocation()
            } else {
                showMapState(MapState.LOCATION_TURNED_OFF)
            }
        } else {
            showMapState(MapState.LOCATION_ACCESS_NEEDED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_view)

        mapView = findViewById(R.id.map_view)
        searchInput = findViewById(R.id.map_view_search_input)
        searchProgress = findViewById(R.id.map_view_search_progress)
        searchResults = findViewById(R.id.map_view_search_results)
        searchModePanel = findViewById(R.id.map_view_search_mode)
        resultsContent = findViewById(R.id.map_view_results_content)
        resultsTitle = findViewById(R.id.map_view_results_title)
        mapLoading = findViewById(R.id.map_view_loading)
        mapLoadingIcon = findViewById(R.id.map_view_loading_icon)
        statePanel = findViewById(R.id.map_view_state_panel)
        stateIcon = findViewById(R.id.earth_view_state_icon)
        stateTitle = findViewById(R.id.earth_view_state_title)
        stateMessage = findViewById(R.id.earth_view_state_message)
        statePrimaryButton = findViewById(R.id.earth_view_state_primary_button)
        stateSecondaryButton = findViewById(R.id.earth_view_state_secondary_button)
        stylePanel = findViewById(R.id.map_view_style_panel)
        standardStyleButton = findViewById(R.id.map_view_style_standard)
        satelliteStyleButton = findViewById(R.id.map_view_style_satellite)
        placeDetails = findViewById(R.id.map_view_place_details)
        placeTitle = findViewById(R.id.map_view_place_title)
        placeAddress = findViewById(R.id.map_view_place_address)
        locationCount = findViewById(R.id.map_view_location_count)

        searchResults.layoutManager = LinearLayoutManager(this)
        placeAdapter = MapPlaceAdapter(::moveToPlace)
        searchResults.adapter = placeAdapter

        findViewById<View>(R.id.map_view_back).setOnClickListener { finish() }
        findViewById<View>(R.id.map_view_search_button).setOnClickListener {
            submitSearch()
        }
        findViewById<View>(R.id.map_view_search_places_button).setOnClickListener {
            submitSearch()
        }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch()
                true
            } else {
                false
            }
        }
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showSearchMode()
        }
        findViewById<View>(R.id.map_view_recent_station).setOnClickListener {
            selectRecentSearch(R.string.map_view_recent_station)
        }
        findViewById<View>(R.id.map_view_recent_palace).setOnClickListener {
            selectRecentSearch(R.string.map_view_recent_palace)
        }
        findViewById<View>(R.id.map_view_recent_gion).setOnClickListener {
            selectRecentSearch(R.string.map_view_recent_gion)
        }
        findViewById<View>(R.id.map_view_my_location).setOnClickListener {
            if (hasLocationPermission()) {
                if (isLocationEnabled()) {
                    enableLocationLayer()
                    moveToCurrentLocation()
                } else {
                    showMapState(MapState.LOCATION_TURNED_OFF)
                }
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
        findViewById<View>(R.id.map_view_layers).setOnClickListener {
            setStylePanelMode(stylePanel.visibility != View.VISIBLE)
        }
        standardStyleButton.setOnClickListener {
            googleMap?.mapType = GoogleMap.MAP_TYPE_NORMAL
            updateStyleButtons()
        }
        satelliteStyleButton.setOnClickListener {
            googleMap?.mapType = GoogleMap.MAP_TYPE_SATELLITE
            updateStyleButtons()
        }
        findViewById<View>(R.id.map_view_gallery_button).setOnClickListener {
            openPhotoGallery()
        }
        findViewById<View>(R.id.map_view_share_location).setOnClickListener {
            shareSelectedPlace()
        }
        findViewById<View>(R.id.map_view_search_another_place).setOnClickListener {
            searchAnotherPlace()
        }
        statePrimaryButton.setOnClickListener { handleMapStatePrimaryAction() }
        stateSecondaryButton.setOnClickListener { handleMapStateSecondaryAction() }

        mapView.onCreate(savedInstanceState)
        setMapLoading(true)
        if (!isNetwork()) showMapState(MapState.OFFLINE)
        mapLoadTimeoutJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(MAP_LOAD_TIMEOUT_MS)
            if (googleMap == null && mapState == null) showMapState(MapState.MAP_FAILED)
        }
        mapView.getMapAsync(this)
        requestMediaPermissionIfNeeded()
    }

    override fun onMapReady(readyMap: GoogleMap) {
        mapLoadTimeoutJob?.cancel()
        googleMap = readyMap
        readyMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        readyMap.uiSettings.isZoomControlsEnabled = false
        readyMap.uiSettings.isMapToolbarEnabled = false
        readyMap.uiSettings.isCompassEnabled = false
        readyMap.setOnMarkerClickListener(this)
        updateStyleButtons()
        if (!isNetwork()) {
            setMapLoading(false)
            showMapState(MapState.OFFLINE)
            return
        }
        if (mapState == MapState.OFFLINE) hideMapState()
        requestLocationPermissionIfNeeded()
        loadPhotoLocations()
        mapLoadingJob?.cancel()
        mapLoadingJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(MAP_LOADING_MIN_MS)
            setMapLoading(false)
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) {
            if (!isLocationEnabled()) {
                showMapState(MapState.LOCATION_TURNED_OFF)
                return
            }
            enableLocationLayer()
            moveToCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun requestMediaPermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadPhotoLocations()
        } else {
            mediaPermissionLauncher.launch(permission)
        }
    }

    private fun loadPhotoLocations() {
        val map = googleMap ?: return
        photoJob?.cancel()
        photoJob = lifecycleScope.launch {
            val locations = withContext(Dispatchers.IO) { queryPhotoLocations() }
            photoMarkers.keys.toList().forEach { it.remove() }
            photoMarkers.clear()
            locations.forEach { photo -> addPhotoMarker(map, photo) }
            updateLocationCount(locations.size)
            if (locations.isNotEmpty() && !userLocationShown) moveCameraToPhotos(locations)
        }
    }

    private fun queryPhotoLocations(): List<PhotoLocation> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val locations = mutableListOf<PhotoLocation>()
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val coordinates = readCoordinates(uri) ?: continue
                locations += PhotoLocation(
                    uri = uri,
                    name = cursor.getString(nameColumn).orEmpty(),
                    latitude = coordinates[0].toDouble(),
                    longitude = coordinates[1].toDouble()
                )
            }
        }
        return locations
    }

    private fun readCoordinates(uri: Uri): FloatArray? = try {
        contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val coordinates = FloatArray(2)
            val exif = android.media.ExifInterface(descriptor.fileDescriptor)
            if (exif.getLatLong(coordinates)) coordinates else null
        }
    } catch (_: Exception) {
        null
    }

    private fun addPhotoMarker(map: GoogleMap, photo: PhotoLocation) {
        val marker = map.addMarker(
            MarkerOptions()
                .position(LatLng(photo.latitude, photo.longitude))
                .title(photo.name)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        ) ?: return
        photoMarkers[marker] = photo
        lifecycleScope.launch(Dispatchers.IO) {
            val thumbnail = loadThumbnail(photo.uri)
            if (thumbnail != null && !isDestroyed) {
                val markerBitmap = createPhotoMarker(thumbnail)
                withContext(Dispatchers.Main) {
                    marker.setIcon(BitmapDescriptorFactory.fromBitmap(markerBitmap))
                }
            }
        }
    }

    private fun loadThumbnail(uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.loadThumbnail(uri, Size(88, 88), null)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private fun createPhotoMarker(source: Bitmap): Bitmap {
        val size = 112
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = getColor(R.color.home_accent)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 22f, 22f, paint)
        val image = Bitmap.createScaledBitmap(source, 88, 88, true)
        canvas.drawBitmap(image, 12f, 12f, paint)
        return bitmap
    }

    private fun moveCameraToPhotos(locations: List<PhotoLocation>) {
        val map = googleMap ?: return
        if (locations.size == 1) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(locations[0].latitude, locations[0].longitude),
                    PHOTO_ZOOM
                )
            )
            return
        }
        val boundsBuilder = LatLngBounds.Builder()
        locations.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
    }

    private fun searchPlace(query: String) {
        val placeQuery = query.trim()
        if (placeQuery.isEmpty()) return
        hideKeyboard()
        searchJob?.cancel()
        searchResults.visibility = View.GONE
        resultsContent.visibility = View.GONE
        setStylePanelMode(false)
        setMapResultsMode(false)
        setPlaceDetailsMode(false)
        hideMapState()
        searchProgress.visibility = View.VISIBLE
        searchJob = lifecycleScope.launch {
            if (!isNetwork()) {
                searchProgress.visibility = View.GONE
                showMapState(MapState.OFFLINE)
                return@launch
            }
            val result = runCatching {
                withContext(Dispatchers.IO) { EarthPlaceSearch.search(placeQuery) }
            }
            searchProgress.visibility = View.GONE
            result.onSuccess { places ->
                if (places.isEmpty()) {
                    showMapState(MapState.NO_PLACES)
                } else {
                    placeAdapter.submitList(places.take(MAX_SEARCH_RESULTS))
                    searchResults.visibility = View.VISIBLE
                    resultsTitle.text = getString(R.string.map_view_places_in, placeQuery)
                    setMapResultsMode(true)
                }
            }.onFailure {
                showMapState(if (isNetwork()) MapState.MAP_FAILED else MapState.OFFLINE)
            }
        }
    }

    private fun submitSearch() {
        hideSearchMode()
        searchPlace(searchInput.text.toString())
    }

    private fun showSearchMode() {
        hideMapState()
        searchResults.visibility = View.GONE
        resultsContent.visibility = View.GONE
        setStylePanelMode(false)
        setPlaceDetailsMode(false)
        searchModePanel.visibility = View.VISIBLE
        findViewById<View>(R.id.map_view_container).visibility = View.GONE
    }

    private fun hideSearchMode() {
        searchModePanel.visibility = View.GONE
        findViewById<View>(R.id.map_view_container).visibility = View.VISIBLE
    }

    private fun showMapState(state: MapState) {
        mapState = state
        mapLoadingJob?.cancel()
        setMapLoading(false)
        searchResults.visibility = View.GONE
        resultsContent.visibility = View.GONE
        stylePanel.visibility = View.GONE
        placeDetails.visibility = View.GONE
        searchModePanel.visibility = View.GONE
        findViewById<View>(R.id.map_view_container).visibility = View.GONE
        statePanel.visibility = View.VISIBLE

        stateIcon.setImageResource(
            when (state) {
                MapState.NO_PLACES -> R.drawable.ic_search
                MapState.OFFLINE -> R.drawable.ic_wifi_off
                MapState.MAP_FAILED -> R.drawable.ic_earth_map_error
                MapState.LOCATION_TURNED_OFF -> R.drawable.ic_my_location
                MapState.LOCATION_ACCESS_NEEDED -> R.drawable.ic_location_access
            }
        )
        stateTitle.setText(
            when (state) {
                MapState.NO_PLACES -> R.string.map_view_no_places_title
                MapState.OFFLINE -> R.string.map_view_offline_title
                MapState.MAP_FAILED -> R.string.map_view_map_failed_title
                MapState.LOCATION_TURNED_OFF -> R.string.map_view_location_off_title
                MapState.LOCATION_ACCESS_NEEDED -> R.string.map_view_location_access_title
            }
        )
        stateMessage.setText(
            when (state) {
                MapState.NO_PLACES -> R.string.map_view_no_places_message
                MapState.OFFLINE -> R.string.map_view_offline_message
                MapState.MAP_FAILED -> R.string.map_view_map_failed_message
                MapState.LOCATION_TURNED_OFF -> R.string.map_view_location_off_message
                MapState.LOCATION_ACCESS_NEEDED -> R.string.map_view_location_access_message
            }
        )
        statePrimaryButton.setText(
            when (state) {
                MapState.NO_PLACES -> R.string.map_view_search_again
                MapState.OFFLINE, MapState.MAP_FAILED -> R.string.map_view_retry
                MapState.LOCATION_TURNED_OFF -> R.string.map_view_turn_on_location
                MapState.LOCATION_ACCESS_NEEDED -> R.string.map_view_open_settings
            }
        )
        stateSecondaryButton.setText(
            when (state) {
                MapState.NO_PLACES -> R.string.map_view_explore_map
                MapState.OFFLINE -> R.string.map_view_back_action
                MapState.MAP_FAILED -> R.string.map_view_search_another
                MapState.LOCATION_TURNED_OFF,
                MapState.LOCATION_ACCESS_NEEDED -> R.string.map_view_search_manually
            }
        )
        stateSecondaryButton.background = when (state) {
            MapState.NO_PLACES -> ContextCompat.getDrawable(this, R.drawable.bg_earth_street_view_button)
            MapState.MAP_FAILED,
            MapState.LOCATION_TURNED_OFF,
            MapState.LOCATION_ACCESS_NEEDED -> ContextCompat.getDrawable(this, R.drawable.bg_earth_place_card)
            MapState.OFFLINE -> null
        }
        stateSecondaryButton.setTextColor(
            ContextCompat.getColor(
                this,
                if (state == MapState.OFFLINE) R.color.secondary_text_color else R.color.primary_text_color
            )
        )
    }

    private fun hideMapState() {
        mapState = null
        statePanel.visibility = View.GONE
        findViewById<View>(R.id.map_view_container).visibility = View.VISIBLE
    }

    private fun handleMapStatePrimaryAction() {
        when (mapState) {
            MapState.NO_PLACES -> {
                hideMapState()
                hideSearchMode()
                searchPlace(searchInput.text.toString())
            }
            MapState.OFFLINE,
            MapState.MAP_FAILED -> retryMap()
            MapState.LOCATION_TURNED_OFF -> openLocationSettings()
            MapState.LOCATION_ACCESS_NEEDED -> openAppSettings()
            null -> Unit
        }
    }

    private fun handleMapStateSecondaryAction() {
        when (mapState) {
            MapState.NO_PLACES -> {
                hideMapState()
                hideSearchMode()
                setMapLoading(false)
            }
            MapState.MAP_FAILED -> {
                hideMapState()
                showSearchMode()
            }
            MapState.LOCATION_TURNED_OFF,
            MapState.LOCATION_ACCESS_NEEDED -> {
                hideMapState()
                showSearchMode()
            }
            MapState.OFFLINE -> finish()
            null -> Unit
        }
    }

    private fun retryMap() {
        if (!isNetwork()) {
            showMapState(MapState.OFFLINE)
            return
        }
        hideMapState()
        setMapLoading(true)
        mapLoadingJob?.cancel()
        mapLoadingJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(MAP_LOADING_MIN_MS)
            if (googleMap == null) {
                showMapState(MapState.MAP_FAILED)
            } else {
                setMapLoading(false)
            }
        }
    }

    private fun openLocationSettings() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun setMapResultsMode(active: Boolean) {
        setMapLoading(false)
        setStylePanelMode(false)
        val container = findViewById<View>(R.id.map_view_container)
        val layoutParams = container.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        if (active) {
            layoutParams.height = dp(425)
            layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            resultsContent.visibility = View.VISIBLE
        } else {
            layoutParams.height = 0
            layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            resultsContent.visibility = View.GONE
        }
        container.layoutParams = layoutParams
    }

    private fun setPlaceDetailsMode(active: Boolean, place: EarthPlace? = null) {
        if (active && isMapLoading) {
            pendingCurrentPlace = place
            return
        }
        setStylePanelMode(false)
        val container = findViewById<View>(R.id.map_view_container)
        val layoutParams = container.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        if (active && place != null) {
            layoutParams.height = dp(392)
            layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            activeSharePlace = place
            placeTitle.text = place.name
            placeAddress.text = formatPlaceAddress(place)
            placeDetails.visibility = View.VISIBLE
        } else {
            layoutParams.height = 0
            layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            activeSharePlace = null
            placeDetails.visibility = View.GONE
        }
        container.layoutParams = layoutParams
    }

    private fun setMapLoading(loading: Boolean) {
        isMapLoading = loading
        mapLoading.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            startMapLoadingAnimation()
        } else {
            stopMapLoadingAnimation()
        }

        val container = findViewById<View>(R.id.map_view_container)
        val layoutParams = container.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        if (loading) {
            container.visibility = View.VISIBLE
            layoutParams.height = dp(LOADING_MAP_HEIGHT_DP)
            layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
        } else if (pendingCurrentPlace != null) {
            val place = pendingCurrentPlace
            pendingCurrentPlace = null
            setPlaceDetailsMode(true, place)
            return
        } else if (stylePanel.visibility != View.VISIBLE &&
            resultsContent.visibility != View.VISIBLE &&
            placeDetails.visibility != View.VISIBLE
        ) {
            layoutParams.height = 0
            layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        }
        container.layoutParams = layoutParams
    }

    private fun startMapLoadingAnimation() {
        if (mapLoadingAnimator?.isRunning == true) return
        mapLoadingAnimator = ObjectAnimator.ofFloat(mapLoadingIcon, View.ROTATION, 0f, 360f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopMapLoadingAnimation() {
        mapLoadingAnimator?.cancel()
        mapLoadingAnimator = null
        mapLoadingIcon.rotation = 0f
    }

    private fun setStylePanelMode(active: Boolean) {
        val container = findViewById<View>(R.id.map_view_container)
        val layoutParams = container.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        if (active) {
            searchResults.visibility = View.GONE
            resultsContent.visibility = View.GONE
            placeDetails.visibility = View.GONE
            container.visibility = View.VISIBLE
            layoutParams.height = dp(STYLE_MAP_HEIGHT_DP)
            layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            stylePanel.visibility = View.VISIBLE
        } else {
            stylePanel.visibility = View.GONE
            if (container.visibility == View.VISIBLE) {
                layoutParams.height = 0
                layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            }
        }
        container.layoutParams = layoutParams
    }

    private fun updateStyleButtons() {
        val satelliteSelected = googleMap?.mapType == GoogleMap.MAP_TYPE_SATELLITE
        standardStyleButton.background = ContextCompat.getDrawable(
            this,
            if (satelliteSelected) R.drawable.bg_map_style_unselected else R.drawable.bg_map_style_selected
        )
        satelliteStyleButton.background = ContextCompat.getDrawable(
            this,
            if (satelliteSelected) R.drawable.bg_map_style_selected else R.drawable.bg_map_style_unselected
        )
        standardStyleButton.setTextColor(
            ContextCompat.getColor(this, if (satelliteSelected) R.color.secondary_text_color else R.color.home_accent)
        )
        satelliteStyleButton.setTextColor(
            ContextCompat.getColor(this, if (satelliteSelected) R.color.home_accent else R.color.secondary_text_color)
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun selectRecentSearch(stringRes: Int) {
        searchInput.setText(getString(stringRes))
        searchInput.setSelection(searchInput.text.length)
    }

    private fun moveToPlace(place: EarthPlace) {
        val map = googleMap ?: return
        val position = LatLng(place.latitude, place.longitude)
        searchResults.visibility = View.GONE
        resultsContent.visibility = View.GONE
        setPlaceDetailsMode(true, place)
        searchInput.setText(place.name)
        searchInput.clearFocus()
        searchMarker?.remove()
        searchMarker = map.addMarker(MarkerOptions().position(position).title(place.name))
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, SEARCH_ZOOM))
    }

    private fun searchAnotherPlace() {
        setMapLoading(false)
        setStylePanelMode(false)
        setPlaceDetailsMode(false)
        setMapResultsMode(false)
        searchInput.setText("")
        searchInput.requestFocus()
        showSearchMode()
    }

    private fun shareSelectedPlace() {
        val place = activeSharePlace ?: return
        val address = formatPlaceAddress(place)
        val shareText = getString(
            R.string.map_view_share_text,
            place.name,
            address,
            place.latitude,
            place.longitude
        )
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.map_view_share_subject, place.name))
            putExtra(Intent.EXTRA_TEXT, shareText)
        }, getString(R.string.map_view_share_location)))
    }

    private fun formatPlaceAddress(place: EarthPlace): String {
        val parts = place.address.split(',').map(String::trim).filter(String::isNotEmpty)
        return when {
            parts.size >= 2 -> parts.takeLast(2).joinToString(", ")
            parts.firstOrNull().equals(place.name, ignoreCase = true) -> "Country"
            else -> place.address
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        if (marker == searchMarker) return true
        val photo = photoMarkers[marker] ?: return false
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(photo.uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, R.string.map_view_photo_unavailable, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun enableLocationLayer() {
        val map = googleMap ?: return
        if (hasLocationPermission()) {
            try {
                map.isMyLocationEnabled = true
            } catch (_: SecurityException) {
            }
        }
    }

    private fun moveToCurrentLocation() {
        if (!hasLocationPermission()) return
        locationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                userLocationShown = true
                val currentPlace = EarthPlace(
                    name = getString(R.string.map_view_current_location_title),
                    address = getString(R.string.map_view_current_location_address),
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                setPlaceDetailsMode(true, currentPlace)
                searchMarker?.remove()
                searchMarker = googleMap?.addMarker(
                    MarkerOptions()
                        .position(LatLng(location.latitude, location.longitude))
                        .title(currentPlace.name)
                )
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.latitude, location.longitude),
                        CURRENT_LOCATION_ZOOM
                    )
                )
                resolveCurrentLocationAddress(currentPlace)
            }
        }
    }

    private fun resolveCurrentLocationAddress(place: EarthPlace) {
        locationAddressJob?.cancel()
        locationAddressJob = lifecycleScope.launch {
            val address = withContext(Dispatchers.IO) {
                reverseGeocode(place.latitude, place.longitude)
            } ?: return@launch
            if (activeSharePlace?.latitude == place.latitude &&
                activeSharePlace?.longitude == place.longitude
            ) {
                val updatedPlace = place.copy(address = address)
                activeSharePlace = updatedPlace
                placeAddress.text = formatPlaceAddress(updatedPlace)
            }
        }
    }

    private fun reverseGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            val address = Geocoder(this, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?: return null
            listOfNotNull(
                address.locality ?: address.subAdminArea ?: address.adminArea,
                address.countryName
            ).distinct().joinToString(", ").ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean {
        val manager = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun updateLocationCount(count: Int) {
        locationCount.text = resources.getQuantityString(
            R.plurals.map_view_saved_places,
            count,
            count
        )
    }

    private fun openPhotoGallery() {
        startActivity(Intent(this, PhotoGalleryActivity::class.java))
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        when (mapState) {
            MapState.LOCATION_TURNED_OFF,
            MapState.LOCATION_ACCESS_NEEDED -> {
                if (hasLocationPermission() && isLocationEnabled()) {
                    hideMapState()
                    enableLocationLayer()
                    moveToCurrentLocation()
                }
            }
            else -> Unit
        }
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        searchJob?.cancel()
        photoJob?.cancel()
        mapLoadingJob?.cancel()
        mapLoadTimeoutJob?.cancel()
        mapLoadingAnimator?.cancel()
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    private data class PhotoLocation(
        val uri: Uri,
        val name: String,
        val latitude: Double,
        val longitude: Double
    )

    private companion object {
        const val SEARCH_ZOOM = 15f
        const val PHOTO_ZOOM = 14f
        const val CURRENT_LOCATION_ZOOM = 16f
        const val MAP_LOADING_MIN_MS = 1200L
        const val MAP_LOAD_TIMEOUT_MS = 10000L
        const val LOADING_MAP_HEIGHT_DP = 548
        const val STYLE_MAP_HEIGHT_DP = 508
        const val MAX_SEARCH_RESULTS = 5
    }
}
