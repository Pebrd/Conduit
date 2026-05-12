package com.spotitidal.domain.usecase

import com.spotitidal.data.sync.SyncEngine
import com.spotitidal.domain.model.*
import com.spotitidal.domain.repository.SpotifyRepository
import com.spotitidal.domain.repository.TidalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

class SyncPlaylistUseCase(
    private val spotifyRepo: SpotifyRepository,
    private val tidalRepo: TidalRepository,
    private val syncEngine: SyncEngine,
) {
    suspend operator fun invoke(
        spotifyPlaylistId: String,
        spotifyPlaylistName: String,
        tidalPlaylistId: String? = null
    ): Flow<SyncProgress> = flow {
        val startTime = Clock.System.now().toEpochMilliseconds()
        val spotifyTracks = spotifyRepo.getPlaylistTracks(spotifyPlaylistId)
        val targetPlaylistId = tidalPlaylistId ?: tidalRepo.createPlaylist(spotifyPlaylistName)
        val existingTidalIds = tidalRepo.getPlaylistTrackIds(targetPlaylistId)
        
        val matched = mutableListOf<MatchedTrack>()
        val notFound = mutableListOf<Track>()
        val lowConfidence = mutableListOf<LowConfidenceMatch>()
        val duplicates = mutableListOf<Track>()
        val blacklisted = mutableListOf<Track>()

        spotifyTracks.forEachIndexed { index, track ->
            emit(SyncProgress.Running(
                current = index + 1,
                total = spotifyTracks.size,
                currentTrack = track.name,
                matched = matched.size,
                notFound = notFound.size,
                duplicates = duplicates.size
            ))

            val result = syncEngine.findTidalTrack(track, spotifyPlaylistId, existingTidalIds)
            when (result) {
                is MatchResult.Found -> matched.add(MatchedTrack(track, result.track, result.method, result.score))
                is MatchResult.LowConfidence -> lowConfidence.add(LowConfidenceMatch(track, result.track, result.score))
                is MatchResult.Duplicate -> duplicates.add(track)
                is MatchResult.NotFound -> notFound.add(track)
                is MatchResult.Blacklisted -> blacklisted.add(track)
            }
        }

        // Add matched tracks to Tidal playlist
        if (matched.isNotEmpty()) {
            tidalRepo.addTracksToPlaylist(targetPlaylistId, matched.map { it.tidal.id })
        }

        emit(SyncProgress.Completed(
            matched = matched.size,
            notFound = notFound.size,
            duplicates = duplicates.size,
            durationMs = Clock.System.now().toEpochMilliseconds() - startTime
        ))
    }
}

sealed class SyncProgress {
    data class Running(
        val current: Int,
        val total: Int,
        val currentTrack: String,
        val matched: Int,
        val notFound: Int,
        val duplicates: Int,
    ) : SyncProgress()

    data class Completed(
        val matched: Int,
        val notFound: Int,
        val duplicates: Int,
        val durationMs: Long,
    ) : SyncProgress()
}
