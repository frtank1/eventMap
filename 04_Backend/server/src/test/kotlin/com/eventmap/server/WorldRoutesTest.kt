package com.eventmap.server

import com.eventmap.server.presentation.routes.worldRoutes
import io.ktor.client.call.body
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
    @Test fun geographyClasspathPackHasExactHashesDataShapesAndPublicCachePolicy() = testApplication {
        // Mount only the public routes: no Application.module, Koin or database.
        application { routing { worldRoutes() } }
        val loader=WorldRoutesTest::class.java.classLoader
        for(origin in listOf("null","https://appassets.androidplatform.net")) {
            val response=client.get("/world/places-manifest.json") { header(HttpHeaders.Origin,origin) }
            assertEquals(HttpStatusCode.OK,response.status)
            assertEquals(ContentType.Application.Json,response.contentType()?.withoutParameters())
            assertEquals("*",response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertNull(response.headers[HttpHeaders.AccessControlAllowCredentials])
            assertEquals("nosniff",response.headers["X-Content-Type-Options"])
            assertEquals("no-cache",response.headers[HttpHeaders.CacheControl])
            val manifestBytes=response.body<ByteArray>()
            assertContentEquals(loader.getResourceAsStream("world/places-manifest.json")!!.use { it.readBytes() },manifestBytes)
            val manifest=Json.parseToJsonElement(manifestBytes.toString(Charsets.UTF_8)).jsonObject
            assertEquals(setOf("schemaVersion","catalogVersion","release","addresses","places"),manifest.keys)
            assertEquals(1,manifest.getValue("schemaVersion").jsonPrimitive.int)
            assertEquals(1,manifest.getValue("catalogVersion").jsonPrimitive.int)
            assertTrue(Regex("[a-zA-Z0-9._-]{1,64}").matches(manifest.getValue("release").jsonPrimitive.content))
            for((kind,limit) in listOf("addresses" to 12*1024*1024,"places" to 1024*1024)) {
                val descriptor=manifest.getValue(kind).jsonObject
                assertEquals(setOf("file","sha256","bytes"),descriptor.keys)
                val sha256=descriptor.getValue("sha256").jsonPrimitive.content
                assertTrue(Regex("[a-f0-9]{64}").matches(sha256))
                val file=descriptor.getValue("file").jsonPrimitive.content
                assertEquals("assets/$sha256.json",file)
                assertTrue(descriptor.getValue("bytes").jsonPrimitive.int in 1..limit)
                val asset=client.get("/world/$file") { header(HttpHeaders.Origin,origin) }
                assertEquals(HttpStatusCode.OK,asset.status)
                assertEquals(ContentType.Application.Json,asset.contentType()?.withoutParameters())
                assertEquals("*",asset.headers[HttpHeaders.AccessControlAllowOrigin])
                assertNull(asset.headers[HttpHeaders.AccessControlAllowCredentials])
                assertEquals("nosniff",asset.headers["X-Content-Type-Options"])
                assertEquals("public, max-age=31536000, immutable",asset.headers[HttpHeaders.CacheControl])
                val bytes=asset.body<ByteArray>()
                assertContentEquals(loader.getResourceAsStream("world/$file")!!.use { it.readBytes() },bytes)
                assertEquals(descriptor.getValue("bytes").jsonPrimitive.int,bytes.size)
                assertEquals(sha256,MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                assertGeographyShape(kind,Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject)
            }
        }
        for(path in listOf("/world/places.json","/world/addresses.json","/world/places-manifest.js",
            "/world/assets/${"0".repeat(64)}.json","/world/assets/${"a".repeat(64)}.js"))
            assertEquals(HttpStatusCode.NotFound,client.get(path).status)
    }

    private fun assertGeographyShape(kind:String,data:JsonObject) {
        assertEquals(1,data.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals("OpenStreetMap",data.getValue("source").jsonPrimitive.content)
        assertEquals("ODbL-1.0",data.getValue("license").jsonPrimitive.content)
        assertTrue(data.getValue("timestamp").jsonPrimitive.content.isNotBlank())
        val ref=Regex("(node|way|relation)/[0-9]{1,20}")
        if(kind=="addresses") {
            val records=data.getValue("records").jsonArray
            assertTrue(records.size in 1..100000)
            val ids=mutableSetOf<String>()
            for(item in records) {
                val row=item.jsonArray
                assertEquals(4,row.size)
                val id=row[0].jsonPrimitive.content
                assertTrue(ref.matches(id)&&ids.add(id),"Invalid or duplicate address reference: $id")
                val bounds=row[1].jsonArray
                assertEquals(4,bounds.size)
                assertCoordinate(bounds[0].jsonPrimitive.double,bounds[1].jsonPrimitive.double)
                assertCoordinate(bounds[2].jsonPrimitive.double,bounds[3].jsonPrimitive.double)
                assertTrue(bounds[0].jsonPrimitive.double<=bounds[2].jsonPrimitive.double)
                assertTrue(bounds[1].jsonPrimitive.double<=bounds[3].jsonPrimitive.double)
                assertTrue(row[2].jsonPrimitive.isString&&row[2].jsonPrimitive.content.length in 1..202)
                assertTrue(row[3].jsonPrimitive.isString&&row[3].jsonPrimitive.content.length<=120)
            }
        } else {
            val places=data.getValue("places").jsonArray
            assertTrue(places.size in 1..128)
            val ids=mutableSetOf<String>();var vertices=0
            for(item in places) {
                val place=item.jsonObject
                val id=place.getValue("id").jsonPrimitive.content
                assertTrue(Regex("[a-z][a-z0-9-]{0,63}").matches(id)&&ids.add(id))
                val refs=place.getValue("sourceRefs").jsonArray.map { it.jsonPrimitive.content }
                assertTrue(refs.size<=40&&refs.distinct().size==refs.size&&refs.all { ref.matches(it) })
                val footprints=place.getValue("footprints").jsonArray
                assertTrue(footprints.size<=40)
                for(footprint in footprints) {
                    val f=footprint.jsonObject
                    assertTrue(f.getValue("sourceRef").jsonPrimitive.content in refs)
                    val rings=f.getValue("rings").jsonArray
                    assertTrue(rings.size in 1..32)
                    var count=0
                    for(ring in rings) {
                        assertTrue(ring.jsonArray.size>=3);count+=ring.jsonArray.size
                        for(point in ring.jsonArray) {
                            assertEquals(2,point.jsonArray.size)
                            assertCoordinate(point.jsonArray[0].jsonPrimitive.double,point.jsonArray[1].jsonPrimitive.double)
                        }
                    }
                    assertTrue(count<=512);vertices+=count
                }
                val entrances=place.getValue("entrances").jsonArray
                assertTrue(entrances.size<=64)
                val entranceIds=mutableSetOf<String>()
                for(entrance in entrances) {
                    val e=entrance.jsonObject;val entranceId=e.getValue("id").jsonPrimitive.content
                    assertTrue(Regex("node/[0-9]{1,20}").matches(entranceId)&&entranceIds.add(entranceId))
                    assertCoordinate(e.getValue("lng").jsonPrimitive.double,e.getValue("lat").jsonPrimitive.double)
                    assertTrue(e.getValue("kind").jsonPrimitive.content in listOf("main","yes","service","exit","emergency","staircase","home","unknown"))
                    assertTrue(e.getValue("access").jsonPrimitive.content in listOf("yes","private","customers","unknown","no","permissive"))
                    assertTrue(e.getValue("wheelchair").jsonPrimitive.content in listOf("yes","no","limited","unknown"))
                }
                val review=place.getValue("review").jsonObject
                assertTrue(review.getValue("status").jsonPrimitive.content in listOf("matched","needs_review","not_found"))
                assertTrue(review.getValue("notes").jsonPrimitive.isString&&review.getValue("notes").jsonPrimitive.content.length<=1000)
                val urls=review.getValue("sourceUrls").jsonArray
                assertTrue(urls.size<=12&&urls.all { it.jsonPrimitive.content.startsWith("https://")&&it.jsonPrimitive.content.length<=500 })
            }
            assertTrue(vertices<=24000)
        }
    }

    private fun assertCoordinate(lng:Double,lat:Double) {
        assertTrue(lng.isFinite()&&lat.isFinite()&&lng in 71.10..71.85&&lat in 50.95..51.40)
    }

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
            assertEquals(2,manifest["engineVersion"]!!.jsonPrimitive.int)
            assertEquals(19,manifest["models"]!!.jsonArray.size)
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
