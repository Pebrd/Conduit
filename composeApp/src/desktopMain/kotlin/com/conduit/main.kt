package com.conduit

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import org.koin.core.context.startKoin
import com.conduit.di.appModule

fun main() {
    startKoin { modules(appModule, com.conduit.platform.platformModule) }
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
