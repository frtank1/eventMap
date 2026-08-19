package com.eventmap.video.data.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

class VideoRemoteDataSource(
    private val httpClient: HttpClient,
) {
    suspend fun now(): HttpResponse = httpClient.get("videos/now")
    suspend fun react(videoId: String): HttpResponse = httpClient.post("videos/$videoId/react")

    // ── Загрузка медиа + публикация рилса (§8 storage) ──

    suspend fun presign(body: PresignRequest): HttpResponse =
        httpClient.post("media/presign") { json(body) }

    /** Прямая загрузка файла в object storage по presigned-URL (абсолютный URL). */
    suspend fun uploadBytes(uploadUrl: String, bytes: ByteArray, contentType: String): HttpResponse =
        httpClient.put(uploadUrl) {
            header(HttpHeaders.ContentType, contentType)
            setBody(bytes)
        }

    suspend fun confirmMedia(body: ConfirmMediaRequest): HttpResponse =
        httpClient.post("media/confirm") { json(body) }

    suspend fun createVideo(body: CreateVideoRequest): HttpResponse =
        httpClient.post("videos") { json(body) }
}

private inline fun <reified T> HttpRequestBuilder.json(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
