package com.conduit.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.conduit.domain.model.SyncProgress
import com.conduit.ui.theme.AmoledBlack
import com.conduit.ui.theme.SuccessGreen
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

    LaunchedEffect(playlistId) {
        viewModel.syncPlaylist(playlistId, playlistName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SYNCING...") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AmoledBlack,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = AmoledBlack
    ) { padding ->
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

            when (val progress = state.progress) {
                is SyncProgress.Started -> {
                    CircularProgressIndicator(color = SuccessGreen)
                    Text("Starting sync...", color = Color.White)
                }
                is SyncProgress.TracksLoaded -> {
                    CircularProgressIndicator(color = SuccessGreen)
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

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatItem("✅", progress.matched)
                        StatItem("❌", progress.notFound)
                        StatItem("⚠️", progress.duplicates)
                    }
                }
                is SyncProgress.Completed -> {
                    Text("COMPLETED!", color = SuccessGreen, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("${progress.matched} tracks synced in ${progress.durationMs / 1000}s", color = Color.White)
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text("DONE")
                    }
                }
                is SyncProgress.Error -> {
                    Text("ERROR", color = MaterialTheme.colorScheme.error)
                    Text(progress.message, color = Color.White)
                }
                null -> {
                    CircularProgressIndicator()
                }
            }

            state.error?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun StatItem(icon: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, style = MaterialTheme.typography.headlineSmall)
        Text(count.toString(), color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}
