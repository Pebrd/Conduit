package com.conduit.domain.usecase

import com.conduit.domain.model.GenreCount
import com.conduit.domain.model.TopArtistItem
import com.conduit.domain.repository.SpotifyRepository

class GetTopArtistsUseCase(
    private val spotifyRepository: SpotifyRepository
) {
    suspend operator fun invoke(timeRange: String = "medium_term", limit: Int = 20): List<TopArtistItem> {
        return spotifyRepository.getTopArtists(timeRange, limit)
    }

    suspend fun getArtistById(artistId: String): TopArtistItem? {
        return spotifyRepository.getArtistDetail(artistId)
    }

    suspend fun getGenreDistribution(limit: Int = 50): List<GenreCount> {
        val artists = spotifyRepository.getTopArtists("long_term", limit)
        val genreCounts = mutableMapOf<String, Int>()
        artists.forEach { artist ->
            artist.genres.forEach { genre ->
                genreCounts[genre] = (genreCounts[genre] ?: 0) + 1
            }
        }
        return genreCounts.entries
            .sortedByDescending { it.value }
            .map { GenreCount(it.key, it.value) }
    }
}
