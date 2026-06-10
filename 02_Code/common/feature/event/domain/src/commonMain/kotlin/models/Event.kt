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
    val cover: String? = null,
    val organizerId: String = "",
    val organizerName: String = "",
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
