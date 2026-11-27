package com.gps.map.camera.and.time.stamp.ui.streetview

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import android.widget.Toast
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.google.android.gms.maps.model.RoundCap
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ads.AdsManager
import com.gps.map.camera.and.time.stamp.ads.bindNativeAllAd
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlace
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlaceSearch
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthViewPlaceAdapter
import com.gps.map.camera.and.time.stamp.utils.location.GpsLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Lets the user drop Pegman on a real map before opening Street View. */
class StreetViewActivity : AppCompatActivity(), OnMapReadyCallback,
    OnStreetViewPanoramaReadyCallback {

    private enum class ScreenState { DRAG, RELEASE, PANORAMA, UNAVAILABLE }

    private lateinit var panoramaView: StreetViewPanoramaView
    private lateinit var mapView: MapView
    private lateinit var coverageWebMap: WebView
    private lateinit var previewContainer: FrameLayout
    private lateinit var mapContainer: FrameLayout
    private lateinit var dragHint: View
    private lateinit var hintIcon: ImageView
    private lateinit var hintTitle: TextView
    private lateinit var hintMessage: TextView
    private lateinit var tryAnotherRoad: TextView
    private lateinit var panoramaLocation: View
    private lateinit var panoramaFullscreen: TextView
    private lateinit var panoramaSplitView: TextView
    private lateinit var mapPlace: TextView
    private lateinit var pegman: ImageView
    private lateinit var mapLocationFocus: ImageButton
    private lateinit var currentLocationDot: ImageView
    private lateinit var mapZoomIn: TextView
    private lateinit var mapZoomOut: TextView
    private lateinit var streetSearchIcon: View
    private lateinit var streetSearchOverlay: View
    private lateinit var streetSearchInput: EditText
    private lateinit var streetSearchResults: RecyclerView
    private lateinit var streetPlaceAdapter: EarthViewPlaceAdapter

    private var googleMap: GoogleMap? = null
    private val coverageRoads = mutableListOf<Polyline>()
    private var lastPanoramaLocation: StreetViewPanoramaLocation? = null
    private var selectedMapPoint: LatLng? = null
    private var currentLocationPoint: LatLng? = null
    private var focusLocationPending = false
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
    private var pegmanMarkersVisible = true
    private var locationJob: Job? = null
    private var panoramaJob: Job? = null
    private var metadataJob: Job? = null
    private var panoramaRequest = 0
    private var pendingPanoramaId: String? = null
    private var coverageWebMapReady = false
    private var streetSearchJob: Job? = null
    private var streetSearchDebounceJob: Job? = null
    private var streetSearchMode = false
    private var updatingStreetSearchText = false
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
        AdsManager.loadNativeAll(this, R.layout.layout_native_ad_small)
        bindNativeAllAd(this, findViewById(R.id.native_all_container))
        bindViews()
        AdsManager.loadInterHome(this, ignoreLimit = true)
        AdsManager.loadInterBack(this, ignoreLimit = true)
        selectedPlace = if (
            intent.getBooleanExtra(EXTRA_CURRENT_LOCATION_ONLY, false)
        ) {
            null
        } else {
            readInitialPlace()
        }
        shouldUseCurrentLocation = selectedPlace == null
        bindActions()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AdsManager.showInterHomeBack(this@StreetViewActivity, onAction = { _ ->
                    finish()
                })
            }
        })
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
        coverageWebMap = findViewById(R.id.street_view_coverage_web_map)
        previewContainer = findViewById(R.id.street_view_preview_container)
        mapContainer = findViewById(R.id.street_view_map_container)
        dragHint = findViewById(R.id.street_view_drag_hint)
        hintIcon = findViewById(R.id.street_view_hint_icon)
        hintTitle = findViewById(R.id.street_view_hint_title)
        hintMessage = findViewById(R.id.street_view_hint_message)
        tryAnotherRoad = findViewById(R.id.street_view_try_another_road)
        panoramaLocation = findViewById(R.id.street_view_panorama_location)
        panoramaFullscreen = findViewById(R.id.street_view_panorama_fullscreen)
        panoramaSplitView = findViewById(R.id.street_view_panorama_split_view)
        mapPlace = findViewById(R.id.street_view_map_place)
        pegman = findViewById(R.id.street_view_pegman)
        mapLocationFocus = findViewById(R.id.street_view_my_location)
        currentLocationDot = findViewById(R.id.street_view_current_location_dot)
        mapZoomIn = findViewById(R.id.street_view_map_zoom_in)
        mapZoomOut = findViewById(R.id.street_view_map_zoom_out)
        streetSearchIcon = findViewById(R.id.street_view_search_icon)
        streetSearchOverlay = findViewById(R.id.street_view_search_overlay)
        streetSearchInput = findViewById(R.id.street_view_search_input)
        streetSearchResults = findViewById(R.id.street_view_search_results)
        streetPlaceAdapter = EarthViewPlaceAdapter(::selectSearchPlace)
        streetSearchResults.layoutManager = LinearLayoutManager(this)
        streetSearchResults.adapter = streetPlaceAdapter

        // The toggle is an ordinary Android control. Remove any theme-provided
        // ripple/elevation surface so it cannot add a white halo below the icon.
        pegman.background = null
        pegman.foreground = null
        pegman.stateListAnimator = null
        pegman.elevation = 0f

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
        protectMapGestures(coverageWebMap)
        protectMapGestures(panoramaView)
        setupCoverageWebMap()
    }

    private fun setupCoverageWebMap() {
        val apiKey = mapsApiKey() ?: return
        coverageWebMapReady = false
        coverageWebMap.settings.javaScriptEnabled = true
        coverageWebMap.settings.domStorageEnabled = true
        coverageWebMap.settings.allowFileAccess = false
        coverageWebMap.addJavascriptInterface(CoverageMapBridge(), "Android")
        coverageWebMap.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Keep the native map visible until the JavaScript API confirms that
                // the coverage layer was created. This preserves the native fallback
                // when the key/network does not allow the Maps JS API to load.
            }
        }
        coverageWebMap.loadDataWithBaseURL(
            "https://maps.google.com",
            coverageMapHtml(apiKey),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun coverageMapHtml(apiKey: String): String {
        val point = selectedPlace?.let { "${it.latitude},${it.longitude}" } ?: "0,0"
        val latitudeText = point.substringBefore(',')
        val longitudeText = point.substringAfter(',')
        return """
            <!doctype html><html><head>
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
              <style>
                html,body,#map{width:100%;height:100%;margin:0;padding:0;background:#dfe8e4}
    .gm-svpc,
    .gm-bundled-control,
    .gm-bundled-control > div{display:none !important;visibility:hidden !important;opacity:0 !important;pointer-events:none !important}
              </style>
            </head><body><div id="map"></div>
            <script>
              let coverageMap;
              let coverageLayer;
              let panoramaMarker;
              let gpsMarker;
              let coverageVisible = true;
              const pegmanIconUrl = 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(
                '<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40">' +
                '<circle cx="20" cy="20" r="19.5" fill="#FFD46B" stroke="#FFF2C6"/>' +
                '<circle cx="20" cy="12.5" r="2.6" fill="#172638"/>' +
                '<path d="M15.1 22v-3.2c0-1.8 1.5-3.1 3.2-3.1h3.4c1.7 0 3.2 1.3 3.2 3.1V22h-2.2v6.2c0 .6-.4 1-1 1h-3.4c-.6 0-1-.4-1-1V22h-2.2Z" fill="#172638"/>' +
                '</svg>'
              );
              const gpsIconUrl = 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(
                '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20">' +
                '<circle cx="10" cy="10" r="8.5" fill="#4285F4" stroke="#FFFFFF" stroke-width="3"/>' +
                '</svg>'
              );
              async function initStreetCoverage() {
                const maps = await google.maps.importLibrary("maps");
                const streetView = await google.maps.importLibrary("streetView");
                coverageMap = new maps.Map(document.getElementById("map"), {
                  center: {lat: $latitudeText, lng: $longitudeText}, zoom: 16,
                  clickableIcons: false, streetViewControl: false,
                  fullscreenControl: false, mapTypeControl: false, zoomControl: false,
                  styles: [{featureType:"poi",stylers:[{visibility:"off"}]}]
                });
                coverageLayer = new streetView.StreetViewCoverageLayer();
                coverageLayer.setMap(coverageMap);
    hideGoogleStreetViewControl();
                panoramaMarker = new google.maps.Marker({
                  map: coverageMap,
                  visible: false,
                  zIndex: 20,
                  icon: {
                    url: pegmanIconUrl,
                    scaledSize: new google.maps.Size(40, 40),
                    anchor: new google.maps.Point(20, 40)
                  }
                });
                gpsMarker = new google.maps.Marker({
                  map: coverageMap,
                  visible: false,
                  zIndex: 30,
                  icon: {
                    url: gpsIconUrl,
                    scaledSize: new google.maps.Size(20, 20),
                    anchor: new google.maps.Point(10, 10)
                  }
                });
                coverageMap.addListener("click", function(event) {
                  if (event.latLng && window.Android) {
                    window.Android.onMapClicked(event.latLng.lat(), event.latLng.lng());
                  }
                });
                if (window.Android) window.Android.onMapReady();
              }
  function hideGoogleStreetViewControl() {
    document.querySelectorAll('.gm-svpc').forEach(function(control) {
      const wrapper = control.closest('.gm-bundled-control') || control.parentElement;
      if (wrapper) {
        wrapper.style.setProperty('display', 'none', 'important');
        wrapper.style.setProperty('visibility', 'hidden', 'important');
        wrapper.style.setProperty('pointer-events', 'none', 'important');
      }
    });
  }
              function moveCoverageMap(lat, lng) {
                if (coverageMap) coverageMap.panTo({lat:lat,lng:lng});
              }
              function setGpsLocation(lat, lng) {
                if (!gpsMarker) return;
                gpsMarker.setPosition({lat:lat,lng:lng});
                gpsMarker.setMap(coverageMap);
                gpsMarker.setVisible(true);
              }
              function setPanoramaMarker(lat, lng) {
                if (!panoramaMarker) return;
                const position = {lat:lat,lng:lng};
                panoramaMarker.setPosition(position);
                panoramaMarker.setMap(coverageVisible ? coverageMap : null);
                panoramaMarker.setVisible(coverageVisible);
                if (coverageMap && (!coverageMap.getBounds() || !coverageMap.getBounds().contains(position))) {
                  coverageMap.panTo(position);
                }
              }
              function setCoverageVisible(visible) {
                coverageVisible = visible;
                if (coverageLayer) coverageLayer.setMap(visible ? coverageMap : null);
                if (panoramaMarker) {
                  panoramaMarker.setMap(visible ? coverageMap : null);
                  panoramaMarker.setVisible(visible);
                }
              }
              function zoomCoverageMap(delta) {
                if (coverageMap) coverageMap.setZoom(coverageMap.getZoom() + delta);
              }
              function resizeCoverageMap() {
                if (!coverageMap) return;
                google.maps.event.trigger(coverageMap, "resize");
                hideGoogleStreetViewControl();
              }
            </script>
            <script async defer src="https://maps.googleapis.com/maps/api/js?key=$apiKey&callback=initStreetCoverage"></script>
            </body></html>
        """.trimIndent()
    }

    private fun mapsApiKey(): String? = runCatching {
        packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun initialCoveragePoint(): LatLng? = selectedMapPoint
        ?: selectedPlace?.let { LatLng(it.latitude, it.longitude) }
        ?: googleMap?.cameraPosition?.target

    private fun syncCoverageWebMap(point: LatLng) {
        if (!coverageWebMapReady) return
        coverageWebMap.post {
            coverageWebMap.evaluateJavascript(
                "moveCoverageMap(${point.latitude},${point.longitude})",
                null
            )
        }
    }

    private fun syncCoverageWebMapGps(point: LatLng) {
        if (!coverageWebMapReady) return
        coverageWebMap.post {
            coverageWebMap.evaluateJavascript(
                "setGpsLocation(${point.latitude},${point.longitude})",
                null
            )
        }
    }

    private fun syncCoverageWebMapPanorama(point: LatLng) {
        if (!coverageWebMapReady) return
        coverageWebMap.post {
            coverageWebMap.evaluateJavascript(
                "setPanoramaMarker(${point.latitude},${point.longitude})",
                null
            )
        }
    }

    private inner class CoverageMapBridge {
        @JavascriptInterface
        fun onMapReady() {
            runOnUiThread {
                coverageWebMapReady = true
                coverageWebMap.visibility = View.VISIBLE
                mapView.visibility = View.GONE
                currentLocationDot.visibility = View.GONE
                initialCoveragePoint()?.let(::syncCoverageWebMap)
                currentLocationPoint?.let(::syncCoverageWebMapGps)
                lastPanoramaLocation?.position?.let(::syncCoverageWebMapPanorama)
                coverageWebMap.evaluateJavascript(
                    "resizeCoverageMap(); setCoverageVisible(${pegmanMarkersVisible})",
                    null
                )
            }
        }

        @JavascriptInterface
        fun onMapClicked(latitude: Double, longitude: Double) {
            runOnUiThread { selectMapPoint(LatLng(latitude, longitude)) }
        }
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
            if (streetSearchMode) {
                hideStreetSearch()
            } else if (state == ScreenState.PANORAMA) {
                finish()
            } else if (mapFullscreen) {
                toggleMapFullscreen()
            } else {
                finish()
            }
        }
        findViewById<ImageButton>(R.id.street_view_map_fullscreen)
            .setOnClickListener { toggleMapFullscreen() }
        mapLocationFocus.setOnClickListener { focusMapOnCurrentLocation() }
        panoramaFullscreen.setOnClickListener { togglePanoramaFullscreen() }
        panoramaSplitView.setOnClickListener { togglePanoramaFullscreen() }
        tryAnotherRoad.setOnClickListener { openStreetView() }
        streetSearchIcon.setOnClickListener { startStreetSearch() }
        findViewById<View>(R.id.street_view_search_button).setOnClickListener {
            searchStreetPlaces(streetSearchInput.text.toString())
        }
        findViewById<View>(R.id.street_view_clear_search).setOnClickListener {
            streetSearchInput.text?.clear()
        }
        streetSearchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showStreetSearch()
        }
        streetSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchStreetPlaces(streetSearchInput.text.toString())
                true
            } else {
                false
            }
        }
        streetSearchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                val query = text?.toString()?.trim().orEmpty()
                findViewById<View>(R.id.street_view_clear_search).visibility =
                    if (query.isEmpty()) View.GONE else View.VISIBLE
                if (updatingStreetSearchText || query.length < MIN_STREET_SEARCH_QUERY_LENGTH) {
                    streetSearchDebounceJob?.cancel()
                    if (query.isEmpty()) streetSearchResults.visibility = View.GONE
                    return
                }
                streetSearchDebounceJob?.cancel()
                streetSearchDebounceJob = lifecycleScope.launch {
                    delay(STREET_SEARCH_DEBOUNCE_MS)
                    searchStreetPlaces(query)
                }
            }

            override fun afterTextChanged(text: Editable?) = Unit
        })
        pegman.setOnClickListener { togglePegmanMarkers() }
        mapZoomIn.setOnClickListener { zoomMap(1f) }
        mapZoomOut.setOnClickListener { zoomMap(-1f) }
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
        // Use the custom focus control above Pegman while retaining the native
        // blue current-location dot on the map.
        map.uiSettings.isMyLocationButtonEnabled = false
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.setOnMapClickListener(::selectMapPoint)
        map.setOnPolylineClickListener { road ->
            // Blue coverage roads are real interaction targets, not decoration.
            // Resolve a point close to the linked panorama. Keeping the visual
            // segment short prevents its end from landing outside the lookup
            // radius on curved streets.
            road.points.lastOrNull()?.let(::selectMapPoint)
        }
        map.setOnCameraIdleListener {
            currentLocationPoint?.let(::positionCurrentLocationDot)
            positionMapPegmanBottomRight()
        }
        map.setOnMapLoadedCallback {
            currentLocationPoint?.let(::positionCurrentLocationDot)
            positionMapPegmanBottomRight()
            lastPanoramaLocation?.let(::drawCoverageRoad)
        }
        enableMyLocationLayer()
        selectedPlace?.let { drawPlace(it) }
        if (selectedPlace == null) {
            resetPegmanToCorner()
            lastPanoramaLocation?.let(::drawCoverageRoad)
        }
        initialCoveragePoint()?.let(::syncCoverageWebMap)
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
            lastPanoramaLocation = location
            if (location == null) {
                // The SDK can emit a transient null while it changes from the
                // previous panorama to the newly requested one. Do not turn
                // that intermediate callback into a false "no coverage"
                // result; requestPanorama() owns the timeout and final state.
                panoramaHasLocation = false
            } else {
                panoramaHasLocation = true
                pendingPanoramaId = null
                panoramaJob?.cancel()
                drawCoverageRoad(location)
                showPreviewState()
            }
        }
        // The preview is the default state. If the panorama callback arrived
        // after the place was loaded, request that place now instead of waiting
        // for a Pegman drag.
        pendingPanoramaId?.let {
            readyPanorama.setPosition(it)
        } ?: selectedPlace?.let {
            requestPanorama(LatLng(it.latitude, it.longitude))
        } ?: showPreviewState()
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

    private fun startStreetSearch() {
        showStreetSearch()
        streetSearchInput.requestFocus()
        streetSearchInput.post {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(streetSearchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showStreetSearch() {
        streetSearchMode = true
        streetSearchOverlay.visibility = View.VISIBLE
        streetSearchIcon.visibility = View.GONE
        findViewById<View>(R.id.street_view_title).visibility = View.VISIBLE
        updateStreetContentPosition()
    }

    private fun hideStreetSearch() {
        streetSearchMode = false
        streetSearchJob?.cancel()
        streetSearchDebounceJob?.cancel()
        streetSearchResults.visibility = View.GONE
        streetSearchOverlay.visibility = View.GONE
        streetSearchIcon.visibility = View.VISIBLE
        findViewById<View>(R.id.street_view_title).visibility = View.VISIBLE
        updateStreetContentPosition()
        streetSearchInput.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(streetSearchInput.windowToken, 0)
    }

    private fun updateStreetContentPosition() {
        val contentScroll = findViewById<View>(R.id.street_view_content_scroll)
        val params = contentScroll.layoutParams as? ConstraintLayout.LayoutParams ?: return
        params.topToTop = ConstraintLayout.LayoutParams.UNSET
        params.topToBottom = if (streetSearchMode) {
            R.id.street_view_search_overlay
        } else {
            R.id.street_view_header
        }
        params.topMargin = 0
        contentScroll.layoutParams = params
    }

    private fun searchStreetPlaces(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.length < MIN_STREET_SEARCH_QUERY_LENGTH) return
        streetSearchDebounceJob?.cancel()
        streetSearchJob?.cancel()
        showStreetSearch()
        streetSearchResults.visibility = View.GONE
        streetSearchJob = lifecycleScope.launch {
            try {
                val places = withContext(Dispatchers.IO) {
                    EarthPlaceSearch.search(query)
                }
                if (!isActive) return@launch
                streetPlaceAdapter.submitList(places)
                streetSearchResults.visibility = if (places.isEmpty()) View.GONE else View.VISIBLE
                if (places.isEmpty()) {
                    Toast.makeText(
                        this@StreetViewActivity,
                        R.string.earth_view_no_places_message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (isActive) {
                    Toast.makeText(
                        this@StreetViewActivity,
                        R.string.earth_view_map_failed_message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun selectSearchPlace(place: EarthPlace) {
        updatingStreetSearchText = true
        streetSearchInput.setText(place.name)
        streetSearchInput.setSelection(streetSearchInput.text.length)
        updatingStreetSearchText = false
        selectedPlace = place
        updatePlace(place)
        hideStreetSearch()
    }

    private fun showPreviewState() {
        state = ScreenState.PANORAMA
        val hasPreview = selectedPlace != null &&
            panorama != null && panoramaHasLocation
        dragHint.visibility = if (hasPreview) View.GONE else View.VISIBLE
        // Keep the live panorama surface attached while it is loading. The
        // loading hint remains over it until a panorama callback succeeds.
        panoramaView.visibility = if (selectedPlace != null) View.VISIBLE else View.GONE
        panoramaLocation.visibility = if (hasPreview) View.VISIBLE else View.GONE
        panoramaFullscreen.visibility = if (hasPreview) View.VISIBLE else View.GONE
        panoramaSplitView.visibility = if (panoramaFullscreenMode && hasPreview) View.VISIBLE else View.GONE
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

    private fun openStreetView(
        point: LatLng? = null,
        exactMapSelection: Boolean = false
    ) {
        val place = selectedPlace
        if (place == null) {
            showUnavailable()
            return
        }
        requestPanorama(
            point ?: LatLng(place.latitude, place.longitude),
            if (exactMapSelection) MAP_CLICK_SEARCH_RADIUS_METERS else PANORAMA_SEARCH_RADIUS_METERS
        )
    }

    private fun showUnavailable() {
        state = ScreenState.UNAVAILABLE
        panoramaHasLocation = false
        panoramaJob?.cancel()
        dragHint.visibility = View.VISIBLE
        panoramaView.visibility = View.GONE
        currentLocationDot.visibility = View.GONE
        panoramaLocation.visibility = View.GONE
        panoramaFullscreen.visibility = View.GONE
        panoramaSplitView.visibility = View.GONE
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
        val point = LatLng(place.latitude, place.longitude)
        googleMap?.let { map ->
            map.clear()
            clearCoverageRoads()
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 16f))
            selectedMapPoint = point
            positionMapPegmanBottomRight()
        }
        syncCoverageWebMap(point)
        // Resolve the nearest panorama through the same request/timeout path
        // used by map taps. This also covers the case where the location fix
        // arrives after the panorama view has already initialized.
        requestPanorama(point, PANORAMA_SEARCH_RADIUS_METERS)
    }

    private fun selectMapPoint(point: LatLng) {
        Log.d(TAG, "selectMapPoint ${point.latitude},${point.longitude}")
        manualPlaceSelected = true
        shouldUseCurrentLocation = false
        val current = selectedPlace ?: EarthPlace(
            getString(R.string.earth_view_current_location),
            "",
            point.latitude,
            point.longitude
        )
        selectedPlace = current.copy(latitude = point.latitude, longitude = point.longitude)
        selectedMapPoint = point
        syncCoverageWebMap(point)
        mapPlace.text = formatMapPlace(selectedPlace!!)
        clearCoverageRoads()
        positionMapPegmanBottomRight()
        // A normal map tap is the quick path: resolve the nearest panorama and
        // show it immediately. Pegman remains available for another road.
        openStreetView(point, exactMapSelection = true)
    }

    private fun positionMapPegmanBottomRight() {
        // This Pegman is the persistent toggle control. It never represents a
        // geographic point and must stay anchored to the map corner.
        pegman.post {
            if (mapContainer.width == 0 || mapContainer.height == 0) return@post
            pegman.x = (mapContainer.width - pegman.width - dp(14)).coerceAtLeast(0).toFloat()
            pegman.y = (mapContainer.height - pegman.height - dp(14)).coerceAtLeast(0).toFloat()
            pegman.translationX = 0f
            pegman.translationY = 0f
            pegman.visibility = View.VISIBLE
            pegman.bringToFront()
        }
    }

    private fun positionCurrentLocationDot(point: LatLng) {
        if (coverageWebMapReady) {
            currentLocationDot.visibility = View.GONE
            return
        }
        val map = googleMap ?: return
        currentLocationDot.post {
            if (mapContainer.width == 0 || mapContainer.height == 0) return@post
            val screenPoint = map.projection.toScreenLocation(point)
            val mapLocation = IntArray(2)
            val containerLocation = IntArray(2)
            mapView.getLocationOnScreen(mapLocation)
            mapContainer.getLocationOnScreen(containerLocation)
            val containerX = mapLocation[0] - containerLocation[0] + screenPoint.x
            val containerY = mapLocation[1] - containerLocation[1] + screenPoint.y
            val dotX = containerX - currentLocationDot.width / 2f
            val dotY = containerY - currentLocationDot.height / 2f
            currentLocationDot.x = dotX
                .coerceIn(0f, (mapContainer.width - currentLocationDot.width).toFloat())
            currentLocationDot.y = dotY
                .coerceIn(0f, (mapContainer.height - currentLocationDot.height).toFloat())
            currentLocationDot.visibility = if (
                dotX + currentLocationDot.width >= 0f &&
                    dotX <= mapContainer.width &&
                    dotY + currentLocationDot.height >= 0f &&
                    dotY <= mapContainer.height
            ) View.VISIBLE else View.GONE
            pegman.bringToFront()
        }
    }

    private fun resetPegmanToCorner() {
        pegman.post {
            pegman.x = (mapContainer.width - pegman.width - dp(20)).coerceAtLeast(0).toFloat()
            pegman.y = (mapContainer.height - pegman.height - dp(20)).coerceAtLeast(0).toFloat()
            pegman.translationX = 0f
            pegman.translationY = 0f
            pegman.visibility = View.VISIBLE
            pegman.bringToFront()
        }
    }

    private fun togglePegmanMarkers() {
        pegmanMarkersVisible = !pegmanMarkersVisible
        if (pegmanMarkersVisible) {
            pegman.clearColorFilter()
        } else {
            pegman.setColorFilter(Color.GRAY)
        }
        coverageWebMap.evaluateJavascript(
            "setCoverageVisible(${pegmanMarkersVisible})",
            null
        )
    }

    private fun focusMapOnCurrentLocation() {
        val point = currentLocationPoint
        if (point == null) {
            focusLocationPending = true
            findCurrentPlace()
            return
        }
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, MAP_PANORAMA_ZOOM))
        selectedMapPoint = point
        positionCurrentLocationDot(point)
        syncCoverageWebMap(point)
        syncCoverageWebMapGps(point)
    }

    private fun drawCoverageRoad(location: StreetViewPanoramaLocation) {
        val center = location.position ?: return
        selectedMapPoint = center
        syncCoverageWebMapPanorama(center)
        val map = googleMap ?: return
        clearCoverageRoads()
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(center, MAP_PANORAMA_ZOOM)
        )
        syncCoverageWebMap(center)
        positionMapPegmanBottomRight()
        val links = location.links?.toList() ?: emptyList()
        if (links.isEmpty()) return
        links.forEach { link ->
            coverageRoads += map.addPolyline(
                PolylineOptions()
                    .add(
                        center,
                        pointAlongBearing(center, link.bearing, COVERAGE_ROAD_LENGTH_METERS)
                    )
                    .color(getColor(R.color.home_accent))
                    .width(8f)
                    .geodesic(true)
                    .startCap(RoundCap())
                    .endCap(RoundCap())
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
        if (coverageWebMapReady) {
            coverageWebMap.evaluateJavascript("zoomCoverageMap($amount)", null)
        }
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
                        currentLocationPoint = LatLng(location.latitude, location.longitude)
                        positionCurrentLocationDot(currentLocationPoint!!)
                        syncCoverageWebMapGps(currentLocationPoint!!)
                        if (focusLocationPending) {
                            focusLocationPending = false
                            focusMapOnCurrentLocation()
                        }
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

    private fun requestPanorama(point: LatLng, radiusMeters: Int = PANORAMA_SEARCH_RADIUS_METERS) {
        Log.d(TAG, "requestPanorama ${point.latitude},${point.longitude} radius=$radiusMeters")
        val request = ++panoramaRequest
        panoramaJob?.cancel()
        metadataJob?.cancel()
        panoramaHasLocation = false
        pendingPanoramaId = null
        showPreviewState()
        // Use the default source so Street View can return any nearby 360°
        // panorama, including indoor imagery and photo spheres.
        panorama?.setPosition(point, radiusMeters)

        // The Android panorama SDK can take a long time to resolve a LatLng,
        // especially after a map tap. Resolve the same point through Google's
        // Street View metadata endpoint as a fallback, then hand the returned
        // pano id back to the SDK. This makes map taps and Pegman drops behave
        // like the Google Maps flow instead of getting stuck on the hint card.
        metadataJob = lifecycleScope.launch {
            delay(STREET_VIEW_METADATA_FALLBACK_DELAY_MS)
            if (request != panoramaRequest || panoramaHasLocation) return@launch
            val metadata = withContext(Dispatchers.IO) { findStreetViewMetadata(point, radiusMeters) }
            Log.d(TAG, "metadata result=${metadata != null} request=$request current=$panoramaRequest")
            if (request == panoramaRequest && !panoramaHasLocation && metadata != null) {
                pendingPanoramaId = metadata.panoId
                selectedMapPoint = metadata.position
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(metadata.position, MAP_PANORAMA_ZOOM)
                )
                syncCoverageWebMapPanorama(metadata.position)
                positionMapPegmanBottomRight()
                panorama?.setPosition(metadata.panoId)
            }
        }

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

    private fun findStreetViewMetadata(point: LatLng, radiusMeters: Int): StreetViewMetadata? {
        val apiKey = runCatching {
            packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
                .metaData?.getString("com.google.android.geo.API_KEY")
        }.getOrNull()?.takeIf { !it.isNullOrBlank() } ?: run {
            Log.w(TAG, "Google Maps API key missing for Street View metadata")
            return null
        }
        val endpoint = URL(
            "https://maps.googleapis.com/maps/api/streetview/metadata" +
                "?location=${point.latitude},${point.longitude}" +
                "&radius=$radiusMeters&key=$apiKey"
        )
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = STREET_VIEW_METADATA_TIMEOUT_MS.toInt()
            readTimeout = STREET_VIEW_METADATA_TIMEOUT_MS.toInt()
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Street View metadata HTTP ${connection.responseCode}")
                return null
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (json.optString("status") != "OK") {
                Log.w(TAG, "Street View metadata status=${json.optString("status")}")
                return null
            }
            val location = json.optJSONObject("location") ?: return null
            val panoId = json.optString("pano_id").takeIf { it.isNotBlank() } ?: return null
            StreetViewMetadata(
                panoId,
                LatLng(location.optDouble("lat"), location.optDouble("lng"))
            )
        } catch (exception: Exception) {
            Log.w(TAG, "Street View metadata request failed", exception)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun toggleMapFullscreen() {
        mapFullscreen = !mapFullscreen

        previewContainer.visibility = if (mapFullscreen) View.GONE else View.VISIBLE
        findViewById<View>(R.id.street_view_divider).visibility = if (mapFullscreen) View.GONE else View.VISIBLE
        findViewById<View>(R.id.street_view_map_title).visibility = if (mapFullscreen) View.GONE else View.VISIBLE
        findViewById<View>(R.id.street_view_section_header).visibility =
            if (mapFullscreen) View.GONE else View.VISIBLE
        mapPlace.visibility = if (mapFullscreen) View.GONE else View.VISIBLE

        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.street_view_content_scroll)
        val contentLayout = findViewById<LinearLayout>(R.id.street_view_content)
        val mapParams = mapContainer.layoutParams as LinearLayout.LayoutParams

        if (mapFullscreen) {
            scrollView?.isNestedScrollingEnabled = false
            scrollView?.scrollTo(0, 0)

            val contentParams = contentLayout.layoutParams
            contentParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            contentLayout.layoutParams = contentParams

            mapParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            mapParams.weight = 0f
            mapParams.setMargins(0, 0, 0, 0)
            mapContainer.layoutParams = mapParams
        } else {
            restoreStreetViewSplitLayout()
            return
        }

        mapContainer.layoutParams = mapParams
        mapContainer.requestLayout()
    }

    private fun togglePanoramaFullscreen() {
        panoramaFullscreenMode = !panoramaFullscreenMode
        panoramaSplitView.visibility = if (panoramaFullscreenMode) View.VISIBLE else View.GONE
        findViewById<View>(R.id.street_view_map_title).visibility = if (panoramaFullscreenMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.street_view_section_header).visibility =
            if (panoramaFullscreenMode) View.GONE else View.VISIBLE
        mapPlace.visibility = if (panoramaFullscreenMode) View.GONE else View.VISIBLE
        mapContainer.visibility = if (panoramaFullscreenMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.street_view_divider).visibility = if (panoramaFullscreenMode) View.GONE else View.VISIBLE

        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.street_view_content_scroll)
        val contentLayout = findViewById<LinearLayout>(R.id.street_view_content)
        val previewParams = previewContainer.layoutParams as LinearLayout.LayoutParams

        if (panoramaFullscreenMode) {
            // Disable scroll and jump to top
            scrollView?.isNestedScrollingEnabled = false
            scrollView?.scrollTo(0, 0)

            // Expand content container to match parent
            val contentParams = contentLayout.layoutParams
            contentParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            contentLayout.layoutParams = contentParams

            // Expand panorama preview frame to fill full screen
            previewParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            previewParams.weight = 0f
            previewParams.setMargins(0, 0, 0, 0)
        } else {
            restoreStreetViewSplitLayout()
            return
        }

        previewContainer.layoutParams = previewParams
        previewContainer.requestLayout()
    }

    private fun restoreStreetViewSplitLayout() {
        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.street_view_content_scroll)
        val contentLayout = findViewById<LinearLayout>(R.id.street_view_content)
        val sideMarginPx = dp(20)

        scrollView.isNestedScrollingEnabled = true
        scrollView.scrollTo(0, 0)

        val contentParams = contentLayout.layoutParams
        contentParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        contentLayout.layoutParams = contentParams
        contentLayout.weightSum = 10f

        val previewParams = previewContainer.layoutParams as LinearLayout.LayoutParams
        previewParams.height = 0
        previewParams.weight = 5f
        previewParams.setMargins(sideMarginPx, dp(20), sideMarginPx, 0)
        previewContainer.layoutParams = previewParams

        val mapParams = mapContainer.layoutParams as LinearLayout.LayoutParams
        mapParams.height = 0
        mapParams.weight = 5f
        mapParams.setMargins(sideMarginPx, dp(16), sideMarginPx, 0)
        mapContainer.layoutParams = mapParams

        contentLayout.post {
            val weightedHeight = previewContainer.height + mapContainer.height
            if (weightedHeight <= 0) return@post
            val eachHeight = weightedHeight / 2
            val restoredPreviewParams = previewContainer.layoutParams as LinearLayout.LayoutParams
            restoredPreviewParams.height = eachHeight
            restoredPreviewParams.weight = 0f
            previewContainer.layoutParams = restoredPreviewParams

            val restoredMapParams = mapContainer.layoutParams as LinearLayout.LayoutParams
            restoredMapParams.height = weightedHeight - eachHeight
            restoredMapParams.weight = 0f
            mapContainer.layoutParams = restoredMapParams
            contentLayout.requestLayout()
        }
        contentLayout.requestLayout()
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
        metadataJob?.cancel()
        locationProvider.stopLocationUpdates()
        mapView.onDestroy()
        panoramaView.onDestroy()
        coverageWebMap.destroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
        panoramaView.onLowMemory()
    }

    companion object {
        private const val TAG = "StreetViewActivity"
        private const val NORMAL_PREVIEW_HEIGHT_DP = 280
        private const val NORMAL_MAP_HEIGHT_DP = 270
        private const val FULLSCREEN_PANEL_HEIGHT_DP = 680
        private const val MAP_PANORAMA_ZOOM = 16f
        const val PANORAMA_SEARCH_RADIUS_METERS = 500
        const val MAP_CLICK_SEARCH_RADIUS_METERS = 500
        private const val COVERAGE_ROAD_LENGTH_METERS = 220.0
        private const val STREET_VIEW_METADATA_FALLBACK_DELAY_MS = 1_500L
        private const val STREET_VIEW_METADATA_TIMEOUT_MS = 5_000L
        private const val MIN_STREET_SEARCH_QUERY_LENGTH = 2
        private const val STREET_SEARCH_DEBOUNCE_MS = 350L
        const val PANORAMA_LOAD_TIMEOUT_MS = 12_000L
        const val PANORAMA_ZOOM_ANIMATION_MS = 220L
        const val EXTRA_CURRENT_LOCATION_ONLY = "street_view_current_location_only"
        const val EXTRA_PLACE_NAME = "earth_view_place_name"
        const val EXTRA_PLACE_ADDRESS = "earth_view_place_address"
        const val EXTRA_PLACE_LATITUDE = "earth_view_place_latitude"
        const val EXTRA_PLACE_LONGITUDE = "earth_view_place_longitude"
    }

    private data class StreetViewMetadata(
        val panoId: String,
        val position: LatLng
    )
}
