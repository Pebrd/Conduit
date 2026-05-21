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
        val storedId = settingsStorage.tidalUserId
        if (storedId.isNotBlank()) {
            cachedUserId = storedId
            return storedId
        }

        val token = tokenRefreshPlugin.getValidToken("tidal")
        
        println("DEBUG TIDAL REBUILD: Obteniendo User ID de la sesión...")
        val tidalService = TidalService(client)
        val sessionsBody = tidalService.getUserId(token)
        
        if (sessionsBody == null) {
            throw Exception("No se pudo obtener la sesión de Tidal. Por favor, reconecta.")
        }

        return try {
            json.parseToJsonElement(sessionsBody)
                .jsonObject["userId"]!!.jsonPrimitive.content
                .also { 
                    cachedUserId = it
                    settingsStorage.tidalUserId = it
                }
        } catch (e: Exception) {
            println("DEBUG TIDAL ERROR PARSING SESSION: $sessionsBody")
            throw Exception("Error al procesar la sesión de Tidal: ${e.message}")
        }
    }

    suspend fun getPlaylists(): List<Playlist> {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val playlists = mutableListOf<Playlist>()
            var cursor: String? = null

            while (true) {
                val response = client.get("https://openapi.tidal.com/v2/playlists") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.Accept, "application/vnd.api+json")
                    parameter("filter[owners.id]", "me")
                    parameter("countryCode", "AR")
                    if (cursor != null) {
                        parameter("page[cursor]", cursor)
                    }
                }
                val body = response.bodyAsText()
                println("DEBUG TIDAL GET PLAYLISTS: status=${response.status} body=${body.take(500)}")

                if (!response.status.isSuccess()) {
                    println("DEBUG: Tidal getPlaylists failed: ${response.status} - ${body.take(300)}")
                    break
                }

                val bodyObj = json.parseToJsonElement(body).jsonObject
                val data = bodyObj["data"]?.jsonArray ?: break

                data.mapNotNull { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val attributes = obj["attributes"]?.jsonObject ?: return@mapNotNull null
                    val name = attributes["name"]?.jsonPrimitive?.content ?: ""
                    val trackCount = attributes["numberOfItems"]?.jsonPrimitive?.intOrNull ?: 0
                    val description = attributes["description"]?.jsonPrimitive?.contentOrNull
                    Playlist(
                        id = id,
                        name = name,
                        trackCount = trackCount,
                        description = description,
                        imageUrl = null,
                        source = MusicService.TIDAL,
                    )
                }.forEach { playlists.add(it) }

                val links = bodyObj["links"]?.jsonObject
                val next = links?.get("next")?.jsonPrimitive?.contentOrNull
                val nextCursor = links?.get("meta")?.jsonObject?.get("nextCursor")?.jsonPrimitive?.contentOrNull

                cursor = nextCursor ?: next?.substringAfter("page[cursor]=", "")?.substringBefore("&")?.takeIf { it.isNotEmpty() }
                if (cursor.isNullOrBlank() || data.isEmpty()) {
                    break
                }
            }
            playlists
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
            var cursor: String? = null

            while (true) {
                val response = client.get("https://openapi.tidal.com/v2/playlists/$playlistId/relationships/items") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.Accept, "application/vnd.api+json")
                    parameter("countryCode", "AR")
                    if (cursor != null) {
                        parameter("page[cursor]", cursor)
                    }
                }
                if (response.status != HttpStatusCode.OK) break

                val bodyText = response.bodyAsText()
                val bodyObj = json.parseToJsonElement(bodyText).jsonObject
                val data = bodyObj["data"]?.jsonArray ?: break

                data.forEach { item ->
                    val id = item.jsonObject["id"]?.jsonPrimitive?.content
                    if (id != null) {
                        allIds.add(id)
                    }
                }

                val links = bodyObj["links"]?.jsonObject
                val next = links?.get("next")?.jsonPrimitive?.contentOrNull
                val nextCursor = links?.get("meta")?.jsonObject?.get("nextCursor")?.jsonPrimitive?.contentOrNull

                cursor = nextCursor ?: next?.substringAfter("page[cursor]=", "")?.substringBefore("&")?.takeIf { it.isNotEmpty() }
                if (cursor.isNullOrBlank() || data.isEmpty()) {
                    break
                }
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
                parameter("countryCode", "AR")
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
                parameter("countryCode", "AR")
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
        
        try {
            println("DEBUG TIDAL: Intentando crear playlist en v2...")
            val response = client.post("https://openapi.tidal.com/v2/playlists") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.api+json")
                contentType(ContentType("application", "vnd.api+json"))
                
                setBody(buildJsonObject {
                    putJsonObject("data") {
                        put("type", "playlists")
                        putJsonObject("attributes") {
                            put("name", name)
                            put("description", description)
                        }
                    }
                })
            }
            
            val body = response.bodyAsText()
            if (response.status.isSuccess()) {
                val jsonBody = json.parseToJsonElement(body).jsonObject
                return jsonBody["data"]?.jsonObject?.get("id")?.jsonPrimitive?.content 
                    ?: throw Exception("ID no encontrado en respuesta v2")
            }
            println("DEBUG TIDAL: creación v2 falló (${response.status}): $body")
            throw Exception("Fallo al crear playlist v2: ${response.status} - $body")
        } catch (e: Exception) {
            println("DEBUG TIDAL: Error al crear playlist en v2: ${e.message}")
            throw e
        }
    }

    /**
     * Agrega tracks a una playlist de Tidal en batches de 25.
     * La API v1 espera form-encoded y requiere el ETag del playlist.
     * El ETag se re-fetcha después de cada batch porque el playlist cambia.
     */
    suspend fun addTracksToPlaylist(playlistId: String, tidalTrackIds: List<String>) {
        if (tidalTrackIds.isEmpty()) return
        val token = tokenRefreshPlugin.getValidToken("tidal")

        // CRITICAL: Tidal v2 relationships/items POST maxItems is 20
        tidalTrackIds.chunked(20).forEach { batch ->
            val response = client.post("https://openapi.tidal.com/v2/playlists/$playlistId/relationships/items") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.api+json")
                contentType(ContentType("application", "vnd.api+json"))
                setBody(buildJsonObject {
                    putJsonArray("data") {
                        batch.forEach { id ->
                            addJsonObject {
                                put("id", id)
                                put("type", "tracks")
                            }
                        }
                    }
                })
            }

            val body = response.bodyAsText()
            println("DEBUG TIDAL ADD TRACKS v2: status=${response.status} batch=${batch.size} body=${body.take(200)}")

            if (!response.status.isSuccess()) {
                throw Exception("Failed to add tracks to playlist v2: ${response.status} - ${body.take(200)}")
            }
        }
    }

    /**
     * Actualiza la descripción de una playlist.
     * La API v1 espera form-encoded via POST.
     */
    suspend fun updatePlaylistDescription(playlistId: String, description: String) {
        val token = tokenRefreshPlugin.getValidToken("tidal")

        val response = client.patch("https://openapi.tidal.com/v2/playlists/$playlistId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/vnd.api+json")
            contentType(ContentType("application", "vnd.api+json"))
            setBody(buildJsonObject {
                putJsonObject("data") {
                    put("type", "playlists")
                    putJsonObject("attributes") {
                        put("description", description)
                    }
                }
            })
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw Exception("Failed to update playlist description v2: ${response.status} - ${body.take(200)}")
        }
    }

    suspend fun searchAlbums(query: String, limit: Int = 3): List<com.conduit.domain.repository.TidalAlbum> {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val response = client.get("https://listen.tidal.com/v1/search/albums") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("query", query)
                parameter("limit", limit)
                parameter("countryCode", "AR")
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
                parameter("countryCode", "AR")
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
                parameter("countryCode", "AR")
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
