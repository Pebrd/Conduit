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
import androidx.compose.ui.unit.sp
import com.conduit.domain.model.DiscoverDestination
import com.conduit.domain.model.MusicService
import com.conduit.domain.model.Playlist
import com.conduit.ui.theme.AmoledBlack
import com.conduit.ui.theme.SurfaceVariant

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
                title = { Text("DISCOVER", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmoledBlack, titleContentColor = Color.White)
            )
        },
        containerColor = AmoledBlack
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("¿Dónde guardar los likes?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlatformButton(
                    name = "SPOTIFY",
                    color = Color(0xFF1DB954),
                    selected = destination.platform == MusicService.SPOTIFY,
                    onClick = { onPlatformChange(MusicService.SPOTIFY) },
                    modifier = Modifier.weight(1f)
                )
                PlatformButton(
                    name = "TIDAL",
                    color = Color(0xFF00FFFF),
                    selected = destination.platform == MusicService.TIDAL,
                    onClick = { onPlatformChange(MusicService.TIDAL) },
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth().clickable { showPlaylistPicker = true },
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(destination.playlistName, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (destination.playlistId == null) "Nueva playlist" else "Playlist existente",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Default.Edit, contentDescription = "Change", tint = Color.Gray)
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
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
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) color else SurfaceVariant
        )
    ) {
        Text(
            name,
            color = if (selected) Color.Black else Color.White,
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
        title = { Text("Elegir playlist", color = Color.White) },
        text = {
            LazyColumn {
                item {
                    TextButton(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Crear nueva", color = Color.White)
                    }
                }
                items(playlists) { playlist ->
                    TextButton(onClick = { onSelect(playlist) }, modifier = Modifier.fillMaxWidth()) {
                        Text(playlist.name, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.Gray)
            }
        }
    )
}
