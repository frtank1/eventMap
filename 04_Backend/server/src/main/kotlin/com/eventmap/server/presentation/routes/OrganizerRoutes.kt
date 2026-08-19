package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.usecase.GetOrganizerEventsUseCase
import com.eventmap.server.domain.usecase.GetOrganizerUseCase
import com.eventmap.server.domain.usecase.SubscribeOrganizerUseCase
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.organizerRoutes(
    getOrganizer: GetOrganizerUseCase,
    getOrganizerEvents: GetOrganizerEventsUseCase,
    subscribeOrganizer: SubscribeOrganizerUseCase,
) {
    get("/organizers/{id}") {
        val org = getOrganizer(call.parameters["id"].orEmpty())
        if (org == null) call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Организатор не найден")))
        else call.respond(org.toDto())
    }
    get("/organizers/{id}/events") {
        call.respond(getOrganizerEvents(call.parameters["id"].orEmpty()).map { it.toDto() })
    }

    // Подписка F1 (V9): реальная запись в organizer_subscriptions (раньше — заглушка)
    authenticate("auth-jwt") {
        post("/organizers/{id}/subscribe") { call.handleSubscribe(subscribeOrganizer, subscribe = true) }
        delete("/organizers/{id}/subscribe") { call.handleSubscribe(subscribeOrganizer, subscribe = false) }
    }
}

private suspend fun ApplicationCall.handleSubscribe(
    subscribeOrganizer: SubscribeOrganizerUseCase,
    subscribe: Boolean,
) {
    val uid = principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
        ?: return respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
    when (subscribeOrganizer(uid, parameters["id"].orEmpty(), subscribe)) {
        is SubscribeOrganizerUseCase.Result.Ok -> respond(HttpStatusCode.OK)
        is SubscribeOrganizerUseCase.Result.NotFound ->
            respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Организатор не найден")))
    }
}
