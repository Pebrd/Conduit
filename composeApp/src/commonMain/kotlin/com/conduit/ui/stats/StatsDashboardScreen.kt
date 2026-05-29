package com.conduit.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.conduit.domain.model.*
import com.conduit.ui.theme.*
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDashboardScreen(
    viewModel: StatsViewModel = koinViewModel(),
    onNavigateToTopArtists: () -> Unit = {},
    onNavigateToTopTracks: () -> Unit = {},
    onNavigateToRecentlyPlayed: () -> Unit = {},
    onNavigateToArtistDetail: (String) -> Unit = {},
    onNavigateToTrackDetail: (String) -> Unit = {},
) {
    val state by viewModel.dashboardState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Stats") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AmoledBlack,
                    titleContentColor = Color.White,
                )
            )
        },
        containerColor = AmoledBlack,
    ) { padding ->
        if (state.isLoading && state.profile == null) {
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

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Profile card
            state.profile?.let { profile ->
                item { ProfileCard(profile) }
            }

            // Mood analysis card
            state.moodAnalysis?.let { mood ->
                if (mood != MoodAnalysis(0.0, 0.0, 0.0, 0.0, 0.0)) {
                    item { MoodAnalysisCard(mood) }
                }
            }

            // Genre distribution
            if (state.genreDistribution.isNotEmpty()) {
                item { GenreCard(state.genreDistribution) }
            }

            // Top artists preview
            if (state.topArtists.isNotEmpty()) {
                item { SectionHeader("Top Artists", "View all", onNavigateToTopArtists) }
                items(state.topArtists.take(3)) { artist ->
                    TopArtistRow(
                        artist = artist,
                        onClick = { onNavigateToArtistDetail(artist.id) },
                    )
                }
            }

            // Top tracks preview
            if (state.topTracks.isNotEmpty()) {
                item { SectionHeader("Top Tracks", "View all", onNavigateToTopTracks) }
                items(state.topTracks.take(3)) { track ->
                    TopTrackRow(
                        track = track,
                        onClick = { onNavigateToTrackDetail(track.id) },
                    )
                }
            }

            // Recently played preview
            if (state.recentlyPlayed.isNotEmpty()) {
                item { SectionHeader("Recently Played", "View all", onNavigateToRecentlyPlayed) }
                items(state.recentlyPlayed.take(3)) { item ->
                    RecentlyPlayedRow(
                        item = item,
                        onClick = { onNavigateToTrackDetail(item.trackId) },
                    )
                }
            }

            // Bottom spacer
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun ProfileCard(profile: UserProfile) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = profile.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(72.dp).background(Color.DarkGray, MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${profile.followers} followers",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim,
                )
                Text(
                    text = profile.product.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (profile.product == "premium") AccentSage else OnSurfaceDim,
                )
            }
        }
    }
}

@Composable
fun MoodAnalysisCard(mood: MoodAnalysis) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Listening Mood", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(12.dp))

            MoodBar("Valence", mood.averageValence, AccentSage)
            Spacer(Modifier.height(8.dp))
            MoodBar("Energy", mood.averageEnergy, AccentBlue)
            Spacer(Modifier.height(8.dp))
            MoodBar("Danceability", mood.averageDanceability, AccentSage)
            Spacer(Modifier.height(8.dp))
            MoodBar("Acousticness", mood.averageAcousticness, WarningYellow)
        }
    }
}

@Composable
fun MoodBar(label: String, value: Double, color: Color) {
    val clamped = value.toFloat().coerceIn(0f, 1f)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
            Text(String.format("%.0f%%", value * 100), style = MaterialTheme.typography.bodySmall, color = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { clamped },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = SurfaceVariant,
        )
    }
}

@Composable
fun GenreCard(genres: List<GenreCount>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Top Genres", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(12.dp))
            genres.take(8).forEach { genre ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(genre.genre.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text("${genre.count}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
        TextButton(onClick = onAction) {
            Text(actionLabel, color = AccentSage)
        }
    }
}

@Composable
fun TopArtistRow(artist: TopArtistItem, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Text("${artist.popularity}", style = MaterialTheme.typography.labelSmall, color = AccentSage)
        }
    }
}

@Composable
fun TopTrackRow(track: TopTrackItem, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = track.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).background(Color.DarkGray, MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceDim,
            )
        }
    }
}

@Composable
fun RecentlyPlayedRow(item: RecentlyPlayedItem, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).background(Color.DarkGray, MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.trackName, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                Text(item.artist, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim, maxLines = 1)
            }
            Text(
                formatPlayedAt(item.playedAt),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceDim,
            )
        }
    }
}
