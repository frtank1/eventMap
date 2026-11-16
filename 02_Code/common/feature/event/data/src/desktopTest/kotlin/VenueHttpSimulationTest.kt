package com.eventmap.event.data

import com.eventmap.core.network.ktor.KtorResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** Opt-in integration test. Real Ktor transport/repository; only the server is simulated. */
class VenueHttpSimulationTest {
    @Test fun serverAddsFloorsWithoutClientUpdateAndFailuresAreNotEmptyPlans(): Unit = runBlocking {
        val base=System.getenv("EVENTMAP_VENUE_SIM_URL")
        assumeTrue("Start local Postman venues simulation and set EVENTMAP_VENUE_SIM_URL",base!=null)
        require(base!!.startsWith("http://127.0.0.1:"))
        val client=HttpClient(OkHttp){defaultRequest { url(base) };install(ContentNegotiation){json(Json{ignoreUnknownKeys=true})}}
        try {
            val repo=VenueRepositoryImpl(client)
            suspend fun mode(value:String) { assertEquals(HttpStatusCode.OK,client.post("__control"){contentType(ContentType.Application.Json);setBody("""{"mode":"$value"}""")}.status) }
            mode("empty");val initial=assertIs<KtorResponse.Success<*>>(repo.getBuilding("landmark:mega")).data as com.eventmap.event.models.VenueBuilding
            assertTrue(initial.floors.isEmpty())
            mode("v1");val first=assertIs<KtorResponse.Success<*>>(repo.getBuilding("landmark:mega")).data as com.eventmap.event.models.VenueBuilding
            assertEquals(listOf("l1"),first.floors.map{it.id})
            mode("v2");val second=assertIs<KtorResponse.Success<*>>(repo.getBuilding("landmark:mega")).data as com.eventmap.event.models.VenueBuilding
            assertEquals(listOf("l1","l2"),second.floors.map{it.id});assertEquals(first.buildingId,second.buildingId)
            val events=assertIs<KtorResponse.Success<*>>(repo.getEvents("landmark:mega")).data as List<*>
            assertEquals(listOf("inside","building-only"),events.map{(it as com.eventmap.event.models.EventDetail).id})
            for(failure in listOf("offline","bad","oversize","incompatible","slow")){
                mode(failure);assertIs<KtorResponse.Failure>(repo.getBuilding("landmark:mega"),failure)
            }
            mode("v1");assertIs<KtorResponse.Success<*>>(repo.getBuilding("landmark:mega"))
            assertIs<KtorResponse.Failure>(repo.getBuilding("../../bad"))
            Unit
        } finally { client.close() }
    }
}
