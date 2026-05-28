package com.conduit.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.domain.model.*
import com.conduit.domain.repository.SpotifyRepository
import com.conduit.domain.usecase.*
import com.conduit.platform.AudioPreviewPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DiscoverStep { SEED, DESTINATION, SWIPE }

data class DiscoverUiState(
    val step: DiscoverStep = DiscoverStep.SEED,
    val session: DiscoverSession? = null,
    val queue: List<DiscoverTrack> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isBuilding: Boolean = false,
    val isPlaying: Boolean = false,
    val error: String? = null,
)

class DiscoverViewModel(
    private val spotifyRepo: SpotifyRepository,
    private val buildMoodProfile: BuildMoodProfileUseCase,
    private val findCandidates: FindCandidatesUseCase,
    private val handleLike: HandleLikeUseCase,
    private val audioPlayer: AudioPreviewPlayer,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val playlists = spotifyRepo.getPlaylists()
                _state.update { it.copy(playlists = playlists) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Error al cargar playlists: ${e.message}") }
            }
        }
    }

    fun selectSeedPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            _state.update { it.copy(isBuilding = true, error = null) }
            try {
                val profile = buildMoodProfile.fromPlaylist(playlist.id)
                val session = DiscoverSession(
                    seed = DiscoverSeed.FromPlaylist(playlist.id, playlist.name, playlist.imageUrl),
                    destination = DiscoverDestination(
                        platform = MusicService.SPOTIFY,
                        playlistId = playlist.id,
                        playlistName = playlist.name,
                    ),
                    moodProfile = profile,
                )
                startSwiping(session)
            } catch (e: Exception) {
                _state.update { it.copy(isBuilding = false, error = "Error al analizar playlist: ${e.message}") }
            }
        }
    }

    fun selectSeedTrack(track: Track) {
        viewModelScope.launch {
            _state.update { it.copy(isBuilding = true, error = null) }
            try {
                val profile = buildMoodProfile.fromTrack(track)
                val playlistName = generatePlaylistName(DiscoverSeed.FromTrack(
                    trackId = track.id,
                    trackName = track.name,
                    artist = track.artist,
                    isrc = track.isrc,
                ))
                val session = DiscoverSession(
                    seed = DiscoverSeed.FromTrack(track.id, track.name, track.artist, track.isrc),
                    destination = DiscoverDestination(
                        platform = MusicService.SPOTIFY,
                        playlistId = null,
                        playlistName = playlistName,
                    ),
                    moodProfile = profile,
                )
                _state.update {
                    it.copy(session = session, isBuilding = false, step = DiscoverStep.DESTINATION)
                }
            } catch (e: Exception) {
                _state.update { it.copy(isBuilding = false, error = "Error al analizar canci\u00f3n: ${e.message}") }
            }
        }
    }

    fun setPlatform(platform: MusicService) {
        _state.update { current ->
            current.copy(
                session = current.session?.copy(
                    destination = current.session.destination.copy(platform = platform)
                )
            )
        }
    }

    fun updateDestination(playlistId: String, playlistName: String) {
        _state.update { current ->
            current.copy(
                session = current.session?.copy(
                    destination = current.session.destination.copy(
                        playlistId = playlistId,
                        playlistName = playlistName,
                    )
                )
            )
        }
    }

    fun confirmDestination() {
        val session = _state.value.session ?: return
        startSwiping(session)
    }

    private fun startSwiping(session: DiscoverSession) {
        viewModelScope.launch {
            try {
                val candidates = findCandidates.invoke(session.moodProfile, session.seenTrackMbids)
                _state.update {
                    it.copy(
                        session = session,
                        queue = candidates,
                        isBuilding = false,
                        step = DiscoverStep.SWIPE,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isBuilding = false, error = "Error al buscar candidatos: ${e.message}") }
            }
        }
    }

    fun like(track: DiscoverTrack) {
        viewModelScope.launch {
            val session = _state.value.session ?: return@launch
            try {
                handleLike.invoke(track, session) { newPlaylistId ->
                    _state.update { current ->
                        current.copy(
                            session = current.session?.copy(
                                destination = current.session.destination.copy(playlistId = newPlaylistId)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Error al guardar: ${e.message}") }
            }
            nextCard(track, liked = true)
        }
    }

    fun skip(track: DiscoverTrack) {
        nextCard(track, liked = false)
    }

    private fun nextCard(track: DiscoverTrack, liked: Boolean) {
        _state.update { current ->
            val newSeen = current.session?.seenTrackMbids?.plus(track.mbid) ?: setOf(track.mbid)
            val newLiked = if (liked) (current.session?.likedTracks?.plus(track) ?: listOf(track)) else (current.session?.likedTracks ?: emptyList())
            val newQueue = current.queue.drop(1)

            if (newQueue.size < 5) viewModelScope.launch { refillQueue() }

            current.copy(
                session = current.session?.copy(seenTrackMbids = newSeen, likedTracks = newLiked),
                queue = newQueue,
                isPlaying = false,
            )
        }
        audioPlayer.stop()
    }

    fun togglePreview(track: DiscoverTrack) {
        if (_state.value.isPlaying) {
            audioPlayer.pause()
            _state.update { it.copy(isPlaying = false) }
        } else {
            track.previewUrl?.let { audioPlayer.play(it) }
            _state.update { it.copy(isPlaying = true) }
        }
    }

    fun reset() {
        _state.value = DiscoverUiState(playlists = _state.value.playlists)
        audioPlayer.stop()
    }

    private suspend fun refillQueue() {
        val session = _state.value.session ?: return
        if (session.moodProfile.genres.isEmpty()) return
        val more = findCandidates.invoke(session.moodProfile, session.seenTrackMbids, limit = 20)
        _state.update { current ->
            current.copy(queue = current.queue + more)
        }
    }
}
