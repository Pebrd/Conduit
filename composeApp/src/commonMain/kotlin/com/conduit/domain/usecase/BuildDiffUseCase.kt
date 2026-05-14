package com.conduit.domain.usecase

import com.conduit.domain.model.*
import com.conduit.domain.repository.SpotifyRepository
import com.conduit.domain.repository.TidalRepository

class BuildDiffUseCase(
    private val spotifyRepo: SpotifyRepository,
    private val tidalRepo: TidalRepository,
) {
    suspend operator fun invoke(spotifyPlaylistId: String, tidalPlaylistId: String?): List<DiffEntry> {
        val spotifyTracks = spotifyRepo.getPlaylistTracks(spotifyPlaylistId)
        val tidalTrackIds = tidalPlaylistId?.let { tidalRepo.getPlaylistTrackIds(it) } ?: emptySet()

        return spotifyTracks.map { track ->
            val status = if (track.id in tidalTrackIds) DiffStatus.OK else DiffStatus.NEW
            DiffEntry(track = track, status = status)
        }
        // Note: MISSING status (tracks in Tidal but not in Spotify) could be added here if needed
    }
}
