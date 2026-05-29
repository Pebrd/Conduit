package com.conduit.domain.model

data class UserProfile(
    val displayName: String,
    val id: String,
    val email: String?,
    val followers: Int,
    val imageUrl: String?,
    val product: String,
)

data class TopArtistItem(
    val id: String,
    val name: String,
    val genres: List<String>,
    val popularity: Int,
    val imageUrl: String?,
    val followers: Int,
)

data class TopTrackItem(
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val imageUrl: String?,
    val durationMs: Long,
    val popularity: Int,
)

data class AudioFeaturesItem(
    val trackId: String,
    val danceability: Double,
    val energy: Double,
    val valence: Double,
    val tempo: Double,
    val acousticness: Double,
    val instrumentalness: Double,
    val speechiness: Double,
)

data class RecentlyPlayedItem(
    val trackId: String,
    val trackName: String,
    val artist: String,
    val album: String,
    val imageUrl: String?,
    val durationMs: Long,
    val playedAt: String,
)

data class MoodAnalysis(
    val averageValence: Double,
    val averageEnergy: Double,
    val averageDanceability: Double,
    val averageTempo: Double,
    val averageAcousticness: Double,
)

data class GenreCount(
    val genre: String,
    val count: Int,
)
