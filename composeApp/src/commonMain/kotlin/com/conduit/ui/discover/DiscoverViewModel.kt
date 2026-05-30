package com.conduit.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.data.http.RateLimitException
import com.conduit.data.itunes.ITunesClient
import com.conduit.data.itunes.toTrack
import com.conduit.data.lastfm.LastFmClient
import com.conduit.data.local.SettingsStorage
import com.conduit.domain.model.*
import com.conduit.domain.repository.SpotifyRepository
import com.conduit.domain.usecase.*
import com.conduit.domain.util.TrackNormalizer
import com.conduit.platform.AudioPreviewPlayer
import kotlinx.coroutines.Job
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.async
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
    val skippedArtists: Set<String> = emptySet(),
    val isRefilling: Boolean = false,
)

// ── ViewModel ──

class DiscoverViewModel(
    private val spotifyRepo: SpotifyRepository,
    private val iTunesClient: ITunesClient,
    private val lastFmClient: LastFmClient,
    private val settingsStorage: SettingsStorage,
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
    private var currentSeedKey: String? = null

    init {
        lastFmClient.setApiKey(settingsStorage.lastfmApiKey)
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

    // ── Real-time search (debounced) ──

    fun searchTracks(query: String) {
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false, error = null) }
            searchJob?.cancel()
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(isSearching = true, error = null) }

            try {
                val tracks = spotifyRepo.searchTracksByQuery(query)
                if (tracks.isNotEmpty()) {
                    _state.update { it.copy(searchResults = tracks, isSearching = false) }
                    return@launch
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Exception) { }

            try {
                val tracks = iTunesClient.searchTracks(query).map { it.toTrack() }
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
                val seedTracks = buildMoodProfile.fromTrack(track)
                val seedTrackIds = seedTracks.map { it.id }
                val playlistName = generatePlaylistName(DiscoverSeed.FromTrack(
                    trackId = track.id,
                    trackName = track.name,
                    artist = track.artist,
                    isrc = track.isrc,
                ))
                // Load persisted seen track IDs for this seed
                currentSeedKey = "discover_seen_track_${track.id}"
                val persistedIds = settingsStorage.getString(currentSeedKey!!)
                val persistedSet = if (persistedIds != null) {
                    try { Json.decodeFromString<List<String>>(persistedIds).toSet() } catch (_: Exception) { emptySet() }
                } else emptySet()

                val recSource = if (lastFmClient.hasApiKey()) "Last.fm" else "Deezer"
                val session = DiscoverSession(
                    seed = DiscoverSeed.FromTrack(track.id, track.name, track.artist, track.isrc),
                    destination = DiscoverDestination(
                        platform = MusicService.SPOTIFY,
                        playlistId = null,
                        playlistName = playlistName,
                    ),
                    seedTrackIds = seedTrackIds,
                    seedArtistName = track.artist.split(",").first().trim(),
                    recommendationSource = recSource,
                    seenTrackIds = seedTrackIds.toSet() + persistedSet,
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
                val seedTracks = buildMoodProfile.fromPlaylist(playlist.id)
                println("[Conduit] DiscoverVM: fromPlaylist returned ${seedTracks.size} tracks")
                val seedTrackIds = seedTracks.map { it.id }
                println("[Conduit] DiscoverVM: seedTrackIds=${seedTrackIds.take(5)}...")
                val firstArtist = seedTracks.firstOrNull()?.artist?.split(",")?.first()?.trim() ?: ""
                println("[Conduit] DiscoverVM: firstArtist='$firstArtist'")
                // Load persisted seen track IDs for this seed
                currentSeedKey = "discover_seen_playlist_${playlist.id}"
                val persistedIds = settingsStorage.getString(currentSeedKey!!)
                val persistedSet = if (persistedIds != null) {
                    try { Json.decodeFromString<List<String>>(persistedIds).toSet() } catch (_: Exception) { emptySet() }
                } else emptySet()

                val recSource = if (lastFmClient.hasApiKey()) "Last.fm" else "Deezer"
                val session = DiscoverSession(
                    seed = DiscoverSeed.FromPlaylist(playlist.id, playlist.name, playlist.imageUrl),
                    destination = DiscoverDestination(
                        platform = MusicService.SPOTIFY,
                        playlistId = playlist.id,
                        playlistName = playlist.name,
                    ),
                    seedTrackIds = seedTrackIds,
                    seedArtistName = firstArtist,
                    recommendationSource = recSource,
                    seenTrackIds = seedTrackIds.toSet() + persistedSet,
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
                val candidates = findCandidates.invoke(session.seedTrackIds, session.seedArtistName, session.seenTrackIds, alreadySeenNames = session.seenTrackNames)
                println("[Conduit] DiscoverVM: preloadCandidates returned ${candidates.size} tracks")
                cachedCandidates = candidates
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: RateLimitException) {
                _state.update { it.copy(error = "Límite de API alcanzado: ${e.message}. Esperá un minuto y volvé a intentar.") }
            } catch (_: Exception) { }
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

        if (session.destination.playlistId != null) {
            // Destination is an existing playlist — run getPlaylistTracks AND findCandidates in parallel
            viewModelScope.launch {
                _state.update { it.copy(isBuilding = true) }
                try {
                    val existingTracksDeferred = async {
                        spotifyRepo.getPlaylistTracks(session.destination.playlistId!!)
                    }
                    val candidatesDeferred = async {
                        findCandidates.invoke(
                            session.seedTrackIds,
                            session.seedArtistName,
                            session.seenTrackIds,
                            alreadySeenNames = session.seenTrackNames,
                        )
                    }

                    val existingTracks = existingTracksDeferred.await()
                    val existingIds = existingTracks.map { it.id }.toSet()
                    val existingNames = existingTracks.map { TrackNormalizer.normalize(it) }.toSet()
                    val updatedSeenIds = session.seenTrackIds + existingIds
                    val updatedSeenNames = session.seenTrackNames + existingNames

                    val candidates = candidatesDeferred.await()
                    val skipped = _state.value.skippedArtists
                    val filtered = candidates
                        .filter { it.mbid !in updatedSeenIds }
                        .filter { it.artist.lowercase().trim() !in skipped }

                    val updatedSession = session.copy(
                        seenTrackIds = updatedSeenIds,
                        seenTrackNames = updatedSeenNames,
                    )

                    _state.update {
                        it.copy(
                            session = updatedSession,
                            queue = filtered,
                            isBuilding = false,
                            step = DiscoverStep.SWIPE,
                        )
                    }
                } catch (_: Exception) {
                    startSwiping(session)
                }
            }
            return
        }

        // New playlist — use cached candidates if available, else fetch fresh
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

        _state.update { it.copy(isBuilding = true) }
        startSwiping(session)
    }

    // ── Swipe ──

    private fun startSwiping(session: DiscoverSession) {
        viewModelScope.launch {
            try {
                val candidates = findCandidates.invoke(session.seedTrackIds, session.seedArtistName, session.seenTrackIds, alreadySeenNames = session.seenTrackNames)
                val skipped = _state.value.skippedArtists
                val filtered = candidates.filter { it.artist.lowercase().trim() !in skipped }
                _state.update {
                    it.copy(
                        session = session,
                        queue = filtered,
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
        nextCard(track, liked = true)
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
        _state.update { it.copy(skippedArtists = it.skippedArtists + track.artist.lowercase().trim()) }
        nextCard(track, liked = false)
    }

    private fun nextCard(track: DiscoverTrack, liked: Boolean) {
        audioPlayer.stop()
        val normalizedName = TrackNormalizer.normalize(track)
        val prevSession = _state.value.session
        val newSeen = (prevSession?.seenTrackIds ?: emptySet()) + track.mbid
        val newSeenNames = (prevSession?.seenTrackNames ?: emptySet()) + normalizedName
        val newLiked = if (liked) (prevSession?.likedTracks ?: emptyList()) + track else (prevSession?.likedTracks ?: emptyList())
        val newQueue = _state.value.queue.drop(1)

        // Trigger refill BEFORE state update if running low
        val currentSize = _state.value.queue.size
        if (currentSize <= 11 && currentSize > 0) {
            viewModelScope.launch { refillQueue() }
        }

        _state.update { current ->
            current.copy(
                session = current.session?.copy(
                    seenTrackIds = newSeen,
                    seenTrackNames = newSeenNames,
                    likedTracks = newLiked,
                ),
                queue = newQueue,
            )
        }

        currentSeedKey?.let { key ->
            settingsStorage.putString(key, Json.encodeToString(newSeen.toList()))
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
        if (session.seedTrackIds.isEmpty()) return
        _state.update { it.copy(isRefilling = true) }
        try {
            val likedSeeds = session.likedTracks.takeLast(5).map { it.mbid }
            val allSeeds = session.seedTrackIds + likedSeeds
            val more = findCandidates.refill(allSeeds, session.seedArtistName, session.seenTrackIds, alreadySeenNames = session.seenTrackNames, limit = 20)
            _state.update { current ->
                val filteredMore = more.filter { it.artist.lowercase().trim() !in current.skippedArtists }
                current.copy(queue = current.queue + filteredMore, isRefilling = false)
            }
        } catch (e: RateLimitException) {
            _state.update { it.copy(isRefilling = false, error = "Límite de API alcanzado al buscar más canciones. Esperá un minuto.") }
        } catch (_: Exception) {
            _state.update { it.copy(isRefilling = false) }
        }
    }
}
