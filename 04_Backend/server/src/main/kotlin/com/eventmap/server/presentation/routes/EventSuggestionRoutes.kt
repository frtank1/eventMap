package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.EventVenue
import com.eventmap.server.domain.model.GeoPoint
import com.eventmap.server.domain.model.Money
import com.eventmap.server.domain.model.validBuildingId
import com.eventmap.server.domain.repository.EventRepository
import com.eventmap.server.domain.usecase.EventVocabulary
import com.eventmap.server.domain.usecase.ModerateEventUseCase
import com.eventmap.server.domain.usecase.SuggestEventUseCase
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.ReviewRequest
import com.eventmap.server.presentation.dto.SuggestEventRequest
import com.eventmap.server.presentation.dto.SuggestEventResponse
import com.eventmap.server.presentation.dto.toAdminDto
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.security.MessageDigest

private const val SUGGESTION_BODY_LIMIT = 16 * 1024
private val suggestionJson = Json { ignoreUnknownKeys = true; isLenient = false }

/**
 * Sliding one-hour window per client and for the whole endpoint. In memory on purpose: client
 * addresses are hashed, never stored, and are forgotten with the window.
 */
class SuggestionLimiter(
    private val perClient: Int = 5,
    private val total: Int = 300,
    private val windowMs: Long = 3_600_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val byClient = HashMap<String, ArrayDeque<Long>>()
    private val all = ArrayDeque<Long>()

    @Synchronized fun tryAcquire(client: String): Boolean {
        val now = clock()
        val from = now - windowMs
        while (all.isNotEmpty() && all.first() <= from) all.removeFirst()
        val key = digest(client)
        val mine = byClient.getOrPut(key) { ArrayDeque() }
        while (mine.isNotEmpty() && mine.first() <= from) mine.removeFirst()
        if (mine.size >= perClient || all.size >= total) return false
        mine.addLast(now)
        all.addLast(now)
        if (byClient.size > 10_000) byClient.entries.removeIf { it.value.isEmpty() || it.value.last() <= from }
        return true
    }

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
}

/** The proxy in front of the server appends the real address last; earlier entries are client-supplied. */
internal fun ApplicationCall.clientAddress(): String =
    (request.header("X-Forwarded-For")?.split(',')?.map { it.trim() }?.lastOrNull { it.isNotEmpty() }
        ?: request.origin.remoteHost).take(64)

private fun problem(code: String, message: String) = ErrorResponse(ErrorResponse.Body(code, message))

