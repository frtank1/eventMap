package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.usecase.LoginUseCase
import com.eventmap.server.domain.usecase.RegisterUseCase
import com.eventmap.server.presentation.dto.AuthResponse
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.LoginRequest
import com.eventmap.server.presentation.dto.RegisterRequest
import com.eventmap.server.presentation.dto.UserDto
import com.eventmap.server.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.authRoutes(register: RegisterUseCase, login: LoginUseCase, jwt: JwtService) {
    post("/auth/register") {
        val body = call.receive<RegisterRequest>()
        when (val r = register(body.email, body.password, body.name)) {
            is RegisterUseCase.Result.Ok -> call.respond(authResponse(r.user.id, r.user.name, jwt))
            is RegisterUseCase.Result.Error -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorResponse.Body("BAD_REQUEST", r.message)))
        }
    }
    post("/auth/login") {
        val body = call.receive<LoginRequest>()
        val user = login(body.email, body.password)
        if (user == null) call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Неверный email или пароль")))
        else call.respond(authResponse(user.id, user.name, jwt))
    }
    post("/auth/password/forgot") { call.respond(HttpStatusCode.OK) }
    post("/auth/password/reset") { call.respond(HttpStatusCode.OK) }
    post("/auth/interests") { call.respond(HttpStatusCode.OK) }
}

private fun authResponse(userId: String, name: String, jwt: JwtService) = AuthResponse(
    accessToken = jwt.generateAccess(userId),
    refreshToken = jwt.generateRefresh(userId),
    expiresIn = jwt.accessTtlSec,
    user = UserDto(userId, name),
)
