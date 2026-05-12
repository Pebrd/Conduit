package com.spotitidal.data.tidal

import com.spotitidal.domain.model.Playlist
import com.spotitidal.domain.model.TidalTrack
import com.spotitidal.domain.model.MusicService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

import com.spotitidal.data.local.TokenStorage

class TidalApiClient(
    private val client: HttpClient,
    private val tokenStorage: TokenStorage
) {

    suspend fun getPlaylists(): List<Playlist> {
        val token = tokenStorage.getAccessToken("tidal") ?: return emptyList()
        val response = client.get("https://api.tidal.com/v1/users/me/playlists") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (response.status != HttpStatusCode.OK) return emptyList()
        val data = response.body<TidalPlaylistsResponse>()
        return data.items.map { it.toDomain() }
    }

    suspend fun getPlaylistTrackIds(playlistId: String): Set<String> {
        val token = tokenStorage.getAccessToken("tidal") ?: return emptySet()
        val response = client.get("https://api.tidal.com/v1/playlists/$playlistId/items") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (response.status != HttpStatusCode.OK) return emptySet()
        val data = response.body<TidalPlaylistItemsResponse>()
        return data.items.map { it.item.id }.toSet()
    }

    suspend fun searchTracks(query: String, limit: Int = 10): List<TidalTrack> {
        val token = tokenStorage.getAccessToken("tidal") ?: return emptyList()
        val response = client.get("https://api.tidal.com/v1/search/tracks") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("query", query)
            parameter("limit", limit)
        }
        if (response.status != HttpStatusCode.OK) return emptyList()
        val data = response.body<TidalTracksResponse>()
        return data.items.map { it.toDomain() }
    }

    suspend fun searchByIsrc(isrc: String): TidalTrack? {
        val token = tokenStorage.getAccessToken("tidal") ?: return null
        val response = client.get("https://api.tidal.com/v1/tracks") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("filter[isrc]", isrc)
        }
        if (response.status != HttpStatusCode.OK) return null
        val data = response.body<TidalTracksResponse>()
        return data.items.firstOrNull()?.toDomain()
    }
}

@Serializable
private data class TidalPlaylistsResponse(val items: List<TidalPlaylistDto>)

@Serializable
private data class TidalPlaylistDto(
    val uuid: String,
    val title: String,
    val numberOfTracks: Int,
    val image: String? = null
) {
    fun toDomain() = Playlist(
        id = uuid,
        name = title,
        trackCount = numberOfTracks,
        imageUrl = image,
        source = MusicService.TIDAL
    )
}

@Serializable
private data class TidalTracksResponse(val items: List<TidalTrackDto>)

@Serializable
private data class TidalTrackDto(
    val id: String,
    val title: String,
    val artist: TidalArtistDto,
    val album: TidalAlbumDto,
    val duration: Long
) {
    fun toDomain() = TidalTrack(
        id = id,
        name = title,
        artist = artist.name,
        album = album.title,
        durationMs = duration * 1000
    )
}

@Serializable
private data class TidalArtistDto(val name: String)

@Serializable
private data class TidalAlbumDto(val title: String)

@Serializable
private data class TidalPlaylistItemsResponse(val items: List<TidalPlaylistItemDto>)

@Serializable
private data class TidalPlaylistItemDto(val item: TidalTrackDto)
