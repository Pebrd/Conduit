package com.conduit.domain.usecase

import com.conduit.domain.model.*
import com.conduit.domain.repository.SpotifyRepository

class BuildMoodProfileUseCase(
    private val spotifyRepo: SpotifyRepository,
) {
    suspend fun fromTrack(track: Track): List<String> {
        return listOf(track.id)
    }

    suspend fun fromPlaylist(playlistId: String): List<String> {
        val tracks = spotifyRepo.getPlaylistTracks(playlistId)
        return tracks.map { it.id }.take(5)
    }
}
