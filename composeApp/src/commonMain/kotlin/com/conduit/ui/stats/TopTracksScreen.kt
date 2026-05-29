package com.conduit.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.conduit.ui.theme.*
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopTracksScreen(
    viewModel: StatsViewModel = koinViewModel(),
    onBack: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
) {
    val state by viewModel.topTracksState.collectAsState()

    LaunchedEffect(state.selectedTimeRange) {
        if (state.tracks.isEmpty() && !state.isLoading) {
            viewModel.loadTopTracks()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Top Tracks") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = AccentSage) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AmoledBlack,
                    titleContentColor = Color.White,
                )
            )
        },
        containerColor = AmoledBlack,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TimeRangeSelector(
                selected = state.selectedTimeRange,
                onSelected = { viewModel.loadTopTracks(it) }
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentSage)
                }
                return@Scaffold
            }

            state.error?.let { error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error, color = ErrorRed)
                }
                return@Scaffold
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.tracks) { index, track ->
                    TrackDetailRow(
                        rank = index + 1,
                        track = track,
                        onClick = { onTrackClick(track.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun TrackDetailRow(rank: Int, track: com.conduit.domain.model.TopTrackItem, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$rank",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurfaceDim,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(32.dp),
            )
            AsyncImage(
                model = track.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).background(Color.DarkGray, MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatDuration(track.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceDim,
                )
                Text(
                    track.album,
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentSage,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 100.dp),
                )
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
