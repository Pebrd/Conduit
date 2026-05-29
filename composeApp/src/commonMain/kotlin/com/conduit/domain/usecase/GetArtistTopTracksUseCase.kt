package com.conduit.domain.usecase

import com.conduit.domain.model.TopTrackItem
import com.conduit.domain.repository.SpotifyRepository

class GetArtistTopTracksUseCase(
    private val spotifyRepository: SpotifyRepository
) {
    suspend operator fun invoke(artistId: String): List<TopTrackItem> {
        return spotifyRepository.getArtistTopTracks(artistId)
    }
}
