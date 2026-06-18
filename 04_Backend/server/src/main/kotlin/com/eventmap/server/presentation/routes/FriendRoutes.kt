package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.usecase.CreateInvitationUseCase
import com.eventmap.server.domain.usecase.GetFriendsUseCase
import com.eventmap.server.domain.usecase.GetInvitationByTokenUseCase
import com.eventmap.server.domain.usecase.GetInvitationForGuestUseCase
import com.eventmap.server.domain.usecase.GetRsvpDashboardUseCase
import com.eventmap.server.domain.usecase.RespondFriendRequestUseCase
import com.eventmap.server.domain.usecase.RsvpUseCase
import com.eventmap.server.domain.usecase.SendFriendRequestUseCase
import com.eventmap.server.presentation.dto.CreateInvitationRequest
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.FriendRequestBody
import com.eventmap.server.presentation.dto.FriendRespondBody
import com.eventmap.server.presentation.dto.RsvpRequest
import com.eventmap.server.presentation.dto.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

private fun ApplicationCall.uid(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()

// Друзья (N1) + приглашения и RSVP (N2–N5, API §8.7)
fun Route.friendRoutes(
    sendRequest: SendFriendRequestUseCase,
    respond: RespondFriendRequestUseCase,
    getFriends: GetFriendsUseCase,
    createInvitation: CreateInvitationUseCase,
    invitationForGuest: GetInvitationForGuestUseCase,
    invitationByToken: GetInvitationByTokenUseCase,
    rsvp: RsvpUseCase,
    dashboard: GetRsvpDashboardUseCase,
) {
    authenticate("auth-jwt") {
        get("/friends") {
            val uid = call.uid() ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            call.respond(getFriends(uid).map { it.toDto() })
        }

        post("/friends/request") {
            val uid = call.uid() ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val b = call.receive<FriendRequestBody>()
            when (val r = sendRequest(uid, b.userId)) {
                is SendFriendRequestUseCase.Result.Ok -> call.respond(HttpStatusCode.Created)
                is SendFriendRequestUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }

        post("/friends/respond") {
            val uid = call.uid() ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val b = call.receive<FriendRespondBody>()
            if (respond(uid, b.userId, b.accept)) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NO_REQUEST", "Заявки нет или отвечать может только адресат")))
        }

        post("/invitations") {
            val uid = call.uid() ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val b = call.receive<CreateInvitationRequest>()
            when (val r = createInvitation(uid, b.eventId, b.templateId, b.guestUserIds, b.allowLink)) {
                is CreateInvitationUseCase.Result.Ok -> call.respond(HttpStatusCode.Created, r.invitation.toDto())
                is CreateInvitationUseCase.Result.EventNotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("EVENT_NOT_FOUND", "Событие не найдено")))
                is CreateInvitationUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }

        // открытка гостю (N4): без списка остальных гостей
        get("/invitations/{id}") {
            val uid = call.uid() ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val inv = invitationForGuest(call.parameters["id"].orEmpty(), uid)
            if (inv == null) call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Приглашение не найдено или ты не в списке")))
            else call.respond(inv.toDto(showGuests = false))
        }

        post("/invitations/{id}/rsvp") {
            val uid = call.uid() ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val b = call.receive<RsvpRequest>()
            when (val r = rsvp.byUser(call.parameters["id"].orEmpty(), uid, b.answer, b.plusOne)) {
                is RsvpUseCase.Result.Ok -> call.respond(HttpStatusCode.OK)
                is RsvpUseCase.Result.NotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Ты не в списке гостей")))
                is RsvpUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }

        // дашборд организатора (N5): полный список + счётчики
        get("/events/{id}/rsvp") {
            val uid = call.uid() ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            when (val r = dashboard(call.parameters["id"].orEmpty(), uid)) {
                is GetRsvpDashboardUseCase.Result.Ok -> call.respond(r.invitation.toDto())
                is GetRsvpDashboardUseCase.Result.NotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Приглашение не найдено")))
                is GetRsvpDashboardUseCase.Result.Forbidden ->
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("FORBIDDEN", "Дашборд доступен организатору")))
            }
        }
    }

    // ===== Публичная веб-открытка по ссылке (без аккаунта) =====
    get("/i/{token}") {
        val inv = invitationByToken(call.parameters["token"].orEmpty())
        if (inv == null) call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Приглашение не найдено")))
        else call.respond(inv.toDto(showGuests = false))
    }

    post("/i/{token}/rsvp") {
        val b = call.receive<RsvpRequest>()
        when (val r = rsvp.byLink(call.parameters["token"].orEmpty(), b.guestName.orEmpty(), b.answer, b.plusOne)) {
            is RsvpUseCase.Result.Ok -> call.respond(HttpStatusCode.OK)
            is RsvpUseCase.Result.NotFound ->
                call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Приглашение не найдено")))
            is RsvpUseCase.Result.Rejected ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
        }
    }
}
