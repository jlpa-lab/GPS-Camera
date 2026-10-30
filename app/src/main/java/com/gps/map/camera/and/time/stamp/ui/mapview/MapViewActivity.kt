package com.gps.map.camera.and.time.stamp.ui.mapview

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlace
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlaceAdapter
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlaceSearch
import com.gps.map.camera.and.time.stamp.ui.gallery.PhotoGalleryActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var mapView: MapView
    private lateinit var searchInput: EditText
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchResults: RecyclerView
    private lateinit var placeAdapter: EarthPlaceAdapter
    private lateinit var locationCount: TextView
    private var googleMap: GoogleMap? = null
    private var searchJob: Job? = null
    private var photoJob: Job? = null
    private var searchMarker: Marker? = null
    private var userLocationShown = false
    private val photoMarkers = mutableMapOf<Marker, PhotoLocation>()
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadPhotoLocations() else updateLocationCount(0)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            enableLocationLayer()
            moveToCurrentLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_view)

        mapView = findViewById(R.id.map_view)
        searchInput = findViewById(R.id.map_view_search_input)
        searchProgress = findViewById(R.id.map_view_search_progress)
        searchResults = findViewById(R.id.map_view_search_results)
        locationCount = findViewById(R.id.map_view_location_count)

        searchResults.layoutManager = LinearLayoutManager(this)
        placeAdapter = EarthPlaceAdapter(::moveToPlace)
        searchResults.adapter = placeAdapter

        findViewById<View>(R.id.map_view_back).setOnClickListener { finish() }
        findViewById<View>(R.id.map_view_search_button).setOnClickListener {
            searchPlace(searchInput.text.toString())
        }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPlace(searchInput.text.toString())
                true
            } else {
                false
            }
        }
        findViewById<View>(R.id.map_view_zoom_in).setOnClickListener {
            googleMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        findViewById<View>(R.id.map_view_zoom_out).setOnClickListener {
            googleMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }
        findViewById<View>(R.id.map_view_my_location).setOnClickListener {
            if (hasLocationPermission()) {
                enableLocationLayer()
                moveToCurrentLocation()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
        findViewById<View>(R.id.map_view_gallery_button).setOnClickListener {
            openPhotoGallery()
        }

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        requestMediaPermissionIfNeeded()
    }

    override fun onMapReady(readyMap: GoogleMap) {
        googleMap = readyMap
        readyMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
        readyMap.uiSettings.isZoomControlsEnabled = false
        readyMap.uiSettings.isMapToolbarEnabled = false
        readyMap.uiSettings.isCompassEnabled = false
        readyMap.setOnMarkerClickListener(this)
        requestLocationPermissionIfNeeded()
        loadPhotoLocations()
    }

    private fun requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) {
            enableLocationLayer()
            moveToCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun requestMediaPermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadPhotoLocations()
        } else {
            mediaPermissionLauncher.launch(permission)
        }
    }

    private fun loadPhotoLocations() {
        val map = googleMap ?: return
        photoJob?.cancel()
        photoJob = lifecycleScope.launch {
            val locations = withContext(Dispatchers.IO) { queryPhotoLocations() }
            photoMarkers.keys.toList().forEach { it.remove() }
            photoMarkers.clear()
            locations.forEach { photo -> addPhotoMarker(map, photo) }
            updateLocationCount(locations.size)
            if (locations.isNotEmpty() && !userLocationShown) moveCameraToPhotos(locations)
        }
    }

    private fun queryPhotoLocations(): List<PhotoLocation> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val locations = mutableListOf<PhotoLocation>()
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val coordinates = readCoordinates(uri) ?: continue
                locations += PhotoLocation(
                    uri = uri,
                    name = cursor.getString(nameColumn).orEmpty(),
                    latitude = coordinates[0].toDouble(),
                    longitude = coordinates[1].toDouble()
                )
            }
        }
        return locations
    }

    private fun readCoordinates(uri: Uri): FloatArray? = try {
        contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val coordinates = FloatArray(2)
            val exif = android.media.ExifInterface(descriptor.fileDescriptor)
            if (exif.getLatLong(coordinates)) coordinates else null
        }
    } catch (_: Exception) {
        null
    }

    private fun addPhotoMarker(map: GoogleMap, photo: PhotoLocation) {
        val marker = map.addMarker(
            MarkerOptions()
                .position(LatLng(photo.latitude, photo.longitude))
                .title(photo.name)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        ) ?: return
        photoMarkers[marker] = photo
        lifecycleScope.launch(Dispatchers.IO) {
            val thumbnail = loadThumbnail(photo.uri)
            if (thumbnail != null && !isDestroyed) {
                val markerBitmap = createPhotoMarker(thumbnail)
                withContext(Dispatchers.Main) {
                    marker.setIcon(BitmapDescriptorFactory.fromBitmap(markerBitmap))
                }
            }
        }
    }

    private fun loadThumbnail(uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.loadThumbnail(uri, Size(88, 88), null)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private fun createPhotoMarker(source: Bitmap): Bitmap {
        val size = 112
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = getColor(R.color.home_accent)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 22f, 22f, paint)
        val image = Bitmap.createScaledBitmap(source, 88, 88, true)
        canvas.drawBitmap(image, 12f, 12f, paint)
        return bitmap
    }

    private fun moveCameraToPhotos(locations: List<PhotoLocation>) {
        val map = googleMap ?: return
        if (locations.size == 1) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(locations[0].latitude, locations[0].longitude),
                    PHOTO_ZOOM
                )
            )
            return
        }
        val boundsBuilder = LatLngBounds.Builder()
        locations.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
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
                withContext(Dispatchers.IO) { EarthPlaceSearch.search(placeQuery) }
            }
            searchProgress.visibility = View.GONE
            result.onSuccess { places ->
                if (places.isEmpty()) {
                    Toast.makeText(
                        this@MapViewActivity,
                        getString(R.string.map_view_place_not_found, placeQuery),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    placeAdapter.submitList(places.take(MAX_SEARCH_RESULTS))
                    searchResults.visibility = View.VISIBLE
                }
            }.onFailure {
                Toast.makeText(
                    this@MapViewActivity,
                    R.string.map_view_search_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun moveToPlace(place: EarthPlace) {
        val map = googleMap ?: return
        val position = LatLng(place.latitude, place.longitude)
        searchResults.visibility = View.GONE
        searchInput.setText(place.name)
        searchInput.clearFocus()
        searchMarker?.remove()
        searchMarker = map.addMarker(MarkerOptions().position(position).title(place.name))
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, SEARCH_ZOOM))
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val photo = photoMarkers[marker] ?: return false
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(photo.uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, R.string.map_view_photo_unavailable, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun enableLocationLayer() {
        val map = googleMap ?: return
        if (hasLocationPermission()) {
            try {
                map.isMyLocationEnabled = true
            } catch (_: SecurityException) {
            }
        }
    }

    private fun moveToCurrentLocation() {
        if (!hasLocationPermission()) return
        locationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                userLocationShown = true
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.latitude, location.longitude),
                        CURRENT_LOCATION_ZOOM
                    )
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun updateLocationCount(count: Int) {
        locationCount.text = resources.getQuantityString(
            R.plurals.map_view_saved_places,
            count,
            count
        )
    }

    private fun openPhotoGallery() {
        startActivity(Intent(this, PhotoGalleryActivity::class.java))
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        searchJob?.cancel()
        photoJob?.cancel()
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    private data class PhotoLocation(
        val uri: Uri,
        val name: String,
        val latitude: Double,
        val longitude: Double
    )

    private companion object {
        const val SEARCH_ZOOM = 15f
        const val PHOTO_ZOOM = 14f
        const val CURRENT_LOCATION_ZOOM = 16f
        const val MAX_SEARCH_RESULTS = 5
    }
}
