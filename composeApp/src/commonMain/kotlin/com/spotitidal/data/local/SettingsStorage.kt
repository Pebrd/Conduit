package com.spotitidal.data.local
import com.russhwolf.multiplatform.settings.Settings

class SettingsStorage(private val settings: Settings) {
    var spotifyClientId: String
        get() = settings.getString("spotify_client_id", "")
        set(value) = settings.putString("spotify_client_id", value)

    var tidalClientId: String
        get() = settings.getString("tidal_client_id", "")
        set(value) = settings.putString("tidal_client_id", value)

    var syncIntervalHours: Int
        get() = settings.getInt("sync_interval_hours", 24)
        set(value) = settings.putInt("sync_interval_hours", value)
}
