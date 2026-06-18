package com.eventmap.game.ui.models

// =====================================================================
//  R · Геймификация — модели UI
// =====================================================================

data class BadgeItem(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
)

data class SeasonPassItem(
    val stepIndex: Int,       // 0-based
    val title: String,
    val emoji: String,
    val unlocked: Boolean,
)

data class MissionItem(
    val id: String,
    val emoji: String,
    val title: String,
    val xpReward: Int,
    val isCoOp: Boolean = false,
    val progress: Float = 0f,    // 0..1
    val done: Boolean = false,
)

data class LeaderboardEntry(
    val rank: Int,
    val districtName: String,
    val emoji: String,
    val score: Int,
    val isCurrentUser: Boolean = false,
)

data class GameState(
    val level: Int = 7,
    val title: String = "Городской исследователь",
    val xp: Int = 2340,
    val xpNextLevel: Int = 3000,
    val badges: List<BadgeItem> = emptyList(),
    val seasonName: String = "Лето 2026",
    val seasonProgress: Int = 3,
    val seasonTotal: Int = 7,
    val seasonPasses: List<SeasonPassItem> = emptyList(),
    val missions: List<MissionItem> = emptyList(),
    val leaderboard: List<LeaderboardEntry> = emptyList(),
)
