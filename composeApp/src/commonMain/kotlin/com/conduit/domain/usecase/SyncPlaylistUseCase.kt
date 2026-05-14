package com.conduit.domain.usecase

import com.conduit.data.sync.*
import com.conduit.domain.model.*
import com.conduit.domain.repository.SpotifyRepository
import com.conduit.domain.repository.TidalRepository
import kotlinx.datetime.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*

class SyncPlaylistUseCase(
    private val spotifyRepo: SpotifyRepository,
    private val tidalRepo: TidalRepository,
    private val syncEngine: SyncEngine,
) {
    companion object {
        const val MAX_CONCURRENT_TRACKS = 5  // conservador para respetar rate limits
    }

    operator fun invoke(playlistId: String): Flow<SyncProgress> = flow {
        val startTime        = Clock.System.now().toEpochMilliseconds()
        val spotifyTracks    = spotifyRepo.getPlaylistTracks(playlistId)
        val existingTidalIds = tidalRepo.getPlaylistTrackIds(playlistId).toMutableSet()

        val semaphore     = Semaphore(MAX_CONCURRENT_TRACKS)
        val matched       = mutableListOf<MatchedTrack>()
        val notFound      = mutableListOf<Track>()
        val lowConfidence = mutableListOf<LowConfidenceMatch>()
        val duplicates    = mutableListOf<Track>()
        val blacklisted   = mutableListOf<Track>()
        var processed     = 0

        coroutineScope {
            val deferredList = spotifyTracks.map { track ->
                async {
                    semaphore.withPermit {
                        track to syncEngine.findTidalTrack(track, playlistId, existingTidalIds)
                    }
                }
            }

            deferredList.forEach { deferred ->
                val (spotifyTrack, result) = deferred.await()
                processed++

                when (result) {
                    is MatchResult.Found -> {
                        val matchedTrack = MatchedTrack(spotifyTrack, result.track, result.method, result.score)
                        matched.add(matchedTrack)
                        existingTidalIds.add(result.track.id)
                    }
                    is MatchResult.LowConfidence -> lowConfidence.add(LowConfidenceMatch(spotifyTrack, result.track, result.score))
                    is MatchResult.Duplicate     -> duplicates.add(spotifyTrack)
                    is MatchResult.NotFound      -> notFound.add(spotifyTrack)
                    is MatchResult.Blacklisted   -> blacklisted.add(spotifyTrack)
                }

                emit(SyncProgress.Running(
                    current      = processed,
                    total        = spotifyTracks.size,
                    currentTrack = spotifyTrack.name,
                    matched      = matched.size,
                    notFound     = notFound.size,
                    duplicates   = duplicates.size,
                ))
            }
        }

        // Add matched tracks to Tidal playlist in batches (max 50 per request)
        if (matched.isNotEmpty()) {
            tidalRepo.addTracksToPlaylist(playlistId, matched.map { it.tidal.id })
        }

        val endTime = Clock.System.now().toEpochMilliseconds()
        emit(SyncProgress.Completed(
            result = SyncResult(
                id            = "sync_${endTime}",
                playlistId    = playlistId,
                playlistName  = spotifyTracks.firstOrNull()?.name ?: playlistId,
                matched       = matched,
                notFound      = notFound,
                lowConfidence = lowConfidence,
                duplicates    = duplicates,
                blacklisted   = blacklisted,
                durationMs    = endTime - startTime,
                timestamp     = endTime,
            ),
            matched    = matched.size,
            notFound   = notFound.size,
            duplicates = duplicates.size,
            durationMs = endTime - startTime,
        ))
    }.flowOn(Dispatchers.IO)
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
        val result: SyncResult,
        val matched: Int,
        val notFound: Int,
        val duplicates: Int,
        val durationMs: Long,
    ) : SyncProgress()
}
