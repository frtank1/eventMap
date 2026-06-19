package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.usecase.RegisterPushTokenUseCase
import com.eventmap.server.domain.usecase.SendPushUseCase
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.PushResultResponse
import com.eventmap.server.presentation.dto.PushTestRequest
import com.eventmap.server.presentation.dto.RegisterTokenRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

private fun ApplicationCall.userId(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()

// Push-уведомления (FCM/APNS). Регистрация токена устройства + админ-тест отправки.
fun Route.pushRoutes(
    register: RegisterPushTokenUseCase,
    sendTest: SendPushUseCase,
    adminKey: String,
) {
    authenticate("auth-jwt") {
        post("/push/register") {
            val uid = call.userId() ?: return@post call.respond(
                HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")),
            )
            val body = call.receive<RegisterTokenRequest>()
            if (body.token.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorResponse.Body("BAD_REQUEST", "token обязателен")))
            }
            register(uid, body.token, body.platform)
            call.respond(HttpStatusCode.OK)
        }
    }

    // Админ-тест: проверить доставку на устройства пользователя (X-Admin-Key).
    post("/admin/push/test") {
        if (call.request.header("X-Admin-Key") != adminKey) {
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("FORBIDDEN", "Неверный админ-ключ")))
        }
        val body = call.receive<PushTestRequest>()
        val sent = sendTest(body.userId, body.title, body.body)
        call.respond(PushResultResponse(sent))
    }
}
