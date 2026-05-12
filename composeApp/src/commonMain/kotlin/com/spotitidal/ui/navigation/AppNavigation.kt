package com.spotitidal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spotitidal.ui.settings.SettingsScreen
import com.spotitidal.ui.home.HomeScreen
import com.spotitidal.ui.diff.DiffScreen
import com.spotitidal.ui.sync.SyncScreen

sealed class Screen(val route: String) {
    object Settings  : Screen("settings")
    object Home      : Screen("home")
    object Diff      : Screen("diff/{playlistId}")
    object Sync      : Screen("sync/{playlistId}/{playlistName}")
    object History   : Screen("history")
    object Blacklist : Screen("blacklist")
    object Resolve   : Screen("resolve/{syncResultId}")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
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
    }
}
