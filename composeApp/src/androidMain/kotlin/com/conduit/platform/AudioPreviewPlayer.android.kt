package com.conduit.platform

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class AudioPreviewPlayer actual constructor() {
    private var mediaPlayer: MediaPlayer? = null
    private val _isPlaying = MutableStateFlow(false)

    actual val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    actual fun play(url: String) {
        stop()
        MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener { mp ->
                mp.start()
                _isPlaying.value = true
            }
            setOnCompletionListener {
                _isPlaying.value = false
            }
            setOnErrorListener { _, _, _ ->
                _isPlaying.value = false
                true
            }
            prepareAsync()
            mediaPlayer = this
        }
    }

    actual fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
            }
        }
    }

    actual fun stop() {
        mediaPlayer?.let {
            try {
                it.stop()
                it.release()
            } catch (_: Exception) {}
            mediaPlayer = null
            _isPlaying.value = false
        }
    }
}
