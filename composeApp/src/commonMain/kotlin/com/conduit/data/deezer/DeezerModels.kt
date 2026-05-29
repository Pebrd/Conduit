package com.conduit.data.deezer

import kotlinx.serialization.Serializable

@Serializable
data class DeezerArtistSearchResult(
    val data: List<DeezerArtist> = emptyList(),
)

@Serializable
data class DeezerArtist(
    val id: Long,
    val name: String,
    val picture_medium: String? = null,
)

@Serializable
data class DeezerRelatedResult(
    val data: List<DeezerArtist> = emptyList(),
)

@Serializable
data class DeezerTopTracksResult(
    val data: List<DeezerTrack> = emptyList(),
)

@Serializable
data class DeezerTrack(
    val id: Long,
    val title: String,
    val artist: DeezerTrackArtist,
    val album: DeezerTrackAlbum,
    val duration: Long,
    val preview: String? = null,
)

@Serializable
data class DeezerTrackArtist(
    val name: String,
)

@Serializable
data class DeezerTrackAlbum(
    val title: String,
)
