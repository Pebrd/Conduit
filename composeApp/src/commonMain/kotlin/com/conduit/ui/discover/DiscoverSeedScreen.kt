package com.conduit.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.conduit.domain.model.Playlist
import com.conduit.domain.model.Track
import com.conduit.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverSeedScreen(
    playlists: List<Playlist>,
    isBuilding: Boolean,
    error: String?,
    searchResults: List<Track>,
    isSearching: Boolean,
    onPlaylistSelected: (Playlist) -> Unit,
    onTrackSelected: (Track) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    var step by remember { mutableStateOf<SeedStep>(SeedStep.CHOOSE_MODE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DISCOVER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (step) {
                            SeedStep.CHOOSE_MODE -> onBack()
                            else -> step = SeedStep.CHOOSE_MODE
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmoledBlack, titleContentColor = OnSurface)
            )
        },
        containerColor = AmoledBlack
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (isBuilding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentSage)
                    Spacer(Modifier.height(16.dp))
                    Text("Analizando música...", color = OnSurfaceDim)
                }
            } else {
                when (step) {
                    SeedStep.CHOOSE_MODE -> ChooseModeScreen(
                        onChoosePlaylist = { step = SeedStep.SELECT_PLAYLIST },
                        onChooseTrack = { step = SeedStep.SEARCH_TRACK },
                    )
                    SeedStep.SELECT_PLAYLIST -> PlaylistPickerScreen(
                        playlists = playlists,
                        onPlaylistSelected = onPlaylistSelected,
                    )
                    SeedStep.SEARCH_TRACK ->                     TrackSearchScreen(
                        searchResults = searchResults,
                        isSearching = isSearching,
                        onQueryChanged = onSearchQueryChanged,
                        onTrackSelected = { track ->
                            onTrackSelected(track)
                        },
                    )
                }
            }

            error?.let {
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter)) {
                    Text(it, color = OnSurface)
                }
            }
        }
    }
}

private enum class SeedStep { CHOOSE_MODE, SELECT_PLAYLIST, SEARCH_TRACK }

@Composable
private fun ChooseModeScreen(
    onChoosePlaylist: () -> Unit,
    onChooseTrack: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "¿Qué mood estás buscando?",
            style = MaterialTheme.typography.titleLarge,
            color = OnSurface,
        )

        Button(
            onClick = onChoosePlaylist,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = MaterialTheme.shapes.extraSmall,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant),
        ) {
            Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = OnSurface)
            Spacer(Modifier.width(4.dp))
            Text("USAR UNA PLAYLIST", color = OnSurface, fontWeight = FontWeight.SemiBold)
        }

        Button(
            onClick = onChooseTrack,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = MaterialTheme.shapes.extraSmall,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant),
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = OnSurface)
            Spacer(Modifier.width(4.dp))
            Text("USAR UNA CANCIÓN", color = OnSurface, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PlaylistPickerScreen(
    playlists: List<Playlist>,
    onPlaylistSelected: (Playlist) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(playlists) { playlist ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onPlaylistSelected(playlist) },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = playlist.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).background(SurfaceDark),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(playlist.name, color = OnSurface, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        Text("${playlist.trackCount} canciones", color = OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSearchScreen(
    searchResults: List<Track>,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onTrackSelected: (Track) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { q ->
                    query = q
                    onQueryChanged(q)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar canción...", color = OnSurfaceDim) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                    focusedBorderColor = AccentSage,
                    unfocusedBorderColor = OnSurfaceDim,
                ),
                singleLine = true,
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AccentSage,
                            strokeWidth = 2.dp
                        )
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; onQueryChanged("") }) {
                            Icon(Icons.Default.Clear, tint = OnSurfaceDim, contentDescription = "Clear")
                        }
                    }
                }
            )
        }

        if (searchResults.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Resultados:", color = OnSurfaceDim, style = MaterialTheme.typography.labelSmall)
            }
            items(searchResults) { track ->
                TrackRow(
                    track = track,
                    onClick = { onTrackSelected(track) },
                )
            }
        } else if (!isSearching && query.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Sin resultados. Probá con otra búsqueda.", color = OnSurfaceDim)
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).background(SurfaceDark),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.name, color = OnSurface, maxLines = 1, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(track.artist, color = OnSurfaceDim, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                Text(track.album, color = OnSurfaceDim, maxLines = 1, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
