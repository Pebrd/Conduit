package com.conduit.scheduler

import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.hours

class SyncScheduler {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(intervalHours: Long = 24, onSync: suspend () -> Unit) {
        scope.launch {
            while (true) {
                delay(intervalHours.hours)
                runCatching { onSync() }
                    .onSuccess  { println("[Conduit] Sync completado") }
                    .onFailure  { println("[Conduit] Error en sync: ${it.message}") }
            }
        }
    }

    fun stop() = scope.cancel()
}
