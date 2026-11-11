package com.gps.map.camera.and.time.stamp.ui.earthview

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.bases.ext.isNetwork
import com.gps.map.camera.and.time.stamp.ui.streetview.StreetViewActivity
import com.gps.map.camera.and.time.stamp.utils.Routes
import earth.worldwind.geom.Angle
import earth.worldwind.geom.AltitudeMode
import earth.worldwind.geom.LookAt
import earth.worldwind.geom.Offset
import earth.worldwind.geom.Position
import earth.worldwind.layer.BackgroundLayer
import earth.worldwind.layer.BlueMarbleLandsatLayer
import earth.worldwind.layer.RenderableLayer
import earth.worldwind.layer.atmosphere.AtmosphereLayer
import earth.worldwind.render.Color
import earth.worldwind.render.Font
import earth.worldwind.render.image.ImageSource
import earth.worldwind.render.image.ResamplingMode
import earth.worldwind.shape.Placemark
import earth.worldwind.shape.PlacemarkAttributes
import earth.worldwind.shape.TextAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.text.Editable
import android.text.TextWatcher

class EarthViewActivity : AppCompatActivity() {

    private enum class EarthState {
        NO_PLACES,
        MAP_FAILED,
        LOCATION_UNAVAILABLE,
        OFFLINE
    }

