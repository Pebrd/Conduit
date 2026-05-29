package com.conduit.domain.usecase

import com.conduit.domain.model.RecentlyPlayedItem
import com.conduit.domain.repository.SpotifyRepository

class GetRecentlyPlayedUseCase(
    private val spotifyRepository: SpotifyRepository
) {
    suspend operator fun invoke(limit: Int = 20): List<RecentlyPlayedItem> {
        return spotifyRepository.getRecentlyPlayed(limit)
    }
}
