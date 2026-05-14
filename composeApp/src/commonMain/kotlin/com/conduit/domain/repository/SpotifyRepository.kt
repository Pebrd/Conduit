package com.conduit.domain.repository

import com.conduit.domain.model.Playlist
import com.conduit.domain.model.Track

interface SpotifyRepository {
    suspend fun getPlaylists(): List<Playlist>
    suspend fun getPlaylistTracks(playlistId: String): List<Track>
    suspend fun getLikedSongs(): List<Track>
}
