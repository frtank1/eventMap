package com.eventmap.server

import com.eventmap.server.presentation.routes.worldRoutes
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.install
import io.ktor.server.application.call
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import java.security.MessageDigest
import kotlin.test.*

class WorldRoutesTest {
    @Test fun publicAssetsHaveExactHashesAndNarrowCorsPolicy() = testApplication {
        application { routing {
            worldRoutes()
            route("/") { install(CORS) { allowHost("trusted.example") }; get("/private-test") { call.respondText("private") } }
        } }
        for(origin in listOf("null","https://appassets.androidplatform.net")) {
            val response=client.get("/world/manifest.json") { header(HttpHeaders.Origin,origin) }
            assertEquals(HttpStatusCode.OK,response.status)
            assertEquals("*",response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("no-cache",response.headers[HttpHeaders.CacheControl])
            val manifest=Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(1,manifest["engineVersion"]!!.jsonPrimitive.int)
            for(item in manifest["models"]!!.jsonArray) {
                val m=item.jsonObject
                val asset=client.get("/world/${m["file"]!!.jsonPrimitive.content}")
                assertEquals(HttpStatusCode.OK,asset.status)
                assertTrue(asset.headers[HttpHeaders.CacheControl]!!.contains("immutable"))
                val bytes=asset.bodyAsText().toByteArray(Charsets.UTF_8)
                assertEquals(m["bytes"]!!.jsonPrimitive.int,bytes.size)
                assertEquals(m["sha256"]!!.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            }
        }
        assertEquals(HttpStatusCode.Forbidden,client.get("/private-test") { header(HttpHeaders.Origin,"https://appassets.androidplatform.net") }.status)
        assertEquals(HttpStatusCode.OK,client.get("/private-test") { header(HttpHeaders.Origin,"http://trusted.example") }.status)
        for(path in listOf("/world/assets/secret.json","/world/assets/..%2Fapplication.conf","/world/mobile.js","/world/releases/pilot-2.json"))
            assertEquals(HttpStatusCode.NotFound,client.get(path).status)
    }
}
