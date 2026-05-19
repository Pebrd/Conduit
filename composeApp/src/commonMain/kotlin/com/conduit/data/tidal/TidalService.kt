package com.conduit.data.tidal

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class TidalTokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val token_type: String,
    val expires_in: Int,
    val user: TidalUser? = null
)

@Serializable
data class TidalUser(
    val userId: Long
)

class TidalService(private val client: HttpClient) {
    private val authUrl = "https://login.tidal.com/authorize"
    private val tokenUrl = "https://auth.tidal.com/v1/oauth2/token"

    suspend fun exchangeCode(
        clientId: String,
        clientSecret: String?,
        code: String,
        codeVerifier: String?,
        redirectUri: String
    ): TidalTokenResponse? {
        println("DEBUG TIDAL REBUILD: Exchanging code for tokens...")
        return try {
            val response = client.post(tokenUrl) {
                setBody(FormDataContent(Parameters.build() {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("client_id", clientId)
                    if (!clientSecret.isNullOrBlank()) {
                        append("client_secret", clientSecret)
                    }
                    if (!codeVerifier.isNullOrBlank()) {
                        append("code_verifier", codeVerifier)
                    }
                    append("redirect_uri", redirectUri)
                }))
            }
            
            val body = response.bodyAsText()
            println("DEBUG TIDAL REBUILD: Token Response Status=${response.status} Body=$body")
            
            if (response.status.isSuccess()) {
                response.body<TidalTokenResponse>()
            } else {
                null
            }
        } catch (e: Exception) {
            println("DEBUG TIDAL REBUILD ERROR: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    suspend fun getUserId(accessToken: String): String? {
        println("DEBUG TIDAL REBUILD: Getting User ID...")
        return try {
            val response = client.get("https://api.tidal.com/v1/sessions") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            val body = response.bodyAsText()
            println("DEBUG TIDAL REBUILD: Sessions Response Status=${response.status} Body=$body")
            
            if (response.status.isSuccess()) {
                // Extraer userId de la sesión
                // El body suele ser un JSON con "userId"
                // Implementación simple para pruebas
                body // Retornamos el body entero por ahora para debuggear
            } else {
                null
            }
        } catch (e: Exception) {
            println("DEBUG TIDAL REBUILD API ERROR: ${e.message}")
            null
        }
    }
}
