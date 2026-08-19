package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.Bbox
import com.eventmap.server.domain.model.GameProfile
import com.eventmap.server.domain.model.Mission
import com.eventmap.server.domain.model.TimeCapsule
import com.eventmap.server.domain.repository.CapsuleRepository
import com.eventmap.server.domain.repository.FriendRepository
import com.eventmap.server.domain.repository.GameRepository
import com.eventmap.server.domain.repository.XpRepository

// ===== Геймификация (R1–R3, API §8.4) =====

// Пороги уровней и титулы (R1)
private val LEVELS = listOf(
    0L to "Новичок",
    100L to "Прохожий",
    300L to "Завсегдатай",
    700L to "Городской исследователь",
    1500L to "Знаток района",
    3000L to "Легенда движа",
    6000L to "Хозяин города",
)

fun levelFor(xp: Long): Triple<Int, String, Long> {
    var idx = 0
    for ((i, p) in LEVELS.withIndex()) if (xp >= p.first) idx = i
    val next = if (idx + 1 < LEVELS.size) LEVELS[idx + 1].first else LEVELS.last().first
    return Triple(idx + 1, LEVELS[idx].second, next)
}

class GetGameProfileUseCase(
    private val xp: XpRepository,
    private val game: GameRepository,
) {
    suspend operator fun invoke(userId: String): GameProfile {
        val total = xp.totalFor(userId)
        val (level, title, next) = levelFor(total)
        val (visited, totalDistricts) = game.districtsStats(userId)
        return GameProfile(
            xp = total, level = level, levelTitle = title, nextLevelXp = next,
            badges = game.badgesOf(userId),
            districtsVisited = visited, districtsTotal = totalDistricts,
        )
    }
}

class GetMissionsUseCase(private val game: GameRepository) {
    suspend operator fun invoke(userId: String): List<Mission> = game.missionsOf(userId)
}

class GetFogUseCase(private val game: GameRepository) {
    /** Туман войны G3: какие районы открыты */
    suspend operator fun invoke(userId: String): List<String> = game.visitedDistrictIds(userId)
}

class GetDistrictLeaderboardUseCase(private val game: GameRepository) {
    suspend operator fun invoke(): List<Triple<String, String, Long>> = game.leaderboard()
}

class GetUserLeaderboardUseCase(
    private val xp: XpRepository,
    private val friends: FriendRepository,
    private val users: com.eventmap.server.domain.repository.UserRepository,
) {
    data class Row(val userId: String, val name: String, val xp: Long, val level: Int, val isMe: Boolean)

    /** Лидерборд игроков R2: scope=global — топ-50 по XP; scope=friends — принятые друзья + я */
    suspend operator fun invoke(userId: String, scope: String): List<Row> {
        val raw: List<Triple<String, String, Long>> = if (scope == "friends") {
            val friendPairs = friends.listFor(userId)
                .filter { it.status == "accepted" }
                .map { it.userId to it.name }
            val myName = users.byId(userId)?.name ?: "Я"
            val all = friendPairs + (userId to myName)
            val totals = xp.totalsFor(all.map { it.first })
            all.map { (id, name) -> Triple(id, name, totals[id] ?: 0L) }
                .sortedByDescending { it.third }
        } else {
            xp.topUsers(50)
        }
        return raw.map { (id, name, total) ->
            Row(userId = id, name = name, xp = total, level = levelFor(total).first, isMe = id == userId)
        }
    }
}

// ===== Капсулы времени (R3/G15) =====

class CreateCapsuleUseCase(
    private val capsules: CapsuleRepository,
    private val friends: FriendRepository,
) {
    sealed interface Result {
        data class Ok(val capsule: TimeCapsule) : Result
        data class Rejected(val code: String, val message: String) : Result
    }

    suspend operator fun invoke(
        ownerId: String,
        lat: Double,
        lng: Double,
        mediaUrl: String?,
        bodyText: String?,
        recipientType: String,
        recipientId: String?,
    ): Result {
        if (mediaUrl.isNullOrBlank() && bodyText.isNullOrBlank()) {
            return Result.Rejected("EMPTY", "Капсула пуста — добавь фото или послание")
        }
        val opensAt: Long? = when (recipientType) {
            "user" -> {
                if (recipientId.isNullOrBlank()) return Result.Rejected("RECIPIENT_REQUIRED", "Укажи друга")
                if (!friends.areFriends(ownerId, recipientId)) {
                    return Result.Rejected("NOT_FRIEND", "Капсулу можно оставить только другу")
                }
                null // откроет на месте в любой момент
            }
            "self_1y" -> System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000
            "public" -> null
            else -> return Result.Rejected("BAD_RECIPIENT", "recipientType: user | self_1y | public")
        }
        val c = capsules.create(
            TimeCapsule(
                id = "", ownerId = ownerId, lat = lat, lng = lng,
                mediaUrl = mediaUrl, bodyText = bodyText,
                recipientType = recipientType,
                recipientId = if (recipientType == "user") recipientId else if (recipientType == "self_1y") ownerId else null,
                opensAtMs = opensAt,
            ),
        )
        return Result.Ok(c)
    }
}

class GetCapsulesNearbyUseCase(private val capsules: CapsuleRepository) {
    suspend operator fun invoke(userId: String, bbox: Bbox?): List<TimeCapsule> =
        capsules.nearbyFor(userId, bbox)
            // содержимое скрываем до открытия: наружу только пины
            .map { if (it.ownerId == userId) it else it.copy(mediaUrl = null, bodyText = null) }
}

class OpenCapsuleUseCase(private val capsules: CapsuleRepository) {
    sealed interface Result {
        data class Ok(val capsule: TimeCapsule) : Result
        data object NotFound : Result
        data class Rejected(val code: String, val message: String) : Result
    }

    private val openRadiusM = 100.0

    suspend operator fun invoke(userId: String, capsuleId: String, lat: Double?, lng: Double?): Result {
        val c = capsules.byId(capsuleId) ?: return Result.NotFound
        val allowed = when (c.recipientType) {
            "user" -> c.recipientId == userId
            "self_1y" -> c.ownerId == userId
            "public" -> true
            else -> false
        }
        if (!allowed) return Result.Rejected("FORBIDDEN", "Эта капсула не для тебя")
        c.opensAtMs?.let { if (System.currentTimeMillis() < it) return Result.Rejected("TOO_EARLY", "Капсула ещё закрыта — откроется позже") }
        if (lat == null || lng == null) return Result.Rejected("GEO_REQUIRED", "Капсула открывается только на месте")
        val d = haversineMeters(c.lat, c.lng, lat, lng)
        if (d > openRadiusM) return Result.Rejected("TOO_FAR", "Ты в ${d.toInt()} м — подойди ближе (≤ ${openRadiusM.toInt()} м)")
        if (!c.opened) capsules.markOpened(capsuleId, userId)
        return Result.Ok(c.copy(opened = true))
    }
}
