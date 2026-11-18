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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.*

/** Event V3 (V17): channels, moderation queue and map presentation. */
class EventChannelsTest {
    private fun database(): Database {
        val db = Database.connect("jdbc:h2:mem:channels-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction(db) { SchemaUtils.create(EventsTable, OrganizersTable, AttendanceTable, EventCommentsTable) }
        return db
    }
    private val soon = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS)
    private fun draft() = Event("", "Ярмарка", category = "market", startAt = soon.toString(),
        endAt = soon.plus(4, ChronoUnit.HOURS).toString(), location = GeoPoint(51.128, 71.430, "Парк"), organizerId = "owner")
    private val park = EventArea(
        polygon = listOf(listOf(71.428, 51.127), listOf(71.432, 51.127), listOf(71.432, 51.130), listOf(71.428, 51.130)),
        entrances = listOf(EventEntrance(71.430, 51.127, "Южный вход")),
    )
    private fun suggestion(extra: String = "", title: String = "Фестиваль во дворе", lat: Double = 51.128, start: Instant = soon) = """{
        "title":"$title","description":"Музыка и еда","category":"music",
        "startAt":"$start","endAt":"${start.plus(3, ChronoUnit.HOURS)}",
        "location":{"lat":$lat,"lng":71.43,"name":"Двор на Кенесары"},
        "buildingId":"landmark:mega","sourceUrl":"https://example.org/fest","contact":"@guest"$extra}"""

    @Test fun legacyClientsKeepPublishedPointEvents() {
        val body = Json.decodeFromString<CreateEventRequest>("""{"title":"Legacy","category":"Спорт"}""")
        val event = body.toDraft("owner")
        assertFalse(body.sendsV3())
        assertEquals("S", event.presentation); assertEquals("published", event.moderationStatus)
        assertNull(event.area); assertNull(event.recurrence); assertNull(event.contact)
        // Contacts never reach the public shape, whatever is stored.
        val public = Json.encodeToString(draft().copy(contact = "+7 700 000 00 00").toDto())
        assertFalse(public.contains("contact")); assertFalse(public.contains("700"))
    }

    @Test fun oneValidatorCoversTerritoryRecurrencePresentationAndSource() {
        assertNull(draft().validationError())
        assertNull(draft().copy(presentation = "M", area = park).validationError())
        assertNotNull(draft().copy(presentation = "M").validationError())
        assertNotNull(draft().copy(area = park.copy(polygon = park.polygon.take(2))).validationError())
        assertNotNull(draft().copy(area = park.copy(polygon = listOf(listOf(71.0, 51.0), listOf(71.3, 51.0), listOf(71.3, 51.3)))).validationError())
        assertNotNull(draft().copy(area = park, location = GeoPoint(51.2, 71.5, "Far")).validationError())
        assertNotNull(draft().copy(area = park.copy(entrances = listOf(EventEntrance(71.6, 51.3)))).validationError())
        val until = soon.atOffset(java.time.ZoneOffset.ofHours(5)).toLocalDate().plusWeeks(6).toString()
        assertNull(draft().copy(recurrence = EventRecurrence(listOf(6, 7), until)).validationError())
        assertNotNull(draft().copy(recurrence = EventRecurrence(listOf(0), until)).validationError())
        assertNotNull(draft().copy(recurrence = EventRecurrence(listOf(6, 6), until)).validationError())
        assertNotNull(draft().copy(recurrence = EventRecurrence(listOf(6), "2020-01-01")).validationError())
        assertNotNull(draft().copy(kind = "gathering", capacity = 10, recurrence = EventRecurrence(listOf(6), until)).validationError())
        assertNull(draft().copy(presentation = "L", sceneTemplate = "concert", sceneParams = """{"stage":"north"}""").validationError())
        assertNotNull(draft().copy(presentation = "L").validationError())
        assertNotNull(draft().copy(presentation = "L", sceneTemplate = "rocket").validationError())
        assertNotNull(draft().copy(presentation = "L", sceneTemplate = "concert", sceneParams = "[1,2]").validationError())
        assertNotNull(draft().copy(sceneTemplate = "concert").validationError())
        assertNotNull(draft().copy(presentation = "XL").validationError())
        assertNotNull(draft().copy(sourceType = "editorial").validationError())
        assertNull(draft().copy(sourceType = "editorial", sourceUrl = "https://astana.kz/afisha/1", sourceName = "Акимат").validationError())
        for (bad in listOf("javascript:alert(1)", "ftp://x.kz/a", "https://user@x.kz/", "https://x.kz/a b", "notaurl"))
            assertNotNull(draft().copy(sourceUrl = bad).validationError(), bad)
        assertNotNull(draft().copy(contact = "x".repeat(201)).validationError())
        assertNotNull(draft().copy(moderationStatus = "hidden").validationError())
        assertNotNull(draft().copy(sourceType = "admin").validationError())
    }

