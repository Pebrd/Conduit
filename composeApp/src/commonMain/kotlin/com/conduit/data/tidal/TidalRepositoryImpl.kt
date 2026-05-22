package com.conduit.data.tidal

import com.conduit.domain.model.Playlist
import com.conduit.domain.model.TidalTrack
import com.conduit.domain.repository.TidalAlbum
import com.conduit.domain.repository.TidalRepository

import com.conduit.data.local.SettingsStorage

class TidalRepositoryImpl(
    private val apiClient: TidalApiClient,
    private val settings: SettingsStorage
) : TidalRepository {
    override suspend fun getPlaylists(): List<Playlist> = apiClient.getPlaylists()
    
    override suspend fun getPlaylistTrackIds(playlistId: String): Set<String> = apiClient.getPlaylistTrackIds(playlistId)
    override suspend fun getPlaylistExistingTracks(playlistId: String): Pair<Set<String>, Set<String>> = apiClient.getPlaylistExistingTracks(playlistId)

    override suspend fun createPlaylist(name: String, description: String): String {
        return apiClient.createPlaylist(name, description)
    }

    override suspend fun updatePlaylistDescription(playlistId: String, description: String) {
        apiClient.updatePlaylistDescription(playlistId, description)
    }

    override suspend fun findOrCreatePlaylist(
        name: String,
        spotifyPlaylistId: String,
    ): String {
        // Paso 0: buscar en el almacenamiento local y verificar que exista
        val localMappedId = settings.getMappedTidalPlaylist(spotifyPlaylistId)
        if (localMappedId != null) {
            if (apiClient.playlistExists(localMappedId)) {
                return localMappedId
            } else {
                settings.removeMappedTidalPlaylist(spotifyPlaylistId)
            }
        }

        val allPlaylists = apiClient.getPlaylists()

        // Paso 1: buscar por ID en descripción
        val byDescription = allPlaylists.firstOrNull { playlist ->
            playlist.description?.contains("conduit:spotify:$spotifyPlaylistId") == true
        }
        if (byDescription != null) {
            settings.saveMappedTidalPlaylist(spotifyPlaylistId, byDescription.id)
            return byDescription.id
        }

        // Paso 2: buscar por nombre exacto (fallback)
        val byName = allPlaylists.firstOrNull { playlist ->
            playlist.name.trim().equals(name.trim(), ignoreCase = true)
        }
        if (byName != null) {
            // Actualizar descripción para futuras búsquedas
            apiClient.updatePlaylistDescription(
                playlistId  = byName.id,
                description = "conduit:spotify:$spotifyPlaylistId",
            )
            settings.saveMappedTidalPlaylist(spotifyPlaylistId, byName.id)
            return byName.id
        }

        // Paso 3: crear nueva playlist
        val newPlaylistId = apiClient.createPlaylist(
            name        = name,
            description = "conduit:spotify:$spotifyPlaylistId",
        )
        settings.saveMappedTidalPlaylist(spotifyPlaylistId, newPlaylistId)
        return newPlaylistId
    }

    override suspend fun addTracksToPlaylist(playlistId: String, tidalTrackIds: List<String>) {
        apiClient.addTracksToPlaylist(playlistId, tidalTrackIds)
    }

    override suspend fun searchByIsrc(isrc: String): TidalTrack? = apiClient.searchByIsrc(isrc)

    override suspend fun searchTracks(query: String, limit: Int): List<TidalTrack> = apiClient.searchTracks(query, limit)

    override suspend fun searchAlbums(query: String, limit: Int): List<TidalAlbum> =
        apiClient.searchAlbums(query, limit)

    override suspend fun getAlbumTracks(albumId: String): List<TidalTrack> =
        apiClient.getAlbumTracks(albumId)

    override suspend fun getTrack(id: String): TidalTrack? =
        apiClient.getTrack(id)

    override fun getMappedPlaylist(spotifyPlaylistId: String): String? =
        settings.getMappedTidalPlaylist(spotifyPlaylistId)

    override fun removeMappedPlaylist(spotifyPlaylistId: String) {
        settings.removeMappedTidalPlaylist(spotifyPlaylistId)
    }

    override suspend fun playlistExists(playlistId: String): Boolean =
        apiClient.playlistExists(playlistId)
}
