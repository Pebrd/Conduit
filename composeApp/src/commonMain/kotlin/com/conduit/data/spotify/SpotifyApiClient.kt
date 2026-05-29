package com.conduit.data.spotify

import com.conduit.domain.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import com.conduit.data.http.TokenRefreshPlugin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

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

    // ── Stats endpoints ──

    suspend fun getProfile(): UserProfile {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val response = client.get("https://api.spotify.com/v1/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val body = response.bodyAsText()
        println("DEBUG PROFILE: status=${response.status} body=$body")
        return Json { ignoreUnknownKeys = true }
            .decodeFromString<SpotifyProfileDto>(body)
            .toDomain()
    }

    suspend fun getTopArtists(timeRange: String = "medium_term", limit: Int = 20): List<TopArtistItem> {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val response = client.get("https://api.spotify.com/v1/me/top/artists") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("time_range", timeRange)
            parameter("limit", limit)
        }
        if (response.status != HttpStatusCode.OK) return emptyList()
        val body = response.bodyAsText()
        return Json { ignoreUnknownKeys = true }
            .decodeFromString<SpotifyTopArtistsResponse>(body)
            .items.map { it.toDomain() }
    }

    suspend fun getTopTracks(timeRange: String = "medium_term", limit: Int = 20): List<TopTrackItem> {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val response = client.get("https://api.spotify.com/v1/me/top/tracks") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("time_range", timeRange)
            parameter("limit", limit)
        }
        if (response.status != HttpStatusCode.OK) return emptyList()
        val body = response.bodyAsText()
        return Json { ignoreUnknownKeys = true }
            .decodeFromString<SpotifyTopTracksResponse>(body)
            .items.map { it.toDomain() }
    }

    suspend fun getRecentlyPlayed(limit: Int = 20): List<RecentlyPlayedItem> {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val response = client.get("https://api.spotify.com/v1/me/player/recently-played") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("limit", limit)
        }
        if (response.status != HttpStatusCode.OK) return emptyList()
        val body = response.bodyAsText()
        val json = Json { ignoreUnknownKeys = true }
        val page = json.decodeFromString<SpotifyRecentlyPlayedResponse>(body)
        return page.items.mapNotNull { item ->
            val track = item.track ?: return@mapNotNull null
            RecentlyPlayedItem(
                trackId = track.id,
                trackName = track.name,
                artist = track.artists?.joinToString(", ") { it.name } ?: "Unknown",
                album = track.album?.name ?: "Unknown",
                imageUrl = track.album?.images?.firstOrNull()?.url,
                durationMs = track.duration_ms,
                playedAt = item.played_at ?: "",
            )
        }
    }

    suspend fun getArtistTopTracks(artistId: String, market: String = "US"): List<TopTrackItem> {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val response = client.get("https://api.spotify.com/v1/artists/$artistId/top-tracks") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("market", market)
        }
        if (response.status != HttpStatusCode.OK) return emptyList()
        val body = response.bodyAsText()
        return Json { ignoreUnknownKeys = true }
            .decodeFromString<SpotifyArtistTopTracksResponse>(body)
            .tracks.map { it.toDomain() }
    }

    suspend fun getArtistDetail(artistId: String): TopArtistItem? {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val response = client.get("https://api.spotify.com/v1/artists/$artistId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw Exception("Artist detail API error ${response.status}: $body")
        }
        return Json { ignoreUnknownKeys = true }
            .decodeFromString<SpotifyTopArtistDto>(body)
            .toDomain()
    }

    suspend fun getTrack(trackId: String): TopTrackItem? {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val response = client.get("https://api.spotify.com/v1/tracks/$trackId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw Exception("Track detail API error ${response.status}: $body")
        }
        return Json { ignoreUnknownKeys = true }
            .decodeFromString<SpotifyTopTrackDto>(body)
            .toDomain()
    }

    suspend fun getAudioFeatures(trackIds: List<String>): List<AudioFeaturesItem> {
        if (trackIds.isEmpty()) return emptyList()
        val token = tokenRefreshPlugin.getValidToken("spotify")
        // Batch in groups of 20
        val results = mutableListOf<AudioFeaturesItem>()
        trackIds.chunked(20).forEach { chunk ->
            val idsParam = chunk.joinToString(",")
            val response = client.get("https://api.spotify.com/v1/audio-features") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("ids", idsParam)
            }
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                val batch = Json { ignoreUnknownKeys = true }
                    .decodeFromString<SpotifyAudioFeaturesBatchDto>(body)
                results.addAll(batch.audio_features.filterNotNull().map { it.toDomain() })
            }
        }
        return results
    }

    // ── Discover endpoints ──

    suspend fun searchTrack(name: String, artist: String): Track? {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val query = "track:${name.replace(" ", "+")}+artist:${artist.replace(" ", "+")}"
        val response = client.get("https://api.spotify.com/v1/search") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("q", query)
            parameter("type", "track")
            parameter("limit", 5)
        }
        if (response.status != HttpStatusCode.OK) return null
        val json = Json { ignoreUnknownKeys = true }
        return json.parseToJsonElement(response.bodyAsText())
            .jsonObject["tracks"]?.jsonObject
            ?.get("items")?.jsonArray
            ?.firstOrNull()
            ?.let { parseSpotifyTrackItem(it.jsonObject) }
    }

    suspend fun searchTracksByQuery(query: String, limit: Int = 20): List<Track> {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val url = "https://api.spotify.com/v1/search?q=${query.encodeURLParameter()}&type=track"
        println("DEBUG SPOTIFY SEARCH URL: $url")
        val response = client.get(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val bodyString = response.bodyAsText()
        println("DEBUG SPOTIFY SEARCH: status=${response.status} q=$query body=${bodyString.take(500)}")
        if (response.status != HttpStatusCode.OK) {
            println("DEBUG SPOTIFY SEARCH ERROR: ${response.status} $bodyString")
            return emptyList()
        }
        val json = Json { ignoreUnknownKeys = true }
        return try {
            json.decodeFromString<SpotifySearchResponse>(bodyString)
                .tracks?.items
                ?.filter { it.id != null }
                ?.map { it.toDomain() }
                ?: emptyList()
        } catch (e: Exception) {
            println("DEBUG SPOTIFY SEARCH PARSE ERROR: ${e.message}")
            emptyList()
        }
    }

    // ── Discover: Related Artists + Top Tracks (replaces /recommendations) ──

    private suspend fun spotifySearch(query: String): List<Track> {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val url = "https://api.spotify.com/v1/search?q=${query.encodeURLParameter()}&type=track"
        println("DEBUG SPOTIFY SEARCH: $url")
        val response = client.get(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            println("DEBUG SPOTIFY SEARCH FAILED: status=${response.status} body=${body.take(200)}")
            return emptyList()
        }
        val json = Json { ignoreUnknownKeys = true }
        return try {
            json.parseToJsonElement(body)
                .jsonObject["tracks"]?.jsonObject
                ?.get("items")?.jsonArray
                ?.mapNotNull { parseSpotifyTrackItem(it.jsonObject) }
                ?: emptyList()
        } catch (e: Exception) {
            println("DEBUG SPOTIFY SEARCH PARSE ERROR: ${e.message}")
            emptyList()
        }
    }

    suspend fun getRelatedArtists(artistId: String): List<String> {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val response = client.get("https://api.spotify.com/v1/artists/$artistId/related-artists") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val bodyString = response.bodyAsText()
        println("DEBUG SPOTIFY RELATED: status=${response.status} artist=$artistId body=${bodyString.take(300)}")
        if (response.status != HttpStatusCode.OK) return emptyList()
        val json = Json { ignoreUnknownKeys = true }
        return try {
            json.parseToJsonElement(bodyString)
                .jsonObject["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                ?: emptyList()
        } catch (e: Exception) {
            println("DEBUG SPOTIFY RELATED PARSE ERROR: ${e.message}")
            emptyList()
        }
    }

    suspend fun getRecommendationsViaRelatedArtists(
        seedTrackIds: List<String>,
        alreadySeen: Set<String> = emptySet(),
        limit: Int = 30,
    ): List<Track> {
        if (seedTrackIds.isEmpty()) return emptyList()

        // 1. Re-fetch seed tracks to get their artist names
        val seedInfo = seedTrackIds.mapNotNull { id ->
            val response = client.get("https://api.spotify.com/v1/tracks/$id") {
                header(HttpHeaders.Authorization, "Bearer ${tokenRefreshPlugin.getValidToken("spotify")}")
            }
            if (response.status != HttpStatusCode.OK) return@mapNotNull null
            val obj = Json { ignoreUnknownKeys = true }
                .parseToJsonElement(response.bodyAsText()).jsonObject
            parseSpotifyTrackItem(obj)
        }

        val allTracks = mutableListOf<Track>()

        // 2. Search by multiple queries for variety
        for (track in seedInfo) {
            val artist = track.artist.split(",").first().trim()
            val safeName = artist.replace("\"", "")

            // a) Search by artist name (tracks by same artist)
            val byArtist = spotifySearch("artist:\"$safeName\"")
            println("DEBUG SPOTIFY RECS: artist='$artist' search returned ${byArtist.size} tracks")
            allTracks.addAll(byArtist)

            // b) Free-text search (mixes artists)
            val broad = spotifySearch(safeName)
            println("DEBUG SPOTIFY RECS: broad='$artist' search returned ${broad.size} tracks")
            allTracks.addAll(broad)
        }

        // 3. Dedup + filter
        println("DEBUG SPOTIFY RECS: total=${allTracks.size} before dedup/filter")
        return allTracks
            .distinctBy { it.id }
            .filter { it.id !in alreadySeen && it.id !in seedTrackIds }
            .take(limit)
    }

    suspend fun searchByIsrc(isrc: String): Track? {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val response = client.get("https://api.spotify.com/v1/search") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("q", "isrc:$isrc")
            parameter("type", "track")
            parameter("limit", 1)
        }
        if (response.status != HttpStatusCode.OK) return null
        val json = Json { ignoreUnknownKeys = true }
        return json.parseToJsonElement(response.bodyAsText())
            .jsonObject["tracks"]?.jsonObject
            ?.get("items")?.jsonArray
            ?.firstOrNull()
            ?.let { parseSpotifyTrackItem(it.jsonObject) }
    }

    suspend fun createPlaylist(name: String): String {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val profileResponse = client.get("https://api.spotify.com/v1/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val userId = Json { ignoreUnknownKeys = true }
            .parseToJsonElement(profileResponse.bodyAsText())
            .jsonObject["id"]?.jsonPrimitive?.content ?: throw Exception("No user ID")
        val response = client.post("https://api.spotify.com/v1/users/$userId/playlists") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("name", name)
                put("description", "Creada por Conduit Discover")
                put("public", false)
            })
        }
        if (response.status != HttpStatusCode.Created) {
            throw Exception("Failed to create Spotify playlist: ${response.status}")
        }
        return Json { ignoreUnknownKeys = true }
            .parseToJsonElement(response.bodyAsText())
            .jsonObject["id"]?.jsonPrimitive?.content
            ?: throw Exception("No playlist ID in response")
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String) {
        val token = tokenRefreshPlugin.getValidToken("spotify")
        val response = client.post("https://api.spotify.com/v1/playlists/$playlistId/tracks") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                putJsonArray("uris") { add("spotify:track:$trackId") }
            })
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception("Failed to add track to Spotify playlist: ${response.status}")
        }
    }

    private fun parseSpotifyTrackItem(obj: JsonObject): Track? {
        val id = obj["id"]?.jsonPrimitive?.content ?: return null
        val name = obj["name"]?.jsonPrimitive?.content ?: return null
        val artistsArr = obj["artists"]?.jsonArray ?: return null
        val artistNames = artistsArr.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
        val artistIds = artistsArr.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
        val album = obj["album"]?.jsonObject
        return Track(
            id = id,
            name = name,
            artist = artistNames.joinToString(", "),
            album = album?.get("name")?.jsonPrimitive?.content ?: "",
            durationMs = obj["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L,
            isrc = obj["external_ids"]?.jsonObject?.get("isrc")?.jsonPrimitive?.contentOrNull,
            previewUrl = obj["preview_url"]?.jsonPrimitive?.contentOrNull,
            imageUrl = album?.get("images")?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content,
            artistIds = artistIds,
        )
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
    val external_ids: ExternalIds? = null,
    val preview_url: String? = null,
) {
    fun toDomain() = Track(
        id = id ?: "",
        name = name ?: "Unknown Track",
        artist = artists?.joinToString(", ") { it.name } ?: "Unknown Artist",
        album = album?.name ?: "Unknown Album",
        durationMs = duration_ms ?: 0L,
        isrc = external_ids?.isrc,
        imageUrl = album?.images?.firstOrNull()?.url,
        previewUrl = preview_url,
        artistIds = artists?.mapNotNull { it.id } ?: emptyList(),
    )
}

@Serializable
internal data class ExternalIds(
    val isrc: String? = null
)

@Serializable
internal data class SpotifyArtistDto(
    val name: String,
    val id: String? = null,
)

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

@Serializable
internal data class SpotifySearchResponse(
    val tracks: SpotifySearchTracks? = null
)

@Serializable
internal data class SpotifySearchTracks(
    val items: List<SpotifyTrackDto> = emptyList()
)

