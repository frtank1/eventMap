package com.eventmap.event.models

import kotlinx.serialization.Serializable

enum class EventStatus { Soon, Live, Ended }

@Serializable
data class EventLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val name: String = "",
)

@Serializable
data class EventPrice(
    val type: String = "free",          // free | paid
    val amount: Int = 0,
    val currency: String = "KZT",
) {
    val isFree: Boolean get() = type == "free"
    fun label(): String = if (isFree) "Бесплатно" else "$amount $currency"
}

/** «N друзей уже там» (GET /events/{id}/friends-going) */
@Serializable
data class FriendsGoing(
    val count: Int = 0,
    val names: List<String> = emptyList(),
)

@Serializable
data class EventDetail(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String = "",
    val status: String = "soon",        // soon | live | ended
    val startAt: String = "",
    val endAt: String = "",
    val location: EventLocation = EventLocation(),
    val price: EventPrice = EventPrice(),
    val goingCount: Int = 0,
    val liveViewers: Int = 0,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val commentsCount: Int = 0, // честный счётчик с бэка (event_comments)
    val cover: String? = null,
    val organizerId: String = "",
    val organizerName: String = "",
    val kind: String = "planned",
    val sourceType: String = "initiator",
    val visibility: String = "public",
    val joinPolicy: String = "open",
    val audience: String = "everyone",
    val minAge: Int? = null,
    val capacity: Int? = null,
    val expiresAtMs: Long? = null,
    val confirmationCount: Int = 0,
    val interestCount: Int = 0,
) {
    fun statusEnum(): EventStatus = when (status) {
        "live" -> EventStatus.Live
        "ended" -> EventStatus.Ended
        else -> EventStatus.Soon
    }
}

@Serializable
data class Participant(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val type: String = "going",         // going | was
)

@Serializable
data class MediaItem(
    val id: String,
    val url: String,
    val type: String = "photo",         // photo | video
    val authorName: String = "",
)

// Черновик создания события (3 шага макета).
data class CreateEventDraft(
    val title: String = "",
    val description: String = "",
    val coverMediaId: String? = null,
    val location: EventLocation? = null,
    val startAt: String = "",
    val endAt: String = "",
    val category: String = "",
    val price: EventPrice = EventPrice(),
    val limit: Int? = null,
    val kind: String = "planned",
    val sourceType: String = "initiator",
    val visibility: String = "public",
    val joinPolicy: String = "open",
    val audience: String = "everyone",
    val minAge: Int? = null,
    val expiresAtMs: Long? = null,
    val proofMediaId: String? = null,
)

@Serializable
data class Ticket(
    val status: String = "none",        // none | pending | paid
    val paymentUrl: String? = null,
)

@Serializable
data class Rating(
    val value: Int,
    val review: String = "",
    val authorName: String = "",
    val dateLabel: String = "",
    val tags: List<String> = emptyList(), // тег-чипсы P4 («🔥 Атмосфера»…)
)

// Комментарий события (D9) — зеркало CommentDto бэка (§8.6)
@Serializable
data class EventComment(
    val id: String,
    val eventId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val parentId: String? = null,
    val body: String,
    val likeCount: Int = 0,
    val likedByMe: Boolean = false,
    val createdAtMs: Long = 0,
)

// Организатор событий (площадка/промоутер/пользователь-организатор).
@Serializable
data class Organizer(
    val id: String,
    val name: String,
    val bio: String = "",
    val avatarUrl: String? = null,
    val isVerified: Boolean = false,
    val eventsCount: Int = 0,
    val subscribers: Int = 0,
    val isSubscribed: Boolean = false,
)
