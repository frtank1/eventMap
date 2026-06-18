package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.usecase.AddPinAnimationUseCase
import com.eventmap.server.domain.usecase.ApplyBusinessUseCase
import com.eventmap.server.domain.usecase.GetMyBusinessUseCase
import com.eventmap.server.domain.usecase.GetPinAnimationsUseCase
import com.eventmap.server.domain.usecase.ReviewBusinessUseCase
import com.eventmap.server.domain.usecase.UpdateBusinessPinUseCase
import com.eventmap.server.presentation.dto.AddAnimationRequest
import com.eventmap.server.presentation.dto.AnimationsResponse
import com.eventmap.server.presentation.dto.BusinessApplyRequest
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.ReviewRequest
import com.eventmap.server.presentation.dto.UpdatePinRequest
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
import io.ktor.server.routing.patch
import io.ktor.server.routing.post

private fun ApplicationCall.uid(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()

// Бизнес-аккаунты юрлиц (M1–M6, API §8.1)
fun Route.businessRoutes(
    apply: ApplyBusinessUseCase,
    myBusiness: GetMyBusinessUseCase,
    pinAnimations: GetPinAnimationsUseCase,
    updatePin: UpdateBusinessPinUseCase,
    review: ReviewBusinessUseCase,
    addAnimation: AddPinAnimationUseCase,
    adminKey: String,
) {
    authenticate("auth-jwt") {
        post("/business/apply") {
            val uid = call.uid() ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val b = call.receive<BusinessApplyRequest>()
            when (val r = apply(uid, b.bin.trim(), b.legalName, b.brandName, b.category, b.phone, b.social, b.documents.map { it.kind to it.fileUrl })) {
                is ApplyBusinessUseCase.Result.Ok -> call.respond(HttpStatusCode.Created, r.account.toDto())
                is ApplyBusinessUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }

        get("/business/me") {
            val uid = call.uid() ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val biz = myBusiness(uid)
            if (biz == null) call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NO_BUSINESS", "Заявки нет — подай через /business/apply")))
            else call.respond(biz.toDto())
        }

        // набор анимаций ТОЛЬКО своей категории (M4)
        get("/business/animations") {
            val uid = call.uid() ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            when (val r = pinAnimations(uid)) {
                is GetPinAnimationsUseCase.Result.Ok ->
                    call.respond(AnimationsResponse(r.category, r.animations.map { it.toDto() }))
                is GetPinAnimationsUseCase.Result.NoBusiness ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NO_BUSINESS", "Сначала подай заявку на бизнес-аккаунт")))
            }
        }

        patch("/business/pin") {
            val uid = call.uid() ?: return@patch call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))
            val b = call.receive<UpdatePinRequest>()
            when (val r = updatePin(uid, b.logoUrl, b.pinColor, b.animationId)) {
                is UpdateBusinessPinUseCase.Result.Ok -> call.respond(r.account.toDto())
                is UpdateBusinessPinUseCase.Result.NoBusiness ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NO_BUSINESS", "Бизнес-аккаунт не найден")))
                is UpdateBusinessPinUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }
    }

    // ===== Админ (MVP: заголовок X-Admin-Key, до полноценных ролей) =====
    post("/admin/business/{id}/review") {
        if (call.request.header("X-Admin-Key") != adminKey) {
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("FORBIDDEN", "Неверный админ-ключ")))
        }
        val b = call.receive<ReviewRequest>()
        if (b.decision != "approved" && b.decision != "rejected") {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorResponse.Body("BAD_REQUEST", "decision: approved | rejected")))
        }
        val r = review(call.parameters["id"].orEmpty(), b.decision == "approved", b.reason)
        if (r == null) call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Заявка не найдена")))
        else call.respond(r.toDto()) // TODO: пуш+email заявителю (M6)
    }

    post("/admin/animation-sets") {
        if (call.request.header("X-Admin-Key") != adminKey) {
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("FORBIDDEN", "Неверный админ-ключ")))
        }
        val b = call.receive<AddAnimationRequest>()
        call.respond(HttpStatusCode.Created, addAnimation(b.category, b.name, b.lottieKey).toDto())
    }
}
