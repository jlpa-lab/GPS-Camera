package com.gps.map.camera.and.time.stamp.ui.route

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlace
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlaceAdapter
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlaceSearch
import com.gps.map.camera.and.time.stamp.utils.location.GpsLocationProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Route planning screen with a real map preview and real directions data. */
class RoutePlanActivity : AppCompatActivity(), OnMapReadyCallback {
    private enum class ScreenState { INPUT, PREVIEW, NO_ROUTE }

    private lateinit var mapView: MapView
    private lateinit var mapContainer: View
    private lateinit var inputContent: View
    private lateinit var previewContent: View
    private lateinit var noRouteContent: View
    private lateinit var fromValue: TextView
    private lateinit var toValue: TextView
    private lateinit var locationStatus: TextView
    private lateinit var locationSettings: TextView
    private lateinit var findRouteButton: TextView
    private lateinit var searchDestinationButton: TextView
    private lateinit var estimatedTrip: TextView
    private lateinit var loadingOverlay: View
    private lateinit var loadingProgress: ProgressBar
    private lateinit var viewDirectionsButton: TextView
    private lateinit var editRouteButton: TextView
    private lateinit var noRouteEditButton: TextView
    private lateinit var noRouteBackButton: TextView
    private lateinit var modeButtons: Map<RouteTravelMode, TextView>

