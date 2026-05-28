package com.conduit.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.conduit.domain.model.DiscoverTrack
import com.conduit.ui.theme.AmoledBlack
import com.conduit.ui.theme.OnSurfaceDim
import com.conduit.ui.theme.SurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    queue: List<DiscoverTrack>,
    isPlaying: Boolean,
    sessionInfo: String,
    destinationInfo: String,
    onLike: (DiscoverTrack) -> Unit,
    onSkip: (DiscoverTrack) -> Unit,
    onTogglePreview: (DiscoverTrack) -> Unit,
    onDestinationClick: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("DISCOVER", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(sessionInfo, color = Color.Gray, fontSize = 10.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmoledBlack, titleContentColor = Color.White)
            )
        },
        containerColor = AmoledBlack
    ) { padding ->
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hay más canciones", color = Color.Gray, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Probá con otra semilla de mood", color = OnSurfaceDim)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant)) {
                        Text("Volver a empezar", color = Color.White)
                    }
                }
            }
        } else {
            val currentTrack = queue.first()

            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentTrack.artworkUrl != null) {
                                AsyncImage(
                                    model = currentTrack.artworkUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(currentTrack.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(currentTrack.artist, color = Color.LightGray, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))

                            if (currentTrack.genres.isNotEmpty()) {
                                Text(
                                    currentTrack.genres.joinToString(" · "),
                                    color = Color(0xFFBB86FC),
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Button(
                                onClick = { onTogglePreview(currentTrack) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isPlaying) "PAUSAR" else "PREVIEW 0:30",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = { onSkip(currentTrack) },
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Skip", modifier = Modifier.size(28.dp))
                    }

                    Button(
                        onClick = { onLike(currentTrack) },
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = "Like", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                TextButton(onClick = onDestinationClick) {
                    Text(destinationInfo, color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}
