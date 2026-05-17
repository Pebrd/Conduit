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
import kotlinx.serialization.json.*
import io.ktor.util.encodeBase64
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.conduit.platform.TidalDeviceResponse

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

    suspend fun getTidalDeviceCode(clientId: String): TidalDeviceResponse? {
        return try {
            val response = client.submitForm(
                url = "https://auth.tidal.com/v1/oauth2/device_authorization",
                formParameters = parameters {
                    append("client_id", clientId)
                    // r_usr w_usr son necesarios para api.tidal.com/v1/sessions
                    // y listen.tidal.com/v1 endpoints
                    append("scope", "r_usr w_usr w_sub")
                }
            )
            val bodyText = response.bodyAsText()
            println("DEBUG TIDAL DEVICE AUTH: status=${response.status} body=${bodyText.take(300)}")

            if (response.status == HttpStatusCode.OK) {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                json.decodeFromString<TidalDeviceResponse>(bodyText)
            } else {
                println("DEBUG TIDAL DEVICE AUTH FAILED: ${response.status} - ${bodyText.take(300)}")
                null
            }
        } catch (e: Exception) {
            println("DEBUG TIDAL DEVICE AUTH EXCEPTION: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    suspend fun pollTidalDeviceToken(
        clientId: String,
        deviceCode: String,
        interval: Int,
        expiresIn: Int,
    ): OAuthTokens? = withContext(Dispatchers.IO) {
        val expiresAt = Clock.System.now().toEpochMilliseconds() + expiresIn * 1000L
        var currentInterval = interval.coerceAtLeast(5) // Mínimo 5 segundos
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        while (Clock.System.now().toEpochMilliseconds() < expiresAt) {
            delay(currentInterval * 1000L)
            try {
                val response = client.submitForm(
                    url = "https://auth.tidal.com/v1/oauth2/token",
                    formParameters = parameters {
                        append("client_id", clientId)
                        append("device_code", deviceCode)
                        append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                        append("scope", "r_usr w_usr w_sub")
                    }
                )

                val bodyText = response.bodyAsText()
                println("DEBUG POLL: status=${response.status} body=${bodyText.take(300)}")

                if (response.status == HttpStatusCode.OK) {
                    val data = response.body<TokenResponse>()
                    return@withContext OAuthTokens(
                        accessToken = data.access_token,
                        refreshToken = data.refresh_token ?: "",
                        expiresAt = Clock.System.now().toEpochMilliseconds() + (data.expires_in * 1000)
                    )
                }

                // Manejar respuestas de error según RFC 8628
                val errorType = try {
                    json.parseToJsonElement(bodyText)
                        .jsonObject["error"]?.jsonPrimitive?.content
                } catch (_: Exception) { null }

                when (errorType) {
                    "authorization_pending" -> {
                        // Normal: el usuario aún no autorizó, seguir esperando
                        continue
                    }
                    "slow_down" -> {
                        // El server pide que reduzcamos la frecuencia
                        currentInterval += 5
                        println("DEBUG POLL: slow_down, increasing interval to ${currentInterval}s")
                        continue
                    }
                    "expired_token", "access_denied" -> {
                        println("DEBUG POLL: $errorType — aborting")
                        return@withContext null
                    }
                    else -> {
                        println("DEBUG POLL: unexpected error '$errorType', continuing...")
                        continue
                    }
                }
            } catch (e: Exception) {
                println("DEBUG POLL ERROR: ${e.message}")
                // Seguir intentando — podría ser un error de red temporal
            }
        }
        println("DEBUG POLL: expired, no token received")
        null
    }

    @Serializable
    private data class TokenResponse(
        val access_token: String,
        val refresh_token: String? = null,
        val expires_in: Long,
        val token_type: String
    )
}
