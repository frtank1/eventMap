package com.eventmap.server.presentation.dto

import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.Organizer
import kotlinx.serialization.Serializable

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class RegisterRequest(val email: String, val password: String, val name: String = "")
@Serializable data class RefreshRequest(val refreshToken: String)

@Serializable data class UserDto(val id: String, val name: String, val avatarUrl: String? = null)
@Serializable data class AuthResponse(val accessToken: String, val refreshToken: String, val expiresIn: Long, val user: UserDto)

@Serializable data class LocationDto(val lat: Double, val lng: Double, val name: String = "")
@Serializable data class PriceDto(val type: String, val amount: Int, val currency: String)
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

fun Event.toDto() = EventDto(
    id, title, description, category, status, startAt, endAt,
    LocationDto(location.lat, location.lng, location.name),
    PriceDto(price.type, price.amount, price.currency),
    goingCount, liveViewers, ratingAvg, ratingCount, cover, organizerId ?: "", organizerName,
)

fun Organizer.toDto() = OrganizerDto(id, name, bio, avatarUrl, isVerified, eventsCount, subscribers)
