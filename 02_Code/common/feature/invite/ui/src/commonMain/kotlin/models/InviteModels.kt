package com.eventmap.invite.ui.models

// =====================================================================
//  N · Друзья и приглашения — модели UI
// =====================================================================

enum class RsvpStatus { Pending, Going, Declined }

data class FriendItem(
    val id: String,
    val name: String,
    val username: String,
    val presenceLabel: String = "",   // «сейчас на …» / «рядом · N км» / «был…»
    val presenceColor: PresenceColor = PresenceColor.None,
)

enum class PresenceColor { Green, Yellow, None }

data class FriendRequest(
    val id: String,
    val name: String,
    val commonLabel: String = "",     // «2 общих друга» / «были на одном событии»
)

enum class PartyType(val emoji: String, val label: String) {
    Wedding("💍", "Свадьба"),
    Birthday("🎂", "День рождения"),
    Kudaly("🎉", "Той/Кудалык"),
    Graduation("🎓", "Выпускной"),
    Housewarming("🏠", "Новоселье"),
    Party("🥳", "Вечеринка"),
}

data class InviteGuest(
    val id: String,
    val name: String,
    val username: String = "",
    val isSelected: Boolean = false,
    val rsvp: RsvpStatus = RsvpStatus.Pending,
)
