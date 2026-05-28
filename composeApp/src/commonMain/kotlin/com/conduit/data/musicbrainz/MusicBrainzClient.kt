package com.conduit.data.musicbrainz

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

class MusicBrainzClient(private val client: HttpClient) {

    private val userAgent = "Conduit/1.0 (conduit-app@proton.me)"
    private val rateLimiter = Semaphore(1)

    suspend fun getByIsrc(isrc: String): MusicBrainzRecording? = withRateLimit {
        val response = client.get("https://musicbrainz.org/ws/2/recording") {
            header("User-Agent", userAgent)
            parameter("query", "isrc:$isrc")
            parameter("fmt", "json")
        }
        if (!response.status.isSuccess()) return@withRateLimit null
        Json { ignoreUnknownKeys = true }
            .parseToJsonElement(response.bodyAsText())
            .jsonObject["recordings"]?.jsonArray
            ?.firstOrNull()
            ?.let { parseRecording(it.jsonObject) }
    }

    suspend fun searchByName(name: String, artist: String): MusicBrainzRecording? = withRateLimit {
        val query = "\"${name.replace("\"", "")}\" AND artist:\"${artist.replace("\"", "")}\""
        val response = client.get("https://musicbrainz.org/ws/2/recording") {
            header("User-Agent", userAgent)
            parameter("query", query)
            parameter("fmt", "json")
            parameter("limit", 5)
        }
        if (!response.status.isSuccess()) return@withRateLimit null
        Json { ignoreUnknownKeys = true }
            .parseToJsonElement(response.bodyAsText())
            .jsonObject["recordings"]?.jsonArray
            ?.firstOrNull()
            ?.let { parseRecording(it.jsonObject) }
    }

    suspend fun searchByGenre(genre: String, limit: Int = 25, offset: Int = 0): List<MusicBrainzRecording> = withRateLimit {
        val response = client.get("https://musicbrainz.org/ws/2/recording") {
            header("User-Agent", userAgent)
            parameter("query", "tag:${genre.replace(" ", "+")}")
            parameter("fmt", "json")
            parameter("limit", limit)
            parameter("offset", offset)
        }
        if (!response.status.isSuccess()) return@withRateLimit emptyList()
        Json { ignoreUnknownKeys = true }
            .parseToJsonElement(response.bodyAsText())
            .jsonObject["recordings"]?.jsonArray
            ?.mapNotNull { parseRecording(it.jsonObject) }
            ?: emptyList()
    }

    suspend fun getRelatedArtists(artistMbid: String): List<MusicBrainzArtist> = withRateLimit {
        val response = client.get("https://musicbrainz.org/ws/2/artist/$artistMbid") {
            header("User-Agent", userAgent)
            parameter("inc", "artist-rels")
            parameter("fmt", "json")
        }
        if (!response.status.isSuccess()) return@withRateLimit emptyList()
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        root["relations"]?.jsonArray
            ?.mapNotNull { rel ->
                val obj = rel.jsonObject
                if (obj["type"]?.jsonPrimitive?.content in setOf("collaborated with", "member of band", "musical colleague")) {
                    val artist = obj["artist"]?.jsonObject ?: return@mapNotNull null
                    val mbid = artist["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = artist["name"]?.jsonPrimitive?.content ?: ""
                    MusicBrainzArtist(mbid, name)
                } else null
            }
            ?: emptyList()
    }

    suspend fun searchByArtist(artistMbid: String, limit: Int = 10): List<MusicBrainzRecording> = withRateLimit {
        val response = client.get("https://musicbrainz.org/ws/2/recording") {
            header("User-Agent", userAgent)
            parameter("query", "arid:$artistMbid")
            parameter("fmt", "json")
            parameter("limit", limit)
        }
        if (!response.status.isSuccess()) return@withRateLimit emptyList()
        Json { ignoreUnknownKeys = true }
            .parseToJsonElement(response.bodyAsText())
            .jsonObject["recordings"]?.jsonArray
            ?.mapNotNull { parseRecording(it.jsonObject) }
            ?: emptyList()
    }

    private suspend fun <T> withRateLimit(block: suspend () -> T): T = rateLimiter.withPermit {
        delay(1_100)
        block()
    }

    private fun parseRecording(obj: JsonObject): MusicBrainzRecording? {
        val mbid = obj["id"]?.jsonPrimitive?.content ?: return null
        val title = obj["title"]?.jsonPrimitive?.content ?: return null
        val length = obj["length"]?.jsonPrimitive?.longOrNull ?: 0L
        val artistCredit = obj["artist-credit"]?.jsonArray?.firstOrNull()?.jsonObject
        val artist = artistCredit?.get("artist")?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
        val artistMbid = artistCredit?.get("artist")?.jsonObject?.get("id")?.jsonPrimitive?.content ?: ""
        val tags = obj["tags"]?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content } ?: emptyList()
        val isrcs = obj["isrcs"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content } ?: emptyList()
        return MusicBrainzRecording(mbid, title, artist, length, tags, isrcs.firstOrNull(), artistMbid)
    }
}
