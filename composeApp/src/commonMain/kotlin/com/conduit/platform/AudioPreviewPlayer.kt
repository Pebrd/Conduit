package com.conduit.platform

import kotlinx.coroutines.flow.StateFlow

expect class AudioPreviewPlayer() {
    fun play(url: String)
    fun pause()
    fun stop()
    val isPlaying: StateFlow<Boolean>
}
