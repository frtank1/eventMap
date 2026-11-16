package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.model.BusinessAccount
import com.eventmap.server.domain.repository.BusinessRepository
import com.eventmap.server.domain.repository.QuestRepository
import com.eventmap.server.presentation.dto.BusinessDocDto
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

// =====================================================================
//  API модерации (общий ключ до полноценных ролей):
//  GET /admin — единая консоль, статические ресурсы в AdminVenueRoutes
//  GET /admin/api/business    — заявки бизнеса по статусу (X-Admin-Key)
//  GET /admin/api/quests      — квесты по статусу (X-Admin-Key)
//  Решения принимают УЖЕ существующие ручки:
//  POST /admin/business/{id}/review  ·  POST /admin/quests/{id}/review
// =====================================================================

@Serializable
data class AdminBusinessDto(
    val id: String, val status: String, val rejectReason: String? = null,
    val bin: String, val legalName: String, val brandName: String, val category: String,
    val phone: String? = null, val social: String? = null,
    val documents: List<BusinessDocDto> = emptyList(),
)

private fun BusinessAccount.toAdminDto() = AdminBusinessDto(
    id = id, status = status, rejectReason = rejectReason,
    bin = bin, legalName = legalName, brandName = brandName, category = category,
    phone = phone, social = social,
    documents = documents.map { BusinessDocDto(it.kind, it.fileUrl) },
)

fun Route.adminRoutes(
    business: BusinessRepository,
    quests: QuestRepository,
    adminKey: String,
) {
    get("/admin/api/business") {
        call.response.headers.append("Cache-Control", "no-store")
        if (!call.adminOk(adminKey)) return@get call.forbidden()
        val status = call.request.queryParameters["status"] ?: "pending"
        if (status !in setOf("pending", "approved", "rejected")) return@get call.respond(HttpStatusCode.BadRequest,
            ErrorResponse(ErrorResponse.Body("INVALID_STATUS", "Неизвестный статус организации")))
        call.respond(business.listByStatus(status).map { it.toAdminDto() })
    }

    get("/admin/api/quests") {
        call.response.headers.append("Cache-Control", "no-store")
        if (!call.adminOk(adminKey)) return@get call.forbidden()
        val status = call.request.queryParameters["status"] ?: "moderation"
        if (status !in setOf("moderation", "active", "rejected")) return@get call.respond(HttpStatusCode.BadRequest,
            ErrorResponse(ErrorResponse.Body("INVALID_STATUS", "Неизвестный статус квеста")))
        call.respond(quests.listByStatus(status).map { it.toDto() })
    }
}

private fun ApplicationCall.adminOk(key: String): Boolean = validAdminKey(key)

private suspend fun ApplicationCall.forbidden() =
    respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("FORBIDDEN", "Неверный админ-ключ")))
