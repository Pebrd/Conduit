package com.conduit

import androidx.compose.runtime.Composable
import com.conduit.ui.theme.ConduitTheme
import com.conduit.ui.navigation.AppNavigation

@Composable
fun App() {
    ConduitTheme {
        AppNavigation()
    }
}
