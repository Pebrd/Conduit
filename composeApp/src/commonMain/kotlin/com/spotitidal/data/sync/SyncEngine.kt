package com.spotitidal.data.sync

import com.spotitidal.domain.model.*
import com.spotitidal.domain.repository.TidalRepository
import com.spotitidal.data.local.BlacklistStorage
import com.spotitidal.data.local.MappingStorage
import kotlin.math.abs

class SyncEngine(
    private val tidalRepo: TidalRepository,
    private val mappingStorage: MappingStorage,
    private val blacklistStorage: BlacklistStorage,
) {
    private val searchCache = mutableMapOf<String, List<TidalTrack>>()
    private val albumCache  = mutableMapOf<String, List<TidalTrack>>()

    suspend fun findTidalTrack(
        track: Track,
        playlistId: String,
        existingTidalTrackIds: Set<String>,
    ): MatchResult {
        // PRE: mapeo manual
        mappingStorage.getMapping(track.id)?.let { mapping ->
            val tidalTrack = tidalRepo.getTrack(mapping.tidalTrackId) ?: return MatchResult.NotFound(track)
            return if (tidalTrack.id in existingTidalTrackIds) MatchResult.Duplicate(tidalTrack)
                   else MatchResult.Found(tidalTrack, MatchMethod.ManualMapping, 1.0)
        }

        // PRE: blacklist
        if (blacklistStorage.isBlacklisted(track.id, playlistId)) return MatchResult.Blacklisted(track)

        // Paso 1: ISRC
        track.isrc?.let { isrc ->
            tidalRepo.searchByIsrc(isrc)?.let { tidalTrack ->
                return if (tidalTrack.id in existingTidalTrackIds) MatchResult.Duplicate(tidalTrack)
                       else MatchResult.Found(tidalTrack, MatchMethod.ISRC, 1.0)
            }
        }

        // Paso 2: normalización
        val normTitle      = normalize(track.name)
        val normAlbum      = normalize(track.album)
        val spotifyArtists = parseArtists(track.artist)
        val normMainArtist = spotifyArtists.firstOrNull() ?: ""

        // Paso 3+4: búsqueda primaria con fallback
        val primaryQuery  = "$normTitle $normMainArtist"
        val candidates = searchCache.getOrPut(primaryQuery) {
            tidalRepo.searchTracks(primaryQuery, limit = 10)
        }.ifEmpty {
            searchCache.getOrPut(normTitle) { tidalRepo.searchTracks(normTitle, limit = 10) }
        }

        // Paso 5+6: scoring
        val best = candidates
            .map { it to computeScore(track, it, spotifyArtists, normTitle, normAlbum) }
            .maxByOrNull { it.second }

        if (best != null) {
            val (tidalTrack, score) = best
            val isDuplicate = tidalTrack.id in existingTidalTrackIds
            when {
                score >= 0.82 -> return if (isDuplicate) MatchResult.Duplicate(tidalTrack)
                                        else MatchResult.Found(tidalTrack, MatchMethod.FuzzyHigh, score)
                score >= 0.62 -> return MatchResult.LowConfidence(tidalTrack, score)
            }
        }

        // Paso 7: búsqueda secundaria por álbum
        return findByAlbum(track, normTitle, normAlbum, normMainArtist, existingTidalTrackIds)
    }

    private suspend fun findByAlbum(
        track: Track,
        normTitle: String,
        normAlbum: String,
        normMainArtist: String,
        existingIds: Set<String>,
    ): MatchResult {
        val albumQuery  = "$normAlbum $normMainArtist"
        val albumTracks = albumCache.getOrPut(albumQuery) {
            tidalRepo.searchAlbums(albumQuery, limit = 3)
                .flatMap { tidalRepo.getAlbumTracks(it.id) }
        }
        val match = albumTracks.firstOrNull { levenshteinNorm(normalize(it.name), normTitle) > 0.85 }
        return if (match != null) {
            if (match.id in existingIds) MatchResult.Duplicate(match)
            else MatchResult.Found(match, MatchMethod.AlbumMatch, 0.85)
        } else MatchResult.NotFound(track)
    }

    private fun computeScore(
        spotify: Track,
        tidal: TidalTrack,
        spotifyArtists: Set<String>,
        normSpotTitle: String,
        normSpotAlbum: String,
    ): Double {
        val tidalArtists  = parseArtists(tidal.artist)
        val titleScore    = levenshteinNorm(normSpotTitle, normalize(tidal.name))         * 0.35
        val artistScore   = jaccard(spotifyArtists, tidalArtists)                         * 0.25
        val durationScore = durationScore(spotify.durationMs, tidal.durationMs)           * 0.20
        val albumScore    = levenshteinNorm(normSpotAlbum, normalize(tidal.album))        * 0.10
        val yearScore     = yearScore(spotify.releaseYear, tidal.releaseYear)             * 0.05
        val penalty       = versionMismatchPenalty(spotify.name, tidal.name)             * 0.05
        return (titleScore + artistScore + durationScore + albumScore + yearScore - penalty).coerceIn(0.0, 1.0)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun normalize(s: String): String = s
        .lowercase()
        .replace(Regex("""\(feat\.?[^)]*\)|\(ft\.?[^)]*\)|\(with [^)]*\)"""), "")
        .replace(Regex("""\[[^]]*\]"""), "")
        .replace(Regex("""\((remaster|live|acoustic|radio edit|extended|deluxe|demo|instrumental|version|edit)[^)]*\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[^a-z0-9 ]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    fun parseArtists(raw: String): Set<String> =
        raw.split(Regex(""",|&|/| x | feat\.?| ft\.?| with """, RegexOption.IGNORE_CASE))
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .toSet()

    fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        return a.intersect(b).size.toDouble() / a.union(b).size.toDouble()
    }

    fun durationScore(spotMs: Long, tidalMs: Long): Double {
        val diffSec = abs(spotMs - tidalMs) / 1000.0
        return when {
            diffSec <= 2  -> 1.00
            diffSec <= 5  -> 0.90
            diffSec <= 15 -> 0.50
            diffSec <= 30 -> 0.20
            else          -> 0.00
        }
    }

    fun yearScore(a: Int?, b: Int?): Double {
        if (a == null || b == null) return 0.5
        return when (abs(a - b)) { 0 -> 1.0; 1 -> 0.7; else -> 0.0 }
    }

    val VERSION_TAGS = listOf("live", "acoustic", "remix", "radio edit", "extended", "instrumental", "demo", "reprise")

    fun versionMismatchPenalty(spotTitle: String, tidalTitle: String): Double {
        val s = spotTitle.lowercase()
        val t = tidalTitle.lowercase()
        return (VERSION_TAGS.count { s.contains(it) != t.contains(it) } * 0.15).coerceAtMost(1.0)
    }

    fun levenshteinNorm(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length).toDouble()
        if (maxLen == 0.0) return 1.0
        return 1.0 - levenshtein(a, b) / maxLen
    }

    fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1]
                       else 1 + minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
        }
        return dp[a.length][b.length]
    }
}

sealed class MatchResult {
    data class Found(val track: TidalTrack, val method: MatchMethod, val score: Double) : MatchResult()
    data class LowConfidence(val track: TidalTrack, val score: Double) : MatchResult()
    data class Duplicate(val track: TidalTrack) : MatchResult()
    data class NotFound(val spotifyTrack: Track) : MatchResult()
    data class Blacklisted(val spotifyTrack: Track) : MatchResult()
}