    private val locationProvider by lazy { GpsLocationProvider(this) }
    private val directionsClient by lazy { RouteDirectionsClient(this) }
    private var googleMap: GoogleMap? = null
    private var mapLifecycleEnabled = false
    private var screenState = ScreenState.INPUT
    private var selectedMode = RouteTravelMode.DRIVE
    private var fromLocation: LatLng? = null
    private var fromPlace: EarthPlace? = null
    private var destination: EarthPlace? = null
    private var routeDetails: RouteDetails? = null
    private var routeJob: Job? = null
    private var searchJob: Job? = null
    private var isLoading = false
    private var startMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var routeOutline: Polyline? = null
    private var routeLine: Polyline? = null
    private var startIcon: BitmapDescriptor? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) loadCurrentLocation()
        else showLocationProblem(GpsLocationProvider.LocationProblem.PERMISSION_DENIED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_plan)
        bindViews()
        bindActions()
        restoreState(savedInstanceState)

        mapLifecycleEnabled = hasMapsApiKey()
        if (mapLifecycleEnabled) {
            try {
                mapView.onCreate(savedInstanceState)
                mapView.getMapAsync(this)
            } catch (_: RuntimeException) {
                mapLifecycleEnabled = false
            }
        }

        updateScreen()
        if (fromLocation == null) requestLocationAccess()
    }

    private fun bindViews() {
        mapView = findViewById(R.id.route_plan_map)
        mapContainer = findViewById(R.id.route_plan_map_container)
        inputContent = findViewById(R.id.route_plan_input_content)
        previewContent = findViewById(R.id.route_plan_preview_content)
        noRouteContent = findViewById(R.id.route_plan_no_route_content)
        fromValue = findViewById(R.id.route_plan_from_value)
        toValue = findViewById(R.id.route_plan_to_value)
        locationStatus = findViewById(R.id.route_plan_location_status)
        locationSettings = findViewById(R.id.route_plan_location_settings)
        findRouteButton = findViewById(R.id.route_plan_find)
        searchDestinationButton = findViewById(R.id.route_plan_search_destination)
        estimatedTrip = findViewById(R.id.route_plan_estimated_trip)
        loadingOverlay = findViewById(R.id.route_plan_loading)
        loadingProgress = findViewById(R.id.route_plan_loading_progress)
        viewDirectionsButton = findViewById(R.id.route_plan_view_directions)
        editRouteButton = findViewById(R.id.route_plan_edit)
        noRouteEditButton = findViewById(R.id.route_plan_no_route_edit)
        noRouteBackButton = findViewById(R.id.route_plan_no_route_back)
        modeButtons = mapOf(
            RouteTravelMode.DRIVE to findViewById(R.id.route_plan_mode_drive),
            RouteTravelMode.WALK to findViewById(R.id.route_plan_mode_walk),
            RouteTravelMode.CYCLE to findViewById(R.id.route_plan_mode_cycle)
        )
    }

    private fun bindActions() {
        findViewById<View>(R.id.route_plan_back).setOnClickListener { finish() }
        findViewById<View>(R.id.route_plan_from_row).setOnClickListener { showPlaceSearch(selectsOrigin = true) }
        findViewById<View>(R.id.route_plan_to_row).setOnClickListener { showPlaceSearch(selectsOrigin = false) }
        findRouteButton.setOnClickListener { findRoute() }
        searchDestinationButton.setOnClickListener { showPlaceSearch(selectsOrigin = false) }
        locationSettings.setOnClickListener { openLocationSettings() }
        viewDirectionsButton.setOnClickListener { openGoogleMapsDirections() }
        editRouteButton.setOnClickListener { showInputState() }
        noRouteEditButton.setOnClickListener { showInputState() }
        noRouteBackButton.setOnClickListener { finish() }
        modeButtons.forEach { (mode, button) -> button.setOnClickListener { selectMode(mode) } }
        previewModeButton(RouteTravelMode.DRIVE).setOnClickListener { selectMode(RouteTravelMode.DRIVE) }
        previewModeButton(RouteTravelMode.WALK).setOnClickListener { selectMode(RouteTravelMode.WALK) }
        previewModeButton(RouteTravelMode.CYCLE).setOnClickListener { selectMode(RouteTravelMode.CYCLE) }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isCompassEnabled = false
        startIcon = createStartMarkerIcon()
        if (locationProvider.hasPermission()) {
            try {
                map.isMyLocationEnabled = true
            } catch (_: SecurityException) {
                // The permission callback remains the source of truth.
            }
        }
        drawRoute()
    }

    private fun requestLocationAccess() {
        if (!locationProvider.hasPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            loadCurrentLocation()
        }
    }

    private fun loadCurrentLocation() {
        showLocationProblem(null)
        locationProvider.findCurrentLocation(
            onSuccess = { location ->
                fromLocation = LatLng(location.latitude, location.longitude)
                fromPlace = null
                showLocationProblem(null)
                updateScreen()
            },
            onFailure = { problem ->
                showLocationProblem(problem)
                updateScreen()
            }
        )
    }

    private fun showLocationProblem(problem: GpsLocationProvider.LocationProblem?) {
        if (problem == null) {
            locationStatus.visibility = View.GONE
            locationSettings.visibility = View.GONE
            return
        }
        locationStatus.visibility = View.VISIBLE
        locationSettings.visibility = View.VISIBLE
        when (problem) {
            GpsLocationProvider.LocationProblem.PERMISSION_DENIED -> {
                locationStatus.setText(R.string.route_plan_location_permission_message)
                locationSettings.setText(R.string.route_plan_open_app_settings)
            }
            GpsLocationProvider.LocationProblem.LOCATION_DISABLED -> {
                locationStatus.setText(R.string.route_plan_location_disabled_message)
                locationSettings.setText(R.string.route_plan_open_location_settings)
            }
            GpsLocationProvider.LocationProblem.FIX_UNAVAILABLE -> {
                locationStatus.setText(R.string.route_plan_location_unavailable_message)
                locationSettings.setText(R.string.route_plan_try_location_again)
            }
        }
    }

    private fun openLocationSettings() {
        val action = if (locationProvider.hasPermission()) {
            Settings.ACTION_LOCATION_SOURCE_SETTINGS
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
        val intent = if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
            Intent(action, Uri.parse("package:$packageName"))
        } else {
            Intent(action)
        }
        startActivity(intent)
    }

    private fun selectMode(mode: RouteTravelMode) {
        if (isLoading) return
        selectedMode = mode
        updateModeButtons()
        if (screenState == ScreenState.PREVIEW) findRoute()
    }

    private fun findRoute() {
        val origin = fromLocation
        val target = destination
        if (origin == null) {
            requestLocationAccess()
            return
        }
        if (target == null || isLoading) return

        routeJob?.cancel()
        isLoading = true
        updateScreen()
        routeJob = lifecycleScope.launch {
            val result = withTimeoutOrNull(ROUTE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    directionsClient.findRoute(origin, LatLng(target.latitude, target.longitude), selectedMode)
                }
            }
            if (!isActive) return@launch
            isLoading = false
            if (result == null) {
                routeDetails = null
                screenState = ScreenState.NO_ROUTE
            } else {
                routeDetails = result
                screenState = ScreenState.PREVIEW
            }
            updateScreen()
            drawRoute()
        }
    }

    private fun showInputState() {
        routeJob?.cancel()
        isLoading = false
        screenState = ScreenState.INPUT
        updateScreen()
    }

    private fun showPlaceSearch(selectsOrigin: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_route_destination_search, null)
        val searchTitle = dialogView.findViewById<TextView>(R.id.route_place_search_title)
        val searchInput = dialogView.findViewById<EditText>(R.id.route_destination_search_input)
        val searchButton = dialogView.findViewById<TextView>(R.id.route_destination_search_button)
        val searchProgress = dialogView.findViewById<ProgressBar>(R.id.route_destination_search_progress)
        val searchResults = dialogView.findViewById<RecyclerView>(R.id.route_destination_search_results)
        val searchMessage = dialogView.findViewById<TextView>(R.id.route_destination_search_message)
        lateinit var dialog: AlertDialog
        searchTitle.setText(
            if (selectsOrigin) {
                R.string.route_plan_search_origin
            } else {
                R.string.route_plan_search_destination
            }
        )
        val adapter = EarthPlaceAdapter { place ->
            if (selectsOrigin) {
                fromPlace = place
                fromLocation = LatLng(place.latitude, place.longitude)
            } else {
                destination = place
            }
            routeDetails = null
            searchJob?.cancel()
            dialog.dismiss()
            updateScreen()
        }
        searchResults.layoutManager = LinearLayoutManager(this)
        searchResults.adapter = adapter

        dialog = AlertDialog.Builder(this).setView(dialogView).create()
        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.length < 2) {
                searchMessage.setText(R.string.route_plan_search_enter_destination)
                searchMessage.visibility = View.VISIBLE
                return@setOnClickListener
            }
            searchJob?.cancel()
            searchProgress.visibility = View.VISIBLE
            searchButton.isEnabled = false
            searchMessage.visibility = View.GONE
            searchJob = lifecycleScope.launch {
                val places = withContext(Dispatchers.IO) {
                    runCatching { EarthPlaceSearch.search(query) }.getOrDefault(emptyList())
                }
                if (!isActive) return@launch
                searchProgress.visibility = View.GONE
                searchButton.isEnabled = true
                adapter.submitList(places)
                if (places.isEmpty()) {
                    searchMessage.setText(R.string.route_plan_search_no_results)
                    searchMessage.visibility = View.VISIBLE
                }
            }
        }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchButton.performClick()
                true
            } else false
        }
        dialog.setOnDismissListener { searchJob?.cancel() }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        searchInput.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    private fun drawRoute() {
        val map = googleMap ?: return
        val details = routeDetails
        val origin = fromLocation
        val target = destination
        if (screenState != ScreenState.PREVIEW || details == null || origin == null || target == null) {
            map.clear()
            startMarker = null
            destinationMarker = null
            routeOutline = null
            routeLine = null
            return
        }

        map.clear()
        routeOutline = map.addPolyline(
            PolylineOptions().addAll(details.path).color(ROUTE_OUTLINE_COLOR).width(10f)
        )
        routeLine = map.addPolyline(
            PolylineOptions().addAll(details.path).color(ROUTE_LINE_COLOR).width(6f)
        )
        startMarker = map.addMarker(
            MarkerOptions().position(origin).title(getString(R.string.route_plan_start))
                .icon(startIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )
        destinationMarker = map.addMarker(
            MarkerOptions().position(LatLng(target.latitude, target.longitude))
                .title(target.name)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        val boundsBuilder = LatLngBounds.builder()
        details.path.forEach(boundsBuilder::include)
        boundsBuilder.include(origin)
        boundsBuilder.include(LatLng(target.latitude, target.longitude))
        runCatching {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 72))
        }
    }

    private fun updateScreen() {
        inputContent.visibility = if (screenState == ScreenState.INPUT) View.VISIBLE else View.GONE
        previewContent.visibility = if (screenState == ScreenState.PREVIEW) View.VISIBLE else View.GONE
        noRouteContent.visibility = if (screenState == ScreenState.NO_ROUTE) View.VISIBLE else View.GONE
        mapContainer.visibility = if (screenState == ScreenState.PREVIEW) View.VISIBLE else View.GONE
        loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        loadingProgress.visibility = if (isLoading) View.VISIBLE else View.GONE

        fromValue.text = fromPlace?.name ?: getString(R.string.route_plan_your_location)
        toValue.text = destination?.name ?: getString(R.string.route_plan_select_destination)
        findRouteButton.isEnabled = fromLocation != null && destination != null && !isLoading
        searchDestinationButton.isEnabled = !isLoading
        viewDirectionsButton.isEnabled = !isLoading
        editRouteButton.isEnabled = !isLoading
        updateModeButtons()
        routeDetails?.let {
            estimatedTrip.text = getString(
                R.string.route_plan_estimated_trip_value,
                formatDuration(it.durationSeconds),
                formatDistance(it.distanceMeters)
            )
        }
    }

    private fun updateModeButtons() {
        modeButtons.forEach { (mode, button) ->
            button.isSelected = mode == selectedMode
            button.isEnabled = !isLoading
            previewModeButton(mode).apply {
                isSelected = mode == selectedMode
                isEnabled = !isLoading
            }
        }
    }

    private fun previewModeButton(mode: RouteTravelMode): TextView = when (mode) {
        RouteTravelMode.DRIVE -> findViewById(R.id.route_plan_preview_mode_drive)
        RouteTravelMode.WALK -> findViewById(R.id.route_plan_preview_mode_walk)
        RouteTravelMode.CYCLE -> findViewById(R.id.route_plan_preview_mode_cycle)
    }

    private fun formatDuration(seconds: Int): String {
        val minutes = (seconds / 60.0).toInt().coerceAtLeast(1)
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            if (remainingMinutes == 0) "${hours}h" else "${hours}h ${remainingMinutes} min"
        } else {
            "$minutes min"
        }
    }

    private fun formatDistance(meters: Int): String {
        return if (meters < 1_000) {
            "$meters m"
        } else {
            String.format(Locale.getDefault(), "%.1f km", meters / 1_000.0)
        }
    }

    private fun openGoogleMapsDirections() {
        val origin = fromLocation ?: return
        val target = destination ?: return
        val url = "https://www.google.com/maps/dir/?api=1" +
            "&origin=${origin.latitude},${origin.longitude}" +
            "&destination=${target.latitude},${target.longitude}" +
            "&travelmode=${selectedMode.directionsValue}"
        val uri = Uri.parse(url)
        val mapsIntent = Intent(Intent.ACTION_VIEW, uri).setPackage(GOOGLE_MAPS_PACKAGE)
        if (mapsIntent.resolveActivity(packageManager) != null) {
            startActivity(mapsIntent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun createStartMarkerIcon(): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val size = (28 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = START_MARKER_COLOR
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
            val metadata = packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            ).metaData
            val key = metadata?.getString("com.google.android.geo.API_KEY")
            !key.isNullOrBlank() && !key.contains("YOUR_MAPS_API_KEY")
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        savedInstanceState ?: return
        screenState = ScreenState.values().getOrElse(
            savedInstanceState.getInt(STATE_SCREEN, ScreenState.INPUT.ordinal)
        ) { ScreenState.INPUT }
        selectedMode = RouteTravelMode.values().getOrElse(
            savedInstanceState.getInt(STATE_MODE, RouteTravelMode.DRIVE.ordinal)
        ) { RouteTravelMode.DRIVE }
        val fromLat = savedInstanceState.getDouble(STATE_FROM_LAT, Double.NaN)
        val fromLon = savedInstanceState.getDouble(STATE_FROM_LON, Double.NaN)
        if (!fromLat.isNaN() && !fromLon.isNaN()) fromLocation = LatLng(fromLat, fromLon)
        val fromName = savedInstanceState.getString(STATE_FROM_NAME)
        if (!fromLat.isNaN() && !fromLon.isNaN() && !fromName.isNullOrBlank()) {
            fromPlace = EarthPlace(
                fromName,
                savedInstanceState.getString(STATE_FROM_ADDRESS).orEmpty(),
                fromLat,
                fromLon
            )
        }
        val destinationLat = savedInstanceState.getDouble(STATE_DESTINATION_LAT, Double.NaN)
        val destinationLon = savedInstanceState.getDouble(STATE_DESTINATION_LON, Double.NaN)
        val destinationName = savedInstanceState.getString(STATE_DESTINATION_NAME)
        if (!destinationLat.isNaN() && !destinationLon.isNaN() && destinationName != null) {
            destination = EarthPlace(
                destinationName,
                savedInstanceState.getString(STATE_DESTINATION_ADDRESS).orEmpty(),
                destinationLat,
                destinationLon
            )
        }
        val routeLatitudes = savedInstanceState.getDoubleArray(STATE_ROUTE_LATITUDES)
        val routeLongitudes = savedInstanceState.getDoubleArray(STATE_ROUTE_LONGITUDES)
        if (routeLatitudes != null && routeLongitudes != null &&
            routeLatitudes.size == routeLongitudes.size && routeLatitudes.isNotEmpty()
        ) {
            routeDetails = RouteDetails(
                routeLatitudes.indices.map { LatLng(routeLatitudes[it], routeLongitudes[it]) },
                savedInstanceState.getInt(STATE_ROUTE_DISTANCE),
                savedInstanceState.getInt(STATE_ROUTE_DURATION)
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SCREEN, screenState.ordinal)
        outState.putInt(STATE_MODE, selectedMode.ordinal)
        fromLocation?.let {
            outState.putDouble(STATE_FROM_LAT, it.latitude)
            outState.putDouble(STATE_FROM_LON, it.longitude)
        }
        fromPlace?.let {
            outState.putString(STATE_FROM_NAME, it.name)
            outState.putString(STATE_FROM_ADDRESS, it.address)
        }
        destination?.let {
            outState.putDouble(STATE_DESTINATION_LAT, it.latitude)
            outState.putDouble(STATE_DESTINATION_LON, it.longitude)
            outState.putString(STATE_DESTINATION_NAME, it.name)
            outState.putString(STATE_DESTINATION_ADDRESS, it.address)
        }
        routeDetails?.let { route ->
            outState.putDoubleArray(STATE_ROUTE_LATITUDES, route.path.map { it.latitude }.toDoubleArray())
            outState.putDoubleArray(STATE_ROUTE_LONGITUDES, route.path.map { it.longitude }.toDoubleArray())
            outState.putInt(STATE_ROUTE_DISTANCE, route.distanceMeters)
            outState.putInt(STATE_ROUTE_DURATION, route.durationSeconds)
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
        if (screenState == ScreenState.INPUT && fromLocation == null && locationProvider.hasPermission()) {
            loadCurrentLocation()
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
        routeJob?.cancel()
        searchJob?.cancel()
        if (mapLifecycleEnabled) mapView.onDestroy()
        super.onDestroy()
    }

    private companion object {
        const val ROUTE_TIMEOUT_MS = 15_000L
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
        const val ROUTE_OUTLINE_COLOR = android.graphics.Color.WHITE
        const val ROUTE_LINE_COLOR = 0xFF2D7FF9.toInt()
        const val START_MARKER_COLOR = 0xFF2D7FF9.toInt()
        const val STATE_SCREEN = "route_screen"
        const val STATE_MODE = "route_mode"
        const val STATE_FROM_LAT = "route_from_lat"
        const val STATE_FROM_LON = "route_from_lon"
        const val STATE_FROM_NAME = "route_from_name"
        const val STATE_FROM_ADDRESS = "route_from_address"
        const val STATE_DESTINATION_LAT = "route_destination_lat"
        const val STATE_DESTINATION_LON = "route_destination_lon"
        const val STATE_DESTINATION_NAME = "route_destination_name"
        const val STATE_DESTINATION_ADDRESS = "route_destination_address"
        const val STATE_ROUTE_LATITUDES = "route_latitudes"
        const val STATE_ROUTE_LONGITUDES = "route_longitudes"
        const val STATE_ROUTE_DISTANCE = "route_distance"
        const val STATE_ROUTE_DURATION = "route_duration"
    }
}
