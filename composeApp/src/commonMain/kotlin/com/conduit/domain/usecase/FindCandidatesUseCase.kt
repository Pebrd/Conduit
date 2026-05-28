package com.conduit.domain.usecase

import com.conduit.data.itunes.ITunesClient
import com.conduit.data.musicbrainz.MusicBrainzClient
import com.conduit.data.musicbrainz.MusicBrainzRecording
import com.conduit.domain.model.*
import kotlinx.coroutines.delay

class FindCandidatesUseCase(
    private val mbClient: MusicBrainzClient,
    private val iTunesClient: ITunesClient,
) {
    suspend fun invoke(
        profile: MoodProfile,
        alreadySeen: Set<String>,
        limit: Int = 30,
    ): List<DiscoverTrack> {
        val topGenres = profile.genres.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        val candidates = mutableListOf<MusicBrainzRecording>()

        topGenres.forEach { genre ->
            delay(1_100)
            candidates.addAll(mbClient.searchByGenre(genre, limit = 15))
        }

        profile.relatedArtistMbids.take(3).forEach { artistMbid ->
            delay(1_100)
            candidates.addAll(mbClient.searchByArtist(artistMbid, limit = 10))
        }

        val filtered = candidates
            .distinctBy { it.mbid }
            .filter { it.mbid !in alreadySeen }
            .filter { it.isrc !in profile.seedIsrcs }

        return filtered.mapNotNull { recording ->
            val iTunes = recording.isrc?.let { iTunesClient.getPreview(it) }
                ?: iTunesClient.searchPreview("${recording.title} ${recording.artist}")
            iTunes ?: return@mapNotNull null

            DiscoverTrack(
                mbid = recording.mbid,
                name = recording.title,
                artist = recording.artist,
                album = "",
                durationMs = recording.durationMs,
                isrc = recording.isrc,
                genres = recording.tags.take(3),
                previewUrl = iTunes.previewUrl,
                artworkUrl = iTunes.artworkUrl,
                matchScore = computeMatchScore(recording.tags, profile),
            )
        }.sortedByDescending { it.matchScore }
            .take(limit)
    }

    private fun computeMatchScore(trackTags: List<String>, profile: MoodProfile): Double {
        val allWeights = profile.genres + profile.tags
        return trackTags.sumOf { tag -> allWeights[tag] ?: 0.0 } / trackTags.size.coerceAtLeast(1)
    }
}
