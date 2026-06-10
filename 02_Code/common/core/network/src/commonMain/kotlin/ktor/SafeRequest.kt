package com.eventmap.core.network.ktor

import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

suspend inline fun <reified T> safeRequest(
    crossinline call: suspend () -> HttpResponse
): KtorResponse<T> {
    return try {
        val response = call()
        if (response.status.isSuccess()) {
            KtorResponse.success(response)
        } else {
            KtorResponse.failure(response)
        }
    } catch (t: Throwable) {
        KtorResponse.failure(t)
    }
}