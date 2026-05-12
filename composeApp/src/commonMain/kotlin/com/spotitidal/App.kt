package com.spotitidal

import androidx.compose.runtime.Composable
import com.spotitidal.ui.theme.SpotiTidalTheme
import com.spotitidal.ui.navigation.AppNavigation

@Composable
fun App() {
    SpotiTidalTheme {
        AppNavigation()
    }
}
