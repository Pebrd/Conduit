package com.conduit.domain.usecase

import com.conduit.data.musicbrainz.MusicBrainzClient
import com.conduit.domain.model.*
import com.conduit.domain.repository.SpotifyRepository
import kotlinx.coroutines.delay

class BuildMoodProfileUseCase(
    private val spotifyRepo: SpotifyRepository,
    private val mbClient: MusicBrainzClient,
) {
    private val knownGenres = setOf(
        "trip hop", "electronic", "jazz", "rock", "pop",
        "hip hop", "classical", "ambient", "folk", "metal",
        "r&b", "soul", "funk", "reggae", "country", "blues",
        "indie", "punk", "alternative", "latin", "dance", "house",
        "techno", "dubstep", "drum and bass", "synth-pop", "disco",
    )

    suspend fun fromPlaylist(playlistId: String): MoodProfile {
        val tracks = spotifyRepo.getPlaylistTracks(playlistId)
        val sample = tracks.filter { it.isrc != null }.take(50)

        val allTags = mutableMapOf<String, Int>()
        val isrcs = mutableListOf<String>()

        sample.forEach { track ->
            delay(1_100)
            val recording = track.isrc?.let { mbClient.getByIsrc(it) } ?: return@forEach
            isrcs.add(track.isrc!!)
            recording.tags.forEach { tag ->
                allTags[tag] = (allTags[tag] ?: 0) + 1
            }
        }

        val maxCount = allTags.values.maxOrNull()?.toDouble() ?: 1.0
        val weights = allTags.mapValues { it.value / maxCount }

        return MoodProfile(
            genres = weights.filter { it.key in knownGenres },
            tags = weights.filter { it.key !in knownGenres },
            seedIsrcs = isrcs,
            relatedArtistMbids = emptyList(),
        )
    }

    suspend fun fromTrack(track: Track): MoodProfile {
        val recording = track.isrc?.let { mbClient.getByIsrc(it) }
            ?: mbClient.searchByName(track.name, track.artist)
            ?: return MoodProfile(emptyMap(), emptyMap(), emptyList(), emptyList())

        val relatedArtists = mbClient.getRelatedArtists(recording.artistMbid)
            .take(5)
            .map { it.mbid }

        val weights = recording.tags.associateWith { 1.0 }

        return MoodProfile(
            genres = weights.filter { it.key in knownGenres },
            tags = weights.filter { it.key !in knownGenres },
            seedIsrcs = listOfNotNull(track.isrc),
            relatedArtistMbids = relatedArtists,
        )
    }
}
