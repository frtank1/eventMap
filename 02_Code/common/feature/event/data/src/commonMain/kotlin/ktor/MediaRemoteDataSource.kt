package com.eventmap.event.data.ktor

import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/**
 * Загрузка медиа по §8 storage.
 *
 * @param apiClient основной клиент (с bearer) — presign/confirm/videos.
 * @param uploadClient «голый» клиент (БЕЗ Auth) — прямой PUT файла в R2.
 *        Bearer на presigned-URL слать нельзя: он не входит в подпись SigV4 и ломает запрос.
 */
class MediaRemoteDataSource(
    private val apiClient: HttpClient,
    private val uploadClient: HttpClient,
) {
    suspend fun presign(body: PresignRequest): HttpResponse =
        apiClient.post("media/presign") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun confirm(body: ConfirmMediaRequest): HttpResponse =
        apiClient.post("media/confirm") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun createVideo(body: CreateVideoRequest): HttpResponse =
        apiClient.post("videos") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    /** Прямой PUT байтов в object storage по presigned-URL (абсолютный URL, без bearer). */
    suspend fun putFile(
        uploadUrl: String,
        bytes: ByteArray,
        contentType: String,
        onProgress: ((Float) -> Unit)? = null,
    ): HttpResponse =
        uploadClient.put(uploadUrl) {
            header(HttpHeaders.ContentType, contentType)
            setBody(bytes)
            onUpload { sent, total ->
                val length = total ?: bytes.size.toLong()
                if (length > 0) onProgress?.invoke((sent.toFloat() / length).coerceIn(0f, 1f))
            }
        }
}
