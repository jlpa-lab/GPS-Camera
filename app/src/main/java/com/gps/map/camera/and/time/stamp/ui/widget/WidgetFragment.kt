package com.gps.map.camera.and.time.stamp.ui.widget

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.utils.Routes
import com.gps.map.camera.and.time.stamp.utils.location.GpsLocationProvider

class WidgetFragment : Fragment(R.layout.fragment_widget), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var mapFallback: ImageView
    private lateinit var currentLocationMarker: View
    private var googleMap: GoogleMap? = null
    private var pendingLocation: LatLng? = null
    private var requestedPermission = false
    private val locationProvider by lazy { GpsLocationProvider(requireContext()) }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            enableMyLocationLayer()
            loadCurrentLocation()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.widget_live_map)
        mapFallback = view.findViewById(R.id.widget_map_fallback)
        currentLocationMarker = view.findViewById(R.id.widget_current_location_marker)
        currentLocationMarker.visibility = View.GONE

        val baseTopPadding = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            target.setPadding(
                target.paddingLeft,
                baseTopPadding + statusTop + dp(12),
                target.paddingRight,
                target.paddingBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        view.findViewById<View>(R.id.widget_live_gps_card).setOnClickListener {
            Routes.startMapViewActivity(requireActivity())
        }
        view.findViewById<View>(R.id.widget_world_card).setOnClickListener {
            Routes.startEarthViewActivity(requireActivity())
        }
        view.findViewById<View>(R.id.widget_trending_card).setOnClickListener {
            Routes.startEarthViewActivity(requireActivity())
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapFallback.visibility = View.GONE
        map.mapType = GoogleMap.MAP_TYPE_SATELLITE
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isScrollGesturesEnabled = false
        map.uiSettings.isZoomGesturesEnabled = false
        map.uiSettings.isRotateGesturesEnabled = false
        map.uiSettings.isTiltGesturesEnabled = false
        map.setOnMapClickListener {
            Routes.startMapViewActivity(requireActivity())
        }

        enableMyLocationLayer()
        pendingLocation?.let(::centerMap)
        loadCurrentLocation()
    }

    private fun enableMyLocationLayer() {
        val map = googleMap ?: return
        if (!locationProvider.hasPermission()) return
        try {
            map.isMyLocationEnabled = true
        } catch (_: SecurityException) {
            // The permission may change while this fragment is being resumed.
        }
    }

    private fun loadCurrentLocation() {
        if (!locationProvider.hasPermission()) {
            if (!requestedPermission) {
                requestedPermission = true
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            return
        }

        locationProvider.findCurrentLocation(
            onSuccess = { location ->
                if (!isAdded) return@findCurrentLocation
                val point = LatLng(location.latitude, location.longitude)
                pendingLocation = point
                currentLocationMarker.visibility = View.VISIBLE
                centerMap(point)
            },
            onFailure = {
                // The satellite preview remains useful even when GPS is off.
            }
        )
    }

    private fun centerMap(point: LatLng) {
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16f))
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
    }

    override fun onPause() {
        if (::mapView.isInitialized) mapView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        googleMap = null
        if (::mapView.isInitialized) mapView.onDestroy()
        super.onDestroyView()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::mapView.isInitialized) mapView.onLowMemory()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
