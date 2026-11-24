package com.gps.map.camera.and.time.stamp.ui.camera

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.graphics.drawable.ColorDrawable
import android.location.Geocoder
import android.util.Log
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.gps.map.camera.and.time.stamp.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.gps.map.camera.and.time.stamp.ui.gpscamera.CameraPhotoPreviewActivity
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.OpenMeteoWeather
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampCustomization
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampCustomizationStore
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampOverlayView
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampRenderData
import com.gps.map.camera.and.time.stamp.utils.location.LocationDisabledDialog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** GPS capture screen. Location is optional; camera permission is required. */
class CameraActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PHOTO_URI = "com.gps.map.camera.and.time.stamp.extra.PHOTO_URI"
        const val EXTRA_LATITUDE = "com.gps.map.camera.and.time.stamp.extra.LATITUDE"
        const val EXTRA_LONGITUDE = "com.gps.map.camera.and.time.stamp.extra.LONGITUDE"
        const val EXTRA_CAPTURED_AT = "com.gps.map.camera.and.time.stamp.extra.CAPTURED_AT"
        const val EXTRA_DELETE_SOURCE_AFTER_SAVE = "com.gps.map.camera.and.time.stamp.extra.DELETE_SOURCE_AFTER_SAVE"
        const val EXTRA_TOOLS_CAMERA = "com.gps.map.camera.and.time.stamp.extra.TOOLS_CAMERA"

        private const val STATE_LENS_FACING = "lens_facing"
        private const val LOCATION_PERMISSION_REQUESTED = "location_permission_requested"
        private const val CAMERA_PERMISSION_REQUESTED = "camera_permission_requested"
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        private const val FINE_LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION
        private const val COARSE_LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION
        private const val STORAGE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE
        private const val LOCATION_INTERVAL_MILLIS = 5_000L
        private const val LOCATION_DISTANCE_METERS = 5f
        private const val LOG_TAG = "GpsCameraCapture"
    }

    private lateinit var cameraPreview: PreviewView
    private lateinit var cameraTitle: TextView
    private lateinit var cameraUnavailablePanel: View
    private lateinit var cameraUnavailableActions: View
    private lateinit var locationStamp: TextView
    private lateinit var flashButton: ImageButton
    private lateinit var captureButton: ImageButton
    private lateinit var templateOverlay: StampOverlayView
    private lateinit var previewFrame: android.widget.FrameLayout
    private lateinit var cameraControls: View
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var imageExecutor: ExecutorService

    private var imageCapture: ImageCapture? = null
    private var activeCamera: Camera? = null
    private var latestLocation: android.location.Location? = null
    private var locationAddress = "Location unavailable"
    private var weatherText: String? = null
    private var stampCustomization: StampCustomization? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var captureInProgress = false
    private var locationPermissionRequestedInThisInstance = false
    private var cameraStartInProgress = false
    private var storagePermissionRequestAttempted = false
    private var permissionRequestInProgress = false
    private var cameraPermissionSheet: Dialog? = null
    private var cameraPermissionSwitch: SwitchCompat? = null
    private val toolsCameraMode: Boolean
        get() = intent.getBooleanExtra(EXTRA_TOOLS_CAMERA, false)

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionRequestInProgress = false
        if (!hasPermission(CAMERA_PERMISSION)) {
            cameraPermissionSwitch?.isChecked = false
            if (getPreferences(MODE_PRIVATE).getBoolean(CAMERA_PERMISSION_REQUESTED, false) &&
                !shouldShowRequestPermissionRationale(CAMERA_PERMISSION)
            ) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.camera_permission_title)
                    .setMessage(R.string.camera_permission_settings)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.open_settings) { _, _ -> openAppSettings() }
                    .show()
            }
            return@registerForActivityResult
        }

        cameraPermissionSheet?.dismiss()
        cameraPermissionSheet = null
        if (!hasRequiredStoragePermission()) {
            if (storagePermissionRequestAttempted) {
                showRequiredPermissionDialog()
            } else {
                requestLegacyStoragePermission()
            }
            return@registerForActivityResult
        }

        startCamera()
        if (hasLocationPermission()) startLocationUpdates()
        else requestLocationPermissionIfAppropriate()
    }

    private val galleryPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { selectedUri ->
        selectedUri ?: return@registerForActivityResult
        val capturedAt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
        startActivity(Intent(this, CameraPhotoPreviewActivity::class.java).apply {
            putExtra(EXTRA_PHOTO_URI, selectedUri.toString())
            putExtra(EXTRA_LATITUDE, latestLocation?.latitude ?: Double.NaN)
            putExtra(EXTRA_LONGITUDE, latestLocation?.longitude ?: Double.NaN)
            putExtra(EXTRA_CAPTURED_AT, capturedAt)
            putExtra(EXTRA_DELETE_SOURCE_AFTER_SAVE, false)
            putExtra(EXTRA_TOOLS_CAMERA, toolsCameraMode)
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(if (toolsCameraMode) R.layout.activity_tools_camera else R.layout.activity_camera)

        lensFacing = savedInstanceState?.getInt(STATE_LENS_FACING, CameraSelector.LENS_FACING_BACK)
            ?: CameraSelector.LENS_FACING_BACK
        cameraSelector = selectorFor(lensFacing)

        cameraPreview = findViewById(R.id.camera_preview)
        cameraTitle = findViewById(R.id.camera_title)
        cameraUnavailablePanel = findViewById(R.id.camera_unavailable_panel)
        cameraUnavailableActions = findViewById(R.id.camera_unavailable_actions)
        previewFrame = findViewById(R.id.camera_preview_frame)
        locationStamp = findViewById(R.id.location_stamp)
        flashButton = findViewById(R.id.flash_button)
        captureButton = findViewById(R.id.capture_button)
        cameraControls = findViewById(R.id.camera_controls)
        stampCustomization = StampCustomizationStore.read(this)
        templateOverlay = StampOverlayView(this)
        previewFrame.addView(
            templateOverlay,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        locationStamp.visibility = View.GONE
        cameraControls.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTemplateOverlayBounds()
        }
        cameraControls.post { updateTemplateOverlayBounds() }

        imageExecutor = Executors.newSingleThreadExecutor()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MILLIS)
            .setMinUpdateDistanceMeters(LOCATION_DISTANCE_METERS)
            .build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::showLocation)
            }
        }

        findViewById<ImageButton>(R.id.back_button).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.camera_gallery_button).setOnClickListener { galleryPicker.launch("image/*") }
        findViewById<ImageButton>(R.id.switch_camera_button).setOnClickListener { switchCamera() }
        findViewById<View>(R.id.camera_retry_button).setOnClickListener { startCamera(forceRebind = true) }
        findViewById<View>(R.id.camera_choose_photo_button).setOnClickListener {
            galleryPicker.launch("image/*")
        }
        flashButton.setOnClickListener { toggleFlash() }
        captureButton.setOnClickListener { capturePhoto() }
        updateGalleryShortcutVisibility()
        updateStamp()
    }

    override fun onResume() {
        super.onResume()
        if (::templateOverlay.isInitialized) {
            stampCustomization = StampCustomizationStore.read(this)
            refreshTemplateOverlay()
        }
        updateGalleryShortcutVisibility()
        continueIfCameraPermissionGranted()
    }

    private fun updateGalleryShortcutVisibility() {
        val gallerySlot = findViewById<View>(R.id.camera_gallery_slot)
        val galleryButton = findViewById<ImageButton>(R.id.camera_gallery_button)
        val mediaUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf("${Environment.DIRECTORY_PICTURES}/GPS Camera/")
        } else {
            selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("%/${Environment.DIRECTORY_PICTURES}/GPS Camera/%")
        }
        val latestGalleryImage = runCatching {
            contentResolver.query(
                mediaUri,
                arrayOf(MediaStore.Images.Media._ID),
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                ContentUris.withAppendedId(mediaUri, cursor.getLong(0))
            }
        }.getOrNull()

        if (latestGalleryImage == null) {
            galleryButton.setImageDrawable(null)
            gallerySlot.visibility = View.GONE
        } else {
            galleryButton.setImageURI(latestGalleryImage)
            gallerySlot.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        stopLocationUpdates()
        super.onPause()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        if (::imageExecutor.isInitialized) imageExecutor.shutdown()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_LENS_FACING, lensFacing)
        super.onSaveInstanceState(outState)
    }

    private fun requestNeededPermissions() {
        continueIfCameraPermissionGranted()
    }

    private fun continueIfCameraPermissionGranted() {
        if (permissionRequestInProgress) return
        if (!hasPermission(CAMERA_PERMISSION)) {
            showCameraPermissionSheet()
            return
        }
        cameraPermissionSheet?.dismiss()
        cameraPermissionSheet = null
        if (!hasRequiredStoragePermission()) {
            if (storagePermissionRequestAttempted) {
                showRequiredPermissionDialog()
            } else {
                requestLegacyStoragePermission()
            }
            return
        }
        startCamera()
        if (hasLocationPermission()) {
            if (LocationDisabledDialog.isLocationEnabled(this)) {
                startLocationUpdates()
            } else {
                updateStamp()
                LocationDisabledDialog.showIfLocationDisabled(this)
            }
        } else {
            requestLocationPermissionIfAppropriate()
        }
    }

    private fun showCameraPermissionSheet() {
        if (cameraPermissionSheet?.isShowing == true || isFinishing || isDestroyed) return
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_camera_permission)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM
                dimAmount = 0.62f
            }
        }

        val permissionSwitch = dialog.findViewById<SwitchCompat>(R.id.camera_permission_switch)
        cameraPermissionSwitch = permissionSwitch
        dialog.findViewById<View>(R.id.close_camera_permission_sheet).setOnClickListener { dialog.dismiss() }
        permissionSwitch.setOnCheckedChangeListener { _, enabled ->
            if (!enabled) return@setOnCheckedChangeListener
            if (hasPermission(CAMERA_PERMISSION)) {
                dialog.dismiss()
                cameraPermissionSheet = null
                continueIfCameraPermissionGranted()
            } else {
                getPreferences(MODE_PRIVATE).edit().putBoolean(CAMERA_PERMISSION_REQUESTED, true).apply()
                permissionRequestInProgress = true
                permissionsLauncher.launch(arrayOf(CAMERA_PERMISSION))
            }
        }
        dialog.setOnDismissListener {
            if (cameraPermissionSheet === dialog) {
                cameraPermissionSheet = null
                cameraPermissionSwitch = null
            }
        }
        cameraPermissionSheet = dialog
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun requestLocationPermissionIfAppropriate() {
        if (hasLocationPermission()) {
            startLocationUpdates()
            return
        }

        val wasRequestedBefore = getPreferences(MODE_PRIVATE)
            .getBoolean(LOCATION_PERMISSION_REQUESTED, false)
        val shouldExplain = shouldShowRequestPermissionRationale(FINE_LOCATION_PERMISSION) ||
            shouldShowRequestPermissionRationale(COARSE_LOCATION_PERMISSION)

        if (wasRequestedBefore && !shouldExplain) {
            AlertDialog.Builder(this)
                .setTitle(R.string.location_permission_title)
                .setMessage(R.string.location_permission_settings)
                .setNegativeButton(R.string.continue_without_location) { _, _ -> updateStamp() }
                .setPositiveButton(R.string.open_settings) { _, _ -> openAppSettings() }
                .show()
        } else if (shouldExplain) {
            AlertDialog.Builder(this)
                .setTitle(R.string.location_permission_title)
                .setMessage(R.string.location_permission_explanation)
                .setNegativeButton(android.R.string.cancel) { _, _ -> updateStamp() }
                .setPositiveButton(R.string.allow) { _, _ -> requestLocationPermission() }
                .show()
        } else if (!locationPermissionRequestedInThisInstance) {
            locationPermissionRequestedInThisInstance = true
            requestLocationPermission()
        }
    }

    private fun requestLocationPermission() {
        getPreferences(MODE_PRIVATE).edit().putBoolean(LOCATION_PERMISSION_REQUESTED, true).apply()
        permissionRequestInProgress = true
        permissionsLauncher.launch(arrayOf(FINE_LOCATION_PERMISSION, COARSE_LOCATION_PERMISSION))
    }

    private fun requestLegacyStoragePermission() {
        storagePermissionRequestAttempted = true
        permissionRequestInProgress = true
        permissionsLauncher.launch(arrayOf(STORAGE_PERMISSION))
    }

    private fun showRequiredPermissionDialog() {
        val cameraDenied = !hasPermission(CAMERA_PERMISSION)
        val permission = if (cameraDenied) CAMERA_PERMISSION else STORAGE_PERMISSION
        val permanentlyDenied = !shouldShowRequestPermissionRationale(permission)
        AlertDialog.Builder(this)
            .setTitle(if (cameraDenied) R.string.camera_permission_title else R.string.storage_permission_title)
            .setMessage(
                when {
                    cameraDenied && permanentlyDenied -> R.string.camera_permission_settings
                    cameraDenied -> R.string.camera_permission_explanation
                    permanentlyDenied -> R.string.storage_permission_settings
                    else -> R.string.storage_permission_explanation
                }
            )
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setPositiveButton(if (permanentlyDenied) R.string.open_settings else R.string.try_again) { _, _ ->
                if (permanentlyDenied) openAppSettings() else {
                    storagePermissionRequestAttempted = false
                    requestNeededPermissions()
                }
            }
            .show()
    }

    private fun openAppSettings() {
        val settingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(settingsIntent)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean =
        hasPermission(FINE_LOCATION_PERMISSION) || hasPermission(COARSE_LOCATION_PERMISSION)

    private fun hasRequiredStoragePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || hasPermission(STORAGE_PERMISSION)

    private fun selectorFor(facing: Int): CameraSelector =
        if (facing == CameraSelector.LENS_FACING_FRONT) CameraSelector.DEFAULT_FRONT_CAMERA
        else CameraSelector.DEFAULT_BACK_CAMERA

    private fun startCamera(forceRebind: Boolean = false) {
        if (cameraStartInProgress) return
        if (!forceRebind && imageCapture != null && activeCamera != null) return
        cameraStartInProgress = true
        showCameraAvailableState()
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                if (!provider.hasCamera(cameraSelector)) {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        CameraSelector.LENS_FACING_FRONT
                    }
                    cameraSelector = selectorFor(lensFacing)
                    if (!provider.hasCamera(cameraSelector)) {
                        showCameraUnavailableState()
                        return@addListener
                    }
                    updateFlashAvailability()
                }

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(cameraPreview.surfaceProvider)
                }
                val captureUseCase = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(cameraPreview.display?.rotation ?: android.view.Surface.ROTATION_0)
                    .build()

                provider.unbindAll()
                activeCamera = null
                imageCapture = null
                activeCamera = provider.bindToLifecycle(this, cameraSelector, preview, captureUseCase)
                imageCapture = captureUseCase
                updateFlashAvailability()
                showCameraAvailableState()
            } catch (_: Exception) {
                showCameraUnavailableState()
            } finally {
                cameraStartInProgress = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showCameraUnavailableState() {
        cameraPreview.visibility = View.GONE
        templateOverlay.visibility = View.GONE
        cameraUnavailablePanel.visibility = View.VISIBLE
        cameraControls.visibility = View.GONE
        cameraUnavailableActions.visibility = View.VISIBLE
        cameraTitle.setText(R.string.camera_preview_title)
        flashButton.visibility = View.INVISIBLE
    }

    private fun showCameraAvailableState() {
        cameraPreview.visibility = View.VISIBLE
        cameraUnavailablePanel.visibility = View.GONE
        cameraUnavailableActions.visibility = View.GONE
        cameraControls.visibility = View.VISIBLE
        cameraTitle.setText(R.string.gps_camera_title)
        if (::templateOverlay.isInitialized) templateOverlay.visibility = View.VISIBLE
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        cameraSelector = selectorFor(lensFacing)
        startCamera(forceRebind = true)
    }

    private fun updateFlashAvailability() {
        val flashAvailable = lensFacing == CameraSelector.LENS_FACING_BACK &&
            activeCamera?.cameraInfo?.hasFlashUnit() == true
        flashButton.visibility = if (flashAvailable) View.VISIBLE else View.INVISIBLE
        if (!flashAvailable) activeCamera?.cameraControl?.enableTorch(false)
    }

    private fun toggleFlash() {
        val camera = activeCamera ?: return
        if (!camera.cameraInfo.hasFlashUnit()) return
        val turnOn = camera.cameraInfo.torchState.value != TorchState.ON
        camera.cameraControl.enableTorch(turnOn)
        flashButton.contentDescription = getString(if (turnOn) R.string.flash_on else R.string.flash_off)
        flashButton.imageTintList = android.content.res.ColorStateList.valueOf(
            if (turnOn) Color.YELLOW else Color.WHITE
        )
    }

    private fun capturePhoto() {
        val captureUseCase = imageCapture ?: return
        if (captureInProgress) return

        captureInProgress = true
        captureButton.isEnabled = false
        val temporaryPhoto = try {
            File.createTempFile("gps-camera-", ".jpg", cacheDir)
        } catch (error: Exception) {
            captureFailed(error, "creating a temporary photo file")
            return
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(temporaryPhoto).build()
        captureUseCase.takePicture(outputOptions, imageExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                saveCapturedPhoto(temporaryPhoto)
            }

            override fun onError(exception: ImageCaptureException) {
                temporaryPhoto.delete()
                Log.e(LOG_TAG, "CameraX failed to capture the image", exception)
                this@CameraActivity.runOnUiThread {
                    captureFailed(exception, "capturing the image")
                }
            }
        })
    }

    private fun saveCapturedPhoto(temporaryPhoto: File) {
        var savedUri: Uri? = null
        var failureStage = "saving the captured photo"
        try {
            try {
                writeExifMetadata(temporaryPhoto)
            } catch (metadataError: Exception) {
                // A photo should still be saved if optional EXIF metadata cannot be written.
                Log.w(LOG_TAG, "Could not write optional EXIF metadata", metadataError)
            }
            savedUri = copyToMediaStore(temporaryPhoto)
        } catch (error: Exception) {
            Log.e(LOG_TAG, "Photo save failed while $failureStage", error)
            savedUri?.let { contentResolver.delete(it, null, null) }
            this@CameraActivity.runOnUiThread { captureFailed(error, failureStage) }
        } finally {
            temporaryPhoto.delete()
        }

        savedUri?.let { photoUri ->
            val capturedAt = DateFormat.getDateTimeInstance().format(Date())
            val latitude = latestLocation?.latitude ?: Double.NaN
            val longitude = latestLocation?.longitude ?: Double.NaN
            this@CameraActivity.runOnUiThread {
                captureInProgress = false
                captureButton.isEnabled = true
                startActivity(Intent(this, CameraPhotoPreviewActivity::class.java).apply {
                    putExtra(EXTRA_PHOTO_URI, photoUri.toString())
                    putExtra(EXTRA_LATITUDE, latitude)
                    putExtra(EXTRA_LONGITUDE, longitude)
                    putExtra(EXTRA_CAPTURED_AT, capturedAt)
                    putExtra(EXTRA_TOOLS_CAMERA, toolsCameraMode)
                })
            }
        }
    }

    private fun writeExifMetadata(imageFile: File) {
        val exif = ExifInterface(imageFile.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.setAttribute(
            ExifInterface.TAG_DATETIME_ORIGINAL,
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date())
        )
        latestLocation?.let { location ->
            exif.setLatLong(location.latitude, location.longitude)
            exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS")
        }
        exif.saveAttributes()
    }

    private fun copyToMediaStore(imageFile: File): Uri {
        val imageName = "GPS_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, imageName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GPS Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val picturesDirectory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GPS Camera"
                )
                check(picturesDirectory.exists() || picturesDirectory.mkdirs()) { "Could not create photo folder" }
                put(MediaStore.Images.Media.DATA, File(picturesDirectory, imageName).absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val destination = contentResolver.insert(collection, values) ?: error("Could not create photo entry")
        try {
            val output = contentResolver.openOutputStream(destination, "w") ?: error("Could not open photo output")
            FileInputStream(imageFile).use { input -> output.use { input.copyTo(it) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.update(destination, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }
        } catch (error: Exception) {
            contentResolver.delete(destination, null, null)
            throw error
        }
        return destination
    }

    private fun captureFailed(error: Throwable? = null, stage: String? = null) {
        captureInProgress = false
        captureButton.isEnabled = true
        val detail = error?.localizedMessage?.takeIf { it.isNotBlank() }
            ?: error?.javaClass?.simpleName
            ?: getString(R.string.unknown_error)
        val message = if (stage == null) getString(R.string.photo_save_error)
        else getString(R.string.photo_save_error_detail, stage, detail)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission() || !LocationDisabledDialog.isLocationEnabled(this)) {
            updateStamp()
            return
        }
        try {
            locationClient.lastLocation.addOnSuccessListener { location -> location?.let(::showLocation) }
            locationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        } catch (_: SecurityException) {
            updateStamp()
        } catch (_: IllegalStateException) {
            updateStamp()
        }
    }

    private fun stopLocationUpdates() {
        if (::locationClient.isInitialized && ::locationCallback.isInitialized) {
            locationClient.removeLocationUpdates(locationCallback)
        }
    }

    @MainThread
    private fun showLocation(location: android.location.Location) {
        latestLocation = location
        locationAddress = "Current location"
        updateStamp()
        imageExecutor.execute {
            val resolved = try {
                if (!Geocoder.isPresent()) null else {
                    @Suppress("DEPRECATION")
                    Geocoder(this, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                        ?.firstOrNull()?.getAddressLine(0)
                }
            } catch (_: Exception) {
                null
            }
            val weather = OpenMeteoWeather.fetchCached(location.latitude, location.longitude)?.uiText
            runOnUiThread {
                locationAddress = resolved?.takeIf { it.isNotBlank() } ?: "Current location"
                weatherText = weather
                refreshTemplateOverlay()
            }
        }
    }

    private fun updateStamp() {
        if (!::locationStamp.isInitialized) return
        val location = latestLocation
        val timestamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
        if (location == null) {
            locationStamp.text = "${getString(R.string.location_unavailable)}\n$timestamp"
        } else {
            val decimalCoordinates = String.format(Locale.getDefault(), "%.6f, %.6f", location.latitude, location.longitude)
            val dmsCoordinates = "${formatCoordinateDms(location.latitude, true)}  ${formatCoordinateDms(location.longitude, false)}"
            locationStamp.text = "$decimalCoordinates\n$dmsCoordinates\n$timestamp"
        }

        refreshTemplateOverlay()
    }

    private fun refreshTemplateOverlay() {
        if (!::templateOverlay.isInitialized) return
        val customization = stampCustomization ?: StampCustomizationStore.read(this).also {
            stampCustomization = it
        }
        val location = latestLocation
        templateOverlay.setCustomization(customization)
        templateOverlay.setRenderData(
            StampRenderData(
                address = locationAddress,
                latitude = location?.latitude ?: Double.NaN,
                longitude = location?.longitude ?: Double.NaN,
                capturedAt = System.currentTimeMillis(),
                weather = if (customization.showWeather) weatherText else null
            )
        )
    }

    private fun updateTemplateOverlayBounds() {
        if (!::templateOverlay.isInitialized) return
        val params = templateOverlay.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        if (params.leftMargin != 0 || params.topMargin != 0 || params.rightMargin != 0 || params.bottomMargin != 0) {
            params.leftMargin = 0
            params.topMargin = 0
            params.rightMargin = 0
            params.bottomMargin = 0
            templateOverlay.layoutParams = params
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatCoordinateDms(coordinate: Double, latitude: Boolean): String {
        val direction = when {
            latitude && coordinate >= 0 -> "N"
            latitude -> "S"
            coordinate >= 0 -> "E"
            else -> "W"
        }
        val absolute = kotlin.math.abs(coordinate)
        val degrees = absolute.toInt()
        val minuteValue = (absolute - degrees) * 60
        val minutes = minuteValue.toInt()
        val seconds = (minuteValue - minutes) * 60
        return String.format(Locale.getDefault(), "%d° %d′ %.2f″ %s", degrees, minutes, seconds, direction)
    }
}



