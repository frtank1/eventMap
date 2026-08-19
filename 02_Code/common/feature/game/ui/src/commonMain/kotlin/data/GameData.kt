package com.eventmap.game.ui.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import com.eventmap.game.ui.models.BadgeItem
import com.eventmap.game.ui.models.GameState
import com.eventmap.game.ui.models.LeaderboardEntry
import com.eventmap.game.ui.models.MissionItem
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.koin.dsl.module

// =====================================================================
//  R · Игровой хаб — сетевой слой (/game/profile, /game/missions, /districts/leaderboard)
//  DTO зеркалят server/presentation/dto/Dto.kt
// =====================================================================

@Serializable
data class BadgeNet(val code: String, val title: String, val emoji: String? = null)

@Serializable
data class GameProfileNet(
    val xp: Long = 0,
    val level: Int = 1,
    val levelTitle: String = "",
    val nextLevelXp: Long = 0,
    val badges: List<BadgeNet> = emptyList(),
    val districtsVisited: Int = 0,
    val districtsTotal: Int = 0,
)

@Serializable
data class MissionNet(
    val id: String,
    val kind: String = "",
    val progress: Int = 0,
    val target: Int = 1,
    val rewardXp: Int = 0,
    val multiplier: Float = 1f,
)

@Serializable
data class DistrictScoreNet(val districtId: String, val name: String, val visits: Long)

// Лидерборд игроков R2 (GET /game/leaderboard/users)
@Serializable
data class UserScoreNet(
    val userId: String,
    val name: String,
    val xp: Long = 0,
    val level: Int = 1,
    val rank: Int = 0,
    val isMe: Boolean = false,
)

// Капсулы времени R3 (/capsules*)
@Serializable
data class CapsuleNet(
    val id: String,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val recipientType: String = "public",
    val opened: Boolean = false,
    val mine: Boolean = false,
    val mediaUrl: String? = null,
    val bodyText: String? = null,
    val opensAtMs: Long? = null,
)

@Serializable
data class CreateCapsuleBody(
    val lat: Double,
    val lng: Double,
    val mediaUrl: String? = null,
    val bodyText: String? = null,
    val recipientType: String, // friend | self | public
    val recipientId: String? = null,
)

@Serializable
data class OpenCapsuleBody(val lat: Double? = null, val lng: Double? = null)

class GameRemoteDataSource(private val httpClient: HttpClient) {
    suspend fun profile(): HttpResponse = httpClient.get("game/profile")
    suspend fun missions(): HttpResponse = httpClient.get("game/missions")
    suspend fun leaderboard(): HttpResponse = httpClient.get("districts/leaderboard")
    suspend fun userLeaderboard(scope: String): HttpResponse =
        httpClient.get("game/leaderboard/users") { parameter("scope", scope) }
    suspend fun capsulesNearby(bbox: String): HttpResponse =
        httpClient.get("capsules/nearby") { parameter("bbox", bbox) }
    suspend fun createCapsule(body: CreateCapsuleBody): HttpResponse =
        httpClient.post("capsules") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    suspend fun openCapsule(id: String, body: OpenCapsuleBody): HttpResponse =
        httpClient.post("capsules/$id/open") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
}

interface GameRepository {
    suspend fun profile(): KtorResponse<GameProfileNet>
    suspend fun missions(): KtorResponse<List<MissionNet>>
    suspend fun leaderboard(): KtorResponse<List<DistrictScoreNet>>
    suspend fun userLeaderboard(scope: String): KtorResponse<List<UserScoreNet>>
    suspend fun capsulesNearby(lat: Double, lng: Double): KtorResponse<List<CapsuleNet>>
    suspend fun createCapsule(body: CreateCapsuleBody): KtorResponse<CapsuleNet>
    suspend fun openCapsule(id: String, lat: Double?, lng: Double?): KtorResponse<CapsuleNet>
}

class GameRepositoryImpl(private val remote: GameRemoteDataSource) : GameRepository {
    override suspend fun profile(): KtorResponse<GameProfileNet> = safeRequest { remote.profile() }
    override suspend fun missions(): KtorResponse<List<MissionNet>> = safeRequest { remote.missions() }
    override suspend fun leaderboard(): KtorResponse<List<DistrictScoreNet>> = safeRequest { remote.leaderboard() }
    override suspend fun userLeaderboard(scope: String): KtorResponse<List<UserScoreNet>> =
        safeRequest { remote.userLeaderboard(scope) }

    // bbox ≈ ±550 м вокруг точки — «капсулы рядом»
    override suspend fun capsulesNearby(lat: Double, lng: Double): KtorResponse<List<CapsuleNet>> =
        safeRequest { remote.capsulesNearby("${lat - 0.005},${lng - 0.005},${lat + 0.005},${lng + 0.005}") }

    override suspend fun createCapsule(body: CreateCapsuleBody): KtorResponse<CapsuleNet> =
        safeRequest { remote.createCapsule(body) }

    override suspend fun openCapsule(id: String, lat: Double?, lng: Double?): KtorResponse<CapsuleNet> =
        safeRequest { remote.openCapsule(id, OpenCapsuleBody(lat, lng)) }
}

val gameDataModule = module {
    single { GameRemoteDataSource(httpClient = get()) }
    single<GameRepository> { GameRepositoryImpl(remote = get()) }
}

// =====================================================================
//  Маппинг DTO → GameState (UI). Сезонный пасс пока живёт на клиенте.
// =====================================================================

private fun missionEmoji(kind: String): String = when {
    "friend" in kind || "coop" in kind -> "👥"
    "district" in kind || "fog" in kind -> "🌫️"
    "quest" in kind -> "🎮"
    "video" in kind -> "🎬"
    else -> "⭐"
}

private fun missionTitle(kind: String): String = when {
    "friend" in kind || "coop" in kind -> "Сходи на событие с другом"
    "district" in kind || "fog" in kind -> "Открой новый район"
    "quest" in kind -> "Пройди квест недели"
    "video" in kind -> "Сними момент на событии"
    else -> "Задание недели"
}

fun buildGameState(
    profile: GameProfileNet,
    missions: List<MissionNet>,
    leaderboard: List<DistrictScoreNet>,
    base: GameState,
): GameState = base.copy(
    level = profile.level,
    title = profile.levelTitle.ifEmpty { base.title },
    xp = profile.xp.toInt(),
    xpNextLevel = profile.nextLevelXp.toInt().coerceAtLeast(1),
    badges = profile.badges
        .mapIndexed { i, b -> BadgeItem(id = b.code.ifEmpty { "b$i" }, emoji = b.emoji ?: "🏅", title = b.title, description = "", unlocked = true) }
        .ifEmpty { base.badges },
    missions = missions
        .map { m ->
            MissionItem(
                id = m.id,
                emoji = missionEmoji(m.kind),
                title = missionTitle(m.kind),
                xpReward = m.rewardXp,
                isCoOp = m.multiplier > 1f,
                progress = (m.progress.toFloat() / m.target.coerceAtLeast(1)).coerceIn(0f, 1f),
                done = m.progress >= m.target,
            )
        }
        .ifEmpty { base.missions },
    leaderboard = leaderboard
        .mapIndexed { i, d ->
            LeaderboardEntry(rank = i + 1, districtName = d.name, emoji = listOf("🥇", "🥈", "🥉").getOrElse(i) { "🏙" }, score = d.visits.toInt())
        }
        .ifEmpty { base.leaderboard },
)
