package com.spotitidal.data.local
import com.russhwolf.settings.Settings

class SettingsStorage(private val settings: Settings) {
    var spotifyClientId: String
        get() = settings.getString("spotify_client_id", "")
        set(value) = settings.putString("spotify_client_id", value)

    var spotifyClientSecret: String
        get() = settings.getString("spotify_client_secret", "")
        set(value) = settings.putString("spotify_client_secret", value)

    var tidalClientId: String
        get() = settings.getString("tidal_client_id", "")
        set(value) = settings.putString("tidal_client_id", value)

    var tidalClientSecret: String
        get() = settings.getString("tidal_client_secret", "")
        set(value) = settings.putString("tidal_client_secret", value)

    var syncIntervalHours: Int
        get() = settings.getInt("sync_interval_hours", 24)
        set(value) = settings.putInt("sync_interval_hours", value)
}
 