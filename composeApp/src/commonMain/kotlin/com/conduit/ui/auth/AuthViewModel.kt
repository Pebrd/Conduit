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

data class AuthUiState(
    val spotifyStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val tidalStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val isLoadingSpotify: Boolean = false,
    val isLoadingTidal: Boolean = false,
    val error: String? = null,
)

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    data class Connected(val username: String = "") : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

class AuthViewModel(
    private val oAuthHandler: OAuthHandler,
    private val tokenStorage: TokenStorage,
    private val settingsStorage: SettingsStorage,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
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
            _state.update {
                it.copy(
                    isLoadingTidal = true,
                    error = null
                )
            }
            try {
                val clientId = settingsStorage.tidalClientId.takeIf { it.isNotBlank() }
                    ?: Credentials.TIDAL_CLIENT_ID
                val clientSecret = settingsStorage.tidalClientSecret.takeIf { it.isNotBlank() }
                val tokens = oAuthHandler.authenticateTidal(clientId, clientSecret)
                if (tokens != null) {
                    tokenStorage.saveTokens("tidal", tokens.accessToken, tokens.refreshToken, tokens.expiresAt)
                    _state.update { it.copy(tidalStatus = ConnectionStatus.Connected(), isLoadingTidal = false) }
                } else {
                    _state.update { it.copy(tidalStatus = ConnectionStatus.Error("Cancelado"), isLoadingTidal = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(tidalStatus = ConnectionStatus.Error(e.message ?: "Error"), isLoadingTidal = false) }
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
