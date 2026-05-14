package com.conduit.data.http

import com.conduit.data.auth.OAuthRepository
import com.conduit.data.local.SettingsStorage
import kotlinx.datetime.Clock

interface TokenStorage {
    suspend fun getAccessToken(service: String): String?
    suspend fun getRefreshToken(service: String): String?
    suspend fun getExpiresAt(service: String): Long?
    suspend fun saveTokens(service: String, accessToken: String, refreshToken: String, expiresAt: Long)
}

class NotAuthenticatedException(service: String) : Exception("Not authenticated for service: $service")

class TokenRefreshPlugin(
    private val tokenStorage: TokenStorage,
    private val oAuthRepository: OAuthRepository,
    private val settingsStorage: SettingsStorage
) {
    suspend fun getValidToken(service: String): String {
        val accessToken = tokenStorage.getAccessToken(service)
        if (accessToken.isNullOrBlank()) throw NotAuthenticatedException(service)
        
        val refreshToken = tokenStorage.getRefreshToken(service) ?: throw NotAuthenticatedException(service)
        val expiresAt = tokenStorage.getExpiresAt(service) ?: 0L

        // Renovar si expira en menos de 5 minutos
        return if (expiresAt - Clock.System.now().toEpochMilliseconds() < 300_000) {
            val refreshedTokens = when (service) {
                "spotify" -> {
                    val clientId = settingsStorage.spotifyClientId.takeIf { it.isNotBlank() } ?: com.conduit.Credentials.SPOTIFY_CLIENT_ID
                    oAuthRepository.refreshSpotifyToken(clientId, refreshToken)
                }
                "tidal" -> {
                    val clientId = settingsStorage.tidalClientId.takeIf { it.isNotBlank() } ?: com.conduit.Credentials.TIDAL_CLIENT_ID
                    val clientSecret = settingsStorage.tidalClientSecret.takeIf { it.isNotBlank() }
                    oAuthRepository.refreshTidalToken(clientId, clientSecret, refreshToken)
                }
                else -> throw IllegalArgumentException("Unknown service: $service")
            } ?: throw NotAuthenticatedException("Failed to refresh token for $service")
            
            tokenStorage.saveTokens(
                service = service,
                accessToken = refreshedTokens.accessToken,
                refreshToken = refreshedTokens.refreshToken,
                expiresAt = refreshedTokens.expiresAt
            )
            refreshedTokens.accessToken
        } else {
            accessToken
        }
    }
}
