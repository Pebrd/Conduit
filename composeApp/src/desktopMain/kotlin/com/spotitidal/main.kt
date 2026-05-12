package com.spotitidal

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import org.koin.core.context.startKoin
import com.spotitidal.di.appModule

fun main() {
    startKoin { modules(appModule, com.spotitidal.platform.platformModule) }
    application {
        val windowState = rememberWindowState(width = 900.dp, height = 700.dp)
        Window(
            onCloseRequest = ::exitApplication,
            title          = "SpotiTidal",
            state          = windowState,
        ) {
            App()
        }
    }
}
