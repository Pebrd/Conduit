package com.conduit.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Desktop
import java.net.URI

actual class AudioPreviewPlayer actual constructor() {
    private val _isPlaying = MutableStateFlow(false)

    actual val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    actual fun play(url: String) {
        stop()
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                _isPlaying.value = true
            }
        } catch (_: Exception) {
            _isPlaying.value = false
        }
    }

    actual fun pause() {
        _isPlaying.value = false
    }

    actual fun stop() {
        _isPlaying.value = false
    }
}
