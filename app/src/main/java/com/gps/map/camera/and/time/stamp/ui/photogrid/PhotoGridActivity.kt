package com.gps.map.camera.and.time.stamp.ui.photogrid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.databinding.ActivityPhotoGridBinding
import com.gps.map.camera.and.time.stamp.ui.gallery.GalleryMediaAccess
import com.gps.map.camera.and.time.stamp.ui.gallery.MediaStoreImageSaver
import com.gps.map.camera.and.time.stamp.ui.gallery.PhotoGalleryPhoto
import com.gps.map.camera.and.time.stamp.ui.gallery.PhotoGalleryActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class PhotoGridActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoGridBinding
    private lateinit var viewModel: PhotoGridViewModel
    private lateinit var adapter: PhotoGridSelectionAdapter
    private var loadedPhotos = emptyList<PhotoGalleryPhoto>()
    private var pageOffset = 0
    private var loadingPage = false
    private var reachedEnd = false
    private var saving = false
    private var permissionWasDenied = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionWasDenied = false
            showGallery()
            loadFirstPage()
        } else {
            permissionWasDenied = true
            showPermissionState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoGridBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[PhotoGridViewModel::class.java]
        adapter = PhotoGridSelectionAdapter(::togglePhoto)
        binding.photoGridGallery.adapter = adapter
        binding.photoGridGallery.layoutManager = GridLayoutManager(this, 3)
        binding.photoGridGallery.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val manager = recyclerView.layoutManager as GridLayoutManager
                if (!loadingPage && !reachedEnd && manager.findLastVisibleItemPosition() >= adapter.itemCount - 6) {
                    loadNextPage()
                }
            }
        })

        binding.photoGridBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.photoGridCreate.setOnClickListener { openEditor() }
        binding.photoGridSave.setOnClickListener { saveGrid() }
        binding.photoGridChangePhotos.setOnClickListener { viewModel.setScreen(PhotoGridScreen.SELECT) }
        binding.photoGridOpenGallery.setOnClickListener { openSavedImage() }
        binding.photoGridCreateAnother.setOnClickListener {
            viewModel.createAnother()
            loadFirstPage()
        }
        binding.photoGridRetry.setOnClickListener { saveGrid() }
        binding.photoGridBackToTools.setOnClickListener { finish() }
        binding.photoGridSpacing.setOnClickListener { showSpacingPicker() }
        binding.photoGridBorder.setOnClickListener { showBorderPicker() }
        binding.photoGridTwoByTwo.setOnClickListener {
            if (it.isEnabled) viewModel.setLayout(PhotoGridLayout.TWO_BY_TWO)
        }
        binding.photoGridVertical.setOnClickListener { viewModel.setLayout(PhotoGridLayout.VERTICAL) }
        binding.photoGridHorizontal.setOnClickListener { viewModel.setLayout(PhotoGridLayout.HORIZONTAL) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (saving) return
                when (viewModel.uiState.value.screen) {
                    PhotoGridScreen.EDIT,
                    PhotoGridScreen.FAILED -> viewModel.setScreen(PhotoGridScreen.SELECT)
                    PhotoGridScreen.SAVED -> viewModel.setScreen(PhotoGridScreen.EDIT)
                    PhotoGridScreen.SELECT -> finish()
                }
            }
        })

        viewModel.uiState.collectInLifecycle { render(it) }
        if (GalleryMediaAccess.hasPermission(this)) {
            showGallery()
            loadFirstPage()
        } else {
            permissionLauncher.launch(GalleryMediaAccess.permission())
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionWasDenied && GalleryMediaAccess.hasPermission(this)) {
            permissionWasDenied = false
            showGallery()
            loadFirstPage()
        }
    }

    private fun showGallery() {
        binding.photoGridPermission.visibility = View.GONE
        binding.photoGridGallery.visibility = View.VISIBLE
    }

    private fun showPermissionState() {
        binding.photoGridGallery.visibility = View.GONE
        binding.photoGridPermission.visibility = View.VISIBLE
        binding.photoGridPermissionMessage.setText(
            if (shouldShowRequestPermissionRationale(GalleryMediaAccess.permission())) {
                R.string.photo_grid_permission_message
            } else {
                R.string.photo_grid_permission_settings_message
            }
        )
        binding.photoGridPermissionButton.setText(
            if (shouldShowRequestPermissionRationale(GalleryMediaAccess.permission())) {
                R.string.photo_grid_allow_access
            } else {
                R.string.photo_grid_open_settings
            }
        )
        binding.photoGridPermissionButton.setOnClickListener {
            if (shouldShowRequestPermissionRationale(GalleryMediaAccess.permission())) {
                permissionLauncher.launch(GalleryMediaAccess.permission())
            } else {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
    }

    private fun loadFirstPage() {
        pageOffset = 0
        reachedEnd = false
        loadedPhotos = emptyList()
        adapter.submitList(emptyList())
        loadNextPage()
    }

    private fun loadNextPage() {
        if (loadingPage || reachedEnd || !GalleryMediaAccess.hasPermission(this)) return
        loadingPage = true
        lifecycleScope.launch {
            val page = withContext(Dispatchers.IO) {
                PhotoGridMediaAccess.queryPhotos(this@PhotoGridActivity, PAGE_SIZE, pageOffset)
            }
            loadedPhotos = loadedPhotos + page
            pageOffset += page.size
            reachedEnd = page.size < PAGE_SIZE
            adapter.submitList(loadedPhotos)
            adapter.setSelectedUris(viewModel.uiState.value.selectedUris)
            loadingPage = false
        }
    }

    private fun togglePhoto(photo: PhotoGalleryPhoto) {
        if (viewModel.uiState.value.selectedUris.size == MAX_PHOTOS && photo.uri !in viewModel.uiState.value.selectedUris) {
            Toast.makeText(this, R.string.photo_grid_maximum_message, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.togglePhoto(photo.uri)
    }

    private fun openEditor() {
        if (viewModel.uiState.value.selectedUris.size !in 2..MAX_PHOTOS) return
        viewModel.setScreen(PhotoGridScreen.EDIT)
    }

    private fun saveGrid() {
        if (saving) return
        val state = viewModel.uiState.value
        if (state.selectedUris.size !in 2..MAX_PHOTOS) return
        saving = true
        binding.photoGridSave.isEnabled = false
        binding.photoGridRetry.isEnabled = false
        binding.photoGridSave.text = getString(R.string.photo_grid_saving)
        binding.photoGridRetry.text = getString(R.string.photo_grid_saving)
        lifecycleScope.launch {
            val result = runCatching {
                var bitmap: android.graphics.Bitmap? = null
                var encodedFile: java.io.File? = null
                try {
                    bitmap = withContext(Dispatchers.IO) {
                        PhotoGridRenderer.render(
                            this@PhotoGridActivity,
                            state.selectedUris,
                            state.layout,
                            state.spacing,
                            state.border.color
                        )
                    }
                    withContext(Dispatchers.IO) {
                        encodedFile = java.io.File.createTempFile("photo-grid-", ".jpg", cacheDir)
                        FileOutputStream(encodedFile!!).use { output ->
                            check(bitmap!!.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, output)) {
                                "Could not encode photo grid"
                            }
                        }
                        MediaStoreImageSaver.saveFile(
                            this@PhotoGridActivity,
                            encodedFile!!,
                            "Photo_Grid_${System.currentTimeMillis()}.jpg"
                        )
                    }
                } finally {
                    bitmap?.takeUnless { it.isRecycled }?.recycle()
                    encodedFile?.delete()
                }
            }
            saving = false
            binding.photoGridSave.isEnabled = true
            binding.photoGridRetry.isEnabled = true
            binding.photoGridSave.text = getString(R.string.photo_grid_save_grid)
            binding.photoGridRetry.text = getString(R.string.photo_grid_retry_export)
            result.onSuccess(viewModel::saveCompleted)
                .onFailure { error ->
                    Log.e("PhotoGrid", "Grid export failed", error)
                    viewModel.saveFailed()
                    Toast.makeText(this@PhotoGridActivity, R.string.photo_grid_save_error, Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun showSpacingPicker() {
        val content = SeekBar(this).apply {
            max = 24
            progress = viewModel.uiState.value.spacing
            setPadding(24, 0, 24, 0)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.photo_grid_spacing)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.setSpacing(content.progress) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showBorderPicker() {
        val borders = PhotoGridBorder.values()
        val selected = borders.indexOf(viewModel.uiState.value.border)
        AlertDialog.Builder(this)
            .setTitle(R.string.photo_grid_border)
            .setSingleChoiceItems(borders.map { it.label }.toTypedArray(), selected) { dialog, which ->
                viewModel.setBorder(borders[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun openSavedImage() {
        startActivity(Intent(this, PhotoGalleryActivity::class.java))
    }

    private fun render(state: PhotoGridUiState) {
        adapter.setSelectedUris(state.selectedUris)
        binding.photoGridSelectedCount.text = getString(
            R.string.photo_grid_selected_count,
            state.selectedUris.size
        )
        binding.photoGridCreate.isEnabled = state.selectedUris.size in 2..MAX_PHOTOS
        binding.photoGridCreate.alpha = if (binding.photoGridCreate.isEnabled) 1f else 0.55f
        binding.photoGridSpacingValue.text = getString(R.string.photo_grid_spacing_value, state.spacing)
        binding.photoGridBorderValue.text = state.border.label
        binding.photoGridEditPreview.show(state.selectedUris, state.layout, state.spacing, state.border.color)
        binding.photoGridSavedPreview.show(state.selectedUris, state.layout, state.spacing, state.border.color)

        binding.photoGridSelectContent.visibility = if (state.screen == PhotoGridScreen.SELECT) View.VISIBLE else View.GONE
        binding.photoGridEditContent.visibility = if (state.screen == PhotoGridScreen.EDIT) View.VISIBLE else View.GONE
        binding.photoGridSavedContent.visibility = if (state.screen == PhotoGridScreen.SAVED) View.VISIBLE else View.GONE
        binding.photoGridFailedContent.visibility = if (state.screen == PhotoGridScreen.FAILED) View.VISIBLE else View.GONE
        binding.photoGridRetry.visibility = if (state.screen == PhotoGridScreen.FAILED) View.VISIBLE else View.GONE
        binding.photoGridBackToTools.visibility = if (state.screen == PhotoGridScreen.FAILED) View.VISIBLE else View.GONE
        binding.photoGridTwoByTwo.isSelected = state.layout == PhotoGridLayout.TWO_BY_TWO
        binding.photoGridVertical.isSelected = state.layout == PhotoGridLayout.VERTICAL
        binding.photoGridHorizontal.isSelected = state.layout == PhotoGridLayout.HORIZONTAL
        // TWO_BY_TWO also handles two photos as a single row, so it is valid for every edit state.
        binding.photoGridTwoByTwo.isEnabled = state.selectedUris.size in 2..MAX_PHOTOS
        binding.photoGridTwoByTwo.alpha = 1f
    }

    private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectInLifecycle(block: (T) -> Unit) {
        lifecycleScope.launch {
            collect { block(it) }
        }
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val MAX_PHOTOS = 4
    }
}
