package com.conduit.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.domain.model.*
import com.conduit.domain.usecase.SyncPlaylistUseCase
import com.conduit.domain.usecase.SyncAllPlaylistsUseCase
import com.conduit.data.local.MappingStorage
import com.conduit.domain.repository.TidalRepository
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
    private val mappingStorage: MappingStorage,
    private val tidalRepo: TidalRepository,
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

    suspend fun searchTidalTracks(query: String): List<TidalTrack> {
        return try {
            tidalRepo.searchTracks(query, limit = 15)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun mapTrackManually(
        playlistId: String,
        spotifyTrack: Track,
        tidalTrack: TidalTrack
    ) {
        viewModelScope.launch {
            try {
                // 1. Agregar a la playlist de Tidal de inmediato
                tidalRepo.addTracksToPlaylist(playlistId, listOf(tidalTrack.id))

                // 2. Guardar mapeo manual para persistencia futura
                mappingStorage.saveMapping(
                    TrackMapping(
                        spotifyTrackId = spotifyTrack.id,
                        tidalTrackId = tidalTrack.id
                    )
                )

                // 3. Actualizar reactivamente el estado de la UI
                _state.update { currentState ->
                    val progress = currentState.progress
                    if (progress is SyncProgress.Completed) {
                        val result = progress.result ?: return@update currentState

                        // Eliminar de "No Encontrados" (notFound) o "Baja Confianza" (lowConfidence)
                        val newNotFound = result.notFound.filterNot { it.id == spotifyTrack.id }
                        val newLowConf = result.lowConfidence.filterNot { it.spotify.id == spotifyTrack.id }

                        // Crear la entrada de track exitoso (MatchedTrack)
                        val newMatchedTrack = MatchedTrack(
                            spotify = spotifyTrack,
                            tidal = tidalTrack,
                            method = MatchMethod.ManualMapping,
                            score = 1.0
                        )
                        val newMatched = result.matched + newMatchedTrack

                        val newResult = result.copy(
                            matched = newMatched,
                            notFound = newNotFound,
                            lowConfidence = newLowConf
                        )

                        val newProgress = progress.copy(
                            matched = newMatched.size,
                            notFound = newNotFound.size,
                            lowConfidence = newLowConf.size,
                            notFoundTracks = newNotFound,
                            lowConfTracks = newLowConf,
                            result = newResult
                        )

                        currentState.copy(progress = newProgress)
                    } else {
                        currentState
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Error al mapear track: ${e.message}") }
            }
        }
    }

    fun cancel() {
        syncJob?.cancel()
        _state.update { it.copy(progress = null, allProgress = null) }
    }
}
