package com.eventmap.server

import com.eventmap.server.data.venue.*
import com.eventmap.server.domain.model.*
import com.eventmap.server.domain.repository.*
import com.eventmap.server.presentation.routes.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.*

class VenueEditorTest {
    private val id="landmark:mega"
    private val plan=VenueBuilding(buildingId=id,revision="draft",name="Синтетический тест, не план MEGA",
        floors=listOf(VenueFloor("l1","Первый этаж",1,VenuePlan(100.0,60.0),listOf(VenueRoom("hall","Зал",listOf(
            PlanPoint(5.0,5.0),PlanPoint(50.0,5.0),PlanPoint(50.0,50.0),PlanPoint(5.0,50.0)))))))
    private fun database(): Database {
        val db=Database.connect("jdbc:h2:mem:venue-editor-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",driver="org.h2.Driver")
        // Execute the actual V16 SQL, not SchemaUtils; production PostgreSQL still needs its own rollout.
        val sql=javaClass.classLoader.getResource("db/migration/V16__venue_plan_editor.sql")!!.readText()
        transaction(db) { sql.split(';').filter { it.isNotBlank() }.forEach { exec(it) } }
        return db
    }
    @Test fun draftsStayPrivateAndPublicationsSurviveNewRepositoryInstance()=runTest {
        val db=database();val first=DbVenueCatalog(database=db);val second=DbVenueCatalog(database=db)
        assertEquals(0,first.editor(id)!!.version)
        assertTrue(first.building(id)!!.floors.isEmpty())
        val saved=first.save(id,0,plan)
        assertEquals(1,saved.version);assertTrue(second.building(id)!!.floors.isEmpty())
        val published=first.publish(id,1)
        assertEquals(2,published.version);assertEquals(plan.floors,second.building(id)!!.floors)
        assertNotEquals("draft",published.published!!.revision)
        assertEquals(published.published,second.revision(id,published.published.revision))
        first.save(id,2,plan.copy(name="Changed draft"))
        assertEquals(plan.name,second.building(id)!!.name)
        assertEquals("Changed draft",DbVenueCatalog(database=db).editor(id)!!.draft.name)
        assertNull(second.validate(EventVenue(id,"l1","hall")))
        assertNotNull(second.validate(EventVenue(id,"l2","hall")))
    }
    @Test fun staleWritesAndRepeatPublishNeverOverwriteOrDuplicateHistory()=runTest {
        val repo=DbVenueCatalog(database=database());repo.save(id,0,plan)
        assertEquals("VERSION_CONFLICT",assertFailsWith<VenueEditError>{repo.save(id,0,plan.copy(name="stale"))}.code)
        val published=repo.publish(id,1)
        assertEquals("VERSION_CONFLICT",assertFailsWith<VenueEditError>{repo.publish(id,1)}.code)
        assertEquals(1,repo.editor(id)!!.history.size);assertEquals(published.published,repo.building(id))
    }
    @Test fun concurrentFirstSavesHaveExactlyOneWinner()=runBlocking {
        val db=database()
        val results=listOf("A","B").map { name -> async(Dispatchers.IO) {
            runCatching { DbVenueCatalog(database=db).save(id,0,plan.copy(name=name)) }
        }}.awaitAll()
        assertEquals(1,results.count{it.isSuccess})
        assertEquals("VERSION_CONFLICT",assertIs<VenueEditError>(results.single{it.isFailure}.exceptionOrNull()).code)
        assertEquals(1,DbVenueCatalog(database=db).editor(id)!!.version)
    }
    @Test fun publishedReferencesCannotBeRemovedButDrawingCanBeCleared()=runTest {
        val repo=DbVenueCatalog(database=database());repo.save(id,0,plan);val publication=repo.publish(id,1).published
        repo.save(id,2,plan.copy(floors=emptyList()))
        assertEquals("REFERENCES_REMOVED",assertFailsWith<VenueEditError>{repo.publish(id,3)}.code)
        assertEquals(publication,repo.building(id));assertEquals(1,repo.editor(id)!!.history.size)
        val noDrawing=plan.copy(floors=plan.floors.map{it.copy(plan=null,rooms=it.rooms.map{r->r.copy(polygon=emptyList())})})
        repo.save(id,3,noDrawing);repo.publish(id,4)
        assertNull(repo.building(id)!!.floors.first().plan);assertEquals(2,repo.editor(id)!!.history.size)
        assertEquals(publication,repo.revision(id,publication!!.revision))
        assertNull(repo.revision("landmark:arena",publication.revision))
    }
    @Test fun malformedAndUnknownTargetsLeaveNoPartialDraft()=runTest {
        val repo=DbVenueCatalog(database=database())
        assertFailsWith<VenueEditError>{repo.save(id,0,plan.copy(buildingId="landmark:arena"))}
        assertFailsWith<VenueEditError>{repo.save(id,0,plan.copy(schemaVersion=2))}
        assertFailsWith<VenueEditError>{repo.save("landmark:unknown",0,plan.copy(buildingId="landmark:unknown"))}
        assertEquals(0,repo.editor(id)!!.version)
        val osm="osm:way:123";repo.save(osm,0,plan.copy(buildingId=osm));repo.publish(osm,1)
        assertTrue(repo.list().any{it.buildingId==osm});assertEquals(osm,repo.building(osm)!!.buildingId)
    }
    @Test fun apiAuthPublicationAndErrorsUseRealDatabase()=testApplication {
        val repo=DbVenueCatalog(database=database())
        application { install(ContentNegotiation){json()};routing { adminVenueRoutes(repo,"test-key");venueRoutes(repo) } }
        for(method in listOf(HttpMethod.Get,HttpMethod.Put,HttpMethod.Post)) {
            val suffix=when(method){HttpMethod.Put->"/$id/draft";HttpMethod.Post->"/$id/publications";else->"/$id"}
            assertEquals(HttpStatusCode.Forbidden,client.request("/admin/api/venues$suffix"){this.method=method}.status)
        }
        assertEquals(HttpStatusCode.Forbidden,client.get("/admin/api/venues/$id/revisions/p-missing").status)
        val empty=client.get("/admin/api/venues/$id"){header("X-Admin-Key","test-key")}.bodyAsText()
        assertTrue(empty.contains("\"floors\":[]"));assertTrue(empty.contains("\"history\":[]"))
        suspend fun save(body:String)=client.put("/admin/api/venues/$id/draft"){header("X-Admin-Key","test-key");contentType(ContentType.Application.Json);setBody(body)}
        val body="""{"expectedVersion":0,"building":${Json.encodeToString(plan)}}"""
        assertEquals(HttpStatusCode.OK,save(body).status)
        assertTrue(Json.decodeFromString<VenueBuilding>(client.get("/venues/$id").bodyAsText()).floors.isEmpty())
        val response=client.post("/admin/api/venues/$id/publications") { header("X-Admin-Key","test-key");contentType(ContentType.Application.Json);setBody("""{"expectedVersion":1}""") }
        assertEquals(HttpStatusCode.OK,response.status);assertEquals("no-store",response.headers[HttpHeaders.CacheControl])
        val published=Json.decodeFromString<VenueEditState>(response.bodyAsText()).published!!
        assertEquals(published,Json.decodeFromString<VenueBuilding>(client.get("/venues/$id").bodyAsText()))
        assertEquals(HttpStatusCode.Conflict,save(body).status)
        assertEquals(HttpStatusCode.BadRequest,save("{bad").status)
        assertEquals(HttpStatusCode.PayloadTooLarge,save("x".repeat(1024*1024+1)).status)
        assertEquals(HttpStatusCode.NotFound,client.get("/admin/api/venues/landmark:unknown"){header("X-Admin-Key","test-key")}.status)
        val html=client.get("/admin/venues");assertEquals(HttpStatusCode.OK,html.status)
        assertTrue(html.headers["Content-Security-Policy"]!!.contains("frame-ancestors 'none'"))
        assertFalse(html.bodyAsText().contains("test-key"))
        assertEquals(HttpStatusCode.OK,client.get("/admin/venues.js").status)
    }
    @Test fun emptyConfiguredKeyNeverGrantsAccess()=testApplication {
        application { install(ContentNegotiation){json()};routing { adminVenueRoutes(DbVenueCatalog(database=database()),"") } }
        assertEquals(HttpStatusCode.Forbidden,client.get("/admin/api/venues"){header("X-Admin-Key","")}.status)
    }
}
