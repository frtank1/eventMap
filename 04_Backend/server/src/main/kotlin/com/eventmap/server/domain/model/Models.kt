package com.eventmap.server.domain.model

// ===== Медиа (хранилище R2/S3) =====
data class Media(
    val id: String,
    val ownerUserId: String,
    val url: String,
    val contentType: String = "",
    val kind: String = "image",   // image | video
    val eventId: String? = null,
    val createdAt: Long = 0,
    val provider: String = "storage",
    val status: String = "ready",
    val playbackId: String? = null, // internal; signed URLs are generated only after access checks
    val thumbnailUrl: String? = null,
)

// ===== Видео-рилсы («Сейчас») =====
data class Video(
    val id: String,
    val eventId: String = "",
    val eventTitle: String = "",
    val authorName: String = "",
    val caption: String = "",
    val mediaUrl: String,
    val thumb: String? = null,
    val viewers: Int = 0,
    val likes: Int = 0,
    val liked: Boolean = false,
    val category: String = "",
    val status: String = "ready",
    val mediaId: String? = null,
)

data class GeoPoint(val lat: Double, val lng: Double, val name: String = "")
data class Money(val type: String = "free", val amount: Int = 0, val currency: String = "KZT")

// Видимая область карты: bbox=swLat,swLng,neLat,neLng (API_CONTRACT §3)
data class Bbox(val swLat: Double, val swLng: Double, val neLat: Double, val neLng: Double)

// Фильтры GET /events
data class EventQuery(
    val bbox: Bbox? = null,
    val categories: List<String> = emptyList(),
    val priceType: String? = null, // free | paid
)

data class Event(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String = "",
    val status: String = "soon",       // soon | live | ended
    val startAt: String = "",
    val endAt: String = "",
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val price: Money = Money(),
    val goingCount: Int = 0,
    val liveViewers: Int = 0,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val commentsCount: Int = 0, // честный счётчик из event_comments (считается на чтении)
    val cover: String? = null,
    val organizerId: String? = null,
    val organizerName: String = "",
    val capacity: Int? = null,
    // ревизия 11.06.2026
    val isAdult: Boolean = false,
    val visibility: String = "public", // public | private (раздел N)
    val geoRadiusM: Int = 300,         // радиус «Я тоже здесь» / гео-чекинов
    // Event V2 (V13)
    val kind: String = "planned",              // planned|gathering|live_signal|private
    val sourceType: String = "initiator",      // organizer|business|government|initiator|witness
    val joinPolicy: String = "open",           // open|approval|invite
    val audience: String = "everyone",         // everyone|family|adults
    val minAge: Int? = null,
    val expiresAtMs: Long? = null,
    val proofMediaId: String? = null,
    val confirmationCount: Int = 0,
    val interestCount: Int = 0,
)

data class Organizer(
    val id: String,
    val name: String,
    val bio: String = "",
    val avatarUrl: String? = null,
    val isVerified: Boolean = false,
    val eventsCount: Int = 0,
    val subscribers: Int = 0,
)

data class User(
    val id: String,
    val email: String,
    val name: String,
    val passwordHash: String,
    // профиль H1 (V9)
    val username: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val city: String = "",
    val interests: List<String> = emptyList(),
)

// ===== Профиль/лента/уведомления/поиск (H, F, G · V9) =====

data class UserProfile(
    val user: User,
    val subscriptionsCount: Int = 0,
    val subscribersCount: Int = 0,
    val eventsCount: Int = 0,
    val isOrganizer: Boolean = false,
    val isVerified: Boolean = false,
)

data class Notification(
    val id: String,
    val userId: String,
    val type: String = "info", // info|event|subscribe|live
    val text: String,
    val createdAtMs: Long = 0,
    val read: Boolean = false,
)

data class SubscriptionRequest(
    val id: String,
    val fromUserId: String,
    val fromName: String = "",
    val createdAtMs: Long = 0,
)

data class EventRating(
    val id: String,
    val eventId: String,
    val userId: String,
    val authorName: String = "",
    val value: Int,
    val review: String = "",
    val tags: List<String> = emptyList(), // тег-чипсы P4
    val createdAtMs: Long = 0,
)

data class AuthTokens(val accessToken: String, val refreshToken: String, val expiresIn: Long, val user: User)

// ===== Рилс-соцслой (D9–D11, API §8.6) =====

