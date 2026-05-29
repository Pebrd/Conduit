package com.conduit.data.itunes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class ITunesClient(private val client: HttpClient) {

    suspend fun getPreview(isrc: String): ITunesResult? = search(isrc)

    suspend fun searchPreview(query: String): ITunesResult? = search(query)

    suspend fun searchTracks(query: String, limit: Int = 15): List<ITunesResult> {
        return try {
            val response = client.get("https://itunes.apple.com/search") {
                parameter("term", query)
                parameter("media", "music")
                parameter("limit", limit)
            }
            if (!response.status.isSuccess()) return emptyList()
            Json { ignoreUnknownKeys = true }
                .parseToJsonElement(response.bodyAsText())
                .jsonObject["results"]?.jsonArray
                ?.mapNotNull { it.jsonObject?.let { obj -> parseResult(obj) } }
                ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun search(term: String): ITunesResult? {
        return try {
            val response = client.get("https://itunes.apple.com/search") {
                parameter("term", term)
                parameter("media", "music")
                parameter("limit", 5)
            }
            if (!response.status.isSuccess()) return null
            Json { ignoreUnknownKeys = true }
                .parseToJsonElement(response.bodyAsText())
                .jsonObject["results"]?.jsonArray
                ?.firstOrNull { it.jsonObject["previewUrl"] != null }
                ?.let { parseResult(it.jsonObject) }
        } catch (_: Exception) { null }
    }

    private fun parseResult(obj: JsonObject) = ITunesResult(
        trackId    = obj["trackId"]?.jsonPrimitive?.longOrNull ?: 0L,
        trackName  = obj["trackName"]?.jsonPrimitive?.content ?: "",
        artistName = obj["artistName"]?.jsonPrimitive?.content ?: "",
        albumName  = obj["collectionName"]?.jsonPrimitive?.content ?: "",
        previewUrl = obj["previewUrl"]?.jsonPrimitive?.content,
        artworkUrl = obj["artworkUrl100"]?.jsonPrimitive?.content?.replace("100x100bb", "600x600bb"),
        durationMs = obj["trackTimeMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
    )
}
