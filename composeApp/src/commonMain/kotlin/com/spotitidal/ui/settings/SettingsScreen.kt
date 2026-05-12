package com.spotitidal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spotitidal.ui.theme.AmoledBlack
import com.spotitidal.ui.theme.SurfaceDark
import com.spotitidal.ui.theme.OnSurfaceDim
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AmoledBlack,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = AmoledBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Spotify Section
            SettingsSection(title = "SPOTIFY") {
                OutlinedTextField(
                    value = state.spotifyClientId,
                    onValueChange = viewModel::updateSpotifyClientId,
                    label = { Text("Client ID") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = viewModel::connectSpotify,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isSpotifyConnected) MaterialTheme.colorScheme.primary else Color.DarkGray
                    ),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(if (state.isSpotifyConnected) "✓ CONNECTED" else "CONNECT SPOTIFY")
                }
            }

            // Tidal Section
            SettingsSection(title = "TIDAL") {
                OutlinedTextField(
                    value = state.tidalClientId,
                    onValueChange = viewModel::updateTidalClientId,
                    label = { Text("Client ID") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = viewModel::connectTidal,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isTidalConnected) MaterialTheme.colorScheme.secondary else Color.DarkGray
                    ),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(if (state.isTidalConnected) "✓ CONNECTED" else "CONNECT TIDAL")
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceDim,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color.DarkGray,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = OnSurfaceDim,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = MaterialTheme.colorScheme.primary
)
