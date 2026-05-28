package com.conduit.data.spotify

import com.conduit.domain.model.*
import com.conduit.domain.repository.SpotifyRepository

class SpotifyRepositoryImpl(
    private val apiClient: SpotifyApiClient
) : SpotifyRepository {
    override suspend fun getPlaylists(): List<Playlist> = apiClient.getPlaylists()
    override suspend fun getPlaylistTracks(playlistId: String): List<Track> = apiClient.getPlaylistTracks(playlistId)
    override suspend fun getLikedSongs(): List<Track> = apiClient.getLikedSongs()

    override suspend fun getProfile(): UserProfile = apiClient.getProfile()
    override suspend fun getTopArtists(timeRange: String, limit: Int): List<TopArtistItem> =
        apiClient.getTopArtists(timeRange, limit)
    override suspend fun getTopTracks(timeRange: String, limit: Int): List<TopTrackItem> =
        apiClient.getTopTracks(timeRange, limit)
    override suspend fun getRecentlyPlayed(limit: Int): List<RecentlyPlayedItem> =
        apiClient.getRecentlyPlayed(limit)
    override suspend fun getAudioFeatures(trackIds: List<String>): List<AudioFeaturesItem> =
        apiClient.getAudioFeatures(trackIds)
    override suspend fun getArtistTopTracks(artistId: String): List<TopTrackItem> =
        apiClient.getArtistTopTracks(artistId)
    override suspend fun getArtistDetail(artistId: String): TopArtistItem? =
        apiClient.getArtistDetail(artistId)
    override suspend fun getTrack(trackId: String): TopTrackItem? =
        apiClient.getTrack(trackId)

    // Discover
    override suspend fun searchTrack(name: String, artist: String): Track? =
        apiClient.searchTrack(name, artist)
    override suspend fun searchTracksByQuery(query: String, limit: Int): List<Track> =
        apiClient.searchTracksByQuery(query, limit)
    override suspend fun searchByIsrc(isrc: String): Track? =
        apiClient.searchByIsrc(isrc)
    override suspend fun createPlaylist(name: String): String =
        apiClient.createPlaylist(name)
    override suspend fun addTrackToPlaylist(playlistId: String, trackId: String) =
        apiClient.addTrackToPlaylist(playlistId, trackId)
}
