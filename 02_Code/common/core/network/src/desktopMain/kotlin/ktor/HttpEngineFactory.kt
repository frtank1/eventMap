package com.eventmap.core.network.ktor

import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*

internal actual class HttpEngineFactory actual constructor() {
    actual fun createEngine(): HttpClientEngineFactory<HttpClientEngineConfig> = OkHttp
}