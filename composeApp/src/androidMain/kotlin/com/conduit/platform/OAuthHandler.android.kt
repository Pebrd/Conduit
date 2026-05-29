package com.conduit.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.conduit.data.auth.OAuthRepository
import com.conduit.domain.model.OAuthTokens
import com.conduit.Credentials
import com.conduit.data.local.SettingsStorage
import com.conduit.platform.TidalDeviceResponse
import kotlinx.coroutines.CompletableDeferred
import java.security.MessageDigest
import java.util.*

class AndroidOAuthHandler(
    private val context: Context,
    private val oauthRepository: OAuthRepository,
    private val settingsStorage: SettingsStorage
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
            .appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative user-library-read user-read-private user-read-email user-top-read user-read-recently-played")
            .appendQueryParameter("show_dialog", "true")
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
        val clientId = settingsStorage.tidalClientId.takeIf { it.isNotBlank() } ?: "V5HLp4iLaNh41xvj"
        val state = UUID.randomUUID().toString()
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val redirectUri = "conduit://tidal-callback"
        val scopes = "user.read collection.read collection.write playlists.write"

        val deferred = CompletableDeferred<String?>()
        pendingResult = deferred

        val uri = Uri.parse("https://login.tidal.com/authorize")
            .buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        val finalUrl = uri.toString()
        println("DEBUG TIDAL AUTH: Opening URL with Strict PKCE: $finalUrl")
        openTidalBrowser(finalUrl)

        val code = deferred.await() ?: return null
        return oauthRepository.exchangeCodeForTidalTokens(clientId, clientSecret, code, codeVerifier, redirectUri)
    }

    override suspend fun getTidalDeviceCode(): TidalDeviceResponse? {
        val clientId = settingsStorage.tidalClientId.takeIf { it.isNotBlank() } ?: "V5HLp4iLaNh41xvj"
        return oauthRepository.getTidalDeviceCode(clientId)
    }

    override fun openTidalBrowser(url: String) {
        // Usamos Intent para mayor compatibilidad, pero con la URL limpia
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        java.security.SecureRandom().nextBytes(bytes)
        // URL_SAFE, NO_PADDING y NO_WRAP son vitales para TIDAL
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP).trim()
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = java.security.MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return android.util.Base64.encodeToString(digest, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP).trim()
    }
}
