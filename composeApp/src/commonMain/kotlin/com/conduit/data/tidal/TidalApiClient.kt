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

    suspend fun playlistExists(playlistId: String): Boolean {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val response = client.get("https://openapi.tidal.com/v2/playlists/$playlistId") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.api+json")
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            println("DEBUG: Tidal playlistExists failed for $playlistId: ${e.message}")
            false
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

    /**
     * Returns a pair of (Set<trackId>, Set<isrc>) for all tracks currently in the playlist.
     * Used to prevent adding duplicate songs (even across different album versions).
     */
    suspend fun getPlaylistExistingTracks(playlistId: String): Pair<Set<String>, Set<String>> {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val allIds = mutableSetOf<String>()
            val allIsrcs = mutableSetOf<String>()
            var cursor: String? = null

            while (true) {
                val response = client.get("https://openapi.tidal.com/v2/playlists/$playlistId/items") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.Accept, "application/vnd.api+json")
                    parameter("include", "items")
                    parameter("countryCode", "AR")
                    if (cursor != null) {
                        parameter("page[cursor]", cursor)
                    }
                }
                if (!response.status.isSuccess()) break

                val bodyText = response.bodyAsText()
                val bodyObj = json.parseToJsonElement(bodyText).jsonObject
                val included = bodyObj["included"]?.jsonArray ?: break

                included.forEach { inc ->
                    val incObj = inc.jsonObject
                    val type = incObj["type"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    if (type != "tracks") return@forEach
                    val id = incObj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val attrs = incObj["attributes"]?.jsonObject ?: return@forEach
                    val isrc = attrs["isrc"]?.jsonPrimitive?.contentOrNull
                    allIds.add(id)
                    if (isrc != null) allIsrcs.add(isrc)
                }

                val links = bodyObj["links"]?.jsonObject
                val next = links?.get("next")?.jsonPrimitive?.contentOrNull
                val nextCursor = links?.get("meta")?.jsonObject?.get("nextCursor")?.jsonPrimitive?.contentOrNull
                cursor = nextCursor ?: next?.substringAfter("page[cursor]=", "")?.substringBefore("&")?.takeIf { it.isNotEmpty() }

                val data = bodyObj["data"]?.jsonArray
                if (cursor.isNullOrBlank() || data.isNullOrEmpty()) break
            }

            println("DEBUG TIDAL PLAYLIST EXISTING: playlistId=$playlistId ids=${allIds.size} isrcs=${allIsrcs.size}")
            Pair(allIds, allIsrcs)
        } catch (e: Exception) {
            println("DEBUG: Tidal getPlaylistExistingTracks failed: ${e.message}")
            Pair(emptySet(), emptySet())
        }
    }

    suspend fun searchTracks(query: String, limit: Int = 30): List<TidalTrack> {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val encodedQuery = query.encodeURLParameter()
            val response = client.get("https://openapi.tidal.com/v2/searchResults/$encodedQuery") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.api+json")
                parameter("include", "tracks,tracks.artists,tracks.albums")
                parameter("countryCode", "AR")
            }
            val bodyText = response.bodyAsText()
            println("DEBUG TIDAL SEARCH TRACKS v2: status=${response.status} query='$query' body=${bodyText.take(300)}")
            if (!response.status.isSuccess()) return emptyList()

            val bodyObj = json.parseToJsonElement(bodyText).jsonObject
            val dataObj = bodyObj["data"]?.jsonObject ?: return emptyList()

            // 1. Construir mapa de artistas y álbumes desde `included`
            val artistNameById = mutableMapOf<String, String>()
            val albumTitleById = mutableMapOf<String, String>()

            bodyObj["included"]?.jsonArray?.forEach { inc ->
                val incObj = inc.jsonObject
                val type = incObj["type"]?.jsonPrimitive?.content ?: return@forEach
                val id = incObj["id"]?.jsonPrimitive?.content ?: return@forEach
                val attrs = incObj["attributes"]?.jsonObject ?: return@forEach

                when (type) {
                    "artists" -> {
                        attrs["name"]?.jsonPrimitive?.contentOrNull?.let { name ->
                            artistNameById[id] = name
                        }
                    }
                    "albums" -> {
                        attrs["title"]?.jsonPrimitive?.contentOrNull?.let { title ->
                            albumTitleById[id] = title
                        }
                    }
                }
            }

            // 2. Parsear todos los tracks que vienen en `included`
            val tracksById = mutableMapOf<String, TidalTrack>()
            bodyObj["included"]?.jsonArray?.forEach { inc ->
                val incObj = inc.jsonObject
                val type = incObj["type"]?.jsonPrimitive?.content ?: return@forEach
                if (type != "tracks") return@forEach

                val id = incObj["id"]?.jsonPrimitive?.content ?: return@forEach
                val attrs = incObj["attributes"]?.jsonObject ?: return@forEach

                val title = attrs["title"]?.jsonPrimitive?.content ?: ""
                val trackIsrc = attrs["isrc"]?.jsonPrimitive?.contentOrNull
                val durationIso = attrs["duration"]?.jsonPrimitive?.contentOrNull ?: "PT0S"
                val durationMs = parseIso8601DurationMs(durationIso)
                val mediaTags = attrs["mediaTags"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                // Artistas asociados a este track
                val artistIds = incObj["relationships"]?.jsonObject
                    ?.get("artists")?.jsonObject
                    ?.get("data")?.jsonArray
                    ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                    ?: emptyList()
                val artistNames = artistIds.mapNotNull { artistNameById[it] }
                    .joinToString(", ")
                    .ifBlank { "Unknown Artist" }

                // Álbum asociado a este track
                val albumId = incObj["relationships"]?.jsonObject
                    ?.get("albums")?.jsonObject
                    ?.get("data")?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
                val albumTitle = albumId?.let { albumTitleById[it] } ?: "Unknown Album"

                val mappedQuality = when {
                    mediaTags.any { it.contains("HIRES", ignoreCase = true) || it.contains("HI_RES", ignoreCase = true) } -> "HI_RES_LOSSLESS"
                    mediaTags.any { it.contains("LOSSLESS", ignoreCase = true) } -> "LOSSLESS"
                    mediaTags.any { it.contains("HIGH", ignoreCase = true) } -> "HIGH"
                    else -> null
                }

                tracksById[id] = TidalTrack(
                    id = id,
                    name = title,
                    artist = artistNames,
                    album = albumTitle,
                    durationMs = durationMs,
                    isrc = trackIsrc,
                    audioQuality = mappedQuality,
                )
            }

            // 3. Obtener el orden de los tracks desde data.relationships.tracks.data
            val trackIdsInOrder = dataObj["relationships"]?.jsonObject
                ?.get("tracks")?.jsonObject
                ?.get("data")?.jsonArray
                ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                ?: emptyList()

            // Retornar los tracks resueltos en el orden del search result
            val result = trackIdsInOrder.mapNotNull { tracksById[it] }
            println("DEBUG TIDAL SEARCH TRACKS v2: query='$query' returned ${result.size} tracks")
            result
        } catch (e: Exception) {
            println("DEBUG: Tidal searchTracks failed for '$query': ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Calidad de audio en orden descendente de preferencia.
     * mediaTags en v2: HIRES_LOSSLESS, LOSSLESS, MQA, HIGH, LOSSY
     * audioQuality en v1: HI_RES_LOSSLESS, LOSSLESS, HIGH, LOW
     */
    private fun qualityScore(mediaTags: List<String>): Int = when {
        mediaTags.any { it.contains("HIRES", ignoreCase = true) || it.contains("HI_RES", ignoreCase = true) } -> 4
        mediaTags.any { it.contains("LOSSLESS", ignoreCase = true) } -> 3
        mediaTags.any { it.contains("MQA", ignoreCase = true) } -> 2
        mediaTags.any { it.contains("HIGH", ignoreCase = true) } -> 1
        else -> 0
    }

    private fun qualityScoreV1(audioQuality: String?): Int = when (audioQuality?.uppercase()) {
        "HI_RES_LOSSLESS", "HI_RES" -> 4
        "LOSSLESS" -> 3
        "HIGH"     -> 1
        else       -> 0
    }

    /**
     * Busca un track por ISRC usando el endpoint oficial v2 GET /tracks?filter[isrc]=...
     * Si hay múltiples versiones del mismo ISRC (distintas calidades),
     * devuelve la de mayor calidad: HIRES_LOSSLESS > LOSSLESS > MQA > HIGH.
     */
    suspend fun searchByIsrc(isrc: String): TidalTrack? {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val response = client.get("https://openapi.tidal.com/v2/tracks") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.api+json")
                parameter("filter[isrc]", isrc)
                parameter("include", "artists")
                parameter("countryCode", "AR")
            }
            val bodyText = response.bodyAsText()
            println("DEBUG TIDAL ISRC v2: status=${response.status} isrc=$isrc body=${bodyText.take(300)}")
            if (!response.status.isSuccess()) return null

            val bodyObj = json.parseToJsonElement(bodyText).jsonObject
            val dataArr = bodyObj["data"]?.jsonArray ?: return null
            if (dataArr.isEmpty()) return null

            // Construir mapa de artistas desde `included`
            val artistNameById = mutableMapOf<String, String>()
            bodyObj["included"]?.jsonArray?.forEach { inc ->
                val incObj = inc.jsonObject
                if (incObj["type"]?.jsonPrimitive?.content == "artists") {
                    val id   = incObj["id"]?.jsonPrimitive?.content ?: return@forEach
                    val name = incObj["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: return@forEach
                    artistNameById[id] = name
                }
            }

            // Parsear TODOS los tracks y elegir el de mayor calidad
            data class IsrcCandidate(val tidalTrack: TidalTrack, val quality: Int)

            val candidates = dataArr.mapNotNull { element ->
                val trackObj   = element.jsonObject
                val id         = trackObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val attributes = trackObj["attributes"]?.jsonObject ?: return@mapNotNull null
                val title      = attributes["title"]?.jsonPrimitive?.content ?: ""
                val trackIsrc  = attributes["isrc"]?.jsonPrimitive?.contentOrNull
                val durationIso = attributes["duration"]?.jsonPrimitive?.contentOrNull ?: "PT0S"
                val durationMs = parseIso8601DurationMs(durationIso)
                val mediaTags  = attributes["mediaTags"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val quality    = qualityScore(mediaTags)

                val artistIds = trackObj["relationships"]?.jsonObject
                    ?.get("artists")?.jsonObject
                    ?.get("data")?.jsonArray
                    ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                    ?: emptyList()
                val artist = artistIds.mapNotNull { artistNameById[it] }.joinToString(", ")
                    .ifBlank { "Unknown Artist" }

                println("DEBUG ISRC CANDIDATE: id=$id quality=$quality mediaTags=$mediaTags")

                IsrcCandidate(
                    tidalTrack = TidalTrack(
                        id         = id,
                        name       = title,
                        artist     = artist,
                        album      = "Unknown Album",
                        durationMs = durationMs,
                        isrc       = trackIsrc,
                    ),
                    quality = quality
                )
            }

            // Retornar el de mayor calidad
            candidates.maxByOrNull { it.quality }?.tidalTrack
        } catch (e: Exception) {
            println("DEBUG: Tidal searchByIsrc v2 failed for '$isrc': ${e.message}")
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
            val encodedQuery = query.encodeURLParameter()
            val response = client.get("https://openapi.tidal.com/v2/searchResults/$encodedQuery") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.api+json")
                parameter("include", "albums,artists")
                parameter("countryCode", "AR")
            }
            val bodyText = response.bodyAsText()
            println("DEBUG TIDAL SEARCH ALBUMS v2: status=${response.status} query='$query' body=${bodyText.take(300)}")
            if (!response.status.isSuccess()) return emptyList()

            val bodyObj = json.parseToJsonElement(bodyText).jsonObject
            val dataObj = bodyObj["data"]?.jsonObject ?: return emptyList()

            // 1. Construir mapa de artistas desde `included`
            val artistNameById = mutableMapOf<String, String>()
            bodyObj["included"]?.jsonArray?.forEach { inc ->
                val incObj = inc.jsonObject
                val type = incObj["type"]?.jsonPrimitive?.content ?: return@forEach
                if (type == "artists") {
                    val id = incObj["id"]?.jsonPrimitive?.content ?: return@forEach
                    val name = incObj["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: return@forEach
                    artistNameById[id] = name
                }
            }

            // 2. Parsear todos los álbumes que vienen en `included`
            val albumsById = mutableMapOf<String, com.conduit.domain.repository.TidalAlbum>()
            bodyObj["included"]?.jsonArray?.forEach { inc ->
                val incObj = inc.jsonObject
                val type = incObj["type"]?.jsonPrimitive?.content ?: return@forEach
                if (type != "albums") return@forEach

                val id = incObj["id"]?.jsonPrimitive?.content ?: return@forEach
                val attrs = incObj["attributes"]?.jsonObject ?: return@forEach
                val title = attrs["title"]?.jsonPrimitive?.content ?: ""

                // Artistas asociados a este álbum
                val artistIds = incObj["relationships"]?.jsonObject
                    ?.get("artists")?.jsonObject
                    ?.get("data")?.jsonArray
                    ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                    ?: emptyList()
                val artistNames = artistIds.mapNotNull { artistNameById[it] }
                    .joinToString(", ")
                    .ifBlank { "Unknown Artist" }

                albumsById[id] = com.conduit.domain.repository.TidalAlbum(
                    id = id,
                    name = title,
                    artist = artistNames
                )
            }

            // 3. Obtener el orden de los álbumes desde data.relationships.albums.data
            val albumIdsInOrder = dataObj["relationships"]?.jsonObject
                ?.get("albums")?.jsonObject
                ?.get("data")?.jsonArray
                ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                ?: emptyList()

            val result = albumIdsInOrder.mapNotNull { albumsById[it] }.take(limit)
            println("DEBUG TIDAL SEARCH ALBUMS v2: query='$query' returned ${result.size} albums")
            result
        } catch (e: Exception) {
            println("DEBUG: Tidal searchAlbums failed for '$query': ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getAlbumTracks(albumId: String): List<TidalTrack> {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val response = client.get("https://openapi.tidal.com/v2/albums/$albumId") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.api+json")
                parameter("include", "items,artists")
                parameter("countryCode", "AR")
            }
            val bodyText = response.bodyAsText()
            println("DEBUG TIDAL ALBUM TRACKS v2: status=${response.status} albumId=$albumId body=${bodyText.take(300)}")
            if (!response.status.isSuccess()) return emptyList()

            val bodyObj = json.parseToJsonElement(bodyText).jsonObject
            val dataObj = bodyObj["data"]?.jsonObject ?: return emptyList()
            val albumTitle = dataObj["attributes"]?.jsonObject?.get("title")?.jsonPrimitive?.content ?: "Unknown Album"

            // 1. Construir mapa de artistas desde `included`
            val artistNameById = mutableMapOf<String, String>()
            bodyObj["included"]?.jsonArray?.forEach { inc ->
                val incObj = inc.jsonObject
                if (incObj["type"]?.jsonPrimitive?.content == "artists") {
                    val id = incObj["id"]?.jsonPrimitive?.content ?: return@forEach
                    val name = incObj["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: return@forEach
                    artistNameById[id] = name
                }
            }

            // 2. Resolver el artista principal del álbum para usarlo de fallback
            val albumArtistIds = dataObj["relationships"]?.jsonObject
                ?.get("artists")?.jsonObject
                ?.get("data")?.jsonArray
                ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                ?: emptyList()
            val albumArtistName = albumArtistIds.mapNotNull { artistNameById[it] }
                .joinToString(", ")
                .ifBlank { "Unknown Artist" }

            // 3. Parsear todas las canciones que vienen en `included`
            val tracksById = mutableMapOf<String, TidalTrack>()
            bodyObj["included"]?.jsonArray?.forEach { inc ->
                val incObj = inc.jsonObject
                val type = incObj["type"]?.jsonPrimitive?.content ?: return@forEach
                if (type != "tracks") return@forEach

                val id = incObj["id"]?.jsonPrimitive?.content ?: return@forEach
                val attrs = incObj["attributes"]?.jsonObject ?: return@forEach

                val title = attrs["title"]?.jsonPrimitive?.content ?: ""
                val trackIsrc = attrs["isrc"]?.jsonPrimitive?.contentOrNull
                val durationIso = attrs["duration"]?.jsonPrimitive?.contentOrNull ?: "PT0S"
                val durationMs = parseIso8601DurationMs(durationIso)
                val mediaTags = attrs["mediaTags"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                // Intentamos resolver artistas específicos del track si estuviesen en relationships
                val trackArtistIds = incObj["relationships"]?.jsonObject
                    ?.get("artists")?.jsonObject
                    ?.get("data")?.jsonArray
                    ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                    ?: emptyList()
                val trackArtistNames = if (trackArtistIds.isNotEmpty()) {
                    trackArtistIds.mapNotNull { artistNameById[it] }.joinToString(", ").ifBlank { albumArtistName }
                } else {
                    albumArtistName
                }

                val mappedQuality = when {
                    mediaTags.any { it.contains("HIRES", ignoreCase = true) || it.contains("HI_RES", ignoreCase = true) } -> "HI_RES_LOSSLESS"
                    mediaTags.any { it.contains("LOSSLESS", ignoreCase = true) } -> "LOSSLESS"
                    mediaTags.any { it.contains("HIGH", ignoreCase = true) } -> "HIGH"
                    else -> null
                }

                tracksById[id] = TidalTrack(
                    id = id,
                    name = title,
                    artist = trackArtistNames,
                    album = albumTitle,
                    durationMs = durationMs,
                    isrc = trackIsrc,
                    audioQuality = mappedQuality,
                )
            }

            // 4. Obtener el orden correcto de las canciones desde data.relationships.items.data
            val trackIdsInOrder = dataObj["relationships"]?.jsonObject
                ?.get("items")?.jsonObject
                ?.get("data")?.jsonArray
                ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                ?: emptyList()

            val result = trackIdsInOrder.mapNotNull { tracksById[it] }
            println("DEBUG TIDAL ALBUM TRACKS v2: albumId=$albumId returned ${result.size} tracks")
            result
        } catch (e: Exception) {
            println("DEBUG: Tidal getAlbumTracks failed for album '$albumId': ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getTrack(trackId: String): TidalTrack? {
        return try {
            val token = tokenRefreshPlugin.getValidToken("tidal")
            val response = client.get("https://openapi.tidal.com/v2/tracks/$trackId") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.api+json")
                parameter("include", "artists,albums")
                parameter("countryCode", "AR")
            }
            val bodyText = response.bodyAsText()
            println("DEBUG TIDAL GET TRACK v2: status=${response.status} trackId=$trackId body=${bodyText.take(300)}")
            if (!response.status.isSuccess()) return null

            val bodyObj = json.parseToJsonElement(bodyText).jsonObject
            val dataObj = bodyObj["data"]?.jsonObject ?: return null

            // 1. Construir mapa de artistas y álbumes desde `included`
            val artistNameById = mutableMapOf<String, String>()
            val albumTitleById = mutableMapOf<String, String>()

            bodyObj["included"]?.jsonArray?.forEach { inc ->
                val incObj = inc.jsonObject
                val type = incObj["type"]?.jsonPrimitive?.content ?: return@forEach
                val id = incObj["id"]?.jsonPrimitive?.content ?: return@forEach
                val attrs = incObj["attributes"]?.jsonObject ?: return@forEach

                when (type) {
                    "artists" -> {
                        attrs["name"]?.jsonPrimitive?.contentOrNull?.let { name ->
                            artistNameById[id] = name
                        }
                    }
                    "albums" -> {
                        attrs["title"]?.jsonPrimitive?.contentOrNull?.let { title ->
                            albumTitleById[id] = title
                        }
                    }
                }
            }

            // 2. Parsear el track principal
            val attrs = dataObj["attributes"]?.jsonObject ?: return null
            val title = attrs["title"]?.jsonPrimitive?.content ?: ""
            val trackIsrc = attrs["isrc"]?.jsonPrimitive?.contentOrNull
            val durationIso = attrs["duration"]?.jsonPrimitive?.contentOrNull ?: "PT0S"
            val durationMs = parseIso8601DurationMs(durationIso)
            val mediaTags = attrs["mediaTags"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

            // Artistas asociados a este track
            val artistIds = dataObj["relationships"]?.jsonObject
                ?.get("artists")?.jsonObject
                ?.get("data")?.jsonArray
                ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                ?: emptyList()
            val artistNames = artistIds.mapNotNull { artistNameById[it] }
                .joinToString(", ")
                .ifBlank { "Unknown Artist" }

            // Álbum asociado a este track
            val albumId = dataObj["relationships"]?.jsonObject
                ?.get("albums")?.jsonObject
                ?.get("data")?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
            val albumTitle = albumId?.let { albumTitleById[it] } ?: "Unknown Album"

            val mappedQuality = when {
                mediaTags.any { it.contains("HIRES", ignoreCase = true) || it.contains("HI_RES", ignoreCase = true) } -> "HI_RES_LOSSLESS"
                mediaTags.any { it.contains("LOSSLESS", ignoreCase = true) } -> "LOSSLESS"
                mediaTags.any { it.contains("HIGH", ignoreCase = true) } -> "HIGH"
                else -> null
            }

            TidalTrack(
                id = trackId,
                name = title,
                artist = artistNames,
                album = albumTitle,
                durationMs = durationMs,
                isrc = trackIsrc,
                audioQuality = mappedQuality,
            )
        } catch (e: Exception) {
            println("DEBUG: Tidal getTrack failed for '$trackId': ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Parsea un TidalTrack desde un JsonObject de la API v1 (listen.tidal.com).
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
            audioQuality = obj["audioQuality"]?.jsonPrimitive?.contentOrNull,
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

    /**
     * Convierte duración ISO 8601 (ej. "PT3M42S", "PT58S", "PT1H2M3S") a milisegundos.
     * Necesario para parsear el campo `duration` de la API v2.
     */
    private fun parseIso8601DurationMs(iso: String): Long {
        // Regex: PT[xH][xM][xS]
        val hours   = Regex("(\\d+)H").find(iso)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val minutes = Regex("(\\d+)M").find(iso)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val seconds = Regex("(\\d+(?:\\.\\d+)?)S").find(iso)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return (hours * 3600 + minutes * 60) * 1000 + (seconds * 1000).toLong()
    }
}
