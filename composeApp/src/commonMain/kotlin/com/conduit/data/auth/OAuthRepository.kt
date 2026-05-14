package com.conduit.data.auth

import com.conduit.domain.model.OAuthTokens
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import io.ktor.util.encodeBase64
import kotlinx.coroutines.delay

class OAuthRepository(private val client: HttpClient) {

    suspend fun exchangeCodeForSpotifyTokens(
        clientId: String,
        clientSecret: String? = null,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): OAuthTokens? {
        return try {
            val response = client.submitForm(
                url = "https://accounts.spotify.com/api/token",
                formParameters = parameters {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", redirectUri)
                    append("client_id", clientId)
                    if (!clientSecret.isNullOrBlank()) {
                        append("client_secret", clientSecret)
                    }
                    append("code_verifier", codeVerifier)
                }
            )
            val data = response.body<TokenResponse>()
            OAuthTokens(
                accessToken = data.access_token,
                refreshToken = data.refresh_token ?: "",
                expiresAt = Clock.System.now().toEpochMilliseconds() + (data.expires_in * 1000)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun exchangeCodeForTidalTokens(
        clientId: String,
        clientSecret: String? = null,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): OAuthTokens? {
        return try {
            val isConfidential = !clientSecret.isNullOrBlank()
            val response = client.submitForm(
                url = "https://auth.tidal.com/v1/oauth2/token",
                formParameters = parameters {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", redirectUri)
                    append("code_verifier", codeVerifier)
                    // Si es público (sin secret), el client_id DEBE ir en el cuerpo.
                    // Si es confidencial, Tidal prefiere que vaya solo en la cabecera Basic.
                    if (!isConfidential) {
                        append("client_id", clientId)
                    }
                }
            ) {
                if (isConfidential) {
                    val auth = "$clientId:$clientSecret".encodeBase64()
                    header(HttpHeaders.Authorization, "Basic $auth")
                }
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                println("DEBUG: Tidal Token Exchange Error: ${response.status} - $errorBody")
                return null
            }

            val data = response.body<TokenResponse>()
            OAuthTokens(
                accessToken = data.access_token,
                refreshToken = data.refresh_token ?: "",
                expiresAt = Clock.System.now().toEpochMilliseconds() + (data.expires_in * 1000)
            )
        } catch (e: Exception) {
            println("DEBUG: Tidal exchange exception: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    suspend fun refreshSpotifyToken(clientId: String, refreshToken: String): OAuthTokens? {
        return try {
            val response = client.submitForm(
                url = "https://accounts.spotify.com/api/token",
                formParameters = parameters {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                    append("client_id", clientId)
                }
            )
            val data = response.body<TokenResponse>()
            OAuthTokens(
                accessToken = data.access_token,
                refreshToken = data.refresh_token ?: refreshToken,
                expiresAt = Clock.System.now().toEpochMilliseconds() + (data.expires_in * 1000)
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun refreshTidalToken(clientId: String, clientSecret: String?, refreshToken: String): OAuthTokens? {
        return try {
            val isConfidential = !clientSecret.isNullOrBlank()
            val response = client.submitForm(
                url = "https://auth.tidal.com/v1/oauth2/token",
                formParameters = parameters {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                    if (!isConfidential) {
                        append("client_id", clientId)
                    }
                }
            ) {
                if (isConfidential) {
                    val auth = "$clientId:$clientSecret".encodeBase64()
                    header(HttpHeaders.Authorization, "Basic $auth")
                }
            }
            val data = response.body<TokenResponse>()
            OAuthTokens(
                accessToken = data.access_token,
                refreshToken = data.refresh_token ?: refreshToken,
                expiresAt = Clock.System.now().toEpochMilliseconds() + (data.expires_in * 1000)
            )
        } catch (e: Exception) {
            null
        }
    }

    @Serializable
    private data class TokenResponse(
        val access_token: String,
        val refresh_token: String? = null,
        val expires_in: Long,
        val token_type: String
    )
}
