package com.eventmap.server

import com.eventmap.server.data.db.*
import com.eventmap.server.data.repository.EventRepositoryImpl
import com.eventmap.server.data.venue.JsonVenueCatalog
import com.eventmap.server.domain.model.*
import com.eventmap.server.domain.usecase.*
import com.eventmap.server.presentation.dto.*
import com.eventmap.server.presentation.routes.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.*

class VenuesTest {
    private val polygon=listOf(PlanPoint(0.0,0.0),PlanPoint(10.0,0.0),PlanPoint(10.0,10.0),PlanPoint(0.0,10.0))
    private val building=VenueBuilding(buildingId="landmark:mega",revision="test-1",name="Synthetic test building",
        floors=listOf(VenueFloor("l1","Этаж 1",1,VenuePlan(100.0,80.0),listOf(VenueRoom("atrium","Атриум",polygon))),
            VenueFloor("l2","Этаж 2",2,rooms=listOf(VenueRoom("shop","Магазин")))))
    private fun catalog(b:VenueBuilding=building)=JsonVenueCatalog("""{"schemaVersion":1,"buildings":[${Json.encodeToString(b)}]}""")
    private fun database(): Database {
        val db=Database.connect("jdbc:h2:mem:venues-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",driver="org.h2.Driver")
        transaction(db) { SchemaUtils.create(EventsTable,OrganizersTable,AttendanceTable,EventCommentsTable) }
        return db
    }
    private fun draft(venue:EventVenue?=null)=Event("","Meet",category="Спорт",startAt="2030-06-01T15:00:00Z",
        location=GeoPoint(51.1,71.4,"Building"),organizerId="owner",venue=venue)

    @Test fun deployedCatalogDoesNotInventMallFloors() = runTest {
        val live=JsonVenueCatalog()
        assertTrue(live.building("landmark:mega")!!.floors.isEmpty())
        assertTrue(live.building("osm:way:123")!!.floors.isEmpty())
        assertNull(live.building("landmark:unknown"))
    }
    @Test fun hierarchyAndDrawingLimitsAreValidated() = runTest {
        val c=catalog()
        for(v in listOf(EventVenue("landmark:mega"),EventVenue("landmark:mega","l1"),EventVenue("landmark:mega","l1","atrium")))assertNull(c.validate(v))
        for(v in listOf(EventVenue("landmark:mega",roomId="atrium"),EventVenue("landmark:mega","l2","atrium"),
            EventVenue("landmark:arena","l1"),EventVenue("osm:node:1"),EventVenue("../../secret")))assertNotNull(c.validate(v))
        assertTrue(building.isValid())
        assertFalse(building.copy(schemaVersion=2).isValid())
        assertFalse(building.copy(floors=building.floors+building.floors.first()).isValid())
        assertFalse(building.copy(floors=listOf(building.floors[0].copy(plan=VenuePlan(Double.NaN,50.0)))).isValid())
        assertFalse(building.copy(floors=listOf(building.floors[0].copy(plan=null))).isValid())
        assertFails { catalog(building.copy(revision="bad/revision")) }
    }
    @Test fun routesDistinguishNoPlanFromMissingOrInvalidBuilding()=testApplication {
        application { install(ContentNegotiation){json()}; routing { venueRoutes(catalog()) } }
        val response=client.get("/venues/landmark:mega")
        assertEquals(HttpStatusCode.OK,response.status)
        assertEquals("no-cache",response.headers[HttpHeaders.CacheControl])
        assertEquals("nosniff",response.headers["X-Content-Type-Options"])
        assertEquals(building,Json.decodeFromString<VenueBuilding>(response.bodyAsText()))
        val noPlan=client.get("/venues/osm:way:123")
        assertEquals(HttpStatusCode.OK,noPlan.status)
        assertTrue(Json.decodeFromString<VenueBuilding>(noPlan.bodyAsText()).floors.isEmpty())
        assertEquals(HttpStatusCode.NotFound,client.get("/venues/landmark:unknown").status)
        assertEquals(HttpStatusCode.BadRequest,client.get("/venues/osm:node:123").status)
    }
    @Test fun refreshedServerDataAddsPlanWithoutChangingBuildingIdentity() = runTest {
        val before=JsonVenueCatalog().building("landmark:mega")!!
        val after=catalog().building(before.buildingId)!!
        assertTrue(before.floors.isEmpty());assertEquals(2,after.floors.size)
        assertEquals(before.buildingId,after.buildingId);assertNotEquals(before.revision,after.revision)
    }
    @Test fun persistenceFilteringAndPrivacyAreExactNotRadiusBased()=runTest {
        val repo=EventRepositoryImpl(database());val create=CreateEventUseCase(repo,catalog())
        val v=EventVenue("landmark:mega","l1","atrium")
        val made=assertIs<CreateEventUseCase.Result.Ok>(create(draft(v))).event
        repo.create(draft(v).copy(visibility="invite"));repo.create(draft(v).copy(visibility="unlisted"))
        repo.create(draft(EventVenue("landmark:arena")));repo.create(draft()) // same geographic point!
        val list=repo.search(EventQuery(buildingId="landmark:mega"))
        assertEquals(listOf(made.id),list.map{it.id});assertEquals(v,repo.byId(made.id)?.venue)
        assertEquals(v,made.toDto().venue)
        assertIs<CreateEventUseCase.Result.Error>(create(draft(v.copy(floorId="l2"))))
        assertEquals(3,repo.search(EventQuery()).size)
    }
    @Test fun editingPreservesLegacyVenueOrClearsItWhenMovedOrRequested()=runTest {
        val repo=EventRepositoryImpl(database());val v=EventVenue("landmark:mega","l1","atrium")
        val saved=repo.create(draft(v));val update=UpdateEventUseCase(repo,catalog())
        val retained=assertIs<UpdateEventUseCase.Result.Ok>(update("owner",saved.id,draft().copy(title="Renamed"))).event
        assertEquals(v,retained.venue)
        assertIs<UpdateEventUseCase.Result.Forbidden>(update("stranger",saved.id,draft(),clearVenue=true))
        assertIs<UpdateEventUseCase.Result.Error>(update("owner",saved.id,draft(v.copy(roomId="unknown"))))
        val removed=assertIs<UpdateEventUseCase.Result.Ok>(update("owner",saved.id,draft(),clearVenue=true)).event
        assertNull(removed.venue)
        update("owner",saved.id,draft(v))
        val moved=assertIs<UpdateEventUseCase.Result.Ok>(update("owner",saved.id,draft().copy(location=GeoPoint(51.2,71.5,"Elsewhere")))).event
        assertNull(moved.venue)
    }
    @Test fun requestAndQueryContractPreserveVenue() {
        val body=Json.decodeFromString<CreateEventRequest>("""{"title":"Meet","venue":{"buildingId":"landmark:mega","floorId":"l1","roomId":"atrium"},"clearVenue":false}""")
        assertEquals(EventVenue("landmark:mega","l1","atrium"),body.toDraft("owner").venue)
        assertEquals("landmark:mega",parseEventQuery(parametersOf("buildingId","landmark:mega"))?.buildingId)
        assertNull(parseEventQuery(parametersOf("buildingId","mvt:123")))
        assertNull(Json.decodeFromString<CreateEventRequest>("""{"title":"Legacy"}""").venue)
    }
}
