package com.conduit.domain.usecase

import com.conduit.domain.model.UserProfile
import com.conduit.domain.repository.SpotifyRepository

class GetUserProfileUseCase(
    private val spotifyRepository: SpotifyRepository
) {
    suspend operator fun invoke(): UserProfile {
        return spotifyRepository.getProfile()
    }
}
