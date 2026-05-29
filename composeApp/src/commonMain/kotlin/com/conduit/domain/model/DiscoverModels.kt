package com.conduit.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DiscoverTrack(
    val mbid: String,
    val name: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val isrc: String? = null,
    val genres: List<String> = emptyList(),
    val previewUrl: String? = null,
    val artworkUrl: String? = null,
    val matchScore: Double = 0.0,
)

@Serializable
data class DiscoverSession(
    val seed: DiscoverSeed,
    val destination: DiscoverDestination,
    val seedTrackIds: List<String> = emptyList(),
    val seedArtistName: String = "",
    val recommendationSource: String = "",
    val seenTrackIds: Set<String> = emptySet(),
    val seenTrackNames: Set<String> = emptySet(),
    val likedTracks: List<DiscoverTrack> = emptyList(),
)

@Serializable
sealed class DiscoverSeed {
    @Serializable
    data class FromPlaylist(
        val playlistId: String,
        val playlistName: String,
        val imageUrl: String? = null,
    ) : DiscoverSeed()

    @Serializable
    data class FromTrack(
        val trackId: String,
        val trackName: String,
        val artist: String,
        val isrc: String? = null,
    ) : DiscoverSeed()
}

@Serializable
data class DiscoverDestination(
    val platform: MusicService,
    val playlistId: String?,
    val playlistName: String,
)

@Serializable
data class MoodProfile(
    val genres: Map<String, Double>,
    val tags: Map<String, Double>,
    val seedIsrcs: List<String>,
    val relatedArtistMbids: List<String>,
)
