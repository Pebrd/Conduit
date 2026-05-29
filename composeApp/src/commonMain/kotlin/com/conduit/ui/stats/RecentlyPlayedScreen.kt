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
fun RecentlyPlayedScreen(
    viewModel: StatsViewModel = koinViewModel(),
    onBack: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
) {
    val state by viewModel.recentlyPlayedState.collectAsState()

    LaunchedEffect(Unit) {
        if (state.items.isEmpty() && !state.isLoading) {
            viewModel.loadRecentlyPlayed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recently Played") },
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

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.items) { index, item ->
                RecentlyPlayedDetailRow(
                    rank = index + 1,
                    item = item,
                    onClick = { onTrackClick(item.trackId) },
                )
            }
        }
    }
}

@Composable
fun RecentlyPlayedDetailRow(rank: Int, item: com.conduit.domain.model.RecentlyPlayedItem, onClick: () -> Unit = {}) {
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

fun formatPlayedAt(isoString: String): String {
    return isoString.take(16).replace("T", " ")
}
