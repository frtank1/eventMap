package com.eventmap.event.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.models.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** Real mobile reader + real Ktor editor + isolated persistent test DB. Never targets a remote server. */
class VenueAdminIntegrationTest {
    @Test fun adminPublishesNewFloorsForUnchangedMobileClient(): Unit = runBlocking {
        val base=System.getenv("EVENTMAP_VENUE_ADMIN_TEST_URL")
        assumeTrue("Start backend venueAdminPreview and set EVENTMAP_VENUE_ADMIN_TEST_URL",base!=null)
        require(base=="http://127.0.0.1:4891/")
        val client=HttpClient(OkHttp){defaultRequest { url(base!!) }}
        try {
            val repo=VenueRepositoryImpl(client)
            // Reserved synthetic ID is written only to the isolated local harness.
            val id="osm:way:999999991"
            val json=Json{encodeDefaults=true}
            var version=Json.parseToJsonElement(client.get("admin/api/venues/$id"){header("X-Admin-Key","local-venue-test-only")}.bodyAsText()).jsonObject.getValue("version").jsonPrimitive.long
            val initial=assertIs<KtorResponse.Success<VenueBuilding>>(repo.getBuilding(id)).data
            val first=VenueBuilding(buildingId=id,revision="draft",name="Synthetic integration test",
                floors=initial.floors.ifEmpty{listOf(VenueFloor("l1","1 этаж",1))})
            suspend fun save(plan:VenueBuilding) {
                val response=client.put("admin/api/venues/$id/draft") {
                    header("X-Admin-Key","local-venue-test-only");contentType(ContentType.Application.Json)
                    setBody("""{"expectedVersion":$version,"building":${json.encodeToString(plan)}}""")
                }
                assertEquals(HttpStatusCode.OK,response.status)
                version=Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("version").jsonPrimitive.long
            }
            suspend fun publish() {
                val response=client.post("admin/api/venues/$id/publications") {
                    header("X-Admin-Key","local-venue-test-only");contentType(ContentType.Application.Json);setBody("""{"expectedVersion":$version}""")
                }
                assertEquals(HttpStatusCode.OK,response.status)
                version=Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("version").jsonPrimitive.long
            }
            save(first)
            assertEquals(initial,assertIs<KtorResponse.Success<VenueBuilding>>(repo.getBuilding(id)).data)
            publish()
            val published=assertIs<KtorResponse.Success<VenueBuilding>>(repo.getBuilding(id)).data
            assertEquals(first.floors,published.floors)
            val second=first.copy(name="Updated without server restart",floors=first.floors.map{it.copy(name=it.name+" · обновлён")} +
                if(first.floors.none{it.id=="l2"})listOf(VenueFloor("l2","2 этаж",2,rooms=listOf(VenueRoom("cafe","Кафе"))))else emptyList())
            save(second);assertEquals(published,assertIs<KtorResponse.Success<VenueBuilding>>(repo.getBuilding(id)).data)
            publish()
            val updated=assertIs<KtorResponse.Success<VenueBuilding>>(repo.getBuilding(id)).data
            assertEquals(second.floors,updated.floors);assertNotEquals(published.revision,updated.revision)
            assertTrue(updated.accepts(EventVenue(id,"l2","cafe")))
            Unit
        } finally { client.close() }
    }
}
