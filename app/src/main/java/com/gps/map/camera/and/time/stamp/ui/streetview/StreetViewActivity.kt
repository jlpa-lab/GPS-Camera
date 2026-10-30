package com.gps.map.camera.and.time.stamp.ui.streetview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StreetViewActivity : AppCompatActivity(), OnStreetViewPanoramaReadyCallback {

    private lateinit var panoramaView: StreetViewPanoramaView
    private lateinit var searchInput: EditText
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchResults: RecyclerView
    private lateinit var placeAdapter: EarthPlaceAdapter
    private lateinit var locationTitle: TextView
    private lateinit var locationDescription: TextView
    private var panorama: StreetViewPanorama? = null
    private var searchJob: Job? = null
    private var selectedPlace: EarthPlace? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_street_view)

        panoramaView = findViewById(R.id.street_view_panorama)
        searchInput = findViewById(R.id.street_view_search_input)
        searchProgress = findViewById(R.id.street_view_search_progress)
        searchResults = findViewById(R.id.street_view_search_results)
        locationTitle = findViewById(R.id.street_view_location_title)
        locationDescription = findViewById(R.id.street_view_location_description)

        searchResults.layoutManager = LinearLayoutManager(this)
        placeAdapter = EarthPlaceAdapter(::showPlace)
        searchResults.adapter = placeAdapter

        findViewById<View>(R.id.street_view_back).setOnClickListener { finish() }
        findViewById<View>(R.id.street_view_search_button).setOnClickListener {
            searchPlace(searchInput.text.toString())
        }
        findViewById<View>(R.id.street_view_map_button).setOnClickListener {
            openSelectedPlaceOnMap()
        }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPlace(searchInput.text.toString())
                true
            } else {
                false
            }
        }

        panoramaView.onCreate(savedInstanceState)
        panoramaView.getStreetViewPanoramaAsync(this)
    }

    override fun onStreetViewPanoramaReady(readyPanorama: StreetViewPanorama) {
        panorama = readyPanorama
        readyPanorama.isUserNavigationEnabled = true
        readyPanorama.isZoomGesturesEnabled = true
        readyPanorama.isPanningGesturesEnabled = true
        readyPanorama.isStreetNamesEnabled = true
        readyPanorama.setPosition(DEFAULT_LOCATION, PANORAMA_SEARCH_RADIUS_METERS)
        searchInput.setText(R.string.street_view_default_search)
        updateLocationCard(DEFAULT_PLACE_NAME, DEFAULT_COUNTRY)
    }

    private fun searchPlace(query: String) {
        val placeQuery = query.trim()
        if (placeQuery.isEmpty()) return

        hideKeyboard()
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
        val country = place.address.substringAfterLast(',').trim()
        updateLocationCard(place.name, country)

        panorama?.setPosition(
            LatLng(place.latitude, place.longitude),
            PANORAMA_SEARCH_RADIUS_METERS
        )
    }

    private fun updateLocationCard(name: String, country: String) {
        locationTitle.text = name
        locationDescription.text = if (country.isNotEmpty()) {
            getString(R.string.street_view_location_description, country)
        } else {
            getString(R.string.street_view_location_description_without_country)
        }
    }

    private fun openSelectedPlaceOnMap() {
        val place = selectedPlace
        val latitude = place?.latitude ?: DEFAULT_LOCATION.latitude
        val longitude = place?.longitude ?: DEFAULT_LOCATION.longitude
        val label = place?.name ?: DEFAULT_PLACE_NAME
        val mapIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
        )
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            Toast.makeText(this, R.string.street_view_map_unavailable, Toast.LENGTH_SHORT).show()
        }
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
        const val DEFAULT_COUNTRY = "Japan"
        const val PANORAMA_SEARCH_RADIUS_METERS = 150
        const val MAX_SEARCH_RESULTS = 5
    }
}