    @Test fun accountsCannotSwitchOnScenesAndOldAppsKeepNewFields() = runTest {
        val repo = EventRepositoryImpl(database())
        val create = CreateEventUseCase(repo, JsonVenueCatalog())
        assertIs<CreateEventUseCase.Result.Error>(create(draft().copy(presentation = "L", sceneTemplate = "concert")))
        val made = assertIs<CreateEventUseCase.Result.Ok>(create(draft().copy(presentation = "M", area = park))).event
        val stored = repo.byId(made.id)!!
        assertEquals(park, stored.area); assertEquals("M", stored.presentation); assertTrue(stored.createdAtMs > 0)
        val update = UpdateEventUseCase(repo, JsonVenueCatalog())
        // An app that predates V17 renames the event: the territory stays.
        val renamed = assertIs<UpdateEventUseCase.Result.Ok>(update("owner", made.id, draft().copy(title = "Ярмарка выходного дня"), keepV3 = true)).event
        assertEquals(park, renamed.area); assertEquals("M", renamed.presentation)
        // A current app sends the full shape and may return to a point.
        val point = assertIs<UpdateEventUseCase.Result.Ok>(update("owner", made.id, draft())).event
        assertNull(point.area); assertEquals("S", point.presentation)
        assertIs<UpdateEventUseCase.Result.Error>(update("owner", made.id, draft().copy(presentation = "L", sceneTemplate = "fair")))
    }

