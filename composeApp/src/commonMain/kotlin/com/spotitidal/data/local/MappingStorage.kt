package com.spotitidal.data.local

import com.russhwolf.settings.Settings
import com.spotitidal.domain.model.TrackMapping
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MappingStorage(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getMappings(): List<TrackMapping> {
        val raw = settings.getStringOrNull("track_mappings") ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveMapping(mapping: TrackMapping) {
        val current = getMappings().toMutableList()
        current.removeAll { it.spotifyTrackId == mapping.spotifyTrackId }
        current.add(mapping)
        settings.putString("track_mappings", json.encodeToString(current))
    }

    fun getMapping(spotifyTrackId: String): TrackMapping? {
        return getMappings().find { it.spotifyTrackId == spotifyTrackId }
    }

    fun removeMapping(spotifyTrackId: String) {
        val current = getMappings().filterNot { it.spotifyTrackId == spotifyTrackId }
        settings.putString("track_mappings", json.encodeToString(current))
    }
}
