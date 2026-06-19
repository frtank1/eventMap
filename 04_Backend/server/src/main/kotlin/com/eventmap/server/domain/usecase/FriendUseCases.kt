package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.Friend
import com.eventmap.server.domain.model.Invitation
import com.eventmap.server.domain.repository.EventRepository
import com.eventmap.server.domain.repository.FriendRepository
import com.eventmap.server.domain.repository.InvitationRepository
import com.eventmap.server.domain.repository.NotificationPort
import java.util.UUID

// ===== Друзья (N1) и приглашения (N2–N5, API §8.7) =====
//
// Правила:
//  - дружба взаимная: заявка → подтверждение адресатом;
//  - приглашение создаёт организатор события; событие становится private
//    (исчезает из публичной карты/выдачи — фильтр в EventRepository.search);
//  - гости без приложения — по link_token: открытка в браузере, RSVP с именем;
//  - RSVP виден только организатору (дашборд N5).

class SendFriendRequestUseCase(
    private val friends: FriendRepository,
    private val notifications: NotificationPort,
) {
    sealed interface Result { data object Ok : Result; data class Rejected(val code: String, val message: String) : Result }

    suspend operator fun invoke(fromId: String, toId: String): Result {
        if (fromId == toId) return Result.Rejected("SELF", "Нельзя добавить в друзья себя")
        if (toId.isBlank()) return Result.Rejected("BAD_REQUEST", "userId обязателен")
        if (!friends.request(fromId, toId)) return Result.Rejected("ALREADY", "Заявка уже есть или вы уже друзья")
        // Пуш адресату (fire-and-forget: no-op если FCM выключен, ошибка не валит запрос)
        runCatching { notifications.sendToUser(toId, "Новая заявка в друзья", "Вас добавили в друзья в EventMap") }
        return Result.Ok
    }
}

class RespondFriendRequestUseCase(private val friends: FriendRepository) {
    /** @return false — заявки не было / отвечает не адресат */
    suspend operator fun invoke(userId: String, otherId: String, accept: Boolean): Boolean =
        friends.respond(userId, otherId, accept)
}

class GetFriendsUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(userId: String): List<Friend> = friends.listFor(userId)
}

class CreateInvitationUseCase(
    private val invitations: InvitationRepository,
    private val events: EventRepository,
    private val notifications: NotificationPort,
) {
    sealed interface Result {
        data class Ok(val invitation: Invitation, val link: String?) : Result
        data object EventNotFound : Result
        data class Rejected(val code: String, val message: String) : Result
    }

    suspend operator fun invoke(
        organizerId: String,
        eventId: String,
        templateId: String,
        guestUserIds: List<String>,
        allowLink: Boolean,
    ): Result {
        val event = events.byId(eventId) ?: return Result.EventNotFound
        if (event.organizerId != organizerId) {
            return Result.Rejected("FORBIDDEN", "Приглашения рассылает организатор события")
        }
        if (invitations.byEvent(eventId) != null) {
            return Result.Rejected("ALREADY_EXISTS", "У события уже есть приглашение — редактируй его")
        }
        if (guestUserIds.isEmpty() && !allowLink) {
            return Result.Rejected("NO_GUESTS", "Добавь гостей или включи ссылку")
        }
        val token = if (allowLink) UUID.randomUUID().toString().replace("-", "") else null
        val inv = invitations.create(eventId, templateId.ifBlank { "classic" }, guestUserIds, token)
        // Пуш приглашённым гостям с аккаунтом (fire-and-forget, no-op если FCM выключен)
        guestUserIds.forEach { gid ->
            runCatching { notifications.sendToUser(gid, "Вас пригласили на событие", event.title) }
        }
        // TODO: events.markPrivate(eventId) — отдельным PATCH
        return Result.Ok(inv, token?.let { "/i/$it" })
    }
}

class GetInvitationForGuestUseCase(private val invitations: InvitationRepository) {
    /** Открытка in-app: только если юзер в списке гостей */
    suspend operator fun invoke(invitationId: String, userId: String): Invitation? {
        val inv = invitations.byId(invitationId) ?: return null
        return if (inv.guests.any { it.userId == userId }) inv else null
    }
}

class GetInvitationByTokenUseCase(private val invitations: InvitationRepository) {
    /** Публичная веб-открытка (без аккаунта) */
    suspend operator fun invoke(token: String): Invitation? = invitations.byLinkToken(token)
}

class RsvpUseCase(private val invitations: InvitationRepository) {
    sealed interface Result { data object Ok : Result; data object NotFound : Result; data class Rejected(val code: String, val message: String) : Result }

    suspend fun byUser(invitationId: String, userId: String, answer: String, plusOne: Int): Result {
        if (answer != "yes" && answer != "no") return Result.Rejected("BAD_ANSWER", "answer: yes | no")
        if (plusOne !in 0..5) return Result.Rejected("BAD_PLUS_ONE", "plusOne: 0..5")
        return if (invitations.rsvpByUser(invitationId, userId, answer, plusOne)) Result.Ok else Result.NotFound
    }

    suspend fun byLink(token: String, guestName: String, answer: String, plusOne: Int): Result {
        if (answer != "yes" && answer != "no") return Result.Rejected("BAD_ANSWER", "answer: yes | no")
        if (guestName.isBlank()) return Result.Rejected("NAME_REQUIRED", "Представься — имя обязательно")
        val inv = invitations.byLinkToken(token) ?: return Result.NotFound
        invitations.rsvpByLink(inv.id, guestName.trim(), answer, plusOne.coerceIn(0, 5))
        return Result.Ok
    }
}

class GetRsvpDashboardUseCase(
    private val invitations: InvitationRepository,
    private val events: EventRepository,
) {
    sealed interface Result { data class Ok(val invitation: Invitation) : Result; data object NotFound : Result; data object Forbidden : Result }

    /** Дашборд N5 — только организатору */
    suspend operator fun invoke(eventId: String, userId: String): Result {
        val event = events.byId(eventId) ?: return Result.NotFound
        if (event.organizerId != userId) return Result.Forbidden
        val inv = invitations.byEvent(eventId) ?: return Result.NotFound
        return Result.Ok(inv)
    }
}
