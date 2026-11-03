package com.gps.map.camera.and.time.stamp.ui.streetview

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.OnStreetViewPanoramaReadyCallback
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.StreetViewPanoramaView
import com.google.android.gms.maps.model.LatLng
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlace
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlaceAdapter
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlaceSearch
import com.gps.map.camera.and.time.stamp.utils.Routes
import android.text.Editable
import android.text.TextWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StreetViewActivity : AppCompatActivity(), OnStreetViewPanoramaReadyCallback {

    private lateinit var panoramaView: StreetViewPanoramaView
    private lateinit var panoramaContainer: FrameLayout
    private lateinit var searchInput: EditText
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchResults: RecyclerView
    private lateinit var placeAdapter: EarthPlaceAdapter
    private lateinit var defaultControls: View
    private lateinit var exploreEarthButton: View
    private lateinit var backToMapButton: View
    private lateinit var locationTitle: TextView
    private lateinit var locationDescription: TextView
    private lateinit var locationMeta: TextView
    private var panorama: StreetViewPanorama? = null
    private var searchJob: Job? = null
    private var selectedPlace: EarthPlace? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_street_view)

        panoramaView = findViewById(R.id.street_view_panorama)
        panoramaContainer = findViewById(R.id.street_view_panorama_container)
        searchInput = findViewById(R.id.street_view_search_input)
        searchProgress = findViewById(R.id.street_view_search_progress)
        searchResults = findViewById(R.id.street_view_search_results)
        defaultControls = findViewById(R.id.street_view_default_controls)
        exploreEarthButton = findViewById(R.id.street_view_explore_earth_button)
        backToMapButton = findViewById(R.id.street_view_back_to_map)
        locationTitle = findViewById(R.id.street_view_location_title)
        locationDescription = findViewById(R.id.street_view_location_description)
        locationMeta = findViewById(R.id.street_view_location_meta)
        selectedPlace = readInitialPlace()
        panoramaContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
        panoramaContainer.clipToOutline = true
        updateStreetViewMode(selectedPlace != null)

        searchResults.layoutManager = LinearLayoutManager(this)
        placeAdapter = EarthPlaceAdapter(::showPlace)
        searchResults.adapter = placeAdapter

        findViewById<View>(R.id.street_view_back).setOnClickListener { finish() }
        findViewById<View>(R.id.street_view_search_button).setOnClickListener {
            searchPlace(searchInput.text.toString())
        }
        findViewById<View>(R.id.street_view_earth_button).setOnClickListener {
            Routes.startEarthViewActivity(this)
        }
        findViewById<View>(R.id.street_view_reset_button).setOnClickListener {
            resetDefaultStreetView()
        }
        exploreEarthButton.setOnClickListener {
            Routes.startEarthViewActivity(this)
        }
        backToMapButton.setOnClickListener {
            finish()
        }
        findViewById<ImageButton>(R.id.street_view_clear_search).setOnClickListener {
            searchInput.text?.clear()
            searchInput.clearFocus()
            hideKeyboard()
        }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPlace(searchInput.text.toString())
                true
            } else {
                false
            }
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                findViewById<View>(R.id.street_view_clear_search).visibility =
                    if (text?.isNotBlank() == true) View.VISIBLE else View.GONE
            }

            override fun afterTextChanged(text: Editable?) = Unit
        })

        panoramaView.onCreate(savedInstanceState)
        panoramaView.getStreetViewPanoramaAsync(this)
    }

    override fun onStreetViewPanoramaReady(readyPanorama: StreetViewPanorama) {
        panorama = readyPanorama
        readyPanorama.isUserNavigationEnabled = true
        readyPanorama.isZoomGesturesEnabled = true
        readyPanorama.isPanningGesturesEnabled = true
        readyPanorama.isStreetNamesEnabled = true
        val initialPlace = selectedPlace
        if (initialPlace == null) {
            readyPanorama.setPosition(DEFAULT_LOCATION, PANORAMA_SEARCH_RADIUS_METERS)
            searchInput.text?.clear()
            updateLocationCard(DEFAULT_PLACE_NAME, DEFAULT_ADDRESS)
            updateStreetViewMode(false)
        } else {
            readyPanorama.setPosition(
                LatLng(initialPlace.latitude, initialPlace.longitude),
                PANORAMA_SEARCH_RADIUS_METERS
            )
            searchInput.setText(initialPlace.name)
            updateLocationCard(
                initialPlace.name,
                formatAddress(initialPlace)
            )
            updateStreetViewMode(true)
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

    private fun searchPlace(query: String) {
        val placeQuery = query.trim()
        if (placeQuery.isEmpty()) return

        hideKeyboard()
        selectedPlace = null
        updateStreetViewMode(false)
        searchJob?.cancel()
        searchResults.visibility = View.GONE
        searchProgress.visibility = View.VISIBLE

        searchJob = lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    EarthPlaceSearch.search(placeQuery)
                }
            }
            searchProgress.visibility = View.GONE
            result.onSuccess { places ->
                if (places.isEmpty()) {
                    Toast.makeText(
                        this@StreetViewActivity,
                        getString(R.string.street_view_place_not_found, placeQuery),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    placeAdapter.submitList(places.take(MAX_SEARCH_RESULTS))
                    searchResults.visibility = View.VISIBLE
                }
            }.onFailure {
                Toast.makeText(
                    this@StreetViewActivity,
                    R.string.street_view_search_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showPlace(place: EarthPlace) {
        selectedPlace = place
        searchResults.visibility = View.GONE
        searchInput.setText(place.name)
        searchInput.clearFocus()
        updateLocationCard(place.name, formatAddress(place))
        updateStreetViewMode(true)

        panorama?.setPosition(
            LatLng(place.latitude, place.longitude),
            PANORAMA_SEARCH_RADIUS_METERS
        )
    }

    private fun updateLocationCard(name: String, address: String) {
        locationTitle.text = name
        locationDescription.text = address
        locationMeta.setText(R.string.street_view_location_meta)
    }

    private fun updateStreetViewMode(resultMode: Boolean) {
        val layoutParams = panoramaContainer.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.dimensionRatio = if (resultMode) "0.75:1" else "0.59:1"
        panoramaContainer.layoutParams = layoutParams
        defaultControls.visibility = if (resultMode) View.GONE else View.VISIBLE
        exploreEarthButton.visibility = if (resultMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.street_view_location_card).visibility =
            if (resultMode) View.VISIBLE else View.GONE
        backToMapButton.visibility = if (resultMode) View.VISIBLE else View.GONE
    }

    private fun resetDefaultStreetView() {
        selectedPlace = null
        searchInput.text?.clear()
        searchInput.clearFocus()
        searchResults.visibility = View.GONE
        updateLocationCard(DEFAULT_PLACE_NAME, DEFAULT_ADDRESS)
        panorama?.setPosition(DEFAULT_LOCATION, PANORAMA_SEARCH_RADIUS_METERS)
        updateStreetViewMode(false)
    }

    private fun formatAddress(place: EarthPlace): String {
        val parts = place.address.split(',').map(String::trim).filter(String::isNotEmpty)
        return if (parts.size >= 2) parts.takeLast(2).joinToString(", ") else place.address
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        panoramaView.onResume()
    }

    override fun onPause() {
        panoramaView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        searchJob?.cancel()
        panoramaView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        panoramaView.onLowMemory()
    }

    private companion object {
        val DEFAULT_LOCATION = LatLng(35.0037, 135.7788)
        const val DEFAULT_PLACE_NAME = "Higashiyama, Kyoto"
        const val DEFAULT_ADDRESS = "Kyoto, Japan"
        const val PANORAMA_SEARCH_RADIUS_METERS = 150
        const val MAX_SEARCH_RESULTS = 5
        const val EXTRA_PLACE_NAME = "earth_view_place_name"
        const val EXTRA_PLACE_ADDRESS = "earth_view_place_address"
        const val EXTRA_PLACE_LATITUDE = "earth_view_place_latitude"
        const val EXTRA_PLACE_LONGITUDE = "earth_view_place_longitude"
    }
}
