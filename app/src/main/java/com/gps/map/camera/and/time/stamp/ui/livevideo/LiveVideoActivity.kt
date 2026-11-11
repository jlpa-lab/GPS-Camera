package com.gps.map.camera.and.time.stamp.ui.livevideo

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gps.map.camera.and.time.stamp.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.json.JSONArray
import java.util.Locale

/** Searchable map of the live-video camera locations from the decompiled export dataset. */
class LiveVideoActivity : AppCompatActivity(), OnMapReadyCallback {
    private data class LiveVideoPin(
        val id: Int,
        val name: String,
        val youtubeId: String,
        val latitude: Double,
        val longitude: Double
    )

    private lateinit var mapView: MapView
    private lateinit var searchInput: EditText
    private lateinit var resultsContainer: LinearLayout
    private lateinit var resultsPanel: View
    private lateinit var emptyState: TextView
    private var googleMap: GoogleMap? = null
    private var allPins: List<LiveVideoPin> = emptyList()
    private var dataLoadFailed = false
    private val markers = mutableMapOf<Marker, LiveVideoPin>()
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_video)

        mapView = findViewById(R.id.live_video_map)
        searchInput = findViewById(R.id.live_video_search)
        resultsPanel = findViewById(R.id.live_video_results_panel)
        resultsContainer = findViewById(R.id.live_video_results)
        emptyState = findViewById(R.id.live_video_empty_state)

        findViewById<View>(R.id.live_video_back).setOnClickListener { finish() }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                renderSearchResults()
                renderMapMarkers()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        loadPinsFromExportAsset()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isZoomControlsEnabled = true
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 2.2f))
        map.setOnMarkerClickListener { marker ->
            markers[marker]?.let { openVideo(it) }
            true
        }
        renderMapMarkers()
    }

    private fun loadPinsFromExportAsset() {
        Thread {
            val result = runCatching {
                val jsonText = assets.open("live_data_video.json").bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonText)
                buildList {
                    for (index in 0 until jsonArray.length()) {
                        val item = jsonArray.optJSONObject(index) ?: continue
                        val name = item.optString("name").trim()
                        val videoId = item.optString("linkVideo").trim()
                        val latitude = item.optDouble("latitude", Double.NaN)
                        val longitude = item.optDouble("longitude", Double.NaN)
                        if (name.isNotEmpty() && videoId.isNotEmpty() &&
                            latitude.isFinite() && longitude.isFinite() &&
                            latitude in -90.0..90.0 && longitude in -180.0..180.0
                        ) {
                            add(LiveVideoPin(item.optInt("id"), name, videoId, latitude, longitude))
                        }
                    }
                }
            }
            val pins = result.getOrDefault(emptyList())

            runOnUiThread {
                allPins = pins
                dataLoadFailed = result.isFailure || pins.isEmpty()
                findViewById<TextView>(R.id.live_video_loading).visibility = View.GONE
                renderSearchResults()
                renderMapMarkers()
            }
        }.start()
    }

    private fun matchingPins(): List<LiveVideoPin> {
        if (searchQuery.isBlank()) return emptyList()
        val query = searchQuery.lowercase(Locale.getDefault())
        return allPins.filter { it.name.lowercase(Locale.getDefault()).contains(query) }
    }

    private fun renderSearchResults() {
        resultsContainer.removeAllViews()
        val matches = matchingPins()
        resultsPanel.visibility = if (searchQuery.isNotBlank() || dataLoadFailed) View.VISIBLE else View.GONE
        if (dataLoadFailed) {
            emptyState.setText(R.string.live_video_data_unavailable)
            emptyState.visibility = View.VISIBLE
            return
        }
        if (searchQuery.isNotBlank() && matches.isEmpty()) {
            emptyState.setText(R.string.live_video_no_results)
            emptyState.visibility = View.VISIBLE
            return
        }
        emptyState.visibility = View.GONE
        matches.forEach { pin ->
            val row = layoutInflater.inflate(R.layout.row_live_video_search_result, resultsContainer, false)
            row.findViewById<TextView>(R.id.live_video_result_name).text = pin.name
            row.findViewById<TextView>(R.id.live_video_result_coordinates).text =
                String.format(Locale.getDefault(), "%.4f, %.4f", pin.latitude, pin.longitude)
            row.setOnClickListener { openVideo(pin) }
            resultsContainer.addView(row)
        }
    }

    private fun renderMapMarkers() {
        val map = googleMap ?: return
        markers.keys.toList().forEach { it.remove() }
        markers.clear()
        val visiblePins = if (searchQuery.isBlank()) allPins else matchingPins()
        visiblePins.forEach { pin ->
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(pin.latitude, pin.longitude))
                    .title(pin.name)
                    .snippet(getString(R.string.tap_marker_to_watch))
            )
            if (marker != null) markers[marker] = pin
        }
    }

    private fun openVideo(pin: LiveVideoPin) {
        startActivity(Intent(this, YoutubeVideoActivity::class.java).apply {
            putExtra(YoutubeVideoActivity.EXTRA_VIDEO_ID, pin.youtubeId)
            putExtra(YoutubeVideoActivity.EXTRA_VIDEO_TITLE, pin.name)
            putExtra(YoutubeVideoActivity.EXTRA_PIN_ID, pin.id)
        })
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { mapView.onSaveInstanceState(outState); super.onSaveInstanceState(outState) }
}
