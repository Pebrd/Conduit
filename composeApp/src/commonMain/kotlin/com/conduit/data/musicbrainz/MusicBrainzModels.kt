package com.conduit.data.musicbrainz

data class MusicBrainzRecording(
    val mbid: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val tags: List<String>,
    val isrc: String? = null,
    val artistMbid: String = "",
)

data class MusicBrainzArtist(
    val mbid: String,
    val name: String,
)
