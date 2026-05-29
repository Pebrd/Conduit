package com.conduit.data.itunes

import com.conduit.domain.model.Track

data class ITunesResult(
    val trackId: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val previewUrl: String?,
    val artworkUrl: String?,
    val durationMs: Long,
)

fun ITunesResult.toTrack() = Track(
    id = "itunes_$trackId",
    name = trackName,
    artist = artistName,
    album = albumName,
    durationMs = durationMs,
    imageUrl = artworkUrl,
)
