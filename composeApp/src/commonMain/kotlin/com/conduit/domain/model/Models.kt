package com.conduit.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val isrc: String? = null,
    val releaseYear: Int? = null,
    val imageUrl: String? = null,
)

@Serializable
data class TidalTrack(
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val isrc: String? = null,
    val releaseYear: Int? = null,
    val audioQuality: String? = null, // HI_RES_LOSSLESS, LOSSLESS, HIGH, etc.
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val imageUrl: String? = null,
    val description: String? = null,
    val source: MusicService,
)

@Serializable
data class SyncResult(
    val id: String,
    val playlistId: String,
    val playlistName: String,
    val matched: List<MatchedTrack>,
    val notFound: List<Track>,
    val lowConfidence: List<LowConfidenceMatch>,
    val duplicates: List<Track>,
    val blacklisted: List<Track>,
    val durationMs: Long,
    val timestamp: Long,
)

@Serializable
data class MatchedTrack(
    val spotify: Track,
    val tidal: TidalTrack,
    val method: MatchMethod,
    val score: Double,
)

@Serializable
data class LowConfidenceMatch(
    val spotify: Track,
    val tidal: TidalTrack,
    val score: Double,
)

@Serializable
data class DiffEntry(
    val track: Track,
    val status: DiffStatus,
    val included: Boolean = true,
)

@Serializable
data class BlacklistEntry(
    val trackId: String,
    val trackName: String,
    val artist: String,
    val scope: BlacklistScope,
    val playlistId: String? = null,
)

@Serializable
data class TrackMapping(
    val spotifyTrackId: String,
    val tidalTrackId: String,
    val confirmedByUser: Boolean = true,
)

@Serializable
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String? = null
)

enum class MusicService { SPOTIFY, TIDAL }
enum class DiffStatus   { NEW, OK, MISSING, CONFLICT, REMOVED }
enum class BlacklistScope { GLOBAL, PER_PLAYLIST }
enum class MatchMethod  { ISRC, FuzzyHigh, AlbumMatch, ManualMapping }
