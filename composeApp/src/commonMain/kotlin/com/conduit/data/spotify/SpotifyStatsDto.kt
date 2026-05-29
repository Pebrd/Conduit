package com.conduit.data.spotify

import com.conduit.domain.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── Profile ──

@Serializable
internal data class SpotifyProfileDto(
    val display_name: String? = null,
    val id: String = "",
    val email: String? = null,
    val followers: SpotifyFollowersDto? = null,
    val images: List<SpotifyImageDto>? = null,
    val product: String? = null,
) {
    fun toDomain() = UserProfile(
        displayName = display_name ?: "Unknown",
        id = id,
        email = email,
        followers = followers?.total ?: 0,
        imageUrl = images?.firstOrNull()?.url,
        product = product ?: "free",
    )
}

@Serializable
internal data class SpotifyFollowersDto(
    val total: Int = 0,
)

// ── Top Artists ──

@Serializable
internal data class SpotifyTopArtistsResponse(
    val items: List<SpotifyTopArtistDto> = emptyList(),
)

@Serializable
internal data class SpotifyTopArtistDto(
    val id: String = "",
    val name: String = "",
    val genres: List<String> = emptyList(),
    val popularity: Int = 0,
    val images: List<SpotifyImageDto>? = null,
    val followers: SpotifyFollowersDto? = null,
) {
    fun toDomain() = TopArtistItem(
        id = id,
        name = name,
        genres = genres,
        popularity = popularity,
        imageUrl = images?.firstOrNull()?.url,
        followers = followers?.total ?: 0,
    )
}

// ── Top Tracks ──

@Serializable
internal data class SpotifyTopTracksResponse(
    val items: List<SpotifyTopTrackDto> = emptyList(),
)

@Serializable
internal data class SpotifyTopTrackDto(
    val id: String = "",
    val name: String = "",
    val artists: List<SpotifyArtistDto>? = null,
    val album: SpotifyTopAlbumDto? = null,
    val duration_ms: Long = 0L,
    val popularity: Int = 0,
) {
    fun toDomain() = TopTrackItem(
        id = id,
        name = name,
        artist = artists?.joinToString(", ") { it.name } ?: "Unknown",
        album = album?.name ?: "Unknown",
        imageUrl = album?.images?.firstOrNull()?.url,
        durationMs = duration_ms,
        popularity = popularity,
    )
}

@Serializable
internal data class SpotifyTopAlbumDto(
    val name: String? = null,
    val images: List<SpotifyImageDto>? = null,
)

// ── Artist Top Tracks ──

@Serializable
internal data class SpotifyArtistTopTracksResponse(
    val tracks: List<SpotifyTopTrackDto> = emptyList(),
)

// ── Recently Played ──

@Serializable
internal data class SpotifyRecentlyPlayedResponse(
    val items: List<SpotifyPlayHistoryDto> = emptyList(),
    val cursors: SpotifyCursorsDto? = null,
)

@Serializable
internal data class SpotifyPlayHistoryDto(
    val track: SpotifyPlayHistoryTrackDto? = null,
    val played_at: String? = null,
)

@Serializable
internal data class SpotifyPlayHistoryTrackDto(
    val id: String = "",
    val name: String = "",
    val artists: List<SpotifyArtistDto>? = null,
    val album: SpotifyTopAlbumDto? = null,
    val duration_ms: Long = 0L,
)

@Serializable
internal data class SpotifyCursorsDto(
    val after: String? = null,
    val before: String? = null,
)

// ── Audio Features ──

@Serializable
internal data class SpotifyAudioFeaturesBatchDto(
    val audio_features: List<SpotifyAudioFeatureDto?> = emptyList(),
)

@Serializable
internal data class SpotifyAudioFeatureDto(
    val id: String = "",
    val danceability: Double = 0.0,
    val energy: Double = 0.0,
    val valence: Double = 0.0,
    val tempo: Double = 0.0,
    val acousticness: Double = 0.0,
    val instrumentalness: Double = 0.0,
    val speechiness: Double = 0.0,
) {
    fun toDomain() = AudioFeaturesItem(
        trackId = id,
        danceability = danceability,
        energy = energy,
        valence = valence,
        tempo = tempo,
        acousticness = acousticness,
        instrumentalness = instrumentalness,
        speechiness = speechiness,
    )
}