/** POST /events/suggestions — no account; the answer never echoes what was sent. */
fun Route.eventSuggestionRoutes(suggest: SuggestEventUseCase, limiter: SuggestionLimiter = SuggestionLimiter()) {
    post("/events/suggestions") {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        val declared = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        if (declared != null && declared > SUGGESTION_BODY_LIMIT)
            return@post call.respond(HttpStatusCode.PayloadTooLarge, problem("BODY_TOO_LARGE", "Слишком длинное предложение"))
        if (!limiter.tryAcquire(call.clientAddress())) {
            call.response.header(HttpHeaders.RetryAfter, "3600")
            return@post call.respond(HttpStatusCode.TooManyRequests, problem("RATE_LIMITED", "Слишком много предложений подряд. Попробуйте через час"))
        }
        val bytes = try {
            withTimeout(10_000) { call.receiveChannel().readRemaining((SUGGESTION_BODY_LIMIT + 1).toLong()).readBytes() }
        } catch (e: Exception) {
            return@post call.respond(HttpStatusCode.BadRequest, problem("BAD_REQUEST", "Не удалось получить форму"))
        }
        if (bytes.size > SUGGESTION_BODY_LIMIT)
            return@post call.respond(HttpStatusCode.PayloadTooLarge, problem("BODY_TOO_LARGE", "Слишком длинное предложение"))
        val body = runCatching { suggestionJson.decodeFromString(SuggestEventRequest.serializer(), bytes.decodeToString()) }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, problem("BAD_REQUEST", "Некорректная форма"))
        // A bot filled the field people never see: answer as usual and keep nothing.
        if (body.website.isNotBlank()) return@post call.respond(HttpStatusCode.Accepted, SuggestEventResponse("pending"))
        if (body.category !in SUGGESTION_CATEGORIES)
            return@post call.respond(HttpStatusCode.BadRequest, problem("BAD_REQUEST", "Выберите категорию из списка"))
        if (body.title.trim().length > 120 || body.description.trim().length > 2000)
            return@post call.respond(HttpStatusCode.BadRequest, problem("BAD_REQUEST", "Название до 120 символов, описание до 2000"))
        val draft = Event(
            id = "", title = body.title, description = body.description, category = body.category,
            startAt = body.startAt.trim(), endAt = body.endAt.trim(),
            location = body.location?.let { GeoPoint(it.lat, it.lng, it.name.trim().take(255)) } ?: GeoPoint(Double.NaN, Double.NaN),
            price = Money(body.price.type, body.price.amount, "KZT"),
            venue = body.buildingId?.takeIf { validBuildingId(it) }?.let { EventVenue(it) },
            sourceUrl = body.sourceUrl, contact = body.contact,
        )
        when (val r = suggest(draft)) {
            is SuggestEventUseCase.Result.Accepted -> call.respond(HttpStatusCode.Accepted, SuggestEventResponse("pending"))
            is SuggestEventUseCase.Result.QueueFull -> {
                call.response.header(HttpHeaders.RetryAfter, "3600")
                call.respond(HttpStatusCode.ServiceUnavailable, problem("QUEUE_FULL", "Очередь предложений заполнена. Попробуйте позже"))
            }
            is SuggestEventUseCase.Result.Error -> call.respond(HttpStatusCode.BadRequest, problem("BAD_REQUEST", r.message))
        }
    }
}

/** Categories of the public map (web shell and curated file). */
val SUGGESTION_CATEGORIES = setOf(
    "sport", "music", "market", "discovery", "route", "quest", "community",
    "newyear", "kids", "art", "theatre", "education",
)

/** Moderation queue of the admin console (shared admin key until personal roles exist). */
fun Route.adminEventRoutes(events: EventRepository, moderate: ModerateEventUseCase, adminKey: String) {
    get("/admin/api/events") {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        if (!call.validAdminKey(adminKey)) return@get call.respond(HttpStatusCode.Forbidden, problem("FORBIDDEN", "Неверный админ-ключ"))
        val status = call.request.queryParameters["status"] ?: "pending"
        if (status !in EventVocabulary.MODERATION)
            return@get call.respond(HttpStatusCode.BadRequest, problem("INVALID_STATUS", "Неизвестный статус события"))
        call.respond(events.byModeration(status).map { it.toAdminDto() })
    }
    post("/admin/events/{id}/review") {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        if (!call.validAdminKey(adminKey)) return@post call.respond(HttpStatusCode.Forbidden, problem("FORBIDDEN", "Неверный админ-ключ"))
        val body = runCatching { call.receive<ReviewRequest>() }.getOrNull()
        if (body == null || body.decision !in setOf("approved", "rejected"))
            return@post call.respond(HttpStatusCode.BadRequest, problem("BAD_REQUEST", "decision: approved | rejected"))
        when (val r = moderate(call.parameters["id"].orEmpty(), body.decision == "approved", body.reason)) {
            is ModerateEventUseCase.Result.Ok -> call.respond(r.event.toAdminDto())
            is ModerateEventUseCase.Result.NotPending ->
                call.respond(HttpStatusCode.Conflict, problem("NOT_PENDING", "Предложение не найдено или решение уже принято"))
            is ModerateEventUseCase.Result.Error ->
                call.respond(HttpStatusCode.UnprocessableEntity, problem("INVALID_EVENT", r.message))
        }
    }
}
