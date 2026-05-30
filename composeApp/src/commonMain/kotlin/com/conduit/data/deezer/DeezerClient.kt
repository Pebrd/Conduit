package com.conduit.data.deezer

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class DeezerClient(
    private val client: HttpClient,
) {
    suspend fun getSimilarArtistsTopTracks(
        artistName: String,
        artistsLimit: Int = 5,
        tracksPerArtist: Int = 5,
    ): List<Pair<String, String>> {
        if (artistName.isBlank()) return emptyList()

        return try {
            val artistId = searchArtist(artistName) ?: return emptyList()
            val related = getRelatedArtists(artistId, artistsLimit)
            val queries = mutableListOf<Pair<String, String>>()

            related.forEach { artist ->
                val topTracks = getTopTracks(artist.id, tracksPerArtist)
                topTracks.forEach { track ->
                    queries.add(track.title to artist.name)
                }
            }

            queries
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun searchArtist(name: String): Long? {
        return try {
            val response = client.get("https://api.deezer.com/search/artist") {
                parameter("q", name)
                parameter("limit", 3)
            }
            if (!response.status.isSuccess()) return null
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<DeezerArtistSearchResult>(response.bodyAsText())
                .data
                .firstOrNull()
                ?.id
        } catch (_: Exception) { null }
    }

    private suspend fun getRelatedArtists(artistId: Long, limit: Int = 5): List<DeezerArtist> {
        return try {
            val response = client.get("https://api.deezer.com/artist/$artistId/related") {
                parameter("limit", limit)
            }
            if (!response.status.isSuccess()) return emptyList()
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<DeezerRelatedResult>(response.bodyAsText()).data
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun getTopTracks(artistId: Long, limit: Int = 5): List<DeezerTrack> {
        return try {
            val response = client.get("https://api.deezer.com/artist/$artistId/top") {
                parameter("limit", limit)
            }
            if (!response.status.isSuccess()) return emptyList()
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<DeezerTopTracksResult>(response.bodyAsText()).data
        } catch (_: Exception) { emptyList() }
    }
}
