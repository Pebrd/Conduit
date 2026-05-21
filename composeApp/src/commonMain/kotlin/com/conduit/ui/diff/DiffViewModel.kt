package com.conduit.ui.diff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.domain.model.DiffEntry
import com.conduit.domain.usecase.BuildDiffUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiffUiState(
    val playlistName: String = "",
    val diffEntries: List<DiffEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class DiffViewModel(
    private val buildDiffUseCase: BuildDiffUseCase,
    private val spotifyRepo: com.conduit.domain.repository.SpotifyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiffUiState())
    val uiState = _uiState.asStateFlow()

    fun loadDiff(playlistId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Obtener nombre de la playlist para la UI y para el paso siguiente
                val playlists = spotifyRepo.getPlaylists()
                val name = playlists.firstOrNull { it.id == playlistId }?.name ?: "Playlist"
                
                val diff = buildDiffUseCase(playlistId, null)
                _uiState.update { it.copy(diffEntries = diff, playlistName = name) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
