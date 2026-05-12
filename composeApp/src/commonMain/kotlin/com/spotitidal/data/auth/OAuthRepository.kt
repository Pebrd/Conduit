package com.spotitidal.data.auth

import com.spotitidal.domain.model.OAuthTokens
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

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
                    if (clientSecret != null && clientSecret.isNotBlank()) {
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
            val response = client.submitForm(
                url = "https://login.tidal.com/oauth2/token",
                formParameters = parameters {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", redirectUri)
                    append("client_id", clientId)
                    if (clientSecret != null && clientSecret.isNotBlank()) {
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
