package dev.ribod.storestudio.core.http

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import java.io.IOException
import kotlin.random.Random

internal const val DEFAULT_TIMEOUT_MS: Long = 2 * 60 * 1000L
private const val MAX_RETRY_DELAY_MS: Long = 60_000L
private const val CONNECT_TIMEOUT_MS: Long = 30_000L

private val IDEMPOTENT = setOf(HttpMethod.Get, HttpMethod.Put, HttpMethod.Delete)

/** Timeouts plus a small retry for 429/5xx and IO errors; POST/PATCH are never replayed. */
internal fun HttpClientConfig<*>.installTimeoutAndRetry() {
    install(HttpTimeout) {
        requestTimeoutMillis = DEFAULT_TIMEOUT_MS
        socketTimeoutMillis = DEFAULT_TIMEOUT_MS
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
    }
    install(HttpRequestRetry) {
        maxRetries = 3
        // Ktor's own Retry-After handling is uncapped, so a hostile or buggy server could stall the run
        delayMillis { retry ->
            val backoff = (1000L shl (retry - 1)) + Random.nextLong(1000)
            val retryAfter = response?.headers?.get(
                HttpHeaders.RetryAfter,
            )?.toLongOrNull()?.times(1000)
            minOf(maxOf(backoff, retryAfter ?: 0), MAX_RETRY_DELAY_MS)
        }
        retryIf { request, response ->
            request.method in IDEMPOTENT &&
                (response.status.value == 429 || response.status.value >= 500)
        }
        // HttpRequestTimeoutException is an IOException
        retryOnExceptionIf { request, cause ->
            request.method in IDEMPOTENT && cause is IOException
        }
    }
}