    private lateinit var worldWindow: EarthWorldWindow
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
    private var mapLoadingJob: Job? = null
    private var mapLoadingAnimator: ObjectAnimator? = null
    private var updatingSearchText = false
    private var searchMode = false
    private var resultMode = false
    private var isMapLoading = false
    private var earthState: EarthState? = null
    private var failedSearchQuery: String? = null
    private var selectedPlace: EarthPlace? = null
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) centerOnCurrentLocation() else showLocationUnavailable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_earth_view)

        worldWindow = findViewById(R.id.earth_map_view)
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

        findViewById<View>(R.id.earth_view_back).setOnClickListener { finish() }
        findViewById<View>(R.id.earth_view_search_button).setOnClickListener {
            searchPlace(searchInput.text.toString(), showKeyboard = false)
        }
        findViewById<View>(R.id.earth_view_reset_globe).setOnClickListener {
            resetGlobe()
        }
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
            Routes.startStreetViewActivity(this)
        }
        resultStreetViewButton.setOnClickListener {
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
                searchJob = lifecycleScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    searchPlace(query, showKeyboard = true, cancelCurrentJob = false)
                }
            }

            override fun afterTextChanged(text: Editable?) = Unit
        })

        configureGlobe()
        setSearchMode(false)
        if (!isNetwork()) {
            showEarthState(EarthState.OFFLINE)
        } else {
            setMapLoading(true)
            mapLoadingJob = lifecycleScope.launch {
                delay(MAP_LOADING_MIN_MS)
                setMapLoading(false)
            }
            showDefaultEarthLocation()
        }
    }

    private fun configureGlobe() {
        worldWindow.engine.layers.addLayer(BackgroundLayer())
        worldWindow.engine.layers.addLayer(BlueMarbleLandsatLayer().apply {
            tiledSurfaceImage?.apply {
                detailControl = IMAGERY_DETAIL_CONTROL
                useAncestorTileTexture = true
                imageOptions?.resamplingMode = ResamplingMode.BILINEAR
                setupTileCache(IMAGERY_TILE_CACHE_CAPACITY, IMAGERY_TILE_CACHE_LOW_WATER)
            }
        })
        worldWindow.engine.layers.addLayer(AtmosphereLayer())
        worldWindow.engine.layers.addLayer(markerLayer)
        resetGlobe()
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

        setResultMode(false)
        mapLoadingJob?.cancel()
        setSearchMode(true)
        setMapLoading(true)
        if (!showKeyboard) hideKeyboard()
        if (cancelCurrentJob) searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { EarthPlaceSearch.search(placeQuery) }
            }
            result.onSuccess { places ->
                setMapLoading(false)
                failedSearchQuery = null
                if (places.isEmpty()) {
                    placeAdapter.submitList(emptyList())
                    showEarthState(EarthState.NO_PLACES)
                } else {
                    hideEarthState()
                    placeAdapter.submitList(places.take(MAX_SEARCH_RESULTS))
                    searchResults.visibility = View.VISIBLE
                }
            }.onFailure {
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
        searchMode = state == EarthState.NO_PLACES

        if (state == EarthState.MAP_FAILED ||
            state == EarthState.LOCATION_UNAVAILABLE ||
            state == EarthState.OFFLINE
        ) {
            failedSearchQuery = null
            updatingSearchText = true
            searchInput.text?.clear()
            updatingSearchText = false
        }

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
        updatingSearchText = true
        searchInput.setText(place.name)
        searchInput.setSelection(searchInput.text.length)
        updatingSearchText = false
        searchInput.clearFocus()
        hideKeyboard()
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

    private fun setSearchMode(active: Boolean) {
        searchMode = active
        updateScreenTitle()
        earthControls.visibility = if (active) View.GONE else View.VISIBLE
        streetViewButton.visibility = if (active) View.GONE else View.VISIBLE
        if (!active) setResultMode(false)
        updateMapLayout()

        if (!active) searchResults.visibility = View.GONE
    }

    private fun setResultMode(active: Boolean, place: EarthPlace? = null) {
        resultMode = active
        placeDetails.visibility = if (active) View.VISIBLE else View.GONE
        resultStreetViewButton.visibility = if (active) View.VISIBLE else View.GONE
        if (active && place != null) {
            placeTitle.text = place.name
            placeAddress.text = formatPlaceAddress(place)
        }
        updateScreenTitle()
        updateMapLayout()
    }

    private fun updateMapLayout() {
        val layoutParams = mapContainer.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.dimensionRatio = null
        layoutParams.height = dp(MAP_HEIGHT_DP)
        layoutParams.topToTop = ConstraintLayout.LayoutParams.UNSET
        layoutParams.topToBottom = R.id.earth_view_search_overlay
        layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        layoutParams.marginStart = dp(20)
        layoutParams.marginEnd = dp(20)
        layoutParams.topMargin = dp(12)
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
        screenTitle.setText(
            if (isMapLoading || !searchMode || resultMode) {
                R.string.earth_view_title
            } else {
                R.string.earth_view_search_title
            }
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun showPlaceOnGlobe(place: EarthPlace) {
        selectedPlace = place
        markerLayer.clearRenderables()

        val labelTextAttributes = TextAttributes().apply {
            font = Font(18f, Typeface.create("sans-serif", Typeface.NORMAL))
            textColor = Color(255, 255, 255, 255)
            outlineColor = Color(0, 0, 0, 255)
            outlineWidth = 3f
            textOffset = Offset.bottomCenter()
            scale = 1.0
        }
        val markerAttributes = PlacemarkAttributes.createWithImage(
            ImageSource.fromResource(R.drawable.ic_earth_marker)
        ).apply {
            isDrawLabel = true
            imageScale = 1.0
            imageOffset = Offset.bottomCenter()
            labelAttributes = labelTextAttributes
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
    }

    private fun showDefaultEarthLocation() {
        if (!hasLocationPermission()) {
            showEarthState(EarthState.LOCATION_UNAVAILABLE)
            return
        }
        locationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null && earthState == null && !searchMode && !resultMode) {
                    showPlaceOnGlobe(
                        EarthPlace(
                            getString(R.string.earth_view_current_location),
                            "",
                            location.latitude,
                            location.longitude
                        )
                    )
                } else if (location == null && earthState == null) {
                    showEarthState(EarthState.LOCATION_UNAVAILABLE)
                }
            }
            .addOnFailureListener { showEarthState(EarthState.LOCATION_UNAVAILABLE) }
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

    private fun centerOnCurrentLocation() {
        if (!hasLocationPermission()) return
        locationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location == null) {
                    showLocationUnavailable()
                } else {
                    showPlaceOnGlobe(
                        EarthPlace(
                            getString(R.string.earth_view_current_location),
                            "",
                            location.latitude,
                            location.longitude
                        )
                    )
                }
            }
            .addOnFailureListener { showLocationUnavailable() }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun showLocationUnavailable() {
        showEarthState(EarthState.LOCATION_UNAVAILABLE)
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        worldWindow.onResume()
    }

    override fun onPause() {
        worldWindow.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        searchJob?.cancel()
        mapLoadingJob?.cancel()
        mapLoadingAnimator?.cancel()
        worldWindow.onPause()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        worldWindow.engine.renderResourceCache.clear()
    }

    private companion object {
        const val MAX_SEARCH_RESULTS = 3
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 450L
        const val MAP_LOADING_MIN_MS = 900L
        const val SEARCH_RANGE_METERS = 1_500.0
        const val DEFAULT_GLOBE_LATITUDE = 18.0
        const val DEFAULT_GLOBE_LONGITUDE = 0.0
        const val DEFAULT_GLOBE_RANGE_METERS = 18_000_000.0
        const val IMAGERY_DETAIL_CONTROL = 0.75
        const val IMAGERY_TILE_CACHE_CAPACITY = 1_800L
        const val IMAGERY_TILE_CACHE_LOW_WATER = 1_350L
        const val MAP_HEIGHT_DP = 540
        const val EXTRA_STREET_VIEW_PLACE_NAME = "earth_view_place_name"
        const val EXTRA_STREET_VIEW_PLACE_ADDRESS = "earth_view_place_address"
        const val EXTRA_STREET_VIEW_PLACE_LATITUDE = "earth_view_place_latitude"
        const val EXTRA_STREET_VIEW_PLACE_LONGITUDE = "earth_view_place_longitude"
    }
}
