package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.model.Bbox
import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.EventQuery
import com.eventmap.server.domain.model.GeoPoint
import com.eventmap.server.domain.model.Money
import com.eventmap.server.domain.repository.EventRepository
import com.eventmap.server.domain.usecase.CreateEventUseCase
import com.eventmap.server.domain.usecase.DeleteEventUseCase
import com.eventmap.server.domain.usecase.GetEventParticipantsUseCase
import com.eventmap.server.domain.usecase.GetFriendsUseCase
import com.eventmap.server.domain.usecase.GetOrganizerEventsUseCase
import com.eventmap.server.domain.usecase.UpdateEventUseCase
import com.eventmap.server.domain.usecase.GetEventRatingsUseCase
import com.eventmap.server.domain.usecase.GetEventTicketStatusUseCase
import com.eventmap.server.domain.usecase.GetEventUseCase
import com.eventmap.server.domain.usecase.GetEventsUseCase
import com.eventmap.server.domain.usecase.PurchaseTicketsUseCase
import com.eventmap.server.domain.usecase.RateEventUseCase
import com.eventmap.server.presentation.dto.CreateEventRequest
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.EventTicketDto
import com.eventmap.server.presentation.dto.RateEventRequest
import com.eventmap.server.presentation.dto.toDto
import com.eventmap.server.presentation.dto.toParticipantDto
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

// «N друзей уже там» (рилс D8): мои друзья ∩ участники события
@Serializable
data class FriendsGoingDto(val count: Int, val names: List<String>)
@Serializable data class EventSignalResponse(val enabled: Boolean, val count: Int)
@Serializable data class JoinEventRequest(val message: String = "")
@Serializable data class ReviewJoinRequest(val userId: String, val approve: Boolean)
@Serializable data class JoinEventResponse(val status: String)

