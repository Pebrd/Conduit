package com.conduit.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.net.URI
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent
import javax.sound.sampled.UnsupportedAudioFileException

actual class AudioPreviewPlayer actual constructor() {
    private var clip: Clip? = null
    private var process: java.lang.Process? = null
    private val _isPlaying = MutableStateFlow(false)

    actual val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    actual fun play(url: String) {
        stop()
        // Try native Java sound first (WAV/AU/AIFF), fall back to ffplay for AAC/M4A
        if (!tryPlayJavaSound(url)) {
            tryPlayFfplay(url)
        }
    }

    private fun tryPlayJavaSound(url: String): Boolean {
        return try {
            val urlObj = URI(url).toURL()
            val audioStream = AudioSystem.getAudioInputStream(BufferedInputStream(urlObj.openStream()))
            val newClip = AudioSystem.getClip()
            newClip.open(audioStream)
            newClip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) {
                    _isPlaying.value = false
                    newClip.close()
                }
            }
            newClip.start()
            clip = newClip
            _isPlaying.value = true
            true
        } catch (_: UnsupportedAudioFileException) {
            false // format not supported, try ffplay
        } catch (_: Exception) {
            false
        }
    }

    private fun tryPlayFfplay(url: String) {
        try {
            val pb = ProcessBuilder("ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet", url)
                .redirectErrorStream(true)
            val p = pb.start()
            process = p
            _isPlaying.value = true
            // Monitor process completion
            Thread {
                try {
                    p.waitFor()
                } catch (_: InterruptedException) {}
                _isPlaying.value = false
                process = null
            }.apply { isDaemon = true }.start()
        } catch (_: Exception) {
            _isPlaying.value = false
        }
    }

    actual fun pause() {
        clip?.let {
            if (it.isRunning) {
                it.stop()
                _isPlaying.value = false
            }
        }
        process?.let {
            if (it.isAlive()) {
                it.destroy()
                _isPlaying.value = false
            }
        }
    }

    actual fun stop() {
        clip?.let {
            try {
                it.stop()
                it.close()
            } catch (_: Exception) {}
            clip = null
        }
        process?.let {
            if (it.isAlive()) {
                it.destroyForcibly()
            }
            process = null
        }
        _isPlaying.value = false
    }
}
