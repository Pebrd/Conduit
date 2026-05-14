package com.conduit.data.tidal

import com.conduit.domain.model.Playlist
import com.conduit.domain.model.TidalTrack
import com.conduit.domain.model.MusicService
import com.conduit.data.local.SettingsStorage
import com.conduit.Credentials
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import com.conduit.data.http.TokenRefreshPlugin
import com.conduit.data.http.withRetry

class TidalApiClient(
    private val client: HttpClient,
    private val tokenRefreshPlugin: TokenRefreshPlugin,
    private val settingsStorage: SettingsStorage
) {

    private fun getEffectiveClientId(): String {
        return settingsStorage.tidalClientId.takeIf { it.isNotBlank() } ?: Credentials.TIDAL_CLIENT_ID
    }

    suspend fun getPlaylists(): List<Playlist> = buildList {
        try {
            // Leemos el token en cada llamada para asegurar que sea válido y actual
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val clientId = getEffectiveClientId()
            
            val response = client.get("https://openapi.tidal.com/v2/my-playlists") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("X-Tidal-Token", clientId)
            }
            
            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.bodyAsText()
                println("DEBUG: Tidal API v2 Error: ${response.status} - $errorBody")
                return@buildList
            }
            
            val page = response.body<TidalV2PlaylistsResponse>()
            addAll(page.data.map { it.toDomain() })
        } catch (e: Exception) {
            println("DEBUG: Tidal getPlaylists failed: ${e.message}")
        }
    }

    suspend fun getPlaylistTrackIds(playlistId: String): Set<String> {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val clientId = getEffectiveClientId()
            
            val response = client.get("https://openapi.tidal.com/v2/playlists/$playlistId/items") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("X-Tidal-Token", clientId)
            }
            if (response.status != HttpStatusCode.OK) return emptySet()
            
            val page = response.body<TidalV2PlaylistItemsResponse>()
            page.data.map { it.id }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    suspend fun searchTracks(query: String, limit: Int = 10): List<TidalTrack> {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val clientId = getEffectiveClientId()
            
            val response = client.get("https://openapi.tidal.com/v2/search/tracks") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("X-Tidal-Token", clientId)
                parameter("query", query)
                parameter("limit", limit)
            }
            if (response.status != HttpStatusCode.OK) return emptyList()
            
            val page = response.body<TidalV2TracksResponse>()
            page.data.map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchByIsrc(isrc: String): TidalTrack? {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val clientId = getEffectiveClientId()
            
            val response = client.get("https://openapi.tidal.com/v2/tracks") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("X-Tidal-Token", clientId)
                parameter("filter[isrc]", isrc)
            }
            if (response.status != HttpStatusCode.OK) return null
            
            val page = response.body<TidalV2TracksResponse>()
            page.data.firstOrNull()?.toDomain()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun addTracksToPlaylist(playlistId: String, tidalTrackIds: List<String>) {
        try {
            val clientId = getEffectiveClientId()
            
            tidalTrackIds.chunked(50).forEach { batch ->
                withRetry {
                    val token = tokenRefreshPlugin.getValidToken("tidal")
                    client.post("https://openapi.tidal.com/v2/playlists/$playlistId/items") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        header("X-Tidal-Token", clientId)
                        contentType(ContentType.Application.Json)
                        setBody(TidalV2AddItemsRequest(data = batch.map { TidalV2ItemReference(id = it, type = "track") }))
                    }
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Tidal addTracks failed: ${e.message}")
        }
    }
}

// DTOs para Tidal OpenAPI v2
@Serializable
private data class TidalV2PlaylistsResponse(val data: List<TidalV2PlaylistDto>)

@Serializable
private data class TidalV2PlaylistDto(
    val id: String,
    val attributes: TidalV2PlaylistAttributes
) {
    fun toDomain() = Playlist(
        id = id,
        name = attributes.title,
        trackCount = attributes.numberOfTracks,
        imageUrl = null, // v2 maneja las imágenes como recursos relacionados
        source = MusicService.TIDAL
    )
}

@Serializable
private data class TidalV2PlaylistAttributes(
    val title: String,
    val numberOfTracks: Int
)

@Serializable
private data class TidalV2TracksResponse(val data: List<TidalV2TrackDto>)

@Serializable
private data class TidalV2TrackDto(
    val id: String,
    val attributes: TidalV2TrackAttributes
) {
    fun toDomain() = TidalTrack(
        id = id,
        name = attributes.title,
        artist = attributes.artistName ?: "Unknown Artist",
        album = attributes.albumTitle ?: "Unknown Album",
        durationMs = attributes.duration ?: 0L
    )
}

@Serializable
private data class TidalV2TrackAttributes(
    val title: String,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val duration: Long? = null
)

@Serializable
private data class TidalV2PlaylistItemsResponse(val data: List<TidalV2ItemReference>)

@Serializable
private data class TidalV2AddItemsRequest(val data: List<TidalV2ItemReference>)

@Serializable
private data class TidalV2ItemReference(val id: String, val type: String)
