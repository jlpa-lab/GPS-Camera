package com.gps.map.camera.and.time.stamp.ui.earthview

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.bases.ext.isNetwork
import com.gps.map.camera.and.time.stamp.ui.streetview.StreetViewActivity
import com.gps.map.camera.and.time.stamp.utils.location.GpsLocationProvider
import earth.worldwind.geom.Angle
import earth.worldwind.geom.AltitudeMode
import earth.worldwind.geom.LookAt
import earth.worldwind.geom.Offset
import earth.worldwind.geom.Position
import earth.worldwind.layer.BackgroundLayer
import earth.worldwind.layer.BlueMarbleLandsatLayer
import earth.worldwind.layer.RenderableLayer
import earth.worldwind.layer.atmosphere.AtmosphereLayer
import earth.worldwind.render.image.ImageConfig
import earth.worldwind.render.image.ImageSource
import earth.worldwind.render.image.ResamplingMode
import earth.worldwind.shape.Placemark
import earth.worldwind.shape.PlacemarkAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import android.text.Editable
import android.text.TextWatcher
import java.util.Locale

class EarthViewActivity : AppCompatActivity(), OnMapReadyCallback {

    private enum class EarthState {
        NO_PLACES,
        MAP_FAILED,
        LOCATION_UNAVAILABLE,
        OFFLINE
    }

    private lateinit var worldWindow: EarthWorldWindow
    private lateinit var map2dView: MapView
    private lateinit var searchIcon: View
    private lateinit var searchOverlay: View
    private lateinit var dimension2d: TextView
    private lateinit var dimension3d: TextView
    private lateinit var splitView: View
    private lateinit var mapContainer: FrameLayout
    private lateinit var screenTitle: TextView
    private lateinit var searchInput: EditText
    private lateinit var searchResults: RecyclerView
    private lateinit var earthControls: View
    private lateinit var streetViewButton: View
    private lateinit var resultStreetViewButton: View
    private lateinit var placeDetails: View
    private lateinit var placeTitle: TextView
    private lateinit var placeAddress: TextView
    private lateinit var mapLoading: View
    private lateinit var mapLoadingIcon: ImageView
    private lateinit var statePanel: View
    private lateinit var stateIcon: ImageView
    private lateinit var stateTitle: TextView
    private lateinit var stateMessage: TextView
    private lateinit var statePrimaryButton: TextView
    private lateinit var stateSecondaryButton: TextView
    private lateinit var placeAdapter: EarthViewPlaceAdapter

