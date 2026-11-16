package com.eventmap.core.network.ktor

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

/** A caller-owned CDN client: no account bearer, default API URL or signed-URL logging. */
fun createMediaHttpClient(allowRedirects: Boolean = true): HttpClient = HttpClient(HttpEngineFactory().createEngine()) {
    followRedirects = allowRedirects
    install(HttpTimeout) { connectTimeoutMillis = 10_000; requestTimeoutMillis = 15_000 }
}
