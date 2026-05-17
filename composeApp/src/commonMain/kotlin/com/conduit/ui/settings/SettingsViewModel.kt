package com.conduit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.Credentials
import com.conduit.data.local.SettingsStorage
import com.conduit.data.local.TokenStorage
import com.conduit.platform.OAuthHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val spotifyClientId: String = "",
    val spotifyClientSecret: String = "",
    val tidalClientId: String = "",
    val tidalClientSecret: String = "",
    val isSpotifyConnected: Boolean = false,
    val isTidalConnected: Boolean = false,
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val error: String? = null
)

class SettingsViewModel(
    private val settingsStorage: SettingsStorage,
    private val tokenStorage: TokenStorage,
    private val oauthHandler: OAuthHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(
            spotifyClientId = settingsStorage.spotifyClientId,
            spotifyClientSecret = settingsStorage.spotifyClientSecret,
            tidalClientId = settingsStorage.tidalClientId,
            tidalClientSecret = settingsStorage.tidalClientSecret
        ) }
        viewModelScope.launch {
            val spotifyToken = tokenStorage.getAccessToken("spotify")
            val tidalToken = tokenStorage.getAccessToken("tidal")
            _uiState.update { it.copy(
                isSpotifyConnected = spotifyToken != null,
                isTidalConnected = tidalToken != null
            ) }
        }
    }

    fun updateSpotifyClientId(id: String) {
        settingsStorage.spotifyClientId = id
        _uiState.update { it.copy(spotifyClientId = id) }
    }

    fun updateSpotifyClientSecret(secret: String) {
        settingsStorage.spotifyClientSecret = secret
        _uiState.update { it.copy(spotifyClientSecret = secret) }
    }

    fun updateTidalClientId(id: String) {
        settingsStorage.tidalClientId = id
        _uiState.update { it.copy(tidalClientId = id) }
    }

    fun updateTidalClientSecret(secret: String) {
        settingsStorage.tidalClientSecret = secret
        _uiState.update { it.copy(tidalClientSecret = secret) }
    }

    fun connectSpotify() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val clientId = uiState.value.spotifyClientId.takeIf { it.isNotBlank() }
                    ?: Credentials.SPOTIFY_CLIENT_ID
                val clientSecret = uiState.value.spotifyClientSecret.takeIf { it.isNotBlank() }
                val tokens = oauthHandler.authenticateSpotify(clientId, clientSecret)
                if (tokens != null) {
                    tokenStorage.saveTokens("spotify", tokens.accessToken, tokens.refreshToken, tokens.expiresAt)
                    _uiState.update { it.copy(isSpotifyConnected = true) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun connectTidal() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val clientId = uiState.value.tidalClientId.takeIf { it.isNotBlank() }
                    ?: Credentials.TIDAL_CLIENT_ID
                val clientSecret = uiState.value.tidalClientSecret.takeIf { it.isNotBlank() }
                val tokens = oauthHandler.authenticateTidal(clientId, clientSecret)
                if (tokens != null) {
                    tokenStorage.saveTokens("tidal", tokens.accessToken, tokens.refreshToken, tokens.expiresAt)
                    _uiState.update { it.copy(isTidalConnected = true) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false, loadingMessage = null) }
            }
        }
    }

    fun disconnectSpotify() {
        tokenStorage.clearTokens("spotify")
        _uiState.update { it.copy(isSpotifyConnected = false) }
    }

    fun disconnectTidal() {
        tokenStorage.clearTokens("tidal")
        _uiState.update { it.copy(isTidalConnected = false) }
    }
}
