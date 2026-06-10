package com.eventmap.auth.data.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

// Все запросы — JSON. baseUrl приходит из CoreConfig (DefaultRequest в KtorModule).
class AuthRemoteDataSource(
    private val httpClient: HttpClient,
) {
    suspend fun login(body: LoginRequest): HttpResponse =
        httpClient.post("auth/login") { json(body) }

    suspend fun register(body: RegisterRequest): HttpResponse =
        httpClient.post("auth/register") { json(body) }

    suspend fun forgotPassword(body: ForgotRequest): HttpResponse =
        httpClient.post("auth/password/forgot") { json(body) }

    suspend fun resetPassword(body: ResetRequest): HttpResponse =
        httpClient.post("auth/password/reset") { json(body) }

    suspend fun saveInterests(body: InterestsRequest): HttpResponse =
        httpClient.post("auth/interests") { json(body) }
}

private inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.json(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
