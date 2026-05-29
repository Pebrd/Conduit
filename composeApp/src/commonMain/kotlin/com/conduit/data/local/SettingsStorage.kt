package com.conduit.data.local
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

    var tidalUserId: String
        get() = settings.getString("tidal_user_id", "")
        set(value) = settings.putString("tidal_user_id", value)

    var lastfmApiKey: String
        get() = settings.getString("lastfm_api_key", "")
        set(value) = settings.putString("lastfm_api_key", value)

    fun getScopesVersion(): Int = settings.getInt("scopes_version", 0)
    fun saveScopesVersion(version: Int) = settings.putInt("scopes_version", version)

    fun getMappedTidalPlaylist(spotifyId: String): String? = settings.getStringOrNull("mapped_playlist_$spotifyId")
    fun saveMappedTidalPlaylist(spotifyId: String, tidalId: String) = settings.putString("mapped_playlist_$spotifyId", tidalId)
    fun removeMappedTidalPlaylist(spotifyId: String) = settings.remove("mapped_playlist_$spotifyId")
}
 