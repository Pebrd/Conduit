package com.spotitidal.data.local

import com.russhwolf.multiplatform.settings.Settings
import com.spotitidal.domain.model.SyncResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HistoryStorage(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getHistory(): List<SyncResult> {
        val raw = settings.getStringOrNull("sync_history") ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addResult(result: SyncResult) {
        val current = getHistory().toMutableList()
        current.add(0, result) // Newest first
        if (current.size > 50) current.removeLast() // Keep last 50
        settings.putString("sync_history", json.encodeToString(current))
    }

    fun clearHistory() {
        settings.remove("sync_history")
    }
}
