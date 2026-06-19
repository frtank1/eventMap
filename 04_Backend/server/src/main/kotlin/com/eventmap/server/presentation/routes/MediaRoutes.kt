package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.usecase.ConfirmMediaUseCase
import com.eventmap.server.domain.usecase.GetEventMediaUseCase
import com.eventmap.server.domain.usecase.PresignUploadUseCase
import com.eventmap.server.presentation.dto.ConfirmMediaRequest
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.PresignRequest
import com.eventmap.server.presentation.dto.PresignResponse
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

// Медиа: presigned-загрузка в R2/S3 + список медиа события (§8 storage)
fun Route.mediaRoutes(
    presign: PresignUploadUseCase,
    confirm: ConfirmMediaUseCase,
    eventMedia: GetEventMediaUseCase,
) {
    // Публично: медиа события (гостям тоже)
    get("/events/{id}/media") {
        call.respond(eventMedia(call.parameters["id"].orEmpty()).map { it.toDto() })
    }

    authenticate("auth-jwt") {
        post("/media/presign") {
            val uid = call.uid() ?: return@post call.respond(
                HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")),
            )
            val body = call.receive<PresignRequest>()
            when (val r = presign(uid, body.kind, body.contentType)) {
                is PresignUploadUseCase.Result.Ok ->
                    call.respond(PresignResponse(r.mediaId, r.uploadUrl, r.publicUrl))
                is PresignUploadUseCase.Result.NotConfigured ->
                    call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse(ErrorResponse.Body("STORAGE_OFF", "Хранилище не настроено (S3_* env)")))
                is PresignUploadUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }

        post("/media/confirm") {
            val uid = call.uid() ?: return@post call.respond(
                HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")),
            )
            val body = call.receive<ConfirmMediaRequest>()
            val media = confirm(body.mediaId, uid, body.url, body.contentType, body.kind, body.eventId)
            call.respond(HttpStatusCode.Created, media.toDto())
        }
    }
}
