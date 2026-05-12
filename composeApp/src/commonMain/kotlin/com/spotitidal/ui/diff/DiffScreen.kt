package com.spotitidal.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spotitidal.domain.model.DiffEntry
import com.spotitidal.domain.model.DiffStatus
import com.spotitidal.ui.theme.AmoledBlack
import com.spotitidal.ui.theme.OnSurfaceDim
import com.spotitidal.ui.theme.SurfaceVariant
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffScreen(
    playlistId: String,
    viewModel: DiffViewModel = koinViewModel(),
    onSyncClick: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(playlistId) {
        viewModel.loadDiff(playlistId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("REVIEW") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AmoledBlack,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Button(
                onClick = { onSyncClick(playlistId) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text("SYNC NOW")
            }
        },
        containerColor = AmoledBlack
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.diffEntries) { entry ->
                    DiffRow(entry)
                }
            }
        }
    }
}

@Composable
fun DiffRow(entry: DiffEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.track.name, color = Color.White)
            Text(entry.track.artist, color = OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
        }
        
        val statusIcon = when (entry.status) {
            DiffStatus.OK -> "✅"
            DiffStatus.NEW -> "➕"
            DiffStatus.CONFLICT -> "⚠️"
            DiffStatus.REMOVED -> "❌"
            DiffStatus.MISSING -> "❓"
        }
        
        Text(statusIcon)
    }
}
