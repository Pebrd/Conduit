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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.conduit.ui.theme.*
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopArtistsScreen(
    viewModel: StatsViewModel = koinViewModel(),
    onBack: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
) {
    val state by viewModel.topArtistsState.collectAsState()

    LaunchedEffect(state.selectedTimeRange) {
        if (state.artists.isEmpty() && !state.isLoading) {
            viewModel.loadTopArtists()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Top Artists") },
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
                onSelected = { viewModel.loadTopArtists(it) }
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
                itemsIndexed(state.artists) { index, artist ->
                    ArtistDetailRow(
                        rank = index + 1,
                        artist = artist,
                        onClick = { onArtistClick(artist.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistDetailRow(rank: Int, artist: com.conduit.domain.model.TopArtistItem, onClick: () -> Unit = {}) {
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
                model = artist.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).background(Color.DarkGray, MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(artist.name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                if (artist.genres.isNotEmpty()) {
                    Text(
                        artist.genres.take(2).joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } },
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim,
                        maxLines = 1,
                    )
                }
            }
            // Simple popularity number (no bar)
            Text(
                "${artist.popularity}",
                style = MaterialTheme.typography.labelSmall,
                color = AccentSage,
            )
        }
    }
}

@Composable
fun TimeRangeSelector(selected: String, onSelected: (String) -> Unit) {
    val ranges = listOf(
        "short_term" to "4 weeks",
        "medium_term" to "6 months",
        "long_term" to "All time",
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ranges.forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelected(key) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = SurfaceVariant,
                    selectedContainerColor = AccentSage.copy(alpha = 0.2f),
                    labelColor = OnSurfaceDim,
                    selectedLabelColor = AccentSage,
                ),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}
