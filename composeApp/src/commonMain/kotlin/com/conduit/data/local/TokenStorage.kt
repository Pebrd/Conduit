package com.conduit.data.local
import com.russhwolf.settings.Settings

class TokenStorage(private val settings: Settings) : com.conduit.data.http.TokenStorage {
    override suspend fun saveTokens(service: String, accessToken: String, refreshToken: String, expiresAt: Long) {
        settings.putString("${service}_access_token", accessToken)
        settings.putString("${service}_refresh_token", refreshToken)
        settings.putLong("${service}_expires_at", expiresAt)
    }

    override suspend fun getAccessToken(service: String): String? {
        return settings.getStringOrNull("${service}_access_token")
    }

    override suspend fun getRefreshToken(service: String): String? {
        return settings.getStringOrNull("${service}_refresh_token")
    }

    override suspend fun getExpiresAt(service: String): Long? {
        val value = settings.getLong("${service}_expires_at", -1L)
        return if (value == -1L) null else value
    }

    fun clearTokens(service: String) {
        settings.remove("${service}_access_token")
        settings.remove("${service}_refresh_token")
        settings.remove("${service}_expires_at")
    }
}
