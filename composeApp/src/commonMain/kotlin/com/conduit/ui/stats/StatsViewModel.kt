package com.conduit.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.domain.model.*
import com.conduit.domain.usecase.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsDashboardState(
    val profile: UserProfile? = null,
    val topArtists: List<TopArtistItem> = emptyList(),
    val topTracks: List<TopTrackItem> = emptyList(),
    val recentlyPlayed: List<RecentlyPlayedItem> = emptyList(),
    val moodAnalysis: MoodAnalysis? = null,
    val genreDistribution: List<GenreCount> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class TopArtistsState(
    val artists: List<TopArtistItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTimeRange: String = "medium_term",
)

data class TopTracksState(
    val tracks: List<TopTrackItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTimeRange: String = "medium_term",
)

data class RecentlyPlayedState(
    val items: List<RecentlyPlayedItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class TrackDetailState(
    val track: TopTrackItem? = null,
    val audioFeatures: AudioFeaturesItem? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class ArtistDetailState(
    val artist: TopArtistItem? = null,
    val topTracks: List<TopTrackItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class StatsViewModel(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val getTopArtistsUseCase: GetTopArtistsUseCase,
    private val getTopTracksUseCase: GetTopTracksUseCase,
    private val getRecentlyPlayedUseCase: GetRecentlyPlayedUseCase,
    private val getMoodAnalysisUseCase: GetMoodAnalysisUseCase,
    private val getArtistTopTracksUseCase: GetArtistTopTracksUseCase,
    private val getTrackAudioFeaturesUseCase: GetTrackAudioFeaturesUseCase,
) : ViewModel() {

    private val _dashboardState = MutableStateFlow(StatsDashboardState())
    val dashboardState = _dashboardState.asStateFlow()

    private val _topArtistsState = MutableStateFlow(TopArtistsState())
    val topArtistsState = _topArtistsState.asStateFlow()

    private val _topTracksState = MutableStateFlow(TopTracksState())
    val topTracksState = _topTracksState.asStateFlow()

    private val _recentlyPlayedState = MutableStateFlow(RecentlyPlayedState())
    val recentlyPlayedState = _recentlyPlayedState.asStateFlow()

    private val _trackDetailState = MutableStateFlow(TrackDetailState())
    val trackDetailState = _trackDetailState.asStateFlow()

    private val _artistDetailState = MutableStateFlow(ArtistDetailState())
    val artistDetailState = _artistDetailState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _dashboardState.update { it.copy(isLoading = true, error = null) }
            try {
                val profileDeferred = async { getUserProfileUseCase() }
                val topArtistsDeferred = async { getTopArtistsUseCase(limit = 5) }
                val topTracksDeferred = async { getTopTracksUseCase(limit = 5) }
                val recentlyPlayedDeferred = async { getRecentlyPlayedUseCase(limit = 5) }
                val moodDeferred = async { getMoodAnalysisUseCase() }
                val genresDeferred = async { getTopArtistsUseCase.getGenreDistribution(limit = 50) }

                val profile = profileDeferred.await()
                val artists = topArtistsDeferred.await()
                val tracks = topTracksDeferred.await()
                val recent = recentlyPlayedDeferred.await()
                val mood = moodDeferred.await()
                val genres = genresDeferred.await()

                _dashboardState.update {
                    it.copy(
                        profile = profile,
                        topArtists = artists,
                        topTracks = tracks,
                        recentlyPlayed = recent,
                        moodAnalysis = mood,
                        genreDistribution = genres.take(8),
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _dashboardState.update {
                    it.copy(isLoading = false, error = e.message ?: "Error loading stats")
                }
            }
        }
    }

    fun loadTopArtists(timeRange: String = "medium_term") {
        viewModelScope.launch {
            _topArtistsState.update { it.copy(isLoading = true, error = null, selectedTimeRange = timeRange) }
            try {
                val artists = getTopArtistsUseCase(timeRange, 50)
                _topArtistsState.update { it.copy(artists = artists, isLoading = false) }
            } catch (e: Exception) {
                _topArtistsState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadTopTracks(timeRange: String = "medium_term") {
        viewModelScope.launch {
            _topTracksState.update { it.copy(isLoading = true, error = null, selectedTimeRange = timeRange) }
            try {
                val tracks = getTopTracksUseCase(timeRange, 50)
                _topTracksState.update { it.copy(tracks = tracks, isLoading = false) }
            } catch (e: Exception) {
                _topTracksState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadRecentlyPlayed() {
        viewModelScope.launch {
            _recentlyPlayedState.update { it.copy(isLoading = true, error = null) }
            try {
                val items = getRecentlyPlayedUseCase(50)
                _recentlyPlayedState.update { it.copy(items = items, isLoading = false) }
            } catch (e: Exception) {
                _recentlyPlayedState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadTrackDetailById(trackId: String) {
        viewModelScope.launch {
            _trackDetailState.update { TrackDetailState(isLoading = true, error = null) }
            try {
                val track = getTopTracksUseCase.getTrackById(trackId)
                val features = if (track != null) getTrackAudioFeaturesUseCase(track.id) else null
                _trackDetailState.update {
                    TrackDetailState(track = track, audioFeatures = features, isLoading = false)
                }
            } catch (e: Exception) {
                _trackDetailState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadArtistDetailById(artistId: String) {
        viewModelScope.launch {
            _artistDetailState.update { ArtistDetailState(isLoading = true, error = null) }
            try {
                val artist = getTopArtistsUseCase.getArtistById(artistId)
                val topTracks = if (artist != null) getArtistTopTracksUseCase(artist.id) else emptyList()
                _artistDetailState.update {
                    ArtistDetailState(artist = artist, topTracks = topTracks, isLoading = false)
                }
            } catch (e: Exception) {
                _artistDetailState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun resetTrackDetail() {
        _trackDetailState.update { TrackDetailState() }
    }

    fun resetArtistDetail() {
        _artistDetailState.update { ArtistDetailState() }
    }
}
