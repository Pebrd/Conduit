package com.spotitidal.platform

import com.spotitidal.data.auth.OAuthRepository
import com.spotitidal.domain.model.OAuthTokens
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

class DesktopOAuthHandler(private val oauthRepository: OAuthRepository) : OAuthHandler {
    override suspend fun authenticateSpotify(clientId: String): OAuthTokens? {
        return authenticate(
            clientId = clientId,
            authUrl = "https://accounts.spotify.com/authorize",
            tokenUrl = "https://accounts.spotify.com/api/token",
            scopes = "playlist-read-private playlist-read-collaborative user-library-read",
            redirectUri = "http://localhost:8888/spotify/callback"
        )
    }

    override suspend fun authenticateTidal(clientId: String): OAuthTokens? {
        return authenticate(
            clientId = clientId,
            authUrl = "https://login.tidal.com/oauth2/authorize",
            tokenUrl = "https://login.tidal.com/oauth2/token",
            scopes = "r_usr w_usr w_sub",
            redirectUri = "http://localhost:8888/tidal/callback"
        )
    }

    private suspend fun authenticate(
        clientId: String,
        authUrl: String,
        tokenUrl: String,
        scopes: String,
        redirectUri: String
    ): OAuthTokens? {
        val state = UUID.randomUUID().toString()
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        
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
                get("/tidal/callback") {
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

        try {
            val uri = "$authUrl?client_id=$clientId&response_type=code&redirect_uri=$redirectUri&scope=$scopes&state=$state&code_challenge_method=S256&code_challenge=$codeChallenge"
            Desktop.getDesktop().browse(URI(uri))
            
            val code = deferredCode.await() ?: return null
            
            return if (authUrl.contains("spotify")) {
                oauthRepository.exchangeCodeForSpotifyTokens(clientId, code, codeVerifier, redirectUri)
            } else {
                oauthRepository.exchangeCodeForTidalTokens(clientId, code, codeVerifier, redirectUri)
            }
        } finally {
            server.stop(1000, 1000)
        }
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
