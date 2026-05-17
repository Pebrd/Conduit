package com.conduit.data.http

import com.conduit.data.auth.OAuthRepository
import com.conduit.data.local.SettingsStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    // Mutex para evitar refreshes concurrentes cuando múltiples tracks
    // se procesan en paralelo y todos necesitan refresh al mismo tiempo
    private val refreshMutex = Mutex()

    suspend fun getValidToken(service: String): String {
        val accessToken = tokenStorage.getAccessToken(service)
        if (accessToken.isNullOrBlank()) throw NotAuthenticatedException(service)
        
        val expiresAt = tokenStorage.getExpiresAt(service) ?: 0L

        // Si el token aún es válido (más de 5 minutos de vida), retornarlo directamente
        if (expiresAt - Clock.System.now().toEpochMilliseconds() >= 300_000) {
            return accessToken
        }

        // Token expirado o por expirar — necesita refresh con lock
        return refreshMutex.withLock {
            // Double-check: otro coroutine pudo haber refresheado mientras esperábamos el lock
            val currentToken = tokenStorage.getAccessToken(service)
            val currentExpiry = tokenStorage.getExpiresAt(service) ?: 0L
            if (currentExpiry - Clock.System.now().toEpochMilliseconds() >= 300_000 && !currentToken.isNullOrBlank()) {
                return@withLock currentToken
            }

            val refreshToken = tokenStorage.getRefreshToken(service) ?: throw NotAuthenticatedException(service)

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
            println("DEBUG TOKEN REFRESH: $service refreshed successfully, expires in ${(refreshedTokens.expiresAt - Clock.System.now().toEpochMilliseconds()) / 1000}s")
            refreshedTokens.accessToken
        }
    }
}
