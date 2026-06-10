package com.eventmap.feed.models

import kotlinx.serialization.Serializable

@Serializable
data class FeedItem(
    val id: String,
    val eventId: String,
    val title: String,
    val organizerName: String = "",
    val timeLabel: String = "",
    val category: String = "",
)

@Serializable
data class NotificationItem(
    val id: String,
    val type: String = "info",      // info | event | subscribe | live
    val text: String,
    val timeLabel: String = "",
    val read: Boolean = false,
)

@Serializable
data class SubscriptionItem(
    val id: String,
    val name: String,
    val type: String = "organizers",   // organizers | genres | people
    val subtitle: String = "",
    val emoji: String = "",
)

@Serializable
data class SubscriptionRequest(
    val id: String,
    val name: String,
    val subtitle: String = "",
)
