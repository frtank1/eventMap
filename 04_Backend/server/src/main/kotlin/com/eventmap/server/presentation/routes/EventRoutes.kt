package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.usecase.GetEventUseCase
import com.eventmap.server.domain.usecase.GetEventsUseCase
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.eventRoutes(getEvents: GetEventsUseCase, getEvent: GetEventUseCase) {
    get("/events") {
        call.respond(getEvents().map { it.toDto() })
    }
    get("/events/{id}") {
        val ev = getEvent(call.parameters["id"].orEmpty())
        if (ev == null) call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("EVENT_NOT_FOUND", "Событие не найдено")))
        else call.respond(ev.toDto())
    }
    get("/events/{id}/participants") { call.respond(emptyList<String>()) }
    get("/events/{id}/media") { call.respond(emptyList<String>()) }
    get("/events/{id}/ratings") { call.respond(emptyList<String>()) }
    post("/events/{id}/attend") { call.respond(HttpStatusCode.OK) }
    post("/events/{id}/rating") { call.respond(HttpStatusCode.OK) }
    post("/events/{id}/purchase") { call.respond(mapOf("status" to "paid")) }
}
