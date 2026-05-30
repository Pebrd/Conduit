package com.conduit.data.lastfm

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class LastFmClient(
    private val client: HttpClient,
) {
    private var apiKey: String = ""

    fun setApiKey(key: String) {
        apiKey = key
    }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    suspend fun getSimilarArtists(artistName: String, limit: Int = 10): List<LastFmArtist> {
        if (!hasApiKey()) return emptyList()
        return try {
            val response = client.get("https://ws.audioscrobbler.com/2.0/") {
                parameter("method", "artist.getsimilar")
                parameter("artist", artistName)
                parameter("api_key", apiKey)
                parameter("format", "json")
                parameter("limit", limit)
            }
            if (!response.status.isSuccess()) return emptyList()
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<LastFmSimilarArtistsResponse>(response.bodyAsText())
                .similarartists?.artist
                ?.filter { it.match.toDoubleOrNull() ?: 0.0 > 0.5 }
                ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getTopTracks(artistName: String, limit: Int = 10): List<LastFmTrack> {
        if (!hasApiKey()) return emptyList()
        return try {
            val response = client.get("https://ws.audioscrobbler.com/2.0/") {
                parameter("method", "artist.gettoptracks")
                parameter("artist", artistName)
                parameter("api_key", apiKey)
                parameter("format", "json")
                parameter("limit", limit)
            }
            if (!response.status.isSuccess()) return emptyList()
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<LastFmTopTracksResponse>(response.bodyAsText())
                .toptracks?.track
                ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getSimilarArtistsTopTracks(artistName: String, artistsLimit: Int = 5, tracksPerArtist: Int = 5): List<String> {
        val similar = getSimilarArtists(artistName, artistsLimit)
        val queries = mutableListOf<String>()
        similar.forEach { artist ->
            val topTracks = getTopTracks(artist.name, tracksPerArtist)
            topTracks.forEach { track ->
                queries.add("${track.name} ${artist.name}")
            }
        }
        return queries
    }

    suspend fun getArtistInfo(artistName: String): LastFmArtistInfo? {
        if (!hasApiKey() || artistName.isBlank()) return null
        return try {
            val response = client.get("https://ws.audioscrobbler.com/2.0/") {
                parameter("method", "artist.getinfo")
                parameter("artist", artistName)
                parameter("api_key", apiKey)
                parameter("format", "json")
            }
            if (!response.status.isSuccess()) return null
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<LastFmArtistInfoResponse>(response.bodyAsText()).artist
        } catch (_: Exception) { null }
    }

    suspend fun getSimilarTracks(trackName: String, artistName: String, limit: Int = 10): List<LastFmSimilarTrackItem> {
        if (!hasApiKey() || trackName.isBlank() || artistName.isBlank()) return emptyList()
        return try {
            val response = client.get("https://ws.audioscrobbler.com/2.0/") {
                parameter("method", "track.getsimilar")
                parameter("artist", artistName)
                parameter("track", trackName)
                parameter("api_key", apiKey)
                parameter("format", "json")
                parameter("limit", limit)
            }
            if (!response.status.isSuccess()) return emptyList()
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<LastFmSimilarTracksResponse>(response.bodyAsText())
                .similartracks?.track
                ?.filter { it.match.toDoubleOrNull() ?: 0.0 > 0.5 }
                ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }
}
