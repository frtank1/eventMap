package com.eventmap.server.presentation.dto

import com.eventmap.server.domain.model.BusinessAccount
import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.EventComment
import com.eventmap.server.domain.model.Friend
import com.eventmap.server.domain.model.GameProfile
import com.eventmap.server.domain.model.Invitation
import com.eventmap.server.domain.model.Mission
import com.eventmap.server.domain.model.TimeCapsule
import com.eventmap.server.domain.model.Organizer
import com.eventmap.server.domain.model.PinAnimation
import com.eventmap.server.domain.model.Quest
import com.eventmap.server.domain.model.QuestPoint
import com.eventmap.server.domain.model.Ticket
import kotlinx.serialization.Serializable

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class RegisterRequest(val email: String, val password: String, val name: String = "")
@Serializable data class RefreshRequest(val refreshToken: String)

@Serializable data class UserDto(val id: String, val name: String, val avatarUrl: String? = null)
@Serializable data class AuthResponse(val accessToken: String, val refreshToken: String, val expiresIn: Long, val user: UserDto)
@Serializable data class RefreshResponse(val accessToken: String, val refreshToken: String, val expiresIn: Long)

@Serializable data class LocationDto(val lat: Double, val lng: Double, val name: String = "")
@Serializable data class PriceDto(val type: String, val amount: Int = 0, val currency: String = "KZT")

// Тело POST /events — зеркало клиентского CreateEventRequest (API_CONTRACT §3).
@Serializable data class CreateEventRequest(
    val title: String,
    val description: String = "",
    val coverMediaId: String? = null,
    val location: LocationDto? = null,
    val startAt: String = "",
    val endAt: String = "",
    val category: String = "",
    val price: PriceDto = PriceDto("free"),
    val limit: Int? = null,
)
@Serializable data class EventDto(
    val id: String, val title: String, val description: String, val category: String, val status: String,
    val startAt: String, val endAt: String, val location: LocationDto, val price: PriceDto,
    val goingCount: Int, val liveViewers: Int, val ratingAvg: Double, val ratingCount: Int,
    val cover: String? = null, val organizerId: String = "", val organizerName: String = "",
)
@Serializable data class OrganizerDto(
    val id: String, val name: String, val bio: String, val avatarUrl: String? = null,
    val isVerified: Boolean, val eventsCount: Int, val subscribers: Int, val isSubscribed: Boolean = false,
)
@Serializable data class ErrorResponse(val error: Body) {
    @Serializable data class Body(val code: String, val message: String)
}

// ===== Рилс-соцслой (D9–D11, API §8.6) =====
@Serializable data class CommentDto(
    val id: String, val eventId: String, val authorId: String, val authorName: String,
    val parentId: String? = null, val body: String,
    val likeCount: Int, val likedByMe: Boolean, val createdAtMs: Long,
)
@Serializable data class AddCommentRequest(val body: String, val parentId: String? = null)
@Serializable data class LikeResponse(val likeCount: Int)
@Serializable data class RemindResponse(val enabled: Boolean)
// action: going (до старта, free) | here (live + гео) | cancel
@Serializable data class AttendRequest(val action: String, val lat: Double? = null, val lng: Double? = null)
@Serializable data class AttendResponse(val status: String? = null, val goingCount: Long)

// ===== Билеты (P, API §8.2) =====
/** Гость: {"userId":"..."} — друг с аккаунтом · {} — анонимный «+N» */
@Serializable data class GuestDto(val userId: String? = null)
@Serializable data class PurchaseRequest(val guests: List<GuestDto> = emptyList(), val provider: String = "mock")
@Serializable data class TicketDto(
    val id: String, val orderId: String, val eventId: String, val eventTitle: String,
    val ownerUserId: String? = null, val status: String,
)
@Serializable data class PurchaseResponse(
    val orderId: String, val status: String, val qty: Int, val amount: Int, val currency: String,
    val tickets: List<TicketDto>, val goingCount: Long,
)
@Serializable data class TicketQrResponse(val qrSecret: String)
@Serializable data class CheckinRequest(val qrSecret: String)

