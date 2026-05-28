package com.conduit.domain.repository

import com.conduit.domain.model.*

interface SpotifyRepository {
    suspend fun getPlaylists(): List<Playlist>
    suspend fun getPlaylistTracks(playlistId: String): List<Track>
    suspend fun getLikedSongs(): List<Track>

    // Stats
    suspend fun getProfile(): UserProfile
    suspend fun getTopArtists(timeRange: String, limit: Int): List<TopArtistItem>
    suspend fun getTopTracks(timeRange: String, limit: Int): List<TopTrackItem>
    suspend fun getRecentlyPlayed(limit: Int): List<RecentlyPlayedItem>
    suspend fun getAudioFeatures(trackIds: List<String>): List<AudioFeaturesItem>
    suspend fun getArtistTopTracks(artistId: String): List<TopTrackItem>
    suspend fun getArtistDetail(artistId: String): TopArtistItem?
    suspend fun getTrack(trackId: String): TopTrackItem?

    // Discover
    suspend fun searchTrack(name: String, artist: String): Track?
    suspend fun searchByIsrc(isrc: String): Track?
    suspend fun createPlaylist(name: String): String
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String)
}
