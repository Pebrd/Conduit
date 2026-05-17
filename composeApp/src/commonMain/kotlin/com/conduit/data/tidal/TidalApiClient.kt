package com.conduit.data.tidal

import com.conduit.domain.model.Playlist
import com.conduit.domain.model.TidalTrack
import com.conduit.domain.model.MusicService
import com.conduit.data.local.SettingsStorage
import com.conduit.Credentials
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
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

    private var cachedUserId: String? = null

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Obtiene el userId de Tidal usando el endpoint de sessions.
     * El token del Device Auth flow funciona con api.tidal.com/v1/sessions.
     */
    private suspend fun getUserId(): String {
        cachedUserId?.let { return it }
        val token = tokenRefreshPlugin.getValidToken("tidal")

        val response = client.get("https://api.tidal.com/v1/sessions") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val body = response.bodyAsText()
        println("DEBUG TIDAL SESSIONS: status=${response.status} body=${body.take(300)}")

        if (!response.status.isSuccess()) {
            throw Exception("Failed to get Tidal session: ${response.status} - ${body.take(200)}")
        }

        return json.parseToJsonElement(body)
            .jsonObject["userId"]!!.jsonPrimitive.content
            .also { cachedUserId = it }
    }

    suspend fun getPlaylists(): List<Playlist> {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val userId = getUserId()
            val response = client.get("https://listen.tidal.com/v1/users/$userId/playlists") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("countryCode", "US")
                parameter("limit", 999)
                parameter("offset", 0)
            }
            val body = response.bodyAsText()
            println("DEBUG TIDAL PLAYLISTS: status=${response.status} body=${body.take(500)}")

            if (!response.status.isSuccess()) {
                println("DEBUG: Tidal getPlaylists failed: ${response.status} - ${body.take(300)}")
                return emptyList()
            }

            val items = json.parseToJsonElement(body)
                .jsonObject["items"]?.jsonArray ?: return emptyList()

            items.map { item ->
                val obj = item.jsonObject
                Playlist(
                    id = obj["uuid"]?.jsonPrimitive?.content ?: "",
                    name = obj["title"]?.jsonPrimitive?.content ?: "",
                    trackCount = obj["numberOfTracks"]?.jsonPrimitive?.intOrNull ?: 0,
                    description = obj["description"]?.jsonPrimitive?.contentOrNull,
                    imageUrl = null,
                    source = MusicService.TIDAL,
                )
            }
        } catch (e: Exception) {
            println("DEBUG: Tidal getPlaylists exception: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getPlaylistTrackIds(playlistId: String): Set<String> {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val allIds = mutableSetOf<String>()
            var offset = 0
            val limit = 100

            while (true) {
                val response = client.get("https://listen.tidal.com/v1/playlists/$playlistId/items") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    parameter("countryCode", "US")
                    parameter("limit", limit)
                    parameter("offset", offset)
                }
                if (response.status != HttpStatusCode.OK) break

                val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val items = body["items"]?.jsonArray ?: break

                items.mapNotNull {
                    it.jsonObject["item"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                        ?: it.jsonObject["id"]?.jsonPrimitive?.content
                }.forEach { allIds.add(it) }

                val totalItems = body["totalNumberOfItems"]?.jsonPrimitive?.intOrNull ?: 0
                offset += limit
                if (offset >= totalItems || items.isEmpty()) break
            }

            println("DEBUG TIDAL PLAYLIST TRACKS: playlistId=$playlistId found=${allIds.size} track IDs")
            allIds
        } catch (e: Exception) {
            println("DEBUG: Tidal getPlaylistTrackIds failed: ${e.message}")
            emptySet()
        }
    }

    suspend fun searchTracks(query: String, limit: Int = 10): List<TidalTrack> {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val response = client.get("https://listen.tidal.com/v1/search/tracks") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("query", query)
                parameter("limit", limit)
                parameter("countryCode", "US")
            }
            if (response.status != HttpStatusCode.OK) return emptyList()

            val items = json.parseToJsonElement(response.bodyAsText())
                .jsonObject["items"]?.jsonArray ?: return emptyList()

            items.map { item -> parseTidalTrack(item.jsonObject) }
        } catch (e: Exception) {
            println("DEBUG: Tidal searchTracks failed for '$query': ${e.message}")
            emptyList()
        }
    }

    /**
     * Busca un track por ISRC usando el endpoint de search y filtrando por ISRC exacto.
     * La API v1 no tiene endpoint dedicado /tracks?isrc=X.
     */
    suspend fun searchByIsrc(isrc: String): TidalTrack? {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val response = client.get("https://listen.tidal.com/v1/search/tracks") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("query", isrc)
                parameter("limit", 5)
                parameter("countryCode", "US")
            }
            if (response.status != HttpStatusCode.OK) return null

            val items = json.parseToJsonElement(response.bodyAsText())
                .jsonObject["items"]?.jsonArray ?: return null

            // Filtrar por ISRC exacto del lado del cliente
            items.map { parseTidalTrack(it.jsonObject) }
                .firstOrNull { it.isrc?.equals(isrc, ignoreCase = true) == true }
        } catch (e: Exception) {
            println("DEBUG: Tidal searchByIsrc failed for '$isrc': ${e.message}")
            null
        }
    }

    /**
     * Crea una playlist en Tidal.
     * La API v1 espera form-encoded, NO JSON.
     */
    suspend fun createPlaylist(name: String, description: String): String {
        val token = tokenRefreshPlugin.getValidToken("tidal")
        val userId = getUserId()

        val response = client.submitForm(
            url = "https://listen.tidal.com/v1/users/$userId/playlists",
            formParameters = parameters {
                append("title", name)
                append("description", description)
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("countryCode", "US")
        }

        val body = response.bodyAsText()
        println("DEBUG TIDAL CREATE PLAYLIST: status=${response.status} body=${body.take(300)}")

        if (!response.status.isSuccess()) {
            throw Exception("Failed to create Tidal playlist: ${response.status} - ${body.take(200)}")
        }

        val jsonBody = json.parseToJsonElement(body).jsonObject
        return jsonBody["uuid"]?.jsonPrimitive?.content
            ?: jsonBody["id"]?.jsonPrimitive?.content
            ?: throw Exception("No playlist ID in response: ${body.take(200)}")
    }

    /**
     * Agrega tracks a una playlist de Tidal en batches de 25.
     * La API v1 espera form-encoded y requiere el ETag del playlist.
     * El ETag se re-fetcha después de cada batch porque el playlist cambia.
     */
    suspend fun addTracksToPlaylist(playlistId: String, tidalTrackIds: List<String>) {
        if (tidalTrackIds.isEmpty()) return
        val token = tokenRefreshPlugin.getValidToken("tidal")

        tidalTrackIds.chunked(25).forEach { batch ->
            withRetry {
                // Obtener ETag actual (cambia con cada modificación)
                val etagResponse = client.get("https://listen.tidal.com/v1/playlists/$playlistId") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    parameter("countryCode", "US")
                }
                val etag = etagResponse.headers["ETag"] ?: ""

                val response = client.submitForm(
                    url = "https://listen.tidal.com/v1/playlists/$playlistId/items",
                    formParameters = parameters {
                        append("trackIds", batch.joinToString(","))
                        append("toIndex", "0")
                    }
                ) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("If-None-Match", etag)
                    parameter("countryCode", "US")
                }

                val body = response.bodyAsText()
                println("DEBUG TIDAL ADD TRACKS: status=${response.status} batch=${batch.size} body=${body.take(200)}")

                if (!response.status.isSuccess()) {
                    throw Exception("Failed to add tracks to playlist: ${response.status} - ${body.take(200)}")
                }
            }
        }
    }

    /**
     * Actualiza la descripción de una playlist.
     * La API v1 espera form-encoded via POST.
     */
    suspend fun updatePlaylistDescription(playlistId: String, description: String) {
        val token = tokenRefreshPlugin.getValidToken("tidal")

        // Obtener ETag actual
        val etagResponse = client.get("https://listen.tidal.com/v1/playlists/$playlistId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("countryCode", "US")
        }
        val etag = etagResponse.headers["ETag"] ?: ""

        val response = client.submitForm(
            url = "https://listen.tidal.com/v1/playlists/$playlistId",
            formParameters = parameters {
                append("description", description)
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("If-None-Match", etag)
            parameter("countryCode", "US")
        }
        println("DEBUG TIDAL UPDATE DESC: status=${response.status} body=${response.bodyAsText().take(200)}")
    }

    suspend fun searchAlbums(query: String, limit: Int = 3): List<com.conduit.domain.repository.TidalAlbum> {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val response = client.get("https://listen.tidal.com/v1/search/albums") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("query", query)
                parameter("limit", limit)
                parameter("countryCode", "US")
            }
            if (response.status != HttpStatusCode.OK) return emptyList()

            val items = json.parseToJsonElement(response.bodyAsText())
                .jsonObject["items"]?.jsonArray ?: return emptyList()

            items.map { item ->
                val obj = item.jsonObject
                com.conduit.domain.repository.TidalAlbum(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    name = obj["title"]?.jsonPrimitive?.content ?: "",
                    artist = parseArtistsFromJson(obj["artists"]?.jsonArray)
                )
            }
        } catch (e: Exception) {
            println("DEBUG: Tidal searchAlbums failed for '$query': ${e.message}")
            emptyList()
        }
    }

    suspend fun getAlbumTracks(albumId: String): List<TidalTrack> {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val response = client.get("https://listen.tidal.com/v1/albums/$albumId/tracks") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("countryCode", "US")
            }
            if (response.status != HttpStatusCode.OK) return emptyList()

            val items = json.parseToJsonElement(response.bodyAsText())
                .jsonObject["items"]?.jsonArray ?: return emptyList()

            items.map { item -> parseTidalTrack(item.jsonObject) }
        } catch (e: Exception) {
            println("DEBUG: Tidal getAlbumTracks failed for album '$albumId': ${e.message}")
            emptyList()
        }
    }

    suspend fun getTrack(trackId: String): TidalTrack? {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val response = client.get("https://listen.tidal.com/v1/tracks/$trackId") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("countryCode", "US")
            }
            if (response.status != HttpStatusCode.OK) return null

            parseTidalTrack(json.parseToJsonElement(response.bodyAsText()).jsonObject)
        } catch (e: Exception) {
            println("DEBUG: Tidal getTrack failed for '$trackId': ${e.message}")
            null
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Parsea un TidalTrack desde un JsonObject.
     * - Parsea TODOS los artistas (no solo el primero)
     * - Convierte duration de segundos a milisegundos
     * - Extrae ISRC si está disponible
     */
    private fun parseTidalTrack(obj: JsonObject): TidalTrack {
        return TidalTrack(
            id = obj["id"]?.jsonPrimitive?.content ?: "",
            name = obj["title"]?.jsonPrimitive?.content ?: "",
            artist = parseArtistsFromJson(obj["artists"]?.jsonArray),
            album = obj["album"]?.jsonObject?.get("title")?.jsonPrimitive?.content ?: "Unknown Album",
            // CRITICAL: Tidal API v1 devuelve duration en SEGUNDOS, convertir a ms
            durationMs = (obj["duration"]?.jsonPrimitive?.long ?: 0L) * 1000,
            isrc = obj["isrc"]?.jsonPrimitive?.contentOrNull,
        )
    }

    /**
     * Parsea todos los artistas de un JsonArray, uniéndolos con ", ".
     * Esto es importante para que SyncEngine.parseArtists() pueda hacer
     * Jaccard similarity correctamente sobre todos los artistas.
     */
    private fun parseArtistsFromJson(artists: JsonArray?): String {
        if (artists == null || artists.isEmpty()) return "Unknown Artist"
        return artists.mapNotNull { artist ->
            artist.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        }.joinToString(", ").ifBlank { "Unknown Artist" }
    }
}
