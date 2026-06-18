package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.model.Bbox
import com.eventmap.server.domain.usecase.CreateCapsuleUseCase
import com.eventmap.server.domain.usecase.GetCapsulesNearbyUseCase
import com.eventmap.server.domain.usecase.GetDistrictLeaderboardUseCase
import com.eventmap.server.domain.usecase.GetFogUseCase
import com.eventmap.server.domain.usecase.GetGameProfileUseCase
import com.eventmap.server.domain.usecase.GetMissionsUseCase
import com.eventmap.server.domain.usecase.OpenCapsuleUseCase
import com.eventmap.server.presentation.dto.CreateCapsuleRequest
import com.eventmap.server.presentation.dto.DistrictScoreDto
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.OpenCapsuleRequest
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

private fun parseBbox(raw: String?): Bbox? {
    val p = raw?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() } ?: return null
    return if (p.size == 4) Bbox(p[0], p[1], p[2], p[3]) else null
}

// Геймификация (R1–R3, API §8.4): профиль игрока, миссии, туман войны, районы, капсулы
fun Route.gameRoutes(
    profile: GetGameProfileUseCase,
    missions: GetMissionsUseCase,
    fog: GetFogUseCase,
    leaderboard: GetDistrictLeaderboardUseCase,
    createCapsule: CreateCapsuleUseCase,
    capsulesNearby: GetCapsulesNearbyUseCase,
    openCapsule: OpenCapsuleUseCase,
) {
    get("/districts/leaderboard") {
        call.respond(leaderboard().map { (id, name, visits) -> DistrictScoreDto(id, name, visits) })
    }

    authenticate("auth-jwt") {
        get("/game/profile") {
            val uid = call.uid() ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            call.respond(profile(uid).toDto())
        }

        get("/game/missions") {
            val uid = call.uid() ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            call.respond(missions(uid).map { it.toDto() })
        }

        get("/map/fog") {
            val uid = call.uid() ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            call.respond(mapOf("visitedDistrictIds" to fog(uid)))
        }

        post("/capsules") {
            val uid = call.uid() ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val b = call.receive<CreateCapsuleRequest>()
            when (val r = createCapsule(uid, b.lat, b.lng, b.mediaUrl, b.bodyText, b.recipientType, b.recipientId)) {
                is CreateCapsuleUseCase.Result.Ok -> call.respond(HttpStatusCode.Created, r.capsule.toDto(uid))
                is CreateCapsuleUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }

        get("/capsules/nearby") {
            val uid = call.uid() ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            call.respond(capsulesNearby(uid, parseBbox(call.request.queryParameters["bbox"])).map { it.toDto(uid) })
        }

        post("/capsules/{id}/open") {
            val uid = call.uid() ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val b = call.receive<OpenCapsuleRequest>()
            when (val r = openCapsule(uid, call.parameters["id"].orEmpty(), b.lat, b.lng)) {
                is OpenCapsuleUseCase.Result.Ok -> call.respond(r.capsule.toDto(uid))
                is OpenCapsuleUseCase.Result.NotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Капсула не найдена")))
                is OpenCapsuleUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }
    }
}
