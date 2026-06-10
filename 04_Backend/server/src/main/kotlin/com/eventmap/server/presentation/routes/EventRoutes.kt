package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.model.Bbox
import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.EventQuery
import com.eventmap.server.domain.model.GeoPoint
import com.eventmap.server.domain.model.Money
import com.eventmap.server.domain.usecase.CreateEventUseCase
import com.eventmap.server.domain.usecase.GetEventUseCase
import com.eventmap.server.domain.usecase.GetEventsUseCase
import com.eventmap.server.presentation.dto.CreateEventRequest
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.eventRoutes(getEvents: GetEventsUseCase, getEvent: GetEventUseCase, createEvent: CreateEventUseCase) {
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
    }
    get("/events/{id}/participants") { call.respond(emptyList<String>()) }
    get("/events/{id}/media") { call.respond(emptyList<String>()) }
    get("/events/{id}/ratings") { call.respond(emptyList<String>()) }
    post("/events/{id}/attend") { call.respond(HttpStatusCode.OK) }
    post("/events/{id}/rating") { call.respond(HttpStatusCode.OK) }
    post("/events/{id}/purchase") { call.respond(mapOf("status" to "paid")) }
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
)
