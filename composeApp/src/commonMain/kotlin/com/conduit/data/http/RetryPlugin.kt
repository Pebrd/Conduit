package com.conduit.data.http

import kotlinx.coroutines.delay
import kotlin.random.Random

class RateLimitException(val retryAfterMs: Long) : Exception("Rate limit exceeded. Retry after $retryAfterMs ms")
class ServerException(val statusCode: Int) : Exception("Server error: $statusCode")

suspend fun <T> withRetry(
    maxRetries: Int = 4,
    initialDelayMs: Long = 1_000,
    maxDelayMs: Long = 30_000,
    onRetry: ((attempt: Int, delayMs: Long) -> Unit)? = null,
    block: suspend () -> T,
): T {
    var currentDelay = initialDelayMs
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: RateLimitException) {
            val delay = if (e.retryAfterMs > 0) e.retryAfterMs
                        else (currentDelay + Random.nextLong(0, currentDelay / 2)).coerceAtMost(maxDelayMs)
            onRetry?.invoke(attempt + 1, delay)
            delay(delay)
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
        } catch (e: ServerException) {
            if (attempt == maxRetries - 1) throw e
            val delay = (currentDelay + Random.nextLong(0, currentDelay / 2)).coerceAtMost(maxDelayMs)
            onRetry?.invoke(attempt + 1, delay)
            delay(delay)
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
        } catch (e: Exception) {
            // Re-throw other exceptions immediately
            throw e
        }
    }
    throw Exception("Max retries exceeded")
}
