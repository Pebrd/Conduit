package com.conduit.data.local

import com.russhwolf.settings.Settings
import com.conduit.domain.model.BlacklistEntry
import com.conduit.domain.model.BlacklistScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BlacklistStorage(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getBlacklist(): List<BlacklistEntry> {
        val raw = settings.getStringOrNull("blacklist") ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addToBlacklist(entry: BlacklistEntry) {
        val current = getBlacklist().toMutableList()
        if (current.none { it.trackId == entry.trackId && it.playlistId == entry.playlistId }) {
            current.add(entry)
            settings.putString("blacklist", json.encodeToString(current))
        }
    }

    fun removeFromBlacklist(trackId: String, playlistId: String? = null) {
        val current = getBlacklist().filterNot { it.trackId == trackId && it.playlistId == playlistId }
        settings.putString("blacklist", json.encodeToString(current))
    }

    fun isBlacklisted(trackId: String, playlistId: String): Boolean {
        val blacklist = getBlacklist()
        return blacklist.any { 
            it.trackId == trackId && (it.scope == BlacklistScope.GLOBAL || it.playlistId == playlistId)
        }
    }
}
