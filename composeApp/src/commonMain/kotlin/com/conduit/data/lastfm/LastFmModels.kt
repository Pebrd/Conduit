package com.conduit.data.lastfm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LastFmSimilarArtistsResponse(
    val similarartists: LastFmSimilarArtists? = null,
)

@Serializable
data class LastFmSimilarArtists(
    val artist: List<LastFmArtist> = emptyList(),
)

@Serializable
data class LastFmArtist(
    val name: String = "",
    val mbid: String = "",
    val match: String = "0",
    val url: String = "",
    val image: List<LastFmImage> = emptyList(),
)

@Serializable
data class LastFmImage(
    val size: String = "",
    @kotlinx.serialization.SerialName("#text") val text: String = "",
)

@Serializable
data class LastFmTopTracksResponse(
    val toptracks: LastFmTopTracks? = null,
)

@Serializable
data class LastFmTopTracks(
    val track: List<LastFmTrack> = emptyList(),
)

@Serializable
data class LastFmTrack(
    val name: String = "",
    val artist: LastFmTrackArtist? = null,
    val mbid: String = "",
    val url: String = "",
)

@Serializable
data class LastFmTrackArtist(
    val name: String = "",
    val mbid: String = "",
)

// ── track.getSimilar ──

@Serializable
data class LastFmSimilarTracksResponse(
    val similartracks: LastFmSimilarTracks? = null,
)

@Serializable
data class LastFmSimilarTracks(
    val track: List<LastFmSimilarTrackItem> = emptyList(),
)

@Serializable
data class LastFmSimilarTrackItem(
    val name: String = "",
    val match: String = "0",
    val artist: LastFmTrackArtist? = null,
    val mbid: String = "",
    val url: String = "",
)

// ── artist.getInfo ──

@Serializable
data class LastFmArtistInfoResponse(
    val artist: LastFmArtistInfo? = null,
)

@Serializable
data class LastFmArtistInfo(
    val name: String = "",
    val mbid: String = "",
    val url: String = "",
    val image: List<LastFmImage> = emptyList(),
    val stats: LastFmArtistStats? = null,
    val tags: LastFmArtistTags? = null,
    val bio: LastFmArtistBio? = null,
)

@Serializable
data class LastFmArtistStats(
    val listeners: String = "0",
    val playcount: String = "0",
)

@Serializable
data class LastFmArtistTags(
    val tag: List<LastFmArtistTag> = emptyList(),
)

@Serializable
data class LastFmArtistTag(
    val name: String = "",
    val url: String = "",
)

@Serializable
data class LastFmArtistBio(
    val summary: String = "",
    val content: String = "",
)
