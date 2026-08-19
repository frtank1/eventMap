package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.usecase.GetFeedUseCase
import com.eventmap.server.domain.usecase.GetNotificationsUseCase
import com.eventmap.server.domain.usecase.GetSubscriptionRequestsUseCase
import com.eventmap.server.domain.usecase.GetSubscriptionsUseCase
import com.eventmap.server.domain.usecase.MarkNotificationsReadUseCase
import com.eventmap.server.domain.usecase.RespondSubscriptionRequestUseCase
import com.eventmap.server.domain.usecase.SearchUseCase
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.toDto
import com.eventmap.server.presentation.dto.toFeedItemDto
import com.eventmap.server.presentation.dto.toSearchHitDto
import com.eventmap.server.presentation.dto.toSubscriptionItemDto
import com.eventmap.server.presentation.dto.SearchHitDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

private fun ApplicationCall.uid(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()

private suspend fun ApplicationCall.unauthorized() =
    respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")))

// Лента, уведомления (F4), подписки (F1) — контракт мобилки (feature/feed)
fun Route.feedRoutes(
    getFeed: GetFeedUseCase,
    getNotifications: GetNotificationsUseCase,
    markRead: MarkNotificationsReadUseCase,
    getSubscriptions: GetSubscriptionsUseCase,
    getRequests: GetSubscriptionRequestsUseCase,
    respondRequest: RespondSubscriptionRequestUseCase,
) {
    authenticate("auth-jwt") {
        // Лента: события организаторов из подписок
        get("/feed") {
            val uid = call.uid() ?: return@get call.unauthorized()
            call.respond(getFeed(uid).map { it.toFeedItemDto() })
        }

        get("/notifications") {
            val uid = call.uid() ?: return@get call.unauthorized()
            call.respond(getNotifications(uid).map { it.toDto() })
        }

        // «Прочитать всё» (F4)
        post("/notifications/read-all") {
            val uid = call.uid() ?: return@post call.unauthorized()
            markRead(uid)
            call.respond(HttpStatusCode.OK)
        }

        // ?tab=organizers|genres|people; пока реальные данные — организаторы
        get("/subscriptions") {
            val uid = call.uid() ?: return@get call.unauthorized()
            when (call.request.queryParameters["tab"] ?: "organizers") {
                "organizers" -> call.respond(getSubscriptions(uid).map { it.toSubscriptionItemDto() })
                else -> call.respond(emptyList<String>()) // жанры/люди — раздел F, позже
            }
        }

        get("/subscription-requests") {
            val uid = call.uid() ?: return@get call.unauthorized()
            call.respond(getRequests(uid).map { it.toDto() })
        }

        post("/subscription-requests/{id}/approve") {
            val uid = call.uid() ?: return@post call.unauthorized()
            if (respondRequest(uid, call.parameters["id"].orEmpty(), approve = true)) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("REQUEST_NOT_FOUND", "Заявка не найдена")))
        }

        post("/subscription-requests/{id}/reject") {
            val uid = call.uid() ?: return@post call.unauthorized()
            if (respondRequest(uid, call.parameters["id"].orEmpty(), approve = false)) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("REQUEST_NOT_FOUND", "Заявка не найдена")))
        }
    }
}

// Поиск G2: /search?q=&type=events|organizers|people|genres (доступен и гостям)
fun Route.searchRoutes(search: SearchUseCase) {
    get("/search") {
        val q = call.request.queryParameters["q"].orEmpty()
        val type = call.request.queryParameters["type"] ?: "events"
        val hits = search(q, type)
        val result: List<SearchHitDto> =
            hits.events.map { it.toSearchHitDto() } +
                hits.organizers.map { it.toSearchHitDto() } +
                hits.people.map { it.toSearchHitDto() } +
                hits.genres.map { SearchHitDto(id = "genre-$it", type = "genres", title = it, emoji = com.eventmap.server.presentation.dto.emojiOf(it)) }
        call.respond(result)
    }
}
