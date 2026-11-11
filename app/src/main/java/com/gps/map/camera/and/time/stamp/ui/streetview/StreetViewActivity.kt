package com.gps.map.camera.and.time.stamp.ui.streetview

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
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

    private enum class StreetViewState {
        STREET_VIEW_FAILED,
        LOCATION_UNAVAILABLE
    }

    private lateinit var panoramaView: StreetViewPanoramaView
    private lateinit var panoramaContainer: FrameLayout
    private lateinit var searchInput: EditText
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchResults: RecyclerView
    private lateinit var placeAdapter: EarthPlaceAdapter
    private lateinit var defaultControls: View
    private lateinit var exploreEarthButton: View
    private lateinit var backToMapButton: View
    private lateinit var contentScroll: View
    private lateinit var statePanel: View
    private lateinit var stateIcon: android.widget.ImageView
    private lateinit var stateTitle: TextView
    private lateinit var stateMessage: TextView
    private lateinit var statePrimaryButton: TextView
    private lateinit var stateSecondaryButton: TextView
    private lateinit var locationTitle: TextView
    private lateinit var locationDescription: TextView
    private lateinit var locationMeta: TextView
    private var panorama: StreetViewPanorama? = null
    private var searchJob: Job? = null
    private var selectedPlace: EarthPlace? = null
    private var streetViewState: StreetViewState? = null
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

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
        contentScroll = findViewById(R.id.street_view_content_scroll)
        statePanel = findViewById(R.id.street_view_state_panel)
        stateIcon = findViewById(R.id.earth_view_state_icon)
        stateTitle = findViewById(R.id.earth_view_state_title)
        stateMessage = findViewById(R.id.earth_view_state_message)
        statePrimaryButton = findViewById(R.id.earth_view_state_primary_button)
        stateSecondaryButton = findViewById(R.id.earth_view_state_secondary_button)
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
        statePrimaryButton.setOnClickListener { retryStreetView() }
        stateSecondaryButton.setOnClickListener { chooseAnotherPlace() }
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
        readyPanorama.setOnStreetViewPanoramaChangeListener { location ->
            if (location == null) {
                showStreetViewState(StreetViewState.STREET_VIEW_FAILED)
            } else {
                hideStreetViewState()
            }
        }
        readyPanorama.isUserNavigationEnabled = true
        readyPanorama.isZoomGesturesEnabled = true
        readyPanorama.isPanningGesturesEnabled = true
        readyPanorama.isStreetNamesEnabled = true
        val initialPlace = selectedPlace
        if (initialPlace == null) {
            loadDefaultStreetView()
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
                    updateStreetViewMode(false)
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
        val compactMode = resultMode || searchResults.visibility == View.VISIBLE
        val layoutParams = panoramaContainer.layoutParams
        layoutParams.height = dp(
            if (compactMode) RESULT_PANORAMA_HEIGHT_DP else DEFAULT_PANORAMA_HEIGHT_DP
        )
        panoramaContainer.layoutParams = layoutParams
        defaultControls.visibility = if (compactMode) View.GONE else View.VISIBLE
        exploreEarthButton.visibility = if (compactMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.street_view_location_card).visibility =
            if (resultMode) View.VISIBLE else View.GONE
        backToMapButton.visibility = if (resultMode) View.VISIBLE else View.GONE
    }

    private fun showStreetViewState(state: StreetViewState) {
        streetViewState = state
        contentScroll.visibility = View.GONE
        statePanel.visibility = View.VISIBLE
        stateIcon.setImageResource(
            when (state) {
                StreetViewState.STREET_VIEW_FAILED -> R.drawable.ic_earth_map_error
                StreetViewState.LOCATION_UNAVAILABLE -> R.drawable.ic_my_location
            }
        )
        stateTitle.setText(
            when (state) {
                StreetViewState.STREET_VIEW_FAILED -> R.string.street_view_load_failed_title
                StreetViewState.LOCATION_UNAVAILABLE -> R.string.street_view_location_unavailable_title
            }
        )
        stateMessage.setText(
            when (state) {
                StreetViewState.STREET_VIEW_FAILED -> R.string.street_view_load_failed_message
                StreetViewState.LOCATION_UNAVAILABLE -> R.string.street_view_location_unavailable_message
            }
        )
        statePrimaryButton.setText(
            when (state) {
                StreetViewState.STREET_VIEW_FAILED -> R.string.street_view_retry
                StreetViewState.LOCATION_UNAVAILABLE -> R.string.street_view_try_again
            }
        )
        stateSecondaryButton.setText(
            when (state) {
                StreetViewState.STREET_VIEW_FAILED -> R.string.street_view_choose_another_place
                StreetViewState.LOCATION_UNAVAILABLE -> R.string.street_view_search_manually
            }
        )
        statePrimaryButton.background = getDrawable(R.drawable.bg_street_view_button)
        stateSecondaryButton.background = getDrawable(
            if (state == StreetViewState.LOCATION_UNAVAILABLE) {
                R.drawable.bg_earth_place_card
            } else {
                R.drawable.bg_earth_street_view_button
            }
        )
        statePrimaryButton.setTextColor(getColor(R.color.white))
        stateSecondaryButton.setTextColor(getColor(R.color.primary_text_color))
    }

    private fun hideStreetViewState() {
        streetViewState = null
        statePanel.visibility = View.GONE
        contentScroll.visibility = View.VISIBLE
    }

    private fun retryStreetView() {
        val state = streetViewState
        hideStreetViewState()
        if (state == StreetViewState.LOCATION_UNAVAILABLE) {
            loadDefaultStreetView()
            return
        }
        val place = selectedPlace
        if (place != null) {
            updateLocationCard(place.name, formatAddress(place))
            updateStreetViewMode(true)
            panorama?.setPosition(
                LatLng(place.latitude, place.longitude),
                PANORAMA_SEARCH_RADIUS_METERS
            )
        } else {
            loadDefaultStreetView()
        }
    }

    private fun chooseAnotherPlace() {
        selectedPlace = null
        searchInput.text?.clear()
        searchResults.visibility = View.GONE
        hideStreetViewState()
        updateStreetViewMode(false)
        searchInput.requestFocus()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun loadDefaultStreetView() {
        val readyPanorama = panorama ?: return
        selectedPlace = null
        searchInput.text?.clear()
        updateLocationCard(DEFAULT_PLACE_NAME, DEFAULT_ADDRESS)
        updateStreetViewMode(false)

        if (!hasLocationPermission()) {
            showStreetViewState(StreetViewState.LOCATION_UNAVAILABLE)
            return
        }
        locationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && selectedPlace == null && panorama === readyPanorama) {
                readyPanorama.setPosition(
                    LatLng(location.latitude, location.longitude),
                    PANORAMA_SEARCH_RADIUS_METERS
                )
            } else if (location == null) {
                showStreetViewState(StreetViewState.LOCATION_UNAVAILABLE)
            }
        }.addOnFailureListener {
            showStreetViewState(StreetViewState.LOCATION_UNAVAILABLE)
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun resetDefaultStreetView() {
        searchInput.clearFocus()
        searchResults.visibility = View.GONE
        loadDefaultStreetView()
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
        const val DEFAULT_PANORAMA_HEIGHT_DP = 540
        const val RESULT_PANORAMA_HEIGHT_DP = 425
        const val MAX_SEARCH_RESULTS = 5
        const val EXTRA_PLACE_NAME = "earth_view_place_name"
        const val EXTRA_PLACE_ADDRESS = "earth_view_place_address"
        const val EXTRA_PLACE_LATITUDE = "earth_view_place_latitude"
        const val EXTRA_PLACE_LONGITUDE = "earth_view_place_longitude"
    }
}
