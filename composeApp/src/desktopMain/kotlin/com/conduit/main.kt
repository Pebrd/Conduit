package com.conduit

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext
import com.conduit.di.appModule
import com.conduit.data.local.SettingsStorage
import com.conduit.data.local.TokenStorage

fun main() {
    startKoin { modules(appModule, com.conduit.platform.platformModule) }

    val settingsStorage: SettingsStorage = GlobalContext.get().get()
    val tokenStorage: TokenStorage = GlobalContext.get().get()
    val SCOPES_VERSION = 3
    if (settingsStorage.getScopesVersion() < SCOPES_VERSION) {
        tokenStorage.clearTokens("spotify")
        tokenStorage.clearTokens("tidal")
        settingsStorage.saveScopesVersion(SCOPES_VERSION)
    }

    application {
        val windowState = rememberWindowState(width = 900.dp, height = 700.dp)
        val icon = painterResource("conduit_logo.png")
        Window(
            onCloseRequest = ::exitApplication,
            title          = "Conduit",
            icon           = icon,
            state          = windowState,
        ) {
            App()
        }
    }
}
