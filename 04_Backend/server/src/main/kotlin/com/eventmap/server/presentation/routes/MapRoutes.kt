package com.eventmap.server.presentation.routes

import com.eventmap.server.data.pulse.PulseService
import com.eventmap.server.domain.model.Bbox
import com.eventmap.server.presentation.dto.AnomalyDto
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.PingRequest
import com.eventmap.server.presentation.dto.PulseEventDto
import com.eventmap.server.presentation.dto.PulseResponse
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

// Пульс карты (G1/G2/G9, API §8.5): пинги (opt-in!) и агрегаты
fun Route.mapRoutes(pulse: PulseService) {
    // агрегаты публичные — это и есть «живая карта»
    get("/map/pulse") {
        val (events, anomalies) = pulse.pulse(parseBbox(call.request.queryParameters["bbox"]))
        call.respond(
            PulseResponse(
                events = events.map { PulseEventDto(it.eventId, it.glow, it.crowd10m, it.viewers) },
                anomalies = anomalies.map { AnomalyDto(it.id, it.lat, it.lng, it.userCount, it.startedAtMs) },
            ),
        )
    }

    authenticate("auth-jwt") {
        // клиент шлёт пинг ТОЛЬКО при включённом тумблере «аналитика» (I2)
        post("/map/ping") {
            val uid = call.uid() ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val b = call.receive<PingRequest>()
            pulse.recordPing(uid, b.lat, b.lng)
            call.respond(HttpStatusCode.Accepted)
        }
    }
}
