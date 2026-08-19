package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.usecase.GetMyProfileUseCase
import com.eventmap.server.domain.usecase.GetUserHistoryUseCase
import com.eventmap.server.domain.usecase.UpdateMyProfileUseCase
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.UpdateMeRequest
import com.eventmap.server.presentation.dto.toDto
import com.eventmap.server.presentation.dto.toHistoryItemDto
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
import io.ktor.server.routing.patch

private fun ApplicationCall.uid(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()

private suspend fun ApplicationCall.unauthorized() =
    respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))

// Профиль H1 (API §H) — контракт мобилки: GET/PATCH /users/me, GET /users/{id}/history
fun Route.userRoutes(
    getMyProfile: GetMyProfileUseCase,
    updateMyProfile: UpdateMyProfileUseCase,
    getUserHistory: GetUserHistoryUseCase,
) {
    authenticate("auth-jwt") {
        get("/users/me") {
            val uid = call.uid() ?: return@get call.unauthorized()
            val profile = getMyProfile(uid)
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("USER_NOT_FOUND", "Пользователь не найден")))
            } else {
                call.respond(profile.toDto())
            }
        }

        patch("/users/me") {
            val uid = call.uid() ?: return@patch call.unauthorized()
            val body = call.receive<UpdateMeRequest>()
            val user = updateMyProfile(uid, body.displayName, body.bio)
            val profile = if (user != null) getMyProfile(uid) else null
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("USER_NOT_FOUND", "Пользователь не найден")))
            } else {
                call.respond(profile.toDto())
            }
        }

        get("/users/{id}/history") {
            val uid = call.uid() ?: return@get call.unauthorized()
            val requested = call.parameters["id"].orEmpty()
            // история — приватная: чужую не отдаём (виден пустой список)
            val target = if (requested == "me" || requested == uid) uid else null
            if (target == null) call.respond(emptyList<String>())
            else call.respond(getUserHistory(target).map { it.toHistoryItemDto() })
        }
    }
}
