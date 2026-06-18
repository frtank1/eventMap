package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.model.QuestPoint
import com.eventmap.server.domain.usecase.CheckinQuestPointUseCase
import com.eventmap.server.domain.usecase.CreateQuestUseCase
import com.eventmap.server.domain.usecase.GetQuestUseCase
import com.eventmap.server.domain.usecase.GetQuestsNearbyUseCase
import com.eventmap.server.domain.usecase.GetReceiptCodeUseCase
import com.eventmap.server.domain.usecase.ReviewQuestUseCase
import com.eventmap.server.domain.model.Bbox
import com.eventmap.server.presentation.dto.CreateQuestRequest
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.QuestCheckinRequest
import com.eventmap.server.presentation.dto.QuestCheckinResponse
import com.eventmap.server.presentation.dto.QuestReviewRequest
import com.eventmap.server.presentation.dto.ReceiptCodeResponse
import com.eventmap.server.presentation.dto.toDto
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
import io.ktor.server.routing.get
import io.ktor.server.routing.post

private fun ApplicationCall.uid(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()

private fun parseBbox(raw: String?): Bbox? {
    val p = raw?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() } ?: return null
    return if (p.size == 4) Bbox(p[0], p[1], p[2], p[3]) else null
}

// Промо-квесты (Q1–Q6, API §8.3)
fun Route.questRoutes(
    createQuest: CreateQuestUseCase,
    nearby: GetQuestsNearbyUseCase,
    getQuest: GetQuestUseCase,
    checkin: CheckinQuestPointUseCase,
    receiptCode: GetReceiptCodeUseCase,
    reviewQuest: ReviewQuestUseCase,
    adminKey: String,
) {
    authenticate("auth-jwt", optional = true) {
        get("/quests/nearby") {
            call.respond(nearby(parseBbox(call.request.queryParameters["bbox"]), call.uid()).map { it.toDto() })
        }
        get("/quests/{id}") {
            val q = getQuest(call.parameters["id"].orEmpty(), call.uid())
            if (q == null) call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("QUEST_NOT_FOUND", "Квест не найден")))
            else call.respond(q.toDto())
        }
    }

    authenticate("auth-jwt") {
        post("/quests") {
            val uid = call.uid() ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val b = call.receive<CreateQuestRequest>()
            val points = b.points.map { QuestPoint(id = "", ord = 0, lat = it.lat, lng = it.lng, placeName = it.placeName, condition = it.condition) }
            when (val r = createQuest(uid, b.title, b.emoji, b.orderedChain, b.startsAt, b.endsAt, b.prizeTitle, b.prizeCount, b.participantLimit, points)) {
                is CreateQuestUseCase.Result.Ok -> call.respond(HttpStatusCode.Created, r.quest.toDto())
                is CreateQuestUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }

        post("/quests/{id}/points/{pid}/checkin") {
            val uid = call.uid() ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val b = call.receive<QuestCheckinRequest>()
            val questId = call.parameters["id"].orEmpty()
            when (val r = checkin(uid, questId, call.parameters["pid"].orEmpty(), b.lat, b.lng, b.mediaUrl, b.qrPayload)) {
                is CheckinQuestPointUseCase.Result.Ok -> {
                    val total = getQuest(questId, uid)?.points?.size ?: 0
                    call.respond(QuestCheckinResponse(r.run.checkedPointIds.size, total, r.finished, r.run.prizeQr, r.xpGained))
                }
                is CheckinQuestPointUseCase.Result.NotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Квест или точка не найдены")))
                is CheckinQuestPointUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }

        // код для кассы (печатает бизнес — владелец квеста)
        get("/quests/{id}/points/{pid}/receipt-code") {
            val uid = call.uid() ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            when (val r = receiptCode(uid, call.parameters["id"].orEmpty(), call.parameters["pid"].orEmpty())) {
                is GetReceiptCodeUseCase.Result.Ok -> call.respond(ReceiptCodeResponse(r.code))
                is GetReceiptCodeUseCase.Result.NotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Квест или точка не найдены")))
                is GetReceiptCodeUseCase.Result.Forbidden ->
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("FORBIDDEN", "Код доступен только владельцу квеста")))
            }
        }
    }

    post("/admin/quests/{id}/review") {
        if (call.request.header("X-Admin-Key") != adminKey) {
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("FORBIDDEN", "Неверный админ-ключ")))
        }
        val b = call.receive<QuestReviewRequest>()
        if (b.decision != "approved" && b.decision != "rejected") {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorResponse.Body("BAD_REQUEST", "decision: approved | rejected")))
        }
        val q = reviewQuest(call.parameters["id"].orEmpty(), b.decision == "approved")
        if (q == null) call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Квест не найден")))
        else call.respond(q.toDto())
    }
}
