package com.gps.map.camera.and.time.stamp.ui.measurement

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.pref.AppSharedPreferencesApp
import com.gps.map.camera.and.time.stamp.ui.settings.MeasurementUnit
import com.gps.map.camera.and.time.stamp.utils.location.GpsLocationProvider
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Lets users place polygon corners on a map and calculate the enclosed area. */
class AreaMeasurementActivity : AppCompatActivity(), OnMapReadyCallback {
    private enum class ScreenState { EMPTY, EDITING, RESULT, INVALID }
    private enum class AreaUnit { SQUARE_METERS, HECTARES }

    private lateinit var mapView: MapView
    private lateinit var mapFallback: View
    private lateinit var mapUnavailable: View
    private lateinit var emptyContent: View
    private lateinit var editingContent: View
    private lateinit var resultContent: View
    private lateinit var invalidAction: View
    private lateinit var invalidBack: View
    private lateinit var instruction: TextView
    private lateinit var pointsCount: TextView
    private lateinit var actionButton: TextView
    private lateinit var unitButton: TextView
    private lateinit var areaValue: TextView
    private lateinit var perimeterValue: TextView

    private var mapLifecycleEnabled = false
    private var googleMap: GoogleMap? = null
    private var markerIcon: BitmapDescriptor? = null
    private val points = mutableListOf<LatLng>()
    private var screenState = ScreenState.EMPTY
    private var areaUnit = AreaUnit.HECTARES
    private val settingsPreferences by lazy { AppSharedPreferencesApp(applicationContext) }
    private val locationProvider by lazy { GpsLocationProvider(this) }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) showUserLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_area_measurement)
        bindViews()
        bindActions()

        mapLifecycleEnabled = hasMapsApiKey()
        if (mapLifecycleEnabled) {
            try {
                mapView.onCreate(savedInstanceState)
                mapView.getMapAsync(this)
            } catch (_: RuntimeException) {
                mapLifecycleEnabled = false
            }
        }
        updateScreenState()
    }

    private fun bindViews() {
        mapView = findViewById(R.id.area_measurement_map)
        mapFallback = findViewById(R.id.area_measurement_map_fallback)
        mapUnavailable = findViewById(R.id.area_measurement_map_unavailable)
        emptyContent = findViewById(R.id.area_measurement_empty_content)
        editingContent = findViewById(R.id.area_measurement_editing_content)
        resultContent = findViewById(R.id.area_measurement_result_content)
        invalidAction = findViewById(R.id.area_measurement_invalid_action)
        invalidBack = findViewById(R.id.area_measurement_invalid_back)
        instruction = findViewById(R.id.area_measurement_instruction)
        pointsCount = findViewById(R.id.area_measurement_points_count)
        actionButton = findViewById(R.id.area_measurement_action)
        unitButton = findViewById(R.id.area_measurement_unit)

        val areaCard = findViewById<View>(R.id.area_measurement_area_card)
        val perimeterCard = findViewById<View>(R.id.area_measurement_perimeter_card)
        areaValue = areaCard.findViewById(R.id.area_measurement_value)
        perimeterValue = perimeterCard.findViewById(R.id.area_measurement_value)
        areaCard.findViewById<TextView>(R.id.area_measurement_value_label)
            .setText(R.string.area_measurement_area)
        perimeterCard.findViewById<TextView>(R.id.area_measurement_value_label)
            .setText(R.string.area_measurement_perimeter)
    }

    private fun bindActions() {
        findViewById<View>(R.id.area_measurement_back).setOnClickListener { finish() }
        actionButton.setOnClickListener {
            Toast.makeText(this, R.string.area_measurement_tap_map, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.area_measurement_undo).setOnClickListener { undoLastPoint() }
        findViewById<View>(R.id.area_measurement_reset).setOnClickListener { resetBoundary() }
        findViewById<View>(R.id.area_measurement_unit).setOnClickListener { toggleAreaUnit() }
        findViewById<View>(R.id.area_measurement_finish).setOnClickListener { finishBoundary() }
        findViewById<View>(R.id.area_measurement_again).setOnClickListener { resetBoundary() }
        findViewById<View>(R.id.area_measurement_result_back).setOnClickListener { finish() }
        invalidAction.setOnClickListener { showEditingState() }
        invalidBack.setOnClickListener { finish() }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        markerIcon = createBoundaryMarkerIcon()
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isZoomControlsEnabled = false
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.setOnMapClickListener(::addPoint)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM))
        centerOnUserLocationIfAvailable()
    }

    private fun addPoint(point: LatLng) {
        if (screenState == ScreenState.RESULT || screenState == ScreenState.INVALID) return
        points += point
        screenState = ScreenState.EDITING
        drawBoundary()
        updateScreenState()
    }

    private fun drawBoundary() {
        googleMap?.let { map ->
            map.clear()
            points.forEachIndexed { index, point ->
                map.addMarker(
                    MarkerOptions()
                        .position(point)
                        .title(getString(R.string.area_measurement_point_number, index + 1))
                        .icon(markerIcon ?: BitmapDescriptorFactory.defaultMarker())
                        .anchor(0.5f, 0.5f)
                )
            }
            if (points.size >= 2) {
                map.addPolygon(
                    PolygonOptions()
                        .addAll(points)
                        .strokeColor(POLYGON_STROKE_COLOR)
                        .strokeWidth(POLYGON_STROKE_WIDTH)
                        .fillColor(POLYGON_FILL_COLOR)
                )
            }
        }
    }

    private fun undoLastPoint() {
        if (points.isNotEmpty()) points.removeAt(points.lastIndex)
        screenState = if (points.isEmpty()) ScreenState.EMPTY else ScreenState.EDITING
        drawBoundary()
        updateScreenState()
    }

    private fun resetBoundary() {
        points.clear()
        googleMap?.clear()
        screenState = ScreenState.EMPTY
        updateScreenState()
    }

    private fun toggleAreaUnit() {
        areaUnit = if (areaUnit == AreaUnit.HECTARES) AreaUnit.SQUARE_METERS else AreaUnit.HECTARES
        if (screenState == ScreenState.RESULT) updateResultValues()
    }

    private fun finishBoundary() {
        if (!hasValidBoundary()) {
            showInvalidState()
            return
        }
        screenState = ScreenState.RESULT
        updateResultValues()
        updateScreenState()
    }

    private fun showEditingState() {
        screenState = ScreenState.EDITING
        updateScreenState()
        centerOnUserLocationIfAvailable()
    }

    private fun hasValidBoundary(): Boolean {
        if (points.size < MINIMUM_POINTS || points.distinct().size < MINIMUM_POINTS) return false
        if (AreaMeasurementCalculator.areaSquareMeters(points) <= 0.0) return false

        val edgeCount = points.size
        for (firstEdge in 0 until edgeCount) {
            val firstStart = points[firstEdge]
            val firstEnd = points[(firstEdge + 1) % edgeCount]
            for (secondEdge in (firstEdge + 1) until edgeCount) {
                if (secondEdge == firstEdge + 1) continue
                if (firstEdge == 0 && secondEdge == edgeCount - 1) continue
                val secondStart = points[secondEdge]
                val secondEnd = points[(secondEdge + 1) % edgeCount]
                if (segmentsIntersect(firstStart, firstEnd, secondStart, secondEnd)) return false
            }
        }
        return true
    }

    private fun segmentsIntersect(a: LatLng, b: LatLng, c: LatLng, d: LatLng): Boolean {
        val first = orientation(a, b, c)
        val second = orientation(a, b, d)
        val third = orientation(c, d, a)
        val fourth = orientation(c, d, b)
        if (first != second && third != fourth) return true
        return first == 0 && onSegment(a, b, c) ||
            second == 0 && onSegment(a, b, d) ||
            third == 0 && onSegment(c, d, a) ||
            fourth == 0 && onSegment(c, d, b)
    }

    private fun orientation(a: LatLng, b: LatLng, c: LatLng): Int {
        val value = (b.longitude - a.longitude) * (c.latitude - b.latitude) -
            (b.latitude - a.latitude) * (c.longitude - b.longitude)
        return when {
            abs(value) < INTERSECTION_EPSILON -> 0
            value > 0.0 -> 1
            else -> 2
        }
    }

    private fun onSegment(a: LatLng, b: LatLng, point: LatLng): Boolean {
        return point.longitude <= maxOf(a.longitude, b.longitude) &&
            point.longitude >= minOf(a.longitude, b.longitude) &&
            point.latitude <= maxOf(a.latitude, b.latitude) &&
            point.latitude >= minOf(a.latitude, b.latitude)
    }

    private fun updateScreenState() {
        val mapIsVisible = mapLifecycleEnabled && screenState != ScreenState.INVALID
        mapView.visibility = if (mapIsVisible) View.VISIBLE else View.GONE
        mapFallback.visibility = if (mapLifecycleEnabled && screenState == ScreenState.INVALID) View.VISIBLE else View.GONE
        mapUnavailable.visibility = if (!mapLifecycleEnabled) View.VISIBLE else View.GONE

        emptyContent.visibility = if (screenState == ScreenState.EMPTY) View.VISIBLE else View.GONE
        editingContent.visibility = if (screenState == ScreenState.EDITING) View.VISIBLE else View.GONE
        resultContent.visibility = if (screenState == ScreenState.RESULT) View.VISIBLE else View.GONE
        invalidAction.visibility = if (screenState == ScreenState.INVALID) View.VISIBLE else View.GONE
        invalidBack.visibility = if (screenState == ScreenState.INVALID) View.VISIBLE else View.GONE

        pointsCount.text = points.size.toString()
        unitButton.text = if (measurementUnit() == MeasurementUnit.METRIC) {
            getString(R.string.area_measurement_units)
        } else {
            getString(R.string.area_measurement_imperial_units)
        }
        instruction.text = if (points.isEmpty()) {
            getString(R.string.area_measurement_instruction)
        } else {
            getString(R.string.area_measurement_points, points.size)
        }
    }

    private fun updateResultValues() {
        val area = AreaMeasurementCalculator.areaSquareMeters(points)
        areaValue.text = formatArea(area)
        perimeterValue.text = formatDistance(AreaMeasurementCalculator.perimeterMeters(points))
    }

    private fun formatArea(squareMeters: Double): String {
        return if (measurementUnit() == MeasurementUnit.METRIC) {
            if (areaUnit == AreaUnit.HECTARES) {
                String.format(Locale.getDefault(), "%.1f ha", squareMeters / 10_000.0)
            } else {
                String.format(Locale.getDefault(), "%,.0f m²", squareMeters)
            }
        } else if (areaUnit == AreaUnit.HECTARES) {
            String.format(Locale.getDefault(), "%.1f ac", squareMeters / 4_046.8564224)
        } else {
            String.format(Locale.getDefault(), "%,.0f ft²", squareMeters * 10.7639104167)
        }
    }

    private fun formatDistance(meters: Double): String {
        return if (measurementUnit() == MeasurementUnit.IMPERIAL) {
            if (meters >= 1_609.344) {
                String.format(Locale.getDefault(), "%.1f mi", meters / 1_609.344)
            } else {
                String.format(Locale.getDefault(), "%,.0f ft", meters * 3.280839895)
            }
        } else if (meters >= 1_000.0) {
            String.format(Locale.getDefault(), "%.1f km", meters / 1_000.0)
        } else {
            String.format(Locale.getDefault(), "%.0f m", meters)
        }
    }

    private fun measurementUnit(): MeasurementUnit =
        MeasurementUnit.fromKey(settingsPreferences.measurementUnit)

    private fun showInvalidState() {
        screenState = ScreenState.INVALID
        updateScreenState()
    }

    private fun createBoundaryMarkerIcon(): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val size = (18.0 * density).roundToInt()
        val center = size / 2f
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MARKER_FILL_COLOR
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = POLYGON_STROKE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        canvas.drawCircle(center, center, size * 0.34f, fill)
        canvas.drawCircle(center, center, size * 0.34f, stroke)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun centerOnUserLocationIfAvailable() {
        if (hasLocationPermission()) {
            showUserLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun showUserLocation() {
        val map = googleMap ?: return
        if (!hasLocationPermission()) return

        try {
            map.isMyLocationEnabled = true
        } catch (_: SecurityException) {
            return
        }
        locationProvider.findCurrentLocation(
            onSuccess = { location ->
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.latitude, location.longitude),
                        USER_LOCATION_ZOOM
                    )
                )
            },
            onFailure = { }
        )
    }

    private fun hasLocationPermission(): Boolean {
        return locationProvider.hasPermission()
    }

    private fun hasMapsApiKey(): Boolean {
        return try {
            val metadata = packageManager
                .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData
            val apiKey = metadata?.getString("com.google.android.geo.API_KEY")
            !apiKey.isNullOrBlank() && !apiKey.contains("YOUR_MAPS_API_KEY")
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun onStart() {
        super.onStart()
        if (mapLifecycleEnabled) mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (mapLifecycleEnabled) mapView.onResume()
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
        if (mapLifecycleEnabled) mapView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (mapLifecycleEnabled) mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val MINIMUM_POINTS = 3
        const val DEFAULT_ZOOM = 5.5f
        const val USER_LOCATION_ZOOM = 16f
        const val POLYGON_STROKE_COLOR = 0xFFE9A02B.toInt()
        const val POLYGON_FILL_COLOR = 0x44E9A02B
        const val MARKER_FILL_COLOR = 0xFFFFFFFF.toInt()
        const val POLYGON_STROKE_WIDTH = 6f
        const val INTERSECTION_EPSILON = 0.000000001
        val DEFAULT_LOCATION = LatLng(20.5937, 78.9629)
    }
}
