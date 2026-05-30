package com.conduit.domain.util

import com.conduit.domain.model.DiscoverTrack
import com.conduit.domain.model.Track

object TrackNormalizer {
    fun normalize(name: String, artist: String): String =
        "${name.lowercase().replace(Regex("""\s*\(.*?\)"""), "").replace(Regex("""\s*[–\-].*$"""), "").trim()} :: ${artist.lowercase().trim()}"

    fun normalize(track: Track): String = normalize(track.name, track.artist)

    fun normalize(track: DiscoverTrack): String = normalize(track.name, track.artist)

    fun cleanTrackNameForLastFm(name: String): String =
        name.replace(Regex("""\s*\(.*?\)"""), "")
            .replace(Regex("""\s*[–\-]\s+.*$"""), "")
            .trim()
}
