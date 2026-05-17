package com.conduit.platform

import com.conduit.domain.model.OAuthTokens
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface OAuthHandler {
    suspend fun authenticateSpotify(clientId: String, clientSecret: String? = null): OAuthTokens?
    suspend fun authenticateTidal(clientId: String, clientSecret: String? = null): OAuthTokens?

    suspend fun getTidalDeviceCode(): TidalDeviceResponse?
    fun openTidalBrowser(url: String)
}

@Serializable
data class TidalDeviceResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = 5
)
