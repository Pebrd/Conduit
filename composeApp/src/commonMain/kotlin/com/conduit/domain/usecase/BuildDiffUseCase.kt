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
        // Get Tidal ISRCs for comparison (IDs differ between services, ISRCs are universal)
        val tidalIsrcs = if (tidalPlaylistId != null) {
            tidalRepo.getPlaylistExistingTracks(tidalPlaylistId).second
        } else emptySet()

        return spotifyTracks.map { track ->
            val matched = track.isrc?.let { it in tidalIsrcs } ?: false
            val status = if (matched) DiffStatus.OK else DiffStatus.NEW
            DiffEntry(track = track, status = status)
        }
        // TODO: MISSING status (tracks in Tidal but not in Spotify) can be added when SyncEngine tracks those
    }
}
