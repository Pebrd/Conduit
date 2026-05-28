package com.conduit.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.conduit.domain.model.Playlist
import com.conduit.domain.model.Track
import com.conduit.ui.theme.AmoledBlack
import com.conduit.ui.theme.OnSurfaceDim
import com.conduit.ui.theme.SurfaceVariant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverSeedScreen(
    playlists: List<Playlist>,
    isBuilding: Boolean,
    error: String?,
    onPlaylistSelected: (Playlist) -> Unit,
    onTrackSelected: (Track) -> Unit,
    onSearch: suspend (String) -> List<Track> = { emptyList() },
    onBack: () -> Unit,
) {
    var step by remember { mutableStateOf<SeedStep>(SeedStep.CHOOSE_MODE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DISCOVER", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (step) {
                            SeedStep.CHOOSE_MODE -> onBack()
                            else -> step = SeedStep.CHOOSE_MODE
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmoledBlack, titleContentColor = Color.White)
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
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("Analizando música...", color = Color.Gray)
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
                    SeedStep.SEARCH_TRACK -> TrackSearchScreen(
                        onTrackSelected = onTrackSelected,
                        onSearch = onSearch,
                    )
                }
            }

            error?.let {
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter)) {
                    Text(it, color = Color.White)
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
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Button(
            onClick = onChoosePlaylist,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant),
        ) {
            Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text("USAR UNA PLAYLIST", color = Color.White, fontWeight = FontWeight.SemiBold)
        }

        Button(
            onClick = onChooseTrack,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant),
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text("USAR UNA CANCIÓN", color = Color.White, fontWeight = FontWeight.SemiBold)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(playlists) { playlist ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onPlaylistSelected(playlist) },
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = playlist.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).background(Color.DarkGray),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(playlist.name, color = Color.White, maxLines = 1)
                        Text("${playlist.trackCount} canciones", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSearchScreen(
    onTrackSelected: (Track) -> Unit,
    onSearch: suspend (String) -> List<Track>,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar canción...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray,
                ),
                singleLine = true,
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = {
                            if (query.isNotBlank()) {
                                scope.launch {
                                    isSearching = true
                                    results = onSearch(query)
                                    isSearching = false
                                }
                            }
                        }) {
                            Icon(Icons.Default.Search, tint = Color.White, contentDescription = "Search")
                        }
                    }
                }
            )
        }

        if (results.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Resultados:", color = Color.Gray, fontSize = 12.sp)
            }
            items(results) { track ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onTrackSelected(track) },
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).background(Color.DarkGray),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.name, color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            Text(track.artist, color = Color.Gray, maxLines = 1, fontSize = 13.sp)
                        }
                        Icon(Icons.Default.MusicNote, tint = Color(0xFF1DB954), contentDescription = null)
                    }
                }
            }
        } else if (!isSearching && query.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Sin resultados. Probá con otra búsqueda.", color = Color.Gray)
            }
        }
    }
}
