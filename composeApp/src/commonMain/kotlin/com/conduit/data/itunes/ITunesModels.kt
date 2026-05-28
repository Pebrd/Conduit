package com.conduit.data.itunes

data class ITunesResult(
    val trackId: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val previewUrl: String?,
    val artworkUrl: String?,
    val durationMs: Long,
)
