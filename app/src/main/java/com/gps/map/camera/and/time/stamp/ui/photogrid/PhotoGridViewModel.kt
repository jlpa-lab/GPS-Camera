package com.gps.map.camera.and.time.stamp.ui.photogrid

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class PhotoGridLayout {
    TWO_BY_TWO,
    VERTICAL,
    HORIZONTAL
}

enum class PhotoGridBorder(val color: Int, val label: String) {
    WHITE(0xFFFFFFFF.toInt(), "White"),
    BLACK(0xFF000000.toInt(), "Black"),
    BLUE(0xFF2878F0.toInt(), "Blue"),
    RED(0xFFE03842.toInt(), "Red")
}

enum class PhotoGridScreen {
    SELECT,
    EDIT,
    SAVED,
    FAILED
}

data class PhotoGridUiState(
    val screen: PhotoGridScreen = PhotoGridScreen.SELECT,
    val selectedUris: List<Uri> = emptyList(),
    val layout: PhotoGridLayout = PhotoGridLayout.TWO_BY_TWO,
    val spacing: Int = 8,
    val border: PhotoGridBorder = PhotoGridBorder.WHITE,
    val savedUri: Uri? = null
)

class PhotoGridViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val state = MutableStateFlow(readState())
    val uiState: StateFlow<PhotoGridUiState> = state

    fun togglePhoto(uri: Uri): Boolean {
        val current = state.value
        val next = if (uri in current.selectedUris) {
            current.selectedUris - uri
        } else {
            if (current.selectedUris.size == MAX_PHOTOS) return false
            current.selectedUris + uri
        }
        update { it.copy(selectedUris = next) }
        return true
    }

    fun setScreen(screen: PhotoGridScreen) = update { it.copy(screen = screen) }

    fun setLayout(layout: PhotoGridLayout) = update { it.copy(layout = layout) }

    fun setSpacing(spacing: Int) = update { it.copy(spacing = spacing.coerceIn(0, 24)) }

    fun setBorder(border: PhotoGridBorder) = update { it.copy(border = border) }

    fun saveCompleted(uri: Uri) = update {
        it.copy(screen = PhotoGridScreen.SAVED, savedUri = uri)
    }

    fun saveFailed() = update { it.copy(screen = PhotoGridScreen.FAILED) }

    fun createAnother() {
        update {
            PhotoGridUiState(
                selectedUris = emptyList(),
                layout = PhotoGridLayout.TWO_BY_TWO,
                spacing = 8,
                border = PhotoGridBorder.WHITE
            )
        }
    }

    private fun update(transform: (PhotoGridUiState) -> PhotoGridUiState) {
        state.update { current ->
            transform(current).also(::saveState)
        }
    }

    private fun readState(): PhotoGridUiState {
        val selected = savedStateHandle.get<ArrayList<String>>(KEY_SELECTED).orEmpty()
            .map(Uri::parse)
        val layout = savedStateHandle.get<String>(KEY_LAYOUT)
            ?.let { runCatching { PhotoGridLayout.valueOf(it) }.getOrNull() }
            ?: PhotoGridLayout.TWO_BY_TWO
        val border = savedStateHandle.get<String>(KEY_BORDER)
            ?.let { runCatching { PhotoGridBorder.valueOf(it) }.getOrNull() }
            ?: PhotoGridBorder.WHITE
        val screen = savedStateHandle.get<String>(KEY_SCREEN)
            ?.let { runCatching { PhotoGridScreen.valueOf(it) }.getOrNull() }
            ?: PhotoGridScreen.SELECT
        return PhotoGridUiState(
            screen = screen,
            selectedUris = selected,
            layout = layout,
            spacing = savedStateHandle.get<Int>(KEY_SPACING) ?: 8,
            border = border,
            savedUri = savedStateHandle.get<String>(KEY_SAVED_URI)?.let(Uri::parse)
        )
    }

    private fun saveState(value: PhotoGridUiState) {
        savedStateHandle[KEY_SELECTED] = ArrayList(value.selectedUris.map(Uri::toString))
        savedStateHandle[KEY_LAYOUT] = value.layout.name
        savedStateHandle[KEY_SPACING] = value.spacing
        savedStateHandle[KEY_BORDER] = value.border.name
        savedStateHandle[KEY_SCREEN] = value.screen.name
        savedStateHandle[KEY_SAVED_URI] = value.savedUri?.toString()
    }

    private companion object {
        const val MAX_PHOTOS = 4
        const val KEY_SELECTED = "photo_grid_selected"
        const val KEY_LAYOUT = "photo_grid_layout"
        const val KEY_SPACING = "photo_grid_spacing"
        const val KEY_BORDER = "photo_grid_border"
        const val KEY_SCREEN = "photo_grid_screen"
        const val KEY_SAVED_URI = "photo_grid_saved_uri"
    }
}
