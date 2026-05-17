package com.conduit.data.http

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun createHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) { 
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }) 
    }

    install(HttpTimeout) {
        requestTimeoutMillis  = 30_000
        connectTimeoutMillis  = 10_000
        socketTimeoutMillis   = 30_000
    }

    install(HttpCallValidator) {
        validateResponse { response ->
            if (response.status.value == 429) {
                val retryAfter = response.headers["Retry-After"]?.toLongOrNull()?.times(1000) ?: 0L
                throw RateLimitException(retryAfter)
            }
            if (response.status.value >= 500) {
                throw ServerException(response.status.value)
            }
        }
    }
}
