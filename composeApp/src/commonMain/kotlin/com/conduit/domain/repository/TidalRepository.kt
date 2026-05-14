package com.conduit.domain.repository

import com.conduit.domain.model.Playlist
import com.conduit.domain.model.TidalTrack

interface TidalRepository {
    suspend fun getPlaylists(): List<Playlist>
    suspend fun getPlaylistTrackIds(playlistId: String): Set<String>
    suspend fun createPlaylist(name: String): String
    suspend fun addTracksToPlaylist(playlistId: String, tidalTrackIds: List<String>)
    suspend fun searchByIsrc(isrc: String): TidalTrack?
    suspend fun searchTracks(query: String, limit: Int = 10): List<TidalTrack>
    suspend fun searchAlbums(query: String, limit: Int = 3): List<TidalAlbum>
    suspend fun getAlbumTracks(albumId: String): List<TidalTrack>
    suspend fun getTrack(id: String): TidalTrack?
}

data class TidalAlbum(val id: String, val name: String, val artist: String)
