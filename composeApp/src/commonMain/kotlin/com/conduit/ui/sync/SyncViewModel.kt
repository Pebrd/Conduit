package com.conduit.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.domain.model.SyncProgress
import com.conduit.domain.model.AllSyncProgress
import com.conduit.domain.usecase.SyncPlaylistUseCase
import com.conduit.domain.usecase.SyncAllPlaylistsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SyncUiState(
    val progress: SyncProgress? = null,
    val allProgress: AllSyncProgress? = null,
    val error: String? = null,
)

class SyncViewModel(
    private val syncPlaylistUseCase: SyncPlaylistUseCase,
    private val syncAllUseCase: SyncAllPlaylistsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private var syncJob: Job? = null

    fun syncPlaylist(playlistId: String, playlistName: String) {
        syncJob = viewModelScope.launch {
            try {
                syncPlaylistUseCase(playlistId, playlistName).collect { progress ->
                    _state.update { it.copy(progress = progress) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun syncAll() {
        syncJob = viewModelScope.launch {
            try {
                syncAllUseCase().collect { progress ->
                    _state.update { it.copy(allProgress = progress) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun cancel() {
        syncJob?.cancel()
        _state.update { it.copy(progress = null, allProgress = null) }
    }
}