// ===== Бизнес-аккаунты (M, API §8.1) =====
@Serializable data class BusinessDocDto(val kind: String, val fileUrl: String)
@Serializable data class BusinessApplyRequest(
    val bin: String, val legalName: String, val brandName: String, val category: String,
    val phone: String? = null, val social: String? = null,
    val documents: List<BusinessDocDto> = emptyList(),
)
@Serializable data class BusinessDto(
    val id: String, val status: String, val rejectReason: String? = null,
    val bin: String, val legalName: String, val brandName: String, val category: String,
    val phone: String? = null, val social: String? = null,
    val logoUrl: String? = null, val pinColor: String, val animationId: String? = null,
)
@Serializable data class PinAnimationDto(val id: String, val category: String, val name: String, val lottieKey: String)
@Serializable data class AnimationsResponse(val category: String, val animations: List<PinAnimationDto>)
@Serializable data class UpdatePinRequest(val logoUrl: String? = null, val pinColor: String? = null, val animationId: String? = null)
@Serializable data class ReviewRequest(val decision: String, val reason: String? = null) // approved | rejected
@Serializable data class AddAnimationRequest(val category: String, val name: String, val lottieKey: String)

// ===== Промо-квесты (Q, API §8.3) =====
@Serializable data class QuestPointDto(
    val id: String = "", val ord: Int = 0, val lat: Double, val lng: Double,
    val placeName: String? = null, val condition: String, val checkedByMe: Boolean = false,
)
@Serializable data class CreateQuestRequest(
    val title: String, val emoji: String? = null, val orderedChain: Boolean = false,
    val startsAt: String? = null, val endsAt: String? = null,
    val prizeTitle: String, val prizeCount: Int? = null, val participantLimit: Int? = null,
    val points: List<QuestPointDto>,
)
@Serializable data class QuestDto(
    val id: String, val businessBrand: String, val title: String, val emoji: String? = null,
    val orderedChain: Boolean, val startsAt: String? = null, val endsAt: String? = null,
    val prizeTitle: String, val status: String,
    val points: List<QuestPointDto>, val finishersCount: Long,
    val myProgress: Int = 0, val myFinished: Boolean = false, val myPrizeQr: String? = null,
)
@Serializable data class QuestCheckinRequest(
    val lat: Double? = null, val lng: Double? = null,
    val mediaUrl: String? = null, val qrPayload: String? = null,
)
@Serializable data class QuestCheckinResponse(
    val checkedPoints: Int, val totalPoints: Int, val finished: Boolean,
    val prizeQr: String? = null, val xpGained: Int,
)
@Serializable data class ReceiptCodeResponse(val code: String)
@Serializable data class QuestReviewRequest(val decision: String) // approved | rejected

// ===== Друзья и приглашения (N, API §8.7) =====
@Serializable data class FriendDto(val userId: String, val name: String, val status: String)
@Serializable data class FriendRequestBody(val userId: String)
@Serializable data class FriendRespondBody(val userId: String, val accept: Boolean)
@Serializable data class CreateInvitationRequest(
    val eventId: String, val templateId: String = "classic",
    val guestUserIds: List<String> = emptyList(), val allowLink: Boolean = true,
)
@Serializable data class InvitationGuestDto(val id: String, val userId: String? = null, val name: String, val rsvp: String? = null, val plusOne: Int = 0)
@Serializable data class InvitationDto(
    val id: String, val eventId: String, val eventTitle: String, val templateId: String,
    val link: String? = null, val guests: List<InvitationGuestDto> = emptyList(),
    val going: Int = 0, val declined: Int = 0, val silent: Int = 0,
)
@Serializable data class RsvpRequest(val answer: String, val plusOne: Int = 0, val guestName: String? = null)

