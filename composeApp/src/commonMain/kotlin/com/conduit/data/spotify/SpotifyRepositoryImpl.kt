package com.conduit.data.spotify

import com.conduit.domain.model.Playlist
import com.conduit.domain.model.Track
import com.conduit.domain.repository.SpotifyRepository

class SpotifyRepositoryImpl(
    private val apiClient: SpotifyApiClient
) : SpotifyRepository {
    override suspend fun getPlaylists(): List<Playlist> = apiClient.getPlaylists()
    override suspend fun getPlaylistTracks(playlistId: String): List<Track> = apiClient.getPlaylistTracks(playlistId)
    override suspend fun getLikedSongs(): List<Track> = apiClient.getLikedSongs()
}
