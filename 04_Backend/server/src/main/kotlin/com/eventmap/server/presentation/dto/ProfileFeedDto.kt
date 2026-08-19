package com.eventmap.server.presentation.dto

import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.EventRating
import com.eventmap.server.domain.model.Notification
import com.eventmap.server.domain.model.Organizer
import com.eventmap.server.domain.model.SubscriptionRequest
import com.eventmap.server.domain.model.User
import com.eventmap.server.domain.model.UserProfile
import kotlinx.serialization.Serializable

// ===== V9: контракт мобилки — профиль H1, лента/уведомления F4, подписки F1, поиск G2, оценки P4 =====
// Формы полей 1:1 с моделями клиента (common/feature/{profile,feed,search,event}).

// --- Профиль (mobile: UserProfile) ---
@Serializable data class ProfileDto(
    val id: String,
    val name: String,
    val username: String,
    val bio: String = "",
    val avatarUrl: String? = null,
    val subscriptions: Int = 0,
    val subscribers: Int = 0,
    val eventsCount: Int = 0,
    val isOrganizer: Boolean = false,
    val isVerified: Boolean = false,
)

@Serializable data class UpdateMeRequest(val displayName: String? = null, val bio: String? = null)

// --- История H1 (mobile: HistoryItem) ---
@Serializable data class HistoryItemDto(
    val eventId: String,
    val title: String,
    val dateLabel: String = "",
    val category: String = "",
)

// --- Лента (mobile: FeedItem) ---
@Serializable data class FeedItemDto(
    val id: String,
    val eventId: String,
    val title: String,
    val organizerName: String = "",
    val timeLabel: String = "",
    val category: String = "",
)

// --- Уведомления F4 (mobile: NotificationItem) ---
@Serializable data class NotificationDto(
    val id: String,
    val type: String = "info", // info | event | subscribe | live
    val text: String,
    val timeLabel: String = "",
    val read: Boolean = false,
)

// --- Подписки F1 (mobile: SubscriptionItem / SubscriptionRequest) ---
@Serializable data class SubscriptionItemDto(
    val id: String,
    val name: String,
    val type: String = "organizers", // organizers | genres | people
    val subtitle: String = "",
    val emoji: String = "",
)

@Serializable data class SubscriptionRequestDto(
    val id: String,
    val name: String,
    val subtitle: String = "",
)

// --- Поиск G2 (mobile: SearchHit) ---
@Serializable data class SearchHitDto(
    val id: String,
    val type: String = "events", // events | organizers | people | genres
    val title: String,
    val subtitle: String = "",
    val eventId: String? = null,
    val emoji: String = "",
)

// --- Билет события (mobile: Ticket {status, paymentUrl}) ---
@Serializable data class EventTicketDto(
    val status: String = "none", // none | pending | paid
    val paymentUrl: String? = null,
)

// --- Оценки P4 (mobile: Rating) ---
@Serializable data class EventRatingDto(
    val value: Int,
    val review: String = "",
    val authorName: String = "",
    val dateLabel: String = "",
    val tags: List<String> = emptyList(), // тег-чипсы P4 (V11)
)

@Serializable data class RateEventRequest(val value: Int, val review: String = "", val tags: List<String> = emptyList())

// --- Гео-шеринг друзей C10/L5 (V12) ---
@Serializable data class FriendLocationDto(
    val userId: String,
    val name: String = "",
    val lat: Double,
    val lng: Double,
    val agoLabel: String = "", // «2 мин назад»
)

@Serializable data class ShareLocationRequest(val lat: Double, val lng: Double)

// --- Интересы A8 ---
@Serializable data class InterestsRequest(val interests: List<String> = emptyList())

// --- Участники (mobile: Participant) ---
@Serializable data class ParticipantDto(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val type: String = "going", // going | was
)

// ===== Маппинг =====

fun UserProfile.toDto() = ProfileDto(
    id = user.id,
    name = user.name,
    username = user.username,
    bio = user.bio,
    avatarUrl = user.avatarUrl,
    subscriptions = subscriptionsCount,
    subscribers = subscribersCount,
    eventsCount = eventsCount,
    isOrganizer = isOrganizer,
    isVerified = isVerified,
)

