package com.conduit.domain.usecase

import com.conduit.domain.model.AudioFeaturesItem
import com.conduit.domain.repository.SpotifyRepository

class GetTrackAudioFeaturesUseCase(
    private val spotifyRepository: SpotifyRepository
) {
    suspend operator fun invoke(trackId: String): AudioFeaturesItem? {
        return spotifyRepository.getAudioFeatures(listOf(trackId)).firstOrNull()
    }
}
