package com.spotitidal.ui.navigation

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
import com.spotitidal.ui.theme.AmoledBlack
import com.spotitidal.ui.theme.SurfaceDark
import com.spotitidal.ui.home.HomeScreen
import com.spotitidal.ui.settings.SettingsScreen
import com.spotitidal.ui.diff.DiffScreen
import com.spotitidal.ui.sync.SyncScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home      : Screen("home", "Home", Icons.Default.Home)
    object Blacklist : Screen("blacklist", "Blacklist", Icons.Default.Block)
    object History   : Screen("history", "History", Icons.Default.History)
    object Settings  : Screen("settings", "Settings", Icons.Default.Settings)
    
    // Non-nav-bar screens
    object Diff      : Screen("diff/{playlistId}", "Review", Icons.Default.Preview)
    object Sync      : Screen("sync/{playlistId}/{playlistName}", "Sync", Icons.Default.Sync)
    object Resolve   : Screen("resolve/{syncResultId}", "Resolve", Icons.Default.Check)
}

val NavItems = listOf(Screen.Home, Screen.Blacklist, Screen.History, Screen.Settings)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Row(modifier = Modifier.fillMaxSize().background(AmoledBlack)) {
        // Navigation Rail for wide screens (Desktop)
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
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onSettingsClick = { navController.navigate(Screen.Settings.route) },
                        onPlaylistClick = { id -> navController.navigate("diff/$id") }
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
                        onSyncClick = { id -> navController.navigate("sync/$id/Syncing...") },
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
    }
}
