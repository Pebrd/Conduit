package com.conduit.data.sync

import com.conduit.domain.model.*
import com.conduit.domain.repository.TidalRepository
import com.conduit.data.local.BlacklistStorage
import com.conduit.data.local.MappingStorage
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class SyncEngine(
    private val tidalRepo: TidalRepository,
    private val mappingStorage: MappingStorage,
    private val blacklistStorage: BlacklistStorage,
) {
    private val searchCache = ConcurrentHashMap<String, List<TidalTrack>>()
    private val albumCache  = ConcurrentHashMap<String, List<TidalTrack>>()

    suspend fun findTidalTrack(
        track: Track,
        playlistId: String,
        existingTidalTrackIds: Set<String>,
    ): MatchResult {
        println("DEBUG TRACK: buscando '${track.name}' - '${track.artist}' isrc=${track.isrc}")
        val result = doFindTidalTrack(track, playlistId, existingTidalTrackIds)
        println("DEBUG RESULT: ${track.name} → $result")
        return result
    }

    private suspend fun doFindTidalTrack(
        track: Track,
        playlistId: String,
        existingTidalTrackIds: Set<String>,
    ): MatchResult {
        // PRE: mapeo manual
        mappingStorage.getMapping(track.id)?.let { mapping ->
            val tidalTrack = tidalRepo.getTrack(mapping.tidalTrackId) ?: return MatchResult.NotFound(track)
            return if (tidalTrack.id in existingTidalTrackIds) MatchResult.Duplicate(track, tidalTrack)
                   else MatchResult.Found(track, tidalTrack, MatchMethod.ManualMapping, 1.0)
        }

        // PRE: blacklist
        if (blacklistStorage.isBlacklisted(track.id, playlistId)) return MatchResult.Blacklisted(track)

        // ── Paso 1: ISRC (más confiable) ──────────────────────────────────────
        track.isrc?.let { isrc ->
            tidalRepo.searchByIsrc(isrc)?.let { tidalTrack ->
                return if (tidalTrack.id in existingTidalTrackIds) MatchResult.Duplicate(track, tidalTrack)
                       else MatchResult.Found(track, tidalTrack, MatchMethod.ISRC, 1.0)
            }
        }

        // ── Preprocesar datos del track ────────────────────────────────────────
        val normTitle      = normalize(track.name)
        val normAlbum      = normalize(track.album)
        val spotifyArtists = parseArtists(track.artist).toMutableSet().apply {
            addAll(extractFeaturedArtists(track.name))
        }
        val normMainArtist = spotifyArtists.firstOrNull() ?: ""

        // ── Paso 2: Multi-estrategia de búsqueda por texto ────────────────────
        val cleanedMainArtist = cleanArtistForSearch(normMainArtist)
        val queryA = cleanSearchQuery("$normTitle $cleanedMainArtist")
        val candidatesA = searchCached(queryA, 30)

        val bestA = candidatesA
            .distinctBy { it.id }
            .map { it to computeScore(track, it, spotifyArtists, normTitle, normAlbum) }
            .maxByOrNull { it.second }

        val finalBest = if (bestA != null && bestA.second >= 0.50) {
            bestA
        } else {
            val fallbackCandidates = mutableListOf<TidalTrack>()
            if (bestA != null) {
                fallbackCandidates.add(bestA.first)
            }

            // Estrategia B: solo título
            fallbackCandidates += searchCached(cleanSearchQuery(normTitle), 30)

            // Estrategia C: artista principal + título (orden invertido)
            if (cleanedMainArtist.isNotBlank()) {
                fallbackCandidates += searchCached(cleanSearchQuery("$cleanedMainArtist $normTitle"), 20)
            }

            // Estrategia D: título simplificado (palabras clave del título, max 4 tokens) + artista principal
            val simplifiedTitle = normTitle.split(" ").take(4).joinToString(" ")
            if (simplifiedTitle != normTitle) {
                fallbackCandidates += searchCached(cleanSearchQuery("$simplifiedTitle $cleanedMainArtist"), 20)
            }

            // Estrategia E: primer token del artista principal (para evitar sufijos/mismatches)
            val firstWordArtist = cleanedMainArtist.split(" ").firstOrNull() ?: ""
            if (firstWordArtist.isNotBlank() && firstWordArtist != cleanedMainArtist) {
                fallbackCandidates += searchCached(cleanSearchQuery("$normTitle $firstWordArtist"), 20)
            }

            // Estrategia F: si hay múltiples artistas, probar con el segundo artista
            val secondArtist = spotifyArtists.drop(1).firstOrNull()
            if (secondArtist != null) {
                val cleanedSecond = cleanArtistForSearch(secondArtist)
                fallbackCandidates += searchCached(cleanSearchQuery("$normTitle $cleanedSecond"), 20)
            }

            fallbackCandidates.distinctBy { it.id }
                .map { it to computeScore(track, it, spotifyArtists, normTitle, normAlbum) }
                .maxByOrNull { it.second }
        }

        // ── Paso 3: Scoring sobre todos los candidatos ─────────────────────────
        if (finalBest != null) {
            val (tidalTrack, score) = finalBest
            val isDuplicate = tidalTrack.id in existingTidalTrackIds
            when {
                score >= 0.55 -> return if (isDuplicate) MatchResult.Duplicate(track, tidalTrack)
                                        else MatchResult.Found(track, tidalTrack, MatchMethod.FuzzyHigh, score)
                score >= 0.20 -> return MatchResult.LowConfidence(track, tidalTrack, score)
            }
        }

        // ── Paso 4: Búsqueda por álbum (fallback exhaustivo) ──────────────────
        val albumResult = findByAlbum(track, normTitle, normAlbum, normMainArtist, spotifyArtists, existingTidalTrackIds)
        if (albumResult !is MatchResult.NotFound) return albumResult

        // ── Paso 5: Búsqueda recursiva con variantes del título ───────────────
        return findByTitleVariants(track, normTitle, normAlbum, normMainArtist, spotifyArtists, existingTidalTrackIds)
    }

    /**
     * Estrategia recursiva: prueba variantes del título limpiando más agresivamente
     * por si el normalizador original fue demasiado conservador o demasiado agresivo.
     */
    private suspend fun findByTitleVariants(
        track: Track,
        normTitle: String,
        normAlbum: String,
        normMainArtist: String,
        spotifyArtists: Set<String>,
        existingIds: Set<String>,
    ): MatchResult {
        // Variante 1: Solo primeras 2 palabras del título + artista
        val shortTitle = normTitle.split(" ").take(2).joinToString(" ")
        if (shortTitle.length >= 4 && shortTitle != normTitle) {
            val candidates = searchCached("$shortTitle $normMainArtist", 20)
            val best = candidates
                .map { it to computeScore(track, it, spotifyArtists, normTitle, normAlbum) }
                .filter { it.second >= 0.70 }
                .maxByOrNull { it.second }
            if (best != null) {
                val (tidalTrack, score) = best
                return if (tidalTrack.id in existingIds) MatchResult.Duplicate(track, tidalTrack)
                       else MatchResult.Found(track, tidalTrack, MatchMethod.FuzzyHigh, score)
            }
        }

        // Variante 2: Transliterar caracteres especiales y acentos antes de normalizar
        val deaccented = deaccent(track.name)
        val normDeaccented = normalize(deaccented)
        if (normDeaccented != normTitle) {
            val candidates = searchCached("$normDeaccented $normMainArtist", 20)
            val best = candidates
                .map { it to computeScore(track, it, spotifyArtists, normDeaccented, normAlbum) }
                .filter { it.second >= 0.72 }
                .maxByOrNull { it.second }
            if (best != null) {
                val (tidalTrack, score) = best
                return if (tidalTrack.id in existingIds) MatchResult.Duplicate(track, tidalTrack)
                       else MatchResult.Found(track, tidalTrack, MatchMethod.FuzzyHigh, score)
            }
        }

        return MatchResult.NotFound(track)
    }

    private suspend fun findByAlbum(
        track: Track,
        normTitle: String,
        normAlbum: String,
        normMainArtist: String,
        spotifyArtists: Set<String>,
        existingIds: Set<String>,
    ): MatchResult {
        // Buscar álbum con nombre + artista, y también solo el nombre del álbum
        val albumQueries = listOfNotNull(
            if (normMainArtist.isNotBlank()) "$normAlbum $normMainArtist" else null,
            normAlbum,
        )

        for (albumQuery in albumQueries) {
            val albumTracks = albumCache[albumQuery] ?: run {
                val tracks = tidalRepo.searchAlbums(albumQuery, limit = 5)
                    .flatMap { tidalRepo.getAlbumTracks(it.id) }
                albumCache[albumQuery] = tracks
                tracks
            }

            // Primero intento de titulo exacto via levenshtein
            val match = albumTracks
                .map { it to computeScore(track, it, spotifyArtists, normTitle, normAlbum) }
                .filter { it.second >= 0.75 }
                .maxByOrNull { it.second }

            if (match != null) {
                val (tidalTrack, score) = match
                return if (tidalTrack.id in existingIds) MatchResult.Duplicate(track, tidalTrack)
                       else MatchResult.Found(track, tidalTrack, MatchMethod.AlbumMatch, score)
            }
        }
        return MatchResult.NotFound(track)
    }

    private fun computeScore(
        spotify: Track,
        tidal: TidalTrack,
        spotifyArtists: Set<String>,
        normSpotTitle: String,
        normSpotAlbum: String,
    ): Double {
        val normTidalTitle = normalize(tidal.name)
        val tidalArtists   = parseArtists(tidal.artist)

        // Título: Levenshtein + bonus de tokens compartidos
        val lev         = levenshteinNorm(normSpotTitle, normTidalTitle)
        val tokenBonus  = tokenOverlapScore(normSpotTitle, normTidalTitle)
        val titleScore  = (lev * 0.7 + tokenBonus * 0.3) * 0.43

        // Artista: Jaccard sobre sets + bonus de substring + token-based similarity
        val jaccArtist  = jaccard(spotifyArtists, tidalArtists)
        val spotTokens  = getArtistTokens(spotifyArtists)
        val tidalTokens = getArtistTokens(tidalArtists)
        val tokenJaccard = if (spotTokens.isEmpty() && tidalTokens.isEmpty()) 1.0
                           else if (spotTokens.isEmpty() || tidalTokens.isEmpty()) 0.0
                           else spotTokens.intersect(tidalTokens).size.toDouble() / spotTokens.union(tidalTokens).size.toDouble()

        val combinedArtistMatch = maxOf(jaccArtist, tokenJaccard)
        val subArtist   = if (spotifyArtists.any { tidalArtists.any { t -> t.contains(it) || it.contains(t) } }) 0.3 else 0.0
        val artistScore = minOf(combinedArtistMatch + subArtist * (1 - combinedArtistMatch), 1.0) * 0.30

        // Duración: más tolerante si el match es muy fuerte (ambos título y artista >= 88% de coincidencia)
        val titleSim = lev * 0.7 + tokenBonus * 0.3
        val artistSim = combinedArtistMatch
        val relaxed = titleSim >= 0.88 && artistSim >= 0.88
        val durationScore = durationScore(spotify.durationMs, tidal.durationMs, relaxed) * 0.18

        // Álbum (menor peso — diferencias de edición son comunes)
        val albumScore  = levenshteinNorm(normSpotAlbum, normalize(tidal.album)) * 0.04

        // Año
        val yearScore   = yearScore(spotify.releaseYear, tidal.releaseYear) * 0.04

        // Calidad de audio: bonus pequeño para desempatar (max 0.01 → no cambia ganador principal)
        val qualityBonus = (qualityScoreV1(tidal.audioQuality) / 400.0)  // 0..0.01

        // Penalización por versión diferente (live vs studio, remix, etc.)
        val penalty     = versionMismatchPenalty(spotify.name, tidal.name) * 0.10

        val total = (titleScore + artistScore + durationScore + albumScore + yearScore + qualityBonus - penalty)
            .coerceIn(0.0, 1.0)

        println("DEBUG SCORE: '${spotify.name}' vs '${tidal.name}' | lev=${lev.fmt()} tok=${tokenBonus.fmt()} art=${artistScore.fmt()} dur=${durationScore.fmt()} q=${tidal.audioQuality} total=${total.fmt()}")

        return total
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun cleanSearchQuery(query: String): String {
        val words = query.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size <= 5) return query
        return words.take(5).joinToString(" ")
    }

    fun cleanArtistForSearch(artist: String): String {
        return artist
            .replace(Regex("""\s+(and|&)\s+the\s+band\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(and|&)\s+the\s+orchestra\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+orchestra\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+band\b""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun getArtistTokens(artists: Set<String>): Set<String> {
        val stopWords = setOf("the", "and", "y", "e", "with", "feat", "ft", "band", "orchestra", "de", "la", "los", "las", "of", "a")
        return artists
            .flatMap { it.lowercase().split(Regex("""\s+""")) }
            .map { it.replace(Regex("[^a-z0-9]"), "") }
            .filter { it.isNotBlank() && it !in stopWords }
            .toSet()
    }

    fun standardizeVersionStrings(title: String): String {
        return title.lowercase()
            .replace(Regex("""\brmx\b"""), "remix")
            .replace(Regex("""\bradio\s+edit\b"""), "edit")
            .replace(Regex("""\boriginal\s+mix\b"""), "original")
            .replace(Regex("""\bextended\s+mix\b"""), "extended")
    }

    private suspend fun searchCached(query: String, limit: Int): List<TidalTrack> {
        val key = "$query|$limit"
        return searchCache[key] ?: run {
            tidalRepo.searchTracks(query, limit).also { searchCache[key] = it }
        }
    }

    private fun Double.fmt() = ((this * 100).toInt().toDouble() / 100.0).toString()

    /** Convierte audioQuality v1 a score numérico para desempatar candidatos. */
    private fun qualityScoreV1(audioQuality: String?): Int = when (audioQuality?.uppercase()) {
        "HI_RES_LOSSLESS", "HI_RES" -> 4
        "LOSSLESS"                   -> 3
        "HIGH"                       -> 1
        else                         -> 0
    }

    /**
     * Normalización profunda:
     * - Elimina tags de feat/ft/with entre paréntesis
     * - Elimina tags de remaster/live/deluxe tanto entre paréntesis como tras guion
     * - Elimina corchetes
     * - Reemplaza acentos comunes
     * - Quita todo lo que no sea alfanumérico
     */
    fun normalize(s: String): String = s
        .lowercase()
        // Estandarizar ampersands y signos de suma
        .replace("&", " and ")
        .replace("+", " and ")
        // Estandarizar abreviaturas de partes y volumenes
        .replace(Regex("""\bvol\.?\s*(\d+)"""), "volume $1")
        .replace(Regex("""\bpt\.?\s*(\d+)"""), "part $1")
        .replace(Regex("""\bno\.?\s*(\d+)"""), "number $1")
        .replace(Regex("""\bvol\.?"""), "volume")
        .replace(Regex("""\bpt\.?"""), "part")
        .replace(Regex("""\bno\.?"""), "number")
        // Estandarizar números romanos de partes comunes
        .replace(Regex("""\bpart i\b"""), "part 1")
        .replace(Regex("""\bpart ii\b"""), "part 2")
        .replace(Regex("""\bpart iii\b"""), "part 3")
        .replace(Regex("""\bpart iv\b"""), "part 4")
        .replace(Regex("""\bpart v\b"""), "part 5")
        // Remover feat/ft/with entre paréntesis
        .replace(Regex("""\(feat\.?[^)]*\)|\(ft\.?[^)]*\)|\(with [^)]*\)"""), "")
        // Remover tags de versión entre paréntesis
        .replace(Regex("""\((remaster(ed)?|live|acoustic|radio.?edit|extended|deluxe|demo|instrumental|version|edit|original.?mix|single.?version|album.?version)[^)]*\)""", RegexOption.IGNORE_CASE), "")
        // Remover tags de versión tras guion (ej. "Song - Remastered 2011")
        .replace(Regex(""" - (remaster(ed)?|live|acoustic|radio.?edit|extended|deluxe|demo|instrumental|version|edit|original.?mix|single.?version|album.?version).*""", RegexOption.IGNORE_CASE), "")
        // Remover texto entre corchetes
        .replace(Regex("""\[[^\]]*\]"""), "")
        // Reemplazar acentos/diacríticos comunes
        .replace(Regex("[àáâãäå]"), "a")
        .replace(Regex("[èéêë]"), "e")
        .replace(Regex("[ìíîï]"), "i")
        .replace(Regex("[òóôõö]"), "o")
        .replace(Regex("[ùúûü]"), "u")
        .replace(Regex("[ýÿ]"), "y")
        .replace("ñ", "n")
        .replace("ç", "c")
        .replace("ß", "ss")
        .replace("ø", "o")
        .replace("æ", "ae")
        // Quitar todo lo que no sea alfanumérico o espacio
        .replace(Regex("[^a-z0-9 ]"), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /**
     * Extrae artistas colaboradores (featured) del título del track de Spotify.
     */
    fun extractFeaturedArtists(title: String): Set<String> {
        val regexes = listOf(
            Regex("""\((?:feat\.?|ft\.?|with)\s+([^)]+)\)""", RegexOption.IGNORE_CASE),
            Regex("""\[(?:feat\.?|ft\.?|with)\s+([^\]]+)\]""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:feat\.?|ft\.?|with)\s+([a-zA-Z0-9\s,&/]+)""", RegexOption.IGNORE_CASE)
        )
        val extracted = mutableSetOf<String>()
        for (regex in regexes) {
            regex.findAll(title).forEach { matchResult ->
                val artistsRaw = matchResult.groupValues[1]
                artistsRaw.split(Regex(""",|&|/|\bx\b|\bfeat\.?\b|\bft\.?\b|\bwith\b""", RegexOption.IGNORE_CASE)).forEach { artist ->
                    val clean = normalize(artist)
                    if (clean.isNotBlank()) {
                        extracted.add(clean)
                    }
                }
            }
        }
        return extracted
    }

    /**
     * Transliteración de acentos antes de normalizar,
     * útil cuando el mismo texto no tiene acentos en Tidal.
     */
    fun deaccent(s: String): String = s
        .replace(Regex("[àáâãäå]"), "a")
        .replace(Regex("[èéêë]"), "e")
        .replace(Regex("[ìíîï]"), "i")
        .replace(Regex("[òóôõö]"), "o")
        .replace(Regex("[ùúûü]"), "u")
        .replace(Regex("[ýÿ]"), "y")
        .replace("ñ", "n")
        .replace("ç", "c")

    /**
     * Score basado en tokens compartidos entre dos strings.
     * Útil para strings donde el orden de palabras varía.
     */
    fun tokenOverlapScore(a: String, b: String): Double {
        val tokA = a.split(" ").filter { it.length > 1 }.toSet()
        val tokB = b.split(" ").filter { it.length > 1 }.toSet()
        if (tokA.isEmpty() && tokB.isEmpty()) return 1.0
        if (tokA.isEmpty() || tokB.isEmpty()) return 0.0
        val intersection = tokA.intersect(tokB).size.toDouble()
        return intersection / maxOf(tokA.size, tokB.size).toDouble()
    }

    fun parseArtists(raw: String): Set<String> =
        raw.split(Regex(""",|&|/| x | feat\.?| ft\.?| with | presenting | pres\. """, RegexOption.IGNORE_CASE))
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .toSet()

    fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        return a.intersect(b).size.toDouble() / a.union(b).size.toDouble()
    }

    fun durationScore(spotMs: Long, tidalMs: Long, relaxed: Boolean = false): Double {
        if (spotMs == 0L || tidalMs == 0L) return 0.5  // desconocido → neutral
        val diffSecRaw = abs(spotMs - tidalMs) / 1000.0
        val diffSec = if (relaxed) diffSecRaw / 1.5 else diffSecRaw
        return when {
            diffSec <= 2  -> 1.00
            diffSec <= 5  -> 0.95
            diffSec <= 10 -> 0.85
            diffSec <= 20 -> 0.60
            diffSec <= 30 -> 0.30
            diffSec <= 60 -> 0.10
            else          -> 0.00
        }
    }

    fun yearScore(a: Int?, b: Int?): Double {
        if (a == null || b == null) return 0.5
        return when (abs(a - b)) { 0 -> 1.0; 1 -> 0.7; 2 -> 0.3; else -> 0.0 }
    }

    val VERSION_TAGS = listOf("live", "acoustic", "remix", "edit", "extended", "instrumental", "demo", "reprise", "medley", "original")

    fun versionMismatchPenalty(spotTitle: String, tidalTitle: String): Double {
        val s = standardizeVersionStrings(spotTitle)
        val t = standardizeVersionStrings(tidalTitle)
        // Penalizar solo cuando una versión tiene el tag y la otra NO
        return (VERSION_TAGS.count { tag -> s.contains(tag) != t.contains(tag) } * 0.12).coerceAtMost(0.60)
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
    data class Found(val spotifyTrack: Track, val track: TidalTrack, val method: MatchMethod, val score: Double) : MatchResult()
    data class LowConfidence(val spotifyTrack: Track, val track: TidalTrack, val score: Double) : MatchResult()
    data class Duplicate(val spotifyTrack: Track, val track: TidalTrack) : MatchResult()
    data class NotFound(val spotifyTrack: Track) : MatchResult()
    data class Blacklisted(val spotifyTrack: Track) : MatchResult()

    fun trackName(): String = when (this) {
        is Found        -> track.name
        is LowConfidence -> track.name
        is Duplicate    -> track.name
        is NotFound     -> spotifyTrack.name
        is Blacklisted  -> spotifyTrack.name
    }
}