// ===== Геймификация (R, API §8.4) =====
@Serializable data class BadgeDto(val code: String, val title: String, val emoji: String? = null)
@Serializable data class GameProfileDto(
    val xp: Long, val level: Int, val levelTitle: String, val nextLevelXp: Long,
    val badges: List<BadgeDto>, val districtsVisited: Int, val districtsTotal: Int,
)
@Serializable data class MissionDto(val id: String, val kind: String, val progress: Int, val target: Int, val rewardXp: Int, val multiplier: Float)
@Serializable data class DistrictScoreDto(val districtId: String, val name: String, val visits: Long)
@Serializable data class CreateCapsuleRequest(
    val lat: Double, val lng: Double, val mediaUrl: String? = null, val bodyText: String? = null,
    val recipientType: String, val recipientId: String? = null,
)
@Serializable data class CapsuleDto(
    val id: String, val lat: Double, val lng: Double, val recipientType: String,
    val opened: Boolean, val mine: Boolean,
    val mediaUrl: String? = null, val bodyText: String? = null, val opensAtMs: Long? = null,
)
@Serializable data class OpenCapsuleRequest(val lat: Double? = null, val lng: Double? = null)

// ===== Пульс карты (G1/G2/G9, API §8.5) =====
@Serializable data class PingRequest(val lat: Double, val lng: Double)
@Serializable data class PulseEventDto(val eventId: String, val glow: Float, val crowd10m: Int, val viewers: Int)
@Serializable data class AnomalyDto(val id: String, val lat: Double, val lng: Double, val userCount: Int, val startedAtMs: Long)
@Serializable data class PulseResponse(val events: List<PulseEventDto>, val anomalies: List<AnomalyDto>)

fun Event.toDto() = EventDto(
    id, title, description, category, status, startAt, endAt,
    LocationDto(location.lat, location.lng, location.name),
    PriceDto(price.type, price.amount, price.currency),
    goingCount, liveViewers, ratingAvg, ratingCount, cover, organizerId ?: "", organizerName,
)

fun Organizer.toDto() = OrganizerDto(id, name, bio, avatarUrl, isVerified, eventsCount, subscribers)

fun EventComment.toDto() = CommentDto(id, eventId, authorId, authorName, parentId, body, likeCount, likedByMe, createdAtMs)

fun Ticket.toDto() = TicketDto(id, orderId, eventId, eventTitle, ownerUserId, status)

fun BusinessAccount.toDto() = BusinessDto(id, status, rejectReason, bin, legalName, brandName, category, phone, social, logoUrl, pinColor, animationId)
fun PinAnimation.toDto() = PinAnimationDto(id, category, name, lottieKey)

fun QuestPoint.toDto() = QuestPointDto(id, ord, lat, lng, placeName, condition, checkedByMe)

fun Friend.toDto() = FriendDto(userId, name, status)

fun GameProfile.toDto() = GameProfileDto(xp, level, levelTitle, nextLevelXp, badges.map { BadgeDto(it.code, it.title, it.emoji) }, districtsVisited, districtsTotal)
fun Mission.toDto() = MissionDto(id, kind, progress, target, rewardXp, multiplier)
fun TimeCapsule.toDto(viewerId: String) = CapsuleDto(id, lat, lng, recipientType, opened, ownerId == viewerId, mediaUrl, bodyText, opensAtMs)
fun Invitation.toDto(showGuests: Boolean = true) = InvitationDto(
    id, eventId, eventTitle, templateId,
    link = linkToken?.let { "/i/$it" },
    guests = if (showGuests) guests.map { InvitationGuestDto(it.id, it.userId, it.name, it.rsvp, it.plusOne) } else emptyList(),
    going = guests.count { it.rsvp == "yes" } + guests.filter { it.rsvp == "yes" }.sumOf { it.plusOne },
    declined = guests.count { it.rsvp == "no" },
    silent = guests.count { it.rsvp == null },
)
fun Quest.toDto() = QuestDto(
    id, businessBrand, title, emoji, orderedChain, startsAt, endsAt, prizeTitle, status,
    points.map { it.toDto() }, finishersCount,
    myProgress = myRun?.checkedPointIds?.size ?: 0,
    myFinished = myRun?.finished == true,
    myPrizeQr = myRun?.prizeQr,
)
