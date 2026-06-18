package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.model.TicketGuest
import com.eventmap.server.domain.usecase.CheckinTicketUseCase
import com.eventmap.server.domain.usecase.GetMyTicketsUseCase
import com.eventmap.server.domain.usecase.GetTicketQrUseCase
import com.eventmap.server.domain.usecase.PurchaseTicketsUseCase
import com.eventmap.server.presentation.dto.CheckinRequest
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.PurchaseRequest
import com.eventmap.server.presentation.dto.PurchaseResponse
import com.eventmap.server.presentation.dto.TicketQrResponse
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

// Билеты с гостями (P1–P2, API §8.2)
fun Route.ticketRoutes(
    purchase: PurchaseTicketsUseCase,
    myTickets: GetMyTicketsUseCase,
    ticketQr: GetTicketQrUseCase,
    checkin: CheckinTicketUseCase,
) {
    authenticate("auth-jwt") {
        post("/events/{id}/tickets/purchase") {
            val uid = call.uid() ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val body = call.receive<PurchaseRequest>()
            val guests = body.guests.map { TicketGuest(it.userId?.takeIf { id -> id.isNotBlank() }) }
            when (val r = purchase(call.parameters["id"].orEmpty(), uid, guests, body.provider)) {
                is PurchaseTicketsUseCase.Result.Ok -> call.respond(
                    HttpStatusCode.Created,
                    PurchaseResponse(
                        orderId = r.order.id, status = r.order.status, qty = r.order.qty,
                        amount = r.order.amount, currency = r.order.currency,
                        tickets = r.order.tickets.map { it.toDto() }, goingCount = r.goingCount,
                    ),
                )
                is PurchaseTicketsUseCase.Result.NotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("EVENT_NOT_FOUND", "Событие не найдено")))
                is PurchaseTicketsUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }

        get("/tickets/my") {
            val uid = call.uid() ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            call.respond(myTickets(uid).map { it.toDto() })
        }

        get("/tickets/{id}/qr") {
            val uid = call.uid() ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            when (val r = ticketQr(call.parameters["id"].orEmpty(), uid)) {
                is GetTicketQrUseCase.Result.Ok -> call.respond(TicketQrResponse(r.qrSecret))
                is GetTicketQrUseCase.Result.NotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("TICKET_NOT_FOUND", "Билет не найден")))
                is GetTicketQrUseCase.Result.Forbidden ->
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("FORBIDDEN", "Это не твой билет")))
            }
        }

        post("/tickets/{id}/checkin") {
            val uid = call.uid() ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val body = call.receive<CheckinRequest>()
            when (val r = checkin(call.parameters["id"].orEmpty(), body.qrSecret, uid)) {
                is CheckinTicketUseCase.Result.Ok -> call.respond(r.ticket.toDto())
                is CheckinTicketUseCase.Result.NotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("TICKET_INVALID", "Билет не найден, уже использован или QR не совпал")))
                is CheckinTicketUseCase.Result.Forbidden ->
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("FORBIDDEN", "Сканировать может только организатор события")))
            }
        }
    }
}
