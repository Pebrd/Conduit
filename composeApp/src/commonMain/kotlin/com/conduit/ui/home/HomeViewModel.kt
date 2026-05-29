package com.conduit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.data.local.HistoryStorage
import com.conduit.domain.model.Playlist
import com.conduit.domain.repository.TidalRepository
import com.conduit.domain.usecase.GetPlaylistsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val playlists: List<Playlist> = emptyList(),
    val syncedPlaylistIds: Set<String> = emptySet(),
    val needsSyncPlaylistIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val getPlaylistsUseCase: GetPlaylistsUseCase,
    private val tidalRepository: TidalRepository,
    private val historyStorage: HistoryStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val playlists = getPlaylistsUseCase()
                val playlistsById = playlists.associateBy { it.id }

                // Check which playlists have a Tidal mapping (non-blocking on main)
                val (syncedIds, needsSyncIds) = withContext(Dispatchers.Default) {
                    val synced = playlists.filter { tidalRepository.getMappedPlaylist(it.id) != null }
                        .map { it.id }
                        .toSet()

                    // Compare last sync track count with current Spotify track count
                    val history = historyStorage.getHistory()
                    val latestSyncByPlaylist = mutableMapOf<String, com.conduit.domain.model.SyncResult>()
                    for (result in history) {
                        if (result.playlistId !in latestSyncByPlaylist) {
                            latestSyncByPlaylist[result.playlistId] = result
                        }
                    }

                    val needsSync = synced.filter { id ->
                        val latest = latestSyncByPlaylist[id] ?: return@filter true // no history → needs sync
                        val spotifyTotal = playlistsById[id]?.trackCount ?: return@filter true
                        val syncedTotal = latest.matched.size + latest.notFound.size +
                            latest.lowConfidence.size + latest.duplicates.size + latest.blacklisted.size
                        spotifyTotal != syncedTotal
                    }.toSet()

                    Pair(synced, needsSync)
                }

                _uiState.update {
                    it.copy(
                        playlists = playlists,
                        syncedPlaylistIds = syncedIds,
                        needsSyncPlaylistIds = needsSyncIds,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
