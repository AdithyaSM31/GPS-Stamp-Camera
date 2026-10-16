package com.gpsstamp.camera.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gpsstamp.camera.gallery.GalleryPhoto
import com.gpsstamp.camera.gallery.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Photo list shared by the grid and the full-screen viewer. */
class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PhotoRepository(app)

    // Null while the first query runs, so the grid doesn't flash "No photos yet" and the viewer doesn't close.
    private val _photos = MutableStateFlow<List<GalleryPhoto>?>(null)
    val photos: StateFlow<List<GalleryPhoto>?> = _photos.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _photos.value = repository.list() }
    }

    suspend fun delete(photo: GalleryPhoto): Boolean {
        val deleted = repository.delete(photo.uri)
        if (deleted) _photos.value = repository.list()
        return deleted
    }

    suspend fun location(photo: GalleryPhoto): Pair<Double, Double>? = repository.location(photo.uri)
}
