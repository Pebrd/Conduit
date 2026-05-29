package com.conduit.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.data.itunes.ITunesClient
import com.conduit.data.itunes.toTrack
import com.conduit.domain.model.*
import com.conduit.domain.repository.SpotifyRepository
import com.conduit.domain.usecase.*
import com.conduit.platform.AudioPreviewPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── UI State ──

enum class DiscoverStep { SEED, DESTINATION, SWIPE }

data class DiscoverUiState(
    val step: DiscoverStep = DiscoverStep.SEED,
    val session: DiscoverSession? = null,
    val queue: List<DiscoverTrack> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val searchResults: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val isBuilding: Boolean = false,
    val isPlaying: Boolean = false,
    val error: String? = null,
)

// ── ViewModel ──

class DiscoverViewModel(
    private val spotifyRepo: SpotifyRepository,
    private val iTunesClient: ITunesClient,
    private val buildMoodProfile: BuildMoodProfileUseCase,
    private val findCandidates: FindCandidatesUseCase,
    private val handleLike: HandleLikeUseCase,
    private val audioPlayer: AudioPreviewPlayer,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var preloadJob: Job? = null
    private var cachedCandidates: List<DiscoverTrack>? = null

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

    // ── Real-time Spotify search (debounced) ──

    fun searchTracks(query: String) {
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false, error = null) }
            searchJob?.cancel()
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _state.update { it.copy(isSearching = true, error = null) }

            // 1) Try Spotify first (has popularity ordering + album art)
            try {
                val tracks = spotifyRepo.searchTracksByQuery(query, limit = 15)
                if (tracks.isNotEmpty()) {
                    _state.update { it.copy(searchResults = tracks, isSearching = false) }
                    return@launch
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce // propagate cancellation
            } catch (_: Exception) {
                // Spotify failed, fall through to iTunes
            }

            // 2) Fallback: iTunes Search API (no auth needed)
            try {
                val tracks = iTunesClient.searchTracks(query, limit = 15).map { it.toTrack() }
                _state.update { it.copy(searchResults = tracks, isSearching = false) }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                _state.update { it.copy(searchResults = emptyList(), isSearching = false, error = "Error de búsqueda: ${e.message}") }
            }
        }
    }

    // ── Seed selection ──

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
                preloadCandidates()
            } catch (e: Exception) {
                _state.update { it.copy(isBuilding = false, error = "Error al analizar canción: ${e.message}") }
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
                _state.update {
                    it.copy(session = session, isBuilding = false, step = DiscoverStep.DESTINATION)
                }
                preloadCandidates()
            } catch (e: Exception) {
                _state.update { it.copy(isBuilding = false, error = "Error al analizar playlist: ${e.message}") }
            }
        }
    }

    // ── Pre-load candidates in background ──

    private fun preloadCandidates() {
        preloadJob?.cancel()
        val session = _state.value.session ?: return
        preloadJob = viewModelScope.launch {
            try {
                val candidates = findCandidates.invoke(session.moodProfile, session.seenTrackMbids)
                cachedCandidates = candidates
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Silently fail — will retry in confirmDestination
            }
        }
    }

    // ── Platform & Destination ──

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

        // Use pre-loaded candidates if available
        if (cachedCandidates != null) {
            _state.update {
                it.copy(
                    session = session,
                    queue = cachedCandidates!!,
                    isBuilding = false,
                    step = DiscoverStep.SWIPE,
                )
            }
            cachedCandidates = null
            return
        }

        // Otherwise fetch synchronously (show loading)
        _state.update { it.copy(isBuilding = true) }
        startSwiping(session)
    }

    // ── Swipe ──

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

    fun autoPlay(track: DiscoverTrack) {
        if (_state.value.isPlaying) audioPlayer.stop()
        track.previewUrl?.let { url ->
            audioPlayer.play(url)
            _state.update { it.copy(isPlaying = true) }
        }
    }

    fun like(track: DiscoverTrack) {
        val session = _state.value.session?.copy() ?: return
        viewModelScope.launch {
            delay(250) // let swipe animation finish
            nextCard(track, liked = true)
        }
        viewModelScope.launch {
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
        }
    }

    fun skip(track: DiscoverTrack) {
        viewModelScope.launch {
            delay(250) // let swipe animation finish
            nextCard(track, liked = false)
        }
    }

    private fun nextCard(track: DiscoverTrack, liked: Boolean) {
        audioPlayer.stop() // stop old preview
        _state.update { current ->
            val newSeen = current.session?.seenTrackMbids?.plus(track.mbid) ?: setOf(track.mbid)
            val newLiked = if (liked) (current.session?.likedTracks?.plus(track) ?: listOf(track)) else (current.session?.likedTracks ?: emptyList())
            val newQueue = current.queue.drop(1)

            if (newQueue.size < 5) viewModelScope.launch { refillQueue() }

            current.copy(
                session = current.session?.copy(seenTrackMbids = newSeen, likedTracks = newLiked),
                queue = newQueue,
                // isPlaying not reset — autoPlay via LaunchedEffect handles it
            )
        }
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
