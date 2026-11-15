package com.gps.map.camera.and.time.stamp.ui.gallery

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GalleryFilter { ALL, PHOTOS, VIDEOS }
enum class GalleryScreen { CAPTURES, DETAILS, DELETE_CONFIRMATION, EMPTY }

data class GalleryUiState(
    val screen: GalleryScreen = GalleryScreen.CAPTURES,
    val filter: GalleryFilter = GalleryFilter.ALL,
    val selectedUri: Uri? = null,
    val captures: List<GalleryCapture> = emptyList(),
    val loading: Boolean = false,
    val permissionDenied: Boolean = false,
    val message: String? = null
)

class GalleryViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val state = MutableStateFlow(readState())
    val uiState: StateFlow<GalleryUiState> = state.asStateFlow()

    fun setFilter(filter: GalleryFilter) = update {
        it.copy(filter = filter, screen = GalleryScreen.CAPTURES, selectedUri = null)
    }

    fun setCaptures(captures: List<GalleryCapture>) = update {
        val hasItems = captures.isNotEmpty()
        val selectedStillExists = it.selectedUri != null && captures.any { capture -> capture.uri == it.selectedUri }
        val screen = if (selectedStillExists &&
            it.screen in setOf(GalleryScreen.DETAILS, GalleryScreen.DELETE_CONFIRMATION)
        ) {
            it.screen
        } else if (hasItems) {
            GalleryScreen.CAPTURES
        } else {
            GalleryScreen.EMPTY
        }
        it.copy(
            captures = captures,
            loading = false,
            screen = screen
        )
    }

    fun setLoading(loading: Boolean) = update { it.copy(loading = loading) }

    fun setPermissionDenied(denied: Boolean) = update {
        it.copy(permissionDenied = denied, loading = false, screen = GalleryScreen.EMPTY)
    }

    fun openDetails(capture: GalleryCapture) = update {
        it.copy(screen = GalleryScreen.DETAILS, selectedUri = capture.uri)
    }

    fun openDeleteConfirmation() = update { it.copy(screen = GalleryScreen.DELETE_CONFIRMATION) }

    fun returnToDetails() = update { it.copy(screen = GalleryScreen.DETAILS) }

    fun deleteCompleted() = update {
        it.copy(
            screen = GalleryScreen.CAPTURES,
            selectedUri = null,
            message = "deleted"
        )
    }

    fun clearMessage() = update { it.copy(message = null) }

    fun clearSelection() = update {
        it.copy(screen = GalleryScreen.CAPTURES, selectedUri = null)
    }

    fun selectedCapture(): GalleryCapture? = state.value.captures.firstOrNull {
        it.uri == state.value.selectedUri
    }

    private fun update(transform: (GalleryUiState) -> GalleryUiState) {
        state.value = transform(state.value)
        saveState(state.value)
    }

    private fun readState(): GalleryUiState {
        val filter = savedStateHandle.get<String>(FILTER_KEY)
            ?.let { runCatching { GalleryFilter.valueOf(it) }.getOrNull() }
            ?: GalleryFilter.ALL
        val screen = savedStateHandle.get<String>(SCREEN_KEY)
            ?.let { runCatching { GalleryScreen.valueOf(it) }.getOrNull() }
            ?: GalleryScreen.CAPTURES
        val selected = savedStateHandle.get<String>(SELECTED_URI_KEY)?.let(Uri::parse)
        return GalleryUiState(filter = filter, screen = screen, selectedUri = selected)
    }

    private fun saveState(value: GalleryUiState) {
        savedStateHandle[FILTER_KEY] = value.filter.name
        savedStateHandle[SCREEN_KEY] = value.screen.name
        savedStateHandle[SELECTED_URI_KEY] = value.selectedUri?.toString()
    }

    private companion object {
        const val FILTER_KEY = "gallery_filter"
        const val SCREEN_KEY = "gallery_screen"
        const val SELECTED_URI_KEY = "gallery_selected_uri"
    }
}
