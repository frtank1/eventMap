package com.eventmap.server.domain.model

data class GeoPoint(val lat: Double, val lng: Double, val name: String = "")
data class Money(val type: String = "free", val amount: Int = 0, val currency: String = "KZT")

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
    val cover: String? = null,
    val organizerId: String? = null,
    val organizerName: String = "",
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
)

data class AuthTokens(val accessToken: String, val refreshToken: String, val expiresIn: Long, val user: User)