fun Event.toHistoryItemDto() = HistoryItemDto(
    eventId = id, title = title, dateLabel = dateLabelOf(startAt), category = category,
)

fun Event.toFeedItemDto() = FeedItemDto(
    id = "feed-$id", eventId = id, title = title,
    organizerName = organizerName, timeLabel = dateLabelOf(startAt), category = category,
)

fun Notification.toDto() = NotificationDto(
    id = id, type = type, text = text, timeLabel = relativeLabelOf(createdAtMs), read = read,
)

fun Organizer.toSubscriptionItemDto() = SubscriptionItemDto(
    id = id, name = name, type = "organizers",
    subtitle = bio.ifEmpty { "$subscribers подписчиков" },
    emoji = "",
)

fun SubscriptionRequest.toDto() = SubscriptionRequestDto(
    id = id, name = fromName.ifEmpty { "Пользователь" }, subtitle = "хочет подписаться",
)

fun Event.toSearchHitDto() = SearchHitDto(
    id = id, type = "events", title = title,
    subtitle = listOf(dateLabelOf(startAt), location.name).filter { it.isNotEmpty() }.joinToString(" · "),
    eventId = id, emoji = emojiOf(category),
)

fun Organizer.toSearchHitDto() = SearchHitDto(
    id = id, type = "organizers", title = name, subtitle = bio, eventId = null, emoji = "",
)

fun User.toSearchHitDto() = SearchHitDto(
    id = id, type = "people", title = name.ifEmpty { "@$username" }, subtitle = "@$username", eventId = null, emoji = "",
)

fun EventRating.toDto() = EventRatingDto(
    value = value, review = review, authorName = authorName, dateLabel = relativeLabelOf(createdAtMs), tags = tags,
)

fun com.eventmap.server.domain.model.FriendLocation.toDto() = FriendLocationDto(
    userId = userId, name = name.ifEmpty { "Друг" }, lat = lat, lng = lng, agoLabel = relativeLabelOf(updatedAtMs),
)

fun User.toParticipantDto(type: String) = ParticipantDto(id = id, name = name.ifEmpty { "@$username" }, avatarUrl = avatarUrl, type = type)

// «2026-07-01T20:00» → «1 июл, 20:00»; пусто — «»
internal fun dateLabelOf(iso: String): String {
    if (iso.length < 10) return iso
    val months = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    val day = iso.substring(8, 10).trimStart('0')
    val month = iso.substring(5, 7).toIntOrNull()?.let { months.getOrNull(it - 1) } ?: return iso
    val time = if (iso.length >= 16) " · ${iso.substring(11, 16)}" else ""
    return "$day $month$time"
}

// относительное время в стиле F4: «5 мин назад», «1 ч назад», «вчера», «2 дн назад»
internal fun relativeLabelOf(atMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (atMs <= 0) return ""
    val diffMin = ((nowMs - atMs) / 60_000).coerceAtLeast(0)
    return when {
        diffMin < 1 -> "только что"
        diffMin < 60 -> "$diffMin мин назад"
        diffMin < 60 * 24 -> "${diffMin / 60} ч назад"
        diffMin < 60 * 48 -> "вчера"
        else -> "${diffMin / (60 * 24)} дн назад"
    }
}

// эмодзи категории для поиска/ленты (категории из CATEGORIES_AND_ROLES.md)
internal fun emojiOf(category: String): String = when (category.lowercase()) {
    "музыка", "music", "концерты" -> "🎵"
    "еда", "food" -> "🍔"
    "спорт", "sport", "забеги" -> "🏃"
    "детям", "kids" -> "🧒"
    "шоу", "show", "стендап" -> "🎭"
    "скидки", "sale" -> "🛍"
    "лекции", "education" -> "📚"
    "выставки", "art" -> "🎨"
    "ночная жизнь", "night" -> "🌃"
    else -> ""
}
