package com.conduit.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conduit.domain.model.DiscoverDestination
import com.conduit.domain.model.MusicService
import com.conduit.domain.model.Playlist
import com.conduit.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverPlatformScreen(
    destination: DiscoverDestination,
    spotifyPlaylists: List<Playlist>,
    tidalPlaylists: List<Playlist>,
    onPlatformChange: (MusicService) -> Unit,
    onDestinationChange: (playlistId: String, playlistName: String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    var showPlaylistPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DISCOVER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmoledBlack, titleContentColor = OnSurface)
            )
        },
        containerColor = AmoledBlack
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "¿Dónde guardar los likes?",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PlatformButton(
                    name = "SPOTIFY",
                    color = AccentSage,
                    selected = destination.platform == MusicService.SPOTIFY,
                    onClick = { onPlatformChange(MusicService.SPOTIFY) },
                    modifier = Modifier.weight(1f)
                )
                PlatformButton(
                    name = "TIDAL",
                    color = AccentBlue,
                    selected = destination.platform == MusicService.TIDAL,
                    onClick = { onPlatformChange(MusicService.TIDAL) },
                    modifier = Modifier.weight(1f)
                )
                PlatformButton(
                    name = "NINGUNO",
                    color = OnSurfaceDim,
                    selected = destination.platform == MusicService.NONE,
                    onClick = { onPlatformChange(MusicService.NONE) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Playlist picker card (hidden when NINGUNO)
            if (destination.platform != MusicService.NONE) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showPlaylistPicker = true },
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (destination.playlistId == null) Icons.Default.Add else Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            tint = OnSurface
                        )
                        Spacer(Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                destination.playlistName,
                                color = OnSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (destination.playlistId == null) "Se creará una playlist nueva"
                                else "Se agregará a playlist existente",
                                color = OnSurfaceDim,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(Icons.Default.Edit, contentDescription = "Cambiar", tint = OnSurfaceDim)
                    }
                }
            } else {
                // NINGUNO info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = OnSurfaceDim)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "No se guardarán los likes",
                            color = OnSurfaceDim,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.extraSmall,
                colors = ButtonDefaults.buttonColors(containerColor = AccentSage),
            ) {
                Text("EMPEZAR", color = AmoledBlack, fontWeight = FontWeight.Bold)
            }
        }

        if (showPlaylistPicker) {
            PlaylistPickerDialog(
                playlists = if (destination.platform == MusicService.SPOTIFY) spotifyPlaylists else tidalPlaylists,
                onSelect = { playlist ->
                    onDestinationChange(playlist.id, playlist.name)
                    showPlaylistPicker = false
                },
                onCreateNew = {
                    showPlaylistPicker = false
                },
                onDismiss = { showPlaylistPicker = false }
            )
        }
    }
}

@Composable
private fun PlatformButton(name: String, color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = MaterialTheme.shapes.extraSmall,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) color else SurfaceVariant
        )
    ) {
        Text(
            name,
            color = if (selected) AmoledBlack else OnSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text("Elegir playlist", color = OnSurface) },
        text = {
            LazyColumn {
                item {
                    TextButton(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = OnSurface)
                        Spacer(Modifier.width(8.dp))
                        Text("Crear nueva", color = OnSurface)
                    }
                }
                items(playlists) { playlist ->
                    TextButton(onClick = { onSelect(playlist) }, modifier = Modifier.fillMaxWidth()) {
                        Text(playlist.name, color = OnSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = OnSurfaceDim)
            }
        }
    )
}
