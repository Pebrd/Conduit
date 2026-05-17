package com.conduit.platform

import com.conduit.data.auth.OAuthRepository
import com.conduit.domain.model.OAuthTokens
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import java.awt.Desktop
import java.net.URI
import java.util.*
import java.security.MessageDigest
import io.ktor.server.application.*
import java.net.URLEncoder
import com.conduit.Credentials
import com.conduit.data.local.SettingsStorage
import com.conduit.platform.TidalDeviceResponse

class DesktopOAuthHandler(
    private val oauthRepository: OAuthRepository,
    private val settingsStorage: SettingsStorage
) : OAuthHandler {

    override suspend fun authenticateSpotify(clientId: String, clientSecret: String?): OAuthTokens? {
        val state = UUID.randomUUID().toString()
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val redirectUri = "http://127.0.0.1:8888/spotify/callback"
        val scopes = "playlist-read-private playlist-read-collaborative user-library-read user-read-private user-read-email"

        val deferredCode = CompletableDeferred<String?>()

        val server = embeddedServer(Netty, port = 8888) {
            routing {
                get("/spotify/callback") {
                    val code = call.request.queryParameters["code"]
                    val returnedState = call.request.queryParameters["state"]
                    if (returnedState == state && code != null) {
                        call.respondText("Autenticación exitosa. Puedes cerrar esta ventana.")
                        deferredCode.complete(code)
                    } else {
                        call.respondText("Error de autenticación.")
                        deferredCode.complete(null)
                    }
                }
            }
        }.start(wait = false)

        return try {
            val encodedScopes = URLEncoder.encode(scopes, "UTF-8")
            val encodedRedirectUri = URLEncoder.encode(redirectUri, "UTF-8")
            val uri = "https://accounts.spotify.com/authorize?client_id=$clientId&response_type=code&redirect_uri=$encodedRedirectUri&scope=$encodedScopes&state=$state&show_dialog=true&code_challenge_method=S256&code_challenge=$codeChallenge"
            Desktop.getDesktop().browse(URI(uri))

            val code = deferredCode.await() ?: return null
            oauthRepository.exchangeCodeForSpotifyTokens(clientId, null, code, codeVerifier, redirectUri)
        } finally {
            server.stop(1000, 1000)
        }
    }

    override suspend fun authenticateTidal(clientId: String, clientSecret: String?): OAuthTokens? {
        // Obsoleto
        return null
    }

    override suspend fun getTidalDeviceCode(): TidalDeviceResponse? {
        val clientId = settingsStorage.tidalClientId.takeIf { it.isNotBlank() } ?: Credentials.TIDAL_CLIENT_ID
        return oauthRepository.getTidalDeviceCode(clientId)
    }

    override fun openTidalBrowser(url: String) {
        Desktop.getDesktop().browse(URI(url))
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        Random().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
}
