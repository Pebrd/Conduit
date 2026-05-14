package com.conduit.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import conduit.composeapp.generated.resources.Res
import conduit.composeapp.generated.resources.conduit_logo
import com.conduit.ui.theme.AccentBlue
import com.conduit.ui.theme.AccentSage
import com.conduit.ui.theme.DividerColor
import com.conduit.ui.theme.OnSurfaceDim
import com.conduit.ui.theme.ErrorRed
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthScreen(onBothConnected: () -> Unit = {}) {
    val viewModel: AuthViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.spotifyStatus, state.tidalStatus) {
        if (state.spotifyStatus is ConnectionStatus.Connected &&
            state.tidalStatus is ConnectionStatus.Connected) {
            onBothConnected()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.conduit_logo),
            contentDescription = "Conduit logo",
            modifier = Modifier.size(72.dp),
        )

        Spacer(Modifier.height(8.dp))

        Text("CONDUIT", style = MaterialTheme.typography.titleLarge, color = AccentSage)
        Text("sync your music", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)

        Spacer(Modifier.height(48.dp))
        HorizontalDivider(color = DividerColor)
        Spacer(Modifier.height(32.dp))

        // Spotify
        Text("SPOTIFY", style = MaterialTheme.typography.titleSmall, color = AccentSage)
        Spacer(Modifier.height(12.dp))
        ServiceButton(
            status = state.spotifyStatus,
            isLoading = state.isLoadingSpotify,
            loadingMessage = null,
            connectLabel = "CONECTAR",
            connectedColor = AccentSage,
            onClick = {
                if (state.spotifyStatus is ConnectionStatus.Connected)
                    viewModel.disconnectSpotify()
                else
                    viewModel.connectSpotify()
            }
        )

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = DividerColor)
        Spacer(Modifier.height(32.dp))

        // Tidal
        Text("TIDAL", style = MaterialTheme.typography.titleSmall, color = AccentBlue)
        Spacer(Modifier.height(12.dp))
        ServiceButton(
            status = state.tidalStatus,
            isLoading = state.isLoadingTidal,
            loadingMessage = null,
            connectLabel = "CONECTAR",
            connectedColor = AccentBlue,
            onClick = {
                if (state.tidalStatus is ConnectionStatus.Connected)
                    viewModel.disconnectTidal()
                else
                    viewModel.connectTidal()
            }
        )

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = DividerColor)
    }
}

@Composable
private fun ServiceButton(
    status: ConnectionStatus,
    isLoading: Boolean,
    loadingMessage: String?,
    connectLabel: String,
    connectedColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onClick,
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = when (status) {
                    is ConnectionStatus.Connected -> connectedColor
                    is ConnectionStatus.Error -> ErrorRed
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("CONECTANDO...")
            } else {
                Text(when (status) {
                    is ConnectionStatus.Connected -> "● CONECTADO"
                    is ConnectionStatus.Error -> "ERROR — REINTENTAR"
                    else -> connectLabel
                })
            }
        }
        if (isLoading && loadingMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = loadingMessage,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
