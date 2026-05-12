package com.spotitidal.data.spotify

import com.spotitidal.domain.model.Playlist
import com.spotitidal.domain.model.Track
import com.spotitidal.domain.repository.SpotifyRepository

class SpotifyRepositoryImpl(
    private val apiClient: SpotifyApiClient
) : SpotifyRepository {
    override suspend fun getPlaylists(): List<Playlist> = apiClient.getPlaylists()
    override suspend fun getPlaylistTracks(playlistId: String): List<Track> = apiClient.getPlaylistTracks(playlistId)
    override suspend fun getLikedSongs(): List<Track> = apiClient.getLikedSongs()
}
