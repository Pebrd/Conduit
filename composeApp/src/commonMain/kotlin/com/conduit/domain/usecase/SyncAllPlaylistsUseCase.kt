package com.conduit.domain.usecase

import com.conduit.domain.model.SyncResult
import com.conduit.domain.model.SyncProgress
import com.conduit.domain.model.AllSyncProgress
import com.conduit.domain.repository.SpotifyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock

class SyncAllPlaylistsUseCase(
    private val spotifyRepo: SpotifyRepository,
    private val syncPlaylistUseCase: SyncPlaylistUseCase,
) {
    companion object {
        const val MAX_CONCURRENT_PLAYLISTS = 3
    }

    operator fun invoke(): Flow<AllSyncProgress> = flow {
        val playlists = spotifyRepo.getPlaylists()
        val semaphore = Semaphore(MAX_CONCURRENT_PLAYLISTS)
        val results   = mutableListOf<SyncResult>()

        emit(AllSyncProgress.Started(playlists.size))

        coroutineScope {
            playlists.map { playlist ->
                async {
                    semaphore.withPermit {
                        syncPlaylistUseCase(playlist.id, playlist.name).last()
                    }
                }
            }.forEach { deferred ->
                val result = deferred.await()
                if (result is SyncProgress.Completed) {
                    val syncResult = result.toSyncResult(results.size.toString()) // simplified ID
                    results.add(syncResult)
                    emit(AllSyncProgress.PlaylistCompleted(
                        result      = syncResult,
                        completed   = results.size,
                        total       = playlists.size,
                    ))
                }
            }
        }

        emit(AllSyncProgress.AllCompleted(results))
    }.flowOn(Dispatchers.IO)
}

// Helper to convert SyncProgress.Completed to SyncResult
private fun SyncProgress.Completed.toSyncResult(idSuffix: String): SyncResult {
    return SyncResult(
        id = "sync_all_$idSuffix",
        playlistId = "unknown", // Would need to pass it from UseCase if strictly needed
        playlistName = playlistName,
        matched = emptyList(), // Detailed matched tracks not easily available here without more mapping
        notFound = notFoundTracks,
        lowConfidence = lowConfTracks,
        duplicates = emptyList(),
        blacklisted = emptyList(),
        durationMs = durationMs,
        timestamp = Clock.System.now().toEpochMilliseconds()
    )
}

