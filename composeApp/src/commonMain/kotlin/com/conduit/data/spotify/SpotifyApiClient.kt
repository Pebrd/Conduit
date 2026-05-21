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
import kotlinx.serialization.json.*
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
            val bodyString = response.bodyAsText()
            println("DEBUG SPOTIFY PLAYLISTS RAW: $bodyString")

            if (response.status != HttpStatusCode.OK) break
            
            val page = try {
                Json { ignoreUnknownKeys = true }.decodeFromString<SpotifyPlaylistsResponse>(bodyString)
            } catch (e: Exception) {
                println("DEBUG: Spotify Playlists Parsing Error: ${e.message}")
                break
            }
            
            addAll(page.items.map { it.toDomain() })
            nextUrl = page.next
        }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<Track> = buildList {
        println("DEBUG SPOTIFY TRACKS: fetching playlist $playlistId via Full Playlist endpoint")
        var token = tokenRefreshPlugin.getValidToken("spotify")
        var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId"
        
        while (nextUrl != null) {
            println("DEBUG SPOTIFY REQ: url=$nextUrl token=${token.take(10)}...")
            var response = client.get(nextUrl) {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/json")
            }
            
            if (response.status == HttpStatusCode.Forbidden) {
                println("DEBUG: 403 detected, attempting forced token refresh...")
                token = tokenRefreshPlugin.getValidToken("spotify") 
                response = client.get(nextUrl) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.Accept, "application/json")
                }
            }

            val bodyString = response.bodyAsText()
            println("DEBUG SPOTIFY BODY: $bodyString")

            if (response.status != HttpStatusCode.OK) {
                println("DEBUG: Spotify Full Playlist Error: ${response.status} | Body: $bodyString")
                break
            }
            
            val page = try {
                val jsonElement = Json { ignoreUnknownKeys = true }.parseToJsonElement(bodyString)
                val jsonObject = jsonElement.jsonObject
                
                // Intentar obtener 'tracks' o 'items'
                val tracksElement = jsonObject["tracks"] ?: jsonObject["items"] ?: jsonElement
                
                if (nextUrl.contains("/playlists/") && !nextUrl.contains("/tracks") && (jsonObject.containsKey("tracks") || jsonObject.containsKey("items"))) {
                    val fullPlaylist = Json { ignoreUnknownKeys = true }.decodeFromString<SpotifyFullPlaylistDto>(bodyString)
                    addAll(fullPlaylist.tracks.items.mapNotNull { it.toTrackDto(Json { ignoreUnknownKeys = true })?.toDomain() })
                    fullPlaylist.tracks.next
                } else {
                    // Si es directamente un objeto de tracks (paginación)
                    val tracksPage = Json { ignoreUnknownKeys = true }.decodeFromJsonElement<SpotifyPlaylistTracksDto>(tracksElement)
                    addAll(tracksPage.items.mapNotNull { it.toTrackDto(Json { ignoreUnknownKeys = true })?.toDomain() })
                    tracksPage.next
                }
            } catch (e: Exception) {
                println("DEBUG: Spotify Parsing Error: ${e.message}")
                e.printStackTrace()
                break
            }
            nextUrl = page
        }
        println("DEBUG SPOTIFY TRACKS: got ${this.size} tracks")
    }

    suspend fun getLikedSongs(): List<Track> = buildList {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        var nextUrl: String? = "https://api.spotify.com/v1/me/tracks?limit=50"
        val json = Json { ignoreUnknownKeys = true }
        
        while (nextUrl != null) {
            val response = client.get(nextUrl) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status != HttpStatusCode.OK) break
            val bodyString = response.bodyAsText()
            val page = json.decodeFromString<SpotifyLikedTracksResponse>(bodyString)
            addAll(page.items.mapNotNull { it.toTrackDto(json)?.toDomain() })
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
    @kotlinx.serialization.SerialName("items") val tracksRef: SpotifyTracksRef? = null,
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
internal data class SpotifyFullPlaylistDto(
    @kotlinx.serialization.SerialName("items") val tracksFromItems: SpotifyPlaylistTracksDto? = null,
    @kotlinx.serialization.SerialName("tracks") val tracksFromTracks: SpotifyPlaylistTracksDto? = null
) {
    val tracks: SpotifyPlaylistTracksDto
        get() = tracksFromItems ?: tracksFromTracks ?: throw Exception("No tracks found in playlist")
}

@Serializable
internal data class SpotifyPlaylistTracksDto(
    val items: List<SpotifyTrackItemDto>,
    val next: String? = null,
    val total: Int = 0,
)

@Serializable
internal data class SpotifyTrackItemDto(
    @kotlinx.serialization.SerialName("track") val trackObj: kotlinx.serialization.json.JsonElement? = null,
    @kotlinx.serialization.SerialName("item") val itemObj: kotlinx.serialization.json.JsonElement? = null,
) {
    fun toTrackDto(json: Json): SpotifyTrackDto? {
        // Intentar parsear el objeto 'item' primero, luego 'track'
        val element = itemObj ?: trackObj ?: return null
        return try {
            // Si el elemento es un booleano o algo raro, fallará el decode y pasaremos al siguiente
            json.decodeFromJsonElement<SpotifyTrackDto>(element)
        } catch (e: Exception) {
            null
        }
    }
}

@Serializable
internal data class SpotifyTrackDto(
    val id: String? = null,
    val name: String? = null,
    val artists: List<SpotifyArtistDto>? = null,
    val album: SpotifyAlbumDto? = null,
    val duration_ms: Long? = null,
    val external_ids: ExternalIds? = null
) {
    fun toDomain() = Track(
        id = id ?: "",
        name = name ?: "Unknown Track",
        artist = artists?.joinToString(", ") { it.name } ?: "Unknown Artist",
        album = album?.name ?: "Unknown Album",
        durationMs = duration_ms ?: 0L,
        isrc = external_ids?.isrc,
        imageUrl = album?.images?.firstOrNull()?.url
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
    val name: String? = null,
    val images: List<SpotifyImageDto>? = null
)

@Serializable
internal data class SpotifyLikedTracksResponse(
    val items: List<SpotifyTrackItemDto>,
    val next: String? = null
)

