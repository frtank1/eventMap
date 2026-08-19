package com.eventmap.quest.ui.models

// =====================================================================
//  Q · Промо-квесты — модели UI
// =====================================================================

enum class CheckInType(val label: String, val emoji: String) {
    Geo("Геолокация", "📍"),
    Photo("Фото на месте", "📷"),
    Qr("Скан QR-чека", "🧾"),
}

enum class PointStatus { Locked, Active, Done }

data class QuestPoint(
    val id: String,
    val title: String,
    val address: String,
    val checkInType: CheckInType,
    val status: PointStatus,
    val distanceM: Int? = null,
    // для чек-ина на бэке (POST /quests/{id}/points/{pid}/checkin)
    val questId: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)

data class QuestItem(
    val id: String,
    val emoji: String,
    val title: String,
    val authorName: String,
    val isVerifiedBusiness: Boolean = false,
    val pointsTotal: Int,
    val pointsDone: Int,
    val checkInType: CheckInType,
    val deadlineLabel: String,
    val finishersCount: Int,
    val rewardLabel: String,
    val points: List<QuestPoint> = emptyList(),
)
