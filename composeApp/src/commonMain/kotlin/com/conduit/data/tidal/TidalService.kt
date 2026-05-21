package com.conduit.data.tidal

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class TidalTokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val token_type: String,
    val expires_in: Int,
    @kotlinx.serialization.SerialName("user_id") val user_id: Long? = null
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
            
            val bodyString = response.bodyAsText()
            println("DEBUG TIDAL REBUILD: Token Response Status=${response.status} Body=$bodyString")
            
            if (response.status.isSuccess()) {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val jsonObj = json.parseToJsonElement(bodyString).jsonObject
                
                val accessToken = jsonObj["access_token"]?.jsonPrimitive?.content ?: ""
                val userId = jsonObj["user_id"]?.jsonPrimitive?.longOrNull
                
                println("DEBUG TIDAL SERVICE: AccessToken Length=${accessToken.length}")
                println("DEBUG TIDAL SERVICE: UserID de la respuesta=$userId")
                
                TidalTokenResponse(
                    access_token = accessToken,
                    refresh_token = jsonObj["refresh_token"]?.jsonPrimitive?.contentOrNull,
                    token_type = jsonObj["token_type"]?.jsonPrimitive?.content ?: "Bearer",
                    expires_in = jsonObj["expires_in"]?.jsonPrimitive?.int ?: 3600,
                    user_id = userId
                )
            } else {
                println("DEBUG TIDAL SERVICE: Error en la respuesta=${response.status}")
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
