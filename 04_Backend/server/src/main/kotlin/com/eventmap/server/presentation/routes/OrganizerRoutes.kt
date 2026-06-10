package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.usecase.GetOrganizerEventsUseCase
import com.eventmap.server.domain.usecase.GetOrganizerUseCase
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.organizerRoutes(getOrganizer: GetOrganizerUseCase, getOrganizerEvents: GetOrganizerEventsUseCase) {
    get("/organizers/{id}") {
        val org = getOrganizer(call.parameters["id"].orEmpty())
        if (org == null) call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Организатор не найден")))
        else call.respond(org.toDto())
    }
    get("/organizers/{id}/events") {
        call.respond(getOrganizerEvents(call.parameters["id"].orEmpty()).map { it.toDto() })
    }
    post("/organizers/{id}/subscribe") { call.respond(HttpStatusCode.OK) }
    delete("/organizers/{id}/subscribe") { call.respond(HttpStatusCode.OK) }
}
