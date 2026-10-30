package com.gps.map.camera.and.time.stamp.ui.gallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gps.map.camera.and.time.stamp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoGalleryActivity : AppCompatActivity() {

    private lateinit var photosGrid: RecyclerView
    private lateinit var dateLabel: TextView
    private lateinit var countLabel: TextView
    private lateinit var allPhotosTab: TextView
    private lateinit var mapPhotosTab: TextView
    private lateinit var adapter: PhotoGalleryAdapter
    private var allPhotos = emptyList<PhotoGalleryPhoto>()
    private var showingMapPhotos = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadPhotos() else showEmptyGallery()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_gallery)

        photosGrid = findViewById(R.id.photo_gallery_grid)
        dateLabel = findViewById(R.id.photo_gallery_date)
        countLabel = findViewById(R.id.photo_gallery_count)
        allPhotosTab = findViewById(R.id.photo_gallery_all_tab)
        mapPhotosTab = findViewById(R.id.photo_gallery_map_tab)

        adapter = PhotoGalleryAdapter(::openPhoto)
        photosGrid.layoutManager = GridLayoutManager(this, GRID_SPAN_COUNT)
        photosGrid.adapter = adapter

        findViewById<View>(R.id.photo_gallery_back).setOnClickListener { finish() }
        allPhotosTab.setOnClickListener { showAllPhotos() }
        mapPhotosTab.setOnClickListener { showMapPhotos() }
        findViewById<TextView>(R.id.photo_gallery_take_photo).setOnClickListener {
            Toast.makeText(this, R.string.coming_soon_title, Toast.LENGTH_SHORT).show()
        }

        if (hasMediaPermission()) {
            loadPhotos()
        } else {
            permissionLauncher.launch(mediaPermission())
        }
    }

    private fun loadPhotos() {
        lifecycleScope.launch {
            allPhotos = withContext(Dispatchers.IO) { queryPhotos() }
            showAllPhotos()
        }
    }

    private fun queryPhotos(): List<PhotoGalleryPhoto> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val photos = mutableListOf<PhotoGalleryPhoto>()
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                photos += PhotoGalleryPhoto(
                    uri = uri,
                    dateAdded = cursor.getLong(dateColumn),
                    hasLocation = hasExifLocation(uri)
                )
            }
        }
        return photos
    }

    private fun hasExifLocation(uri: android.net.Uri): Boolean = try {
        contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val coordinates = FloatArray(2)
            android.media.ExifInterface(descriptor.fileDescriptor).getLatLong(coordinates)
        } ?: false
    } catch (_: Exception) {
        false
    }

    private fun showAllPhotos() {
        showingMapPhotos = false
        updateTabs()
        showPhotos(allPhotos)
    }

    private fun showMapPhotos() {
        showingMapPhotos = true
        updateTabs()
        showPhotos(allPhotos.filter { it.hasLocation })
    }

    private fun showPhotos(photos: List<PhotoGalleryPhoto>) {
        adapter.submitList(photos)
        if (photos.isEmpty()) {
            dateLabel.setText(R.string.photo_gallery_no_photos)
        } else {
            dateLabel.text = formatDate(photos.first().dateAdded)
        }
        val locationCount = allPhotos.count { it.hasLocation }
        countLabel.text = if (locationCount > 0) {
            getString(R.string.photo_gallery_count_with_location, photos.size)
        } else {
            getString(R.string.photo_gallery_count_without_location, photos.size)
        }
    }

    private fun showEmptyGallery() {
        allPhotos = emptyList()
        showAllPhotos()
    }

    private fun updateTabs() {
        allPhotosTab.isSelected = !showingMapPhotos
        mapPhotosTab.isSelected = showingMapPhotos
        allPhotosTab.setTextColor(
            getColor(if (allPhotosTab.isSelected) R.color.home_accent else R.color.secondary_text_color)
        )
        mapPhotosTab.setTextColor(
            getColor(if (mapPhotosTab.isSelected) R.color.home_accent else R.color.secondary_text_color)
        )
    }

    private fun formatDate(seconds: Long): String {
        val date = Date(seconds * 1000)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val photoDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(date)
        val day = SimpleDateFormat("d MMMM", Locale.getDefault()).format(date)
        return if (today == photoDay) {
            getString(R.string.photo_gallery_today, day)
        } else {
            SimpleDateFormat("EEEE • d MMMM", Locale.getDefault()).format(date)
        }
    }

    private fun openPhoto(photo: PhotoGalleryPhoto) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(photo.uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, R.string.map_view_photo_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mediaPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun hasMediaPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, mediaPermission()) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val GRID_SPAN_COUNT = 3
    }
}
