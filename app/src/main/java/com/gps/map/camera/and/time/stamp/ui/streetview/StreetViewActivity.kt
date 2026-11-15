package com.gps.map.camera.and.time.stamp.ui.streetview

import android.Manifest
import android.graphics.Point
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.OnStreetViewPanoramaReadyCallback
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.StreetViewPanoramaView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.StreetViewPanoramaLocation
import com.google.android.gms.maps.model.StreetViewPanoramaCamera
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlace
import com.gps.map.camera.and.time.stamp.utils.location.GpsLocationProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Lets the user drop Pegman on a real map before opening Street View. */
class StreetViewActivity : AppCompatActivity(), OnMapReadyCallback,
    OnStreetViewPanoramaReadyCallback {

    private enum class ScreenState { DRAG, RELEASE, PANORAMA, UNAVAILABLE }

    private lateinit var panoramaView: StreetViewPanoramaView
    private lateinit var mapView: MapView
    private lateinit var previewContainer: FrameLayout
    private lateinit var mapContainer: FrameLayout
    private lateinit var dragHint: View
    private lateinit var hintIcon: ImageView
    private lateinit var hintTitle: TextView
    private lateinit var hintMessage: TextView
    private lateinit var tryAnotherRoad: TextView
    private lateinit var panoramaLocation: View
    private lateinit var panoramaFullscreen: TextView
    private lateinit var mapPlace: TextView
    private lateinit var pegman: ImageView
    private lateinit var mapZoomIn: TextView
    private lateinit var mapZoomOut: TextView
    private lateinit var panoramaZoomIn: TextView
    private lateinit var panoramaZoomOut: TextView

    private var googleMap: GoogleMap? = null
    private val coverageRoads = mutableListOf<Polyline>()
    private var shouldUseCurrentLocation = false
    private var manualPlaceSelected = false
    private var panorama: StreetViewPanorama? = null
    private var panoramaHasLocation = false
    private var selectedPlace: EarthPlace? = null
    private var state = ScreenState.DRAG
    private var mapFullscreen = false
    private var panoramaFullscreenMode = false
    private var permissionWasRequested = false
    private var returningFromSettings = false
    private var lastLocationProblem: GpsLocationProvider.LocationProblem? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var pegmanMoved = false
    private var locationJob: Job? = null
    private var panoramaJob: Job? = null
    private var panoramaRequest = 0
    private val locationProvider by lazy { GpsLocationProvider(this) }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            enableMyLocationLayer()
            findCurrentPlace()
        } else {
            lastLocationProblem = GpsLocationProvider.LocationProblem.PERMISSION_DENIED
            showUnavailable()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_street_view)
        bindViews()
        selectedPlace = if (
            intent.getBooleanExtra(EXTRA_CURRENT_LOCATION_ONLY, false)
        ) {
            null
        } else {
            readInitialPlace()
        }
        shouldUseCurrentLocation = selectedPlace == null
        bindActions()

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        panoramaView.onCreate(savedInstanceState)
        panoramaView.getStreetViewPanoramaAsync(this)

        selectedPlace?.let { updatePlace(it) } ?: findCurrentPlace()
        showPreviewState()
    }

    private fun bindViews() {
        panoramaView = findViewById(R.id.street_view_panorama)
        mapView = findViewById(R.id.street_view_aerial_map)
        previewContainer = findViewById(R.id.street_view_preview_container)
        mapContainer = findViewById(R.id.street_view_map_container)
        dragHint = findViewById(R.id.street_view_drag_hint)
        hintIcon = findViewById(R.id.street_view_hint_icon)
        hintTitle = findViewById(R.id.street_view_hint_title)
        hintMessage = findViewById(R.id.street_view_hint_message)
        tryAnotherRoad = findViewById(R.id.street_view_try_another_road)
        panoramaLocation = findViewById(R.id.street_view_panorama_location)
        panoramaFullscreen = findViewById(R.id.street_view_panorama_fullscreen)
        mapPlace = findViewById(R.id.street_view_map_place)
        pegman = findViewById(R.id.street_view_pegman)
        mapZoomIn = findViewById(R.id.street_view_map_zoom_in)
        mapZoomOut = findViewById(R.id.street_view_map_zoom_out)
        panoramaZoomIn = findViewById(R.id.street_view_panorama_zoom_in)
        panoramaZoomOut = findViewById(R.id.street_view_panorama_zoom_out)

        previewContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
        previewContainer.clipToOutline = true
        mapContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
        mapContainer.clipToOutline = true
        // Keep Pegman visible even while the location fix or the map camera
        // is still being initialised. It is the entry point for a manual map
        // selection, so it must never depend on a panorama callback.
        pegman.visibility = View.VISIBLE

        // The page container must not steal map gestures. In particular, a
        // vertical move during a pinch can otherwise be interpreted as page
        // scrolling instead of Google Maps zoom/pan input.
        protectMapGestures(mapView)
        protectMapGestures(panoramaView)
    }

    private fun protectMapGestures(mapSurface: View) {
        mapSurface.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }

    private fun bindActions() {
        findViewById<View>(R.id.street_view_back).setOnClickListener {
            if (state == ScreenState.PANORAMA) {
                finish()
            } else if (mapFullscreen) {
                toggleMapFullscreen()
            } else {
                finish()
            }
        }
        findViewById<ImageButton>(R.id.street_view_map_fullscreen)
            .setOnClickListener { toggleMapFullscreen() }
        panoramaFullscreen.setOnClickListener { togglePanoramaFullscreen() }
        tryAnotherRoad.setOnClickListener { openStreetView() }
        pegman.setOnTouchListener(::onPegmanTouch)
        mapZoomIn.setOnClickListener { zoomMap(1f) }
        mapZoomOut.setOnClickListener { zoomMap(-1f) }
        panoramaZoomIn.setOnClickListener { zoomPanorama(0.5f) }
        panoramaZoomOut.setOnClickListener { zoomPanorama(-0.5f) }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isZoomGesturesEnabled = true
        map.uiSettings.isScrollGesturesEnabled = true
        map.uiSettings.isRotateGesturesEnabled = true
        map.uiSettings.isTiltGesturesEnabled = false
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.setOnMapClickListener(::selectMapPoint)
        map.setOnPolylineClickListener { road ->
            // Blue coverage roads are real interaction targets, not decoration.
            // Resolve the panorama at the road end when the user taps one.
            road.points.lastOrNull()?.let(::selectMapPoint)
        }
        enableMyLocationLayer()
        selectedPlace?.let { drawPlace(it) } ?: resetPegmanToCorner()
    }

    private fun enableMyLocationLayer() {
        val map = googleMap ?: return
        if (!locationProvider.hasPermission()) return
        try {
            // Let Google Maps render its native blue current-location dot.
            map.isMyLocationEnabled = true
        } catch (_: SecurityException) {
            // Permission may be revoked while the map is starting.
        }
    }

    override fun onStreetViewPanoramaReady(readyPanorama: StreetViewPanorama) {
        panorama = readyPanorama
        readyPanorama.isUserNavigationEnabled = true
        readyPanorama.isZoomGesturesEnabled = true
        readyPanorama.isPanningGesturesEnabled = true
        readyPanorama.isStreetNamesEnabled = true
        readyPanorama.setOnStreetViewPanoramaChangeListener { location ->
            if (location == null) {
                panoramaHasLocation = false
                if (state == ScreenState.PANORAMA) showUnavailable()
            } else {
                panoramaHasLocation = true
                panoramaJob?.cancel()
                drawCoverageRoad(location)
                showPreviewState()
            }
        }
        // The preview is the default state. If the panorama callback arrived
        // after the place was loaded, request that place now instead of waiting
        // for a Pegman drag.
        selectedPlace?.let {
            requestPanorama(LatLng(it.latitude, it.longitude))
        } ?: showPreviewState()
    }

    private fun onPegmanTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.tag = floatArrayOf(view.translationX, view.translationY)
                downRawX = event.rawX
                downRawY = event.rawY
                pegmanMoved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val start = view.tag as? FloatArray ?: return true
                pegmanMoved = pegmanMoved ||
                    abs(event.rawX - downRawX) > dp(8) || abs(event.rawY - downRawY) > dp(8)
                view.translationX = start[0] + event.rawX - downRawX
                view.translationY = start[1] + event.rawY - downRawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                val wasMoved = pegmanMoved
                val droppedPoint = if (wasMoved) pegmanDropPoint() else null
                view.animate().translationX(0f).translationY(0f).setDuration(160).start()
                if (droppedPoint != null) {
                    selectMapPoint(droppedPoint)
                } else {
                    openStreetView()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                view.animate().translationX(0f).translationY(0f).setDuration(160).start()
                return true
            }
        }
        return true
    }

    private fun showDragState() {
        // Kept as a compatibility entry point for older state callbacks. The
        // usable preview is now always the first screen; the user can tap a
        // covered road or Pegman directly.
        showPreviewState()
    }

    private fun showReleaseState() {
        showPreviewState()
    }

    private fun showPreviewState() {
        state = ScreenState.PANORAMA
        val hasPreview = selectedPlace != null && panorama != null && panoramaHasLocation
        dragHint.visibility = if (hasPreview) View.GONE else View.VISIBLE
        panoramaView.visibility = if (hasPreview) View.VISIBLE else View.GONE
        panoramaLocation.visibility = if (hasPreview) View.VISIBLE else View.GONE
        panoramaFullscreen.visibility = if (hasPreview) View.VISIBLE else View.GONE
        panoramaZoomIn.visibility = if (hasPreview) View.VISIBLE else View.GONE
        panoramaZoomOut.visibility = if (hasPreview) View.VISIBLE else View.GONE
        tryAnotherRoad.visibility = View.GONE
        findViewById<TextView>(R.id.street_view_section_title)
            .setText(R.string.street_view_title)
        if (!hasPreview) {
            hintIcon.setImageResource(R.drawable.ic_globe)
            hintIcon.background = null
            hintIcon.setPadding(dp(20), dp(20), dp(20), dp(20))
            hintTitle.setText(R.string.street_view_title)
            hintMessage.setText(R.string.street_view_loading_message)
        }
    }

    private fun openStreetView(point: LatLng? = null) {
        val place = selectedPlace
        if (place == null) {
            showUnavailable()
            return
        }
        requestPanorama(point ?: LatLng(place.latitude, place.longitude))
    }

    private fun showUnavailable() {
        state = ScreenState.UNAVAILABLE
        panoramaHasLocation = false
        panoramaJob?.cancel()
        dragHint.visibility = View.VISIBLE
        panoramaView.visibility = View.GONE
        panoramaLocation.visibility = View.GONE
        panoramaFullscreen.visibility = View.GONE
        hintIcon.setImageResource(R.drawable.ic_earth_map_error)
        hintIcon.background = null
        hintIcon.setPadding(dp(20), dp(20), dp(20), dp(20))
        hintTitle.setText(R.string.street_view_no_street_view_title)
        hintMessage.setText(R.string.street_view_no_coverage_message)
        tryAnotherRoad.visibility = View.VISIBLE
    }

    private fun updatePlace(place: EarthPlace) {
        selectedPlace = place
        mapPlace.text = formatMapPlace(place)
        findViewById<TextView>(R.id.street_view_location_title).text = place.name
        findViewById<TextView>(R.id.street_view_location_description).text =
            getString(R.string.street_view_location_meta)
        drawPlace(place)
    }

    private fun drawPlace(place: EarthPlace) {
        googleMap?.let { map ->
            val point = LatLng(place.latitude, place.longitude)
            map.clear()
            clearCoverageRoads()
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 16f))
            positionPegman(point)
        }
        // Request the nearest panorama immediately so coverage roads are
        // visible before the user opens Street View.
        panorama?.setPosition(
            LatLng(place.latitude, place.longitude),
            PANORAMA_SEARCH_RADIUS_METERS
        )
    }

    private fun selectMapPoint(point: LatLng) {
        manualPlaceSelected = true
        shouldUseCurrentLocation = false
        val current = selectedPlace ?: EarthPlace(
            getString(R.string.earth_view_current_location),
            "",
            point.latitude,
            point.longitude
        )
        selectedPlace = current.copy(latitude = point.latitude, longitude = point.longitude)
        mapPlace.text = formatMapPlace(selectedPlace!!)
        clearCoverageRoads()
        positionPegman(point)
        // A normal map tap is the quick path: resolve the nearest panorama and
        // show it immediately. Pegman remains available for another road.
        openStreetView(point)
    }

    private fun pegmanDropPoint(): LatLng? {
        val map = googleMap ?: return null
        val x = (pegman.x + pegman.translationX + pegman.width / 2f - mapView.left).toInt()
        val y = (pegman.y + pegman.translationY + pegman.height - mapView.top).toInt()
        return map.projection.fromScreenLocation(Point(x, y))
    }

    private fun positionPegman(point: LatLng) {
        val map = googleMap ?: return
        pegman.post {
            if (mapContainer.width == 0 || mapContainer.height == 0) return@post
            val screenPoint = map.projection.toScreenLocation(point)
            pegman.x = (mapView.left + screenPoint.x - pegman.width / 2f)
                .coerceIn(0f, (mapContainer.width - pegman.width).toFloat())
            pegman.y = (mapView.top + screenPoint.y - pegman.height.toFloat())
                .coerceIn(0f, (mapContainer.height - pegman.height).toFloat())
            pegman.translationX = 0f
            pegman.translationY = 0f
            pegman.visibility = View.VISIBLE
        }
    }

    private fun resetPegmanToCorner() {
        pegman.post {
            pegman.x = (mapContainer.width - pegman.width - dp(20)).coerceAtLeast(0).toFloat()
            pegman.y = (mapContainer.height - pegman.height - dp(20)).coerceAtLeast(0).toFloat()
            pegman.translationX = 0f
            pegman.translationY = 0f
            pegman.visibility = View.VISIBLE
        }
    }

    private fun drawCoverageRoad(location: StreetViewPanoramaLocation) {
        val map = googleMap ?: return
        clearCoverageRoads()
        val center = location.position ?: return
        positionPegman(center)
        val links = location.links ?: return
        if (links.isEmpty()) return
        links.forEach { link ->
            coverageRoads += map.addPolyline(
                PolylineOptions()
                    .add(center, pointAlongBearing(center, link.bearing, 220.0))
                    .color(getColor(R.color.home_accent))
                    .width(10f)
                    .zIndex(2f)
                    .clickable(true)
            )
        }
    }

    private fun clearCoverageRoads() {
        coverageRoads.forEach { it.remove() }
        coverageRoads.clear()
    }

    private fun zoomMap(amount: Float) {
        googleMap?.animateCamera(CameraUpdateFactory.zoomBy(amount))
    }

    private fun zoomPanorama(amount: Float) {
        val currentPanorama = panorama ?: return
        val current = currentPanorama.panoramaCamera
        val camera = StreetViewPanoramaCamera.Builder(current)
            .zoom((current.zoom + amount).coerceAtLeast(0f))
            .build()
        currentPanorama.animateTo(camera, PANORAMA_ZOOM_ANIMATION_MS)
    }

    private fun pointAlongBearing(start: LatLng, bearing: Float, distanceMeters: Double): LatLng {
        val radius = 6_371_000.0
        val angularDistance = distanceMeters / radius
        val bearingRadians = Math.toRadians(bearing.toDouble())
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val lat2 = Math.asin(
            Math.sin(lat1) * Math.cos(angularDistance) +
                Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearingRadians)
        )
        val lon2 = lon1 + Math.atan2(
            Math.sin(bearingRadians) * Math.sin(angularDistance) * Math.cos(lat1),
            Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2)
        )
        return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    private fun findCurrentPlace() {
        if (!locationProvider.hasPermission()) {
            permissionWasRequested = true
            locationPermissionLauncher.launch(locationPermissions())
            return
        }
        locationJob?.cancel()
        locationJob = lifecycleScope.launch {
            locationProvider.findCurrentLocation(
                onSuccess = { location ->
                    runOnUiThread {
                        if (shouldUseCurrentLocation && !manualPlaceSelected) {
                            updatePlace(
                                EarthPlace(
                                    getString(R.string.earth_view_current_location),
                                    "",
                                    location.latitude,
                                    location.longitude
                                )
                            )
                        }
                    }
                },
                onFailure = { problem ->
                    runOnUiThread {
                        lastLocationProblem = problem
                        showUnavailable()
                    }
                }
            )
        }
    }

    private fun requestPanorama(point: LatLng) {
        val request = ++panoramaRequest
        panoramaJob?.cancel()
        panoramaHasLocation = false
        showPreviewState()
        panorama?.setPosition(point, PANORAMA_SEARCH_RADIUS_METERS)

        // The SDK reports a null location when no panorama exists, but a
        // network/service failure can leave the view waiting indefinitely.
        // Always give the user a useful retry state instead of an endless
        // "Loading nearby Street View" card.
        panoramaJob = lifecycleScope.launch {
            delay(PANORAMA_LOAD_TIMEOUT_MS)
            if (request == panoramaRequest && !panoramaHasLocation) {
                showUnavailable()
            }
        }
    }

    private fun toggleMapFullscreen() {
        mapFullscreen = !mapFullscreen
        previewContainer.visibility = if (mapFullscreen) View.GONE else View.VISIBLE
        findViewById<View>(R.id.street_view_divider).visibility = if (mapFullscreen) View.GONE else View.VISIBLE
        findViewById<View>(R.id.street_view_map_title).visibility = if (mapFullscreen) View.GONE else View.VISIBLE
        mapPlace.visibility = if (mapFullscreen) View.GONE else View.VISIBLE
        val params = mapContainer.layoutParams
        params.height = dp(
            if (mapFullscreen) FULLSCREEN_PANEL_HEIGHT_DP else NORMAL_MAP_HEIGHT_DP
        )
        mapContainer.layoutParams = params
        mapContainer.requestLayout()
    }

    private fun togglePanoramaFullscreen() {
        panoramaFullscreenMode = !panoramaFullscreenMode
        findViewById<View>(R.id.street_view_map_title).visibility = if (panoramaFullscreenMode) View.GONE else View.VISIBLE
        mapPlace.visibility = if (panoramaFullscreenMode) View.GONE else View.VISIBLE
        mapContainer.visibility = if (panoramaFullscreenMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.street_view_divider).visibility = if (panoramaFullscreenMode) View.GONE else View.VISIBLE
        val params = previewContainer.layoutParams
        params.height = dp(
            if (panoramaFullscreenMode) FULLSCREEN_PANEL_HEIGHT_DP else NORMAL_PREVIEW_HEIGHT_DP
        )
        previewContainer.layoutParams = params
        previewContainer.requestLayout()
    }

    private fun readInitialPlace(): EarthPlace? {
        val name = intent.getStringExtra(EXTRA_PLACE_NAME)?.trim().orEmpty()
        val address = intent.getStringExtra(EXTRA_PLACE_ADDRESS)?.trim().orEmpty()
        val latitude = intent.getDoubleExtra(EXTRA_PLACE_LATITUDE, Double.NaN)
        val longitude = intent.getDoubleExtra(EXTRA_PLACE_LONGITUDE, Double.NaN)
        return if (name.isNotEmpty() && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            EarthPlace(name, address, latitude, longitude)
        } else null
    }

    private fun formatMapPlace(place: EarthPlace): String {
        val parts = place.address.split(',').map(String::trim).filter(String::isNotEmpty)
        return if (parts.isNotEmpty()) "${place.name}, ${parts.takeLast(2).joinToString(", ")}" else place.name
    }

    private fun locationPermissions() = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        panoramaView.onResume()
        if (returningFromSettings) {
            returningFromSettings = false
            findCurrentPlace()
        }
    }

    override fun onPause() {
        mapView.onPause()
        panoramaView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        locationJob?.cancel()
        panoramaJob?.cancel()
        locationProvider.stopLocationUpdates()
        mapView.onDestroy()
        panoramaView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
        panoramaView.onLowMemory()
    }

    companion object {
        private const val NORMAL_PREVIEW_HEIGHT_DP = 280
        private const val NORMAL_MAP_HEIGHT_DP = 270
        private const val FULLSCREEN_PANEL_HEIGHT_DP = 680
        const val PANORAMA_SEARCH_RADIUS_METERS = 500
        const val PANORAMA_LOAD_TIMEOUT_MS = 12_000L
        const val PANORAMA_ZOOM_ANIMATION_MS = 220L
        const val EXTRA_CURRENT_LOCATION_ONLY = "street_view_current_location_only"
        const val EXTRA_PLACE_NAME = "earth_view_place_name"
        const val EXTRA_PLACE_ADDRESS = "earth_view_place_address"
        const val EXTRA_PLACE_LATITUDE = "earth_view_place_latitude"
        const val EXTRA_PLACE_LONGITUDE = "earth_view_place_longitude"
    }
}
