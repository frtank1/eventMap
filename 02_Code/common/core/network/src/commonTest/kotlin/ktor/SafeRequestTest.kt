package com.eventmap.core.network.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Serializable
private data class Dummy(val id: Int)

class SafeRequestTest {

    private fun client(status: HttpStatusCode, body: String): HttpClient =
        HttpClient(
            MockEngine {
                respond(
                    content = body,
                    status = status,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    @Test
    fun success200_returnsParsedBody() = runTest {
        val c = client(HttpStatusCode.OK, """{"id":42}""")
        val result = safeRequest<Dummy> { c.get("/") }

        assertIs<KtorResponse.Success<Dummy>>(result)
        assertEquals(42, result.data.id)
    }

    @Test
    fun http404_returnsFailureWithStatus() = runTest {
        val c = client(HttpStatusCode.NotFound, "")
        val result = safeRequest<Dummy> { c.get("/") }

        assertIs<KtorResponse.Failure>(result)
        assertEquals(404, result.error.status)
    }

    @Test
    fun thrownException_isMappedToFailure() = runTest {
        val result = safeRequest<Dummy> { throw RuntimeException("boom") }
        assertIs<KtorResponse.Failure>(result)
    }
}
