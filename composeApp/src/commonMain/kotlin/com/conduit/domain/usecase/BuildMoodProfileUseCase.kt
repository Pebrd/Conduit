package com.conduit.domain.usecase

import com.conduit.domain.model.*
import com.conduit.domain.repository.SpotifyRepository

class BuildMoodProfileUseCase(
    private val spotifyRepo: SpotifyRepository,
) {
    suspend fun fromTrack(track: Track): List<Track> {
        return listOf(track)
    }

    suspend fun fromPlaylist(playlistId: String): List<Track> {
        val tracks = spotifyRepo.getPlaylistTracks(playlistId)
        return tracks
            .groupBy { it.artist }
            .flatMap { (_, artistTracks) -> artistTracks.take(2) }
    }
}
