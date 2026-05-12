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
            // Header / Landing info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .padding(16.dp)
            ) {
                Text(
                    "Welcome to SpotiTidal",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "To begin, you need to provide your own API credentials. This ensures your data stays private and gives you full control over the synchronization.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceDim
                )
                
                TextButton(
                    onClick = { /* TODO: Show dialog with info */ },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("How to get my Client IDs? ↗", color = MaterialTheme.colorScheme.primary)
                }
            }

            // Spotify Section
            SettingsSection(title = "SPOTIFY") {
                OutlinedTextField(
                    value = state.spotifyClientId,
                    onValueChange = viewModel::updateSpotifyClientId,
                    label = { Text("Client ID") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.labelSmall,
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
                    textStyle = MaterialTheme.typography.labelSmall,
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

            // Sync Settings
            SettingsSection(title = "SYNC CONFIGURATION") {
                Text(
                    "Sync Interval",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("MANUAL", "DAILY", "WEEKLY").forEach { interval ->
                        FilterChip(
                            selected = false, // TODO: Bind to state
                            onClick = { /* TODO */ },
                            label = { Text(interval) },
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = AmoledBlack,
                                labelColor = OnSurfaceDim,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
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
