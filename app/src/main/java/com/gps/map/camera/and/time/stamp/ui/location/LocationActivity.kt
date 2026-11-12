package com.gps.map.camera.and.time.stamp.ui.location

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlace
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlaceSearch
import com.gps.map.camera.and.time.stamp.utils.location.GpsLocationProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** Shows the current GPS position or a searched place. */
class LocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var mapContainer: View
    private lateinit var mapUnavailable: View
    private lateinit var loadingContent: View
    private lateinit var foundContent: View
    private lateinit var searchContent: View
    private lateinit var unavailableContent: View
    private lateinit var foundTitle: TextView
    private lateinit var latitudeValue: TextView
    private lateinit var longitudeValue: TextView
    private lateinit var accuracyRow: View
    private lateinit var accuracyValue: TextView
    private lateinit var searchInput: EditText
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchMessage: TextView
    private lateinit var searchResults: RecyclerView

    private val locationProvider by lazy { GpsLocationProvider(this) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val searchAdapter by lazy { LocationPlaceAdapter(::onPlaceSelected) }
    private var googleMap: GoogleMap? = null
    private var markerIcon: BitmapDescriptor? = null
    private var mapLifecycleEnabled = false
    private var state = LocationScreenState.LOADING
    private var previousState = LocationScreenState.LOADING
    private var lastProblem: GpsLocationProvider.LocationProblem? = null
    private var locationResult: LocationResult? = null
    private var searchJob: Job? = null
    private var locationAttempt = 0
    private var permissionWasRequested = false
    private var returningFromSettings = false
    private var disposed = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            startLocationFetch()
        } else {
            showUnavailable(GpsLocationProvider.LocationProblem.PERMISSION_DENIED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)
        bindViews()
        bindActions()
        restoreState(savedInstanceState)

        mapLifecycleEnabled = hasMapsApiKey()
        if (mapLifecycleEnabled) {
            runCatching {
                mapView.onCreate(savedInstanceState)
                mapView.getMapAsync(this)
            }.onFailure { mapLifecycleEnabled = false }
        }

        updateState()
        if (savedInstanceState == null || state == LocationScreenState.LOADING) {
            startLocationFetch()
        }
        if (state == LocationScreenState.SEARCHING) focusSearchField()
    }

    private fun bindViews() {
        mapView = findViewById(R.id.location_map)
        mapContainer = findViewById(R.id.location_map_container)
        mapUnavailable = findViewById(R.id.location_map_unavailable)
        loadingContent = findViewById(R.id.location_loading_content)
        foundContent = findViewById(R.id.location_found_content)
        searchContent = findViewById(R.id.location_search_content)
        unavailableContent = findViewById(R.id.location_unavailable_content)
        foundTitle = findViewById(R.id.location_found_title)
        latitudeValue = findViewById(R.id.location_latitude_value)
        longitudeValue = findViewById(R.id.location_longitude_value)
        accuracyRow = findViewById(R.id.location_accuracy_row)
        accuracyValue = findViewById(R.id.location_accuracy_value)
        searchInput = findViewById(R.id.location_search_input)
        searchProgress = findViewById(R.id.location_search_progress)
        searchMessage = findViewById(R.id.location_search_message)
        searchResults = findViewById(R.id.location_search_results)
        searchResults.layoutManager = LinearLayoutManager(this)
        searchResults.adapter = searchAdapter
    }

    private fun bindActions() {
        findViewById<ImageButton>(R.id.location_back).setOnClickListener { handleBack() }
        findViewById<View>(R.id.location_loading_search).setOnClickListener { openSearch() }
        findViewById<View>(R.id.location_search_another).setOnClickListener { openSearch() }
        findViewById<View>(R.id.location_unavailable_search).setOnClickListener { openSearch() }
        findViewById<View>(R.id.location_try_again).setOnClickListener { retryLocation() }
        findViewById<View>(R.id.location_share).setOnClickListener { shareLocation() }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                onSearchChanged(text?.toString().orEmpty())
            }
            override fun afterTextChanged(editable: Editable?) = Unit
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isCompassEnabled = false
        markerIcon = createLocationMarkerIcon()
        drawLocation()
    }

    private fun startLocationFetch() {
        cancelLocationTimeout()
        locationAttempt++
        val attempt = locationAttempt
        lastProblem = null
        state = LocationScreenState.LOADING
        updateState()

        if (!locationProvider.hasPermission()) {
            permissionWasRequested = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        if (!locationProvider.isLocationEnabled()) {
            showUnavailable(GpsLocationProvider.LocationProblem.LOCATION_DISABLED)
            return
        }

        mainHandler.postDelayed({
            if (!disposed && attempt == locationAttempt) {
                showUnavailable(GpsLocationProvider.LocationProblem.FIX_UNAVAILABLE)
            }
        }, LOCATION_TIMEOUT_MILLIS)

        locationProvider.findCurrentLocation(
            onSuccess = { location ->
                runOnUiThread {
                    if (disposed || attempt != locationAttempt) return@runOnUiThread
                    cancelLocationTimeout()
                    showFound(
                        LocationResult(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = location.accuracy
                        )
                    )
                }
            },
            onFailure = { problem ->
                runOnUiThread {
                    if (!disposed && attempt == locationAttempt) showUnavailable(problem)
                }
            }
        )
    }

    private fun showFound(result: LocationResult) {
        cancelLocationTimeout()
        locationResult = result
        lastProblem = null
        state = LocationScreenState.FOUND
        updateState()
        drawLocation()
    }

    private fun showUnavailable(problem: GpsLocationProvider.LocationProblem) {
        cancelLocationTimeout()
        locationAttempt++
        lastProblem = problem
        state = LocationScreenState.UNAVAILABLE
        updateState()
    }

    private fun retryLocation() {
        if (lastProblem == GpsLocationProvider.LocationProblem.LOCATION_DISABLED) {
            openLocationSettings()
            return
        }
        if (lastProblem == GpsLocationProvider.LocationProblem.PERMISSION_DENIED &&
            permissionWasRequested && !canRequestLocationPermission()
        ) {
            openAppSettings()
            return
        }
        startLocationFetch()
    }

    private fun canRequestLocationPermission(): Boolean {
        return shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            !permissionWasRequested
    }

    private fun openSearch() {
        searchJob?.cancel()
        cancelLocationTimeout()
        locationAttempt++
        previousState = state
        state = LocationScreenState.SEARCHING
        searchMessage.visibility = View.GONE
        searchProgress.visibility = View.GONE
        updateState()
        focusSearchField()
    }

    private fun focusSearchField() {
        searchInput.requestFocus()
        searchInput.post {
            val inputMethod = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethod.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun onSearchChanged(query: String) {
        if (state != LocationScreenState.SEARCHING) return
        searchJob?.cancel()
        searchAdapter.submitList(emptyList())
        searchMessage.visibility = View.GONE
        if (query.trim().length < 2) {
            searchProgress.visibility = View.GONE
            return
        }

        searchJob = lifecycleScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            searchProgress.visibility = View.VISIBLE
            val places = withContext(Dispatchers.IO) {
                runCatching { EarthPlaceSearch.search(query.trim()) }.getOrDefault(emptyList())
            }
            if (!isActive || state != LocationScreenState.SEARCHING) return@launch
            searchProgress.visibility = View.GONE
            searchAdapter.submitList(places)
            searchMessage.visibility = if (places.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun onPlaceSelected(place: EarthPlace) {
        searchJob?.cancel()
        hideKeyboard()
        showFound(
            LocationResult(
                latitude = place.latitude,
                longitude = place.longitude,
                accuracyMeters = null,
                place = place
            )
        )
    }

    private fun shareLocation() {
        val result = locationResult ?: return
        val coordinates = LocationFormatter.shareCoordinates(result.latitude, result.longitude)
        val shareText = getString(R.string.location_share_text, coordinates)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.location_share_title)))
    }

    private fun updateState() {
        val showsMap = state == LocationScreenState.LOADING || state == LocationScreenState.FOUND
        mapContainer.visibility = if (showsMap) View.VISIBLE else View.GONE
        setMapWeight(showsMap)
        mapUnavailable.visibility = if (showsMap && !mapLifecycleEnabled) View.VISIBLE else View.GONE
        mapView.visibility = if (showsMap && mapLifecycleEnabled) View.VISIBLE else View.GONE
        loadingContent.visibility = if (state == LocationScreenState.LOADING) View.VISIBLE else View.GONE
        foundContent.visibility = if (state == LocationScreenState.FOUND) View.VISIBLE else View.GONE
        searchContent.visibility = if (state == LocationScreenState.SEARCHING) View.VISIBLE else View.GONE
        unavailableContent.visibility = if (state == LocationScreenState.UNAVAILABLE) View.VISIBLE else View.GONE

        locationResult?.let { result ->
            foundTitle.text = result.place?.name ?: getString(R.string.location_your_location)
            latitudeValue.text = LocationFormatter.latitude(result.latitude)
            longitudeValue.text = LocationFormatter.longitude(result.longitude)
            accuracyRow.visibility = if (result.accuracyMeters == null) View.GONE else View.VISIBLE
            result.accuracyMeters?.let { accuracyValue.text = LocationFormatter.accuracy(it) }
        }
    }

    private fun setMapWeight(visible: Boolean) {
        val params = mapContainer.layoutParams as LinearLayout.LayoutParams
        params.height = 0
        params.weight = if (visible) 1f else 0f
        mapContainer.layoutParams = params
    }

    private fun drawLocation() {
        val map = googleMap ?: return
        val result = locationResult ?: return
        val position = LatLng(result.latitude, result.longitude)
        map.clear()
        map.addMarker(
            MarkerOptions()
                .position(position)
                .title(result.place?.name ?: getString(R.string.location_your_location))
                .icon(markerIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, LOCATION_ZOOM))
    }

    private fun handleBack() {
        if (state == LocationScreenState.SEARCHING) {
            searchJob?.cancel()
            hideKeyboard()
            state = previousState
            updateState()
            if (state == LocationScreenState.LOADING) startLocationFetch()
        } else {
            finish()
        }
    }

    private fun hideKeyboard() {
        val inputMethod = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethod.hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchInput.clearFocus()
    }

    private fun openLocationSettings() {
        returningFromSettings = true
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun openAppSettings() {
        returningFromSettings = true
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun cancelLocationTimeout() {
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun createLocationMarkerIcon(): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val size = (28 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MARKER_COLOR
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        canvas.drawCircle(center, center, size * 0.32f, fill)
        canvas.drawCircle(center, center, size * 0.32f, stroke)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun hasMapsApiKey(): Boolean {
        return try {
            val info = packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            )
            val key = info.metaData?.getString("com.google.android.geo.API_KEY")
            !key.isNullOrBlank() && !key.contains("YOUR_MAPS_API_KEY")
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        savedInstanceState ?: return
        state = LocationScreenState.values().getOrElse(
            savedInstanceState.getInt(STATE_SCREEN, LocationScreenState.LOADING.ordinal)
        ) { LocationScreenState.LOADING }
        previousState = LocationScreenState.values().getOrElse(
            savedInstanceState.getInt(STATE_PREVIOUS_SCREEN, LocationScreenState.LOADING.ordinal)
        ) { LocationScreenState.LOADING }
        permissionWasRequested = savedInstanceState.getBoolean(STATE_PERMISSION_REQUESTED)
        val latitude = savedInstanceState.getDouble(STATE_LATITUDE, Double.NaN)
        val longitude = savedInstanceState.getDouble(STATE_LONGITUDE, Double.NaN)
        if (!latitude.isNaN() && !longitude.isNaN()) {
            val accuracy = savedInstanceState.getFloat(STATE_ACCURACY, NO_ACCURACY)
            val placeName = savedInstanceState.getString(STATE_PLACE_NAME)
            locationResult = LocationResult(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracy.takeUnless { it == NO_ACCURACY },
                place = placeName?.let { EarthPlace(it, "", latitude, longitude) }
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SCREEN, state.ordinal)
        outState.putInt(STATE_PREVIOUS_SCREEN, previousState.ordinal)
        outState.putBoolean(STATE_PERMISSION_REQUESTED, permissionWasRequested)
        locationResult?.let { result ->
            outState.putDouble(STATE_LATITUDE, result.latitude)
            outState.putDouble(STATE_LONGITUDE, result.longitude)
            outState.putFloat(STATE_ACCURACY, result.accuracyMeters ?: NO_ACCURACY)
            result.place?.let { outState.putString(STATE_PLACE_NAME, it.name) }
        }
        if (mapLifecycleEnabled) mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (mapLifecycleEnabled) mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (mapLifecycleEnabled) mapView.onResume()
        if (returningFromSettings) {
            returningFromSettings = false
            if (locationProvider.hasPermission() && locationProvider.isLocationEnabled()) {
                startLocationFetch()
            }
        }
    }

    override fun onPause() {
        if (mapLifecycleEnabled) mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (mapLifecycleEnabled) mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (mapLifecycleEnabled) mapView.onLowMemory()
    }

    override fun onDestroy() {
        disposed = true
        locationAttempt++
        cancelLocationTimeout()
        searchJob?.cancel()
        if (mapLifecycleEnabled) mapView.onDestroy()
        super.onDestroy()
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 15_000L
        const val SEARCH_DEBOUNCE_MILLIS = 400L
        const val LOCATION_ZOOM = 15f
        const val MARKER_COLOR = 0xFF2D7FF9.toInt()
        const val NO_ACCURACY = -1f
        const val STATE_SCREEN = "location_screen"
        const val STATE_PREVIOUS_SCREEN = "location_previous_screen"
        const val STATE_PERMISSION_REQUESTED = "location_permission_requested"
        const val STATE_LATITUDE = "location_latitude"
        const val STATE_LONGITUDE = "location_longitude"
        const val STATE_ACCURACY = "location_accuracy"
        const val STATE_PLACE_NAME = "location_place_name"
    }
}
