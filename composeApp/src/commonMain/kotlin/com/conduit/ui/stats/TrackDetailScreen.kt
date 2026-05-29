package com.conduit.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.conduit.ui.theme.*
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(
    trackId: String,
    viewModel: StatsViewModel = koinViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.trackDetailState.collectAsState()
    val track = state.track

    LaunchedEffect(trackId) {
        if (track == null || track.id != trackId) {
            viewModel.loadTrackDetailById(trackId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(track?.name ?: "Track") },
                navigationIcon = {
                    TextButton(onClick = {
                        viewModel.resetTrackDetail()
                        onBack()
                    }) { Text("Back", color = AccentSage) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AmoledBlack,
                    titleContentColor = Color.White,
                )
            )
        },
        containerColor = AmoledBlack,
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentSage)
            }
            return@Scaffold
        }

        state.error?.let { error ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error, color = ErrorRed)
            }
            return@Scaffold
        }

        val t = track ?: return@Scaffold

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            // Hero image
            AsyncImage(
                model = t.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(280.dp).background(Color.DarkGray),
                contentScale = ContentScale.Crop,
            )

            Column(modifier = Modifier.padding(20.dp)) {
                // Track name
                Text(
                    t.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(8.dp))

                // Artist & Album
                Text(
                    t.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = AccentSage,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    t.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceDim,
                )

                Spacer(Modifier.height(20.dp))

                // Duration + Popularity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    StatChip("Duration", formatDuration(t.durationMs))
                    StatChip("Popularity", "${t.popularity}%")
                }

                Spacer(Modifier.height(24.dp))

                // Audio Features
                state.audioFeatures?.let { af ->
                    Text(
                        "Audio Features",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))

                    MoodBar("Valence", af.valence, AccentSage)
                    Spacer(Modifier.height(8.dp))
                    MoodBar("Energy", af.energy, AccentBlue)
                    Spacer(Modifier.height(8.dp))
                    MoodBar("Danceability", af.danceability, AccentSage)
                    Spacer(Modifier.height(8.dp))
                    MoodBar("Acousticness", af.acousticness, WarningYellow)
                    Spacer(Modifier.height(8.dp))
                    MoodBar("Speechiness", af.speechiness, AccentBlue)
                    Spacer(Modifier.height(8.dp))
                    MoodBar("Instrumentalness", af.instrumentalness, AccentSage)
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Tempo", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
                        Text("${af.tempo.toInt()} BPM", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
