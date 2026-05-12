package com.spotitidal.platform

import com.spotitidal.domain.model.OAuthTokens

interface OAuthHandler {
    suspend fun authenticateSpotify(clientId: String): OAuthTokens?
    suspend fun authenticateTidal(clientId: String): OAuthTokens?
}