    @Test fun guestSuggestionWaitsForModeratorAndNeverLeaksContact() = testApplication {
        val db = database()
        val repo = EventRepositoryImpl(db)
        application {
            install(ContentNegotiation) { json() }
            routing {
                eventSuggestionRoutes(SuggestEventUseCase(repo, JsonVenueCatalog()), SuggestionLimiter(perClient = 50))
                adminEventRoutes(repo, ModerateEventUseCase(repo), "test-only")
            }
        }
        suspend fun send(body: String) = client.post("/events/suggestions") { contentType(ContentType.Application.Json); setBody(body) }

        val accepted = send(suggestion())
        assertEquals(HttpStatusCode.Accepted, accepted.status)
        assertEquals("""{"status":"pending"}""", accepted.bodyAsText().replace(Regex("\\s"), ""))
        assertEquals("no-store", accepted.headers[HttpHeaders.CacheControl])
        assertTrue(repo.search(EventQuery()).isEmpty(), "nothing reaches the map before moderation")
        assertTrue(repo.searchText("фестиваль").isEmpty()); assertTrue(repo.categoriesLike("mus").isEmpty())
        val queued = repo.byModeration("pending").single()
        assertEquals("guest", queued.sourceType); assertEquals("S", queued.presentation); assertNull(queued.organizerId)
        assertEquals("@guest", queued.contact); assertEquals(EventVenue("landmark:mega"), queued.venue)

        // Re-sent form, bot trap, wrong inputs: still one suggestion in the queue.
        assertEquals(HttpStatusCode.Accepted, send(suggestion()).status)
        assertEquals(HttpStatusCode.Accepted, send(suggestion(""","website":"http://spam"""", title = "Бот")).status)
        assertEquals(HttpStatusCode.BadRequest, send(suggestion().replace("\"music\"", "\"casino\"")).status)
        assertEquals(HttpStatusCode.BadRequest, send(suggestion(lat = 43.2)).status) // Almaty
        assertEquals(HttpStatusCode.BadRequest, send(suggestion(start = Instant.now().minus(2, ChronoUnit.DAYS))).status)
        assertEquals(HttpStatusCode.BadRequest, send("{not json").status)
        assertEquals(HttpStatusCode.PayloadTooLarge, send(suggestion(""","description":"${"x".repeat(20_000)}"""")).status)
        assertEquals(1, repo.byModeration("pending").size)

        assertEquals(HttpStatusCode.Forbidden, client.get("/admin/api/events").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/admin/api/events?status=all") { header("X-Admin-Key", "test-only") }.status)
        val queue = client.get("/admin/api/events") { header("X-Admin-Key", "test-only") }
        assertTrue(queue.bodyAsText().contains("@guest"), "the moderator sees the contact")
        suspend fun review(id: String, decision: String) = client.post("/admin/events/$id/review") {
            header("X-Admin-Key", "test-only"); contentType(ContentType.Application.Json); setBody("""{"decision":"$decision"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, client.post("/admin/events/${queued.id}/review") {
            contentType(ContentType.Application.Json); setBody("""{"decision":"approved"}""") }.status)
        assertEquals(HttpStatusCode.OK, review(queued.id, "approved").status)
        assertEquals(HttpStatusCode.Conflict, review(queued.id, "rejected").status, "a decision is taken once")
        val published = repo.search(EventQuery()).single()
        assertEquals("published", published.moderationStatus); assertNotNull(published.reviewedAtMs)
        assertFalse(Json.encodeToString(published.toDto()).contains("@guest"))

        assertEquals(HttpStatusCode.Accepted, send(suggestion(title = "Второе предложение")).status)
        val second = repo.byModeration("pending").single()
        assertEquals(HttpStatusCode.OK, review(second.id, "rejected").status)
        assertEquals(1, repo.search(EventQuery()).size)
        assertEquals("rejected", repo.byId(second.id)!!.moderationStatus)
    }

    @Test fun limiterCountsPerClientAndForTheWholeEndpoint() {
        var now = 1_000_000L
        val limiter = SuggestionLimiter(perClient = 2, total = 3, windowMs = 60_000, clock = { now })
        assertTrue(limiter.tryAcquire("1.1.1.1")); assertTrue(limiter.tryAcquire("1.1.1.1"))
        assertFalse(limiter.tryAcquire("1.1.1.1"))
        assertTrue(limiter.tryAcquire("2.2.2.2"))
        assertFalse(limiter.tryAcquire("3.3.3.3"), "endpoint-wide cap")
        now += 60_001
        assertTrue(limiter.tryAcquire("1.1.1.1"))
    }

    @Test fun rateLimitAnswers429WithRetryAfter() = testApplication {
        val repo = EventRepositoryImpl(database())
        application {
            install(ContentNegotiation) { json() }
            routing { eventSuggestionRoutes(SuggestEventUseCase(repo, JsonVenueCatalog()), SuggestionLimiter(perClient = 1)) }
        }
        suspend fun send(forwarded: String) = client.post("/events/suggestions") {
            header("X-Forwarded-For", forwarded); contentType(ContentType.Application.Json); setBody(suggestion())
        }
        assertEquals(HttpStatusCode.Accepted, send("9.9.9.9, 10.0.0.1").status)
        // A client cannot escape the limit by prepending its own address: the proxy's last entry counts.
        val limited = send("8.8.8.8, 10.0.0.1")
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("3600", limited.headers[HttpHeaders.RetryAfter])
    }
}
