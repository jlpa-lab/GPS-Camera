package com.gps.map.camera.and.time.stamp.ui.gpscamera

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.pref.AppSharedPreferencesApp
import com.gps.map.camera.and.time.stamp.ui.settings.StampTemplate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** GPS stamp editor opened from the capture preview's Next button. */
class CameraStampEditorActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        private const val LOG_TAG = "GpsCameraEditor"
        private val POSITION_LABELS = arrayOf("Bottom left", "Bottom right", "Top left", "Top right")
        private val POSITION_GRAVITIES = intArrayOf(
            Gravity.BOTTOM or Gravity.START,
            Gravity.BOTTOM or Gravity.END,
            Gravity.TOP or Gravity.START,
            Gravity.TOP or Gravity.END
        )
    }

    private lateinit var sourcePhotoUri: Uri
    private lateinit var photoView: ImageView
    private lateinit var mapView: MapView
    private lateinit var mapCard: FrameLayout
    private lateinit var mapFallback: TextView
    private lateinit var stampPanel: View
    private lateinit var addressText: TextView
    private lateinit var coordinatesText: TextView
    private lateinit var dateText: TextView
    private lateinit var locationClientLabel: TextView
    private val saveExecutor = Executors.newSingleThreadExecutor()
    private val addressExecutor = Executors.newSingleThreadExecutor()

    private var googleMap: GoogleMap? = null
    private var mapEnabled = false
    private var mapCardVisible = true
    private var stampPositionIndex = 0
    private var latitude = Double.NaN
    private var longitude = Double.NaN
    private var capturedAt = ""
    private var address = ""
    private var isSaving = false
    private var deleteSourceAfterSave = true
    private val savedPreferences by lazy { AppSharedPreferencesApp(applicationContext) }
    private val galleryPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { selectedUri ->
        if (selectedUri != null) replacePhoto(selectedUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gps_stamp_editor)

        val photoText = intent.getStringExtra(CameraActivity.EXTRA_PHOTO_URI)
        if (photoText == null) {
            finish()
            return
        }
        sourcePhotoUri = Uri.parse(photoText)
        latitude = intent.getDoubleExtra(CameraActivity.EXTRA_LATITUDE, Double.NaN)
        longitude = intent.getDoubleExtra(CameraActivity.EXTRA_LONGITUDE, Double.NaN)
        capturedAt = intent.getStringExtra(CameraActivity.EXTRA_CAPTURED_AT).orEmpty()

        photoView = findViewById(R.id.editor_photo)
        photoView.setImageURI(sourcePhotoUri)
        mapView = findViewById(R.id.editor_map_view)
        mapCard = findViewById(R.id.editor_map_card)
        mapFallback = findViewById(R.id.editor_map_fallback)
        stampPanel = findViewById(R.id.editor_stamp_panel)
        addressText = findViewById(R.id.editor_address)
        coordinatesText = findViewById(R.id.editor_coordinates)
        dateText = findViewById(R.id.editor_date)
        locationClientLabel = findViewById(R.id.editor_weather)

        dateText.text = capturedAt
        val coordinateLabel = if (latitude.isNaN() || longitude.isNaN()) {
            getString(R.string.location_unavailable)
        } else {
            String.format(Locale.getDefault(), "Lat: %.5f   Long: %.5f", latitude, longitude)
        }
        coordinatesText.text = coordinateLabel
        addressText.text = getString(R.string.address_loading)
        locationClientLabel.text = getString(R.string.weather_unavailable)

        mapEnabled = hasMapsApiKey()
        if (mapEnabled) {
            mapView.onCreate(savedInstanceState)
            mapView.getMapAsync(this)
        } else {
            mapView.visibility = View.GONE
            mapFallback.visibility = View.VISIBLE
            mapFallback.setText(R.string.map_unavailable)
        }

        findViewById<View>(R.id.editor_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.editor_save).setOnClickListener { saveEditedPhoto() }
        findViewById<TextView>(R.id.editor_tab_details).setOnClickListener { toggleDetails() }
        findViewById<TextView>(R.id.editor_tab_position).setOnClickListener { chooseStampPosition() }
        findViewById<TextView>(R.id.editor_tab_map).setOnClickListener { toggleMap() }
        findViewById<TextView>(R.id.editor_tab_location).setOnClickListener { toggleLocationDetails() }
        findViewById<TextView>(R.id.editor_tab_photo).setOnClickListener { galleryPicker.launch("image/*") }
        loadAddress()
        updateStampPosition()
    }

    override fun onStart() {
        super.onStart()
        if (mapEnabled) mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (mapEnabled) mapView.onResume()
    }

    override fun onPause() {
        if (mapEnabled) mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (mapEnabled) mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (mapEnabled) mapView.onLowMemory()
    }

    override fun onDestroy() {
        if (mapEnabled) mapView.onDestroy()
        saveExecutor.shutdown()
        addressExecutor.shutdown()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (mapEnabled) mapView.onSaveInstanceState(outState)
        outState.putInt("stamp_position", stampPositionIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isZoomControlsEnabled = false
        if (!latitude.isNaN() && !longitude.isNaN()) {
            val point = LatLng(latitude, longitude)
            map.addMarker(MarkerOptions().position(point).title(getString(R.string.current_position)))
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 15f))
            mapFallback.visibility = View.GONE
        } else {
            mapFallback.visibility = View.VISIBLE
            mapFallback.setText(R.string.map_waiting_for_location)
        }
    }

    private fun loadAddress() {
        if (latitude.isNaN() || longitude.isNaN()) {
            address = getString(R.string.location_unavailable)
            addressText.text = address
            return
        }
        addressExecutor.execute {
            val resolvedAddress = try {
                if (!Geocoder.isPresent()) null else {
                    @Suppress("DEPRECATION")
                    Geocoder(this, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
                        ?.firstOrNull()?.getAddressLine(0)
                }
            } catch (_: Exception) {
                null
            }
            val displayAddress = resolvedAddress?.takeIf { it.isNotBlank() }
                ?: String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude)
            address = displayAddress
            runOnUiThread {
                addressText.text = displayAddress
            }
        }
    }

    private fun toggleDetails() {
        stampPanel.visibility = if (stampPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        setSelectedTab(R.id.editor_tab_details)
    }

    private fun replacePhoto(uri: Uri) {
        sourcePhotoUri = uri
        deleteSourceAfterSave = false
        photoView.setImageURI(uri)
        // Keep the capture's GPS/time stamp. A selected gallery photo replaces only the image.
        setSelectedTab(R.id.editor_tab_photo)
    }

    private fun chooseStampPosition() {
        AlertDialog.Builder(this)
            .setTitle(R.string.position)
            .setSingleChoiceItems(POSITION_LABELS, stampPositionIndex) { dialog, selected ->
                stampPositionIndex = selected
                updateStampPosition()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateStampPosition() {
        val params = stampPanel.layoutParams as FrameLayout.LayoutParams
        params.gravity = POSITION_GRAVITIES[stampPositionIndex]
        params.width = if (stampPositionIndex < 2) FrameLayout.LayoutParams.MATCH_PARENT else dp(330)
        params.marginStart = dp(14)
        params.marginEnd = dp(14)
        params.bottomMargin = dp(18)
        params.topMargin = dp(182)
        stampPanel.layoutParams = params
    }

    private fun toggleMap() {
        mapCardVisible = !mapCardVisible
        mapCard.visibility = if (mapCardVisible) View.VISIBLE else View.GONE
        mapFallback.visibility = if (!mapEnabled && mapCardVisible) View.VISIBLE else View.GONE
        setSelectedTab(R.id.editor_tab_map)
    }

    private fun toggleLocationDetails() {
        val visible = addressText.visibility != View.VISIBLE
        addressText.visibility = if (visible) View.VISIBLE else View.GONE
        coordinatesText.visibility = if (visible) View.VISIBLE else View.GONE
        setSelectedTab(R.id.editor_tab_location)
    }

    private fun setSelectedTab(selectedId: Int) {
        listOf(R.id.editor_tab_details, R.id.editor_tab_position, R.id.editor_tab_map,
            R.id.editor_tab_location, R.id.editor_tab_photo).forEach { id ->
            findViewById<TextView>(id).setTextColor(if (id == selectedId) 0xFF67A2FF.toInt() else Color.WHITE)
        }
    }

    private fun saveEditedPhoto() {
        if (isSaving) return
        isSaving = true
        findViewById<TextView>(R.id.editor_save).isEnabled = false
        val currentMap = googleMap
        if (!mapCardVisible || currentMap == null) {
            saveExecutor.execute { renderAndSave(null) }
            return
        }

        // Map snapshot callbacks may not arrive on devices without a working Maps service/key.
        // Save the photo without the map if the callback is delayed.
        val callbackUsed = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        val timeout = Runnable {
            if (callbackUsed.compareAndSet(false, true)) {
                saveExecutor.execute { renderAndSave(null) }
            }
        }
        mainHandler.postDelayed(timeout, 1500L)
        currentMap.snapshot { mapBitmap ->
            if (callbackUsed.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeout)
                saveExecutor.execute { renderAndSave(mapBitmap) }
            } else {
                mapBitmap?.recycle()
            }
        }
    }

    private fun renderAndSave(mapBitmap: Bitmap?) {
        var baseBitmap: Bitmap? = null
        var stampedBitmap: Bitmap? = null
        var temporaryOutput: File? = null
        var failureStage = "reading the selected photo"
        try {
            baseBitmap = decodePhotoWithOrientation(sourcePhotoUri) ?: error("Could not read the captured photo")
            failureStage = "drawing the GPS stamp"
            stampedBitmap = drawStamp(baseBitmap, mapBitmap)
            failureStage = "encoding the stamped photo"
            temporaryOutput = File(cacheDir, "gps-stamped-${System.currentTimeMillis()}.jpg")
            FileOutputStream(temporaryOutput).use { output ->
                check(stampedBitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                    "Could not encode the stamped photo"
                }
            }
            failureStage = "writing GPS metadata"
            writeExif(temporaryOutput)
            failureStage = "saving the photo to the gallery"
            val finalUri = insertPhoto(temporaryOutput)
            // Only the app-owned camera capture is disposable; never delete a gallery original.
            if (deleteSourceAfterSave) {
                contentResolver.delete(sourcePhotoUri, null, null)
            }
            val savedPhotoIntent = Intent(this, CameraSavedPhotoActivity::class.java).apply {
                putExtra(CameraActivity.EXTRA_PHOTO_URI, finalUri.toString())
                putExtra(CameraActivity.EXTRA_LATITUDE, latitude)
                putExtra(CameraActivity.EXTRA_LONGITUDE, longitude)
                putExtra(CameraActivity.EXTRA_CAPTURED_AT, capturedAt)
            }
            runOnUiThread {
                startActivity(savedPhotoIntent)
                finish()
            }
        } catch (error: Exception) {
            Log.e(LOG_TAG, "Failed while applying the GPS photo stamp", error)
            runOnUiThread {
                isSaving = false
                findViewById<TextView>(R.id.editor_save).isEnabled = true
                Toast.makeText(this, getString(R.string.photo_save_error_detail, failureStage, error.localizedMessage ?: error.javaClass.simpleName), Toast.LENGTH_LONG).show()
            }
        } finally {
            baseBitmap?.recycle()
            stampedBitmap?.recycle()
            mapBitmap?.recycle()
            temporaryOutput?.delete()
        }
    }

    private fun decodePhotoWithOrientation(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = 1 }
        val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (largestDimension > 0 && largestDimension.toLong() / sampleSize > 3200L && sampleSize < (1 shl 29)) {
            sampleSize *= 2
        }
        decodeOptions.inSampleSize = sampleSize
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null
        check(bitmap.width > 0 && bitmap.height > 0) { "The selected image has invalid dimensions" }
        val orientation = contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        }
        if (matrix.isIdentity) return bitmap
        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (upright !== bitmap) bitmap.recycle()
        return upright
    }

    private fun drawStamp(photo: Bitmap, mapBitmap: Bitmap?): Bitmap {
        val result = photo.copy(Bitmap.Config.ARGB_8888, true) ?: error("Could not create stamp canvas")
        val canvas = Canvas(result)
        val template = StampTemplate.fromKey(savedPreferences.defaultStamp)
        val scale = maxOf(1f, photo.width / 1080f)
        var textSize = when (template) {
            StampTemplate.MINIMAL -> 22f * scale
            StampTemplate.CLASSIC -> 25f * scale
            StampTemplate.DETAILED -> 28f * scale
        }
        val padding = 18f * scale
        if (mapBitmap != null && mapCardVisible) {
            val mapWidth = photo.width * 0.38f
            val mapHeight = mapWidth * 1.05f
            val mapRect = RectF((photo.width - mapWidth) / 2f, photo.height * 0.39f,
                (photo.width + mapWidth) / 2f, photo.height * 0.39f + mapHeight)
            val clip = Path().apply { addRoundRect(mapRect, 28f * scale, 28f * scale, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawBitmap(mapBitmap, null, mapRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        }

        if (stampPanel.visibility == View.VISIBLE) {
            val panelHeight = when (template) {
                StampTemplate.MINIMAL -> photo.height * 0.21f
                StampTemplate.CLASSIC -> photo.height * 0.27f
                StampTemplate.DETAILED -> photo.height * 0.31f
            }
            val panelTop = photo.height - panelHeight
            val panel = RectF(0f, panelTop, photo.width.toFloat(), photo.height.toFloat())
            val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xC9080A10.toInt() }
            canvas.drawRoundRect(panel, 30f * scale, 30f * scale, panelPaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = textSize }
            val purplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8255F7.toInt() }
            val chipHeight = textSize * 2.2f
            val chipWidth = minOf(photo.width * 0.58f, textPaint.measureText(capturedAt) + padding * 2)
            val chipRect = RectF(padding, panelTop + padding, padding + chipWidth, panelTop + padding + chipHeight)
            canvas.drawRoundRect(chipRect, 18f * scale, 18f * scale, purplePaint)
            canvas.drawText(capturedAt, chipRect.left + padding, chipRect.centerY() + textSize * 0.34f, textPaint)

            val addressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = textSize * 0.9f }
            val addressLines = wrapText(address, addressPaint, photo.width * 0.62f).take(
                if (template == StampTemplate.MINIMAL) 1 else 3
            )
            var baseline = chipRect.bottom + textSize * 1.6f
            addressLines.forEach { line ->
                canvas.drawText(line, padding, baseline, addressPaint)
                baseline += textSize * 1.2f
            }
            val coordinateLine = if (latitude.isNaN() || longitude.isNaN()) getString(R.string.location_unavailable)
            else String.format(Locale.getDefault(), "Lat: %.5f   Long: %.5f", latitude, longitude)
            val coordsWidth = minOf(photo.width * 0.8f, addressPaint.measureText(coordinateLine) + padding * 2)
            val coordsRect = RectF(padding, photo.height - textSize * 2.3f, padding + coordsWidth, photo.height - padding * 0.6f)
            canvas.drawRoundRect(coordsRect, 24f * scale, 24f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA4B4C52.toInt() })
            canvas.drawText(coordinateLine, coordsRect.left + padding * 0.7f, coordsRect.centerY() + addressPaint.textSize * 0.32f, addressPaint)
        }
        return result
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        text.split(Regex("\\s+")).forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                result.add(current.toString())
                current = StringBuilder(word)
            } else current = StringBuilder(candidate)
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private fun writeExif(file: File) {
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date()))
        if (!latitude.isNaN() && !longitude.isNaN()) exif.setLatLong(latitude, longitude)
        exif.saveAttributes()
    }

    private fun insertPhoto(file: File): Uri {
        val filename = "GPS_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GPS Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GPS Camera")
                check(folder.exists() || folder.mkdirs())
                put(MediaStore.Images.Media.DATA, File(folder, filename).absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = contentResolver.insert(collection, values) ?: error("Could not create photo entry")
        try {
            val output = contentResolver.openOutputStream(uri, "w") ?: error("Could not open photo output")
            FileInputStream(file).use { input -> output.use { input.copyTo(it) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
        } catch (error: Exception) {
            contentResolver.delete(uri, null, null)
            throw error
        }
        return uri
    }

    private fun hasMapsApiKey(): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val key = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
            !key.isNullOrBlank() && !key.contains("YOUR_MAPS_API_KEY")
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
