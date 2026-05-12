package com.spotitidal.platform

import com.spotitidal.domain.model.OAuthTokens
import com.spotitidal.data.auth.OAuthRepository

expect class OAuthHandler(oauthRepository: OAuthRepository) {
    suspend fun authenticateSpotify(clientId: String): OAuthTokens?
    suspend fun authenticateTidal(clientId: String): OAuthTokens?
}
