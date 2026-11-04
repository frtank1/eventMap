package com.eventmap.video.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.video.data.ktor.VideoRemoteDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class MuxUploadClientTest {
    @Test fun directUploadHasNoEventMapTokenAndProcessingPublicationUsesMediaId() = runTest {
        var externalCalls = 0
        val paths = mutableListOf<String>()
        val upload = HttpClient(MockEngine { request ->
            externalCalls++
            assertEquals("upload.example.invalid", request.url.host)
            assertNull(request.headers[HttpHeaders.Authorization])
            assertEquals(HttpMethod.Put, request.method)
            respond("", HttpStatusCode.OK)
        })
        val api = HttpClient(MockEngine { request ->
            assertEquals("Bearer test-eventmap-token", request.headers[HttpHeaders.Authorization])
            paths += request.url.encodedPath
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            val response = when (request.url.encodedPath) {
                "/media/presign" -> {
                    assertEquals("event-1", body["eventId"]!!.jsonPrimitive.content)
                    assertEquals(3L, body["sizeBytes"]!!.jsonPrimitive.long)
                    """{"mediaId":"mux-test","uploadUrl":"https://upload.example.invalid/file","publicUrl":"","provider":"mux"}"""
                }
                "/media/confirm" -> {
                    assertEquals("mux-test", body["mediaId"]!!.jsonPrimitive.content)
                    """{"id":"mux-test","url":"","status":"processing"}"""
                }
                "/videos" -> {
                    assertEquals("mux-test", body["mediaId"]!!.jsonPrimitive.content)
                    assertEquals("event-1", body["eventId"]!!.jsonPrimitive.content)
                    """{"id":"mux-test","eventId":"event-1","mediaUrl":"","status":"processing"}"""
                }
                else -> error("Unexpected API path")
            }
            respond(response, if (request.url.encodedPath == "/videos") HttpStatusCode.Accepted else HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            defaultRequest { url("https://eventmap.example/"); header(HttpHeaders.Authorization, "Bearer test-eventmap-token") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        }
        try {
            val repo = VideoRepositoryImpl(VideoRemoteDataSource(api, upload))
            val result = repo.publishReel(byteArrayOf(1, 2, 3), "video/mp4", "event-1", "Moment", null)
            assertIs<KtorResponse.Success<*>>(result)
            assertEquals(1, externalCalls)
            assertEquals(listOf("/media/presign", "/media/confirm", "/videos"), paths)
        } finally { api.close(); upload.close() }
    }
}
