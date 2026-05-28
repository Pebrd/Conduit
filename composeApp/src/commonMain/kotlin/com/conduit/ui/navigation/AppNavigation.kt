package com.conduit.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import com.conduit.ui.theme.AmoledBlack
import com.conduit.ui.theme.SurfaceDark
import com.conduit.ui.home.HomeScreen
import com.conduit.ui.settings.SettingsScreen
import com.conduit.ui.diff.DiffScreen
import com.conduit.ui.sync.SyncScreen
import com.conduit.ui.auth.AuthScreen
import com.conduit.ui.stats.StatsDashboardScreen
import com.conduit.ui.stats.TopArtistsScreen
import com.conduit.ui.stats.TopTracksScreen
import com.conduit.ui.stats.RecentlyPlayedScreen
import com.conduit.ui.stats.TrackDetailScreen
import com.conduit.ui.stats.ArtistDetailScreen
import com.conduit.ui.stats.StatsViewModel
import com.conduit.ui.discover.DiscoverSeedScreen
import com.conduit.ui.discover.DiscoverPlatformScreen
import com.conduit.ui.discover.DiscoverScreen
import com.conduit.ui.discover.DiscoverViewModel
import com.conduit.ui.discover.DiscoverStep
import org.koin.compose.viewmodel.koinViewModel
import androidx.navigation.NavBackStackEntry

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Auth     : Screen("auth", "Auth", Icons.Default.Lock)
    object Home      : Screen("home", "Home", Icons.Default.Home)
    object Blacklist : Screen("blacklist", "Blacklist", Icons.Default.Block)
    object History   : Screen("history", "History", Icons.Default.History)
    object Settings  : Screen("settings", "Settings", Icons.Default.Settings)
    object Stats     : Screen("stats", "Stats", Icons.Default.BarChart)
    object Discover  : Screen("discover", "Discover", Icons.Default.Explore)
    
    // Non-nav-bar screens
    object Diff      : Screen("diff/{playlistId}", "Review", Icons.Default.Preview)
    object Sync      : Screen("sync/{playlistId}/{playlistName}", "Sync", Icons.Default.Sync)
    object Resolve   : Screen("resolve/{syncResultId}", "Resolve", Icons.Default.Check)
    
    // Stats sub-screens
    object TopArtists      : Screen("stats/top-artists", "Top Artists", Icons.Default.Person)
    object TopTracks       : Screen("stats/top-tracks", "Top Tracks", Icons.Default.MusicNote)
    object RecentlyPlayed  : Screen("stats/recently-played", "Recently Played", Icons.Default.History)
    object TrackDetail     : Screen("stats/track/{trackId}", "Track", Icons.Default.MusicNote)
    object ArtistDetail    : Screen("stats/artist/{artistId}", "Artist", Icons.Default.Person)
}

val NavItems = listOf(Screen.Home, Screen.Discover, Screen.Stats, Screen.Blacklist, Screen.History, Screen.Settings)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(AmoledBlack)) {
        val isCompact = maxWidth < 600.dp

        if (isCompact) {
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = SurfaceDark,
                        contentColor = Color.White
                    ) {
                        NavItems.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                alwaysShowLabel = false,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray,
                                    indicatorColor = AmoledBlack
                                )
                            )
                        }
                    }
                },
                containerColor = AmoledBlack
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    AppNavHost(navController)
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = SurfaceDark,
                    contentColor = Color.White,
                ) {
                    Spacer(Modifier.weight(1f))
                    NavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            alwaysShowLabel = false,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = AmoledBlack
                            )
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Box(modifier = Modifier.weight(1f)) {
                    AppNavHost(navController)
                }
            }
        }
    }
}

/** Returns the parent Stats NavBackStackEntry so all stats screens share one ViewModel. */
private fun parentStatsEntry(navController: androidx.navigation.NavHostController): NavBackStackEntry {
    return navController.getBackStackEntry(Screen.Stats.route)
}

