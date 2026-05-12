package com.spotitidal.domain.repository

import com.spotitidal.domain.model.Playlist
import com.spotitidal.domain.model.Track

interface SpotifyRepository {
    suspend fun getPlaylists(): List<Playlist>
    suspend fun getPlaylistTracks(playlistId: String): List<Track>
    suspend fun getLikedSongs(): List<Track>
}
