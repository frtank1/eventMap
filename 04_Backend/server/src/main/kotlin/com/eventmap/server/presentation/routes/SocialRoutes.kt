package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.usecase.AddCommentUseCase
import com.eventmap.server.domain.usecase.AttendUseCase
import com.eventmap.server.domain.usecase.GetCommentsUseCase
import com.eventmap.server.domain.usecase.ToggleCommentLikeUseCase
import com.eventmap.server.domain.usecase.ToggleReminderUseCase
import com.eventmap.server.presentation.dto.AddCommentRequest
import com.eventmap.server.presentation.dto.AttendRequest
import com.eventmap.server.presentation.dto.AttendResponse
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.LikeResponse
import com.eventmap.server.presentation.dto.RemindResponse
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

private fun ApplicationCall.userId(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()

private suspend fun ApplicationCall.unauthorized() =
    respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))

// Рилс-соцслой (D9–D11) + честное присутствие (D1c) · API §3, §8.6
fun Route.socialRoutes(
    getComments: GetCommentsUseCase,
    addComment: AddCommentUseCase,
    toggleLike: ToggleCommentLikeUseCase,
    toggleReminder: ToggleReminderUseCase,
    attend: AttendUseCase,
) {
    // чтение комментариев доступно гостям; viewerId — для likedByMe
    authenticate("auth-jwt", optional = true) {
        get("/events/{id}/comments") {
            val eventId = call.parameters["id"].orEmpty()
            call.respond(getComments(eventId, call.userId()).map { it.toDto() })
        }
    }

    authenticate("auth-jwt") {
        post("/events/{id}/comments") {
            val uid = call.userId() ?: return@post call.unauthorized()
            val body = call.receive<AddCommentRequest>()
            when (val r = addComment(call.parameters["id"].orEmpty(), uid, body.body, body.parentId)) {
                is AddCommentUseCase.Result.Ok -> call.respond(HttpStatusCode.Created, r.comment.toDto())
                is AddCommentUseCase.Result.Error ->
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorResponse.Body("BAD_REQUEST", r.message)))
            }
        }

        post("/comments/{id}/like") {
            val uid = call.userId() ?: return@post call.unauthorized()
            val count = toggleLike(call.parameters["id"].orEmpty(), uid)
            if (count == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("COMMENT_NOT_FOUND", "Комментарий не найден")))
            } else {
                call.respond(LikeResponse(count))
            }
        }

        post("/events/{id}/remind") {
            val uid = call.userId() ?: return@post call.unauthorized()
            call.respond(RemindResponse(toggleReminder(uid, call.parameters["id"].orEmpty())))
        }

        post("/events/{id}/attend") {
            val uid = call.userId() ?: return@post call.unauthorized()
            val body = call.receive<AttendRequest>()
            when (val r = attend(uid, call.parameters["id"].orEmpty(), body.action, body.lat, body.lng)) {
                is AttendUseCase.Result.Ok -> call.respond(AttendResponse(r.status, r.goingCount))
                is AttendUseCase.Result.NotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("EVENT_NOT_FOUND", "Событие не найдено")))
                is AttendUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }
    }
}
