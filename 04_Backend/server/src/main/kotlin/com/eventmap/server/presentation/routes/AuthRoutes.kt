package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.usecase.IssueTokensUseCase
import com.eventmap.server.domain.usecase.LoginUseCase
import com.eventmap.server.domain.usecase.LogoutUseCase
import com.eventmap.server.domain.usecase.RefreshSessionUseCase
import com.eventmap.server.domain.usecase.RegisterUseCase
import com.eventmap.server.domain.usecase.SaveInterestsUseCase
import com.eventmap.server.domain.usecase.SessionTokens
import com.eventmap.server.presentation.dto.AuthResponse
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.InterestsRequest
import com.eventmap.server.presentation.dto.LoginRequest
import com.eventmap.server.presentation.dto.RefreshRequest
import com.eventmap.server.presentation.dto.RefreshResponse
import com.eventmap.server.presentation.dto.RegisterRequest
import com.eventmap.server.presentation.dto.UserDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.authRoutes(
    register: RegisterUseCase,
    login: LoginUseCase,
    issueTokens: IssueTokensUseCase,
    refreshSession: RefreshSessionUseCase,
    logout: LogoutUseCase,
    saveInterests: SaveInterestsUseCase,
) {
    post("/auth/register") {
        val body = call.receive<RegisterRequest>()
        when (val r = register(body.email, body.password, body.name)) {
            is RegisterUseCase.Result.Ok ->
                call.respond(issueTokens(r.user.id).toAuthResponse(r.user.id, r.user.name))
            is RegisterUseCase.Result.Error ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorResponse.Body("BAD_REQUEST", r.message)))
        }
    }
    post("/auth/login") {
        val body = call.receive<LoginRequest>()
        val user = login(body.email, body.password)
        if (user == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Неверный email или пароль")))
        } else {
            call.respond(issueTokens(user.id).toAuthResponse(user.id, user.name))
        }
    }
    // Ротация refresh-токена: старый отзывается, выдаётся новая пара.
    post("/auth/refresh") {
        val body = call.receive<RefreshRequest>()
        val tokens = refreshSession(body.refreshToken)
        if (tokens == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Refresh-токен недействителен")))
        } else {
            call.respond(RefreshResponse(tokens.accessToken, tokens.refreshToken, tokens.expiresIn))
        }
    }
    post("/auth/logout") {
        val body = call.receive<RefreshRequest>()
        logout(body.refreshToken)
        call.respond(HttpStatusCode.OK)
    }
    post("/auth/password/forgot") { call.respond(HttpStatusCode.OK) }
    post("/auth/password/reset") { call.respond(HttpStatusCode.OK) }

    // Интересы A8: сохраняются в users.interests (персонализация карты/ленты)
    authenticate("auth-jwt") {
        post("/auth/interests") {
            val uid = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val body = call.receive<InterestsRequest>()
            saveInterests(uid, body.interests)
            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun SessionTokens.toAuthResponse(userId: String, name: String) = AuthResponse(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresIn = expiresIn,
    user = UserDto(userId, name),
)