fun Route.eventRoutes(
    getEvents: GetEventsUseCase,
    getEvent: GetEventUseCase,
    createEvent: CreateEventUseCase,
    updateEvent: UpdateEventUseCase,
    deleteEvent: DeleteEventUseCase,
    myEvents: GetOrganizerEventsUseCase,
    friendsOf: GetFriendsUseCase,
    participants: GetEventParticipantsUseCase,
    eventRatings: GetEventRatingsUseCase,
    rateEvent: RateEventUseCase,
    ticketStatus: GetEventTicketStatusUseCase,
    purchase: PurchaseTicketsUseCase,
    eventRepo: EventRepository,
) {
    get("/events") {
        val query = parseEventQuery(call.request.queryParameters)
        if (query == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorResponse.Body("BAD_REQUEST", "bbox: ожидается swLat,swLng,neLat,neLng")))
        } else {
            call.respond(getEvents(query).map { it.toDto() })
        }
    }
    get("/events/{id}") {
        val ev = getEvent(call.parameters["id"].orEmpty())
        if (ev == null) call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("EVENT_NOT_FOUND", "Событие не найдено")))
        else call.respond(ev.toDto())
    }
    authenticate("auth-jwt") {
        post("/events") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нет userId в токене")))
                return@post
            }
            val body = call.receive<CreateEventRequest>()
            when (val r = createEvent(body.toDraft(creatorId = userId))) {
                is CreateEventUseCase.Result.Ok -> call.respond(HttpStatusCode.Created, r.event.toDto())
                is CreateEventUseCase.Result.Error ->
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorResponse.Body("BAD_REQUEST", r.message)))
            }
        }

        post("/events/{id}/signals/{type}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val result = eventRepo.toggleSignal(
                eventId = call.parameters["id"].orEmpty(),
                userId = userId,
                type = call.parameters["type"].orEmpty(),
            ) ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorResponse.Body("BAD_SIGNAL", "Допустимо confirm или interest")),
            )
            call.respond(EventSignalResponse(result.first, result.second))
        }

        post("/events/{id}/join") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val body = call.receive<JoinEventRequest>()
            val status = eventRepo.requestJoin(call.parameters["id"].orEmpty(), userId, body.message)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("EVENT_NOT_FOUND", "Событие не найдено")))
            if (status == "invite_required") {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("INVITE_REQUIRED", "Вступление только по приглашению")))
            } else {
                call.respond(JoinEventResponse(status))
            }
        }

        post("/events/{id}/join/review") {
            val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val body = call.receive<ReviewJoinRequest>()
            val status = eventRepo.reviewJoin(call.parameters["id"].orEmpty(), ownerId, body.userId, body.approve)
                ?: return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("FORBIDDEN", "Нет прав или заявки")))
            call.respond(JoinEventResponse(status))
        }

        // «N друзей уже там»: мои принятые друзья, отметившиеся на событии
        get("/events/{id}/friends-going") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val eventId = call.parameters["id"].orEmpty()
            val friendIds = friendsOf(userId).filter { it.status == "accepted" }.map { it.userId }.toSet()
            val mine = participants(eventId, "going").filter { it.id in friendIds }
            call.respond(FriendsGoingDto(count = mine.size, names = mine.take(3).map { it.name }))
        }

        // Мои события (organizerId созданных руками = userId создателя)
        get("/events/my") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            call.respond(myEvents(userId).map { it.toDto() })
        }

        // Редактирование своего события (владелец = organizerId)
        patch("/events/{id}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@patch call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val body = call.receive<CreateEventRequest>()
            when (val r = updateEvent(userId, call.parameters["id"].orEmpty(), body.toDraft(creatorId = userId))) {
                is UpdateEventUseCase.Result.Ok -> call.respond(r.event.toDto())
                is UpdateEventUseCase.Result.NotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("EVENT_NOT_FOUND", "Событие не найдено")))
                is UpdateEventUseCase.Result.Forbidden ->
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("FORBIDDEN", "Редактировать может только создатель")))
                is UpdateEventUseCase.Result.Error ->
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorResponse.Body("BAD_REQUEST", r.message)))
            }
        }

        // Удаление своего события
        delete("/events/{id}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            when (deleteEvent(userId, call.parameters["id"].orEmpty())) {
                is DeleteEventUseCase.Result.Ok -> call.respond(HttpStatusCode.NoContent)
                is DeleteEventUseCase.Result.NotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("EVENT_NOT_FOUND", "Событие не найдено")))
                is DeleteEventUseCase.Result.Forbidden ->
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("FORBIDDEN", "Удалять может только создатель")))
            }
        }
    }
    // Участники D1c: «иду» + «был» (attendance × users)
    get("/events/{id}/participants") {
        val eventId = call.parameters["id"].orEmpty()
        val type = call.request.queryParameters["type"] ?: "going"
        call.respond(participants(eventId, type).map { it.toParticipantDto(type) })
    }
    // GET /events/{id}/media — реализован в MediaRoutes (реальные медиа из object storage)

    // Оценки P4 (V9): реальные, с пересчётом rating_avg/count
    get("/events/{id}/ratings") {
        call.respond(eventRatings(call.parameters["id"].orEmpty()).map { it.toDto() })
    }
    // attend → SocialRoutes (честная гео-логика D1c)

    authenticate("auth-jwt") {
        post("/events/{id}/rating") {
            val uid = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val body = call.receive<RateEventRequest>()
            when (val r = rateEvent(call.parameters["id"].orEmpty(), uid, body.value, body.review, body.tags)) {
                is RateEventUseCase.Result.Ok -> call.respond(r.rating.toDto())
                is RateEventUseCase.Result.NotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("EVENT_NOT_FOUND", "Событие не найдено")))
                is RateEventUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }

        // Статус моего билета на событие (mobile: Ticket {status, paymentUrl})
        get("/events/{id}/ticket") {
            val uid = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            call.respond(EventTicketDto(status = ticketStatus(call.parameters["id"].orEmpty(), uid)))
        }

        // Упрощённая покупка (P1, mobile: POST без тела) → реальный заказ через PurchaseTicketsUseCase
        post("/events/{id}/purchase") {
            val uid = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            when (val r = purchase(call.parameters["id"].orEmpty(), uid, guests = emptyList(), provider = "mock")) {
                is PurchaseTicketsUseCase.Result.Ok -> call.respond(EventTicketDto(status = "paid"))
                is PurchaseTicketsUseCase.Result.NotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("EVENT_NOT_FOUND", "Событие не найдено")))
                is PurchaseTicketsUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }
    }
}

// null = невалидный bbox (вернём 400). Отсутствие bbox — валидно (без гео-фильтра).
private fun parseEventQuery(params: Parameters): EventQuery? {
    val bbox = params["bbox"]?.let { raw ->
        val parts = raw.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        if (parts.size != 4) return null
        Bbox(swLat = parts[0], swLng = parts[1], neLat = parts[2], neLng = parts[3])
    }
    val categories = params["categories"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    val price = params["price"]?.takeIf { it == "free" || it == "paid" }
    return EventQuery(bbox = bbox, categories = categories, priceType = price)
}

private fun CreateEventRequest.toDraft(creatorId: String) = Event(
    id = "",
    title = title.trim(),
    description = description.trim(),
    category = category,
    status = "soon",
    startAt = startAt,
    endAt = endAt,
    location = location?.let { GeoPoint(it.lat, it.lng, it.name) } ?: GeoPoint(0.0, 0.0),
    price = Money(price.type, price.amount, price.currency),
    cover = coverMediaId,
    organizerId = creatorId,
    capacity = limit,
    kind = kind,
    // Обычный JWT не может объявить себя государством/бизнесом.
    // Повышенные sourceType назначаются отдельным аккредитованным контуром.
    sourceType = sourceType.takeIf { it == "initiator" || it == "witness" } ?: "initiator",
    visibility = visibility,
    joinPolicy = joinPolicy,
    audience = audience,
    minAge = minAge,
    expiresAtMs = expiresAtMs,
    proofMediaId = proofMediaId,
)
