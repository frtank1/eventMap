package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.usecase.CreateVideoUseCase
import com.eventmap.server.domain.usecase.GetVideoFeedUseCase
import com.eventmap.server.domain.usecase.ReactVideoUseCase
import com.eventmap.server.presentation.dto.CreateVideoRequest
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.LikeResponse
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

// Видео-рилсы (E1/E2 «Сейчас») · контракт мобилки: videos/now, videos/{id}/react
fun Route.videoRoutes(
    feed: GetVideoFeedUseCase,
    react: ReactVideoUseCase,
    create: CreateVideoUseCase,
) {
    // Лента доступна гостям; viewerId — для отметки liked.
    authenticate("auth-jwt", optional = true) {
        get("/videos/now") {
            call.respond(feed(call.userId()).map { it.toDto() })
        }
    }

    authenticate("auth-jwt") {
        post("/videos/{id}/react") {
            val uid = call.userId() ?: return@post call.unauthorized()
            val count = react(call.parameters["id"].orEmpty(), uid)
            if (count == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("VIDEO_NOT_FOUND", "Видео не найдено")))
            } else {
                call.respond(LikeResponse(count))
            }
        }

        // Публикация рилса: файл уже загружен через /media/presign → mediaUrl
        post("/videos") {
            val uid = call.userId() ?: return@post call.unauthorized()
            val body = call.receive<CreateVideoRequest>()
            when (val r = create(uid, body.eventId, body.mediaUrl, body.thumb, body.caption, body.category)) {
                is CreateVideoUseCase.Result.Ok -> call.respond(HttpStatusCode.Created, r.video.toDto())
                is CreateVideoUseCase.Result.EventNotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("EVENT_NOT_FOUND", "Событие не найдено")))
                is CreateVideoUseCase.Result.Error ->
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorResponse.Body("BAD_REQUEST", r.message)))
            }
        }
    }
}
