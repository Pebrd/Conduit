package com.conduit.platform

import com.conduit.domain.model.OAuthTokens

interface OAuthHandler {
    suspend fun authenticateSpotify(clientId: String, clientSecret: String? = null): OAuthTokens?
    suspend fun authenticateTidal(clientId: String, clientSecret: String? = null): OAuthTokens?
}
