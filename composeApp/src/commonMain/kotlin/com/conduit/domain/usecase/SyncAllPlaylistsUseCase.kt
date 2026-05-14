package com.conduit.domain.usecase

import com.conduit.domain.model.SyncResult
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

        emit(AllSyncProgress.Started(total = playlists.size))

        coroutineScope {
            playlists
                .map { playlist ->
                    async {
                        semaphore.withPermit {
                            // .last() returns the final SyncProgress — always Completed
                            val finalProgress = syncPlaylistUseCase(playlist.id).last()
                            (finalProgress as? SyncProgress.Completed)?.result
                                ?: SyncResult(
                                    id            = "sync_err_${Clock.System.now().toEpochMilliseconds()}",
                                    playlistId    = playlist.id,
                                    playlistName  = playlist.name,
                                    matched       = emptyList(),
                                    notFound      = emptyList(),
                                    lowConfidence = emptyList(),
                                    duplicates    = emptyList(),
                                    blacklisted   = emptyList(),
                                    durationMs    = 0L,
                                    timestamp     = Clock.System.now().toEpochMilliseconds(),
                                )
                        }
                    }
                }
                .awaitAll()
                .forEach { result ->
                    results.add(result)
                    emit(AllSyncProgress.PlaylistCompleted(result, completed = results.size, total = playlists.size))
                }
        }

        emit(AllSyncProgress.AllCompleted(results))
    }.flowOn(Dispatchers.IO)
}

sealed class AllSyncProgress {
    data class Started(val total: Int) : AllSyncProgress()
    data class PlaylistCompleted(val result: SyncResult, val completed: Int, val total: Int) : AllSyncProgress()
    data class AllCompleted(val results: List<SyncResult>) : AllSyncProgress()
}

