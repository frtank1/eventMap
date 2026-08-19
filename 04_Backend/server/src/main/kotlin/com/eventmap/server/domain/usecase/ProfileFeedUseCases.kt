package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.EventRating
import com.eventmap.server.domain.model.Notification
import com.eventmap.server.domain.model.Organizer
import com.eventmap.server.domain.model.SubscriptionRequest
import com.eventmap.server.domain.model.User
import com.eventmap.server.domain.model.UserProfile
import com.eventmap.server.domain.repository.AttendanceRepository
import com.eventmap.server.domain.repository.BusinessRepository
import com.eventmap.server.domain.repository.EventRepository
import com.eventmap.server.domain.repository.NotificationRepository
import com.eventmap.server.domain.repository.OrganizerRepository
import com.eventmap.server.domain.repository.RatingRepository
import com.eventmap.server.domain.repository.SubscriptionRepository
import com.eventmap.server.domain.repository.UserRepository

// ===== Профиль H1: /users/me, /users/{id}/history =====

class GetMyProfileUseCase(
    private val users: UserRepository,
    private val subscriptions: SubscriptionRepository,
    private val events: EventRepository,
    private val business: BusinessRepository,
) {
    suspend operator fun invoke(userId: String): UserProfile? {
        val user = users.byId(userId) ?: return null
        val biz = business.byUser(userId)
        return UserProfile(
            user = user,
            subscriptionsCount = subscriptions.countFor(userId),
            subscribersCount = 0, // подписки людей на людей — раздел N, позже
            eventsCount = events.byOrganizer(userId).size,
            isOrganizer = biz != null || events.byOrganizer(userId).isNotEmpty(),
            isVerified = biz?.status == "approved",
        )
    }
}

class UpdateMyProfileUseCase(private val users: UserRepository) {
    suspend operator fun invoke(userId: String, displayName: String?, bio: String?): User? =
        users.updateProfile(userId, displayName?.trim()?.takeIf { it.isNotEmpty() }, bio?.trim())
}

class SaveInterestsUseCase(private val users: UserRepository) {
    suspend operator fun invoke(userId: String, interests: List<String>): Boolean =
        users.saveInterests(userId, interests.filter { it.isNotBlank() }.take(30))
}

class GetUserHistoryUseCase(
    private val attendance: AttendanceRepository,
    private val events: EventRepository,
) {
    suspend operator fun invoke(userId: String): List<Event> {
        val ids = attendance.eventIdsOf(userId, type = "was")
        return events.byIds(ids).sortedByDescending { it.startAt }
    }
}

class GetEventParticipantsUseCase(
    private val attendance: AttendanceRepository,
    private val users: UserRepository,
) {
    suspend operator fun invoke(eventId: String, type: String): List<User> =
        users.byIds(attendance.userIdsOf(eventId, type))
}

// ===== Лента и уведомления (F4): /feed, /notifications =====

class GetFeedUseCase(
    private val subscriptions: SubscriptionRepository,
    private val events: EventRepository,
) {
    /** События организаторов, на которых подписан юзер (по времени начала). */
    suspend operator fun invoke(userId: String): List<Event> =
        events.byOrganizers(subscriptions.organizerIdsOf(userId))
}

class GetNotificationsUseCase(private val notifications: NotificationRepository) {
    suspend operator fun invoke(userId: String): List<Notification> = notifications.listFor(userId)
}

class MarkNotificationsReadUseCase(private val notifications: NotificationRepository) {
    suspend operator fun invoke(userId: String) = notifications.markAllRead(userId)
}

// ===== Подписки (F1): /subscriptions, /subscription-requests =====

class GetSubscriptionsUseCase(private val subscriptions: SubscriptionRepository) {
    suspend operator fun invoke(userId: String): List<Organizer> = subscriptions.organizersOf(userId)
}

class SubscribeOrganizerUseCase(
    private val subscriptions: SubscriptionRepository,
    private val organizers: OrganizerRepository,
) {
    sealed interface Result {
        data class Ok(val subscribed: Boolean) : Result
        data object NotFound : Result
    }

    suspend operator fun invoke(userId: String, organizerId: String, subscribe: Boolean): Result {
        organizers.byId(organizerId) ?: return Result.NotFound
        if (subscribe) subscriptions.subscribe(userId, organizerId) else subscriptions.unsubscribe(userId, organizerId)
        return Result.Ok(subscribe)
    }
}

class GetSubscriptionRequestsUseCase(private val subscriptions: SubscriptionRepository) {
    suspend operator fun invoke(userId: String): List<SubscriptionRequest> = subscriptions.requestsFor(userId)
}

class RespondSubscriptionRequestUseCase(private val subscriptions: SubscriptionRepository) {
    /** @return false — заявка не найдена/чужая/уже обработана */
    suspend operator fun invoke(userId: String, requestId: String, approve: Boolean): Boolean =
        subscriptions.respondRequest(requestId, userId, approve)
}

// ===== Поиск G2: /search?q&type =====

class SearchUseCase(
    private val events: EventRepository,
    private val organizers: OrganizerRepository,
    private val users: UserRepository,
) {
    data class Hits(
        val events: List<Event> = emptyList(),
        val organizers: List<Organizer> = emptyList(),
        val people: List<User> = emptyList(),
        val genres: List<String> = emptyList(),
    )

    suspend operator fun invoke(q: String, type: String): Hits {
        val query = q.trim()
        if (query.length < 2) return Hits()
        return when (type) {
            "organizers" -> Hits(organizers = organizers.searchByName(query))
            "people" -> Hits(people = users.searchByName(query))
            "genres" -> Hits(genres = events.categoriesLike(query))
            else -> Hits(events = events.searchText(query))
        }
    }
}

// ===== Оценки P4: POST /events/{id}/rating, GET /events/{id}/ratings =====

class RateEventUseCase(
    private val ratings: RatingRepository,
    private val attendance: AttendanceRepository,
) {
    sealed interface Result {
        data class Ok(val rating: EventRating) : Result
        data object NotFound : Result
        data class Rejected(val code: String, val message: String) : Result
    }

    suspend operator fun invoke(eventId: String, userId: String, value: Int, review: String, tags: List<String> = emptyList()): Result {
        if (value !in 1..5) return Result.Rejected("BAD_VALUE", "Оценка — от 1 до 5 звёзд")
        val rating = ratings.rate(eventId, userId, value, review.take(2000), tags) ?: return Result.NotFound
        // оценил — значит был: пополняем историю H1
        attendance.set(userId, eventId, type = "was")
        return Result.Ok(rating)
    }
}

class GetEventRatingsUseCase(private val ratings: RatingRepository) {
    suspend operator fun invoke(eventId: String): List<EventRating> = ratings.byEvent(eventId)
}

// ===== Статус билета юзера на событие (mobile: GET /events/{id}/ticket) =====

class GetEventTicketStatusUseCase(private val tickets: com.eventmap.server.domain.repository.TicketRepository) {
    /** paid — есть активный билет; none — нет */
    suspend operator fun invoke(eventId: String, userId: String): String =
        if (tickets.myTickets(userId).any { it.eventId == eventId && it.status == "active" }) "paid" else "none"
}
