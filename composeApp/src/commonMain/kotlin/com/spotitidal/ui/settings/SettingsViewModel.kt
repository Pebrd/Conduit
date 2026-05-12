package com.spotitidal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotitidal.data.local.SettingsStorage
import com.spotitidal.data.local.TokenStorage
import com.spotitidal.platform.OAuthHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val spotifyClientId: String = "",
    val tidalClientId: String = "",
    val isSpotifyConnected: Boolean = false,
    val isTidalConnected: Boolean = false,
    val isLoading: Boolean = false,
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
            tidalClientId = settingsStorage.tidalClientId,
            isSpotifyConnected = tokenStorage.getAccessToken("spotify") != null,
            isTidalConnected = tokenStorage.getAccessToken("tidal") != null
        ) }
    }

    fun updateSpotifyClientId(id: String) {
        settingsStorage.spotifyClientId = id
        _uiState.update { it.copy(spotifyClientId = id) }
    }

    fun updateTidalClientId(id: String) {
        settingsStorage.tidalClientId = id
        _uiState.update { it.copy(tidalClientId = id) }
    }

    fun connectSpotify() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val tokens = oauthHandler.authenticateSpotify(uiState.value.spotifyClientId)
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
                val tokens = oauthHandler.authenticateTidal(uiState.value.tidalClientId)
                if (tokens != null) {
                    tokenStorage.saveTokens("tidal", tokens.accessToken, tokens.refreshToken, tokens.expiresAt)
                    _uiState.update { it.copy(isTidalConnected = true) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
