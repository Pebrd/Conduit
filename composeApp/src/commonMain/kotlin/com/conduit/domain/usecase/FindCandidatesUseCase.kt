package com.conduit.domain.usecase

import com.conduit.data.deezer.DeezerClient
import com.conduit.data.itunes.ITunesClient
import com.conduit.data.lastfm.LastFmClient
import com.conduit.domain.model.*
import com.conduit.domain.repository.SpotifyRepository
import com.conduit.domain.util.TrackNormalizer.normalize
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class FindCandidatesUseCase(
    private val spotifyRepo: SpotifyRepository,
    private val lastFmClient: LastFmClient,
    private val deezerClient: DeezerClient,
    private val iTunesClient: ITunesClient,
) {
    suspend operator fun invoke(
        seedTrackIds: List<String>,
        artistName: String,
        alreadySeen: Set<String>,
        alreadySeenNames: Set<String> = emptySet(),
        limit: Int = 30,
    ): List<DiscoverTrack> {
        if (seedTrackIds.isEmpty()) return emptyList()

        val seenArtistTitles = mutableSetOf<String>()
        seenArtistTitles.addAll(alreadySeenNames)
        val seenIds = mutableSetOf<String>()

        // Step 1: Fetch seed track names from Spotify
        val seedTracks = seedTrackIds.mapNotNull { id ->
            try { spotifyRepo.getTrack(id) } catch (_: Exception) { null }
        }

        // Step 2: Use Last.fm track.getSimilar for recommendations
        val searchQueries = mutableListOf<Pair<String, String>>() // trackName, artistName
        val matchScores = mutableMapOf<String, Double>() // normalized key -> match score

        if (lastFmClient.hasApiKey()) {
            for (seed in seedTracks) {
                val similar = lastFmClient.getSimilarTracks(seed.name, seed.artist.split(",").first().trim(), limit = 5)
                similar.forEach { rec ->
                    val recArtist = rec.artist?.name ?: return@forEach
                    if (rec.name.isNotBlank() && recArtist.isNotBlank()) {
                        val scoreKey = normalize(rec.name, recArtist)
                        matchScores[scoreKey] = rec.match.toDoubleOrNull() ?: 1.0
                        searchQueries.add(rec.name to recArtist)
                    }
                }
            }
        }

        // Step 3: Fallback — Deezer
        if (searchQueries.isEmpty() && artistName.isNotBlank()) {
            val deezerQueries = deezerClient.getSimilarArtistsTopTracks(artistName, 5, 5)
            deezerQueries.forEach { query ->
                // query is "trackName artistName" — split to use field search
                val parts = query.split(" ")
                if (parts.size >= 2) {
                    val artist = parts.last()
                    val track = parts.dropLast(1).joinToString(" ")
                    searchQueries.add(track to artist)
                } else {
                    searchQueries.add(query to artistName)
                }
            }
        }

        // Step 4: Fallback — get recommendations via related artists
        if (searchQueries.isEmpty() && artistName.isNotBlank()) {
            try {
                val related = spotifyRepo.getRecommendationsViaRelatedArtists(seedTrackIds, alreadySeen, limit)
                for (track in related) {
                    searchQueries.add(track.name to track.artist.split(",").first().trim())
                }
            } catch (_: Exception) { }
        }

        // Step 5: Last resort — search by seed artist directly
        if (searchQueries.isEmpty() && artistName.isNotBlank()) {
            searchQueries.add("" to artistName)
        }

        // Step 6: Search each recommendation on Spotify with field-filtered query
        val tracks = mutableListOf<Track>()

        for ((recTrackName, recArtist) in searchQueries) {
            if (tracks.size >= limit) break
            try {
                val query = if (recTrackName.isNotBlank()) {
                    "track:\"${recTrackName.replace("\"", "")}\" artist:\"${recArtist.replace("\"", "")}\""
                } else {
                    "artist:\"${recArtist.replace("\"", "")}\""
                }
                val results = spotifyRepo.searchTracksByQuery(query, limit = 3)
                results.filter { it.id !in alreadySeen && it.id !in seenIds }.forEach { track ->
                    val norm = normalize(track)
                    if (norm in seenArtistTitles) return@forEach
                    seenIds.add(track.id)
                    seenArtistTitles.add(norm)
                    tracks.add(track)
                }
            } catch (_: Exception) { }
            if (tracks.size >= limit) break
        }

        // Step 7: If we got too few results, do a broader fallback search
        if (tracks.size < limit / 2 && artistName.isNotBlank()) {
            try {
                val broadQuery = "artist:\"${artistName.replace("\"", "")}\""
                val more = spotifyRepo.searchTracksByQuery(broadQuery, limit = limit - tracks.size)
                more.filter { it.id !in alreadySeen && it.id !in seenIds }.forEach { track ->
                    val norm = normalize(track)
                    if (norm in seenArtistTitles) return@forEach
                    seenIds.add(track.id)
                    seenArtistTitles.add(norm)
                    tracks.add(track)
                }
            } catch (_: Exception) { }
        }

        return coroutineScope {
            tracks.map { track ->
                async {
                    val previewUrl = track.previewUrl ?: findITunesPreview(track)
                    val genres = track.artistIds.firstOrNull()?.let { artistId ->
                        try { spotifyRepo.getArtistDetail(artistId)?.genres ?: emptyList() } catch (_: Exception) { emptyList() }
                    } ?: emptyList()
                    DiscoverTrack(
                        mbid = track.id,
                        name = track.name,
                        artist = track.artist,
                        album = track.album,
                        durationMs = track.durationMs,
                        isrc = track.isrc,
                        previewUrl = previewUrl,
                        artworkUrl = track.imageUrl,
                        genres = genres,
                        matchScore = matchScores[normalize(track)] ?: 1.0,
                    )
                }
            }.awaitAll()
        }
    }

    suspend fun refill(
        seedTrackIds: List<String>,
        artistName: String,
        alreadySeen: Set<String>,
        alreadySeenNames: Set<String> = emptySet(),
        limit: Int = 20,
    ): List<DiscoverTrack> = invoke(seedTrackIds, artistName, alreadySeen, alreadySeenNames, limit)

    private suspend fun findITunesPreview(track: Track): String? {
        return try {
            track.isrc?.let { iTunesClient.getPreview(it) }?.previewUrl
                ?: iTunesClient.searchPreview("${track.name} ${track.artist}")?.previewUrl
        } catch (_: Exception) { null }
    }
}
