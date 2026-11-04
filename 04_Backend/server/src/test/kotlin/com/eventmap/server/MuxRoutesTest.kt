package com.eventmap.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.eventmap.server.config.AppConfig
import com.eventmap.server.data.repository.*
import com.eventmap.server.data.video.*
import com.eventmap.server.domain.repository.*
import com.eventmap.server.domain.usecase.*
import com.eventmap.server.presentation.routes.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import com.eventmap.server.data.db.MuxWebhookEventsTable
import kotlin.test.*

class MuxRoutesTest {
    private fun app(config: AppConfig = muxTestConfig(), block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        createMuxTestDatabase()
        application {
            install(ContentNegotiation) { json() }
            install(Authentication) { jwt("auth-jwt") {
                verifier(JWT.require(Algorithm.HMAC256("test-only")).build())
                validate { JWTPrincipal(it.payload) }
            } }
            val media = MediaRepositoryImpl()
            val videos = VideoRepositoryImpl()
            val actual = MuxGatewayImpl(config)
            val gateway = object : MuxGateway by actual {
                override suspend fun createUpload(mediaId: String) = MuxUpload("up-$mediaId", "https://upload.example.invalid/$mediaId")
            }
            val service = MuxVideoService(config, gateway, MuxUploadRepositoryImpl(), media, MediaAccessImpl())
            val storage = object : StoragePort {
                override val configured = true
                override fun presignPut(key: String, contentType: String) = "https://storage.example.invalid/$key"
                override fun publicUrl(key: String) = "https://cdn.example.invalid/$key"
            }
            val blobs = object : MediaBlobRepository {
                override suspend fun save(key: String, contentType: String, bytes: ByteArray) = Unit
                override suspend fun get(key: String): Pair<String, ByteArray>? = null
            }
            routing {
                mediaRoutes(PresignUploadUseCase(storage), ConfirmMediaUseCase(media), GetEventMediaUseCase(media), blobs, config, service)
                videoRoutes(GetVideoFeedUseCase(videos), ReactVideoUseCase(videos), CreateVideoUseCase(videos, EventRepositoryImpl()), service)
                muxRoutes(service)
            }
        }
        block()
    }

    private fun HttpRequestBuilder.asUser(user: String = "owner") {
        bearerAuth(JWT.create().withClaim("userId", user).sign(Algorithm.HMAC256("test-only")))
        contentType(ContentType.Application.Json)
    }

    @Test fun uploadConfirmPublishWebhookAndPlayback() = app {
        val begin = client.post("/media/presign") {
            asUser(); setBody("""{"kind":"video","contentType":"video/mp4","eventId":"public","sizeBytes":1000}""")
        }
        assertEquals(HttpStatusCode.OK, begin.status)
        val presign = Json.parseToJsonElement(begin.bodyAsText()).jsonObject
        assertEquals("", presign["publicUrl"]!!.jsonPrimitive.content)
        val id = presign["mediaId"]!!.jsonPrimitive.content
        val confirm = client.post("/media/confirm") {
            asUser(); setBody("""{"mediaId":"$id","url":"https://attacker.invalid/fake.mp4","kind":"video","contentType":"video/mp4","eventId":"public"}""")
        }
        assertEquals(HttpStatusCode.OK, confirm.status)
        assertTrue(confirm.bodyAsText().contains("processing"))
        assertFalse(confirm.bodyAsText().contains("attacker"))
        repeat(2) {
            val published = client.post("/videos") { asUser(); setBody("""{"mediaId":"$id","eventId":"public","caption":"Moment"}""") }
            assertEquals(HttpStatusCode.Accepted, published.status)
        }
        assertEquals("[]", client.get("/videos/now").bodyAsText())
        val raw = muxReady("ready-event", id)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/webhooks/mux") { setBody(raw) }.status)
        repeat(2) {
            assertEquals(HttpStatusCode.NoContent, client.post("/webhooks/mux") {
                header("mux-signature", muxSignature(raw)); setBody(raw)
            }.status)
        }
        val feed = Json.parseToJsonElement(client.get("/videos/now").bodyAsText()).jsonArray
        assertEquals(1, feed.size)
        assertTrue(feed[0].jsonObject["mediaUrl"]!!.jsonPrimitive.content.startsWith("https://stream.mux.com/"))
        assertTrue(feed[0].jsonObject["thumb"]!!.jsonPrimitive.content.contains("token="))
        assertEquals("private, no-store", client.get("/media/$id").headers[HttpHeaders.CacheControl])
        transaction { assertEquals(1L, MuxWebhookEventsTable.selectAll().count()) }
    }

    @Test fun privateEventAndOtherUsersUploadAreForbidden() = app {
        val denied = client.post("/media/presign") { asUser("outsider")
            setBody("""{"kind":"video","contentType":"video/mp4","eventId":"private","sizeBytes":10}""") }
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/events/private/media").status)
        val allowed = client.post("/media/presign") { asUser()
            setBody("""{"kind":"video","contentType":"video/mp4","eventId":"public","sizeBytes":10}""") }
        val id = Json.parseToJsonElement(allowed.bodyAsText()).jsonObject["mediaId"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.Forbidden, client.post("/media/confirm") { asUser("outsider")
            setBody("""{"mediaId":"$id","url":"","kind":"video","contentType":"video/mp4","eventId":"public"}""") }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/videos") { asUser("outsider")
            setBody("""{"mediaId":"$id","eventId":"public"}""") }.status)
    }

    @Test fun incompleteConfigurationDoesNotFallBackToPublicStorage() = app(muxTestConfig().copy(muxTokenSecret = "")) {
        assertEquals(HttpStatusCode.ServiceUnavailable, client.post("/media/presign") { asUser()
            setBody("""{"kind":"video","contentType":"video/mp4","eventId":"public","sizeBytes":10}""") }.status)
        assertEquals(HttpStatusCode.OK, client.post("/media/presign") { asUser()
            setBody("""{"kind":"image","contentType":"image/jpeg"}""") }.status)
    }

    @Test fun anonymousUploadMissingEventAndOversizedWebhookAreRejected() = app {
        assertEquals(HttpStatusCode.Unauthorized, client.post("/media/presign") {
            contentType(ContentType.Application.Json); setBody("""{"kind":"video","contentType":"video/mp4"}""") }.status)
        assertEquals(HttpStatusCode.UnprocessableEntity, client.post("/media/presign") { asUser()
            setBody("""{"kind":"video","contentType":"video/mp4","sizeBytes":10}""") }.status)
        assertEquals(HttpStatusCode.PayloadTooLarge, client.post("/webhooks/mux") { setBody(ByteArray(256 * 1024 + 1)) }.status)
        assertEquals(HttpStatusCode.UnprocessableEntity, client.post("/videos") { asUser()
            setBody("""{"mediaUrl":"https://attacker.invalid/fake.mp4","eventId":"public"}""") }.status)
    }
}
