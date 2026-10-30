package com.gps.map.camera.and.time.stamp.ui.earthview

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.gps.map.camera.and.time.stamp.R
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

    private lateinit var worldWindow: EarthWorldWindow
    private lateinit var searchInput: EditText
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchResults: RecyclerView
    private lateinit var placeAdapter: EarthPlaceAdapter

    private val markerLayer = RenderableLayer("Earth view marker")
    private var searchJob: Job? = null
    private var updatingSearchText = false
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
        searchInput = findViewById(R.id.earth_view_search_input)
        searchProgress = findViewById(R.id.earth_view_search_progress)
        searchResults = findViewById(R.id.earth_view_search_results)

        placeAdapter = EarthPlaceAdapter(::moveToPlace)
        searchResults.layoutManager = LinearLayoutManager(this)
        searchResults.adapter = placeAdapter

        findViewById<View>(R.id.earth_view_back).setOnClickListener { finish() }
        findViewById<View>(R.id.earth_view_search_button).setOnClickListener {
            searchPlace(searchInput.text.toString(), showKeyboard = false)
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
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPlace(searchInput.text.toString(), showKeyboard = false)
                true
            } else {
                false
            }
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                val query = text?.toString()?.trim().orEmpty()
                findViewById<View>(R.id.earth_view_clear_search).visibility =
                    if (query.isEmpty()) View.GONE else View.VISIBLE

                if (updatingSearchText || query.length < MIN_QUERY_LENGTH) {
                    if (query.length < MIN_QUERY_LENGTH) {
                        searchJob?.cancel()
                        placeAdapter.submitList(emptyList())
                        searchResults.visibility = View.GONE
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
    }

    private fun configureGlobe() {
        worldWindow.engine.layers.addLayer(BackgroundLayer())
        worldWindow.engine.layers.addLayer(BlueMarbleLandsatLayer())
        worldWindow.engine.layers.addLayer(AtmosphereLayer())
        worldWindow.engine.layers.addLayer(markerLayer)
        worldWindow.requestRedraw()
    }

    private fun searchPlace(
        query: String,
        showKeyboard: Boolean,
        cancelCurrentJob: Boolean = true
    ) {
        val placeQuery = query.trim()
        if (placeQuery.length < MIN_QUERY_LENGTH) return

        if (!showKeyboard) hideKeyboard()
        if (cancelCurrentJob) searchJob?.cancel()
        searchProgress.visibility = View.VISIBLE
        searchJob = lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { EarthPlaceSearch.search(placeQuery) }
            }
            searchProgress.visibility = View.GONE
            result.onSuccess { places ->
                if (places.isEmpty()) {
                    placeAdapter.submitList(emptyList())
                    searchResults.visibility = View.GONE
                    Toast.makeText(
                        this@EarthViewActivity,
                        getString(R.string.earth_view_place_not_found, placeQuery),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    placeAdapter.submitList(places.take(MAX_SEARCH_RESULTS))
                    searchResults.visibility = View.VISIBLE
                }
            }.onFailure {
                Toast.makeText(
                    this@EarthViewActivity,
                    R.string.earth_view_search_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun moveToPlace(place: EarthPlace) {
        searchResults.visibility = View.GONE
        updatingSearchText = true
        searchInput.setText(place.name)
        searchInput.setSelection(searchInput.text.length)
        updatingSearchText = false
        searchInput.clearFocus()
        hideKeyboard()
        showPlaceOnGlobe(place)
    }

    private fun showPlaceOnGlobe(place: EarthPlace) {
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
        Toast.makeText(this, R.string.earth_view_location_unavailable, Toast.LENGTH_SHORT).show()
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
        worldWindow.onPause()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        worldWindow.engine.renderResourceCache.clear()
    }

    private companion object {
        const val MAX_SEARCH_RESULTS = 5
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 450L
        const val SEARCH_RANGE_METERS = 1_500.0
    }
}
