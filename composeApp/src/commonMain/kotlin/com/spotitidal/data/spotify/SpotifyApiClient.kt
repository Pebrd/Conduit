package com.spotitidal.data.spotify

import com.spotitidal.domain.model.Playlist
import com.spotitidal.domain.model.Track
import com.spotitidal.domain.model.MusicService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import com.spotitidal.data.local.TokenStorage

class SpotifyApiClient(
    private val client: HttpClient,
    private val tokenStorage: TokenStorage
) {

    suspend fun getPlaylists(): List<Playlist> {
        val token = tokenStorage.getAccessToken("spotify") ?: return emptyList()
        val response = client.get("https://api.spotify.com/v1/me/playlists") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (response.status != HttpStatusCode.OK) return emptyList()
        val data = response.body<SpotifyPlaylistsResponse>()
        return data.items.map { it.toDomain() }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<Track> {
        val token = tokenStorage.getAccessToken("spotify") ?: return emptyList()
        val response = client.get("https://api.spotify.com/v1/playlists/$playlistId/tracks") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (response.status != HttpStatusCode.OK) return emptyList()
        val data = response.body<SpotifyTracksResponse>()
        return data.items.map { it.track.toDomain() }
    }

    suspend fun getLikedSongs(): List<Track> {
        val token = tokenStorage.getAccessToken("spotify") ?: return emptyList()
        val response = client.get("https://api.spotify.com/v1/me/tracks") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (response.status != HttpStatusCode.OK) return emptyList()
        val data = response.body<SpotifyLikedTracksResponse>()
        return data.items.map { it.track.toDomain() }
    }
}

@Serializable
private data class SpotifyPlaylistsResponse(val items: List<SpotifyPlaylistDto>)

@Serializable
private data class SpotifyPlaylistDto(
    val id: String,
    val name: String,
    val tracks: SpotifyTracksInfo,
    val images: List<SpotifyImageDto>? = null
) {
    fun toDomain() = Playlist(
        id = id,
        name = name,
        trackCount = tracks.total,
        imageUrl = images?.firstOrNull()?.url,
        source = MusicService.SPOTIFY
    )
}

@Serializable
private data class SpotifyTracksInfo(val total: Int)

@Serializable
private data class SpotifyImageDto(val url: String)

@Serializable
private data class SpotifyTracksResponse(val items: List<SpotifyPlaylistTrackDto>)

@Serializable
private data class SpotifyPlaylistTrackDto(val track: SpotifyTrackDto)

@Serializable
private data class SpotifyLikedTracksResponse(val items: List<SpotifyPlaylistTrackDto>)

@Serializable
private data class SpotifyTrackDto(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtistDto>,
    val album: SpotifyAlbumDto,
    val duration_ms: Long,
    val external_ids: Map<String, String>? = null
) {
    fun toDomain() = Track(
        id = id,
        name = name,
        artist = artists.joinToString(", ") { it.name },
        album = album.name,
        durationMs = duration_ms,
        isrc = external_ids?.get("isrc"),
        imageUrl = album.images?.firstOrNull()?.url
    )
}

@Serializable
private data class SpotifyArtistDto(val name: String)

@Serializable
private data class SpotifyAlbumDto(
    val name: String,
    val images: List<SpotifyImageDto>? = null
)
