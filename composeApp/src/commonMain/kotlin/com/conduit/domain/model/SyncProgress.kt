package com.conduit.domain.model

import kotlinx.serialization.Serializable

sealed class SyncProgress {
    data class Started(val playlistName: String) : SyncProgress()
    data class TracksLoaded(val total: Int) : SyncProgress()
    data class Running(
        val current: Int,
        val total: Int,
        val currentTrack: String,
        val matched: Int,
        val notFound: Int,
        val duplicates: Int,
    ) : SyncProgress()
    data class Completed(
        val playlistName: String,
        val matched: Int,
        val notFound: Int,
        val lowConfidence: Int,
        val duplicates: Int,
        val blacklisted: Int,
        val notFoundTracks: List<Track>,
        val lowConfTracks: List<LowConfidenceMatch>,
        val durationMs: Long,
        val result: SyncResult? = null // For backward compatibility if needed
    ) : SyncProgress()
    data class Error(val message: String) : SyncProgress()
}

sealed class AllSyncProgress {
    data class Started(val total: Int) : AllSyncProgress()
    data class PlaylistCompleted(
        val result: SyncResult,
        val completed: Int,
        val total: Int,
    ) : AllSyncProgress()
    data class AllCompleted(val results: List<SyncResult>) : AllSyncProgress()
}
