package com.spotitidal.scheduler

import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.hours

class SyncScheduler {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(intervalHours: Long = 24, onSync: suspend () -> Unit) {
        scope.launch {
            while (true) {
                delay(intervalHours.hours)
                runCatching { onSync() }
                    .onSuccess  { println("[SpotiTidal] Sync completado") }
                    .onFailure  { println("[SpotiTidal] Error en sync: ${it.message}") }
            }
        }
    }

    fun stop() = scope.cancel()
}
