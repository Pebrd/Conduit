package com.conduit.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.conduit.domain.model.*
import com.conduit.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    playlistId: String,
    playlistName: String,
    viewModel: SyncViewModel = koinViewModel(),
    onFinish: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var showDetails by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        viewModel.syncPlaylist(playlistId, playlistName)
    }

    val progress = state.progress

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showDetails) "SYNC DETAILS" else "SYNCING...") },
                navigationIcon = {
                    if (showDetails) {
                        IconButton(onClick = { showDetails = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AmoledBlack,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = AmoledBlack
    ) { padding ->
        if (showDetails && progress is SyncProgress.Completed) {
            SyncDetailsView(
                playlistId = progress.result?.playlistId ?: playlistId,
                progress = progress,
                paddingValues = padding,
                viewModel = viewModel,
                onBackClick = { showDetails = false }
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = playlistName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                when (progress) {
                    is SyncProgress.Started -> {
                        CircularProgressIndicator(color = SuccessGreen)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Starting sync...", color = Color.White)
                    }
                    is SyncProgress.TracksLoaded -> {
                        CircularProgressIndicator(color = SuccessGreen)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loaded ${progress.total} tracks from Spotify", color = Color.White)
                    }
                    is SyncProgress.Running -> {
                        LinearProgressIndicator(
                            progress = { progress.current.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = SuccessGreen,
                            trackColor = Color.DarkGray,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Butt
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "${progress.current} / ${progress.total}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                        
                        Text(
                            text = progress.currentTrack,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatItem(Icons.Default.CheckCircle, SuccessGreen, progress.matched, "Matched")
                            StatItem(Icons.Default.Cancel, ErrorRed, progress.notFound, "Not Found")
                            StatItem(Icons.Default.Warning, WarningYellow, progress.duplicates, "Duplicate")
                        }
                    }
                    is SyncProgress.Completed -> {
                        Text("COMPLETED!", color = SuccessGreen, style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("${progress.matched} tracks synced in ${progress.durationMs / 1000}s", color = Color.White)
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showDetails = true },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.extraSmall,
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant)
                            ) {
                                Text("VER DETALLES", color = Color.White)
                            }

                            Button(
                                onClick = onFinish,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text("DONE")
                            }
                        }
                    }
                    is SyncProgress.Error -> {
                        Text("ERROR", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(progress.message, color = Color.White)
                    }
                    null -> {
                        CircularProgressIndicator(color = SuccessGreen)
                    }
                }

                state.error?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun StatItem(icon: ImageVector, iconColor: Color, count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(count.toString(), color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun SyncDetailsView(
    playlistId: String,
    progress: SyncProgress.Completed,
    paddingValues: PaddingValues,
    viewModel: SyncViewModel,
    onBackClick: () -> Unit
) {
    val result = progress.result
    val matched = result?.matched ?: emptyList()
    val duplicates = result?.duplicates ?: emptyList()
    val notFound = result?.notFound ?: emptyList()
    val lowConf = result?.lowConfidence ?: emptyList()
    val blacklisted = result?.blacklisted ?: emptyList()

    var activeSpotifyTrackToMap by remember { mutableStateOf<Track?>(null) }

    Column(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(16.dp)
    ) {
        // Summary Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = progress.playlistName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sincronizados: ${progress.matched}", color = SuccessGreen, style = MaterialTheme.typography.bodyMedium)
                    Text("No Encontrados: ${progress.notFound}", color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Duplicados: ${progress.duplicates}", color = WarningYellow, style = MaterialTheme.typography.bodyMedium)
                    Text("Baja Confianza: ${progress.lowConfidence}", color = AccentBlue, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scrollable List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (lowConf.isNotEmpty()) {
                item { CategoryHeader("Baja Confianza — Presiona para corregir", WarningYellow, Icons.Filled.Warning) }
                items(lowConf) { item ->
                    DetailRow(
                        title = item.spotify.name,
                        artist = item.spotify.artist,
                        subText = "Tidal: ${item.tidal.name} (${item.tidal.artist})\n[Toca para corregir/buscar manualmente]",
                        badge = "${(item.score * 100).toInt()}% match",
                        badgeColor = WarningYellow,
                        onClick = { activeSpotifyTrackToMap = item.spotify }
                    )
                }
            }

            if (notFound.isNotEmpty()) {
                item { CategoryHeader("No Encontrados — Presiona para buscar", ErrorRed, Icons.Filled.Cancel) }
                items(notFound) { item ->
                    DetailRow(
                        title = item.name,
                        artist = item.artist,
                        subText = "No se halló en Tidal.\n[Toca para vincular manualmente]",
                        badge = "Vincular",
                        badgeColor = ErrorRed,
                        onClick = { activeSpotifyTrackToMap = item }
                    )
                }
            }

            if (matched.isNotEmpty()) {
                item { CategoryHeader("Sincronizados con Éxito", SuccessGreen, Icons.Filled.CheckCircle) }
                items(matched) { item ->
                    val quality = item.tidal.audioQuality ?: ""
                    DetailRow(
                        title = item.spotify.name,
                        artist = item.spotify.artist,
                        subText = "Tidal: ${item.tidal.name} (${item.tidal.artist})",
                        badge = if (quality.isNotBlank()) quality else item.method.name,
                        badgeColor = SuccessGreen
                    )
                }
            }

            if (duplicates.isNotEmpty()) {
                item { CategoryHeader("Duplicados — Ya Existentes", Color.Gray, Icons.Filled.Repeat) }
                items(duplicates) { item ->
                    DetailRow(
                        title = item.name,
                        artist = item.artist,
                        subText = "Ya estaba en la playlist de Tidal (omitido)",
                        badge = "Duplicate",
                        badgeColor = Color.Gray
                    )
                }
            }

            if (blacklisted.isNotEmpty()) {
                item { CategoryHeader("Ignorados — Blacklist", Color.DarkGray, Icons.Filled.Block) }
                items(blacklisted) { item ->
                    DetailRow(
                        title = item.name,
                        artist = item.artist,
                        subText = "Excluido por lista negra del usuario",
                        badge = "Excluded",
                        badgeColor = Color.DarkGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraSmall,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant)
        ) {
            Text("VOLVER AL RESUMEN", color = Color.White)
        }
    }

    if (activeSpotifyTrackToMap != null) {
        ManualMappingDialog(
            spotifyTrack = activeSpotifyTrackToMap!!,
            viewModel = viewModel,
            playlistId = playlistId,
            onDismiss = { activeSpotifyTrackToMap = null }
        )
    }
}

@Composable
fun CategoryHeader(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.15f))
            .padding(vertical = 6.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}

@Composable
fun DetailRow(
    title: String,
    artist: String,
    subText: String,
    badge: String,
    badgeColor: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = badgeColor.copy(alpha = 0.2f),
                contentColor = badgeColor,
                shape = MaterialTheme.shapes.extraSmall,
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = badge,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualMappingDialog(
    spotifyTrack: Track,
    viewModel: SyncViewModel,
    playlistId: String,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("${spotifyTrack.name} ${spotifyTrack.artist}") }
    var results by remember { mutableStateOf<List<TidalTrack>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Trigger initial search
    LaunchedEffect(Unit) {
        searching = true
        results = viewModel.searchTidalTracks(query)
        searching = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.85f),
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = AmoledBlack,
                shape = MaterialTheme.shapes.extraSmall,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "VINCULAR TEMA",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "Spotify: ${spotifyTrack.name} - ${spotifyTrack.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Búsqueda Manual
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = SurfaceVariant,
                                unfocusedContainerColor = SurfaceVariant
                            ),
                            shape = MaterialTheme.shapes.extraSmall,
                            singleLine = true,
                            placeholder = { Text("Buscar en Tidal...", color = Color.DarkGray) }
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    searching = true
                                    results = viewModel.searchTidalTracks(query)
                                    searching = false
                                }
                            },
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("BUSCAR", color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Lista de resultados
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (searching) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = SuccessGreen
                            )
                        } else if (results.isEmpty()) {
                            Text(
                                text = "Sin resultados. Prueba otra búsqueda.",
                                modifier = Modifier.align(Alignment.Center),
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(results) { track ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                                        shape = MaterialTheme.shapes.extraSmall,
                                        onClick = {
                                            viewModel.mapTrackManually(playlistId, spotifyTrack, track)
                                            onDismiss()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = track.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = track.artist,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = OnSurfaceDim
                                                )
                                                Text(
                                                    text = "Álbum: ${track.album}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Column(horizontalAlignment = Alignment.End) {
                                                Surface(
                                                    color = SuccessGreen.copy(alpha = 0.2f),
                                                    contentColor = SuccessGreen,
                                                    shape = MaterialTheme.shapes.extraSmall,
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
                                                ) {
                                                    Text(
                                                        text = "VINCULAR",
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val durMin = (track.durationMs / 1000) / 60
                                                val durSec = (track.durationMs / 1000) % 60
                                                Text(
                                                    text = "$durMin:${durSec.toString().padStart(2, '0')}",
                                                    color = Color.Gray,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text("CANCELAR", color = Color.White)
                        }
                    }
                }
            }
        }
    )
}