data class EventComment(
    val id: String,
    val eventId: String,
    val authorId: String,
    val authorName: String = "",
    val parentId: String? = null,
    val body: String,
    val likeCount: Int = 0,
    val likedByMe: Boolean = false,
    val createdAtMs: Long = 0,
)

// Присутствие: going («приду»/«я тоже здесь») | was (история)
data class Attendance(val userId: String, val eventId: String, val type: String)

// ===== Бизнес-аккаунты (M, API §8.1) =====

data class BusinessAccount(
    val id: String,
    val userId: String,
    val bin: String,
    val legalName: String,
    val brandName: String,
    val category: String,
    val phone: String? = null,
    val social: String? = null,
    val status: String = "pending",     // pending | approved | rejected
    val rejectReason: String? = null,
    val logoUrl: String? = null,
    val pinColor: String = "#FF5C8A",
    val animationId: String? = null,
    val documents: List<BusinessDocument> = emptyList(),
)

data class BusinessDocument(val id: String, val kind: String, val fileUrl: String)

data class PinAnimation(val id: String, val category: String, val name: String, val lottieKey: String)

// ===== Геймификация (R, API §8.4) =====

data class Badge(val code: String, val title: String, val emoji: String? = null)

data class GameProfile(
    val xp: Long,
    val level: Int,
    val levelTitle: String,
    val nextLevelXp: Long,
    val badges: List<Badge>,
    val districtsVisited: Int,
    val districtsTotal: Int,
)

data class Mission(
    val id: String,
    val kind: String,
    val progress: Int,
    val target: Int,
    val rewardXp: Int,
    val multiplier: Float,
    val status: String,
)

data class TimeCapsule(
    val id: String,
    val ownerId: String,
    val lat: Double,
    val lng: Double,
    val mediaUrl: String? = null,
    val bodyText: String? = null,
    val recipientType: String,   // user | self_1y | public
    val recipientId: String? = null,
    val opensAtMs: Long? = null,
    val opened: Boolean = false,
)

// ===== Друзья и приглашения (N, API §8.7) =====

data class Friend(
    val userId: String,
    val name: String,
    val status: String,          // accepted | pending_in (ждёт моего ответа) | pending_out (я отправил)
)

// Позиция друга на карте (C10/L5, V12) — только для подтверждённых друзей, только свежая
data class FriendLocation(
    val userId: String,
    val name: String = "",
    val lat: Double,
    val lng: Double,
    val updatedAtMs: Long = 0,
)

data class InvitationGuest(
    val id: String,
    val userId: String?,         // null = гость по ссылке
    val name: String,            // имя из профиля или guest_name
    val rsvp: String?,           // yes | no | null (молчит)
    val plusOne: Int = 0,
)

data class Invitation(
    val id: String,
    val eventId: String,
    val eventTitle: String = "",
    val templateId: String,
    val linkToken: String? = null,
    val guests: List<InvitationGuest> = emptyList(),
)

// ===== Промо-квесты (Q, API §8.3) =====

data class QuestPoint(
    val id: String,
    val ord: Int,
    val lat: Double,
    val lng: Double,
    val placeName: String? = null,
    val condition: String,            // geo | photo | purchase
    val checkedByMe: Boolean = false,
)

data class Quest(
    val id: String,
    val businessId: String,
    val businessBrand: String = "",
    val title: String,
    val emoji: String? = null,
    val orderedChain: Boolean = false,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val prizeTitle: String,
    val prizeCount: Int? = null,
    val participantLimit: Int? = null,
    val status: String = "moderation",
    val points: List<QuestPoint> = emptyList(),
    val finishersCount: Long = 0,
    val myRun: QuestRun? = null,
)

data class QuestRun(
    val id: String,
    val questId: String,
    val userId: String,
    val finished: Boolean = false,
    val prizeQr: String? = null,
    val checkedPointIds: Set<String> = emptySet(),
)

// ===== Билеты (P, API §8.2) =====

/** Гость покупки: userId — друг с аккаунтом (видим в списке) | null — анонимный «+N» */
data class TicketGuest(val userId: String?)

data class Ticket(
    val id: String,
    val orderId: String,
    val eventId: String,
    val eventTitle: String = "",
    val ownerUserId: String?,   // null = анонимный
    val qrSecret: String,
    val status: String,         // active | used | refunded
)

data class TicketOrder(
    val id: String,
    val eventId: String,
    val buyerId: String,
    val qty: Int,
    val amount: Int,
    val currency: String,
    val provider: String,
    val status: String,
    val tickets: List<Ticket> = emptyList(),
)
