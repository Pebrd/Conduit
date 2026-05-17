package com.conduit.data.spotify

import com.conduit.domain.model.Playlist
import com.conduit.domain.model.Track
import com.conduit.domain.model.MusicService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.conduit.data.http.TokenRefreshPlugin

class SpotifyApiClient(
    private val client: HttpClient,
    private val tokenRefreshPlugin: TokenRefreshPlugin
) {

    suspend fun getPlaylists(): List<Playlist> = buildList {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        var nextUrl: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
        
        while (nextUrl != null) {
            val response = client.get(nextUrl) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status != HttpStatusCode.OK) break
            val page = response.body<SpotifyPlaylistsResponse>()
            addAll(page.items.map { it.toDomain() })
            nextUrl = page.next
        }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<Track> = buildList {
        println("DEBUG SPOTIFY TRACKS: fetching playlist $playlistId")
        val token = tokenRefreshPlugin.getValidToken("spotify")
        var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100"
        
        while (nextUrl != null) {
            val response = client.get(nextUrl) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            val bodyString = response.bodyAsText()
            println("DEBUG SPOTIFY RAW: status=${response.status} body=${bodyString.take(500)}")

            if (response.status != HttpStatusCode.OK) {
                println("DEBUG: Spotify Tracks Error: ${response.status}")
                break
            }
            
            val page = try {
                Json { ignoreUnknownKeys = true }.decodeFromString<SpotifyPlaylistTracksDto>(bodyString)
            } catch (e: Exception) {
                println("DEBUG: Spotify Tracks Parsing Error: ${e.message}")
                throw e
            }
            addAll(page.items.mapNotNull { it.track?.toDomain() })
            nextUrl = page.next
        }
        println("DEBUG SPOTIFY TRACKS: got ${this.size} tracks, first=${this.firstOrNull()?.name}")
    }

    suspend fun getLikedSongs(): List<Track> = buildList {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        var nextUrl: String? = "https://api.spotify.com/v1/me/tracks?limit=50"
        
        while (nextUrl != null) {
            val response = client.get(nextUrl) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status != HttpStatusCode.OK) break
            val page = response.body<SpotifyLikedTracksResponse>()
            addAll(page.items.mapNotNull { it.track?.toDomain() })
            nextUrl = page.next
        }
    }
}

@Serializable
internal data class SpotifyPlaylistsResponse(
    val items: List<SpotifyPlaylistDto>,
    val next: String? = null
)

@Serializable
internal data class SpotifyPlaylistDto(
    val id: String,
    val name: String,
    val images: List<SpotifyImageDto> = emptyList(),
    @kotlinx.serialization.SerialName("tracks") val tracksRef: SpotifyTracksRef? = null,
) {
    fun toDomain() = Playlist(
        id = id,
        name = name,
        trackCount = tracksRef?.total ?: 0,
        imageUrl = images.firstOrNull()?.url,
        source = MusicService.SPOTIFY
    )
}

@Serializable
internal data class SpotifyTracksRef(
    val href: String? = null,
    val total: Int = 0,
)

@Serializable
internal data class SpotifyImageDto(val url: String)

@Serializable
internal data class SpotifyPlaylistTracksDto(
    val items: List<SpotifyTrackItemDto>,
    val next: String? = null,
    val total: Int,
)

@Serializable
internal data class SpotifyTrackItemDto(
    val track: SpotifyTrackDto? = null,
)

@Serializable
internal data class SpotifyLikedTracksResponse(
    val items: List<SpotifyTrackItemDto>,
    val next: String? = null
)

@Serializable
internal data class SpotifyTrackDto(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtistDto>,
    val album: SpotifyAlbumDto,
    val duration_ms: Long,
    val external_ids: ExternalIds? = null
) {
    fun toDomain() = Track(
        id = id,
        name = name,
        artist = artists.joinToString(", ") { it.name },
        album = album.name,
        durationMs = duration_ms,
        isrc = external_ids?.isrc,
        imageUrl = album.images?.firstOrNull()?.url
    )
}

@Serializable
internal data class ExternalIds(
    val isrc: String? = null
)

@Serializable
internal data class SpotifyArtistDto(val name: String)

@Serializable
internal data class SpotifyAlbumDto(
    val name: String,
    val images: List<SpotifyImageDto>? = null
)
