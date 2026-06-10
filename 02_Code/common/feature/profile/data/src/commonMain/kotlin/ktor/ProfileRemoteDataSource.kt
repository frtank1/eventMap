package com.eventmap.profile.data.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class UpdateMeRequest(val displayName: String, val bio: String)

class ProfileRemoteDataSource(
    private val httpClient: HttpClient,
) {
    suspend fun getMe(): HttpResponse = httpClient.get("users/me")
    suspend fun getHistory(userId: String): HttpResponse = httpClient.get("users/$userId/history")
    suspend fun updateMe(body: UpdateMeRequest): HttpResponse =
        httpClient.patch("users/me") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
}
