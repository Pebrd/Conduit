package com.conduit.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.domain.usecase.SyncPlaylistUseCase
import com.conduit.domain.usecase.SyncProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncUiState(
    val progress: SyncProgress? = null,
    val error: String? = null,
    val isCompleted: Boolean = false
)

class SyncViewModel(
    private val syncPlaylistUseCase: SyncPlaylistUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState = _uiState.asStateFlow()

    fun startSync(playlistId: String, playlistName: String) {
        viewModelScope.launch {
            try {
                syncPlaylistUseCase(playlistId).collect { progress ->
                    _uiState.update { it.copy(
                        progress = progress,
                        isCompleted = progress is SyncProgress.Completed
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
