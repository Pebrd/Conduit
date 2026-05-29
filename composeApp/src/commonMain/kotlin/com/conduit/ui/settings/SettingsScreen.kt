package com.conduit.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInNew
import com.conduit.ui.theme.*
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var showInstructions by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    if (showInstructions) {
        AlertDialog(
            onDismissRequest = { showInstructions = false },
            confirmButton = {
                TextButton(onClick = { showInstructions = false }) {
                    Text("GOT IT", color = MaterialTheme.colorScheme.primary)
                }
            },
            title = { Text("How to get Client IDs") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Spotify:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("1. Go to developer.spotify.com and Create App\n2. Add Redirect URIs:\n   • conduit://spotify/callback (Android)\n   • http://127.0.0.1:8888/spotify/callback (Desktop)\n3. Copy the Client ID & Secret\n4. In 'User Management', add your Spotify email", style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        Text("Tidal:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        Text("1. Go to developer.tidal.com and Create App\n2. Copy the Client ID & Secret", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            containerColor = SurfaceDark,
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            shape = MaterialTheme.shapes.extraSmall
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS", style = MaterialTheme.typography.titleLarge) },
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
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .padding(16.dp)
            ) {
                Text(
                    "Configuration",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Set up your API credentials to enable synchronization. For mobile/desktop apps, the Client Secret is optional if PKCE is used, but providing it ensures compatibility with all flows.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceDim
                )
                
                TextButton(
                    onClick = { showInstructions = true },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Instruction Guide ↗", color = MaterialTheme.colorScheme.primary)
                }
            }

            // Spotify Section
            SettingsSection(title = "SPOTIFY") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.spotifyClientId,
                        onValueChange = viewModel::updateSpotifyClientId,
                        label = { Text("Client ID") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.labelSmall,
                        colors = textFieldColors()
                    )
                    IconButton(onClick = { uriHandler.openUri("https://developer.spotify.com") }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open Dashboard", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.spotifyClientSecret,
                    onValueChange = viewModel::updateSpotifyClientSecret,
                    label = { Text("Client Secret (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.labelSmall,
                    colors = textFieldColors(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = viewModel::connectSpotify,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isSpotifyConnected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (state.isSpotifyConnected) Color.Black else MaterialTheme.colorScheme.primary
                    ),
                    border = if (!state.isSpotifyConnected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    if (state.isSpotifyConnected) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CONNECTED")
                    } else {
                        Text("CONNECT SPOTIFY")
                    }
                }
            }

            // Tidal Section
            SettingsSection(title = "TIDAL") {
                if (state.error != null) {
                    Text(
                        text = state.error!!,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.tidalClientId,
                        onValueChange = viewModel::updateTidalClientId,
                        label = { Text("Client ID") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.labelSmall,
                        colors = textFieldColors()
                    )
                    IconButton(onClick = { uriHandler.openUri("https://developer.tidal.com") }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open Dashboard", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.tidalClientSecret,
                    onValueChange = viewModel::updateTidalClientSecret,
                    label = { Text("Client Secret (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.labelSmall,
                    colors = textFieldColors(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val deviceCode = state.tidalDeviceCode
                if (deviceCode != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("CÓDIGO DE VINCULACIÓN", style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
                            Text(
                                deviceCode,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Introduce este código en la web de Tidal que se ha abierto.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = OnSurfaceDim
                            )
                        }
                    }
                }

                Button(
                    onClick = viewModel::connectTidal,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isTidalConnected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                        contentColor = if (state.isTidalConnected) Color.Black else MaterialTheme.colorScheme.secondary
                    ),
                    border = if (!state.isTidalConnected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    if (state.isTidalConnected) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CONNECTED")
                    } else {
                        Text("CONNECT TIDAL")
                    }
                }
            }

            // Last.fm Section
            SettingsSection(title = "LAST.FM (DISCOVER)") {
                OutlinedTextField(
                    value = state.lastfmApiKey,
                    onValueChange = viewModel::updateLastfmApiKey,
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.labelSmall,
                    colors = textFieldColors(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Necesitás una API key de last.fm/api para el Discover. Sin API key no funciona.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
            }

            // Accounts Section
            SettingsSection(title = "CUENTAS") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Spotify", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    TextButton(onClick = { viewModel.disconnectSpotify() }) {
                        Text("DESCONECTAR", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                    }
                }
                HorizontalDivider(color = DividerColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Tidal", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    TextButton(onClick = { viewModel.disconnectTidal() }) {
                        Text("DESCONECTAR", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                    }
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    state.loadingMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
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
