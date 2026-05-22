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
        const val MAX_CONCURRENT_TRACKS = 5
    }

    operator fun invoke(
        spotifyPlaylistId: String,
        spotifyPlaylistName: String,
    ): Flow<SyncProgress> = flow {
        val startTime = Clock.System.now().toEpochMilliseconds()
        emit(SyncProgress.Started(spotifyPlaylistName))

        // 1. Obtener tracks de Spotify (paginado)
        val spotifyTracks = spotifyRepo.getPlaylistTracks(spotifyPlaylistId)
        emit(SyncProgress.TracksLoaded(spotifyTracks.size))

        // 2. Encontrar o crear la playlist en Tidal
        val tidalPlaylistId = tidalRepo.findOrCreatePlaylist(
            name              = spotifyPlaylistName,
            spotifyPlaylistId = spotifyPlaylistId,
        )

        // 3. Obtener tracks ya existentes en la playlist de Tidal (IDs + ISRCs para dedup completo)
        val (existingTidalIds, existingTidalIsrcs) = tidalRepo.getPlaylistExistingTracks(tidalPlaylistId)

        println("DEBUG SYNC: playlist=$spotifyPlaylistName tracks=${spotifyTracks.size} tidalId=$tidalPlaylistId")

        // 4. Procesar tracks en paralelo con semáforo
        val semaphore   = Semaphore(MAX_CONCURRENT_TRACKS)
        val matched     = mutableListOf<MatchedTrack>()
        val notFound    = mutableListOf<Track>()
        val lowConf     = mutableListOf<LowConfidenceMatch>()
        val duplicates  = mutableListOf<Track>()
        val blacklisted = mutableListOf<Track>()
        var processed   = 0

        coroutineScope {
            spotifyTracks.map { track ->
                async {
                    semaphore.withPermit {
                        syncEngine.findTidalTrack(track, spotifyPlaylistId, existingTidalIds)
                    }
                }
            }.forEach { deferred ->
                val result = deferred.await()
                processed++

                when (result) {
                    is MatchResult.Found -> matched.add(
                        MatchedTrack(
                            spotify = result.spotifyTrack,
                            tidal = result.track,
                            method = result.method,
                            score = result.score
                        )
                    )
                    is MatchResult.LowConfidence -> lowConf.add(
                        LowConfidenceMatch(
                            spotify = result.spotifyTrack,
                            tidal = result.track,
                            score = result.score
                        )
                    )
                    is MatchResult.Duplicate    -> duplicates.add(result.spotifyTrack)
                    is MatchResult.NotFound     -> notFound.add(result.spotifyTrack)
                    is MatchResult.Blacklisted  -> blacklisted.add(result.spotifyTrack)
                }

                emit(SyncProgress.Running(
                    current       = processed,
                    total         = spotifyTracks.size,
                    currentTrack  = result.trackName(),
                    matched       = matched.size,
                    notFound      = notFound.size,
                    duplicates    = duplicates.size,
                ))
            }
        }

        // 5. Agregar los tracks encontrados a la playlist de Tidal (en batch)
        // Deduplicar por ID Y por ISRC para evitar versiones duplicadas del mismo tema
        val tidalIdsToAdd = matched
            .filterNot { it.tidal.id in existingTidalIds }
            .filterNot { isrc -> isrc.tidal.isrc != null && isrc.tidal.isrc in existingTidalIsrcs }
            .map { it.tidal.id }
        if (tidalIdsToAdd.isNotEmpty()) {
            tidalRepo.addTracksToPlaylist(tidalPlaylistId, tidalIdsToAdd)
        }

        val endTime = Clock.System.now().toEpochMilliseconds()
        val duration = endTime - startTime

        val syncResult = SyncResult(
            id = spotifyPlaylistId,
            playlistId = tidalPlaylistId,
            playlistName = spotifyPlaylistName,
            matched = matched,
            notFound = notFound,
            lowConfidence = lowConf,
            duplicates = duplicates,
            blacklisted = blacklisted,
            durationMs = duration,
            timestamp = Clock.System.now().toEpochMilliseconds()
        )

        // 6. Emitir resultado final
        emit(SyncProgress.Completed(
            playlistName  = spotifyPlaylistName,
            matched       = matched.size,
            notFound      = notFound.size,
            lowConfidence = lowConf.size,
            duplicates    = duplicates.size,
            blacklisted   = blacklisted.size,
            notFoundTracks= notFound,
            lowConfTracks = lowConf,
            durationMs    = duration,
            result        = syncResult
        ))

    }.flowOn(Dispatchers.IO)
}

// Extension to map TidalTrack back to Spotify Track for UI/reporting if needed
private fun TidalTrack.toSpotify() = Track(
    id = id,
    name = name,
    artist = artist,
    album = album,
    durationMs = durationMs
)
