package com.eventmap.profile.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val subscriptions: Int = 0,
    val subscribers: Int = 0,
    val eventsCount: Int = 0,
    val isOrganizer: Boolean = false,
    val isVerified: Boolean = false,
)

@Serializable
data class HistoryItem(
    val eventId: String,
    val title: String,
    val dateLabel: String = "",
    val category: String = "",
)