@Composable
fun AppNavHost(navController: androidx.navigation.NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Auth.route
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onBothConnected = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onPlaylistClick = { id -> navController.navigate("diff/$id") },
                onDiscoverClick = { navController.navigate(Screen.Discover.route) },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Diff.route) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            DiffScreen(
                playlistId = playlistId,
                onSyncClick = { id, name -> 
                    val encodedName = name.replace("/", " ").replace("?", "")
                    navController.navigate("sync/$id/$encodedName") 
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Sync.route) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            val playlistName = backStackEntry.arguments?.getString("playlistName") ?: "Playlist"
            SyncScreen(
                playlistId = playlistId,
                playlistName = playlistName,
                onFinish = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Stats.route) { entry ->
            // The stats entry is the parent ViewModelStoreOwner for all stats screens
            StatsDashboardScreen(
                onNavigateToTopArtists = { navController.navigate(Screen.TopArtists.route) },
                onNavigateToTopTracks = { navController.navigate(Screen.TopTracks.route) },
                onNavigateToRecentlyPlayed = { navController.navigate(Screen.RecentlyPlayed.route) },
                onNavigateToArtistDetail = { id -> navController.navigate("stats/artist/$id") },
                onNavigateToTrackDetail = { id -> navController.navigate("stats/track/$id") },
            )
        }
        composable(Screen.TopArtists.route) {
            val statsEntry = parentStatsEntry(navController)
            TopArtistsScreen(
                onBack = { navController.popBackStack() },
                onArtistClick = { id -> navController.navigate("stats/artist/$id") },
                viewModel = koinViewModel(viewModelStoreOwner = statsEntry),
            )
        }
        composable(Screen.TopTracks.route) {
            val statsEntry = parentStatsEntry(navController)
            TopTracksScreen(
                onBack = { navController.popBackStack() },
                onTrackClick = { id -> navController.navigate("stats/track/$id") },
                viewModel = koinViewModel(viewModelStoreOwner = statsEntry),
            )
        }
        composable(Screen.RecentlyPlayed.route) {
            val statsEntry = parentStatsEntry(navController)
            RecentlyPlayedScreen(
                onBack = { navController.popBackStack() },
                onTrackClick = { id -> navController.navigate("stats/track/$id") },
                viewModel = koinViewModel(viewModelStoreOwner = statsEntry),
            )
        }
        composable(Screen.TrackDetail.route) { backStackEntry ->
            val trackId = backStackEntry.arguments?.getString("trackId") ?: return@composable
            val statsEntry = parentStatsEntry(navController)
            TrackDetailScreen(
                trackId = trackId,
                onBack = { navController.popBackStack() },
                viewModel = koinViewModel(viewModelStoreOwner = statsEntry),
            )
        }
        composable(Screen.ArtistDetail.route) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getString("artistId") ?: return@composable
            val statsEntry = parentStatsEntry(navController)
            ArtistDetailScreen(
                artistId = artistId,
                onBack = { navController.popBackStack() },
                viewModel = koinViewModel(viewModelStoreOwner = statsEntry),
            )
        }
        composable(Screen.Discover.route) {
            val discoverViewModel: DiscoverViewModel = koinViewModel()
            val state by discoverViewModel.state.collectAsState()

            when (state.step) {
                DiscoverStep.SEED -> DiscoverSeedScreen(
                    playlists = state.playlists,
                    isBuilding = state.isBuilding,
                    error = state.error,
                    onPlaylistSelected = { discoverViewModel.selectSeedPlaylist(it) },
                    onTrackSelected = { discoverViewModel.selectSeedTrack(it) },
                    onSearch = { discoverViewModel.searchSpotifyTracks(it) },
                    onBack = { navController.popBackStack() },
                )
                DiscoverStep.DESTINATION -> state.session?.let { session ->
                    DiscoverPlatformScreen(
                        destination = session.destination,
                        spotifyPlaylists = state.playlists,
                        tidalPlaylists = emptyList(),
                        onPlatformChange = { discoverViewModel.setPlatform(it) },
                        onDestinationChange = { id, name -> discoverViewModel.updateDestination(id, name) },
                        onConfirm = { discoverViewModel.confirmDestination() },
                        onBack = { discoverViewModel.reset() },
                    )
                }
                DiscoverStep.SWIPE -> state.session?.let { session ->
                    DiscoverScreen(
                        queue = state.queue,
                        isPlaying = state.isPlaying,
                        sessionInfo = "basado en: ${session.destination.playlistName}",
                        destinationInfo = "→ ${session.destination.playlistName} · ${session.destination.platform.name}",
                        onLike = { discoverViewModel.like(it) },
                        onSkip = { discoverViewModel.skip(it) },
                        onTogglePreview = { discoverViewModel.togglePreview(it) },
                        onDestinationClick = { /* show destination picker in future */ },
                        onBack = { discoverViewModel.reset() },
                        onFinish = { navController.popBackStack() },
                    )
                }
            }
        }
        composable(Screen.Blacklist.route) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Blacklist Screen (Coming Soon)", color = Color.White)
            }
        }
        composable(Screen.History.route) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("History Screen (Coming Soon)", color = Color.White)
            }
        }
    }
}
