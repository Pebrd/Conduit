package com.conduit.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.conduit.domain.model.TopArtistItem
import com.conduit.domain.model.TopTrackItem
import com.conduit.ui.theme.*
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    viewModel: StatsViewModel = koinViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.artistDetailState.collectAsState()
    val artist = state.artist

    LaunchedEffect(artistId) {
        if (artist == null || artist.id != artistId) {
            viewModel.loadArtistDetailById(artistId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artist?.name ?: "Artist") },
                navigationIcon = {
                    TextButton(onClick = {
                        viewModel.resetArtistDetail()
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

        val a = artist ?: return@Scaffold

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            // Hero image
            AsyncImage(
                model = a.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(280.dp).background(Color.DarkGray),
                contentScale = ContentScale.Crop,
            )

            Column(modifier = Modifier.padding(20.dp)) {
                // Artist name
                Text(
                    a.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(8.dp))

                // Followers + Popularity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    StatChip("Followers", formatFollowers(a.followers))
                    StatChip("Popularity", "${a.popularity}%")
                }

                // Popularity bar
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { a.popularity / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = AccentSage,
                    trackColor = SurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))

                // Genres
                if (a.genres.isNotEmpty()) {
                    Text(
                        "Genres",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Genre chips
                    val rows = a.genres.chunked(3)
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { genre ->
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            genre.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = SurfaceVariant,
                                        labelColor = Color.White,
                                    ),
                                    border = null,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Top Tracks by this artist
                if (state.topTracks.isNotEmpty()) {
                    Text(
                        "Top Tracks",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))

                    state.topTracks.take(5).forEachIndexed { index, track ->
                        ArtistTrackRow(index + 1, track)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistTrackRow(rank: Int, track: TopTrackItem) {
    Card(
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
                modifier = Modifier.width(28.dp),
            )
            AsyncImage(
                model = track.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).background(Color.DarkGray, MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                Text(track.album, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim, maxLines = 1)
            }
            Text(
                formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceDim,
            )
        }
    }
}

private fun formatFollowers(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
