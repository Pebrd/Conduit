package com.conduit.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.Credentials
import com.conduit.data.local.SettingsStorage
import com.conduit.data.local.TokenStorage
import com.conduit.platform.OAuthHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.conduit.data.auth.OAuthRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AuthUiState(
    val spotifyStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val tidalStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val isLoadingSpotify: Boolean = false,
    val isLoadingTidal: Boolean = false,
    val error: String? = null,
    val tidalDeviceCode: String? = null,
    val tidalVerificationUri: String? = null,
)

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    data class Connected(val username: String = "") : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

class AuthViewModel(
    private val oAuthHandler: OAuthHandler,
    private val oAuthRepository: OAuthRepository,
    private val tokenStorage: TokenStorage,
    private val settingsStorage: SettingsStorage,
) : ViewModel() {

    companion object {
        // Increment when Spotify scopes change to force re-authentication
        private const val SPOTIFY_SCOPES_VERSION = 1
    }

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Force re-auth if Spotify scopes have changed
            if (settingsStorage.getScopesVersion() < SPOTIFY_SCOPES_VERSION) {
                tokenStorage.clearTokens("spotify")
                settingsStorage.saveScopesVersion(SPOTIFY_SCOPES_VERSION)
            }

            val spotifyToken = tokenStorage.getAccessToken("spotify")
            val tidalToken = tokenStorage.getAccessToken("tidal")
            _state.update {
                it.copy(
                    spotifyStatus = if (spotifyToken != null) ConnectionStatus.Connected() else ConnectionStatus.Disconnected,
                    tidalStatus = if (tidalToken != null) ConnectionStatus.Connected() else ConnectionStatus.Disconnected,
                )
            }
        }
    }

    fun connectSpotify() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingSpotify = true, error = null) }
            try {
                val clientId = settingsStorage.spotifyClientId.takeIf { it.isNotBlank() }
                    ?: Credentials.SPOTIFY_CLIENT_ID
                val clientSecret = settingsStorage.spotifyClientSecret.takeIf { it.isNotBlank() }
                val tokens = oAuthHandler.authenticateSpotify(clientId, clientSecret)
                if (tokens != null) {
                    tokenStorage.saveTokens("spotify", tokens.accessToken, tokens.refreshToken, tokens.expiresAt)
                    _state.update { it.copy(spotifyStatus = ConnectionStatus.Connected(), isLoadingSpotify = false) }
                } else {
                    _state.update { it.copy(spotifyStatus = ConnectionStatus.Error("Cancelado"), isLoadingSpotify = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(spotifyStatus = ConnectionStatus.Error(e.message ?: "Error"), isLoadingSpotify = false) }
            }
        }
    }

    fun connectTidal() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingTidal = true, error = null) }
            
            val clientId = settingsStorage.tidalClientId.takeIf { it.isNotBlank() } ?: "V5HLp4iLaNh41xvj"
            
            // Intentamos primero Device Flow (puede fallar si el client_id no lo soporta)
            val deviceResponse = try {
                oAuthRepository.getTidalDeviceCode(clientId)
            } catch (e: Exception) {
                println("DEBUG TIDAL: Device flow error: ${e.message}")
                null
            }
            if (deviceResponse != null) {
                _state.update { 
                    it.copy(
                        tidalDeviceCode = deviceResponse.userCode,
                        tidalVerificationUri = "https://" + deviceResponse.verificationUriComplete,
                        isLoadingTidal = false
                    ) 
                }
                
                // Abrimos el navegador automáticamente para el usuario
                oAuthHandler.openTidalBrowser("https://" + deviceResponse.verificationUriComplete)

                // Empezamos el polling
                val tokens = oAuthRepository.pollTidalDeviceToken(
                    clientId = clientId,
                    deviceCode = deviceResponse.deviceCode,
                    interval = deviceResponse.interval,
                    expiresIn = deviceResponse.expiresIn
                )

                if (tokens != null) {
                    tokenStorage.saveTokens("tidal", tokens.accessToken, tokens.refreshToken, tokens.expiresAt)
                    _state.update { 
                        it.copy(
                            tidalStatus = ConnectionStatus.Connected("Tidal User"),
                            tidalDeviceCode = null,
                            tidalVerificationUri = null
                        ) 
                    }
                } else {
                    _state.update { it.copy(error = "Tidal connection timed out or failed") }
                }
            } else {
                // Si falla el device flow, intentamos el normal (fallback)
                val tokens = oAuthHandler.authenticateTidal(clientId, null)
                if (tokens != null) {
                    tokenStorage.saveTokens("tidal", tokens.accessToken, tokens.refreshToken, tokens.expiresAt)
                    _state.update { it.copy(tidalStatus = ConnectionStatus.Connected("Tidal User")) }
                } else {
                    _state.update { it.copy(error = "Failed to connect to Tidal") }
                }
                _state.update { it.copy(isLoadingTidal = false) }
            }
        }
    }

    fun disconnectSpotify() {
        tokenStorage.clearTokens("spotify")
        _state.update { it.copy(spotifyStatus = ConnectionStatus.Disconnected) }
    }

    fun disconnectTidal() {
        tokenStorage.clearTokens("tidal")
        _state.update { it.copy(tidalStatus = ConnectionStatus.Disconnected) }
    }
}
