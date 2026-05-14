package com.conduit.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.conduit.data.auth.OAuthRepository
import com.conduit.domain.model.OAuthTokens
import kotlinx.coroutines.CompletableDeferred
import java.security.MessageDigest
import java.util.*

class AndroidOAuthHandler(
    private val context: Context,
    private val oauthRepository: OAuthRepository
) : OAuthHandler {

    companion object {
        private var pendingResult: CompletableDeferred<String?>? = null

        fun onCallback(uri: android.net.Uri) {
            println("DEBUG OAUTH CALLBACK: $uri")
            val code = uri.getQueryParameter("code")
            pendingResult?.complete(code)
            pendingResult = null
        }
    }

    override suspend fun authenticateSpotify(clientId: String, clientSecret: String?): OAuthTokens? {
        val state = UUID.randomUUID().toString()
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val redirectUri = "conduit://spotify/callback"

        val deferred = CompletableDeferred<String?>()
        pendingResult = deferred

        val uri = Uri.parse("https://accounts.spotify.com/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative user-library-read")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        val code = deferred.await() ?: return null
        return oauthRepository.exchangeCodeForSpotifyTokens(clientId, null, code, codeVerifier, redirectUri)
    }

    override suspend fun authenticateTidal(clientId: String, clientSecret: String?): OAuthTokens? {
        val state = UUID.randomUUID().toString()
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val redirectUri = "conduit://tidal/callback"
        val scopes = "playlists.read playlists.write"

        val deferred = CompletableDeferred<String?>()
        pendingResult = deferred

        val uri = Uri.parse("https://login.tidal.com/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        val code = deferred.await() ?: return null
        return oauthRepository.exchangeCodeForTidalTokens(clientId, clientSecret, code, codeVerifier, redirectUri)
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        Random().nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return android.util.Base64.encodeToString(hash, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    }
}
