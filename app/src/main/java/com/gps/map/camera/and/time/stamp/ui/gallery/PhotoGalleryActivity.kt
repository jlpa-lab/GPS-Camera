package com.gps.map.camera.and.time.stamp.ui.gallery

import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ads.AdRemoteConfig
import com.gps.map.camera.and.time.stamp.ads.AdsManager
import com.gps.map.camera.and.time.stamp.ads.banner_all
import com.gps.map.camera.and.time.stamp.databinding.ActivityGalleryBinding
import com.gps.map.camera.and.time.stamp.ui.camera.CameraActivity
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampCustomizationStore
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampOverlayView
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampRenderData
import com.gps.map.camera.and.time.stamp.utils.location.LocationDisabledDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class PhotoGalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private lateinit var viewModel: GalleryViewModel
    private lateinit var adapter: GalleryCaptureAdapter
    private lateinit var galleryTemplateOverlay: StampOverlayView
    private var loadedCaptures = emptyList<GalleryCapture>()
    private var offset = 0
    private var loadingPage = false
    private var reachedEnd = false
    private var deleting = false
    private var deleteUri: Uri? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.setPermissionDenied(false)
            loadFirstPage()
        } else {
            viewModel.setPermissionDenied(true)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) loadFirstPage()
    }

    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) completeDelete() else resetDeleteControls()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadPhotoGalleryBanner()
        viewModel = ViewModelProvider(this)[GalleryViewModel::class.java]
        adapter = GalleryCaptureAdapter(::openDetails)
        galleryTemplateOverlay = StampOverlayView(this)
        binding.galleryDetailMediaFrame.addView(
            galleryTemplateOverlay,
            1,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        // Keep the legacy view in the layout for binding compatibility, but
        // render the selected template through the shared overlay instead.
        binding.galleryStampCard.visibility = View.GONE

        binding.galleryGrid.layoutManager = GridLayoutManager(this, 3)
        binding.galleryGrid.adapter = adapter
        binding.galleryGrid.addItemDecoration(GalleryGridSpacingDecoration(dp(8), dp(8), 3))
        binding.galleryGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || loadingPage || reachedEnd) return
                val manager = recyclerView.layoutManager as GridLayoutManager
                if (manager.findLastVisibleItemPosition() >= adapter.itemCount - 6) loadNextPage()
            }
        })

        binding.galleryBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.galleryFilterAll.setOnClickListener { changeFilter(GalleryFilter.ALL) }
        binding.galleryFilterPhotos.setOnClickListener { changeFilter(GalleryFilter.PHOTOS) }
        binding.galleryFilterVideos.setOnClickListener { changeFilter(GalleryFilter.VIDEOS) }
        binding.galleryTakePhoto.setOnClickListener { openCamera() }
        binding.galleryEmptyTakePhoto.setOnClickListener { openCamera() }
        binding.galleryBackTools.setOnClickListener { finish() }
        binding.galleryPermissionAction.setOnClickListener { handlePermissionAction() }
        binding.galleryPermissionButton.setOnClickListener { handlePermissionAction() }
        binding.galleryShare.setOnClickListener { shareSelectedCapture() }
        binding.galleryDelete.setOnClickListener { viewModel.openDeleteConfirmation() }
        binding.galleryDetailPlay.setOnClickListener { openSelectedCapture() }
        binding.galleryConfirmDelete.setOnClickListener { deleteSelectedCapture() }
        binding.galleryKeep.setOnClickListener { viewModel.returnToDetails() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (deleting) return
                when (viewModel.uiState.value.screen) {
                    GalleryScreen.DELETE_CONFIRMATION -> viewModel.returnToDetails()
                    GalleryScreen.DETAILS -> viewModel.clearSelection()
                    GalleryScreen.CAPTURES,
                    GalleryScreen.EMPTY -> finish()
                }
            }
        })

        viewModel.uiState.collectInLifecycle { render(it) }
        if (GalleryMediaAccess.hasGalleryPermissions(this)) loadFirstPage()
        else permissionLauncher.launch(GalleryMediaAccess.galleryPermissions())
    }

    override fun onResume() {
        super.onResume()
        viewModel.selectedCapture()?.let { selected ->
            if (viewModel.uiState.value.screen != GalleryScreen.CAPTURES &&
                viewModel.uiState.value.screen != GalleryScreen.EMPTY
            ) populateDetails(selected)
        }
        if (GalleryMediaAccess.hasGalleryPermissions(this) &&
            viewModel.uiState.value.screen in setOf(GalleryScreen.CAPTURES, GalleryScreen.EMPTY)
        ) {
            if (viewModel.uiState.value.permissionDenied) viewModel.setPermissionDenied(false)
            loadFirstPage()
        }
    }

    private fun changeFilter(filter: GalleryFilter) {
        viewModel.setFilter(filter)
        if (GalleryMediaAccess.hasGalleryPermissions(this)) loadFirstPage()
    }

    private fun loadFirstPage() {
        if (!GalleryMediaAccess.hasGalleryPermissions(this)) {
            viewModel.setPermissionDenied(true)
            return
        }
        offset = 0
        reachedEnd = false
        loadedCaptures = emptyList()
        adapter.submitList(emptyList())
        loadNextPage()
    }

    private fun loadNextPage() {
        if (loadingPage || reachedEnd || !GalleryMediaAccess.hasGalleryPermissions(this)) return
        loadingPage = true
        viewModel.setLoading(true)
        lifecycleScope.launch {
            val page = withContext(Dispatchers.IO) {
                GalleryMediaRepository.queryCaptures(
                    this@PhotoGalleryActivity,
                    viewModel.uiState.value.filter,
                    PAGE_SIZE,
                    offset
                )
            }
            loadedCaptures = loadedCaptures + page
            offset += page.size
            reachedEnd = page.size < PAGE_SIZE
            adapter.submitList(loadedCaptures)
            viewModel.setCaptures(loadedCaptures)
            loadingPage = false
        }
    }

    private fun openCamera() {
        if (LocationDisabledDialog.showIfCameraAccessDisabled(this)) return
        cameraLauncher.launch(Intent(this, CameraActivity::class.java).apply {
        })
    }

    private fun openDetails(capture: GalleryCapture) {
        viewModel.openDetails(capture)
        populateDetails(capture)
    }

    private fun populateDetails(capture: GalleryCapture) {
        Glide.with(binding.galleryDetailImage).load(capture.uri).centerCrop().into(binding.galleryDetailImage)
        val isVideo = capture.type == GalleryCaptureType.VIDEO
        binding.galleryDetailPlay.visibility = if (isVideo) View.VISIBLE else View.GONE
        binding.galleryShare.text = getString(if (isVideo) R.string.gallery_share_video else R.string.gallery_share_photo)
        binding.galleryDelete.text = getString(if (isVideo) R.string.gallery_delete_video else R.string.gallery_delete_photo)
        binding.galleryDetailDate.text = formatDate(capture.dateAdded * 1000L)
        binding.galleryDetailTitle.setText(R.string.gallery_title)
        lifecycleScope.launch {
            val location = withContext(Dispatchers.IO) { resolveLocation(capture) }
            if (viewModel.uiState.value.selectedUri != capture.uri) return@launch
            binding.galleryDetailTitle.text = location.shortName
            val showTemplate = !capture.hasBakedStamp
            galleryTemplateOverlay.visibility = if (showTemplate) View.VISIBLE else View.GONE
            if (showTemplate) {
                galleryTemplateOverlay.setCustomization(StampCustomizationStore.read(this@PhotoGalleryActivity))
                galleryTemplateOverlay.setRenderData(
                    StampRenderData(
                        address = location.longName,
                        latitude = capture.latitude ?: Double.NaN,
                        longitude = capture.longitude ?: Double.NaN,
                        capturedAt = capture.dateAdded * 1000L,
                        capturedAtText = formatDateWithTime(capture.dateAdded * 1000L)
                    )
                )
            }
        }
    }

    private fun resolveLocation(capture: GalleryCapture): LocationText {
        val latitude = capture.latitude
        val longitude = capture.longitude
        if (latitude == null || longitude == null) {
            return LocationText(getString(R.string.gallery_title), getString(R.string.gallery_title), getString(R.string.gallery_location_unavailable))
        }
        val address = try {
            if (!Geocoder.isPresent()) null else {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        } catch (_: Exception) {
            null
        }
        return LocationText(
            address.shortName(latitude, longitude),
            address.longName(latitude, longitude),
            formatCoordinates(latitude, longitude)
        )
    }

    private fun shareSelectedCapture() {
        val capture = viewModel.selectedCapture() ?: return
        try {
            MediaShareHelper.openChooser(this, capture.uri, capture.type, getString(R.string.share_photo))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.gallery_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSelectedCapture() {
        val capture = viewModel.selectedCapture() ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(capture.uri, if (capture.type == GalleryCaptureType.VIDEO) "video/*" else "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSelectedCapture() {
        if (deleting) return
        val capture = viewModel.selectedCapture() ?: return
        deleting = true
        deleteUri = capture.uri
        binding.galleryConfirmDelete.isEnabled = false
        binding.galleryKeep.isEnabled = false
        binding.galleryConfirmDelete.setText(R.string.gallery_deleting)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            deleteRequestLauncher.launch(IntentSenderRequest.Builder(
                MediaStore.createDeleteRequest(contentResolver, listOf(capture.uri)).intentSender
            ).build())
            return
        }
        try {
            if (contentResolver.delete(capture.uri, null, null) > 0) completeDelete() else deleteFailed()
        } catch (error: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && error is android.app.RecoverableSecurityException) {
                deleteRequestLauncher.launch(IntentSenderRequest.Builder(error.userAction.actionIntent.intentSender).build())
            } else deleteFailed()
        }
    }

    private fun completeDelete() {
        val uri = deleteUri
        deleting = false
        deleteUri = null
        binding.galleryConfirmDelete.isEnabled = true
        binding.galleryKeep.isEnabled = true
        if (uri != null && isAvailable(uri)) {
            deleteFailed()
            return
        }
        viewModel.deleteCompleted()
        Toast.makeText(this, R.string.gallery_deleted, Toast.LENGTH_SHORT).show()
        loadFirstPage()
    }

    private fun loadPhotoGalleryBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }
    private fun deleteFailed() {
        resetDeleteControls()
        Toast.makeText(this, R.string.gallery_delete_failed, Toast.LENGTH_SHORT).show()
    }

    private fun resetDeleteControls() {
        deleting = false
        deleteUri = null
        binding.galleryConfirmDelete.isEnabled = true
        binding.galleryKeep.isEnabled = true
        val selected = viewModel.selectedCapture()
        binding.galleryConfirmDelete.text = getString(
            if (selected?.type == GalleryCaptureType.VIDEO) R.string.gallery_delete_video else R.string.gallery_delete_photo
        )
    }

    private fun isAvailable(uri: Uri): Boolean = try {
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: Exception) {
        false
    }

    private fun handlePermissionAction() {
        val permissions = GalleryMediaAccess.galleryPermissions()
        if (shouldShowRequestPermissionRationale(permissions.first())) permissionLauncher.launch(permissions)
        else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun render(state: GalleryUiState) {
        val selected = state.selectedUri?.let { uri -> state.captures.firstOrNull { it.uri == uri } }
        if (selected != null && state.screen != GalleryScreen.CAPTURES && state.screen != GalleryScreen.EMPTY) {
            populateDetails(selected)
            Glide.with(binding.galleryDeletePreview).load(selected.uri).centerCrop().into(binding.galleryDeletePreview)
            binding.galleryDeleteTitle.text = getString(if (selected.type == GalleryCaptureType.VIDEO) R.string.gallery_delete_this_video else R.string.gallery_delete_this_photo)
            binding.galleryConfirmDelete.text = getString(if (selected.type == GalleryCaptureType.VIDEO) R.string.gallery_delete_video else R.string.gallery_delete_photo)
            binding.galleryKeep.text = getString(if (selected.type == GalleryCaptureType.VIDEO) R.string.gallery_keep_video else R.string.gallery_keep_photo)
        }

        binding.galleryCapturesState.visibility = if (state.screen == GalleryScreen.CAPTURES) View.VISIBLE else View.GONE
        binding.galleryDetailsState.visibility = if (state.screen == GalleryScreen.DETAILS) View.VISIBLE else View.GONE
        binding.galleryDeleteState.visibility = if (state.screen == GalleryScreen.DELETE_CONFIRMATION) View.VISIBLE else View.GONE
        binding.galleryEmptyState.visibility = if (state.screen == GalleryScreen.EMPTY) View.VISIBLE else View.GONE
        binding.galleryFilterAll.isSelected = state.filter == GalleryFilter.ALL
        binding.galleryFilterPhotos.isSelected = state.filter == GalleryFilter.PHOTOS
        binding.galleryFilterVideos.isSelected = state.filter == GalleryFilter.VIDEOS
        binding.galleryGrid.visibility = if (state.loading || state.captures.isNotEmpty()) View.VISIBLE else View.GONE
        binding.galleryPermissionMessage.visibility = if (state.permissionDenied) View.VISIBLE else View.GONE
        binding.galleryPermissionButton.visibility = if (state.permissionDenied) View.VISIBLE else View.GONE

        if (state.screen == GalleryScreen.EMPTY) {
            if (state.permissionDenied) {
                binding.galleryEmptyTitle.setText(R.string.gallery_permission_title)
                binding.galleryEmptyMessage.setText(R.string.gallery_permission_message)
                binding.galleryPermissionAction.visibility = View.VISIBLE
            } else {
                binding.galleryPermissionAction.visibility = View.GONE
                binding.galleryEmptyTitle.setText(when (state.filter) {
                    GalleryFilter.ALL -> R.string.gallery_no_captures
                    GalleryFilter.PHOTOS -> R.string.gallery_no_photos
                    GalleryFilter.VIDEOS -> R.string.gallery_no_videos
                })
                binding.galleryEmptyMessage.setText(R.string.gallery_no_captures_message)
            }
        }
    }

    private fun formatDate(epochMillis: Long): String = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date(epochMillis))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun formatDateWithTime(epochMillis: Long): String = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()).format(Date(epochMillis))

    private fun formatCoordinates(latitude: Double, longitude: Double): String =
        "${"%.4f".format(Locale.getDefault(), kotlin.math.abs(latitude))}° ${if (latitude >= 0) "N" else "S"}  " +
            "${"%.4f".format(Locale.getDefault(), kotlin.math.abs(longitude))}° ${if (longitude >= 0) "E" else "W"}"

    private data class LocationText(val shortName: String, val longName: String, val coordinates: String)

    private fun Address?.shortName(latitude: Double, longitude: Double): String {
        val city = this?.locality ?: this?.subAdminArea ?: this?.adminArea
        return listOfNotNull(city, this?.countryName).joinToString(", ").ifBlank {
            "%.4f, %.4f".format(Locale.getDefault(), latitude, longitude)
        }
    }

    private fun Address?.longName(latitude: Double, longitude: Double): String {
        val place = this?.featureName ?: this?.subLocality ?: this?.locality
        return listOfNotNull(place, this?.countryName).joinToString(", ").ifBlank {
            "%.4f, %.4f".format(Locale.getDefault(), latitude, longitude)
        }
    }

    private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectInLifecycle(block: (T) -> Unit) {
        lifecycleScope.launch { collect { block(it) } }
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}


private class GalleryGridSpacingDecoration(
    private val horizontalSpacing: Int,
    private val verticalSpacing: Int,
    private val spanCount: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: android.graphics.Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val column = position % spanCount
        outRect.left = horizontalSpacing * column / spanCount
        outRect.right = horizontalSpacing * (spanCount - 1 - column) / spanCount
        outRect.bottom = verticalSpacing
    }
}
