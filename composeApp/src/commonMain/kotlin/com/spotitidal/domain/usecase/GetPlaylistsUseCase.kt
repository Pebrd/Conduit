package com.spotitidal.domain.usecase

import com.spotitidal.domain.model.Playlist
import com.spotitidal.domain.repository.SpotifyRepository

class GetPlaylistsUseCase(
    private val spotifyRepo: SpotifyRepository
) {
    suspend operator fun invoke(): List<Playlist> {
        return spotifyRepo.getPlaylists()
    }
}
