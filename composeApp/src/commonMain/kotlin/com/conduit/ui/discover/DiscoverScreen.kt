package com.conduit.ui.discover

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.conduit.domain.model.DiscoverTrack
import com.conduit.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    queue: List<DiscoverTrack>,
    isPlaying: Boolean,
    isRefilling: Boolean,
    error: String? = null,
    sessionInfo: String,
    destinationInfo: String,
    onLike: (DiscoverTrack) -> Unit,
    onSkip: (DiscoverTrack) -> Unit,
    onTogglePreview: (DiscoverTrack) -> Unit,
    onAutoPlay: (DiscoverTrack) -> Unit,
    onDestinationClick: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "DISCOVER",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            sessionInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceDim,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmoledBlack, titleContentColor = OnSurface)
            )
        },
        containerColor = AmoledBlack
    ) { padding ->
        // ── Error banner ──
        error?.let { msg ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                    }
                }
            }
            return@Scaffold
        }

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (isRefilling) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentSage)
                        Spacer(Modifier.height(16.dp))
                        Text("Buscando más canciones...", style = MaterialTheme.typography.titleLarge, color = OnSurfaceDim)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No hay más canciones", style = MaterialTheme.typography.titleLarge, color = OnSurfaceDim)
                        Spacer(Modifier.height(8.dp))
                        Text("Probá con otra semilla de mood", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onBack,
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant)
                        ) {
                            Text("Volver a empezar", color = OnSurface)
                        }
                    }
                }
            }
        } else {
            val currentTrack = queue.first()

            // ── Auto-play preview when track changes ──
            LaunchedEffect(currentTrack.mbid) {
                onAutoPlay(currentTrack)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Swipeable card (constrained width on desktop) ──
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    SwipeableCard(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        onLike = onLike,
                        onSkip = onSkip,
                        onTogglePreview = onTogglePreview,
                        modifier = Modifier.fillMaxHeight().widthIn(max = 450.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Destination tag ──
                TextButton(onClick = onDestinationClick) {
                    Text(destinationInfo, style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
                }
            }
        }
    }
}

// ── Swipeable card with spring-animated drag gesture ──

private const val SWIPE_THRESHOLD = 0.4f

@Composable
private fun SwipeableCard(
    track: DiscoverTrack,
    isPlaying: Boolean,
    onLike: (DiscoverTrack) -> Unit,
    onSkip: (DiscoverTrack) -> Unit,
    onTogglePreview: (DiscoverTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val dragAnim = remember { Animatable(0f) }
    var actionTriggered by remember { mutableStateOf(false) }
    val dragFraction by dragAnim.asState()

    var cardWidth by remember { mutableStateOf(0f) }

    fun commitAction(direction: Float) {
        if (actionTriggered) return
        actionTriggered = true
        scope.launch {
            dragAnim.animateTo(
                targetValue = if (direction > 0) 1.5f else -1.5f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            if (direction > 0) onLike(track) else onSkip(track)
        }
    }

    fun snapBack() {
        scope.launch {
            dragAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Reset when track changes
    LaunchedEffect(track.mbid) {
        dragAnim.snapTo(0f)
        actionTriggered = false
    }

    Box(
        modifier = modifier
            .onSizeChanged { size -> cardWidth = size.width.toFloat() }
    ) {
        // ── Background: shown when swiping ──
        if (dragFraction != 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (dragFraction > 0) AccentSage else ErrorRed)
            ) {
                if (dragFraction > 0) {
                    // Like (swiping right)
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = "Like",
                        tint = AmoledBlack,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(32.dp)
                            .size(48.dp)
                            .scale(minOf(1f, abs(dragFraction) * 2f)),
                    )
                } else {
                    // Skip (swiping left)
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Skip",
                        tint = AmoledBlack,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(32.dp)
                            .size(48.dp)
                            .scale(minOf(1f, abs(dragFraction) * 2f)),
                    )
                }
            }
        }

        // ── Card with spring-animated drag ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset((dragFraction * cardWidth).roundToInt(), 0) }
                .pointerInput(track.mbid) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (!actionTriggered) {
                                snapBack()
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (actionTriggered) return@detectHorizontalDragGestures
                            val newFrac = (dragAnim.value + dragAmount / cardWidth)
                                .coerceIn(-1f, 1f)
                            scope.launch {
                                dragAnim.snapTo(newFrac)
                            }
                            if (abs(newFrac) >= SWIPE_THRESHOLD) {
                                commitAction(newFrac)
                            }
                        }
                    )
                }
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.extraSmall,
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Artwork
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f).background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        if (track.artworkUrl != null) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = OnSurfaceDim,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }

                    // Track info + preview control
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(track.name, style = MaterialTheme.typography.titleLarge, color = OnSurface)
                        Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDim)
                        Spacer(Modifier.height(4.dp))

                        if (track.genres.isNotEmpty()) {
                            Text(
                                track.genres.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentPurple,
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Play/Pause button — stays functional (swipe doesn't eat taps)
                        Button(
                            onClick = { onTogglePreview(track) },
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentSage),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                tint = AmoledBlack
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isPlaying) "PAUSAR" else "PREVIEW 0:30",
                                color = AmoledBlack,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
