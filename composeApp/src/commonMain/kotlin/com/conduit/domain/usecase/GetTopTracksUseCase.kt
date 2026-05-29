package com.conduit.domain.usecase

import com.conduit.domain.model.TopTrackItem
import com.conduit.domain.repository.SpotifyRepository

class GetTopTracksUseCase(
    private val spotifyRepository: SpotifyRepository
) {
    suspend operator fun invoke(timeRange: String = "medium_term", limit: Int = 20): List<TopTrackItem> {
        return spotifyRepository.getTopTracks(timeRange, limit)
    }

    suspend fun getTrackById(trackId: String): TopTrackItem? {
        return spotifyRepository.getTrack(trackId)
    }
}
