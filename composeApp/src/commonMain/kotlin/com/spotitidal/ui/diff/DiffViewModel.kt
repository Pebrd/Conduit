package com.spotitidal.ui.diff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotitidal.domain.model.DiffEntry
import com.spotitidal.domain.usecase.BuildDiffUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiffUiState(
    val diffEntries: List<DiffEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class DiffViewModel(
    private val buildDiffUseCase: BuildDiffUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiffUiState())
    val uiState = _uiState.asStateFlow()

    fun loadDiff(playlistId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // For now, we don't have the tidalPlaylistId, so we pass null
                val diff = buildDiffUseCase(playlistId, null)
                _uiState.update { it.copy(diffEntries = diff) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
