package com.spotitidal.data.local
import com.russhwolf.settings.Settings

class TokenStorage(private val settings: Settings) {
    fun saveTokens(service: String, accessToken: String, refreshToken: String, expiresAt: Long) {
        settings.putString("${service}_access_token", accessToken)
        settings.putString("${service}_refresh_token", refreshToken)
        settings.putLong("${service}_expires_at", expiresAt)
    }

    fun getAccessToken(service: String): String? {
        return settings.getStringOrNull("${service}_access_token")
    }

    fun getRefreshToken(service: String): String? {
        return settings.getStringOrNull("${service}_refresh_token")
    }

    fun getExpiresAt(service: String): Long {
        return settings.getLong("${service}_expires_at", 0L)
    }

    fun clearTokens(service: String) {
        settings.remove("${service}_access_token")
        settings.remove("${service}_refresh_token")
        settings.remove("${service}_expires_at")
    }
}