    private val markerLayer = RenderableLayer("Earth view marker")
    private var searchJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var mapLoadingJob: Job? = null
    private var mapLoadingAnimator: ObjectAnimator? = null
    private var updatingSearchText = false
    private var searchMode = false
    private var resultMode = false
    private var isMapLoading = false
    private var earthState: EarthState? = null
    private var failedSearchQuery: String? = null
    private var selectedPlace: EarthPlace? = null
    private var googleMap2d: GoogleMap? = null
    private var mapLifecycleEnabled = false
    private val locationProvider by lazy { GpsLocationProvider(this) }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            showDefaultEarthLocation()
        } else {
            showLocationUnavailable()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_earth_view)

        worldWindow = findViewById(R.id.earth_map_view)
        worldWindow.controller = EarthViewController(worldWindow, MIN_IMAGERY_RANGE_METERS)
        map2dView = findViewById(R.id.earth_map_2d_view)
        searchIcon = findViewById(R.id.earth_view_search_icon)
        searchOverlay = findViewById(R.id.earth_view_search_overlay)
        dimension2d = findViewById(R.id.earth_view_2d_button)
        dimension3d = findViewById(R.id.earth_view_3d_button)
        splitView = findViewById(R.id.earth_view_split_view)
        mapContainer = findViewById(R.id.earth_map_container)
        screenTitle = findViewById(R.id.earth_view_title)
        searchInput = findViewById(R.id.earth_view_search_input)
        searchResults = findViewById(R.id.earth_view_search_results)
        earthControls = findViewById(R.id.earth_view_controls)
        streetViewButton = findViewById(R.id.earth_view_street_view_button)
        resultStreetViewButton = findViewById(R.id.earth_view_result_street_view_button)
        placeDetails = findViewById(R.id.earth_view_place_details)
        placeTitle = findViewById(R.id.earth_view_place_title)
        placeAddress = findViewById(R.id.earth_view_place_address)
        mapLoading = findViewById(R.id.earth_view_map_loading)
        mapLoadingIcon = findViewById(R.id.earth_view_map_loading_icon)
        statePanel = findViewById(R.id.earth_view_state_panel)
        stateIcon = findViewById(R.id.earth_view_state_icon)
        stateTitle = findViewById(R.id.earth_view_state_title)
        stateMessage = findViewById(R.id.earth_view_state_message)
        statePrimaryButton = findViewById(R.id.earth_view_state_primary_button)
        stateSecondaryButton = findViewById(R.id.earth_view_state_secondary_button)

        placeAdapter = EarthViewPlaceAdapter(::moveToPlace)
        searchResults.layoutManager = LinearLayoutManager(this)
        searchResults.adapter = placeAdapter
        selectedPlace = readInitialPlace()

        findViewById<View>(R.id.earth_view_back).setOnClickListener { finish() }
        searchIcon.setOnClickListener { startSearch() }
        findViewById<View>(R.id.earth_view_search_button).setOnClickListener {
            searchPlace(searchInput.text.toString(), showKeyboard = false)
        }
        dimension2d.setOnClickListener { setViewMode(false) }
        dimension3d.setOnClickListener { setViewMode(true) }
        findViewById<ImageButton>(R.id.earth_view_clear_search).setOnClickListener {
            searchInput.text?.clear()
        }
        findViewById<View>(R.id.earth_view_my_location).setOnClickListener {
            if (hasLocationPermission()) {
                centerOnCurrentLocation()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
        findViewById<View>(R.id.earth_view_street_view_button).setOnClickListener {
            openCurrentLocationInStreetView()
        }
        resultStreetViewButton.setOnClickListener {
            openSelectedPlaceInStreetView()
        }
        splitView.setOnClickListener {
            // The split-view affordance is the map result's direct entry point
            // into the same selected place in Street View.
            openSelectedPlaceInStreetView()
        }
        statePrimaryButton.setOnClickListener { handleStatePrimaryAction() }
        stateSecondaryButton.setOnClickListener { handleStateSecondaryAction() }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPlace(searchInput.text.toString(), showKeyboard = false)
                true
            } else {
                false
            }
        }
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) setSearchMode(true)
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                val query = text?.toString()?.trim().orEmpty()
                if (!updatingSearchText && resultMode) setResultMode(false)
                if (!updatingSearchText && query.isNotEmpty()) setSearchMode(true)
                findViewById<View>(R.id.earth_view_clear_search).visibility =
                    if (query.isEmpty()) View.GONE else View.VISIBLE

                if (updatingSearchText || query.length < MIN_QUERY_LENGTH) {
                    if (query.length < MIN_QUERY_LENGTH) {
                        searchJob?.cancel()
                        searchDebounceJob?.cancel()
                        placeAdapter.submitList(emptyList())
                        searchResults.visibility = View.GONE
                        if (!updatingSearchText && query.isEmpty()) {
                            setSearchMode(false)
                            resetGlobe()
                        }
                    }
                    return
                }

                searchJob?.cancel()
                searchDebounceJob?.cancel()
                searchDebounceJob = lifecycleScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    searchDebounceJob = null
                    searchPlace(query, showKeyboard = true, cancelCurrentJob = false)
                }
            }

            override fun afterTextChanged(text: Editable?) = Unit
        })

        configureGlobe()
        mapLifecycleEnabled = hasMapsApiKey()
        if (mapLifecycleEnabled) {
            runCatching {
                map2dView.onCreate(savedInstanceState)
                map2dView.getMapAsync(this)
            }.onFailure { mapLifecycleEnabled = false }
        }
        setViewMode(true)
        setSearchMode(false)
        if (!isNetwork()) {
            showEarthState(EarthState.OFFLINE)
        } else {
            setMapLoading(true)
            mapLoadingJob = lifecycleScope.launch {
                delay(MAP_LOADING_MIN_MS)
                setMapLoading(false)
            }
            val initialPlace = selectedPlace
            if (initialPlace == null) {
                showDefaultEarthLocation()
            } else {
                updatingSearchText = true
                searchInput.setText(initialPlace.name)
                updatingSearchText = false
                showPlaceOnGlobe(initialPlace)
                setSearchMode(true)
                setResultMode(true, initialPlace)
            }
        }
    }

    private fun readInitialPlace(): EarthPlace? {
        val name = intent.getStringExtra(EXTRA_PLACE_NAME)?.trim().orEmpty()
        val address = intent.getStringExtra(EXTRA_PLACE_ADDRESS)?.trim().orEmpty()
        val latitude = intent.getDoubleExtra(EXTRA_PLACE_LATITUDE, Double.NaN)
        val longitude = intent.getDoubleExtra(EXTRA_PLACE_LONGITUDE, Double.NaN)
        return if (
            name.isNotEmpty() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0
        ) {
            EarthPlace(name, address, latitude, longitude)
        } else {
            null
        }
    }

    private fun configureGlobe() {
        worldWindow.engine.layers.addLayer(BackgroundLayer())
        worldWindow.engine.layers.addLayer(BlueMarbleLandsatLayer().apply {
            tiledSurfaceImage?.apply {
                detailControl = IMAGERY_DETAIL_CONTROL
                retrieveTopLevelTiles = true
                useAncestorTileTexture = true
                imageOptions?.apply {
                    // Keep the full 32-bit source data. RGB_565 saves memory but visibly
                    // reduces colour fidelity and can amplify decode artefacts on tiles.
                    imageConfig = ImageConfig.RGBA_8888
                    // Bilinear smooths the short transition while a child tile is loading;
                    // it cannot create detail that is not present in the 15 m source.
                    resamplingMode = ResamplingMode.BILINEAR
                }
                setupTileCache(IMAGERY_TILE_CACHE_CAPACITY, IMAGERY_TILE_CACHE_LOW_WATER)
            }
        })
        worldWindow.engine.layers.addLayer(WorldImageryLayer().apply {
            tiledSurfaceImage?.apply {
                detailControl = WORLD_IMAGERY_DETAIL_CONTROL
                retrieveTopLevelTiles = true
                useAncestorTileTexture = true
                imageOptions?.apply {
                    imageConfig = ImageConfig.RGBA_8888
                    resamplingMode = ResamplingMode.BILINEAR
                }
                setupTileCache(WORLD_IMAGERY_TILE_CACHE_CAPACITY, WORLD_IMAGERY_TILE_CACHE_LOW_WATER)
            }
        })
        worldWindow.engine.layers.addLayer(AtmosphereLayer())
        worldWindow.engine.layers.addLayer(markerLayer)
        resetGlobe()
    }

    private fun startSearch() {
        setViewMode(true)
        setSearchMode(true, animate = true)
        searchInput.requestFocus()
        searchInput.post {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setViewMode(show3d: Boolean, automaticCloseRange: Boolean = false) {
        worldWindow.visibility = if (show3d) View.VISIBLE else View.GONE
        map2dView.visibility = if (!show3d && mapLifecycleEnabled) View.VISIBLE else View.GONE
        dimension2d.background = if (show3d) {
            ContextCompat.getDrawable(this, R.drawable.bg_earth_view_control)
        } else {
            ContextCompat.getDrawable(this, R.drawable.bg_map_style_selected)
        }
        dimension3d.background = if (show3d) {
            ContextCompat.getDrawable(this, R.drawable.bg_map_style_selected)
        } else {
            ContextCompat.getDrawable(this, R.drawable.bg_earth_view_control)
        }
        dimension2d.setTextColor(ContextCompat.getColor(this, if (show3d) R.color.secondary_text_color else R.color.earth_background))
        dimension3d.setTextColor(ContextCompat.getColor(this, if (show3d) R.color.earth_background else R.color.secondary_text_color))
        if (!show3d) {
            googleMap2d?.mapType = GoogleMap.MAP_TYPE_SATELLITE
            googleMap2d?.isBuildingsEnabled = true
            selectedPlace?.let(::showPlaceOn2dMap)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap2d = map
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isScrollGesturesEnabled = true
        map.uiSettings.isZoomGesturesEnabled = true
        map.uiSettings.isRotateGesturesEnabled = true
        map.uiSettings.isTiltGesturesEnabled = true
        map.mapType = GoogleMap.MAP_TYPE_SATELLITE
        map.isBuildingsEnabled = true
        selectedPlace?.let(::showPlaceOn2dMap) ?: map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(DEFAULT_GLOBE_LATITUDE, DEFAULT_GLOBE_LONGITUDE),
                DEFAULT_2D_ZOOM
            )
        )
    }

    private fun showPlaceOn2dMap(place: EarthPlace) {
        googleMap2d?.let { map ->
            val point = LatLng(place.latitude, place.longitude)
            map.clear()
            map.addMarker(MarkerOptions().position(point).title(place.name))
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, LOCATION_2D_ZOOM))
        }
    }

    private fun resetGlobe() {
        markerLayer.clearRenderables()
        worldWindow.engine.cameraFromLookAt(
            LookAt(
                Position.fromDegrees(DEFAULT_GLOBE_LATITUDE, DEFAULT_GLOBE_LONGITUDE, 0.0),
                AltitudeMode.CLAMP_TO_GROUND,
                DEFAULT_GLOBE_RANGE_METERS,
                Angle.fromDegrees(0.0),
                Angle.fromDegrees(0.0),
                Angle.fromDegrees(0.0)
            )
        )
        worldWindow.requestRedraw()
    }

    private fun searchPlace(
        query: String,
        showKeyboard: Boolean,
        cancelCurrentJob: Boolean = true
    ) {
        val placeQuery = query.trim()
        if (placeQuery.length < MIN_QUERY_LENGTH) return

        searchDebounceJob?.cancel()
        if (cancelCurrentJob) searchJob?.cancel()
        setResultMode(false)
        mapLoadingJob?.cancel()
        hideEarthState()
        searchResults.visibility = View.GONE
        setViewMode(true)
        setSearchMode(true)
        setMapLoading(true)
        if (!showKeyboard) hideKeyboard()
        searchJob = lifecycleScope.launch {
            try {
                val places = withContext(Dispatchers.IO) {
                    EarthPlaceSearch.search(placeQuery)
                }
                if (!isActive) return@launch
                setMapLoading(false)
                failedSearchQuery = null
                val displayPlaces = prioritizeSearchResults(placeQuery, places)
                if (displayPlaces.isEmpty()) {
                    placeAdapter.submitList(emptyList())
                    showEarthState(EarthState.NO_PLACES)
                } else {
                    hideEarthState()
                    placeAdapter.submitList(displayPlaces)
                    searchResults.visibility = View.VISIBLE
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (!isActive) return@launch
                setMapLoading(false)
                if (isNetwork()) {
                    failedSearchQuery = placeQuery
                    showEarthState(EarthState.MAP_FAILED)
                } else {
                    failedSearchQuery = null
                    showEarthState(EarthState.OFFLINE)
                }
            }
        }
    }

    private fun prioritizeSearchResults(query: String, places: List<EarthPlace>): List<EarthPlace> {
        if (places.isEmpty()) return emptyList()
        val normalizedQuery = normalizeSearchText(query)
        val exact = places.firstOrNull {
            normalizeSearchText(it.name) == normalizedQuery
        }
        if (exact != null) return listOf(exact)

        return places
            .distinctBy { it.placeId ?: "${it.name}|${it.latitude}|${it.longitude}" }
            .sortedByDescending { place ->
                val name = normalizeSearchText(place.name)
                when {
                    name.startsWith(normalizedQuery) -> 3
                    name.contains(normalizedQuery) -> 2
                    normalizeSearchText(place.address).contains(normalizedQuery) -> 1
                    else -> 0
                }
            }
            .take(MAX_SEARCH_RESULTS)
    }

    private fun normalizeSearchText(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun showEarthState(state: EarthState) {
        earthState = state
        setMapLoading(false)
        searchResults.visibility = View.GONE
        placeDetails.visibility = View.GONE
        resultStreetViewButton.visibility = View.GONE
        earthControls.visibility = View.GONE
        streetViewButton.visibility = View.GONE
        mapContainer.visibility = View.GONE
        statePanel.visibility = View.VISIBLE
        resultMode = false

        // Keep the expanded search field visible for every query outcome. In
        // particular, do not let the empty/error panel hide the query the user
        // just entered.
        setSearchMode(active = searchInput.text?.isNotBlank() == true)

        // Keep the query visible in the top search field for loading, empty,
        // and error states so the user always knows what was searched.

        stateIcon.setImageResource(
            when (state) {
                EarthState.NO_PLACES -> R.drawable.ic_search
                EarthState.MAP_FAILED -> R.drawable.ic_earth_map_error
                EarthState.LOCATION_UNAVAILABLE -> R.drawable.ic_my_location
                EarthState.OFFLINE -> R.drawable.ic_globe
            }
        )
        stateTitle.setText(
            when (state) {
                EarthState.NO_PLACES -> R.string.earth_view_no_places_title
                EarthState.MAP_FAILED -> R.string.earth_view_map_failed_title
                EarthState.LOCATION_UNAVAILABLE -> R.string.earth_view_location_unavailable_title
                EarthState.OFFLINE -> R.string.earth_view_offline_title
            }
        )
        stateMessage.setText(
            when (state) {
                EarthState.NO_PLACES -> R.string.earth_view_no_places_message
                EarthState.MAP_FAILED -> R.string.earth_view_map_failed_message
                EarthState.LOCATION_UNAVAILABLE -> R.string.earth_view_location_unavailable_message
                EarthState.OFFLINE -> R.string.earth_view_offline_message
            }
        )
        statePrimaryButton.setText(
            when (state) {
                EarthState.NO_PLACES -> R.string.earth_view_search_again
                EarthState.MAP_FAILED,
                EarthState.OFFLINE -> R.string.earth_view_retry
                EarthState.LOCATION_UNAVAILABLE -> R.string.earth_view_try_again
            }
        )
        stateSecondaryButton.setText(
            when (state) {
                EarthState.NO_PLACES -> R.string.earth_view_explore_map
                EarthState.MAP_FAILED -> R.string.earth_view_search_another_place
                EarthState.LOCATION_UNAVAILABLE -> R.string.earth_view_search_manually
                EarthState.OFFLINE -> R.string.earth_view_back_action
            }
        )
        stateSecondaryButton.background = when (state) {
            EarthState.NO_PLACES -> ContextCompat.getDrawable(this, R.drawable.bg_earth_street_view_button)
            EarthState.MAP_FAILED -> ContextCompat.getDrawable(this, R.drawable.bg_earth_place_card)
            EarthState.LOCATION_UNAVAILABLE -> ContextCompat.getDrawable(this, R.drawable.bg_earth_place_card)
            EarthState.OFFLINE -> null
        }
        stateSecondaryButton.setTextColor(
            ContextCompat.getColor(
                this,
                if (state == EarthState.OFFLINE) R.color.secondary_text_color else R.color.primary_text_color
            )
        )
        updateScreenTitle()
    }

    private fun hideEarthState() {
        earthState = null
        statePanel.visibility = View.GONE
        mapContainer.visibility = View.VISIBLE
    }

    private fun handleStatePrimaryAction() {
        when (earthState) {
            EarthState.NO_PLACES -> {
                hideEarthState()
                searchPlace(searchInput.text.toString(), showKeyboard = false)
            }
            EarthState.MAP_FAILED,
            EarthState.OFFLINE -> retryCurrentMapOrSearch()
            EarthState.LOCATION_UNAVAILABLE -> retryCurrentLocation()
            null -> Unit
        }
    }

    private fun handleStateSecondaryAction() {
        when (earthState) {
            EarthState.NO_PLACES -> {
                hideEarthState()
                setSearchMode(false)
                resetGlobe()
            }
            EarthState.MAP_FAILED -> {
                failedSearchQuery = null
                searchInput.text?.clear()
                hideEarthState()
                setSearchMode(true)
            }
            EarthState.LOCATION_UNAVAILABLE -> {
                hideEarthState()
                setSearchMode(true)
                resetGlobe()
            }
            EarthState.OFFLINE -> finish()
            null -> Unit
        }
    }

    private fun retryCurrentMapOrSearch() {
        worldWindow.engine.renderResourceCache.absentResourceList.clear()
        val query = failedSearchQuery ?: searchInput.text.toString().trim()
        hideEarthState()
        if (query.length >= MIN_QUERY_LENGTH) {
            failedSearchQuery = null
            searchPlace(query, showKeyboard = false)
        } else {
            setSearchMode(false)
            resetGlobe()
            if (!isNetwork()) {
                showEarthState(EarthState.OFFLINE)
            } else {
                setMapLoading(true)
                mapLoadingJob?.cancel()
                mapLoadingJob = lifecycleScope.launch {
                    delay(MAP_LOADING_MIN_MS)
                    setMapLoading(false)
                }
            }
        }
    }

    private fun retryCurrentLocation() {
        hideEarthState()
        setSearchMode(false)
        resetGlobe()
        if (!isNetwork()) {
            showEarthState(EarthState.OFFLINE)
            return
        }
        setMapLoading(true)
        mapLoadingJob?.cancel()
        mapLoadingJob = lifecycleScope.launch {
            delay(MAP_LOADING_MIN_MS)
            setMapLoading(false)
        }
        showDefaultEarthLocation()
    }

    private fun moveToPlace(place: EarthPlace) {
        searchResults.visibility = View.GONE
        // Keep the exact query the user typed in the persistent top search box.
        searchInput.clearFocus()
        hideKeyboard()
        setViewMode(true)
        setSearchMode(true)
        setResultMode(false)
        setMapLoading(true)
        showPlaceOnGlobe(place)
        mapLoadingJob?.cancel()
        mapLoadingJob = lifecycleScope.launch {
            delay(MAP_LOADING_MIN_MS)
            setResultMode(true, place)
            setMapLoading(false)
        }
    }

    private fun setSearchMode(active: Boolean, animate: Boolean = false) {
        searchMode = active
        screenTitle.visibility = if (active) View.GONE else View.VISIBLE
        searchOverlay.visibility = if (active) View.VISIBLE else View.GONE
        searchIcon.visibility = if (active) View.GONE else View.VISIBLE
        earthControls.visibility = if (active) View.GONE else View.VISIBLE
        streetViewButton.visibility = if (active) View.GONE else View.VISIBLE
        if (!active) setResultMode(false)
        updateMapLayout()

        if (!active) searchResults.visibility = View.GONE

        if (active) {
            if (animate) {
                searchOverlay.animate().cancel()
                searchOverlay.pivotX = 0f
                searchOverlay.pivotY = dp(24).toFloat()
                searchOverlay.alpha = 0f
                searchOverlay.scaleX = 0.16f
                searchOverlay.post {
                    searchOverlay.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .setDuration(220L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            } else {
                searchOverlay.animate().cancel()
                searchOverlay.alpha = 1f
                searchOverlay.scaleX = 1f
            }
        } else {
            searchOverlay.animate().cancel()
            searchOverlay.alpha = 1f
            searchOverlay.scaleX = 1f
        }
        updateScreenTitle()
    }

    private fun setResultMode(active: Boolean, place: EarthPlace? = null) {
        resultMode = active
        placeDetails.visibility = if (active) View.VISIBLE else View.GONE
        resultStreetViewButton.visibility = if (active) View.VISIBLE else View.GONE
        splitView.visibility = if (active) View.VISIBLE else View.GONE
        if (active && place != null) {
            placeTitle.text = place.name
            placeAddress.text = formatPlaceAddress(place)
        }
        updateScreenTitle()
        updateMapLayout()
        // Search results must remain in the WorldWind Earth view. The Google
        // MapView is reserved for the explicit 2D control only.
        if (active) setViewMode(true)
    }

    private fun updateMapLayout() {
        val layoutParams = mapContainer.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.dimensionRatio = null
        layoutParams.height = dp(MAP_HEIGHT_DP)
        layoutParams.topToTop = ConstraintLayout.LayoutParams.UNSET
        layoutParams.topToBottom = R.id.earth_view_header_overlay
        layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        layoutParams.marginStart = dp(20)
        layoutParams.marginEnd = dp(20)
        layoutParams.topMargin = dp(MAP_TOP_MARGIN_DP)
        mapContainer.layoutParams = layoutParams
        mapContainer.background = ContextCompat.getDrawable(this, R.drawable.bg_earth_map_result)
        mapContainer.clipToOutline = true
        mapContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    private fun formatPlaceAddress(place: EarthPlace): String {
        val parts = place.address.split(',').map(String::trim).filter(String::isNotEmpty)
        return when {
            parts.size >= 2 -> parts.takeLast(2).joinToString(", ")
            parts.firstOrNull().equals(place.name, ignoreCase = true) -> "Country"
            else -> place.address
        }
    }

    private fun setMapLoading(loading: Boolean) {
        isMapLoading = loading
        mapLoading.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            startMapLoadingAnimation()
        } else {
            stopMapLoadingAnimation()
        }
        updateScreenTitle()
        updateMapLayout()
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

    private fun updateScreenTitle() {
        if (searchMode || resultMode) {
            screenTitle.visibility = View.GONE
            return
        }
        screenTitle.visibility = View.VISIBLE
        screenTitle.setText(R.string.earth_view_title)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun showPlaceOnGlobe(place: EarthPlace) {
        selectedPlace = place
        markerLayer.clearRenderables()

        val markerAttributes = PlacemarkAttributes.createWithImage(
            ImageSource.fromBitmap(createMarkerLabelBitmap(place.name))
        ).apply {
            // The bitmap contains the complete 36dp marker + 6dp gap + 108x32dp
            // label card, keeping both elements aligned while the globe moves.
            isDrawLabel = false
            imageOffset = Offset.bottomCenter()
        }
        val marker = Placemark(
            Position.fromDegrees(place.latitude, place.longitude, 0.0),
            markerAttributes,
            place.name
        ).apply {
            altitudeMode = AltitudeMode.CLAMP_TO_GROUND
            isAlwaysOnTop = true
            isBillboardingEnabled = true
        }
        markerLayer.addRenderable(marker)
        worldWindow.engine.cameraFromLookAt(
            LookAt(
                Position.fromDegrees(place.latitude, place.longitude, 0.0),
                AltitudeMode.CLAMP_TO_GROUND,
                SEARCH_RANGE_METERS,
                Angle.fromDegrees(0.0),
                Angle.fromDegrees(0.0),
                Angle.fromDegrees(0.0)
            )
        )
        worldWindow.requestRedraw()
        showPlaceOn2dMap(place)
    }

    private fun createMarkerLabelBitmap(label: String): Bitmap {
        val markerSize = dp(36)
        val cardWidth = dp(108)
        val cardHeight = dp(32)
        val gap = dp(6)
        val bitmap = Bitmap.createBitmap(
            cardWidth,
            markerSize + gap + cardHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val cardTop = (markerSize + gap).toFloat()
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(23, 38, 56)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(0f, cardTop, cardWidth.toFloat(), bitmap.height.toFloat()),
            dp(10).toFloat(),
            dp(10).toFloat(),
            cardPaint
        )

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = dp(12).toFloat()
            typeface = ResourcesCompat.getFont(this@EarthViewActivity, R.font.poppins_semibold)
                ?: Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val maxTextWidth = (cardWidth - dp(12)).toFloat()
        var visibleLabel = label.trim().ifEmpty { getString(R.string.earth_view_title) }
        if (textPaint.measureText(visibleLabel) > maxTextWidth) {
            val ellipsis = "…"
            while (visibleLabel.length > 1 &&
                textPaint.measureText(visibleLabel + ellipsis) > maxTextWidth
            ) {
                visibleLabel = visibleLabel.dropLast(1)
            }
            visibleLabel += ellipsis
        }
        val metrics = textPaint.fontMetrics
        val baseline = cardTop + cardHeight / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(visibleLabel, cardWidth / 2f, baseline, textPaint)

        val markerDrawable = ContextCompat.getDrawable(this, R.drawable.ic_earth_marker)
        markerDrawable?.setBounds(
            (cardWidth - markerSize) / 2,
            0,
            (cardWidth + markerSize) / 2,
            markerSize
        )
        markerDrawable?.draw(canvas)
        return bitmap
    }

    private fun showDefaultEarthLocation() {
        if (!locationProvider.hasPermission()) {
            locationPermissionLauncher.launch(locationPermissions())
            return
        }
        locationProvider.findCurrentLocation(
            onSuccess = { location ->
                runOnUiThread {
                    if (earthState == null && !searchMode && !resultMode) {
                        showPlaceOnGlobe(currentLocationPlace(location.latitude, location.longitude))
                    }
                }
            },
            onFailure = { runOnUiThread { showLocationUnavailable() } }
        )
    }

    private fun openSelectedPlaceInStreetView() {
        val place = selectedPlace ?: return
        startActivity(
            Intent(this, StreetViewActivity::class.java).apply {
                putExtra(EXTRA_STREET_VIEW_PLACE_NAME, place.name)
                putExtra(EXTRA_STREET_VIEW_PLACE_ADDRESS, place.address)
                putExtra(EXTRA_STREET_VIEW_PLACE_LATITUDE, place.latitude)
                putExtra(EXTRA_STREET_VIEW_PLACE_LONGITUDE, place.longitude)
            }
        )
    }

    private fun openCurrentLocationInStreetView() {
        startActivity(
            Intent(this, StreetViewActivity::class.java).apply {
                putExtra(StreetViewActivity.EXTRA_CURRENT_LOCATION_ONLY, true)
            }
        )
    }

    private fun centerOnCurrentLocation() {
        if (!locationProvider.hasPermission()) {
            locationPermissionLauncher.launch(locationPermissions())
            return
        }
        locationProvider.findCurrentLocation(
            onSuccess = { location ->
                runOnUiThread {
                    showPlaceOnGlobe(currentLocationPlace(location.latitude, location.longitude))
                }
            },
            onFailure = { runOnUiThread { showLocationUnavailable() } }
        )
    }

    private fun currentLocationPlace(latitude: Double, longitude: Double): EarthPlace =
        EarthPlace(
            getString(R.string.earth_view_current_location),
            "",
            latitude,
            longitude
        )

    private fun locationPermissions() = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private fun hasLocationPermission(): Boolean = locationProvider.hasPermission()

    private fun showLocationUnavailable() {
        showEarthState(EarthState.LOCATION_UNAVAILABLE)
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    private fun hasMapsApiKey(): Boolean {
        return try {
            val info = packageManager.getApplicationInfo(
                packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            val key = info.metaData?.getString("com.google.android.geo.API_KEY")
            !key.isNullOrBlank() && !key.contains("YOUR_MAPS_API_KEY")
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun onResume() {
        super.onResume()
        worldWindow.onResume()
        if (mapLifecycleEnabled) map2dView.onResume()
    }

    override fun onPause() {
        if (mapLifecycleEnabled) map2dView.onPause()
        worldWindow.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        searchJob?.cancel()
        searchDebounceJob?.cancel()
        mapLoadingJob?.cancel()
        mapLoadingAnimator?.cancel()
        locationProvider.stopLocationUpdates()
        worldWindow.onPause()
        if (mapLifecycleEnabled) map2dView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        worldWindow.engine.renderResourceCache.clear()
        if (mapLifecycleEnabled) map2dView.onLowMemory()
    }

    private companion object {
        const val MAX_SEARCH_RESULTS = 3
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 450L
        const val MAP_LOADING_MIN_MS = 900L
        // World Imagery reaches sub-meter pixels in many populated areas. This limit keeps
        // the camera above the range where missing provider detail becomes obvious blur.
        // World Imagery has useful city-level detail below this range. Keeping the clamp here
        // prevents an accidental black/empty tile request while still allowing close zoom.
        const val MIN_IMAGERY_RANGE_METERS = 250.0
        const val SEARCH_RANGE_METERS = 1_200.0
        const val LOCATION_2D_ZOOM = 17f
        const val DEFAULT_2D_ZOOM = 3f
        const val DEFAULT_GLOBE_LATITUDE = 18.0
        const val DEFAULT_GLOBE_LONGITUDE = 0.0
        const val DEFAULT_GLOBE_RANGE_METERS = 18_000_000.0
        const val IMAGERY_DETAIL_CONTROL = 1.0
        const val IMAGERY_TILE_CACHE_CAPACITY = 1_800L
        const val IMAGERY_TILE_CACHE_LOW_WATER = 1_350L
        const val WORLD_IMAGERY_DETAIL_CONTROL = 1.15
        const val WORLD_IMAGERY_TILE_CACHE_CAPACITY = 1_800L
        const val WORLD_IMAGERY_TILE_CACHE_LOW_WATER = 1_350L
        const val MAP_HEIGHT_DP = 540
        const val MAP_TOP_MARGIN_DP = 16
        const val EXTRA_PLACE_NAME = "earth_view_place_name"
        const val EXTRA_PLACE_ADDRESS = "earth_view_place_address"
        const val EXTRA_PLACE_LATITUDE = "earth_view_place_latitude"
        const val EXTRA_PLACE_LONGITUDE = "earth_view_place_longitude"
        const val EXTRA_STREET_VIEW_PLACE_NAME = "earth_view_place_name"
        const val EXTRA_STREET_VIEW_PLACE_ADDRESS = "earth_view_place_address"
        const val EXTRA_STREET_VIEW_PLACE_LATITUDE = "earth_view_place_latitude"
        const val EXTRA_STREET_VIEW_PLACE_LONGITUDE = "earth_view_place_longitude"
    }
}
