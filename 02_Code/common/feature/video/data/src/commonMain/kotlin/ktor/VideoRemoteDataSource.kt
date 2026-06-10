package com.eventmap.video.data.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse

class VideoRemoteDataSource(
    private val httpClient: HttpClient,
) {
    suspend fun now(): HttpResponse = httpClient.get("videos/now")
    suspend fun react(videoId: String): HttpResponse = httpClient.post("videos/$videoId/react")
}
